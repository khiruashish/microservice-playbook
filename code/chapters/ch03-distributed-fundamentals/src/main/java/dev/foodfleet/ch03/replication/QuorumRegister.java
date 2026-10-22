package dev.foodfleet.ch03.replication;

import dev.foodfleet.ch03.clock.HybridLogicalClock;
import dev.foodfleet.ch03.clock.HybridLogicalClock.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * A single value replicated to N replicas, as a leaderless (Dynamo-style) store would do it.
 * It exists to make CAP, PACELC and quorum arithmetic tangible in a unit test.
 *
 * <ul>
 *   <li>A write is applied <em>synchronously</em> to W replicas and replicated <em>asynchronously</em>
 *       to the rest (call {@link #replicate()} to let replication catch up).</li>
 *   <li>A read consults R replicas, starting from the other end of the replica list (as a load
 *       balancer would spread reads), and returns the newest version seen.</li>
 *   <li>Versions are hybrid logical clock timestamps; conflicts resolve last-write-wins.</li>
 *   <li>Under a partition, {@link Mode#CP} refuses operations that can't reach a quorum, and
 *       {@link Mode#AP} accepts them with whatever replicas it can reach and reconciles later.</li>
 * </ul>
 * Not thread-safe: it is a teaching model driven step by step from tests.
 */
public final class QuorumRegister<V> {

    public enum Mode { CP, AP }

    private final int w;
    private final int r;
    private final Mode mode;
    private final HybridLogicalClock clock;
    private final List<Replica<V>> replicas;
    private final List<PendingReplication<V>> pending = new ArrayList<>();

    public QuorumRegister(int n, int w, int r, Mode mode, HybridLogicalClock clock) {
        if (n < 1 || w < 1 || w > n || r < 1 || r > n) {
            throw new IllegalArgumentException("need 1 <= W <= N and 1 <= R <= N");
        }
        this.w = w;
        this.r = r;
        this.mode = Objects.requireNonNull(mode);
        this.clock = Objects.requireNonNull(clock);
        this.replicas = IntStream.range(0, n).mapToObj(Replica<V>::new).toList();
    }

    /** True when every read quorum overlaps every write quorum, so a read always sees the latest acknowledged write. */
    public boolean isStronglyConsistentConfiguration() {
        return r + w > replicas.size();
    }

    public WriteResult write(V value) {
        List<Replica<V>> reachable = replicas.stream().filter(Replica::reachable).toList();
        int required = mode == Mode.CP ? w : 1;
        if (reachable.size() < required) {
            throw new QuorumUnavailableException("write", required, reachable.size());
        }
        Timestamp version = clock.now();
        int synchronous = Math.min(w, reachable.size());
        List<Integer> acknowledged = new ArrayList<>();
        for (int i = 0; i < synchronous; i++) {
            reachable.get(i).apply(version, value);
            acknowledged.add(reachable.get(i).id());
        }
        for (Replica<V> replica : replicas) {
            if (!acknowledged.contains(replica.id())) {
                pending.add(new PendingReplication<>(replica.id(), version, value)); // hinted handoff if unreachable
            }
        }
        return new WriteResult(version, acknowledged, synchronous < w);
    }

    public ReadResult<V> read() {
        List<Replica<V>> reachable = new ArrayList<>(replicas.stream().filter(Replica::reachable).toList());
        int required = mode == Mode.CP ? r : 1;
        if (reachable.size() < required) {
            throw new QuorumUnavailableException("read", required, reachable.size());
        }
        Collections.reverse(reachable);
        List<Replica<V>> consulted = reachable.subList(0, Math.min(r, reachable.size()));
        Replica<V> newest = consulted.get(0);
        for (Replica<V> replica : consulted) {
            if (replica.version().isAfter(newest.version())) {
                newest = replica;
            }
        }
        return new ReadResult<>(Optional.ofNullable(newest.value()), newest.version(),
                consulted.stream().map(Replica::id).toList());
    }

    /** Lets asynchronous replication (anti-entropy, hinted handoff) deliver to every reachable replica. */
    public void replicate() {
        Iterator<PendingReplication<V>> it = pending.iterator();
        while (it.hasNext()) {
            PendingReplication<V> p = it.next();
            Replica<V> target = replicas.get(p.replicaId());
            if (target.reachable()) {
                target.apply(p.version(), p.value());
                it.remove();
            }
        }
    }

    /** Cuts the coordinator off from the given replicas. */
    public void partition(int... replicaIds) {
        Arrays.stream(replicaIds).forEach(id -> replicas.get(id).reachable(false));
    }

    public void heal() {
        replicas.forEach(replica -> replica.reachable(true));
    }

    /** Value held by each replica, for assertions. Empty optional = never written. */
    public List<Optional<V>> replicaValues() {
        return replicas.stream().map(replica -> Optional.ofNullable(replica.value())).toList();
    }

    public record WriteResult(Timestamp version, List<Integer> acknowledgedBy, boolean belowWriteQuorum) {
    }

    public record ReadResult<V>(Optional<V> value, Timestamp version, List<Integer> consulted) {
    }

    private record PendingReplication<V>(int replicaId, Timestamp version, V value) {
    }

    private static final class Replica<V> {

        private final int id;
        private boolean reachable = true;
        private Timestamp version = Timestamp.ZERO;
        private V value;

        Replica(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        boolean reachable() {
            return reachable;
        }

        void reachable(boolean reachable) {
            this.reachable = reachable;
        }

        Timestamp version() {
            return version;
        }

        V value() {
            return value;
        }

        /** Last-write-wins: only a newer version replaces the current one, so replays are harmless. */
        void apply(Timestamp newVersion, V newValue) {
            if (newVersion.isAfter(version)) {
                version = newVersion;
                value = newValue;
            }
        }
    }
}
