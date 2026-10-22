package dev.foodfleet.ch29.catalog;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every menu change is one transaction: the item row, the restaurant's menu card, and an outbox row for
 * the stores that keep copies. Either all three happen or none does; nothing here talks to Redis.
 */
@Service
public class CatalogService {

    public record Changed(long itemId, long restaurantId, int itemVersion, int cardVersion) {
    }

    static final String UPDATE_ITEM = """
            UPDATE menu_items SET price_paise = :price, available = :available, version = version + 1
             WHERE id = :id
            RETURNING restaurant_id, version""";

    static final String OUTBOX = """
            INSERT INTO catalog_outbox (item_id, restaurant_id, price_paise, available, version)
            VALUES (:item, :restaurant, :price, :available, :version)""";

    private final JdbcClient jdbc;
    private final MenuCards cards;
    private final TransactionTemplate tx;

    public CatalogService(JdbcClient jdbc, MenuCards cards, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.tx = tx;
    }

    public Changed change(long itemId, ItemChange change) {
        return tx.execute(status -> {
            long[] row = updateItem(itemId, change);
            return new Changed(itemId, row[0], (int) row[1], cards.rebuild(row[0]));
        });
    }

    /** Many items of one restaurant (a menu edit session): one transaction, one card rebuild. */
    public int changeAll(Map<Long, ItemChange> changes) {
        return tx.execute(status -> {
            // rows are locked in id order, so two concurrent bulk edits cannot deadlock each other
            TreeSet<Long> restaurants = new TreeSet<>();
            new TreeMap<>(changes).forEach((itemId, change) -> restaurants.add(updateItem(itemId, change)[0]));
            restaurants.forEach(cards::rebuild);
            return changes.size();
        });
    }

    private long[] updateItem(long itemId, ItemChange change) {
        long[] row = jdbc.sql(UPDATE_ITEM).param("price", change.pricePaise()).param("available", change.available())
                .param("id", itemId)
                .query((rs, n) -> new long[] {rs.getLong(1), rs.getInt(2)})
                .optional().orElseThrow(() -> new NoSuchElementException("no menu item " + itemId));
        jdbc.sql(OUTBOX).param("item", itemId).param("restaurant", row[0]).param("price", change.pricePaise())
                .param("available", change.available()).param("version", (int) row[1]).update();
        return row;
    }
}
