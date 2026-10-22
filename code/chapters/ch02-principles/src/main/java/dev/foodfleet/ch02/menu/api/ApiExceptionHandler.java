package dev.foodfleet.ch02.menu.api;

import dev.foodfleet.ch02.menu.browse.RestaurantNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(RestaurantNotFoundException.class)
    ProblemDetail restaurantNotFound(RestaurantNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://foodfleet.dev/problems/restaurant-not-found"));
        problem.setTitle("Restaurant not found");
        problem.setProperty("restaurantId", ex.restaurantId());
        return problem;
    }
}
