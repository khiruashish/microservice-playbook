package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.validation.InvalidInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A rejected value becomes a 400 naming the field and the rule, never echoing the value back. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(InvalidInput.class)
    ProblemDetail invalid(InvalidInput e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid value");
        p.setProperty("field", e.field());
        p.setProperty("rule", e.rule());
        return p;
    }
}
