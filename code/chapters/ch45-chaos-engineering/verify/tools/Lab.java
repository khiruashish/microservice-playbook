import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The chapter's test bench: real restaurant-service instances (the JDK's HTTP server) on loopback, a real
 * Toxiproxy 2.12.0 server in front of each, and open-loop users on virtual threads. Faults are injected by
 * Toxiproxy at the TCP level, so the clients under test meet them the way they would in production.
 */
final class Lab {

    private Lab() {
    }

    /** The menu restaurant-service returns: 20 KiB, with a Content-Length. */
    static final byte[] MENU = menu();

    private static byte[] menu() {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        while (sb.length() < 20 * 1024 - 40) {
            sb.append("{\"name\":\"masala dosa\",\"price\":120},");
        }
        sb.setLength(sb.length() - 1);
        sb.append("]}");
        while (sb.length() < 20 * 1024) {
            sb.append(' ');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static HttpServer restaurantService() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1024);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu", ex -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ex.getResponseHeaders().add("Content-Type", "application/json");
            try (OutputStream out = ex.getResponseBody()) {
                ex.sendResponseHeaders(200, MENU.length);
                out.write(MENU);
            } catch (IOException e) {
                // the client (or a toxic) went away
            } finally {
                ex.close();
            }
        });
        server.start();
        return server;
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Starts toxiproxy-server on a free port and waits for its API. */
    static final class ToxiproxyServer implements AutoCloseable {
        final Process process;
        final int port;

        ToxiproxyServer(Path binary) throws Exception {
            this.port = freePort();
            this.process = new ProcessBuilder(binary.toString(), "-host", "127.0.0.1", "-port", Integer.toString(port))
                    .redirectErrorStream(true)
                    .redirectOutput(Path.of(System.getProperty("java.io.tmpdir"), "toxiproxy-ch45.log").toFile())
                    .start();
            for (int i = 0; i < 100; i++) {
                try {
                    new java.net.Socket("127.0.0.1", port).close();
                    return;
                } catch (IOException e) {
                    Thread.sleep(50);
                }
            }
            throw new IllegalStateException("toxiproxy-server did not start");
        }

        URI api() {
            return URI.create("http://127.0.0.1:" + port);
        }

        @Override
        public void close() {
            process.destroy();
        }
    }

    interface User {
        void arrive(double atSecond) throws Exception;
    }

    /** Poisson arrivals at {@code rate}/s until {@code stop} returns true; each user on its own virtual thread. */
    static Thread openLoop(long start, double rate, java.util.function.BooleanSupplier stop, User user) {
        return Thread.ofPlatform().daemon().start(() -> {
            ExecutorService users = Executors.newVirtualThreadPerTaskExecutor();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double t = 0;
            try {
                while (!stop.getAsBoolean()) {
                    t += -Math.log(1 - r.nextDouble()) / rate;
                    long wait = start + (long) (t * 1e9) - System.nanoTime();
                    if (wait > 0) {
                        TimeUnit.NANOSECONDS.sleep(wait);
                    }
                    double at = t;
                    users.submit(() -> {
                        user.arrive(at);
                        return null;
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            users.shutdown();
        });
    }

    static List<String> row(Object... cells) {
        List<String> l = new ArrayList<>();
        for (Object c : cells) {
            l.add(String.valueOf(c));
        }
        return l;
    }
}
