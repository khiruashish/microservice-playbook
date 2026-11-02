import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Problem: a service registry: instances register, heartbeat, and are evicted after a TTL; clients watch for changes. */
public class ServiceRegistry {

    /** A controllable clock so expiry is deterministic in tests (inject java.time.Clock in production). */
    static final class FakeClock { Instant now = Instant.parse("2025-01-01T00:00:00Z"); void advance(Duration d) { now = now.plus(d); } }

    record InstanceInfo(String service, String id, String address) { }

    static final class Registry {
        private final Map<String, Map<String, InstanceInfo>> byService = new TreeMap<>();
        private final Map<String, Instant> lastHeartbeat = new HashMap<>();
        private final Map<String, List<Consumer<Set<String>>>> watchers = new HashMap<>();
        private final Duration ttl;
        private final FakeClock clock;

        Registry(Duration ttl, FakeClock clock) { this.ttl = ttl; this.clock = clock; }

        synchronized void register(InstanceInfo i) {
            byService.computeIfAbsent(i.service(), s -> new TreeMap<>()).put(i.id(), i);
            lastHeartbeat.put(i.id(), clock.now);
            notifyWatchers(i.service());
        }

        synchronized boolean heartbeat(String id) {
            if (!lastHeartbeat.containsKey(id)) return false;          // unknown: client must re-register
            lastHeartbeat.put(id, clock.now);
            return true;
        }

        synchronized void deregister(String service, String id) {
            if (byService.getOrDefault(service, Map.of()).remove(id) != null) { lastHeartbeat.remove(id); notifyWatchers(service); }
        }

        /** Run periodically: evict instances whose last heartbeat is older than the TTL. */
        synchronized List<String> evictExpired() {
            List<String> evicted = new ArrayList<>();
            for (var entry : byService.entrySet()) {
                boolean changed = entry.getValue().values().removeIf(i -> {
                    boolean expired = Duration.between(lastHeartbeat.get(i.id()), clock.now).compareTo(ttl) > 0;
                    if (expired) { evicted.add(i.id()); lastHeartbeat.remove(i.id()); }
                    return expired;
                });
                if (changed) notifyWatchers(entry.getKey());
            }
            return evicted;
        }

        synchronized Set<String> addresses(String service) {
            Set<String> out = new TreeSet<>();
            byService.getOrDefault(service, Map.of()).values().forEach(i -> out.add(i.address()));
            return out;
        }

        synchronized void watch(String service, Consumer<Set<String>> onChange) {
            watchers.computeIfAbsent(service, s -> new ArrayList<>()).add(onChange);
        }

        private void notifyWatchers(String service) {
            Set<String> snapshot = addresses(service);
            watchers.getOrDefault(service, List.of()).forEach(w -> w.accept(snapshot));
        }
    }

    public static void main(String[] args) {
        FakeClock clock = new FakeClock();
        Registry reg = new Registry(Duration.ofSeconds(30), clock);
        List<String> events = new ArrayList<>();
        reg.watch("orders", addrs -> events.add("orders now " + addrs));

        reg.register(new InstanceInfo("orders", "o-1", "10.0.0.1:8080"));
        reg.register(new InstanceInfo("orders", "o-2", "10.0.0.2:8080"));
        clock.advance(Duration.ofSeconds(20));
        reg.heartbeat("o-1");                               // o-2 stops heartbeating (crashed)
        clock.advance(Duration.ofSeconds(15));
        System.out.println("evicted at t=35s: " + reg.evictExpired());
        reg.deregister("orders", "o-1");                    // graceful shutdown
        System.out.println("heartbeat from evicted o-2 accepted? " + reg.heartbeat("o-2") + " (must re-register)");
        events.forEach(System.out::println);
        check(events.size() == 4 && reg.addresses("orders").isEmpty(), "register x2, evict, deregister");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
