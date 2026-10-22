package dev.foodfleet.ch29;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch29.catalog.CatalogService;
import dev.foodfleet.ch29.catalog.DishSearch;
import dev.foodfleet.ch29.catalog.ItemChange;
import dev.foodfleet.ch29.catalog.MenuCards;
import dev.foodfleet.ch29.projection.PriceCache;
import dev.foodfleet.ch29.projection.PriceProjector;
import dev.foodfleet.ch29.riders.PartitionMaintenance;
import dev.foodfleet.ch29.riders.Ping;
import dev.foodfleet.ch29.riders.PositionHistory;
import dev.foodfleet.ch29.riders.RiderLocations;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

/**
 * Both services against real PostgreSQL and Redis. Scheduled jobs are slowed down so each test drives
 * the projector itself. Skipped automatically when Docker isn't available.
 */
@SpringBootTest(properties = {"catalog.projector.poll-interval=1h", "riders.live.prune-interval=1h",
        "riders.history.flush-interval=1h"})
@Testcontainers(disabledWithoutDocker = true)
class PolyglotContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired JdbcClient jdbc;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired CatalogService catalog;
    @Autowired MenuCards cards;
    @Autowired DishSearch search;
    @Autowired PriceProjector projector;
    @Autowired PriceCache prices;
    @Autowired RiderLocations riders;
    @Autowired PositionHistory history;
    @Autowired PartitionMaintenance partitions;

    @BeforeEach
    void seed() {
        redisTemplate.delete(redisTemplate.keys("*"));
        jdbc.sql("TRUNCATE catalog_outbox, menu_cards, options, option_groups, menu_items, menu_sections, restaurants").update();
        jdbc.sql("INSERT INTO restaurants VALUES (1, 'Meghana Kitchen', 'Bengaluru')").update();
        jdbc.sql("INSERT INTO menu_sections VALUES (10, 1, 'Mains', 1), (11, 1, 'Desserts', 2)").update();
        jdbc.sql("""
                INSERT INTO menu_items (id, section_id, restaurant_id, name, description, price_paise, is_veg, position) VALUES
                  (100, 10, 1, 'Paneer Butter Masala', 'cottage cheese, tomato, butter', 26000, true, 1),
                  (101, 10, 1, 'Hyderabadi Chicken Biryani', 'basmati rice, chicken, saffron', 32000, false, 2),
                  (102, 11, 1, 'Gulab Jamun', 'milk dumplings in syrup', 9000, true, 1)""").update();
        jdbc.sql("INSERT INTO option_groups VALUES (1000, 101, 'Portion', 1, 1)").update();
        jdbc.sql("INSERT INTO options VALUES (5000, 1000, 'Regular', 0), (5001, 1000, 'Large', 12000)").update();
        cards.rebuild(1);
    }

    @Test
    void aPriceChangeUpdatesTheCardInTheSameTransactionAndReachesRedisThroughTheOutbox() {
        CatalogService.Changed changed = catalog.change(101, new ItemChange(34000, true));

        assertThat(changed.cardVersion()).isEqualTo(2);
        assertThat(cards.find(1).orElseThrow().json()).contains("\"price\": 34000").contains("\"Large\"");
        assertThat(prices.find(101)).isEmpty();                       // not yet: Redis is a copy, fed asynchronously

        assertThat(projector.drainOnce()).isEqualTo(1);

        assertThat(prices.find(101)).contains(new PriceCache.CachedPrice(changed.itemVersion(), 34000, true));
        assertThat(jdbc.sql("SELECT count(*) FROM catalog_outbox").query(Long.class).single()).isZero();
    }

    @Test
    void aBulkEditRebuildsTheCardOnce() {
        catalog.changeAll(Map.of(100L, new ItemChange(27000, true), 102L, new ItemChange(9500, false)));

        assertThat(cards.find(1).orElseThrow().version()).isEqualTo(2);
        assertThat(projector.drainOnce()).isEqualTo(2);
        assertThat(prices.find(102).orElseThrow().available()).isFalse();
    }

    @Test
    void replayedAndOutOfOrderChangesCannotMoveTheCopyBackwards() {
        jdbc.sql("""
                INSERT INTO catalog_outbox (item_id, restaurant_id, price_paise, available, version) VALUES
                  (100, 1, 29000, true, 3), (100, 1, 27000, true, 2), (100, 1, 29000, true, 3)""").update();

        projector.drainOnce();

        assertThat(prices.find(100)).contains(new PriceCache.CachedPrice(3, 29000, true));
        assertThat(prices.apply(100, 1, 1, true)).isFalse();
    }

    @Test
    void searchFindsWordsInAnyOrderPrefixesAndTypos() {
        assertThat(search.search("biryani chicken", 10)).extracting(DishSearch.Hit::itemId).containsExactly(101L);
        assertThat(search.search("chicken biry", 10)).extracting(DishSearch.Hit::itemId).containsExactly(101L);
        assertThat(search.search("cottage cheese", 10)).extracting(DishSearch.Hit::itemId).containsExactly(100L);

        List<DishSearch.Hit> typo = search.search("panner buter masala", 10);
        assertThat(typo).isNotEmpty();
        assertThat(typo.getFirst().itemId()).isEqualTo(100L);
        assertThat(typo.getFirst().matchedBy()).isEqualTo(DishSearch.MatchedBy.SIMILAR_NAME);
    }

    @Test
    void nearbyRidersComeNearestFirstAndSilentOnesArePruned() {
        Instant now = Instant.now();
        riders.update(new Ping(1, 12.9352, 77.6245, 20, now));                         // Koramangala
        riders.update(new Ping(2, 12.9279, 77.6271, 15, now));                         // ~0.8 km south
        riders.update(new Ping(3, 12.9716, 77.5946, 25, now.minus(Duration.ofMinutes(5))));  // MG Road, silent

        List<RiderLocations.NearbyRider> near = riders.nearby(12.9300, 77.6260, 3, 10);
        assertThat(near).extracting(RiderLocations.NearbyRider::riderId).containsExactly(2L, 1L);
        assertThat(near.getFirst().distanceKm()).isLessThan(0.5);

        assertThat(riders.pruneSilent(Duration.ofSeconds(30), now)).isEqualTo(1);
        assertThat(riders.nearby(12.9716, 77.5946, 1, 10)).isEmpty();
    }

    @Test
    void historyIsWrittenInBatchesIntoDailyPartitions() {
        partitions.maintain();
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        int written = history.record(List.of(new Ping(7, 12.93, 77.62, 18, t), new Ping(7, 12.931, 77.621, 21, t.plusSeconds(4))));

        assertThat(written).isEqualTo(2);
        assertThat(history.track(7, t.minusSeconds(1), t.plusSeconds(60))).extracting(Ping::recordedAt)
                .containsExactly(t, t.plusSeconds(4));
        assertThat(jdbc.sql("SELECT count(*) FROM pg_inherits WHERE inhparent = 'rider_pings'::regclass")
                .query(Long.class).single()).isEqualTo(9L);   // 7 days kept + 2 ready ahead
    }
}
