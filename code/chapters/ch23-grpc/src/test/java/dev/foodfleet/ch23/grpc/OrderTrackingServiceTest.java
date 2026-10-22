package dev.foodfleet.ch23.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Timestamp;
import dev.foodfleet.ch23.grpc.proto.GeoPoint;
import dev.foodfleet.ch23.grpc.proto.GetStatusRequest;
import dev.foodfleet.ch23.grpc.proto.OrderStatus;
import dev.foodfleet.ch23.grpc.proto.OrderTrackingGrpc;
import dev.foodfleet.ch23.grpc.proto.Stage;
import dev.foodfleet.ch23.grpc.proto.TrackingEvent;
import dev.foodfleet.ch23.grpc.proto.WatchOrderRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real service and the real generated stubs, over Spring Boot's in-process test transport:
 * every call goes through gRPC (serialisation, status codes, deadlines, cancellation), no port.
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
class OrderTrackingServiceTest {

    @Autowired
    OrderTrackingGrpc.OrderTrackingBlockingStub stub;

    @Autowired
    TrackingClient client;

    @Autowired
    TrackingStore store;

    @BeforeEach
    void anOrderOnItsWay() {
        store.update(status("o-42", Stage.PICKED_UP, 17));
    }

    @Test
    void getStatusReturnsTheCurrentStatus() {
        OrderStatus status = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .getStatus(GetStatusRequest.newBuilder().setOrderId("o-42").build());

        assertThat(status.getStage()).isEqualTo(Stage.PICKED_UP);
        assertThat(status.getEtaMinutes()).isEqualTo(17);
    }

    @Test
    void unknownOrdersAreNotFoundAndBadRequestsInvalidArgument() {
        assertThatThrownBy(() -> stub.getStatus(GetStatusRequest.newBuilder().setOrderId("o-missing").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> stub.getStatus(GetStatusRequest.newBuilder().build()))
                .hasMessageContaining("INVALID_ARGUMENT");
        assertThat(client.status("o-missing", Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void watchOrderStreamsChangesUntilDelivered() throws Exception {
        Iterator<TrackingEvent> events = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .watchOrder(WatchOrderRequest.newBuilder().setOrderId("o-42").build());
        List<TrackingEvent> received = new ArrayList<>();
        received.add(events.next());                                   // current status first
        awaitListeners("o-42", 1);

        store.riderMoved("o-42", GeoPoint.newBuilder().setLatitude(12.9352).setLongitude(77.6245).build(), now());
        store.update(status("o-42", Stage.DELIVERED, 0));
        events.forEachRemaining(received::add);                        // ends when the server completes

        assertThat(received.size()).isEqualTo(3);
        assertThat(received.get(0).getStatus().getStage()).isEqualTo(Stage.PICKED_UP);
        assertThat(received.get(1).hasRiderLocation()).isTrue();
        assertThat(received.get(2).getStatus().getStage()).isEqualTo(Stage.DELIVERED);
        awaitListeners("o-42", 0);
    }

    @Test
    void whenTheClientsDeadlinePassesTheServerStopsListening() throws Exception {
        Iterator<TrackingEvent> events = stub.withDeadlineAfter(300, TimeUnit.MILLISECONDS)
                .watchOrder(WatchOrderRequest.newBuilder().setOrderId("o-42").build());
        events.next();
        awaitListeners("o-42", 1);

        assertThatThrownBy(() -> events.forEachRemaining(e -> { }))     // the order never arrives in time
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining(Status.Code.DEADLINE_EXCEEDED.name());
        awaitListeners("o-42", 0);                                     // the cancellation reached the server
    }

    private void awaitListeners(String orderId, int expected) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (store.listenerCount(orderId) != expected && System.nanoTime() < until) {
            Thread.sleep(10);
        }
        assertThat(store.listenerCount(orderId)).isEqualTo(expected);
    }

    private static OrderStatus status(String orderId, Stage stage, int eta) {
        return OrderStatus.newBuilder().setOrderId(orderId).setStage(stage).setEtaMinutes(eta)
                .setRestaurantName("Meghana Foods, Koramangala").setUpdatedAt(now()).build();
    }

    private static Timestamp now() {
        long ms = System.currentTimeMillis();
        return Timestamp.newBuilder().setSeconds(ms / 1000).setNanos((int) (ms % 1000) * 1_000_000).build();
    }
}
