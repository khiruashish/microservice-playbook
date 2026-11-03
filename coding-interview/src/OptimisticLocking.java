import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

/** Problem: many service instances update the same stock row; prevent lost updates without holding DB locks. */
public class OptimisticLocking {

    record Stock(String sku, int quantity, long version) { }

    static final class ConflictException extends RuntimeException { ConflictException(String m) { super(m); } }

    /** Simulates: UPDATE stock SET quantity=?, version=version+1 WHERE sku=? AND version=?  (0 rows -> conflict) */
    static final class StockTable {
        private final ConcurrentHashMap<String, Stock> rows = new ConcurrentHashMap<>();
        void insert(Stock s) { rows.put(s.sku(), s); }
        Stock read(String sku) { return rows.get(sku); }

        void update(Stock expected, int newQuantity) {
            Stock next = new Stock(expected.sku(), newQuantity, expected.version() + 1);
            if (!rows.replace(expected.sku(), expected, next)) throw new ConflictException("version " + expected.version() + " is stale");
        }
    }

    /** Lost update, scripted so it is reproducible: two instances read, then both write (last write wins). */
    static int lostUpdate(Map<String, Integer> table) {
        int readByA = table.get("apple");      // instance A reads 10
        int readByB = table.get("apple");      // instance B reads 10
        table.put("apple", readByA - 1);       // A writes 9
        table.put("apple", readByB - 1);       // B writes 9: A's decrement is lost
        return table.get("apple");
    }

    /** Read -> compute -> conditional write; on conflict re-read and retry (bounded). */
    static int updateWithRetry(StockTable table, String sku, UnaryOperator<Integer> change, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Stock current = table.read(sku);
            int next = change.apply(current.quantity());
            if (next < 0) throw new IllegalStateException("out of stock");
            try { table.update(current, next); return attempt; }
            catch (ConflictException e) { Thread.onSpinWait(); }
        }
        throw new ConflictException("gave up after " + maxAttempts + " attempts");
    }

    public static void main(String[] args) throws Exception {
        int workers = 200;

        int naive = lostUpdate(new HashMap<>(Map.of("apple", 10)));
        System.out.println("without versioning : 10 - 2 sales = " + naive + "  <-- one update lost (read, read, write, write)");
        check(naive == 9, "lost update demonstrated");

        StockTable table = new StockTable();
        table.insert(new Stock("apple", 1_000, 0));
        List<Future<Integer>> attempts = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < workers; i++) attempts.add(pool.submit(() -> updateWithRetry(table, "apple", q -> q - 1, 1_000)));
        }
        int retries = 0;
        for (Future<Integer> f : attempts) retries += f.get() - 1;
        Stock finalRow = table.read("apple");
        System.out.println("with version check : quantity=" + finalRow.quantity() + ", version=" + finalRow.version() + " (200 writers, conflicting ones re-read and retried)");
        check(finalRow.quantity() == 800 && finalRow.version() == 200, "no lost updates");

        Stock stale = new Stock("apple", 800, 3);
        try { table.update(stale, 1); }
        catch (ConflictException e) { System.out.println("stale write rejected: " + e.getMessage() + " -> HTTP 409/412"); }
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
