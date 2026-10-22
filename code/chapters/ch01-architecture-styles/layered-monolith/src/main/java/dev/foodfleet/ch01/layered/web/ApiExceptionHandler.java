package dev.foodfleet.ch01.layered.web;

import dev.foodfleet.ch01.layered.service.KitchenRejectedException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain failures to RFC 9457 Problem Details responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(KitchenRejectedException.class)
    ProblemDetail kitchenRejected(KitchenRejectedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://foodfleet.dev/problems/restaurant-unavailable"));
        problem.setTitle("Restaurant unavailable");
        problem.setProperty("restaurantId", ex.restaurantId());
        return problem;
    }
}
