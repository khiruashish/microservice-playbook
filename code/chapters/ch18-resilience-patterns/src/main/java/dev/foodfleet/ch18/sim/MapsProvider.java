package dev.foodfleet.ch18.sim;

import dev.foodfleet.ch18.resilience.Call;
import dev.foodfleet.ch18.resilience.Outcome;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The external maps/routing provider that eta lookups depend on. Normally ~40 ms. During the
 * incident window it browns out: 80% of calls take ~2.5 s, the rest are still fast. Like real services it gets slower as concurrent work
 * piles up (above 150 in flight) and sheds load with 503 above 300 in flight. Abandoned requests
 * (the caller timed out) still occupy it until they finish.
 */
public final class MapsProvider implements Call {

    private final EventLoop loop;
    private final Random random;
    private final long incidentStart;
    private final long incidentEnd;
    private int inFlight;
    private int peakInFlight;
    private long calls;
    private long callsDuringIncident;

    public MapsProvider(EventLoop loop, Random random, long incidentStart, long incidentEnd) {
        this.loop = loop;
        this.random = random;
        this.incidentStart = incidentStart;
        this.incidentEnd = incidentEnd;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        calls++;
        long now = loop.now();
        if (now >= incidentStart && now < incidentEnd) {
            callsDuringIncident++;
        }
        if (inFlight >= 300) {
            loop.after(EventLoop.ms(5), () -> done.accept(Outcome.UNAVAILABLE));   // shedding load
            return;
        }
        inFlight++;
        peakInFlight = Math.max(peakInFlight, inFlight);
        boolean incident = now >= incidentStart && now < incidentEnd;
        double base = incident && random.nextDouble() < 0.8 ? 2_500 : 40;
        double congestion = Math.max(1.0, inFlight / 150.0);
        long latency = EventLoop.ms(base * (0.5 + random.nextDouble()) * congestion);
        loop.after(latency, () -> {
            inFlight--;
            done.accept(Outcome.SUCCESS);
        });
    }

    public long calls() {
        return calls;
    }

    public long callsDuringIncident() {
        return callsDuringIncident;
    }

    public int peakInFlight() {
        return peakInFlight;
    }
}
