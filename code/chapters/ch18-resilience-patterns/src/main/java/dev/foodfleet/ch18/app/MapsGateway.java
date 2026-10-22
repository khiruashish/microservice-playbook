package dev.foodfleet.ch18.app;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The maps call with the inner layers of protection, all from Spring Framework 7:
 * <ul>
 *   <li><b>timeout</b>: {@code spring.http.serviceclient.maps.read-timeout} (300 ms), on the client;</li>
 *   <li><b>retry</b>: {@code @Retryable}, one retry after 100 ms ± 50 ms of jitter, only for I/O errors
 *       (timeouts included) and 5xx. A 4xx is an answer, not a failure;</li>
 *   <li><b>bulkhead</b>: {@code @ConcurrencyLimit(20)}. With virtual threads nothing else limits how many
 *       requests can be waiting on maps at once, so this is what keeps a slow provider from holding
 *       every request in tracking-service.</li>
 * </ul>
 * Note {@code @ConcurrencyLimit} makes callers wait for a permit rather than reject them; the circuit
 * breaker around this call (in {@link EtaService}) is what turns a long outage into fast failures.
 */
@Component
public class MapsGateway {

    private final MapsClient maps;

    public MapsGateway(MapsClient maps) {
        this.maps = maps;
    }

    @ConcurrencyLimit(20)
    @Retryable(includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetries = 1, delay = 100, jitter = 50)
    public MapsClient.Route route(String from, String to) {
        return maps.route(from, to);
    }
}
