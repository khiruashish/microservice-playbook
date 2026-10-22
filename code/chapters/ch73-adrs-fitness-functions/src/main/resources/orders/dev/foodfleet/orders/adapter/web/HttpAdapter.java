package dev.foodfleet.orders.adapter.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.orders.api.OrderApi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class HttpAdapter {

    private final OrderApi api;

    public HttpAdapter(OrderApi api) {
        this.api = api;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 512);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/actuator/health", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        server.createContext("/orders/", ex -> {
            String id = ex.getRequestURI().getPath().substring("/orders/".length());
            api.get(id).ifPresentOrElse(
                    o -> reply(ex, 200, "{\"id\":\"" + o.id() + "\",\"restaurant\":\"" + o.restaurant() + "\",\"lines\":" + o.lines()
                            + ",\"totalInPaise\":" + o.totalInPaise() + ",\"placedAt\":\"" + o.placedAt() + "\",\"status\":\"" + o.status() + "\"}"),
                    () -> reply(ex, 404, "{}"));
        });
        server.start();
        return server;
    }

    static void reply(HttpExchange ex, int status, String body) {
        try (ex) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // the client went away
        }
    }
}
