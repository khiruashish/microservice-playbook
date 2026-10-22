package dev.foodfleet.ch65.chaos;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * menu-service (prices, 5 ms per request) and order-service, whose call to menu-service is the thing under test, in
 * three versions: no timeout; a 300 ms timeout with one retry; a 300 ms timeout with the last known price as fallback.
 */
public final class Services {

    public enum Client { NO_TIMEOUT, TIMEOUT_AND_RETRY, TIMEOUT_AND_FALLBACK }

    private Services() {
    }

    public static HttpServer menu() throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4096);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.createContext("/", ex -> {
            try (ex) {
                Thread.sleep(5);
                byte[] b = "{\"price\":\"129.50\"}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        s.start();
        return s;
    }

    /** order-service: POST-less for brevity: GET /orders/new?item=… prices the item at menu-service and answers 201. */
    public static HttpServer order(String menu, Client client) throws IOException {
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        Map<String, String> lastKnown = new ConcurrentHashMap<>();
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4096);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.createContext("/", ex -> {
            try (ex) {
                String item = "paneer-tikka";
                String price = null;
                int attempts = client == Client.TIMEOUT_AND_RETRY ? 2 : 1;
                for (int a = 0; a < attempts && price == null; a++) {
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(menu + "/restaurants/7/items/" + item));
                    if (client != Client.NO_TIMEOUT) {
                        b.timeout(Duration.ofMillis(300));
                    }
                    try {
                        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 200) {
                            price = r.body();
                            lastKnown.put(item, price);
                        }
                    } catch (HttpTimeoutException e) {
                        // try again, or fall back
                    }
                }
                if (price == null && client == Client.TIMEOUT_AND_FALLBACK) {
                    price = lastKnown.get(item);
                }
                int status = price == null ? 503 : 201;
                ex.sendResponseHeaders(status, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        s.start();
        return s;
    }
}
