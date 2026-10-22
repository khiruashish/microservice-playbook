package dev.foodfleet.ch24.experiment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The services behind the customer app, in memory, counting every call: order-service,
 * restaurant-service, rider-service and review-service. Each call is assumed to cost a fixed
 * network + query time ({@link #MS_PER_CALL}), whether it asks for one item or a batch.
 */
public final class FoodFleetBackends {

    public static final double MS_PER_CALL = 6.0;

    public enum Status { PLACED, PREPARING, PICKED_UP, DELIVERED, CANCELLED }

    public record OrderItem(String name, int quantity, int pricePaise) {
    }

    public record Order(String id, String customerId, Status status, int totalPaise, String placedAt,
                        String restaurantId, String riderId, List<OrderItem> items) {
    }

    public record Restaurant(String id, String name, String area, double rating) {
    }

    public record Rider(String id, String name, String vehicle) {
    }

    public record Review(String id, String restaurantId, int rating, String text) {
    }

    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private final Map<String, Rider> riders = new LinkedHashMap<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();
    private final Map<String, Integer> calls = new LinkedHashMap<>();
    private int rows;

    public FoodFleetBackends() {
        String[][] rs = {
            {"r-11", "Meghana Foods", "Koramangala", "4.4"}, {"r-12", "Vidyarthi Bhavan", "Basavanagudi", "4.6"},
            {"r-13", "Truffles", "Koramangala", "4.3"}, {"r-14", "Empire Restaurant", "Indiranagar", "4.0"},
            {"r-15", "CTR", "Malleshwaram", "4.7"}, {"r-16", "Nagarjuna", "Residency Road", "4.2"},
        };
        for (String[] r : rs) {
            restaurants.put(r[0], new Restaurant(r[0], r[1], r[2], Double.parseDouble(r[3])));
        }
        String[][] rd = {{"d-31", "Suresh K", "EV scooter"}, {"d-32", "Imran P", "Motorcycle"},
            {"d-33", "Lakshmi N", "EV scooter"}, {"d-34", "Arjun R", "Bicycle"}, {"d-35", "Farhan S", "Motorcycle"}};
        for (String[] r : rd) {
            riders.put(r[0], new Rider(r[0], r[1], r[2]));
        }
        String[] rid = {"r-11", "r-12", "r-11", "r-13", "r-14", "r-15", "r-11", "r-16", "r-12", "r-13"};
        String[] did = {"d-31", "d-32", "d-31", null, "d-33", "d-34", "d-35", "d-32", null, "d-33"};
        String[][] menu = {{"Chicken Biryani", "32000"}, {"Masala Dosa", "9000"}, {"Paneer Butter Masala", "28000"},
            {"Filter Coffee", "4000"}, {"Ghee Rice", "18000"}, {"Andhra Meals", "26000"}};
        for (int k = 0; k < 10; k++) {
            List<OrderItem> items = new ArrayList<>();
            int total = 0;
            for (int j = 0; j <= k % 3; j++) {
                String[] m = menu[(k + j * 2) % menu.length];
                int qty = 1 + (k + j) % 2;
                items.add(new OrderItem(m[0], qty, Integer.parseInt(m[1])));
                total += qty * Integer.parseInt(m[1]);
            }
            Status status = did[k] == null ? Status.CANCELLED : (k == 0 ? Status.PICKED_UP : Status.DELIVERED);
            orders.add(new Order("o-%04d".formatted(9001 + k), "c-1001", status, total,
                    java.time.Instant.parse("2026-10-06T14:45:00Z").minusSeconds(k * 31_500L).toString(),  // newest first
                    rid[k], did[k], items));
        }
        int id = 1;
        for (String r : restaurants.keySet()) {
            for (int k = 0; k < 40; k++) {
                reviews.add(new Review("v-" + id++, r, 3 + k % 3, "Review " + k + " of " + r));
            }
        }
    }

    // --- order-service ---
    public List<Order> recentOrders(String customerId, int first) {
        count("order-service: recent orders", first);
        return orders.stream().filter(o -> o.customerId().equals(customerId)).limit(first).toList();
    }

    // --- restaurant-service ---
    public Restaurant restaurant(String id) {
        count("restaurant-service: one", 1);
        return restaurants.get(id);
    }

    public Map<String, Restaurant> restaurants(Collection<String> ids) {
        Map<String, Restaurant> found = new LinkedHashMap<>();
        for (String id : new LinkedHashSet<>(ids)) {
            found.put(id, restaurants.get(id));
        }
        count("restaurant-service: batch", found.size());
        return found;
    }

    // --- rider-service ---
    public Rider rider(String id) {
        count("rider-service: one", 1);
        return riders.get(id);
    }

    public Map<String, Rider> riders(Collection<String> ids) {
        Map<String, Rider> found = new LinkedHashMap<>();
        for (String id : new LinkedHashSet<>(ids)) {
            found.put(id, riders.get(id));
        }
        count("rider-service: batch", found.size());
        return found;
    }

    // --- review-service ---
    public List<Review> reviews(String restaurantId, int first) {
        List<Review> page = reviews.stream().filter(r -> r.restaurantId().equals(restaurantId)).limit(first).toList();
        count("review-service: one restaurant", page.size());
        return page;
    }

    public Map<String, List<Review>> reviews(Collection<String> restaurantIds, int first) {
        Map<String, List<Review>> found = new LinkedHashMap<>();
        int n = 0;
        for (String id : new LinkedHashSet<>(restaurantIds)) {
            List<Review> page = reviews.stream().filter(r -> r.restaurantId().equals(id)).limit(first).toList();
            found.put(id, page);
            n += page.size();
        }
        count("review-service: batch", n);
        return found;
    }

    private void count(String what, int rowCount) {
        calls.merge(what, 1, Integer::sum);
        rows += rowCount;
    }

    public int totalCalls() {
        return calls.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int rows() {
        return rows;
    }

    public Map<String, Integer> calls() {
        return Map.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        rows = 0;
    }

    public List<Order> allOrders() {
        return List.copyOf(orders);
    }
}
