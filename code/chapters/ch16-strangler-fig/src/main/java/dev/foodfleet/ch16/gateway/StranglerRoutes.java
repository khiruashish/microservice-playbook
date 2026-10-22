package dev.foodfleet.ch16.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The migration, as routes, most specific first:
 * <ol>
 *   <li>order history: fully migrated, always the new service;</li>
 *   <li>pricing: the canary cohort goes to pricing-service...</li>
 *   <li>...everyone else's pricing still goes to the monolith;</li>
 *   <li>everything else: the monolith (the catch-all shrinks as capabilities move).</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
class StranglerRoutes {

    @Bean
    RouterFunction<ServerResponse> stranglerRoutes(StranglerProperties p) {
        return route("order-history")
                .GET("/api/orders/history/**", http())
                .before(uri(p.orderHistoryService()))
                .build()
            .and(route("pricing-new")
                .route(path("/api/pricing/**").and(new CanaryPredicate(p.pricingCanaryPercent())), http())
                .before(uri(p.pricingService()))
                .build())
            .and(route("pricing-monolith")
                .route(path("/api/pricing/**"), http())
                .before(uri(p.monolith()))
                .build())
            .and(route("monolith")
                .route(path("/**"), http())
                .before(uri(p.monolith()))
                .build());
    }
}
