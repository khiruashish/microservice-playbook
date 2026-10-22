package dev.foodfleet.ch23.grpc;

import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A server interceptor applied to every service: logs method, status, duration and the deadline
 * the caller gave us. Missing deadlines are worth seeing: they are calls that could wait forever.
 * Registered for every service by {@link GrpcServerConfig}.
 */
class RequestLoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        long start = System.nanoTime();
        Deadline deadline = Context.current().getDeadline();
        String budget = deadline == null ? "none" : deadline.timeRemaining(TimeUnit.MILLISECONDS) + "ms";
        ServerCall<Q, R> logging = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                log.info("grpc method={} status={} durationMs={} deadline={}", call.getMethodDescriptor().getFullMethodName(),
                        status.getCode(), (System.nanoTime() - start) / 1_000_000, budget);
                super.close(status, trailers);
            }
        };
        return next.startCall(logging, headers);
    }
}
