import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Problem: only one instance runs the nightly job (leader election via a lease); a paused old leader must not corrupt data. */
public class LeaseWithFencing {

    static final class FakeClock { Instant now = Instant.parse("2025-01-01T00:00:00Z"); void advance(Duration d) { now = now.plus(d); } }

    record Lease(String holder, Instant expiresAt, long fencingToken) { }

    /** The lock service (in reality: etcd, ZooKeeper, a DB row, Redis with care). Tokens increase on every new grant. */
    static final class LeaseService {
        private Lease current;
        private long nextToken = 1;
        private final FakeClock clock;
        LeaseService(FakeClock clock) { this.clock = clock; }

        synchronized Optional<Lease> tryAcquire(String who, Duration ttl) {
            if (current != null && current.expiresAt().isAfter(clock.now) && !current.holder().equals(who)) return Optional.empty();
            long token = current != null && current.holder().equals(who) && current.expiresAt().isAfter(clock.now) ? current.fencingToken() : nextToken++;
            current = new Lease(who, clock.now.plus(ttl), token);          // acquire, or renew keeping the same token
            return Optional.of(current);
        }
    }

    /** The protected resource checks the token: it rejects writes from any token older than the highest it has seen. */
    static final class Storage {
        private long highestToken;
        final List<String> writes = new ArrayList<>();

        synchronized boolean write(long token, String data) {
            if (token < highestToken) return false;                         // fenced off: a stale leader
            highestToken = token;
            writes.add("token " + token + ": " + data);
            return true;
        }
    }

    public static void main(String[] args) {
        FakeClock clock = new FakeClock();
        LeaseService leases = new LeaseService(clock);
        Storage storage = new Storage();
        Duration ttl = Duration.ofSeconds(10);

        Lease a = leases.tryAcquire("node-A", ttl).orElseThrow();
        System.out.println("node-A leader, token " + a.fencingToken());
        System.out.println("node-B acquire while A holds the lease: " + leases.tryAcquire("node-B", ttl).isPresent());
        storage.write(a.fencingToken(), "batch part 1");

        clock.advance(Duration.ofSeconds(15));          // node-A hits a 15 s GC pause; its lease expires
        Lease b = leases.tryAcquire("node-B", ttl).orElseThrow();
        System.out.println("A paused 15s, lease expired -> node-B leader, token " + b.fencingToken());
        storage.write(b.fencingToken(), "batch (rerun by B)");

        boolean staleWrite = storage.write(a.fencingToken(), "batch part 2");   // A wakes up still thinking it is leader
        System.out.println("node-A wakes up and writes with token " + a.fencingToken() + " -> accepted? " + staleWrite);
        storage.writes.forEach(w -> System.out.println("storage: " + w));

        clock.advance(Duration.ofSeconds(5));
        Lease renewed = leases.tryAcquire("node-B", ttl).orElseThrow();
        System.out.println("node-B renews: token stays " + renewed.fencingToken() + ", expires " + renewed.expiresAt());

        check(!staleWrite && storage.writes.size() == 2, "fencing token blocks the zombie leader");
        check(renewed.fencingToken() == b.fencingToken(), "renewal keeps the token");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
