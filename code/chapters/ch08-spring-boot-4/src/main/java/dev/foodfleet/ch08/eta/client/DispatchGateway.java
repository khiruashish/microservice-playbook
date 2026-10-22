package dev.foodfleet.ch08.eta.client;

import dev.foodfleet.ch08.eta.domain.EtaCalculator.RiderLeg;
import java.util.Optional;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Calls dispatch with Spring Framework 7's built-in resilience annotations (no extra library):
 *
 * <ul>
 *   <li>{@code @Retryable}: 2 retries after the first attempt, 50 ms then 100 ms, only for errors
 *       that can be transient (I/O errors, timeouts, 5xx). A 4xx is an answer, not a failure.</li>
 *   <li>{@code @ConcurrencyLimit}: at most 50 in-flight calls. With virtual threads, nothing else
 *       limits concurrency, so a slow dispatch could otherwise collect thousands of waiting calls.</li>
 * </ul>
 *
 * Both need {@code @EnableResilientMethods} (see {@code ClientConfiguration}).
 */
@Component
public class DispatchGateway {

    private final DispatchClient client;

    DispatchGateway(DispatchClient client) {
        this.client = client;
    }

    @Retryable(includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetries = 2, delay = 50, multiplier = 2)
    @ConcurrencyLimit(50)
    public Optional<RiderLeg> riderFor(String orderId) {
        try {
            DispatchClient.RiderEstimate estimate = client.rider(orderId);
            return Optional.of(new RiderLeg(estimate.riderId(), estimate.minutesToRestaurant(), estimate.minutesToCustomer()));
        } catch (HttpClientErrorException.NotFound noRiderYet) {
            return Optional.empty();
        }
    }
}
