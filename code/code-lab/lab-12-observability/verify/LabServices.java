import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lab 12's three stand-in services on the JDK's HTTP server, with no tracing code at all: the OpenTelemetry Java
 * agent does it. Run as {@code java -javaagent:... LabServices.java order|menu|kitchen <port> [downstream ports]}.
 *   menu    GET  /menus/{id}    200 after ~5 ms; /menus/missing answers 404
 *   kitchen POST /tickets        202
 *   order   POST /orders?restaurant=r-1&handoff=executor|thread
 *           calls menu, then notifies kitchen on another thread: through an executor, or a bare new Thread()
 */
public final class LabServices {

    static final Logger LOG = Logger.getLogger("foodfleet");
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ExecutorService NOTIFIER = Executors.newFixedThreadPool(4);

    public static void main(String[] args) throws IOException {
        String role = args[0];
        int port = Integer.parseInt(args[1]);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        server.setExecutor(Executors.newFixedThreadPool(16));
        switch (role) {
            case "menu" -> server.createContext("/menus/", ex -> {
                String id = ex.getRequestURI().getPath().substring("/menus/".length());
                sleep(5);
                if (id.equals("missing")) {
                    LOG.warning("menu not found: " + id);
                    reply(ex, 404, "{\"error\":\"not found\"}");
                } else {
                    reply(ex, 200, "{\"restaurant\":\"" + id + "\",\"items\":3}");
                }
            });
            case "kitchen" -> server.createContext("/tickets", ex -> {
                LOG.info("ticket created");
                reply(ex, 202, "{}");
            });
            case "order" -> {
                int menuPort = Integer.parseInt(args[2]);
                int kitchenPort = Integer.parseInt(args[3]);
                server.createContext("/orders", ex -> {
                    String query = ex.getRequestURI().getQuery();
                    String restaurant = param(query, "restaurant", "r-1");
                    String handoff = param(query, "handoff", "executor");
                    int menu = call("GET", "http://127.0.0.1:" + menuPort + "/menus/" + restaurant);
                    if (menu != 200) {
                        LOG.severe("order rejected: menu answered " + menu);
                        reply(ex, 502, "{\"error\":\"menu unavailable\"}");
                        return;
                    }
                    Runnable notifyKitchen = () -> call("POST", "http://127.0.0.1:" + kitchenPort + "/tickets");
                    if (handoff.equals("thread")) {
                        new Thread(notifyKitchen).start();          // the agent can't follow this hand-off
                    } else {
                        NOTIFIER.submit(notifyKitchen);              // executors are instrumented: context travels
                    }
                    LOG.info("order placed for " + restaurant);
                    reply(ex, 201, "{\"status\":\"PLACED\"}");
                });
            }
            default -> throw new IllegalArgumentException(role);
        }
        server.start();
        System.out.println(role + " listening on " + port);
    }

    static int call(String method, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build();
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | InterruptedException e) {
            return 599;
        }
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static String param(String query, String name, String fallback) {
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith(name + "=")) {
                    return pair.substring(name.length() + 1);
                }
            }
        }
        return fallback;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
