package dev.foodfleet.ch32;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch32.info.RestaurantInfoService;
import dev.foodfleet.ch32.menu.ItemPrice;
import dev.foodfleet.ch32.menu.ItemPriceCache;
import dev.foodfleet.ch32.menu.ItemPrices;
import dev.foodfleet.ch32.top.TopRestaurants;
import dev.foodfleet.ch32.views.ViewCounter;
import dev.foodfleet.ch32.views.ViewFlusher;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The four caches against real PostgreSQL and Redis. Skipped without Docker. */
@SpringBootTest(properties = {"views.flush-interval=1h", "top.min-reviews=2"})
@Testcontainers(disabledWithoutDocker = true)
class CachingContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired JdbcClient jdbc;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired RestaurantInfoService info;
    @Autowired ItemPrices prices;
    @Autowired ItemPriceCache priceCache;
    @Autowired TopRestaurants top;
    @Autowired ViewCounter views;
    @Autowired ViewFlusher flusher;

    @BeforeEach
    void seed() {
        redisTemplate.delete(redisTemplate.keys("*"));
        jdbc.sql("TRUNCATE restaurants, items, reviews, item_views, flushed_batches").update();
        jdbc.sql("INSERT INTO restaurants VALUES (1, 'Meghana Kitchen', '11:00', '23:00')").update();
        jdbc.sql("INSERT INTO items (item_id, price_paise) VALUES (100, 32000)").update();
        jdbc.sql("INSERT INTO reviews (restaurant_id, rating) VALUES (1, 5), (1, 4), (2, 3), (2, 3)").update();
    }

    @Test
    void cacheableReadsAreEvictedOnlyAfterTheUpdateCommits() {
        assertThat(info.get(1).closes()).isEqualTo(LocalTime.of(23, 0));
        jdbc.sql("UPDATE restaurants SET closing_time = '01:00' WHERE id = 1").update();   // behind the cache's back
        assertThat(info.get(1).closes()).isEqualTo(LocalTime.of(23, 0));                   // served from Redis

        info.changeHours(1, LocalTime.of(10, 0), LocalTime.of(22, 0));

        assertThat(info.get(1).closes()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void anOldPriceCanNeverReplaceANewerOne() {
        ItemPrice v2 = prices.changePrice(100, 34000);                 // written through after the commit
        assertThat(priceCache.get(100)).contains(v2);

        assertThat(priceCache.putIfNewer(new ItemPrice(100, 1, 32000))).isFalse();   // a slow reader's late fill
        assertThat(prices.get(100).pricePaise()).isEqualTo(34000);
    }

    @Test
    void aMissIsFilledFromTheDatabase() {
        assertThat(prices.get(100)).isEqualTo(new ItemPrice(100, 1, 32000));
        assertThat(priceCache.get(100)).contains(new ItemPrice(100, 1, 32000));
    }

    @Test
    void theExpensiveAggregationIsComputedOnceAndServedFromRedis() {
        String first = top.json();
        jdbc.sql("INSERT INTO reviews (restaurant_id, rating) VALUES (2, 5), (2, 5), (2, 5)").update();

        assertThat(top.json()).isEqualTo(first);                       // within the soft TTL
        assertThat(first).startsWith("[{\"restaurant_id\":1,\"rating\":4.50,");          // 2 reviews at 4.5 rank first
    }

    @Test
    void viewsAreFlushedOnceEvenIfAFlushIsRepeated() {
        for (int i = 0; i < 25; i++) {
            views.viewed(100);
        }
        views.viewed(101);

        assertThat(flusher.flush()).isEqualTo(2);
        views.viewed(100);
        assertThat(flusher.flush()).isEqualTo(1);
        assertThat(flusher.flush()).isZero();
        assertThat(jdbc.sql("SELECT views FROM item_views ORDER BY item_id").query(Long.class).list()).isEqualTo(List.of(26L, 1L));
    }

    @Test
    void aBatchLeftBehindByACrashedFlusherIsAppliedExactlyOnce() {
        views.viewed(100);
        views.viewed(100);
        // the flusher claimed the batch and committed it, then died before cleaning up
        redisTemplate.rename("views", "views:flushing");
        redisTemplate.opsForValue().set("views:batch", "batch-7");
        jdbc.sql("INSERT INTO flushed_batches (batch_id) VALUES ('batch-7')").update();
        jdbc.sql("INSERT INTO item_views VALUES (100, 2)").update();

        assertThat(flusher.flush()).isZero();                          // the batch is recognised and skipped

        assertThat(jdbc.sql("SELECT views FROM item_views WHERE item_id = 100").query(Long.class).single()).isEqualTo(2L);
        assertThat(redisTemplate.hasKey("views:flushing")).isFalse();
    }
}
