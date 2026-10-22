package dev.foodfleet.ch52.health;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A named set of health checks served on one endpoint, like Spring Boot's health groups
 * ({@code /actuator/health/liveness}, {@code /actuator/health/readiness}): the group's status is its worst member's.
 * Each check runs with a timeout, so one hanging dependency can't make the endpoint slower than the probe's own
 * timeout; a check that times out counts as DOWN.
 */
public final class HealthGroup {

    private static final ExecutorService CHECKS = Executors.newVirtualThreadPerTaskExecutor();

    private final String name;
    private final Map<String, HealthCheck> members;
    private final Duration timeout;

    public HealthGroup(String name, Map<String, HealthCheck> members, Duration timeout) {
        this.name = name;
        this.members = new LinkedHashMap<>(members);
        this.timeout = timeout;
    }

    public String name() {
        return name;
    }

    public Map<String, HealthCheck> members() {
        return Map.copyOf(members);
    }

    /** Runs every member (in parallel) and returns the aggregate. */
    public Report report() {
        Map<String, Future<HealthCheck.Result>> running = new LinkedHashMap<>();
        members.forEach((n, c) -> running.put(n, CHECKS.submit(c::check)));
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, HealthCheck.Result> results = new LinkedHashMap<>();
        Status status = Status.UNKNOWN;          // an empty group, like Spring Boot's
        boolean first = true;
        for (Map.Entry<String, Future<HealthCheck.Result>> e : running.entrySet()) {
            HealthCheck.Result r;
            try {
                r = e.getValue().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ex) {
                e.getValue().cancel(true);
                r = HealthCheck.Result.down("timed out after " + timeout.toMillis() + " ms");
            } catch (Exception ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                r = HealthCheck.Result.down(cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
            results.put(e.getKey(), r);
            status = first ? r.status() : Status.worst(status, r.status());
            first = false;
        }
        return new Report(status, results);
    }

    public record Report(Status status, Map<String, HealthCheck.Result> components) {

        /** Spring Boot's JSON shape; details only when {@code showDetails} (Boot: show-details=when-authorized). */
        public String json(boolean showDetails) {
            StringBuilder b = new StringBuilder("{\"status\":\"").append(status).append('"');
            if (showDetails) {
                b.append(",\"components\":{");
                boolean first = true;
                for (Map.Entry<String, HealthCheck.Result> e : components.entrySet()) {
                    b.append(first ? "" : ",").append('"').append(e.getKey()).append("\":{\"status\":\"").append(e.getValue().status()).append('"');
                    if (!e.getValue().details().isEmpty()) {
                        b.append(",\"details\":{");
                        boolean f2 = true;
                        for (Map.Entry<String, Object> d : e.getValue().details().entrySet()) {
                            b.append(f2 ? "" : ",").append('"').append(d.getKey()).append("\":\"")
                                    .append(String.valueOf(d.getValue()).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                            f2 = false;
                        }
                        b.append('}');
                    }
                    b.append('}');
                    first = false;
                }
                b.append('}');
            }
            return b.append('}').toString();
        }
    }
}
