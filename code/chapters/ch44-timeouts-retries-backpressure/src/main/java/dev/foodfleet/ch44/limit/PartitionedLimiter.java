package dev.foodfleet.ch44.limit;

import com.netflix.concurrency.limits.limiter.AbstractPartitionedLimiter;

/**
 * Netflix concurrency-limits' partitioned limiter (the library ships the abstract class and builds concrete ones in
 * its servlet and gRPC modules). Each partition is guaranteed its share of the adaptive limit when the service is
 * at the limit; below it, any partition may use spare capacity.
 */
public final class PartitionedLimiter<C> extends AbstractPartitionedLimiter<C> {

    private PartitionedLimiter(Builder<C> builder) {
        super(builder);
    }

    public static <C> Builder<C> newBuilder() {
        return new Builder<>();
    }

    public static final class Builder<C> extends AbstractPartitionedLimiter.Builder<Builder<C>, C> {

        @Override
        protected Builder<C> self() {
            return this;
        }

        public PartitionedLimiter<C> build() {
            return new PartitionedLimiter<>(this);
        }
    }
}
