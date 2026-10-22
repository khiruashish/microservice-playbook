package dev.foodfleet.ch02.menu.pricing;

import dev.foodfleet.ch02.menu.pricing.PricingClient.SurgeResponse;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Design for failure, layer 1: make the remote call itself well-behaved.
 * <ul>
 *   <li><b>Timeouts</b> come from {@code spring.http.serviceclient.pricing.*} (300 ms read).</li>
 *   <li><b>Retry only what is transient and safe:</b> I/O errors and 5xx on an idempotent GET.
 *       A 4xx is our bug or a real "no", and retrying it only adds load.</li>
 *   <li><b>Exponential backoff with jitter</b> so a fleet of menu-service pods doesn't retry in
 *       lock-step and hammer a recovering pricing-service (a retry storm).</li>
 *   <li><b>@ConcurrencyLimit</b> caps in-flight calls. With virtual threads there is no thread
 *       pool to act as an accidental bulkhead, so the limit has to be explicit. Excess callers wait;
 *       Resilience4j's bulkhead (chapter 43) adds fail-fast rejection.</li>
 * </ul>
 * Worst case: 3 attempts × 300 ms + 100 ms + 200 ms of backoff ≈ 1.2 s. Chapter 44 shows how to fit
 * that inside the caller's deadline budget.
 */
@Component
class RemoteSurgeLookup implements SurgeLookup {

    static final int MIN_BPS = 10_000; // 1.0×: surge never lowers prices
    static final int MAX_BPS = 30_000; // 3.0×: business cap

    private final PricingClient client;

    RemoteSurgeLookup(PricingClient client) {
        this.client = client;
    }

    @Override
    @Retryable(includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetries = 2, delay = 100, multiplier = 2, jitter = 20)
    @ConcurrencyLimit(20)
    public int multiplierBps(String zone) {
        SurgeResponse response = client.currentSurge(zone);
        if (response == null || response.multiplierBps() < MIN_BPS || response.multiplierBps() > MAX_BPS) {
            // Defensive consumer: never let a bad upstream value reach a customer's bill.
            throw new InvalidSurgeException(zone, response == null ? null : response.multiplierBps());
        }
        return response.multiplierBps();
    }
}
