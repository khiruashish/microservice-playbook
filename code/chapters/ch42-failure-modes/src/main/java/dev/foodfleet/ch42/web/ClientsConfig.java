package dev.foodfleet.ch42.web;

import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

/**
 * order-service's edges: a deadline on every incoming request, and a client per downstream service that passes the
 * deadline on and retries within a budget. Timeouts come from {@code spring.http.clients.*} (order-service.yml):
 * without them the JDK client waits forever.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class ClientsConfig {

    @Bean
    FilterRegistrationBean<DeadlineFilter> deadlineFilter() {
        FilterRegistrationBean<DeadlineFilter> r = new FilterRegistrationBean<>(new DeadlineFilter(Duration.ofSeconds(2)));
        r.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return r;
    }

    @Bean
    RestClient restaurantClient(RestClient.Builder builder) {
        return builder.baseUrl("http://restaurant-service.restaurants.svc:8080")
                .requestInterceptor(new DeadlinePropagation())
                .requestInterceptor(new BudgetedRetry(0.1, 3, Duration.ofMillis(50), Duration.ofMillis(500)))
                .build();
    }
}
