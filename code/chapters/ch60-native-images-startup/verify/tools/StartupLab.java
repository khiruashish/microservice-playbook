import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch60.menu.MenuCatalog;
import dev.foodfleet.ch60.startup.Warmup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * menu-service for the startup experiments: Logback, Micrometer with the Prometheus registry, and a menu endpoint
 * doing real work ({@link MenuCatalog}). The same code runs on the JVM, from a CDS archive or AOT cache, and as a
 * GraalVM native image.
 * <ul>
 *   <li>{@code /menu?r=N}: restaurant N's menu, 150 items, with today's discount</li>
 *   <li>{@code /actuator/prometheus}, {@code /ready}</li>
 * </ul>
 * {@code WARMUP=N} runs N menu renders before the server opens. The training run for AOT caches, CDS archives and
 * the native-image agent is {@link Trainer}; {@code --train-core} is a training run that only renders 3,000 menus,
 * without the HTTP server, so misses part of the startup path.
 */
public final class StartupLab {

    static final Logger LOG = LoggerFactory.getLogger("menu-service");

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--train-core")) {
            int discount = Integer.parseInt(System.getenv().getOrDefault("DISCOUNT", "10"));
            Warmup.Result r = Warmup.run(i -> MenuCatalog.render(MenuCatalog.menu(i % 500, 150), discount), 3000, 60_000);
            LOG.info("training run without HTTP: {} renders in {} ms", r.iterations(), r.millis());
            return;
        }
        start(8080);
    }

    /** Starts the service on {@code port} (0: any free port); {@link Trainer} uses this for training runs. */
    static HttpServer start(int port) throws Exception {
        // the JDK's HTTP server writes headers and body separately; without TCP_NODELAY, Nagle's algorithm and the
        // client's delayed ACK add about 40 ms to every response on loopback
        System.setProperty("sun.net.httpserver.nodelay", "true");
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer timer = Timer.builder("http.server.requests").tag("uri", "/menu").publishPercentileHistogram().register(registry);
        Counter served = registry.counter("menu.served");
        AtomicLong count = new AtomicLong();
        int discount = Integer.parseInt(System.getenv().getOrDefault("DISCOUNT", "10"));
        int warm = Integer.parseInt(System.getenv().getOrDefault("WARMUP", "0"));
        if (warm > 0) {
            Warmup.Result r = Warmup.run(i -> {
                timer.record(() -> MenuCatalog.render(MenuCatalog.menu(i % 500, 150), discount));
                if (i % 500 == 0) {
                    LOG.info("warm-up {}", i);
                    registry.scrape();
                }
            }, warm, 60_000);
            LOG.info("warmed up: {} renders in {} ms", r.iterations(), r.millis());
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 1024);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.createContext("/menu", ex -> {
            String q = ex.getRequestURI().getQuery();
            int restaurant = q == null ? 1 : Integer.parseInt(q.substring(q.indexOf('=') + 1));
            String body = timer.record(() -> MenuCatalog.render(MenuCatalog.menu(restaurant, 150), discount));
            served.increment();
            if (count.incrementAndGet() % 1000 == 0) {
                LOG.info("served {}", count.get());
            }
            respond(ex, body);
        });
        server.createContext("/actuator/prometheus", ex -> respond(ex, registry.scrape()));
        server.createContext("/ready", ex -> respond(ex, "ok"));
        server.start();
        LOG.info("started in {} ms", System.currentTimeMillis() - startMillis());
        return server;
    }

    static long startMillis() {
        try {
            return ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    static void respond(HttpExchange ex, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        } finally {
            ex.close();
        }
    }
}
