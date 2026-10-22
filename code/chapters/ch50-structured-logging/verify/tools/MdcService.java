import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch50.mdc.MdcPropagation;
import dev.foodfleet.ch50.mdc.RequestMdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * order-service's request handling, reduced to logging: each request puts its request ID in the MDC and logs one line
 * from each of nine places a request's work can run. Logs are JSON lines (Logback's JsonEncoder) in -Dff.log; with
 * the OpenTelemetry agent attached, its Logback instrumentation adds trace_id and span_id to every event's MDC.
 *
 * Usage: java [-javaagent:...] -Dff.port=... -Dff.log=... MdcService
 */
public final class MdcService {

    static final Logger LOG = LoggerFactory.getLogger("order-service");
    static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();
    static final ExecutorService LEAKY = Executors.newSingleThreadExecutor();

    public static void main(String[] args) throws Exception {
        Lab.root(Lab.file(Path.of(System.getProperty("ff.log")), Lab.json(), true));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.getInteger("ff.port")), 64);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/health", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/orders", ex -> {
            String q = ex.getRequestURI().getQuery();
            int n = Integer.parseInt(q.substring(q.indexOf('=') + 1));
            try (RequestMdc ignored = RequestMdc.open("request.id", "r-" + n)) {
                handle(n);
            } catch (Exception e) {
                LOG.error("order {} failed", n, e);
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
    }

    static void handle(int n) throws Exception {
        LOG.info("order {} same thread", n);
        POOL.submit(() -> LOG.info("order {} fixed thread pool", n)).get();
        VIRTUAL.submit(() -> LOG.info("order {} virtual-thread executor", n)).get();
        CompletableFuture.runAsync(() -> LOG.info("order {} CompletableFuture.runAsync", n)).get();
        Thread t = new Thread(() -> LOG.info("order {} new Thread", n));
        t.start();
        t.join();
        Thread.ofVirtual().start(() -> LOG.info("order {} Thread.ofVirtual", n)).join();
        POOL.submit(MdcPropagation.wrap(() -> LOG.info("order {} fixed thread pool, MdcPropagation.wrap", n))).get();
        // a task on a shared single-thread pool that sets the MDC for itself on even orders and never clears it ...
        LEAKY.submit(() -> {
            if (n % 2 == 0) {
                MDC.put("request.id", "r-" + n);
            }
        }).get();
        // ... and the next task that thread runs
        LEAKY.submit(() -> LOG.info("order {} pooled thread after a task that left the MDC set", n)).get();
    }
}
