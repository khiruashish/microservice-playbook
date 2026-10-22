package dev.foodfleet.ch30.indoubt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InDoubtPolicyTest {

    final Instant now = Instant.parse("2026-10-07T10:00:00Z");
    final InDoubtPolicy policy = new InDoubtPolicy(Duration.ofSeconds(30));

    PreparedTransaction prepared(String gid, int secondsAgo) {
        return new PreparedTransaction(gid, now.minusSeconds(secondsAgo), "foodfleet", "foodfleet");
    }

    @Test
    void theLoggedDecisionWinsWhateverTheAge() {
        List<InDoubtPolicy.Resolution> r = policy.resolve(
                List.of(prepared("order-1", 2), prepared("order-2", 3600)),
                Map.of("order-1", "COMMIT", "order-2", "ROLLBACK"), now);

        assertThat(r).extracting(InDoubtPolicy.Resolution::action)
                .containsExactly(InDoubtPolicy.Action.COMMIT, InDoubtPolicy.Action.ROLLBACK);
        assertThat(r.get(0).sql()).isEqualTo("COMMIT PREPARED 'order-1'");
    }

    @Test
    void noDecisionMeansPresumedAbortButOnlyAfterTheGracePeriod() {
        List<InDoubtPolicy.Resolution> r = policy.resolve(
                List.of(prepared("order-3", 5), prepared("order-4", 31)), Map.of(), now);

        assertThat(r).extracting(InDoubtPolicy.Resolution::action)
                .containsExactly(InDoubtPolicy.Action.WAIT, InDoubtPolicy.Action.ROLLBACK);
        assertThat(r.get(1).why()).contains("presumed abort");
        assertThat(r.get(1).sql()).isEqualTo("ROLLBACK PREPARED 'order-4'");
    }

    @Test
    void quotesInGlobalIdsAreEscaped() {
        InDoubtPolicy.Resolution r = policy.resolve(List.of(prepared("it's", 60)), Map.of(), now).getFirst();
        assertThat(r.sql()).isEqualTo("ROLLBACK PREPARED 'it''s'");
    }
}
