package dev.foodfleet.ch64.app;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * menu-service: prices, and stock for limited dishes. The biryani of the day has {@code DAILY_STOCK} portions
 * (default 5) per instance; reserving one past that answers 409.
 */
public final class MenuApp {

    record Item(BigDecimal price, AtomicInteger stock) {
    }

    private MenuApp() {
    }

    public static HttpServer start(int port, int dailyStock) throws IOException {
        Map<String, Item> items = new ConcurrentHashMap<>();
        items.put("paneer-tikka", new Item(new BigDecimal("129.50"), new AtomicInteger(Integer.MAX_VALUE)));
        items.put("dal-makhani", new Item(new BigDecimal("189.00"), new AtomicInteger(Integer.MAX_VALUE)));
        items.put("biryani-of-the-day", new Item(new BigDecimal("349.00"), new AtomicInteger(dailyStock)));
        return Web.serve(port, (method, path, body) -> {
            String[] p = path.split("/"); // /restaurants/7/items/{id}[/reserve]
            Item item = p.length >= 5 && p[2].equals("7") ? items.get(p[4]) : null;
            if (item == null) {
                return Web.json(404, Map.of("error", "no such item"));
            }
            if (method.equals("POST") && p.length == 6 && p[5].equals("reserve")) {
                int left = item.stock().getAndUpdate(s -> s > 0 ? s - 1 : s);
                return left > 0 ? Web.json(200, Map.of("reserved", true)) : Web.json(409, Map.of("error", "sold out"));
            }
            return Web.json(200, Map.of("price", item.price().toPlainString(), "available", item.stock().get() > 0));
        });
    }
}
