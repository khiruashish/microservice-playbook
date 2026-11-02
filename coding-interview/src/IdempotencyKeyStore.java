import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Problem: make POST /payments safe to retry with an Idempotency-Key header. Same key -> same response, exactly-once effect. */
public class IdempotencyKeyStore {

    record Response(int status, String body) { }

    /** IN_PROGRESS while the first request runs; COMPLETED with the stored response afterwards. */
    sealed interface Entry permits InProgress, Completed { String requestHash(); }
    record InProgress(String requestHash) implements Entry { }
    record Completed(String requestHash, Response response) implements Entry { }

    static final class IdempotencyService {
        private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();   // in production: DB/Redis with TTL

        Response handle(String key, String requestBody, Supplier<Response> operation) {
            String hash = Integer.toHexString(requestBody.hashCode());                        // production: SHA-256 of canonical body
            Entry existing = store.putIfAbsent(key, new InProgress(hash));                    // atomic claim of the key
            if (existing != null) {
                if (!existing.requestHash().equals(hash)) return new Response(422, "key reused with a different payload");
                if (existing instanceof Completed c) return c.response();                     // replay stored response
                return new Response(409, "request with this key is still in progress");
            }
            try {
                Response r = operation.get();
                if (r.status() >= 500) store.remove(key);                                     // transient failure: allow a retry
                else store.put(key, new Completed(hash, r));
                return r;
            } catch (RuntimeException e) {
                store.remove(key);
                throw e;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        IdempotencyService svc = new IdempotencyService();
        List<String> ledger = Collections.synchronizedList(new ArrayList<>());
        Supplier<Response> charge = () -> { ledger.add("charged 500"); return new Response(201, "payment pay_1 created"); };

        System.out.println("1st call          -> " + svc.handle("key-A", "{amount:500}", charge));
        System.out.println("client retry      -> " + svc.handle("key-A", "{amount:500}", charge));
        System.out.println("same key, new body-> " + svc.handle("key-A", "{amount:900}", charge));
        check(ledger.size() == 1, "charged exactly once");

        // 20 concurrent duplicates: exactly one executes, others get 409 or the replay
        CountDownLatch slow = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Response>> fs = new ArrayList<>();
            for (int i = 0; i < 20; i++) fs.add(pool.submit(() -> svc.handle("key-B", "{amount:100}", () -> {
                try { slow.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                ledger.add("charged 100");
                return new Response(201, "payment pay_2 created");
            })));
            Thread.sleep(50);
            slow.countDown();
            Map<Integer, Integer> statuses = new TreeMap<>();
            for (Future<Response> f : fs) statuses.merge(f.get().status(), 1, Integer::sum);
            boolean onlyCreatedOrConflict = statuses.keySet().stream().allMatch(st -> st == 201 || st == 409);
            System.out.println("20 concurrent duplicates -> charges for key-B = " + ledger.stream().filter(s -> s.endsWith("100")).count()
                    + ", every response is 201 (original or replay) or 409 (in progress): " + onlyCreatedOrConflict);
            check(onlyCreatedOrConflict && statuses.getOrDefault(201, 0) >= 1, "statuses");
            check(ledger.stream().filter(s -> s.endsWith("100")).count() == 1, "one execution under concurrency");
        }

        Response failed = svc.handle("key-C", "{amount:1}", () -> new Response(503, "bank down"));
        Response retried = svc.handle("key-C", "{amount:1}", () -> new Response(201, "payment pay_3 created"));
        System.out.println("5xx is not stored -> retry executes: " + failed.status() + " then " + retried.status());
        check(retried.status() == 201, "5xx releases the key");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
