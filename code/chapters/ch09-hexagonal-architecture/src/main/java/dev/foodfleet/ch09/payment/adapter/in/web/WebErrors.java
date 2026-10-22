package dev.foodfleet.ch09.payment.adapter.in.web;

import dev.foodfleet.ch09.payment.application.port.in.IdempotencyConflictException;
import dev.foodfleet.ch09.payment.application.port.in.PaymentNotFoundException;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.GatewayUnavailableException;
import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository.ConcurrentUpdateException;
import dev.foodfleet.ch09.payment.domain.DomainRuleViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One place where core exceptions become HTTP: the core never knows about status codes. */
@RestControllerAdvice
class WebErrors {

    @ExceptionHandler(DomainRuleViolation.class)
    ProblemDetail rule(DomainRuleViolation e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Payment rule violated", e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", e.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, ConcurrentUpdateException.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflicting request", e.getMessage());
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    ProblemDetail gateway(GatewayUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider unavailable",
                "Please try again; you will not be charged twice.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
