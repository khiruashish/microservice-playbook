package dev.foodfleet.lab01;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain errors as RFC 9457 problem documents with a documented type URI (ADR-021). Framework errors (validation,
 * unsupported methods, unreadable bodies) become problem documents through spring.mvc.problemdetails.enabled.
 */
@RestControllerAdvice
public class ApiErrors {

    static final URI RESTAURANT_NOT_FOUND = URI.create("https://docs.foodfleet.example/problems/restaurant-not-found");

    @ExceptionHandler
    ProblemDetail restaurantNotFound(RestaurantNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setType(RESTAURANT_NOT_FOUND);
        problem.setTitle("Restaurant not found");
        problem.setProperty("restaurantId", e.restaurantId());
        return problem;
    }
}
