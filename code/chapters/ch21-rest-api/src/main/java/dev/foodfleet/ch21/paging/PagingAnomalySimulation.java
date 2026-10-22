package dev.foodfleet.ch21.paging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What happens to a client paging through a live list. The support dashboard lists open orders,
 * newest first, 20 per page. While an agent pages through, new orders arrive at the top and orders
 * close (delivered) anywhere in the list. Every order that was open when paging started and stayed
 * open throughout should be seen exactly once.
 *
 * <ul>
 *   <li><b>offset</b>: page n = skip 20 x n. An arrival at the top pushes everything down: the last
 *       item of the previous page appears again (duplicate). A closed order above the offset pulls
 *       everything up: an item slides into the previous page's range and is never seen (skip).</li>
 *   <li><b>keyset</b>: page n = the 20 items after the last one seen. Arrivals and removals elsewhere
 *       don't move that position.</li>
 * </ul>
 */
public final class PagingAnomalySimulation {

    public enum Strategy { OFFSET, KEYSET }

    public record Order(String id, Instant placedAt) {
    }

    public record Result(Strategy strategy, int mustSee, int duplicates, int skipped) {
    }

    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::placedAt).reversed().thenComparing(Order::id, Comparator.reverseOrder());

    public static Result run(Strategy strategy, int open, int pages, int arrivalsPerPage, int closuresPerPage,
                             long seed) {
        Random random = new Random(seed);
        List<Order> list = new ArrayList<>();
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
        int next = 0;
        for (int i = 0; i < open; i++) {
            list.add(new Order("o-" + next++, now.minusSeconds(open - i)));
        }
        list.sort(NEWEST_FIRST);
        Set<String> atStart = new HashSet<>();
        list.forEach(o -> atStart.add(o.id()));
        Set<String> closedDuringSession = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();

        Order last = null;
        for (int page = 0; page < pages; page++) {
            List<Order> items = switch (strategy) {
                case OFFSET -> list.subList(Math.min(page * 20, list.size()), Math.min(page * 20 + 20, list.size()));
                case KEYSET -> {
                    Order after = last;
                    yield list.stream().filter(o -> after == null || NEWEST_FIRST.compare(o, after) > 0).limit(20).toList();
                }
            };
            if (items.isEmpty()) {
                break;
            }
            items.forEach(o -> seen.merge(o.id(), 1, Integer::sum));
            last = items.getLast();

            // while the agent reads this page: new orders arrive, some open orders close
            now = now.plusSeconds(15);
            for (int a = 0; a < arrivalsPerPage; a++) {
                list.add(new Order("o-" + next++, now.plusMillis(a)));
            }
            for (int c = 0; c < closuresPerPage && !list.isEmpty(); c++) {
                Order closed = list.remove(random.nextInt(list.size()));
                closedDuringSession.add(closed.id());
            }
            list.sort(NEWEST_FIRST);
        }

        int duplicates = 0;
        for (int count : seen.values()) {
            duplicates += Math.max(0, count - 1);
        }
        // Originals in their starting order (newest first). The reader is accountable for every
        // original down to the deepest one it actually reached, that stayed open throughout.
        List<String> startOrder = new ArrayList<>(atStart);
        startOrder.sort(Comparator.comparingInt((String id) -> Integer.parseInt(id.substring(2))).reversed());
        int deepest = -1;
        for (int rank = 0; rank < startOrder.size(); rank++) {
            if (seen.containsKey(startOrder.get(rank))) {
                deepest = rank;
            }
        }
        int mustSee = 0;
        int skipped = 0;
        for (int rank = 0; rank <= deepest; rank++) {
            String id = startOrder.get(rank);
            if (closedDuringSession.contains(id)) {
                continue;
            }
            mustSee++;
            if (!seen.containsKey(id)) {
                skipped++;
            }
        }
        return new Result(strategy, mustSee, duplicates, skipped);
    }

    public static void main(String[] args) {
        System.out.println("Support dashboard: 2,000 open orders, an agent reads 50 pages of 20 (15 s per page);");
        System.out.println("meanwhile orders arrive at the top and close anywhere in the list.\n");
        System.out.printf("%-9s %-24s %10s %11s %9s%n", "strategy", "per page: new / closed", "should see",
                "duplicates", "skipped");
        int[][] loads = {{0, 0}, {3, 0}, {0, 3}, {3, 3}, {10, 10}};
        for (int[] load : loads) {
            for (Strategy s : Strategy.values()) {
                Result r = run(s, 2_000, 50, load[0], load[1], 21);
                System.out.printf("%-9s %-24s %10d %11d %9d%n", s, load[0] + " / " + load[1], r.mustSee(),
                        r.duplicates(), r.skipped());
            }
        }
    }
}
