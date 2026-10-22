package dev.foodfleet.ch33.address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/** Runs the backfill once at start-up on the instance where app.backfill.enabled=true. Safe to re-run. */
@Component
@ConditionalOnBooleanProperty("app.backfill.enabled")
public class BackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunner.class);
    private final KeysetBackfill backfill;

    public BackfillRunner(KeysetBackfill backfill) {
        this.backfill = backfill;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        log.info("backfill finished: {}", backfill.run());
    }
}
