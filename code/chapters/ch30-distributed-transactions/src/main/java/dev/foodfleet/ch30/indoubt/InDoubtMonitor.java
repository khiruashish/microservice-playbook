package dev.foodfleet.ch30.indoubt;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * For any system that runs two-phase commit (an XA transaction manager, or PREPARE TRANSACTION from tools):
 * finds prepared transactions in this database and resolves them from the decision log. Runs every 10 s;
 * every action is logged, because a prepared transaction that nobody resolves blocks its rows forever.
 */
@Component
public class InDoubtMonitor {

    private static final Logger log = LoggerFactory.getLogger(InDoubtMonitor.class);

    static final String PREPARED = """
            SELECT gid, prepared, owner, database FROM pg_prepared_xacts
             WHERE database = current_database() ORDER BY prepared""";

    static final String DECISIONS = "SELECT gid, decision FROM orders.tm_log";

    private final JdbcClient jdbc;
    private final InDoubtPolicy policy;
    private final Clock clock;

    @Autowired
    public InDoubtMonitor(JdbcClient jdbc, @Value("${indoubt.grace:30s}") Duration grace) {
        this(jdbc, new InDoubtPolicy(grace), Clock.systemUTC());
    }

    InDoubtMonitor(JdbcClient jdbc, InDoubtPolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${indoubt.check-interval:10s}")
    public List<InDoubtPolicy.Resolution> check() {
        List<PreparedTransaction> prepared = jdbc.sql(PREPARED)
                .query((rs, n) -> new PreparedTransaction(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                        rs.getString(3), rs.getString(4)))
                .list();
        if (prepared.isEmpty()) {
            return List.of();
        }
        Map<String, String> decisions = new HashMap<>();
        jdbc.sql(DECISIONS).query((rs, n) -> decisions.put(rs.getString(1), rs.getString(2))).list();
        List<InDoubtPolicy.Resolution> resolutions = policy.resolve(prepared, decisions, clock.instant());
        for (InDoubtPolicy.Resolution r : resolutions) {
            if (r.action() == InDoubtPolicy.Action.WAIT) {
                log.info("prepared transaction {} in doubt: {}", r.tx().gid(), r.why());
                continue;
            }
            jdbc.sql(r.sql()).update();                     // COMMIT/ROLLBACK PREPARED can't run in a transaction block
            log.warn("resolved prepared transaction {}: {} ({})", r.tx().gid(), r.action(), r.why());
        }
        return resolutions;
    }
}
