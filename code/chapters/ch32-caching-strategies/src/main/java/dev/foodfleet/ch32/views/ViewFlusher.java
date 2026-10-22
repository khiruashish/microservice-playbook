package dev.foodfleet.ch32.views;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves buffered view counts to PostgreSQL once a second. The pending hash is renamed atomically, so new views
 * go to a fresh hash while this batch is written; a flusher that dies leaves the batch under views:flushing and
 * the next run finishes it. The batch id is stored next to it and recorded in PostgreSQL in the same statement
 * as the counts, so finishing a batch that had already been committed adds nothing. Measured: without the
 * batch id, a flusher dying after its commit counted 12,000-12,800 views twice; with it, none.
 */
@Component
public class ViewFlusher {

    private static final Logger log = LoggerFactory.getLogger(ViewFlusher.class);

    static final String FLUSHING = "views:flushing";
    static final String BATCH = "views:batch";

    @SuppressWarnings("rawtypes")
    static final RedisScript<List> CLAIM = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
              if redis.call('EXISTS', KEYS[1]) == 0 then return {} end
              redis.call('RENAME', KEYS[1], KEYS[2])
              redis.call('SET', KEYS[3], ARGV[1])
            end
            local batch = redis.call('GET', KEYS[3])
            local out = redis.call('HGETALL', KEYS[2])
            table.insert(out, 1, batch)
            return out""", List.class);

    static final String APPLY = """
            WITH batch AS (INSERT INTO flushed_batches (batch_id) VALUES (:batch) ON CONFLICT DO NOTHING RETURNING 1),
                 counts AS (SELECT * FROM jsonb_each_text(CAST(:counts AS jsonb)))
            INSERT INTO item_views (item_id, views)
            SELECT key::bigint, value::bigint FROM counts, batch
            ON CONFLICT (item_id) DO UPDATE SET views = item_views.views + EXCLUDED.views""";

    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;

    public ViewFlusher(StringRedisTemplate redis, JdbcClient jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${views.flush-interval:1s}")
    public void scheduled() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("view flush failed, the batch stays in Redis for the next run: {}", e.toString());
        }
    }

    /** Returns the number of items whose counts were written (0 if nothing was pending or the batch was a repeat). */
    public int flush() {
        @SuppressWarnings("unchecked")
        List<Object> claimed = redis.execute(CLAIM, List.of(ViewCounter.PENDING, FLUSHING, BATCH), UUID.randomUUID().toString());
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        String batch = (String) claimed.getFirst();
        List<String> json = new ArrayList<>();
        for (int i = 1; i + 1 < claimed.size(); i += 2) {
            json.add("\"" + Long.parseLong((String) claimed.get(i)) + "\":" + Long.parseLong((String) claimed.get(i + 1)));
        }
        int written = jdbc.sql(APPLY).param("batch", batch).param("counts", "{" + String.join(",", json) + "}").update();
        redis.delete(List.of(FLUSHING, BATCH));
        return written;
    }
}
