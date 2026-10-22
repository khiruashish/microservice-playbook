package dev.foodfleet.ch63.contract;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch63.json.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The consumer's side: a stub of the provider that answers exactly the contract's interactions, for the consumer's
 * own tests (Pact's mock server, Spring Cloud Contract's stub runner). A request the contract doesn't describe gets a
 * 500, so a client that calls something it hasn't declared fails its own tests.
 */
public final class StubServer implements AutoCloseable {

    private final HttpServer server;

    public StubServer(Contract contract) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
        server.createContext("/", ex -> {
            try (ex) {
                String path = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
                Contract.Interaction match = contract.interactions().stream()
                        .filter(i -> i.method().equals(ex.getRequestMethod()) && i.path().equals(path))
                        .filter(i -> i.requestHeaders().entrySet().stream().allMatch(h -> h.getValue().equals(ex.getRequestHeaders().getFirst(h.getKey()))))
                        .findFirst().orElse(null);
                byte[] body = (match == null ? "{\"error\":\"no interaction in the contract for " + ex.getRequestMethod() + " " + path + "\"}"
                        : match.body() == null ? "" : Json.write(match.body())).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(match == null ? 500 : match.status(), body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    ex.getResponseBody().write(body);
                }
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
