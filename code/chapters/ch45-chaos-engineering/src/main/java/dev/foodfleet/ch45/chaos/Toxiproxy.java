package dev.foodfleet.ch45.chaos;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A minimal client for Toxiproxy's HTTP API (port 8474 by default): create a proxy in front of a dependency, add and
 * remove faults, switch the proxy off, reset everything. Experiments inject faults through it; rollbacks undo them.
 */
public final class Toxiproxy {

    private final URI api;
    private final HttpClient http = HttpClient.newBuilder()
            .proxy(ProxySelector.of(null))
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Toxiproxy(URI api) {
        this.api = api;
    }

    /** {@code listen} like "127.0.0.1:21212"; {@code upstream} like "127.0.0.1:8081". */
    public void createProxy(String name, String listen, String upstream) {
        send("POST", "/proxies", "{\"name\":\"" + name + "\",\"listen\":\"" + listen + "\",\"upstream\":\"" + upstream
                + "\",\"enabled\":true}", 201);
    }

    public void add(String proxy, Toxic toxic) {
        send("POST", "/proxies/" + proxy + "/toxics", toxic.json(), 200);
    }

    public void remove(String proxy, String toxicName) {
        send("DELETE", "/proxies/" + proxy + "/toxics/" + toxicName, null, 204);
    }

    /** A disabled proxy refuses connections and drops the open ones: the dependency is gone. */
    public void enabled(String proxy, boolean enabled) {
        send("POST", "/proxies/" + proxy, "{\"enabled\":" + enabled + "}", 200);
    }

    /** Removes every toxic and re-enables every proxy: the emergency stop. */
    public void reset() {
        send("POST", "/reset", null, 204);
    }

    private void send(String method, String path, String body, int expected) {
        HttpRequest.Builder b = HttpRequest.newBuilder(api.resolve(path)).timeout(Duration.ofSeconds(5));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != expected) {
                throw new IllegalStateException(method + " " + path + " -> " + r.statusCode() + ": " + r.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Toxiproxy unreachable at " + api, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
