package dev.foodfleet.ch66.backend;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A downstream service (menu, pricing, delivery ETA) that answers after a delay: {@code fastMillis} usually, and
 * {@code slowMillis} for {@code slowFraction} of requests, the way a GC pause, a cold cache or a busy disk makes a
 * small share of calls slow. Virtual threads, so the backend itself never queues.
 */
public final class Backend {

    private Backend() {
    }

    public static HttpServer start(int port, long fastMillis, long slowMillis, double slowFraction) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8192);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.createContext("/", ex -> {
            try (ex) {
                boolean slow = ThreadLocalRandom.current().nextDouble() < slowFraction;
                Thread.sleep(slow ? slowMillis : fastMillis);
                byte[] b = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        s.start();
        return s;
    }

    /** {@code Backend <port> <fast ms> <slow ms> <slow fraction>}: runs until killed. */
    public static void main(String[] args) throws IOException {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        // the JDK's server keeps at most 200 idle keep-alive connections by default and closes the rest, which a
        // client then finds closed when it reuses them; a busy caller holds more than that
        System.setProperty("sun.net.httpserver.maxIdleConnections", "4000");
        start(Integer.parseInt(args[0]), Long.parseLong(args[1]), Long.parseLong(args[2]), Double.parseDouble(args[3]));
    }
}
