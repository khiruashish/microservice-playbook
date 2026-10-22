package dev.foodfleet.ch43.web;

import java.io.IOException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * payment-service's authorisation call, protected with Spring Framework 7's own annotations instead of Resilience4j:
 * enough for a retry and a concurrency limit, with no extra dependency. Retrying a payment is safe only because the
 * request carries an idempotency key (chapter 28) that payment-service deduplicates on.
 */
@Component
public class PaymentClient {

    private final RestClient paymentService;

    public PaymentClient(RestClient paymentService) {
        this.paymentService = paymentService;
    }

    /** I/O failures only (matched through the cause chain); 2 retries after 200–300 ms, then 200–600 ms; 2 s in all. */
    @Retryable(includes = IOException.class, maxRetries = 2, delay = 200, multiplier = 2, jitter = 100, maxDelay = 1000, timeout = 2000)
    @ConcurrencyLimit(limit = 16, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    public Authorisation authorise(PaymentRequest request) {
        return paymentService.post().uri("/authorisations")
                .header("Idempotency-Key", request.idempotencyKey())
                .body(request)
                .retrieve()
                .body(Authorisation.class);
    }

    public record PaymentRequest(String orderId, long amountPaise, String idempotencyKey) {
    }

    public record Authorisation(String orderId, String status) {
    }
}
