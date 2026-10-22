package dev.foodfleet.ch61;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * menu-service as the consumer contract describes it: answers exactly the contract's interactions and 404 for
 * anything else. Consumer tests run against it; the provider runs the same file against the real menu-service.
 */
final class MenuStub implements AutoCloseable {

    private static final Pattern INTERACTION = Pattern.compile(
            "\"path\"\\s*:\\s*\"([^\"]+)\".*?\"status\"\\s*:\\s*(\\d+)\\s*,\\s*\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);

    final HttpServer server;
    final Map<String, String[]> responses = new HashMap<>();

    MenuStub() throws IOException {
        try (InputStream in = MenuStub.class.getResourceAsStream("/contracts/menu-service.json")) {
            Matcher m = INTERACTION.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (m.find()) {
                responses.put(m.group(1), new String[] {m.group(2), m.group(3).replace("\\\"", "\"")});
            }
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        server.createContext("/", ex -> {
            String[] r = responses.getOrDefault(ex.getRequestURI().getPath(), new String[] {"404", "{\"error\":\"no such interaction\"}"});
            byte[] b = r[1].getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(Integer.parseInt(r[0]), b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
