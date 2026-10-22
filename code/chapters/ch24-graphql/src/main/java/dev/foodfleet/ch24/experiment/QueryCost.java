package dev.foodfleet.ch24.experiment;

import dev.foodfleet.ch24.mini.Engine;
import dev.foodfleet.ch24.mini.Query;

/**
 * What one nested query can cost. Restaurant -> reviews -> restaurant -> reviews ... is a legal
 * query against the schema (every review links back to its restaurant), and each level multiplies
 * the work by the page size. The static complexity, computed from the document before executing
 * anything, predicts the work exactly, so a server can refuse the query up front.
 */
public final class QueryCost {

    public static final long MAX_COMPLEXITY = 2_000;

    /** {@code restaurant { name reviews(first: n) { rating restaurant { name reviews(...) ... } } } } */
    public static String nested(int levels, int first) {
        StringBuilder q = new StringBuilder("{ restaurant(id: \"r-11\") { name ");
        for (int k = 0; k < levels; k++) {
            q.append("reviews(first: ").append(first).append(") { rating ");
            if (k < levels - 1) {
                q.append("restaurant { name ");
            }
        }
        for (int k = 0; k < levels; k++) {
            q.append(k == 0 ? "} " : "} } ");
        }
        return q.append("} }").toString();
    }

    public record Row(int levels, int depth, long complexity, int resolvedFields, int callsPerItem, int callsBatched,
                      boolean allowed) {
    }

    public static Row measure(int levels, int first) {
        Query query = Query.parse(nested(levels, first));
        FoodFleetBackends b = new FoodFleetBackends();
        Engine engine = FoodFleetGraph.engine(b);
        long complexity = engine.complexity(query);
        engine.execute(query, Engine.Mode.PER_ITEM, "root");
        int perItem = b.totalCalls();
        int fields = engine.resolvedFields();
        b.reset();
        engine.execute(query, Engine.Mode.BATCHED, "root");
        return new Row(levels, query.depth(), complexity, fields, perItem, b.totalCalls(), complexity <= MAX_COMPLEXITY);
    }

    public static void main(String[] args) {
        System.out.println("restaurant -> reviews(first: 10) -> restaurant -> ...   limit: complexity " + MAX_COMPLEXITY + "\n");
        System.out.printf("%7s %6s %12s %16s %16s %15s  %s%n", "levels", "depth", "complexity", "fields if run",
                "calls per-item", "calls batched", "with the limit");
        for (int levels = 1; levels <= 5; levels++) {
            Row r = measure(levels, 10);
            System.out.printf("%7d %6d %12d %16d %16d %15d  %s%n", r.levels(), r.depth(), r.complexity(),
                    r.resolvedFields(), r.callsPerItem(), r.callsBatched(), r.allowed() ? "allowed" : "rejected up front");
        }
    }
}
