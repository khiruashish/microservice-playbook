package dev.foodfleet.ch22.experiment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A stand-in for order-history-service on loopback, over HTTP or HTTPS (self-signed certificate
 * made with the JDK's keytool). Endpoints: {@code /fast} (answers at once), {@code /slow} (200 ms of
 * work), {@code /hang} (never answers in time: 60 s).
 */
public final class LocalServer implements AutoCloseable {

    static {
        System.setProperty("sun.net.httpserver.nodelay", "true");     // see chapter 17
    }

    private static final byte[] BODY = "{\"id\":\"o-42\",\"status\":\"DELIVERED\"}".getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final SSLContext sslContext;

    private LocalServer(boolean https) throws Exception {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        if (https) {
            sslContext = selfSigned();
            HttpsServer s = HttpsServer.create(address, 4_096);
            s.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            server = s;
        } else {
            sslContext = null;
            server = HttpServer.create(address, 4_096);
        }
        server.setExecutor(executor);
        server.createContext("/fast", ex -> respond(ex, 0));
        server.createContext("/slow", ex -> respond(ex, 200));
        server.createContext("/hang", ex -> respond(ex, 60_000));
        server.start();
    }

    public static LocalServer http() throws Exception {
        return new LocalServer(false);
    }

    public static LocalServer https() throws Exception {
        return new LocalServer(true);
    }

    private void respond(HttpExchange exchange, long workMillis) throws IOException {
        peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
            if (workMillis > 0) {
                Thread.sleep(workMillis);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, BODY.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(BODY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public String baseUrl() {
        return (sslContext != null ? "https" : "http") + "://localhost:" + server.getAddress().getPort();
    }

    /** The context a client needs to trust this server's self-signed certificate. */
    public SSLContext clientSslContext() {
        return sslContext;
    }

    public int peakInFlight() {
        return peakInFlight.get();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();          // interrupt handlers still "working" (the 60 s /hang)
    }

    /** keytool -genkeypair ... ; the same key store serves as the client's trust store. */
    private static SSLContext selfSigned() throws Exception {
        Path dir = Files.createTempDirectory("ch22-tls");
        Path store = dir.resolve("server.p12");
        char[] password = "changeit".toCharArray();
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", "changeit", "-keypass", "changeit")
                .redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (keytool.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            keyStore.load(in, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }
}
