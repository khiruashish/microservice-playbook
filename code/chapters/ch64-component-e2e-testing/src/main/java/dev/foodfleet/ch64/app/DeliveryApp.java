package dev.foodfleet.ch64.app;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.json.Json;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * delivery-service: accepts a delivery at once (202) and assigns a courier later, after a delay drawn from a
 * log-normal distribution with median {@code medianMillis} and shape {@code sigma}: usually quick, sometimes slow,
 * like matching a courier at lunch.
 */
public final class DeliveryApp {

    private DeliveryApp() {
    }

    public static HttpServer start(int port, double medianMillis, double sigma, long seed) throws IOException {
        Map<String, String> status = new ConcurrentHashMap<>();
        ScheduledExecutorService couriers = Executors.newScheduledThreadPool(2);
        Random random = new Random(seed);
        return Web.serve(port, (method, path, body) -> {
            String[] p = path.split("/"); // /deliveries[/{orderId}]
            if (method.equals("POST") && p.length == 2) {
                String order = String.valueOf(Json.object(Json.parse(body)).get("orderId"));
                status.put(order, "PENDING");
                long delay;
                synchronized (random) {
                    delay = Math.round(medianMillis * Math.exp(sigma * random.nextGaussian()));
                }
                couriers.schedule(() -> status.put(order, "COURIER_ASSIGNED"), delay, TimeUnit.MILLISECONDS);
                return Web.json(202, Map.of("orderId", order, "status", "PENDING"));
            }
            if (method.equals("GET") && p.length == 3) {
                String s = status.get(p[2]);
                return s == null ? Web.json(404, Map.of("error", "no such delivery")) : Web.json(200, Map.of("orderId", p[2], "status", s));
            }
            return Web.json(404, Map.of("error", "not found"));
        });
    }
}
