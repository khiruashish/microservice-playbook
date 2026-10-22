package dev.foodfleet.ch61.order;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** menu-service over HTTP: {@code GET /menu/{restaurant}/items/{item}} answers {@code {"price":"129.50","available":true}}. */
public final class HttpMenuClient implements MenuClient {

    private static final Pattern PRICE = Pattern.compile("\"price\"\\s*:\\s*\"([0-9.]+)\"");
    private static final Pattern AVAILABLE = Pattern.compile("\"available\"\\s*:\\s*(true|false)");

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public HttpMenuClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Price price(int restaurantId, String itemId) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/menu/" + restaurantId + "/items/" + itemId))
                    .timeout(Duration.ofSeconds(2)).header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) {
                return new Price(BigDecimal.ZERO, false);
            }
            if (r.statusCode() != 200) {
                throw new IllegalStateException("menu-service answered " + r.statusCode());
            }
            Matcher p = PRICE.matcher(r.body());
            Matcher a = AVAILABLE.matcher(r.body());
            if (!p.find() || !a.find()) {
                throw new IllegalStateException("unexpected menu-service response: " + r.body());
            }
            return new Price(new BigDecimal(p.group(1)), Boolean.parseBoolean(a.group(1)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("menu-service unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
