package dev.foodfleet.ch69.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** OrderPlaced events like the ones on FoodFleet's {@code orders} topic: about 300 bytes of JSON each. */
public final class Events {

    static final String[] DISHES = {"paneer-tikka", "butter-chicken", "masala-dosa", "veg-biryani", "chole-bhature", "idli-sambar", "gulab-jamun",
            "dal-makhani", "hyderabadi-biryani", "pav-bhaji", "rogan-josh", "malai-kofta", "aloo-paratha", "filter-coffee"};
    static final String[] CITIES = {"Bengaluru", "Mumbai", "Delhi", "Hyderabad", "Chennai", "Pune", "Kolkata"};

    private Events() {
    }

    public static List<OrderSink.OrderPlaced> orders(int from, int n) {
        SplittableRandom r = new SplittableRandom(42 + from);
        long base = 1_769_454_000_000L; // 2026-01-26T19:00Z
        List<OrderSink.OrderPlaced> out = new ArrayList<>(n);
        for (int i = from; i < from + n; i++) {
            String id = String.format("ord-%08d", i);
            String restaurant = String.format("rest-%04d", r.nextInt(2000));
            long placed = base + i * 3L;
            StringBuilder items = new StringBuilder();
            long total = 0;
            for (int k = 0, m = 1 + r.nextInt(4); k < m; k++) {
                int qty = 1 + r.nextInt(3);
                long price = (90 + r.nextInt(400)) * 100L + 50;
                total += qty * price;
                items.append(k == 0 ? "" : ",").append("{\"sku\":\"").append(DISHES[r.nextInt(DISHES.length)]).append("\",\"qty\":").append(qty)
                        .append(",\"price\":").append(price / 100.0).append('}');
            }
            String json = "{\"type\":\"OrderPlaced\",\"orderId\":\"" + id + "\",\"restaurantId\":\"" + restaurant + "\",\"customerId\":\"cust-"
                    + String.format("%06d", r.nextInt(500_000)) + "\",\"items\":[" + items + "],\"total\":" + total / 100.0
                    + ",\"currency\":\"INR\",\"city\":\"" + CITIES[r.nextInt(CITIES.length)] + "\",\"placedAt\":" + placed + "}";
            out.add(new OrderSink.OrderPlaced(id, restaurant, total, placed, json));
        }
        return out;
    }
}
