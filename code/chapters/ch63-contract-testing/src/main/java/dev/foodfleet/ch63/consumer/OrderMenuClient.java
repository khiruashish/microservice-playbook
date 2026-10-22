package dev.foodfleet.ch63.consumer;

import dev.foodfleet.ch63.json.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** order-service's client of menu-service: the price and availability of one item. An unknown item can't be ordered. */
public final class OrderMenuClient {

    public record Price(BigDecimal price, boolean available) {
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public OrderMenuClient(String base) {
        this.base = base;
    }

    public Price price(int restaurant, String item) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/" + restaurant + "/items/" + item))
                    .header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) {
                return new Price(BigDecimal.ZERO, false);
            }
            if (r.statusCode() != 200) {
                throw new IllegalStateException("menu-service answered " + r.statusCode());
            }
            Map<String, Object> body = Json.object(Json.parse(r.body()));
            return new Price(new BigDecimal(body.get("price").toString()), (Boolean) body.get("available"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
