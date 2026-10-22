package dev.foodfleet.ch46.failover;

import java.util.List;
import java.util.Optional;

/**
 * Client-side failover for a database with one writable primary among several hosts: remember the primary, and when
 * a write fails, ask each host whether it is the primary (PostgreSQL: {@code SELECT pg_is_in_recovery()} is false)
 * and use the first that is. The same idea as the PostgreSQL JDBC driver's
 * {@code targetServerType=primary} with several hosts in the URL, without waiting for DNS to change.
 */
public final class PrimaryLocator {

    @FunctionalInterface
    public interface RoleCheck {
        /** True if the host accepts writes; false if it is a standby; throws if it can't be reached. */
        boolean isPrimary(String host) throws Exception;
    }

    private final List<String> hosts;
    private final RoleCheck check;
    private volatile String current;

    public PrimaryLocator(List<String> hosts, RoleCheck check) {
        if (hosts.isEmpty()) {
            throw new IllegalArgumentException("at least one host");
        }
        this.hosts = List.copyOf(hosts);
        this.check = check;
        this.current = hosts.get(0);
    }

    public String current() {
        return current;
    }

    /** Looks for the primary among all hosts, starting after the one that just failed. */
    public Optional<String> relocate() {
        int start = Math.max(0, hosts.indexOf(current));
        for (int i = 1; i <= hosts.size(); i++) {
            String host = hosts.get((start + i) % hosts.size());
            try {
                if (check.isPrimary(host)) {
                    current = host;
                    return Optional.of(host);
                }
            } catch (Exception unreachable) {
                // try the next one
            }
        }
        return Optional.empty();
    }
}
