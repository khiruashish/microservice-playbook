package dev.foodfleet.ch56.hpa;

import java.util.List;

/**
 * The HorizontalPodAutoscaler's replica count for a CPU utilization target, following Kubernetes 1.37's
 * {@code replica_calculator.go}. Utilization is usage over <em>requests</em>, averaged over the pods with a usable sample.
 * Pods that aren't ready, or whose sample may include their startup, don't count on a scale-up; pods without a sample
 * count as idle on a scale-up and as fully busy on a scale-down, so missing data never makes the HPA more aggressive.
 */
public final class ReplicaCalculator {

    /** How the HPA treats a pod's CPU sample. */
    public enum Sample {
        /** Ready and past its startup: counted. */
        USABLE,
        /** Pending, not ready, or ready so recently that the sample may cover its startup: ignored, and 0 on a scale-up. */
        UNREADY,
        /** No sample from metrics-server yet: 0 on a scale-up, 100% of the request on a scale-down. */
        MISSING
    }

    public record Pod(Sample sample, long usageMillicores, long requestMillicores) {
    }

    public record Result(int replicas, int utilizationPercent) {
    }

    private ReplicaCalculator() {
    }

    /**
     * @param tolerance the HPA ignores ratios within 1 ± tolerance (0.1 by default)
     */
    public static Result desired(int currentReplicas, int targetUtilizationPercent, double tolerance, List<Pod> pods) {
        long usage = 0;
        long requests = 0;
        int usable = 0;
        int unready = 0;
        int missing = 0;
        for (Pod p : pods) {
            switch (p.sample()) {
                case USABLE -> {
                    usage += p.usageMillicores();
                    requests += p.requestMillicores();
                    usable++;
                }
                case UNREADY -> unready++;
                case MISSING -> missing++;
            }
        }
        if (usable == 0) {
            throw new IllegalStateException("no usable metrics (pods might be unready)");
        }
        int utilization = (int) (usage * 100 / requests);
        double ratio = (double) utilization / targetUtilizationPercent;
        boolean scaleUpWithUnready = unready > 0 && ratio > 1.0;
        if (!scaleUpWithUnready && missing == 0) {
            if (within(ratio, tolerance)) {
                return new Result(currentReplicas, utilization);
            }
            return new Result((int) Math.ceil(ratio * usable), utilization);
        }
        // fill in the pods without a usable sample, pessimistically for the direction of the change
        long newUsage = usage;
        long newRequests = requests;
        int counted = usable;
        for (Pod p : pods) {
            if (p.sample() == Sample.MISSING && ratio != 1.0) {
                newUsage += ratio < 1.0 ? p.requestMillicores() * Math.max(100, targetUtilizationPercent) / 100 : 0;
                newRequests += p.requestMillicores();
                counted++;
            } else if (p.sample() == Sample.UNREADY && scaleUpWithUnready) {
                newRequests += p.requestMillicores();
                counted++;
            }
        }
        double newRatio = (double) (newUsage * 100 / newRequests) / targetUtilizationPercent;
        if (within(newRatio, tolerance) || (ratio < 1.0 && newRatio > 1.0) || (ratio > 1.0 && newRatio < 1.0)) {
            return new Result(currentReplicas, utilization);
        }
        int replicas = (int) Math.ceil(newRatio * counted);
        if ((newRatio < 1.0 && replicas > currentReplicas) || (newRatio > 1.0 && replicas < currentReplicas)) {
            return new Result(currentReplicas, utilization);
        }
        return new Result(replicas, utilization);
    }

    static boolean within(double ratio, double tolerance) {
        return 1.0 - tolerance <= ratio && ratio <= 1.0 + tolerance;
    }
}
