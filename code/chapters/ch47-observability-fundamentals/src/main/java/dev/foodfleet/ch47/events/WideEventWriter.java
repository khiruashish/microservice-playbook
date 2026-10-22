package dev.foodfleet.ch47.events;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes wide events as JSON lines (one event, one line), the shape every log pipeline (the OpenTelemetry
 * Collector's filelog receiver, Fluent Bit, Vector) and every column store can ingest without parsing rules.
 * A {@link ReentrantLock} rather than {@code synchronized}, so virtual threads don't pin while writing.
 */
public final class WideEventWriter implements AutoCloseable {

    private final BufferedWriter out;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong events = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();

    public WideEventWriter(Writer out) {
        this.out = new BufferedWriter(out, 64 * 1024);
    }

    public void write(WideEvent.Record event) {
        String line = event.toJson();
        lock.lock();
        try {
            out.write(line);
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
        events.incrementAndGet();
        bytes.addAndGet(line.length() + 1L);
    }

    public long events() {
        return events.get();
    }

    /** Characters written (equal to bytes for ASCII content). */
    public long bytes() {
        return bytes.get();
    }

    public void flush() {
        lock.lock();
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            out.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }
}
