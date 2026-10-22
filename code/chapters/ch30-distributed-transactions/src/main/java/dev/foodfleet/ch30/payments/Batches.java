package dev.foodfleet.ch30.payments;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns a batch into the JSON arrays the ledger's statements unpack with {@code jsonb_to_recordset}. Totals per
 * dish and per customer are sorted by key, and the statements lock rows in that order, so two batches running
 * at once can't deadlock.
 */
final class Batches {

    private Batches() {
    }

    static String orders(List<OrderPlaced> batch) {
        return batch.stream().map(o -> "{\"order_id\":%d,\"customer_id\":%d,\"item_id\":%d,\"amount_paise\":%d}"
                        .formatted(o.orderId(), o.customerId(), o.itemId(), o.amountPaise()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    static Map<Long, Integer> portionsPerItem(List<OrderPlaced> batch) {
        Map<Long, Integer> totals = new TreeMap<>();
        batch.forEach(o -> totals.merge(o.itemId(), 1, Integer::sum));
        return totals;
    }

    static Map<Long, Long> paisePerCustomer(List<OrderPlaced> batch) {
        Map<Long, Long> totals = new TreeMap<>();
        batch.forEach(o -> totals.merge(o.customerId(), (long) o.amountPaise(), Long::sum));
        return totals;
    }

    static String items(Map<Long, Integer> perItem) {
        return perItem.entrySet().stream().map(e -> "{\"item_id\":%d,\"n\":%d}".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    static String customers(Map<Long, Long> perCustomer) {
        return perCustomer.entrySet().stream().map(e -> "{\"customer_id\":%d,\"amount\":%d}".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
