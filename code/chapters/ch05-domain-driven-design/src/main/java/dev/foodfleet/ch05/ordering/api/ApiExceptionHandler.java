package dev.foodfleet.ch05.ordering.api;

import dev.foodfleet.ch05.ordering.application.OrderNotFoundException;
import dev.foodfleet.ch05.ordering.application.UpstreamContractException;
import dev.foodfleet.ch05.ordering.domain.DomainException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Each failure kind gets its own status code:
 * a broken business rule (422), an invalid value (400), a missing order (404), a broken upstream contract (502).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail businessRule(DomainException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violated", "Order rule violated", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidValue(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-value", "Invalid value", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "order-not-found", "Order not found", ex.getMessage());
    }

    @ExceptionHandler(UpstreamContractException.class)
    ProblemDetail upstreamContract(UpstreamContractException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "catalog-contract-broken", "Catalog returned invalid data", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://foodfleet.dev/problems/" + type));
        problem.setTitle(title);
        return problem;
    }
}
