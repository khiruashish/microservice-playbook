package dev.foodfleet.ch29.riders;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Riders whose app stopped reporting (logged off, out of signal) must not be offered orders. */
@Component
public class SilentRiderPruner {

    private final RiderLocations live;
    private final Duration maxSilence;

    public SilentRiderPruner(RiderLocations live, @Value("${riders.live.max-silence:30s}") Duration maxSilence) {
        this.live = live;
        this.maxSilence = maxSilence;
    }

    @Scheduled(fixedDelayString = "${riders.live.prune-interval:5s}")
    public void prune() {
        while (live.pruneSilent(maxSilence, Instant.now()) == 1_000) {
            // a full page: keep going
        }
    }
}
