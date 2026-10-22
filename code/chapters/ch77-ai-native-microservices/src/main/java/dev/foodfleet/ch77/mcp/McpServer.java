package dev.foodfleet.ch77.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch77.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * FoodFleet's MCP server for customer assistants, on the stateless Streamable HTTP transport of MCP 2026-07-28:
 * POST /mcp with one JSON-RPC request, one JSON response; no initialize handshake and no session. Every request
 * carries its protocol version in {@code _meta}, mirrored in the {@code MCP-Protocol-Version} header, and its method
 * (and tool name) in {@code Mcp-Method} and {@code Mcp-Name}; mismatches are rejected with 400 and HeaderMismatch.
 * <p>
 * Two tools: {@code search_menu} and {@code get_order_status}. Who the caller is comes from the bearer token, never
 * from tool arguments, unless the server is built {@code trustingArguments}: then {@code get_order_status} takes a
 * customer_id argument, as a naive integration would, and serves whatever the model (or text injected into its
 * context) asks for.
 */
public final class McpServer implements AutoCloseable {

    public static final String VERSION = "2026-07-28";
    public static final int HEADER_MISMATCH = -32020;
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    record Order(String id, String customer, String status) {
    }

    private final HttpServer server;
    private final boolean trustingArguments;
    private final Map<String, String> tokens;          // bearer token -> customer
    private final Map<String, Order> orders;
    private final List<String> menu = List.of("Paneer tikka", "Masala dosa", "Veg biryani", "Chicken biryani", "Filter coffee", "Gulab jamun");

    public McpServer(int port, boolean trustingArguments, Map<String, String> tokens, Map<String, String[]> ordersByCustomer) throws IOException {
        this.trustingArguments = trustingArguments;
        this.tokens = tokens;
        this.orders = new java.util.HashMap<>();
        ordersByCustomer.forEach((customer, ids) -> {
            for (String id : ids) {
                orders.put(id, new Order(id, customer, "OUT_FOR_DELIVERY"));
            }
        });
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 256);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/mcp", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String id = Json.raw(body, "id");
        String method = Json.string(body, "method");
        String meta = Json.raw(body, "_meta");
        String version = meta == null ? null : Json.string(meta, "io.modelcontextprotocol/protocolVersion");
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String customer = auth != null && auth.startsWith("Bearer ") ? tokens.get(auth.substring(7)) : null;
        if (customer == null) {
            ex.getResponseHeaders().set("WWW-Authenticate", "Bearer resource_metadata=\"/.well-known/oauth-protected-resource\"");
            reply(ex, 401, "");
            return;
        }
        // the standard headers must match the body: routing and policy may be decided on either
        String name = "tools/call".equals(method) ? Json.string(Json.raw(body, "params"), "name") : null;
        String hMethod = ex.getRequestHeaders().getFirst("Mcp-Method");
        String hName = ex.getRequestHeaders().getFirst("Mcp-Name");
        String hVersion = ex.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (hMethod == null || !hMethod.equals(method) || (name != null && !name.equals(hName)) || hVersion == null || !hVersion.equals(version)) {
            reply(ex, 400, error(id, HEADER_MISMATCH, "headers missing or not matching the body"));
            return;
        }
        if (!VERSION.equals(version)) {
            reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":" + UNSUPPORTED_PROTOCOL_VERSION
                    + ",\"message\":\"Unsupported protocol version\",\"data\":{\"supported\":[\"" + VERSION + "\"],\"requested\":" + Json.quote(String.valueOf(version)) + "}}}");
            return;
        }
        String result = switch (method) {
            case "server/discover" -> "{\"resultType\":\"complete\",\"supportedVersions\":[\"" + VERSION + "\"],\"capabilities\":{\"tools\":{}},"
                    + "\"instructions\":\"FoodFleet orders and menus for the signed-in customer.\",\"ttlMs\":3600000,\"cacheScope\":\"public\","
                    + "\"_meta\":{\"io.modelcontextprotocol/serverInfo\":{\"name\":\"foodfleet-mcp\",\"version\":\"1.0.0\"}}}";
            case "tools/list" -> "{\"resultType\":\"complete\",\"ttlMs\":300000,\"cacheScope\":\"public\",\"tools\":[" + tool("get_order_status",
                    "The status of one of the signed-in customer's orders.", trustingArguments ? "\"order_id\":{\"type\":\"string\"},\"customer_id\":{\"type\":\"string\"}" : "\"order_id\":{\"type\":\"string\"}")
                    + "," + tool("search_menu", "Menu items matching a query.", "\"query\":{\"type\":\"string\"}") + "]}";
            case "tools/call" -> call(name, Json.raw(Json.raw(body, "params"), "arguments"), customer);
            default -> null;
        };
        if (result == null) {
            reply(ex, 200, error(id, -32601, "Method not found"));
            return;
        }
        reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}");
    }

    private String call(String name, String args, String customer) {
        if ("search_menu".equals(name)) {
            String q = String.valueOf(Json.string(args, "query")).toLowerCase();
            List<String> hits = menu.stream().filter(m -> m.toLowerCase().contains(q)).toList();
            return text(String.join(", ", hits), false, "{\"items\":" + hits.stream().map(Json::quote).toList() + "}");
        }
        if ("get_order_status".equals(name)) {
            String orderId = Json.string(args, "order_id");
            String who = trustingArguments && Json.string(args, "customer_id") != null ? Json.string(args, "customer_id") : customer;
            Order o = orders.get(orderId);
            if (o == null || !o.customer().equals(who)) {
                // a tool error, not a protocol error: the model sees it and can recover
                return text("No order " + orderId + " for this customer.", true, null);
            }
            return text("Order " + o.id() + ": " + o.status(), false, "{\"orderId\":" + Json.quote(o.id()) + ",\"customer\":" + Json.quote(o.customer())
                    + ",\"status\":" + Json.quote(o.status()) + "}");
        }
        return text("Unknown tool " + name, true, null);
    }

    private static String text(String text, boolean isError, String structured) {
        return "{\"resultType\":\"complete\",\"content\":[{\"type\":\"text\",\"text\":" + Json.quote(text) + "}]" + (structured == null ? "" : ",\"structuredContent\":" + structured)
                + ",\"isError\":" + isError + "}";
    }

    private static String tool(String name, String description, String properties) {
        return "{\"name\":\"" + name + "\",\"description\":" + Json.quote(description) + ",\"inputSchema\":{\"type\":\"object\",\"properties\":{" + properties + "}}}";
    }

    private static String error(String id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + (id == null ? "null" : id) + ",\"error\":{\"code\":" + code + ",\"message\":" + Json.quote(message) + "}}";
    }

    private static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
