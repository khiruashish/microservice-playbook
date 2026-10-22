package dev.foodfleet.ch32.top;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * "Top rated near you": an aggregation over every recent review (~120-190 ms on 1 M reviews, seconds when many
 * run at once). Shown on the home screen, so it is requested constantly; a few seconds of staleness is fine.
 */
@Service
public class TopRestaurants {

    static final String TOP = """
            SELECT coalesce(json_agg(t)::text, '[]') FROM (
              SELECT restaurant_id, round(avg(rating), 2) AS rating, count(*) AS reviews FROM reviews
               WHERE created_at > now() - interval '90 days'
               GROUP BY restaurant_id HAVING count(*) >= :minReviews
               ORDER BY rating DESC, restaurant_id LIMIT 20) t""";

    private final JdbcClient jdbc;
    private final SoftTtlCache cache;
    private final int minReviews;

    public TopRestaurants(JdbcClient jdbc, CacheStore store,
                          @Value("${cache.top-restaurants.soft-ttl:30s}") Duration softTtl,
                          @Value("${cache.top-restaurants.hard-ttl:1h}") Duration hardTtl,
                          @Value("${top.min-reviews:200}") int minReviews) {
        this.jdbc = jdbc;
        this.minReviews = minReviews;
        this.cache = new SoftTtlCache(store, Clock.systemUTC(), Executors.newVirtualThreadPerTaskExecutor(), softTtl, hardTtl,
                Duration.ofSeconds(10));
    }

    /** JSON, ready to send. */
    public String json() {
        return cache.get("top-restaurants", () -> jdbc.sql(TOP).param("minReviews", minReviews).query(String.class).single());
    }
}
