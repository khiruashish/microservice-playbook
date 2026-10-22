package dev.foodfleet.ch08.eta.api;

import dev.foodfleet.ch08.eta.client.KitchenUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details for the one failure clients must handle. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(KitchenUnavailableException.class)
    ResponseEntity<ProblemDetail> kitchenUnavailable(KitchenUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The restaurant's kitchen is not reporting a preparation time right now.");
        problem.setTitle("ETA temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem);
    }
}
