package dev.foodfleet.ch32.menu;

import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads: cache-aside (cache, else database, then fill if newer). Writes: the database transaction first; after
 * it commits, the new version is written through to the cache. If Redis is down, reads fall back to the
 * database and writes still succeed: the cache is an optimisation, never the source of truth.
 */
@Service
public class ItemPrices {

    private static final Logger log = LoggerFactory.getLogger(ItemPrices.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ItemPriceCache cache;

    public ItemPrices(JdbcClient jdbc, TransactionTemplate tx, ItemPriceCache cache) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.cache = cache;
    }

    public ItemPrice get(long itemId) {
        try {
            Optional<ItemPrice> cached = cache.get(itemId);
            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (DataAccessException redisDown) {
            log.debug("cache unavailable, reading the database: {}", redisDown.toString());
            return load(itemId);
        }
        ItemPrice loaded = load(itemId);
        try {
            cache.putIfNewer(loaded);
        } catch (DataAccessException ignored) {
            // the next read tries again
        }
        return loaded;
    }

    public ItemPrice changePrice(long itemId, int pricePaise) {
        ItemPrice committed = tx.execute(status -> jdbc.sql("""
                        UPDATE items SET price_paise = :price, version = version + 1 WHERE item_id = :id
                        RETURNING item_id, version, price_paise""")
                .param("price", pricePaise).param("id", itemId)
                .query((rs, n) -> new ItemPrice(rs.getLong(1), rs.getInt(2), rs.getInt(3))).optional()
                .orElseThrow(() -> new NoSuchElementException("no item " + itemId)));
        try {
            cache.putIfNewer(committed);                  // after the commit, never before
        } catch (DataAccessException redisDown) {
            log.warn("price {} committed but not cached: {}", committed, redisDown.toString());
        }
        return committed;
    }

    private ItemPrice load(long itemId) {
        return jdbc.sql("SELECT item_id, version, price_paise FROM items WHERE item_id = :id").param("id", itemId)
                .query((rs, n) -> new ItemPrice(rs.getLong(1), rs.getInt(2), rs.getInt(3))).optional()
                .orElseThrow(() -> new NoSuchElementException("no item " + itemId));
    }
}
