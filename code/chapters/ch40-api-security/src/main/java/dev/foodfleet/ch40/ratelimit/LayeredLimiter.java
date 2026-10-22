package dev.foodfleet.ch40.ratelimit;

import java.util.List;
import java.util.function.Function;

/**
 * Several limits that must all pass, each keyed differently: per client within a tenant, then per tenant, so one
 * restaurant chain's runaway integration uses up its own tenant's share and not the platform's. A request denied
 * by a later layer has still used a slot in the earlier ones; with the per-client layer first that costs only the
 * offending client, which is the one that should pay.
 */
public final class LayeredLimiter {

    public record Layer(RateLimiter limiter, Function<Caller, String> key) {
    }

    /** Who is calling: the tenant from the verified token, the client (OAuth client id or user), the address. */
    public record Caller(String tenant, String client, String address) {
    }

    public record Outcome(RateLimiter.Decision decision, RateLimiter.Policy policy) {
    }

    private final List<Layer> layers;

    public LayeredLimiter(List<Layer> layers) {
        this.layers = List.copyOf(layers);
    }

    /** The first denial, or the allowance of the layer with the least left (what the client should be told). */
    public Outcome tryAcquire(Caller caller, long nowNanos) {
        Outcome tightest = null;
        for (Layer layer : layers) {
            RateLimiter.Decision d = layer.limiter().tryAcquire(layer.key().apply(caller), nowNanos);
            if (!d.allowed()) {
                return new Outcome(d, layer.limiter().policy());
            }
            if (tightest == null || d.remaining() < tightest.decision().remaining()) {
                tightest = new Outcome(d, layer.limiter().policy());
            }
        }
        return tightest;
    }
}
