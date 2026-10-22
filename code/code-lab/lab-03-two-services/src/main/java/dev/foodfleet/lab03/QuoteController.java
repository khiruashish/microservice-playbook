package dev.foodfleet.lab03;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    public record QuoteRequest(@NotBlank String restaurantId, @NotEmpty List<@Valid LineRequest> lines) {
    }

    public record LineRequest(@NotBlank String itemId, @Positive @Max(50) int quantity) {
    }

    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/quotes")
    public QuoteService.Quote quote(@Valid @RequestBody QuoteRequest request) {
        return quotes.quote(request.restaurantId(),
                request.lines().stream().map(l -> new QuoteService.Line(l.itemId(), l.quantity())).toList());
    }

    @ExceptionHandler
    ProblemDetail notFound(QuoteService.RestaurantNotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unknownItems(QuoteService.UnknownItems e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unavailable(QuoteService.MenuUnavailable e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("retryable", true);
        return problem;
    }
}
