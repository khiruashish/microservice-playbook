package dev.foodfleet.ch23.grpc;

import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/** Server-wide concerns: one interceptor for every service, and exception-to-status mapping. */
@Configuration(proxyBeanMethods = false)
class GrpcServerConfig {

    @Bean
    @Order(100)
    @GlobalServerInterceptor
    ServerInterceptor requestLogging() {
        return new RequestLoggingInterceptor();
    }

    /**
     * Domain exceptions to gRPC status codes, in one place (the gRPC counterpart of chapter 21's
     * problem details). Returning null leaves other exceptions to the default handling (UNKNOWN).
     */
    @Bean
    GrpcExceptionHandler trackingExceptions() {
        return exception -> exception instanceof TrackingStore.OrderNotFound notFound
                ? Status.NOT_FOUND.withDescription(notFound.getMessage()).asException()
                : null;
    }
}
