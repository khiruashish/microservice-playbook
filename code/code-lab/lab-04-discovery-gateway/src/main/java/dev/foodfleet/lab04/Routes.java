package dev.foodfleet.lab04;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.requestSize;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The only public entry point: routing, size limits, request ids. No business logic and no aggregation here; that's
 * the BFFs' job (ADR-010). Routes go to service ids, not hosts: "lb" picks an instance per request.
 */
@Configuration
public class Routes {

    static final String REQUEST_ID = "X-Request-Id";

    /** Gives every request an id the services log and propagate, unless a trusted caller already set one. */
    static Function<ServerRequest, ServerRequest> requestId() {
        return request -> request.headers().firstHeader(REQUEST_ID) != null ? request
                : ServerRequest.from(request).header(REQUEST_ID, UUID.randomUUID().toString()).build();
    }

    @Bean
    RouterFunction<ServerResponse> menuRoute() {
        return route("menu")
                .route(path("/api/restaurants/**"), http())
                .before(stripPrefix(1))                              // /api/restaurants/r-1/menu -> /restaurants/r-1/menu
                .before(requestId())
                .before(requestSize(DataSize.ofKilobytes(256)))      // bodies capped at the edge (ADR-040)
                .filter(lb("menu-service"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> quoteRoute() {
        return route("quotes")
                .route(path("/api/quotes"), http())
                .before(stripPrefix(1))
                .before(requestId())
                .before(requestSize(DataSize.ofKilobytes(256)))
                .filter(lb("order-service"))
                .build();
    }
}
