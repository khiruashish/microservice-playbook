package dev.foodfleet.ch31.reads;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

class ReplicaRouterTest {

    /** A replica that catches up by one step every time it is asked, starting at {@code start}. */
    static final class FakePositions implements Positions {
        long replica;
        boolean replicaKnown = true;
        final List<String> calls = new ArrayList<>();

        FakePositions(long replica) {
            this.replica = replica;
        }

        @Override
        public ConsistencyToken primary() {
            calls.add("primary position");
            return new ConsistencyToken(100);
        }

        @Override
        public Optional<ConsistencyToken> replica() {
            calls.add("replica position");
            return replicaKnown ? Optional.of(new ConsistencyToken(replica++)) : Optional.empty();
        }
    }

    // two distinct clients the query function can tell apart; nothing here opens a connection
    final JdbcClient primary = JdbcClient.create(new SimpleDriverDataSource());
    final JdbcClient replica = JdbcClient.create(new SimpleDriverDataSource());
    final List<Duration> slept = new ArrayList<>();

    ReplicaRouter router(Positions p) {
        return new ReplicaRouter(primary, replica, p, Duration.ofMillis(50), slept::add);
    }

    String whichDb(JdbcClient db) {
        return db == primary ? "primary" : "replica";
    }

    @Test
    void withoutATokenTheReplicaAnswers() {
        ReplicaRouter.Routed<String> r = router(new FakePositions(10)).read(Optional.empty(), this::whichDb);
        assertThat(r.value()).isEqualTo("replica");
        assertThat(r.source()).isEqualTo(ReplicaRouter.Source.REPLICA);
    }

    @Test
    void theReplicaIsCheckedBeforeTheQueryAndWaitedForWhileBehind() {
        FakePositions p = new FakePositions(40);
        ReplicaRouter.Routed<String> r = router(p).read(Optional.of(new ConsistencyToken(42)), this::whichDb);

        assertThat(r.value()).isEqualTo("replica");
        assertThat(slept).hasSize(2);                                // positions 40 and 41 were behind
        assertThat(r.token().position()).isEqualTo(43L);             // read again after the query
    }

    @Test
    void aReplicaFarBehindSendsTheReadToThePrimary() {
        FakePositions p = new FakePositions(0);
        ReplicaRouter.Routed<String> r = new ReplicaRouter(primary, replica, p, Duration.ZERO, slept::add)
                .read(Optional.of(new ConsistencyToken(1_000)), this::whichDb);

        assertThat(r.value()).isEqualTo("primary");
        assertThat(r.token().position()).isEqualTo(1_000L);          // never older than what the caller had seen
    }

    @Test
    void aServerThatIsNotReplayingIsNotTrustedAsAReplica() {
        FakePositions p = new FakePositions(0);
        p.replicaKnown = false;
        ReplicaRouter.Routed<String> r = router(p).read(Optional.empty(), this::whichDb);
        assertThat(r.source()).isEqualTo(ReplicaRouter.Source.PRIMARY);
        assertThat(slept).isEmpty();
    }

    @Test
    void aCanceledReplicaQueryFallsBackToThePrimary() {
        ReplicaRouter.Routed<String> r = router(new FakePositions(10)).read(Optional.empty(), db -> {
            if (db == replica) {
                throw new DataAccessResourceFailureException("canceling statement due to conflict with recovery");
            }
            return "primary";
        });
        assertThat(r.value()).isEqualTo("primary");
    }
}
