package dev.foodfleet.ch29.riders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

/**
 * Where every rider is now: a Redis geo set (member = rider id) plus a sorted set of when each was last
 * heard from. Only the latest position matters and losing a second of it is harmless (the next ping
 * arrives in four), so Redis's in-memory writes fit: 90,000-95,000 updates/s with an fsync every second
 * against PostgreSQL's 16,800 with synchronous_commit off, measured in the chapter.
 */
@Component
public class RiderLocations {

    public record NearbyRider(long riderId, double distanceKm) {
    }

    // One hash tag, so both keys live in the same Redis Cluster slot and one script may touch both.
    static final String LIVE = "{riders}:live";
    static final String SEEN = "{riders}:seen";

    // Position and last-seen together in one round trip, atomically.
    static final RedisScript<Long> UPDATE = RedisScript.of("""
            redis.call('GEOADD', KEYS[1], ARGV[1], ARGV[2], ARGV[3])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[3])
            return 1""", Long.class);

    // Riders not heard from since the cut-off leave both sets; a rider who pings again simply comes back.
    static final RedisScript<Long> PRUNE = RedisScript.of("""
            local stale = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, 1000)
            if #stale == 0 then return 0 end
            redis.call('ZREM', KEYS[2], unpack(stale))
            redis.call('ZREM', KEYS[1], unpack(stale))
            return #stale""", Long.class);

    private final StringRedisTemplate redis;

    public RiderLocations(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void update(Ping ping) {
        redis.execute(UPDATE, List.of(LIVE, SEEN), Double.toString(ping.lon()), Double.toString(ping.lat()),
                Long.toString(ping.riderId()), Long.toString(ping.recordedAt().getEpochSecond()));
    }

    /** Nearest riders first. Redis sorts every rider inside the circle, so keep the radius tight. */
    public List<NearbyRider> nearby(double lat, double lon, double radiusKm, int count) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(LIVE,
                GeoReference.fromCoordinate(lon, lat), new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(count));
        if (results == null) {
            return List.of();
        }
        return results.getContent().stream()
                .map(r -> new NearbyRider(Long.parseLong(r.getContent().getName()), r.getDistance().getValue()))
                .toList();
    }

    /** Removes riders not heard from for {@code maxSilence}; returns how many (at most 1,000 per call). */
    public long pruneSilent(Duration maxSilence, Instant now) {
        Long removed = redis.execute(PRUNE, List.of(LIVE, SEEN), Long.toString(now.minus(maxSilence).getEpochSecond()));
        return removed == null ? 0 : removed;
    }
}
