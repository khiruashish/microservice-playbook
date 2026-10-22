package dev.foodfleet.ch30.indoubt;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What to do with each prepared transaction. A prepared transaction keeps its row locks until it is committed
 * or rolled back, across restarts of the database: in the chapter's experiment one in-doubt order cut the
 * throughput on two popular dishes from ~160 to 2-6 orders/s until it was resolved. The coordinator's decision
 * log is the authority: a logged COMMIT is committed, a logged ROLLBACK rolled back; with no decision logged,
 * the transaction is presumed aborted, but only once it is older than the grace period, so a live
 * coordinator between PREPARE and its log write is not overruled.
 */
public final class InDoubtPolicy {

    public enum Action { COMMIT, ROLLBACK, WAIT }

    public record Resolution(PreparedTransaction tx, Action action, String why) {

        public String sql() {
            return switch (action) {
                case COMMIT -> "COMMIT PREPARED '" + tx.gid().replace("'", "''") + "'";
                case ROLLBACK -> "ROLLBACK PREPARED '" + tx.gid().replace("'", "''") + "'";
                case WAIT -> throw new IllegalStateException("nothing to execute while waiting");
            };
        }
    }

    private final Duration grace;

    public InDoubtPolicy(Duration grace) {
        this.grace = grace;
    }

    public List<Resolution> resolve(List<PreparedTransaction> prepared, Map<String, String> decisions, Instant now) {
        List<Resolution> out = new ArrayList<>();
        for (PreparedTransaction tx : prepared) {
            String decision = decisions.get(tx.gid());
            Duration age = Duration.between(tx.prepared(), now);
            if ("COMMIT".equals(decision)) {
                out.add(new Resolution(tx, Action.COMMIT, "the coordinator logged COMMIT"));
            } else if ("ROLLBACK".equals(decision)) {
                out.add(new Resolution(tx, Action.ROLLBACK, "the coordinator logged ROLLBACK"));
            } else if (age.compareTo(grace) > 0) {
                out.add(new Resolution(tx, Action.ROLLBACK, "no decision logged after " + age.toSeconds() + " s: presumed abort"));
            } else {
                out.add(new Resolution(tx, Action.WAIT, "no decision yet, " + age.toMillis() + " ms old"));
            }
        }
        return out;
    }
}
