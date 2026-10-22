package dev.foodfleet.ch32.info;

import java.time.LocalTime;
import java.util.NoSuchElementException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restaurant details change rarely and are read on every menu view: the simple case, handled by Spring's cache
 * abstraction. {@code sync = true} makes concurrent misses on one instance wait for a single load (measured:
 * 20 recomputations instead of 160 for 80 clients); the eviction is deferred to after the commit by the
 * transaction-aware cache manager.
 */
@Service
public class RestaurantInfoService {

    private final JdbcClient jdbc;

    public RestaurantInfoService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(cacheNames = "restaurant-info", key = "#id", sync = true)
    public RestaurantInfo get(long id) {
        return jdbc.sql("SELECT id, name, opening_time, closing_time FROM restaurants WHERE id = :id").param("id", id)
                .query((rs, n) -> new RestaurantInfo(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)))
                .optional().orElseThrow(() -> new NoSuchElementException("no restaurant " + id));
    }

    @Transactional
    @CacheEvict(cacheNames = "restaurant-info", key = "#id")
    public void changeHours(long id, LocalTime opens, LocalTime closes) {
        int updated = jdbc.sql("UPDATE restaurants SET opening_time = :opens, closing_time = :closes WHERE id = :id")
                .param("opens", opens).param("closes", closes).param("id", id).update();
        if (updated == 0) {
            throw new NoSuchElementException("no restaurant " + id);
        }
    }
}
