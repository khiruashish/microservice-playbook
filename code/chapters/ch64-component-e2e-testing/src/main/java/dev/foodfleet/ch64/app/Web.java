package dev.foodfleet.ch64.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.json.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

/** The little HTTP plumbing the three services share. */
public final class Web {

    public record Response(int status, String body) {
        public Map<String, Object> json() {
            return Json.object(Json.parse(body));
        }
    }

    public interface Handler {
        Response handle(String method, String path, String body) throws Exception;
    }

    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private Web() {
    }

    public static HttpServer serve(int port, Handler handler) throws IOException {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 256);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", ex -> {
            Response r;
            try {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                r = handler.handle(ex.getRequestMethod(), ex.getRequestURI().getPath(), body);
            } catch (Exception e) {
                r = new Response(500, Json.write(Map.of("error", String.valueOf(e))));
            }
            send(ex, r);
        });
        server.start();
        return server;
    }

    static void send(HttpExchange ex, Response r) {
        try (ex) {
            byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                ex.getResponseBody().write(b);
            }
        } catch (IOException e) {
            // client went away
        }
    }

    public static Response call(String method, String url, String body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        } catch (IOException e) {
            return new Response(599, Json.write(Map.of("error", String.valueOf(e))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(599, "{}");
        }
    }

    public static Response json(int status, Object body) {
        return new Response(status, Json.write(body));
    }
}
