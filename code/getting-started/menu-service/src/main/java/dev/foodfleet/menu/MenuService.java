package dev.foodfleet.menu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * FoodFleet's first microservice: menu-service, with nothing but the JDK.
 * <ul>
 *   <li>GET /restaurants/{id}/menu (optionally ?veg=true): the menu as JSON</li>
 *   <li>GET /health: 200 when the service can serve</li>
 *   <li>anything else: an RFC 9457 problem document, never an HTML error page</li>
 * </ul>
 * One virtual thread per request. Port from PORT (default 8080), as a container platform sets it.
 * Run without a build: {@code java src/main/java/dev/foodfleet/menu/MenuService.java}
 */
public final class MenuService {

    static final Map<String, List<MenuItem>> MENUS = Map.of(
            "r-1", List.of(new MenuItem("m-1", "Paneer tikka", 24_900, true), new MenuItem("m-2", "Chicken biryani", 32_900, false),
                    new MenuItem("m-3", "Masala dosa", 14_900, true)),
            "r-2", List.of(new MenuItem("m-4", "Filter coffee", 4_900, true)));

    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health", ex -> send(ex, 200, "application/json", "{\"status\":\"UP\"}"));
        server.createContext("/", MenuService::route);
        server.start();
        return server;
    }

    static void route(HttpExchange ex) throws IOException {
        String[] path = ex.getRequestURI().getPath().split("/");   // "", "restaurants", "{id}", "menu"
        if (!ex.getRequestMethod().equals("GET")) {
            problem(ex, 405, "Method not allowed", ex.getRequestMethod() + " is not supported here");
        } else if (path.length == 4 && path[1].equals("restaurants") && path[3].equals("menu")) {
            List<MenuItem> menu = MENUS.get(path[2]);
            if (menu == null) {
                problem(ex, 404, "Restaurant not found", "No restaurant with id " + path[2]);
                return;
            }
            boolean vegOnly = "veg=true".equals(ex.getRequestURI().getQuery());
            String items = menu.stream().filter(i -> !vegOnly || i.vegetarian()).map(MenuItem::json).collect(Collectors.joining(","));
            send(ex, 200, "application/json", "{\"restaurantId\":\"" + path[2] + "\",\"items\":[" + items + "]}");
        } else {
            problem(ex, 404, "Not found", "No resource at " + ex.getRequestURI().getPath());
        }
    }

    /** RFC 9457 Problem Details: the error format every FoodFleet API uses. */
    static void problem(HttpExchange ex, int status, String title, String detail) throws IOException {
        send(ex, status, "application/problem+json", "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"detail\":\""
                + detail.replace("\"", "\\\"") + "\",\"instance\":\"" + ex.getRequestURI().getPath() + "\"}");
    }

    static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        start(port);
        System.out.println("menu-service listening on " + port);
    }
}
