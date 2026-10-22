package dev.foodfleet.ch61.order;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * order-service's HTTP API: {@code POST /orders} places an order (201, or 200 for a replay of the same idempotency
 * key), {@code GET /orders/{id}} reads one. Validation errors are 400 with a message.
 */
public final class OrderApi {

    private final OrderService service;
    private final OrderRepository orders;

    public OrderApi(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        server.createContext("/orders", this::handle);
        server.start();
        return server;
    }

    void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (ex.getRequestMethod().equals("POST") && path.equals("/orders")) {
                OrderRepository.Stored s = service.place(parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                respond(ex, s.created() ? 201 : 200, json(s.order()));
            } else if (ex.getRequestMethod().equals("GET") && path.startsWith("/orders/")) {
                var o = orders.findById(Long.parseLong(path.substring("/orders/".length())));
                if (o.isPresent()) {
                    respond(ex, 200, json(o.get()));
                } else {
                    respond(ex, 404, "{\"error\":\"not found\"}");
                }
            } else {
                respond(ex, 405, "{\"error\":\"method not allowed\"}");
            }
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        } catch (RuntimeException e) {
            respond(ex, 500, "{\"error\":\"internal\"}");
        }
    }

    @SuppressWarnings("unchecked")
    static Order.Request parse(String body) {
        Map<String, Object> m;
        try {
            m = Json.object(body);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed JSON");
        }
        List<Order.Item> items = new ArrayList<>();
        if (m.get("items") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> i = (Map<String, Object>) o;
                items.add(new Order.Item((String) i.get("itemId"), ((Number) i.get("quantity")).intValue()));
            }
        }
        Object restaurant = m.get("restaurantId");
        return new Order.Request((String) m.get("idempotencyKey"), (String) m.get("customerId"), restaurant == null ? 0 : ((Number) restaurant).intValue(),
                items, (String) m.get("coupon"));
    }

    static String json(Order o) {
        StringBuilder lines = new StringBuilder();
        for (Order.Line l : o.lines()) {
            lines.append(lines.isEmpty() ? "" : ",").append("{\"itemId\":\"").append(l.itemId()).append("\",\"quantity\":").append(l.quantity())
                    .append(",\"unitPrice\":\"").append(l.unitPrice().toPlainString()).append("\"}");
        }
        return "{\"id\":" + o.id() + ",\"customerId\":\"" + o.customerId() + "\",\"restaurantId\":" + o.restaurantId() + ",\"lines\":[" + lines
                + "],\"subtotal\":\"" + o.subtotal().toPlainString() + "\",\"discount\":\"" + o.discount().toPlainString() + "\",\"tax\":\""
                + o.tax().toPlainString() + "\",\"total\":\"" + o.total().toPlainString() + "\",\"status\":\"" + o.status() + "\"}";
    }

    static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
