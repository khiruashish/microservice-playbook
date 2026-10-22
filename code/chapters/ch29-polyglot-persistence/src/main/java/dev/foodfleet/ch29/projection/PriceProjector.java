package dev.foodfleet.ch29.projection;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains catalog_outbox into the Redis price cache. Each batch is claimed, applied and deleted in one
 * database transaction: if Redis fails or this instance dies mid-batch, the transaction rolls back and the
 * rows are claimed again later (the version guard makes the second application harmless). Rows are
 * claimed with SKIP LOCKED rather than read past an "id > last seen" offset, because ids are assigned at
 * insert and committed in a different order: the offset reader left 29-48 of 5,000 items wrong in the
 * chapter's experiment, the claiming reader none.
 */
@Component
public class PriceProjector {

    private static final Logger log = LoggerFactory.getLogger(PriceProjector.class);

    record PriceChanged(long id, long itemId, int pricePaise, boolean available, int version) {
    }

    static final int BATCH = 500;

    static final String CLAIM = """
            DELETE FROM catalog_outbox
             WHERE id IN (SELECT id FROM catalog_outbox ORDER BY id LIMIT :batch FOR UPDATE SKIP LOCKED)
            RETURNING id, item_id, price_paise, available, version""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PriceCache cache;

    public PriceProjector(JdbcClient jdbc, TransactionTemplate tx, PriceCache cache) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.cache = cache;
    }

    @Scheduled(fixedDelayString = "${catalog.projector.poll-interval:20ms}")
    public void poll() {
        try {
            while (drainOnce() == BATCH) {
                // a full batch: there may be more waiting
            }
        } catch (RuntimeException e) {
            log.warn("price projection paused, will retry: {}", e.toString());
        }
    }

    /** Applies one batch; returns how many changes it claimed. */
    public int drainOnce() {
        Integer claimed = tx.execute(status -> {
            List<PriceChanged> changes = jdbc.sql(CLAIM).param("batch", BATCH)
                    .query((rs, n) -> new PriceChanged(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getBoolean(4), rs.getInt(5)))
                    .list();
            changes.stream().sorted((a, b) -> Long.compare(a.id(), b.id()))
                    .forEach(c -> cache.apply(c.itemId(), c.version(), c.pricePaise(), c.available()));
            return changes.size();
        });
        return claimed == null ? 0 : claimed;
    }
}
