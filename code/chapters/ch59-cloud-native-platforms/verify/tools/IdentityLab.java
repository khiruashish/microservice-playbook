import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A pod that does what a cloud SDK's web-identity credential provider does with its token file: reads it, and notices
 * when the kubelet replaces it. {@code /token?which=sts} is the projected token for the cloud's token service,
 * {@code /token?which=default} the default one for the API server; {@code /rotations} lists when the projected token
 * changed, in seconds since the pod started.
 */
public final class IdentityLab {

    static final Path STS = Path.of("/var/run/secrets/tokens/sts-token");
    static final Path DEFAULT = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        List<String> rotations = new CopyOnWriteArrayList<>();
        String[] last = {read(STS)};
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            String now = read(STS);
            if (!now.equals(last[0])) {
                rotations.add(String.format("%.0f", (System.nanoTime() - start) / 1e9));
                last[0] = now;
            }
        }, 1, 1, TimeUnit.SECONDS);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 16);
        server.createContext("/token", ex -> respond(ex, read(ex.getRequestURI().getQuery().endsWith("default") ? DEFAULT : STS)));
        server.createContext("/rotations", ex -> respond(ex, String.join(" ", rotations)));
        server.createContext("/uptime", ex -> respond(ex, String.format("%.0f", (System.nanoTime() - start) / 1e9)));
        server.start();
    }

    static String read(Path p) {
        try {
            return Files.readString(p).strip();
        } catch (IOException e) {
            return "";
        }
    }

    static void respond(HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
