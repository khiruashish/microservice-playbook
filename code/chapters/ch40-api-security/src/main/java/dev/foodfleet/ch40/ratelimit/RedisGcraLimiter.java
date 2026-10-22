package dev.foodfleet.ch40.ratelimit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The same GCRA as {@link GcraLimiter}, kept in Redis so that every gateway or service instance shares one limit
 * per client. One round trip per decision: the script ({@code ratelimit/gcra.lua}) reads and updates the client's
 * theoretical arrival time atomically, using Redis's clock rather than each instance's.
 */
public final class RedisGcraLimiter implements RateLimiter {

    /** Runs a Lua script; in the service this is {@code StringRedisTemplate.execute(RedisScript, keys, args)}. */
    @FunctionalInterface
    public interface ScriptRunner {
        List<Long> eval(String script, List<String> keys, List<String> args) throws Exception;
    }

    /** Redis could not be asked. The caller decides whether that means allow, deny or fall back. */
    public static final class Unavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unavailable(Throwable cause) {
            super("rate limit store unavailable: " + cause.getMessage(), cause);
        }
    }

    public static final String SCRIPT = load();

    private final String name;
    private final int limit;
    private final Duration window;
    private final long intervalMicros;
    private final long toleranceMicros;
    private final ScriptRunner redis;

    public RedisGcraLimiter(String name, int limit, Duration window, int burst, ScriptRunner redis) {
        if (limit < 1 || burst < 1) {
            throw new IllegalArgumentException("limit and burst must be positive");
        }
        this.name = name;
        this.limit = limit;
        this.window = window;
        this.intervalMicros = window.toNanos() / 1000 / limit;
        this.toleranceMicros = intervalMicros * (burst - 1L);
        this.redis = redis;
    }

    /** {@code nowNanos} is ignored: the script uses the Redis server's clock. */
    @Override
    public Decision tryAcquire(String key, long nowNanos) {
        List<Long> r;
        try {
            // the braces make it a Redis Cluster hash tag: all of one client's keys land on one shard
            r = redis.eval(SCRIPT, List.of("rl:" + name + ":{" + key + "}"),
                    List.of(Long.toString(intervalMicros), Long.toString(toleranceMicros)));
        } catch (Exception e) {
            throw new Unavailable(e);
        }
        Duration resetIn = Duration.ofNanos(r.get(2) * 1000);
        if (r.get(0) == 1L) {
            long remaining = (toleranceMicros - (r.get(2) - intervalMicros)) / intervalMicros;
            return Decision.allow(Math.max(0, remaining), resetIn);
        }
        return Decision.deny(Duration.ofNanos(r.get(1) * 1000), resetIn);
    }

    @Override
    public Policy policy() {
        return new Policy(name, limit, window);
    }

    private static String load() {
        try (InputStream in = RedisGcraLimiter.class.getResourceAsStream("/ratelimit/gcra.lua")) {
            if (in == null) {
                throw new IllegalStateException("ratelimit/gcra.lua not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
