package dev.foodfleet.ch33.pool;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks connection-pool sizes against the database. A database does useful work on about as many connections as
 * it has cores busy; beyond that, connections queue for CPU, locks and I/O inside the server, and every one costs
 * memory. Measured on 2 vCPUs: 16 to 256 connections added at most 10-19% throughput and raised p99 inside the
 * database from 6 ms to 76-98 ms; opening a connection per transaction cut throughput from about 7,000 to 430-460.
 * The demand doesn't change with the pool size: requests beyond it wait in the application, which is cheaper and
 * fairer than waiting in the database.
 */
public final class PoolSizing {

    public record Fleet(String service, int instances, int poolSizePerInstance) {

        int connections() {
            return instances * poolSizePerInstance;
        }
    }

    public record Verdict(int totalConnections, int available, int suggestedTotal, List<String> problems) {

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private PoolSizing() {
    }

    /** The usual starting point for the whole database: cores × 2, plus the disks that can work in parallel. */
    public static int suggestedTotal(int databaseCores, int effectiveSpindles) {
        return databaseCores * 2 + effectiveSpindles;
    }

    /**
     * @param maxConnections the database's max_connections
     * @param reserved connections kept free for administrators, migrations, replication and monitoring
     * @param maxInstancesDuringDeploy how many instances run at once during a rolling deploy, as a share of normal
     *        (e.g. 1.25 when a quarter of the instances are replaced at a time with surge)
     */
    public static Verdict check(List<Fleet> fleets, int maxConnections, int reserved, double maxInstancesDuringDeploy,
                                int databaseCores, int effectiveSpindles) {
        int total = fleets.stream().mapToInt(Fleet::connections).sum();
        int peak = (int) Math.ceil(fleets.stream().mapToDouble(f -> f.connections() * maxInstancesDuringDeploy).sum());
        int available = maxConnections - reserved;
        int suggested = suggestedTotal(databaseCores, effectiveSpindles);
        List<String> problems = new ArrayList<>();
        if (peak > available) {
            problems.add("during a deploy the fleets can open " + peak + " connections; the database allows " + available);
        }
        if (total > suggested * 4) {
            problems.add(total + " connections for " + databaseCores + " cores: most will queue inside the database;"
                    + " smaller pools (or a pooler such as PgBouncer in front) will do more work");
        }
        for (Fleet f : fleets) {
            if (f.poolSizePerInstance() < 2) {
                problems.add(f.service() + ": a pool of " + f.poolSizePerInstance() + " can't serve a request and a health check at once");
            }
        }
        return new Verdict(total, available, suggested, List.copyOf(problems));
    }
}
