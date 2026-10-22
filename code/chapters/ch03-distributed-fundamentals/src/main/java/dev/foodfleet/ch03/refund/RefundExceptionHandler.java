package dev.foodfleet.ch03.refund;

import dev.foodfleet.ch03.idempotency.IdempotencyKeyReusedException;
import dev.foodfleet.ch03.idempotency.RequestInProgressException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RefundExceptionHandler {

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://foodfleet.dev/problems/idempotency-key-reused"));
        problem.setTitle("Idempotency-Key reused with a different request");
        return problem;
    }

    @ExceptionHandler(RequestInProgressException.class)
    ResponseEntity<ProblemDetail> inProgress(RequestInProgressException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://foodfleet.dev/problems/request-in-progress"));
        problem.setTitle("Request still in progress");
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1").body(problem);
    }
}
