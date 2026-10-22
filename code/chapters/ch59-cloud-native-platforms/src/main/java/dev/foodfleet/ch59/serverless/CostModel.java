package dev.foodfleet.ch59.serverless;

/**
 * List prices, as published for AWS US East (N. Virginia) on 2026-10-15, and what a day of traffic costs on a
 * function platform against always-on containers. Other regions and clouds differ by region and by tens of percent;
 * the shape of the comparison is what carries over.
 */
public final class CostModel {

    /** Lambda, x86: per GB-second and per request. */
    public static final double LAMBDA_GB_SECOND = 0.0000166667;
    public static final double LAMBDA_REQUEST = 0.20 / 1_000_000;
    /** Fargate, Linux x86: per vCPU-second and per GB-second. */
    public static final double FARGATE_VCPU_SECOND = 0.000011244;
    public static final double FARGATE_GB_SECOND = 0.000001235;

    private CostModel() {
    }

    /** A day on Lambda: billed for each instance's busy time (including its cold starts) at {@code memoryGb}, plus requests. */
    public static double lambdaDay(FunctionPool.Result r, double memoryGb) {
        return r.busySeconds() * memoryGb * LAMBDA_GB_SECOND + r.requests() * LAMBDA_REQUEST;
    }

    /** A day of {@code tasks} always-on Fargate tasks of the given size. */
    public static double fargateDay(int tasks, double vcpu, double memoryGb) {
        return tasks * 86_400 * (vcpu * FARGATE_VCPU_SECOND + memoryGb * FARGATE_GB_SECOND);
    }

    /** A day of Fargate tasks following the load: {@code taskSecondsPerDay} summed over the day. */
    public static double fargateScaled(double taskSecondsPerDay, double vcpu, double memoryGb) {
        return taskSecondsPerDay * (vcpu * FARGATE_VCPU_SECOND + memoryGb * FARGATE_GB_SECOND);
    }
}
