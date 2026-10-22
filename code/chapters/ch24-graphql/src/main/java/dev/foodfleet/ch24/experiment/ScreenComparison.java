package dev.foodfleet.ch24.experiment;

import dev.foodfleet.ch24.experiment.FoodFleetBackends.Order;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Restaurant;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Rider;
import dev.foodfleet.ch24.mini.Engine;
import dev.foodfleet.ch24.mini.Json;
import dev.foodfleet.ch24.mini.Query;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * The "My orders" screen four ways: what the phone sends and receives, how many round trips it
 * makes, and how many calls reach the services behind it.
 *
 * <ol>
 *   <li>REST resources, composed by the app: orders, then each restaurant and rider (parallel).</li>
 *   <li>GraphQL with per-item resolvers (the N+1 problem).</li>
 *   <li>GraphQL with batch resolvers (DataLoader / {@code @BatchMapping}).</li>
 *   <li>A hand-written BFF endpoint for this screen (chapter 10).</li>
 * </ol>
 * Latency model: 80 ms mobile RTT per sequential wave of requests, 4 Mbit/s down and 1 Mbit/s up for
 * the gzipped bytes, {@value FoodFleetBackends#MS_PER_CALL} ms per backend call made one after another
 * (blocking resolvers), or per wave where calls run in parallel.
 */
public final class ScreenComparison {

    public static final String SCREEN_QUERY = """
            query MyOrders {
              myOrders(first: 10) {
                id status totalPaise placedAt
                restaurant { name area }
                rider { name }
                items { name quantity }
              }
            }
            """;

    static final double RTT_MS = 80;
    static final double DOWN_BYTES_PER_MS = 500;      // 4 Mbit/s
    static final double UP_BYTES_PER_MS = 125;        // 1 Mbit/s

    public record Row(String approach, int phoneRequests, int waves, int requestBytes, int responseBytes,
                      int responseGzipBytes, int backendCalls, double latencyMs) {
    }

    public static List<Row> run() {
        FoodFleetBackends b = new FoodFleetBackends();
        List<Row> rows = new ArrayList<>();
        rows.add(rest(b));
        rows.add(graphql(b, Engine.Mode.PER_ITEM, "GraphQL, per-item resolvers"));
        rows.add(graphql(b, Engine.Mode.BATCHED, "GraphQL, batched (DataLoader)"));
        rows.add(bff(b));
        return rows;
    }

    /** The GraphQL response for the screen, as JSON (also used by the tests). */
    public static String graphqlResponse(Engine.Mode mode) {
        FoodFleetBackends b = new FoodFleetBackends();
        return Json.write(FoodFleetGraph.engine(b).execute(Query.parse(SCREEN_QUERY), mode, "root"));
    }

    static Row rest(FoodFleetBackends b) {
        b.reset();
        List<String> bodies = new ArrayList<>();
        int requestBytes = "GET /customers/me/orders?limit=10".length();
        List<Order> orders = b.recentOrders(FoodFleetGraph.CUSTOMER, 10);
        bodies.add(Json.write(orders.stream().map(ScreenComparison::fullOrder).toList()));
        // second wave, in parallel: every distinct restaurant and rider (the app de-duplicates)
        for (String id : new LinkedHashSet<>(orders.stream().map(Order::restaurantId).toList())) {
            requestBytes += ("GET /restaurants/" + id).length();
            bodies.add(Json.write(fullRestaurant(b.restaurant(id))));
        }
        for (String id : new LinkedHashSet<>(orders.stream().map(Order::riderId).filter(x -> x != null).toList())) {
            requestBytes += ("GET /riders/" + id).length();
            bodies.add(Json.write(fullRider(b.rider(id))));
        }
        int raw = bodies.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
        int gz = bodies.stream().mapToInt(ScreenComparison::gzip).sum();      // each response compressed on its own
        double backend = 2 * FoodFleetBackends.MS_PER_CALL;                    // one wave, then a parallel wave
        return new Row("REST resources, composed by the app", bodies.size(), 2, requestBytes, raw, gz,
                b.totalCalls(), latency(2, requestBytes, gz, backend));
    }

    static Row graphql(FoodFleetBackends b, Engine.Mode mode, String label) {
        b.reset();
        String body = Json.write(FoodFleetGraph.engine(b).execute(Query.parse(SCREEN_QUERY), mode, "root"));
        int requestBytes = Json.write(Json.obj("query", SCREEN_QUERY)).getBytes(StandardCharsets.UTF_8).length;
        int raw = body.getBytes(StandardCharsets.UTF_8).length;
        int gz = gzip(body);
        double backend = b.totalCalls() * FoodFleetBackends.MS_PER_CALL;      // blocking resolvers, one after another
        return new Row(label, 1, 1, requestBytes, raw, gz, b.totalCalls(), latency(1, requestBytes, gz, backend));
    }

    static Row bff(FoodFleetBackends b) {
        b.reset();
        List<Order> orders = b.recentOrders(FoodFleetGraph.CUSTOMER, 10);
        Map<String, Restaurant> restaurants = b.restaurants(orders.stream().map(Order::restaurantId).toList());
        Map<String, Rider> riders = b.riders(orders.stream().map(Order::riderId).filter(x -> x != null).toList());
        List<Object> screen = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id());
            m.put("status", o.status().name());
            m.put("totalPaise", o.totalPaise());
            m.put("placedAt", o.placedAt());
            Restaurant r = restaurants.get(o.restaurantId());
            m.put("restaurant", Json.obj("name", r.name(), "area", r.area()));
            m.put("rider", o.riderId() == null ? null : Json.obj("name", riders.get(o.riderId()).name()));
            m.put("items", o.items().stream().map(i -> Json.obj("name", i.name(), "quantity", i.quantity())).toList());
            screen.add(m);
        }
        String body = Json.write(Json.obj("orders", screen));
        int requestBytes = "GET /bff/customer/my-orders".length();
        double backend = 2 * FoodFleetBackends.MS_PER_CALL;                    // orders, then restaurants + riders in parallel
        return new Row("BFF endpoint for this screen", 1, 1, requestBytes, body.getBytes(StandardCharsets.UTF_8).length,
                gzip(body), b.totalCalls(), latency(1, requestBytes, gzip(body), backend));
    }

    static double latency(int waves, int requestBytes, int responseGzipBytes, double backendMs) {
        return waves * RTT_MS + requestBytes / UP_BYTES_PER_MS + responseGzipBytes / DOWN_BYTES_PER_MS + backendMs;
    }

    // Full REST representations: what a resource endpoint returns to every client.
    static Map<String, Object> fullOrder(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("customerId", o.customerId());
        m.put("status", o.status().name());
        m.put("placedAt", o.placedAt());
        m.put("updatedAt", o.placedAt());
        int items = o.items().stream().mapToInt(i -> i.quantity() * i.pricePaise()).sum();
        m.put("charges", Json.obj("itemsPaise", items, "deliveryFeePaise", 3900, "packagingPaise", 1500,
                "taxesPaise", items / 20, "discountPaise", 0, "totalPaise", o.totalPaise()));
        m.put("payment", Json.obj("method", "UPI", "status", "CAPTURED", "reference", "pay_" + o.id().substring(2) + "XK29QZ"));
        m.put("deliveryAddress", Json.obj("line1", "Flat 402, Prestige Shantiniketan", "line2", "Whitefield Main Road",
                "area", "Whitefield", "city", "Bengaluru", "pincode", "560048", "latitude", 12.9915, "longitude", 77.7279));
        m.put("restaurantId", o.restaurantId());
        m.put("riderId", o.riderId());
        m.put("items", o.items().stream().map(i -> Json.obj("menuItemId", "m-" + Math.abs(i.name().hashCode() % 10000),
                "name", i.name(), "quantity", i.quantity(), "pricePaise", i.pricePaise(), "customisations", List.of(),
                "notes", "")).toList());
        m.put("_links", Json.obj("self", "/orders/" + o.id(), "restaurant", "/restaurants/" + o.restaurantId(),
                "rider", o.riderId() == null ? "" : "/riders/" + o.riderId(), "invoice", "/orders/" + o.id() + "/invoice",
                "support", "/support/orders/" + o.id()));
        return m;
    }

    static Map<String, Object> fullRestaurant(Restaurant r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("name", r.name());
        m.put("area", r.area());
        m.put("address", Json.obj("line1", "No. 124, 1st Cross", "area", r.area(), "city", "Bengaluru", "pincode", "560034"));
        m.put("location", Json.obj("latitude", 12.9352, "longitude", 77.6245));
        m.put("cuisines", List.of("South Indian", "Biryani", "North Indian"));
        m.put("rating", r.rating());
        m.put("ratingCount", 18432);
        m.put("priceForTwoPaise", 60000);
        m.put("avgPrepMinutes", 18);
        m.put("isOpen", true);
        List<Object> hours = new ArrayList<>();
        for (String d : List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) {
            hours.add(Json.obj("day", d, "opens", "11:00", "closes", "23:30"));
        }
        m.put("openingHours", hours);
        m.put("fssaiLicence", "11221333000" + r.id().substring(2));
        m.put("images", List.of("https://img.foodfleet.dev/r/" + r.id() + "/cover.webp",
                "https://img.foodfleet.dev/r/" + r.id() + "/logo.webp"));
        m.put("_links", Json.obj("self", "/restaurants/" + r.id(), "menu", "/restaurants/" + r.id() + "/menu",
                "reviews", "/restaurants/" + r.id() + "/reviews"));
        return m;
    }

    static Map<String, Object> fullRider(Rider r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("name", r.name());
        m.put("vehicle", r.vehicle());
        m.put("vehicleNumber", "KA-01-XX-" + r.id().substring(2) + "17");
        m.put("rating", 4.8);
        m.put("deliveriesCompleted", 2140);
        m.put("languages", List.of("Kannada", "Hindi", "English"));
        m.put("photoUrl", "https://img.foodfleet.dev/d/" + r.id() + ".webp");
        m.put("_links", Json.obj("self", "/riders/" + r.id()));
        return m;
    }

    static int gzip(String s) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(s.getBytes(StandardCharsets.UTF_8));
            }
            return out.size();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Request body size when the app sends a persisted query's hash instead of the document. */
    public static int persistedQueryRequestBytes() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(SCREEN_QUERY.getBytes(StandardCharsets.UTF_8)));
        return Json.write(Json.obj("extensions", Json.obj("persistedQuery", Json.obj("version", 1, "sha256Hash", hash))))
                .getBytes(StandardCharsets.UTF_8).length;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\"My orders\" screen: 10 orders, 6 restaurants, 5 riders\n");
        System.out.printf("%-38s %8s %6s %8s %9s %8s %8s %10s%n", "approach", "requests", "waves", "req B",
                "resp B", "gzip B", "backend", "latency ms");
        for (Row r : run()) {
            System.out.printf("%-38s %8d %6d %8d %9d %8d %8d %10.0f%n", r.approach(), r.phoneRequests(), r.waves(),
                    r.requestBytes(), r.responseBytes(), r.responseGzipBytes(), r.backendCalls(), r.latencyMs());
        }
        FoodFleetBackends b = new FoodFleetBackends();
        graphql(b, Engine.Mode.PER_ITEM, "");
        System.out.println("\nper-item resolvers, calls by service: " + new java.util.TreeMap<>(b.calls()));
        graphql(b, Engine.Mode.BATCHED, "");
        System.out.println("batched resolvers,  calls by service: " + new java.util.TreeMap<>(b.calls()));
        System.out.println("\nGraphQL request body: " + graphql(b, Engine.Mode.BATCHED, "").requestBytes()
                + " bytes; as a persisted query hash: " + persistedQueryRequestBytes() + " bytes");
        System.out.println("identical responses, per-item vs batched: "
                + graphqlResponse(Engine.Mode.PER_ITEM).equals(graphqlResponse(Engine.Mode.BATCHED)));
    }
}
