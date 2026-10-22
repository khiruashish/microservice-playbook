package dev.foodfleet.ch76.platform;

/**
 * List prices, AWS US East (N. Virginia), x86, on demand, as published in October 2026; free tiers ignored.
 * Lambda bills duration in 1 ms steps, including the INIT phase of cold starts (since 1 August 2025), at a memory size
 * of 1,769 MB, where a function gets one vCPU. Fargate bills per second with a one-minute minimum.
 */
public final class Prices {

    public static final double LAMBDA_GB_SECOND = 0.0000166667;
    public static final double LAMBDA_PER_MILLION = 0.20;
    public static final double LAMBDA_MEMORY_GB = 1_769 / 1_024.0;
    public static final double HTTP_API_PER_MILLION = 1.00;
    public static final double FARGATE_VCPU_SECOND = 0.000011244;
    public static final double FARGATE_GB_SECOND = 0.000001235;
    /** A container instance of 1 vCPU and 2 GB, per second. */
    public static final double INSTANCE_SECOND = FARGATE_VCPU_SECOND + 2 * FARGATE_GB_SECOND;

    private Prices() {
    }

    /**
     * One Lambda invocation billed for {@code millis} (rounded up to the next millisecond). A front door (an HTTP API at
     * {@link #HTTP_API_PER_MILLION}, or a load balancer) costs the same for any platform and isn't included.
     */
    public static double lambda(double millis) {
        return Math.ceil(millis) / 1_000 * LAMBDA_MEMORY_GB * LAMBDA_GB_SECOND + LAMBDA_PER_MILLION / 1e6;
    }
}
