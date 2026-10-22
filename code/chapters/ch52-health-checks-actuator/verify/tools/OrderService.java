import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch52.availability.Availability;
import dev.foodfleet.ch52.health.HealthCheck;
import dev.foodfleet.ch52.health.HealthGroup;
import dev.foodfleet.ch52.pg.PgPing;
import dev.foodfleet.ch52.shutdown.GracefulShutdown;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * order-service for the chapter's Kubernetes experiments, configured the ways a Spring Boot service can be:
 * <ul>
 *   <li>port 8080: {@code GET /menu} (served from memory) and {@code POST /orders} (needs PostgreSQL)</li>
 *   <li>port 8081: {@code /actuator/health} (every check, including the database),
 *       {@code /actuator/health/liveness} and {@code /actuator/health/readiness}</li>
 * </ul>
 * Environment: {@code DB_HOST}; {@code STARTUP_SECONDS} (time before the ports open, standing in for a Spring Boot
 * context refresh: connection pools, caches, migrations); {@code LIVENESS_INCLUDES_DB} and
 * {@code READINESS_INCLUDES_DB} (health group membership); {@code GRACEFUL} (finish in-flight requests on SIGTERM);
 * {@code DRAIN_SECONDS} (refuse readiness and keep serving this long before stopping); {@code DB_PASSWORD} (only
 * held in memory, for the heap dump experiment).
 */
public final class OrderService {

    static final Availability AVAILABILITY = new Availability();

    public static void main(String[] args) throws Exception {
        Instant started = Instant.now();
        int startup = Integer.parseInt(env("STARTUP_SECONDS", "0"));
        log("starting; initialisation takes " + startup + " s");
        Thread.sleep(startup * 1000L);
        PgPing pg = new PgPing(env("DB_HOST", "127.0.0.1"), 5432, "postgres", "postgres", Duration.ofMillis(800));
        String password = env("DB_PASSWORD", "");          // a datasource keeps its password in memory
        HealthCheck db = () -> {
            pg.ping();
            return new HealthCheck.Result(dev.foodfleet.ch52.health.Status.UP, Map.of("database", "PostgreSQL", "validationQuery", "SELECT 1"));
        };
        Map<String, HealthCheck> all = new LinkedHashMap<>();
        all.put("db", db);
        all.put("livenessState", AVAILABILITY.livenessCheck());
        all.put("readinessState", AVAILABILITY.readinessCheck());
        Map<String, HealthCheck> liveness = new LinkedHashMap<>();
        liveness.put("livenessState", AVAILABILITY.livenessCheck());
        if (bool("LIVENESS_INCLUDES_DB")) {
            liveness.put("db", db);
        }
        Map<String, HealthCheck> readiness = new LinkedHashMap<>();
        readiness.put("readinessState", AVAILABILITY.readinessCheck());
        if (bool("READINESS_INCLUDES_DB")) {
            readiness.put("db", db);
        }
        Duration checkTimeout = Duration.ofMillis(900);
        HealthGroup healthAll = new HealthGroup("health", all, checkTimeout);
        HealthGroup healthLive = new HealthGroup("liveness", liveness, checkTimeout);
        HealthGroup healthReady = new HealthGroup("readiness", readiness, checkTimeout);

        GracefulShutdown shutdown = new GracefulShutdown(AVAILABILITY, Duration.ofSeconds(Integer.parseInt(env("DRAIN_SECONDS", "0"))),
                Duration.ofSeconds(20));
        HttpServer app = HttpServer.create(new InetSocketAddress(8080), 256);
        app.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        app.createContext("/menu", ex -> shutdown.track(() -> respond(ex, 200, "{\"items\":42}")));
        app.createContext("/orders", ex -> shutdown.track(() -> {
            try {
                Thread.sleep(40);                      // the order's own work
                pg.ping();                             // and its database round trip
                respond(ex, 201, "{\"order\":\"created\"}");
            } catch (Exception e) {
                respond(ex, 503, "{\"error\":\"database unavailable\"}");
            }
        }));
        HttpServer mgmt = HttpServer.create(new InetSocketAddress(8081), 64);
        mgmt.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        mgmt.createContext("/actuator/health", ex -> {
            String path = ex.getRequestURI().getPath();
            HealthGroup g = path.endsWith("/liveness") ? healthLive : path.endsWith("/readiness") ? healthReady : healthAll;
            HealthGroup.Report r = g.report();
            respond(ex, r.status().httpStatus(), r.json(true));
        });
        mgmt.start();
        app.start();
        AVAILABILITY.readiness(Availability.Readiness.ACCEPTING_TRAFFIC);
        log("started in " + Duration.between(started, Instant.now()).toMillis() + " ms; liveness " + liveness.keySet()
                + ", readiness " + readiness.keySet() + ", graceful " + bool("GRACEFUL") + ", drain " + env("DRAIN_SECONDS", "0") + " s"
                + (password.isEmpty() ? "" : ", database password loaded"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("SIGTERM; in flight: " + shutdown.inFlight());
            if (!bool("GRACEFUL")) {
                app.stop(0);
                mgmt.stop(0);
                log("stopped immediately");
                return;
            }
            try {
                int left = shutdown.shutdown(new GracefulShutdown.Server() {
                    @Override
                    public void stopAccepting() {
                        app.stop(20);              // stops accepting now, returns when in-flight exchanges finish (20 s at most)
                    }

                    @Override
                    public void close() {
                        app.stop(0);
                        mgmt.stop(0);
                    }
                });
                log("stopped gracefully; requests cut off: " + left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        Thread.currentThread().join();
    }

    static void respond(HttpExchange ex, int code, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        } finally {
            ex.close();
        }
    }

    static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : v;
    }

    static boolean bool(String k) {
        return Boolean.parseBoolean(env(k, "false"));
    }

    static void log(String s) {
        System.out.println(Instant.now() + " order-service " + s);
    }
}
