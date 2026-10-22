package dev.foodfleet.ch23.grpc;

import dev.foodfleet.ch23.grpc.proto.GetStatusRequest;
import dev.foodfleet.ch23.grpc.proto.OrderStatus;
import dev.foodfleet.ch23.grpc.proto.OrderTrackingGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * How a caller (the mobile BFF) uses the generated blocking stub: a deadline on every call, and
 * status codes mapped to what the caller can do about them. If this runs inside a gRPC request of
 * its own, the incoming deadline also applies: gRPC takes the shorter of the two.
 */
@Component
public class TrackingClient {

    private final OrderTrackingGrpc.OrderTrackingBlockingStub stub;

    public TrackingClient(OrderTrackingGrpc.OrderTrackingBlockingStub stub) {
        this.stub = stub;
    }

    public Optional<OrderStatus> status(String orderId, Duration deadline) {
        try {
            return Optional.of(stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .getStatus(GetStatusRequest.newBuilder().setOrderId(orderId).build()));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;     // DEADLINE_EXCEEDED, UNAVAILABLE, ...: the caller's resilience policy decides
        }
    }
}
