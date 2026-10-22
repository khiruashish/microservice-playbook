package dev.foodfleet.ch56.hpa;

import java.util.ArrayList;
import java.util.List;

/**
 * What an HPA does with each recommendation, following Kubernetes 1.37's {@code horizontal.go}: a stabilization window
 * (scale down only to the highest recommendation of the window, scale up only to the lowest), then rate limits per
 * period, then min and max. One instance per HPA; call {@link #next} once per sync (every 15 s by default).
 */
public final class ScalingBehavior {

    public enum Type { PODS, PERCENT }

    public record Policy(Type type, int value, int periodSeconds) {
    }

    /** {@code selectMax}: the policy allowing the biggest change wins (the default); otherwise the smallest. */
    public record Rules(int stabilizationSeconds, boolean selectMax, List<Policy> policies) {

        /** Kubernetes' defaults: no window; up by 4 pods or 100% per 15 s, whichever is more. */
        public static Rules defaultScaleUp() {
            return new Rules(0, true, List.of(new Policy(Type.PODS, 4, 15), new Policy(Type.PERCENT, 100, 15)));
        }

        /** Kubernetes' defaults: a 300 s window; down by up to 100% per 15 s. */
        public static Rules defaultScaleDown() {
            return new Rules(300, true, List.of(new Policy(Type.PERCENT, 100, 15)));
        }
    }

    private record Recommendation(long atMillis, int replicas) {
    }

    private record Event(long atMillis, int change) {
    }

    private final int min;
    private final int max;
    private final Rules up;
    private final Rules down;
    private final List<Recommendation> recommendations = new ArrayList<>();
    private final List<Event> ups = new ArrayList<>();
    private final List<Event> downs = new ArrayList<>();

    public ScalingBehavior(int min, int max, Rules up, Rules down) {
        this.min = min;
        this.max = max;
        this.up = up;
        this.down = down;
    }

    /** The replica count the HPA sets, given the calculator's recommendation. */
    public int next(long nowMillis, int current, int recommended) {
        // 1. stabilize: within each window, use the lowest recent recommendation to go up, the highest to go down
        int upBound = recommended;
        int downBound = recommended;
        for (Recommendation r : recommendations) {
            if (r.atMillis() > nowMillis - up.stabilizationSeconds() * 1000L) {
                upBound = Math.min(upBound, r.replicas());
            }
            if (r.atMillis() > nowMillis - down.stabilizationSeconds() * 1000L) {
                downBound = Math.max(downBound, r.replicas());
            }
        }
        long keep = Math.max(up.stabilizationSeconds(), down.stabilizationSeconds()) * 1000L;
        recommendations.removeIf(r -> r.atMillis() <= nowMillis - keep);
        recommendations.add(new Recommendation(nowMillis, recommended));
        int desired = Math.min(Math.max(current, upBound), downBound);
        // 2. rate limits and bounds
        if (desired > current) {
            int limit = Math.max(current, limit(nowMillis, current, up, true));
            desired = Math.min(desired, Math.min(max, limit));
        } else if (desired < current) {
            int limit = Math.min(current, limit(nowMillis, current, down, false));
            desired = Math.max(desired, Math.max(min, limit));
        }
        if (desired > current) {
            ups.add(new Event(nowMillis, desired - current));
        } else if (desired < current) {
            downs.add(new Event(nowMillis, current - desired));
        }
        return desired;
    }

    private int limit(long now, int current, Rules rules, boolean scaleUp) {
        int result = rules.selectMax() == scaleUp ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Policy p : rules.policies()) {
            int periodStart = current - changed(ups, now, p.periodSeconds()) + changed(downs, now, p.periodSeconds());
            int proposed = switch (p.type()) {
                case PODS -> scaleUp ? periodStart + p.value() : periodStart - p.value();
                case PERCENT -> scaleUp ? (int) Math.ceil(periodStart * (1 + p.value() / 100.0)) : (int) (periodStart * (1 - p.value() / 100.0));
            };
            result = rules.selectMax() == scaleUp ? Math.max(result, proposed) : Math.min(result, proposed);
        }
        return result;
    }

    private static int changed(List<Event> events, long now, int periodSeconds) {
        return events.stream().filter(e -> e.atMillis() > now - periodSeconds * 1000L).mapToInt(Event::change).sum();
    }
}
