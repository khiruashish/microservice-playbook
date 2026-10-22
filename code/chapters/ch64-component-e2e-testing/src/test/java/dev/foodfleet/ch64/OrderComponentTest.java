package dev.foodfleet.ch64;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.app.OrderApp;
import dev.foodfleet.ch64.app.Web;
import dev.foodfleet.ch64.json.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Component tests: order-service started for real, on PostgreSQL in a container, with menu- and delivery-service
 * replaced by stubs that behave as their contracts say. Every test uses its own customer, so tests don't collide.
 */
@Tag("component")
class OrderComponentTest {

    static String containerId;
    static HttpServer menu;
    static HttpServer delivery;
    static HttpServer order;
    static String base;
    static final AtomicBoolean soldOut = new AtomicBoolean();

    @BeforeAll
    static void start() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5432", "-e", "POSTGRES_HOST_AUTH_METHOD=trust",
                System.getProperty("ff.pg.image", "c62/postgres:16"), "-c", "fsync=off").strip();
        String port = docker("port", containerId, "5432/tcp").strip().lines().findFirst().orElseThrow().replaceAll(".*:", "");
        String jdbc = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        for (int i = 0; ; i++) {
            try (Connection c = DriverManager.getConnection(jdbc, "postgres", "")) {
                break;
            } catch (Exception e) {
                if (i > 600) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
        menu = Web.serve(0, (method, path, body) -> {
            if (path.endsWith("/reserve")) {
                return soldOut.get() ? Web.json(409, Map.of("error", "sold out")) : Web.json(200, Map.of("reserved", true));
            }
            return path.endsWith("/paneer-tikka") ? Web.json(200, Map.of("price", "129.50", "available", true)) : Web.json(404, Map.of("error", "no such item"));
        });
        delivery = Web.serve(0, (method, path, body) -> method.equals("POST") ? Web.json(202, Map.of("status", "PENDING"))
                : Web.json(200, Map.of("orderId", path.substring(path.lastIndexOf('/') + 1), "status", "COURIER_ASSIGNED")));
        order = OrderApp.start(0, "http://127.0.0.1:" + menu.getAddress().getPort(), "http://127.0.0.1:" + delivery.getAddress().getPort(), OrderApp.jdbc(jdbc));
        base = "http://127.0.0.1:" + order.getAddress().getPort();
    }

    @AfterAll
    static void stop() throws Exception {
        order.stop(0);
        menu.stop(0);
        delivery.stop(0);
        docker("rm", "-f", containerId);
    }

    static Web.Response place(String customer, String item, String coupon) {
        return Web.call("POST", base + "/orders", "{\"customerId\":\"" + customer + "\",\"restaurantId\":7,\"itemId\":\"" + item + "\""
                + (coupon == null ? "" : ",\"coupon\":\"" + coupon + "\"") + "}");
    }

    @Test
    void firstCouponOrderGetsTwentyPercentOff() {
        Web.Response r = place("c-" + UUID.randomUUID(), "paneer-tikka", "FIRST20");
        assertEquals(201, r.status());
        assertEquals("25.90", r.json().get("discount"));
        assertEquals("103.60", r.json().get("total"));
    }

    @Test
    void theCouponWorksOncePerCustomer() {
        String customer = "c-" + UUID.randomUUID();
        place(customer, "paneer-tikka", "FIRST20");
        assertEquals("0.00", place(customer, "paneer-tikka", "FIRST20").json().get("discount"));
    }

    @Test
    void anUnknownItemIs400() {
        assertEquals(400, place("c-" + UUID.randomUUID(), "pizza", null).status());
    }

    @Test
    void aSoldOutDishIs409() {
        soldOut.set(true);
        try {
            assertEquals(409, place("c-" + UUID.randomUUID(), "paneer-tikka", null).status());
        } finally {
            soldOut.set(false);
        }
    }

    @Test
    void anOrderShowsItsDeliveryStatus() {
        Object id = place("c-" + UUID.randomUUID(), "paneer-tikka", null).json().get("id");
        Map<String, Object> shown = Json.object(Json.parse(Web.call("GET", base + "/orders/" + id, null).body()));
        assertEquals("COURIER_ASSIGNED", shown.get("delivery"));
    }

    static String docker(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "docker";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }
}
