package dev.foodfleet.ch31.reads;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcPositions implements Positions {

    private final JdbcClient primary;
    private final JdbcClient replica;

    public JdbcPositions(JdbcClient primary, @Qualifier("replica") JdbcClient replica) {
        this.primary = primary;
        this.replica = replica;
    }

    @Override
    public ConsistencyToken primary() {
        return ConsistencyToken.parse(primary.sql("SELECT pg_current_wal_lsn()::text").query(String.class).single());
    }

    @Override
    public Optional<ConsistencyToken> replica() {
        try {
            // NULL on a server that isn't replaying WAL (a primary, or a test where "replica" is the primary)
            return replica.sql("SELECT pg_last_wal_replay_lsn()::text").query(String.class).optional()
                    .map(ConsistencyToken::parse);
        } catch (DataAccessException | IllegalArgumentException unavailable) {
            return Optional.empty();
        }
    }
}
