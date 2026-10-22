package dev.foodfleet.ch63.consumer;

import dev.foodfleet.ch63.json.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** app-bff's client: the whole menu, for the app's menu screen, with a spice level the app shows as an icon. */
public final class AppMenuClient {

    public enum Spice { LOW, MEDIUM, HIGH }

    public record MenuItem(String id, String name, BigDecimal price, Spice spice, String description, boolean available) {
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public AppMenuClient(String base) {
        this.base = base;
    }

    public List<MenuItem> menu(int restaurant) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/" + restaurant + "/menu"))
                    .header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("menu-service answered " + r.statusCode());
            }
            List<MenuItem> items = new ArrayList<>();
            for (Object o : Json.array(Json.object(Json.parse(r.body())).get("items"))) {
                Map<String, Object> i = Json.object(o);
                items.add(new MenuItem((String) i.get("id"), (String) i.get("name"), new BigDecimal(i.get("price").toString()),
                        Spice.valueOf((String) i.get("spice")), (String) i.get("description"), (Boolean) i.get("available")));
            }
            return items;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
