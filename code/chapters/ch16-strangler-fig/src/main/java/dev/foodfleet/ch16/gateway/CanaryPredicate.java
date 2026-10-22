package dev.foodfleet.ch16.gateway;

import dev.foodfleet.ch16.strangler.Cohorts;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * True for customers in the first {@code percent} cohorts: the same customers on every request, so
 * nobody flips between old and new prices mid-session. The customer id comes from the authenticated
 * token (the edge sets {@code X-Customer-Id} after validating it and strips any value a client sent;
 * chapters 10 and 36). Requests without one stay on the monolith.
 */
public record CanaryPredicate(int percent) implements RequestPredicate {

    public static final String CUSTOMER_ID = "X-Customer-Id";

    @Override
    public boolean test(ServerRequest request) {
        String customerId = request.headers().firstHeader(CUSTOMER_ID);
        return customerId != null && !customerId.isBlank() && Cohorts.inRollout(customerId, percent);
    }
}
