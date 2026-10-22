package dev.foodfleet.ch18.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * The outer layers: a circuit breaker (Spring Cloud CircuitBreaker on Resilience4j, configured as
 * {@code resilience4j.circuitbreaker.instances.maps}) and a fallback to the last ETA we computed for
 * the order, marked approximate. The app shows "about 25 min" instead of an error.
 */
@Service
public class EtaService {

    public record Eta(int minutes, boolean approximate) {
    }

    private final MapsGateway maps;
    private final CircuitBreaker breaker;
    private final Map<String, Eta> lastKnown = new ConcurrentHashMap<>();

    public EtaService(MapsGateway maps, CircuitBreakerFactory<?, ?> breakers) {
        this.maps = maps;
        this.breaker = breakers.create("maps");
    }

    public Eta eta(String orderId, String riderLocation, String customerLocation) {
        return breaker.run(() -> {
            Eta fresh = new Eta(Math.ceilDiv(maps.route(riderLocation, customerLocation).durationSeconds(), 60), false);
            lastKnown.put(orderId, fresh);
            return fresh;
        }, failure -> {
            Eta previous = lastKnown.get(orderId);
            return previous != null ? new Eta(previous.minutes(), true) : new Eta(30, true);   // typical delivery
        });
    }
}
