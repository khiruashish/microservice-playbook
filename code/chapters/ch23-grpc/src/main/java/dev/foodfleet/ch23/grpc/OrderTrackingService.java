package dev.foodfleet.ch23.grpc;

import dev.foodfleet.ch23.grpc.proto.GetStatusRequest;
import dev.foodfleet.ch23.grpc.proto.OrderStatus;
import dev.foodfleet.ch23.grpc.proto.OrderTrackingGrpc;
import dev.foodfleet.ch23.grpc.proto.Stage;
import dev.foodfleet.ch23.grpc.proto.TrackingEvent;
import dev.foodfleet.ch23.grpc.proto.WatchOrderRequest;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.grpc.server.service.GrpcService;

/**
 * The OrderTracking service. Unary GetStatus, and server-streaming WatchOrder that sends the
 * current status, then every change, until the order is delivered or the client goes away.
 */
@GrpcService
public class OrderTrackingService extends OrderTrackingGrpc.OrderTrackingImplBase {

    private final TrackingStore store;

    public OrderTrackingService(TrackingStore store) {
        this.store = store;
    }

    @Override
    public void getStatus(GetStatusRequest request, StreamObserver<OrderStatus> response) {
        if (request.getOrderId().isBlank()) {
            response.onError(Status.INVALID_ARGUMENT.withDescription("order_id is required").asRuntimeException());
            return;
        }
        // TrackingStore.OrderNotFound becomes NOT_FOUND through the GrpcExceptionHandler in GrpcServerConfig
        response.onNext(store.status(request.getOrderId()));
        response.onCompleted();
    }

    @Override
    public void watchOrder(WatchOrderRequest request, StreamObserver<TrackingEvent> response) {
        ServerCallStreamObserver<TrackingEvent> stream = (ServerCallStreamObserver<TrackingEvent>) response;
        OrderStatus current = store.status(request.getOrderId());       // NOT_FOUND before streaming starts
        AtomicBoolean done = new AtomicBoolean();
        synchronized (stream) {                     // StreamObserver is not thread-safe; also orders events
            TrackingStore.Subscription subscription = store.subscribe(request.getOrderId(), event -> {
                synchronized (stream) {
                    if (done.get() || stream.isCancelled()) {
                        return;
                    }
                    stream.onNext(event);
                    if (event.hasStatus() && event.getStatus().getStage() == Stage.DELIVERED) {
                        done.set(true);
                        stream.onCompleted();
                    }
                }
            });
            // The app closed the screen, lost signal, or its deadline passed: stop listening.
            stream.setOnCancelHandler(subscription::close);
            stream.setOnCloseHandler(subscription::close);
            // Subscribed first, then the current status: a change in between is not lost.
            stream.onNext(TrackingEvent.newBuilder().setOrderId(current.getOrderId()).setStatus(current)
                    .setAt(current.getUpdatedAt()).build());
            if (current.getStage() == Stage.DELIVERED) {
                done.set(true);
                stream.onCompleted();
            }
        }
    }
}
