import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch53.container.ContainerLimits;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * menu-service for the chapter's container experiments: an HTTP server with SLF4J/Logback logging and Micrometer
 * metrics, the libraries a Spring Boot service also loads. Logs its container limits at startup, serves GET /menu and
 * GET /actuator/prometheus, and stops gracefully on SIGTERM. {@code --train} serves a few requests to itself and
 * exits: the training run that records an AOT cache.
 */
public final class MenuService {

    static final Logger LOG = LoggerFactory.getLogger("menu-service");

    public static void main(String[] args) throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer timer = Timer.builder("http.server.requests").tag("uri", "/menu").publishPercentileHistogram().register(registry);
        LOG.info(ContainerLimits.describe(ContainerLimits.read(Path.of("/sys/fs/cgroup")), ContainerLimits.jvm()));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 128);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu", ex -> timer.record(() -> {
            try {
                byte[] b = "{\"items\":[\"masala dosa\",\"idli\",\"vada\"]}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            } catch (java.io.IOException e) {
                // client went away
            } finally {
                ex.close();
            }
        }));
        server.createContext("/actuator/prometheus", ex -> {
            byte[] b = registry.scrape().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        LOG.info("started in {} ms", System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("SIGTERM received: stopping gracefully");
            server.stop(1);
            LOG.info("stopped");
        }));
        if (args.length > 0 && args[0].equals("--train")) {
            for (int i = 0; i < 200; i++) {
                for (String p : new String[] {"/menu", "/actuator/prometheus"}) {
                    try (var in = URI.create("http://127.0.0.1:8080" + p).toURL().openStream()) {
                        in.readAllBytes();
                    }
                }
            }
            LOG.info("training run done");
            server.stop(0);
            System.exit(0);
        }
    }
}
