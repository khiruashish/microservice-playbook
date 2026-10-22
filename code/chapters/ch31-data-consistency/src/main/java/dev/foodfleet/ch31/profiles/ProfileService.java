package dev.foodfleet.ch31.profiles;

import dev.foodfleet.ch31.reads.ConsistencyToken;
import dev.foodfleet.ch31.reads.Positions;
import dev.foodfleet.ch31.reads.ReplicaRouter;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Writes go to the primary and only succeed against the version the client last saw (no lost updates through
 * the API); the answer carries a consistency token. Reads go through the router with the client's token.
 */
@Service
public class ProfileService {

    public record Written(Profile profile, ConsistencyToken token) {
    }

    static final String UPDATE = """
            UPDATE profiles SET address = :address, version = version + 1, updated_at = now()
             WHERE customer_id = :customer AND version = :expected
            RETURNING customer_id, address, version""";

    static final String SELECT = "SELECT customer_id, address, version FROM profiles WHERE customer_id = :customer";

    private final JdbcClient primary;
    private final ReplicaRouter router;
    private final Positions positions;

    public ProfileService(JdbcClient primary, ReplicaRouter router, Positions positions) {
        this.primary = primary;
        this.router = router;
        this.positions = positions;
    }

    public Written update(long customerId, String address, int expectedVersion) {
        Optional<Profile> updated = primary.sql(UPDATE).param("address", address).param("customer", customerId)
                .param("expected", expectedVersion)
                .query((rs, n) -> new Profile(rs.getLong(1), rs.getString(2), rs.getInt(3))).optional();
        if (updated.isEmpty()) {
            Profile current = primary.sql(SELECT).param("customer", customerId)
                    .query((rs, n) -> new Profile(rs.getLong(1), rs.getString(2), rs.getInt(3))).optional()
                    .orElseThrow(() -> new NoSuchElementException("no customer " + customerId));
            throw new VersionConflict(current.version());
        }
        // autocommit: the update is committed, so the position now covers it
        return new Written(updated.get(), positions.primary());
    }

    public ReplicaRouter.Routed<Optional<Profile>> find(long customerId, Optional<ConsistencyToken> atLeast) {
        return router.read(atLeast, db -> db.sql(SELECT).param("customer", customerId)
                .query((rs, n) -> new Profile(rs.getLong(1), rs.getString(2), rs.getInt(3))).optional());
    }
}
