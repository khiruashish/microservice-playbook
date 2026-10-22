package dev.foodfleet.ch29.riders;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Applies {@link PartitionPlan} at start-up and every hour. Idempotent; safe on several instances. */
@Component
public class PartitionMaintenance implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenance.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int retentionDays;

    @Autowired
    public PartitionMaintenance(JdbcClient jdbc, @Value("${riders.history.retention-days:7}") int retentionDays) {
        this(jdbc, Clock.systemUTC(), retentionDays);
    }

    PartitionMaintenance(JdbcClient jdbc, Clock clock, int retentionDays) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Override
    public void run(ApplicationArguments args) {
        maintain();
    }

    @Scheduled(cron = "0 7 * * * *")
    public PartitionPlan.Plan maintain() {
        List<String> existing = jdbc.sql("""
                SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                 WHERE i.inhparent = 'rider_pings'::regclass""").query(String.class).list();
        PartitionPlan.Plan plan = PartitionPlan.plan(LocalDate.now(clock.withZone(ZoneOffset.UTC)), retentionDays, 2, existing);
        plan.create().forEach(p -> jdbc.sql(p.createSql()).update());
        plan.drop().forEach(p -> {
            jdbc.sql(p.dropSql()).update();
            log.info("dropped {} (older than {} days)", p.name(), retentionDays);
        });
        return plan;
    }
}
