package dev.foodfleet.ch29.riders;

import java.time.Instant;

/** One position report from a rider's phone. */
public record Ping(long riderId, double lat, double lon, float speedKmh, Instant recordedAt) {

    public Ping {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("not a position: " + lat + ", " + lon);
        }
    }
}
