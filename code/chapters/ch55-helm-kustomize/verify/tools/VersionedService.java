import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * menu-service for the chapter's deployment experiments: {@code /version} answers with the build baked into the image
 * (/app/version.txt), {@code /config} with the discount from the environment and from the mounted ConfigMap.
 * {@code CRASH_ON_START=true} makes it exit at startup, the way a release with a broken configuration does.
 */
public final class VersionedService {

    public static void main(String[] args) throws Exception {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("CRASH_ON_START", "false"))) {
            System.err.println("missing required property spring.datasource.url");
            System.exit(1);
        }
        String version = read(Path.of("/app/version.txt"));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 128);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu", ex -> respond(ex, "{\"items\":42}"));
        server.createContext("/version", ex -> respond(ex, version));
        server.createContext("/config", ex -> respond(ex, "env=" + System.getenv().getOrDefault("DISCOUNT", "-") + " mounted="
                + read(Path.of("/etc/menu-config/discount"))));
        server.start();
        System.out.println("menu-service " + version + " started");
    }

    static String read(Path p) {
        try {
            return Files.readString(p).strip();
        } catch (IOException e) {
            return "-";
        }
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
