package dev.foodfleet.ch01.modulith.order.internal;

import dev.foodfleet.ch01.modulith.order.KitchenUnavailableException;
import dev.foodfleet.ch01.modulith.order.RestaurantUnavailableException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Note the two different failures. "Restaurant said no" is a business outcome (409).
 * "We couldn't ask" is an infrastructure outcome (503, retryable). In-process, only the
 * first one can happen. Over the network, both can.
 */
@RestControllerAdvice
class OrderApiExceptionHandler {

    @ExceptionHandler(RestaurantUnavailableException.class)
    ProblemDetail restaurantUnavailable(RestaurantUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://foodfleet.dev/problems/restaurant-unavailable"));
        problem.setTitle("Restaurant unavailable");
        problem.setProperty("restaurantId", ex.restaurantId());
        return problem;
    }

    @ExceptionHandler(KitchenUnavailableException.class)
    ResponseEntity<ProblemDetail> kitchenUnavailable(KitchenUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "We could not confirm the restaurant's availability. Please retry shortly.");
        problem.setType(URI.create("https://foodfleet.dev/problems/dependency-unavailable"));
        problem.setTitle("Kitchen service unavailable");
        problem.setProperty("restaurantId", ex.restaurantId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem);
    }
}
