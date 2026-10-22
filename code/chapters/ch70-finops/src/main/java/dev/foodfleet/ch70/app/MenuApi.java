package dev.foodfleet.ch70.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Executors;

/**
 * Three kinds of FoodFleet workload in one program, for measuring what they really use against what they ask for:
 * <ul>
 *   <li>{@code api}: menu-service's {@code GET /menu}: renders a restaurant's menu as JSON, {@code RENDER} (15) times over
 *       (CPU per request)</li>
 *   <li>{@code cache}: a service that holds a large in-memory catalogue ({@code CACHE_MB}) and answers cheaply</li>
 *   <li>{@code batch}: a worker that is idle most of the time and busy on every core for a few seconds in each period</li>
 * </ul>
 * All of them answer {@code /ready}, and on SIGTERM stop being ready, wait for load balancers to notice, then finish
 * the requests in progress.
 */
public final class MenuApi {

    record Item(String name, long pricePaise, boolean veg) {
    }

    private MenuApi() {
    }

    static volatile boolean ready = true;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "api";
        System.setProperty("sun.net.httpserver.nodelay", "true");
        System.setProperty("sun.net.httpserver.maxIdleConnections", "4000");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 1024);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        List<List<Item>> menus = menus("cache".equals(mode) ? Integer.parseInt(env("CACHE_MB", "300")) : 2);
        int renderTimes = Integer.parseInt(env("RENDER", "15"));
        server.createContext("/menu", ex -> {
            int restaurant = Math.floorMod(ex.getRequestURI().getQuery() == null ? 0 : ex.getRequestURI().getQuery().hashCode(), menus.size());
            reply(ex, 200, "cache".equals(mode) ? String.valueOf(menus.get(restaurant).size()) : render(menus.get(restaurant), renderTimes));
        });
        server.createContext("/ready", ex -> reply(ex, ready ? 200 : 503, ready ? "ok" : "draining"));
        server.start();
        if ("batch".equals(mode)) {
            batch(Integer.parseInt(env("PERIOD_S", "30")), Integer.parseInt(env("BUSY_S", "6")));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ready = false; // fail readiness, so endpoints drop this pod
            sleep(Integer.parseInt(env("DRAIN_S", "5")) * 1000L);
            server.stop(5); // then finish what's in progress
        }));
    }

    /** Menus of 20 items; {@code mb} megabytes of them, roughly. */
    static List<List<Item>> menus(int mb) {
        SplittableRandom r = new SplittableRandom(7);
        int n = Math.max(50, mb * 600);
        List<List<Item>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Item> items = new ArrayList<>(20);
            for (int j = 0; j < 20; j++) {
                items.add(new Item("dish-" + i + "-" + j + "-" + r.nextInt(1000), 9_900 + r.nextInt(40_000), r.nextBoolean()));
            }
            out.add(items);
        }
        return out;
    }

    /** The menu as JSON, {@code times} over (a restaurant page renders the menu with its variants and offers). */
    static String render(List<Item> items, int times) {
        StringBuilder json = new StringBuilder(64 * 1024).append('[');
        for (int t = 0; t < times; t++) {
            for (Item it : items) {
                json.append("{\"name\":\"").append(it.name()).append("\",\"price\":").append(it.pricePaise() / 100.0).append(",\"veg\":").append(it.veg())
                        .append(",\"variant\":").append(t).append("},");
            }
        }
        return json.append(']').toString();
    }

    static void batch(int periodSeconds, int busySeconds) {
        int cores = Runtime.getRuntime().availableProcessors();
        Thread.ofPlatform().daemon().start(() -> {
            while (true) {
                long until = System.nanoTime() + busySeconds * 1_000_000_000L;
                List<Thread> workers = new ArrayList<>();
                for (int c = 0; c < cores; c++) {
                    workers.add(Thread.ofPlatform().start(() -> {
                        long x = 0;
                        while (System.nanoTime() < until) {
                            x += render(List.of(new Item("settlement", x, true)), 50).length();
                        }
                    }));
                }
                for (Thread w : workers) {
                    try {
                        w.join();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                sleep((periodSeconds - busySeconds) * 1000L);
            }
        });
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        try (ex) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            if ("close".equalsIgnoreCase(ex.getRequestHeaders().getFirst("Connection"))) {
                ex.getResponseHeaders().set("Connection", "close");
            }
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        }
    }

    static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
