package dev.foodfleet.ch23.grpc;

import dev.foodfleet.ch23.grpc.proto.OrderTrackingGrpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * tracking-service, serving the OrderTracking gRPC API (port 9090 by default). It also imports a
 * blocking stub for the same API on the "tracking" channel, as another service (the mobile BFF)
 * would, so the example shows both sides.
 */
@SpringBootApplication
@ImportGrpcClients(target = "tracking", types = OrderTrackingGrpc.OrderTrackingBlockingStub.class)
public class TrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingApplication.class, args);
    }
}
