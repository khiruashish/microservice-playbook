package dev.foodfleet.ch63.menu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * menu-service, the provider: a restaurant's menu, and one item's price and availability.
 * <ul>
 *   <li>{@code GET /restaurants/{r}/menu}: {@code {"restaurantId", "currency", "items": [...]}}</li>
 *   <li>{@code GET /restaurants/{r}/items/{id}}: one item, or 404</li>
 * </ul>
 * The data is FoodFleet's: restaurant 7 (Spice Route, 6 items, the one every test uses) and restaurant 9 (Biryani
 * House, 45 items).
 */
public final class MenuService {

    public record Item(String id, String name, BigDecimal price, boolean available, String spice, String description, int calories) {
    }

    public static final Map<Integer, List<Item>> DATA = data();

    private MenuService() {
    }

    /** Runs the provider on the given port until killed. */
    public static void main(String[] args) throws IOException {
        start(Integer.parseInt(args[0]));
    }

    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 256);
        server.createContext("/restaurants/", MenuService::handle);
        server.start();
        return server;
    }

    static void handle(HttpExchange ex) {
        try (ex) {
            String[] p = ex.getRequestURI().getPath().split("/");
            // ["", "restaurants", "{r}", "menu"] or ["", "restaurants", "{r}", "items", "{id}"]
            int restaurant = Integer.parseInt(p[2]);
            List<Item> items = DATA.get(restaurant);
            if (items == null) {
                respond(ex, 404, "{\"error\":\"no such restaurant\"}");
            } else if (p.length == 4 && p[3].equals("menu")) {
                respond(ex, 200, menuJson(restaurant, items));
            } else if (p.length == 5 && p[3].equals("items")) {
                Optional<Item> item = items.stream().filter(i -> i.id().equals(p[4])).findFirst();
                if (item.isPresent()) {
                    respond(ex, 200, itemJson(item.get()));
                } else {
                    respond(ex, 404, "{\"error\":\"no such item\"}");
                }
            } else {
                respond(ex, 404, "{\"error\":\"not found\"}");
            }
        } catch (RuntimeException e) {
            respond(ex, 400, "{\"error\":\"bad request\"}");
        }
    }

    static String menuJson(int restaurant, List<Item> items) {
        List<Item> page = items;
        StringBuilder b = new StringBuilder("{\"restaurantId\":").append(restaurant).append(",\"currency\":\"INR\",\"items\":[");
        for (int k = 0; k < page.size(); k++) {
            b.append(k == 0 ? "" : ",").append(itemJson(page.get(k)));
        }
        return b.append("]}").toString();
    }

    static String itemJson(Item i) {
        StringBuilder b = new StringBuilder("{\"id\":\"").append(i.id()).append("\",\"name\":\"").append(i.name()).append('"');
        b.append(",\"price\":").append(price(i));
        b.append(",\"available\":").append(i.available());
        b.append(",\"spice\":\"").append(i.spice()).append('"');
        b.append(",\"description\":\"").append(i.description()).append('"');
        b.append(",\"calories\":").append(i.calories());
        return b.append('}').toString();
    }

    static String price(Item i) {
        return "\"" + i.price().toPlainString() + "\"";
    }

    static void respond(HttpExchange ex, int status, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        }
    }

    static Map<Integer, List<Item>> data() {
        Map<Integer, List<Item>> d = new LinkedHashMap<>();
        d.put(7, List.of(
                new Item("paneer-tikka", "Paneer Tikka", new BigDecimal("129.50"), true, "MEDIUM", "Cottage cheese, charred in the tandoor", 420),
                new Item("dal-makhani", "Dal Makhani", new BigDecimal("189.00"), true, "LOW", "Black lentils, slow-cooked overnight", 510),
                new Item("butter-naan", "Butter Naan", new BigDecimal("45.00"), true, "LOW", "Leavened bread with butter", 260),
                new Item("chicken-65", "Chicken 65", new BigDecimal("219.00"), true, "HIGH", "Fried chicken, curry leaves and chilli", 610),
                new Item("veg-biryani", "Veg Biryani", new BigDecimal("199.00"), true, "MEDIUM", "Basmati rice and vegetables, dum-cooked", 680),
                new Item("kulfi", "Kulfi", new BigDecimal("79.00"), false, "LOW", "Frozen milk dessert", 240)));
        List<Item> biryani = new ArrayList<>();
        String[] spices = {"LOW", "MEDIUM", "HIGH"};
        for (int k = 1; k <= 45; k++) {
            String spice = k == 45 ? "HIGH" : spices[k % 3];
            biryani.add(new Item("bh-" + k, "Biryani House dish " + k, new BigDecimal(150 + k * 5).setScale(2), k % 11 != 0, spice,
                    "House special number " + k, 400 + k * 7));
        }
        d.put(9, List.copyOf(biryani));
        return d;
    }
}
