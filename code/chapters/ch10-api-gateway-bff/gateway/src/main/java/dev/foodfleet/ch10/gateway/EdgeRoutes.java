package dev.foodfleet.ch10.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * FoodFleet's public API surface, as code. One router, routes in priority order (most specific
 * first). The gateway routes and protects; it holds no business logic and calls no second service.
 */
@Configuration(proxyBeanMethods = false)
class EdgeRoutes {

    @Bean
    TokenBucketRateLimiter placeOrderLimiter(EdgeProperties properties) {
        EdgeProperties.RateLimit limit = properties.placeOrderRateLimit();
        return new TokenBucketRateLimiter(limit.capacity(), limit.refillPerMinute(), limit.maxClients(), System::nanoTime);
    }

    @Bean
    RouterFunction<ServerResponse> edgeRoutes(EdgeProperties properties, TokenBucketRateLimiter placeOrderLimiter) {
        EdgeProperties.Backends backends = properties.backends();
        return route("eta")
                .GET("/api/orders/{orderId}/eta", http())
                .before(uri(backends.eta()))
                .before(EdgeFilters.edgeHygiene())
                .build()
            .and(route("place-order")
                // Placing orders is expensive and abusable (fake orders tie up kitchens): rate-limited.
                .POST("/api/orders", http())
                .before(uri(backends.ordering()))
                .before(EdgeFilters.edgeHygiene())
                .filter(EdgeFilters.rateLimit(placeOrderLimiter, EdgeFilters::clientAddress))
                .build())
            .and(route("orders")
                .route(path("/api/orders/**"), http())
                .before(uri(backends.ordering()))
                .before(EdgeFilters.edgeHygiene())
                .build())
            .and(route("menus")
                // Reads are safe to retry; if menu-service keeps failing, answer fast from a fallback.
                .GET("/api/menus/**", http())
                .before(uri(backends.menu()))
                .before(EdgeFilters.edgeHygiene())
                .filter(retry(config -> config.setRetries(2)
                        .setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
                        .setMethods(Set.of(HttpMethod.GET))
                        .setBackoff(Duration.ofMillis(50), Duration.ofMillis(200), 2)))
                .filter(circuitBreaker(config -> config
                        .setId("menu-service")
                        .setFallbackUri("forward:/fallback/menus")
                        .setStatusCodes("500", "502", "503", "504")))
                .build())
            .and(route("payments")
                // No gateway retries for payments: POSTs are only safe to repeat with an
                // Idempotency-Key, and that is the client's and payment-service's contract (chapter 9).
                .route(path("/api/payments/**"), http())
                .before(uri(backends.payment()))
                .before(EdgeFilters.edgeHygiene())
                .build())
            .and(route("mobile-bff")
                .route(path("/bff/mobile/**"), http())
                .before(uri(backends.mobileBff()))
                .before(EdgeFilters.edgeHygiene())
                .build());
    }
}
