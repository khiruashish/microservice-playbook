import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch54.config.MountedConfig;
import dev.foodfleet.ch54.runtime.PodInfo;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * menu-service for the chapter's Kubernetes experiments: SLF4J/Logback and Micrometer like a Spring Boot service, plus
 * endpoints that show what Kubernetes gives it.
 * <ul>
 *   <li>{@code /whoami}: the pod's name (Downward API)</li>
 *   <li>{@code /config}: the discount from an environment variable, a ConfigMap directory read once at startup, the
 *       same directory watched, and a file mounted with subPath</li>
 *   <li>{@code /work?ms=N}: N ms of CPU time, so CPU throttling shows up as wall-clock time</li>
 *   <li>{@code /cpustat}: the container's cgroup cpu.stat (throttling counters)</li>
 * </ul>
 */
public final class KubeLab {

    static final Logger LOG = LoggerFactory.getLogger("menu-service");
    static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        PodInfo pod = PodInfo.from(System.getenv());
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer timer = Timer.builder("http.server.requests").publishPercentileHistogram().register(registry);
        Path dir = Path.of("/etc/menu-config");
        Map<String, String> once = MountedConfig.readOnce(dir);
        MountedConfig watched = MountedConfig.watch(dir, 1, TimeUnit.SECONDS, v -> LOG.info("config changed: {}", v));
        LOG.info("pod {} in {} on {}; cpu limit {}m, memory limit {} MiB; JVM sees {} processors, max heap {} MiB", pod.name(), pod.namespace(),
                pod.node(), pod.cpuLimitMillis(), pod.memoryLimitBytes() >> 20, Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() >> 20);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 256);
        // platform threads: the /work endpoint measures its own CPU time, which virtual threads don't report
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.createContext("/whoami", ex -> respond(ex, pod.name()));
        server.createContext("/menu", ex -> timer.record(() -> respond(ex, "{\"items\":42}")));
        server.createContext("/config", ex -> {
            String sub;
            try {
                sub = Files.readString(Path.of("/etc/subpath/discount")).strip();
            } catch (IOException e) {
                sub = "-";
            }
            respond(ex, "env=" + System.getenv().getOrDefault("DISCOUNT", "-") + " once=" + once.getOrDefault("discount", "-")
                    + " watched=" + watched.get("discount", "-") + " subpath=" + sub);
        });
        server.createContext("/work", ex -> {
            String q = ex.getRequestURI().getQuery();
            long ms = q == null ? 5 : Long.parseLong(q.substring(q.indexOf('=') + 1));
            boolean cpuClock = THREADS.getCurrentThreadCpuTime() >= 0;      // -1 where per-thread CPU time isn't available
            long start = cpuClock ? THREADS.getCurrentThreadCpuTime() : System.nanoTime();
            long x = 0;
            while ((cpuClock ? THREADS.getCurrentThreadCpuTime() : System.nanoTime()) - start < ms * 1_000_000) {
                for (int i = 0; i < 10_000; i++) {
                    x += i * 31L ^ x;
                }
            }
            respond(ex, "done " + (x & 1));
        });
        server.createContext("/cpustat", ex -> {
            String s;
            try {
                Path v2 = Path.of("/sys/fs/cgroup/cpu.stat");
                s = Files.readString(Files.exists(v2) ? v2 : Path.of("/sys/fs/cgroup/cpu/cpu.stat"));
            } catch (IOException e) {
                s = "unavailable";
            }
            respond(ex, s);
        });
        server.createContext("/actuator/prometheus", ex -> respond(ex, registry.scrape()));
        server.start();
        LOG.info("started in {} ms", System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            watched.close();
        }));
    }

    static void respond(HttpExchange ex, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        } finally {
            ex.close();
        }
    }
}
