package dev.foodfleet.ch29.riders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Takes pings from the request threads and writes them to history once a second in batches of up to
 * 5,000: 10,000 pings/s become a few statements a second instead of 10,000 transactions. A crash loses
 * at most the last second of history; the live position is already in Redis.
 */
@Component
public class PingBuffer {

    private static final Logger log = LoggerFactory.getLogger(PingBuffer.class);
    static final int MAX_BATCH = 5_000;
    static final int MAX_QUEUED = 200_000;

    private final ConcurrentLinkedQueue<Ping> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();   // ConcurrentLinkedQueue.size() walks the queue
    private final PositionHistory history;

    public PingBuffer(PositionHistory history) {
        this.history = history;
    }

    /** False when the buffer is full (history is falling behind); the caller still updated the live position. */
    public boolean offer(Ping ping) {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            return false;
        }
        return queue.offer(ping);
    }

    @Scheduled(fixedDelayString = "${riders.history.flush-interval:1s}")
    public void flush() {
        List<Ping> batch = new ArrayList<>(MAX_BATCH);
        Ping p;
        while ((p = queue.poll()) != null) {
            queued.decrementAndGet();
            batch.add(p);
            if (batch.size() == MAX_BATCH) {
                write(batch);
                batch = new ArrayList<>(MAX_BATCH);
            }
        }
        write(batch);
    }

    private void write(List<Ping> batch) {
        try {
            history.record(batch);
        } catch (RuntimeException e) {
            log.warn("dropped {} pings of history: {}", batch.size(), e.toString());
        }
    }
}
