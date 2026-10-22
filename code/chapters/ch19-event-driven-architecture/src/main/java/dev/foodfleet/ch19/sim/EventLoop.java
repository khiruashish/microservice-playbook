package dev.foodfleet.ch19.sim;

import java.util.PriorityQueue;

/**
 * A discrete-event simulator (the same one as chapter 18): time only moves when the next event
 * runs, so ten minutes of lunch-hour traffic take about a second, and every run with the same seed
 * gives the same numbers. Time is in microseconds.
 */
public final class EventLoop {

    private record Event(long at, long seq, Runnable action) {
    }

    private final PriorityQueue<Event> events = new PriorityQueue<>(
            (a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.seq, b.seq));
    private long now;
    private long seq;

    public long now() {
        return now;
    }

    public void at(long time, Runnable action) {
        events.add(new Event(Math.max(time, now), seq++, action));
    }

    public void after(long delay, Runnable action) {
        at(now + delay, action);
    }

    public void runUntil(long end) {
        while (!events.isEmpty() && events.peek().at <= end) {
            Event e = events.poll();
            now = e.at;
            e.action.run();
        }
        now = end;
    }

    public static long ms(double millis) {
        return Math.round(millis * 1_000);
    }

    public static long seconds(double s) {
        return Math.round(s * 1_000_000);
    }
}
