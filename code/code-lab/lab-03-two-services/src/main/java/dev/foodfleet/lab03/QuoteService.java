package dev.foodfleet.lab03;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Prices an order from menu-service's current prices. The caller never sends prices; it sends dish ids. */
@Service
public class QuoteService {

    public record Line(String itemId, int quantity) {
    }

    public record Quote(String restaurantId, List<QuotedLine> lines, long totalPaise) {
    }

    public record QuotedLine(String itemId, String name, int quantity, long linePaise) {
    }

    public static class RestaurantNotFound extends RuntimeException {
        RestaurantNotFound(String id) {
            super("No restaurant with id " + id);
        }
    }

    public static class UnknownItems extends RuntimeException {
        UnknownItems(List<String> ids) {
            super("Not on the menu: " + String.join(", ", ids));
        }
    }

    public static class MenuUnavailable extends RuntimeException {
        MenuUnavailable(Throwable cause) {
            super("menu-service didn't answer in time", cause);
        }
    }

    private final MenuClient menus;

    public QuoteService(MenuClient menus) {
        this.menus = menus;
    }

    public Quote quote(String restaurantId, List<Line> lines) {
        MenuClient.Menu menu;
        try {
            menu = menus.menu(restaurantId);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new RestaurantNotFound(restaurantId);      // menu-service's 404 means something to us
            }
            throw e;                                               // menu-service failed: should be a 502 (exercise 2)
        } catch (ResourceAccessException e) {
            throw new MenuUnavailable(e);                          // timeout or connection refused: 503, retry later
        }
        Map<String, MenuClient.Item> byId = menu.items().stream()
                .collect(Collectors.toMap(MenuClient.Item::id, Function.identity()));
        List<String> unknown = lines.stream().map(Line::itemId).filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new UnknownItems(unknown);
        }
        List<QuotedLine> quoted = lines.stream().map(l -> {
            MenuClient.Item item = byId.get(l.itemId());
            return new QuotedLine(item.id(), item.name(), l.quantity(), Math.multiplyExact(item.pricePaise(), l.quantity()));
        }).toList();
        return new Quote(restaurantId, quoted, quoted.stream().mapToLong(QuotedLine::linePaise).sum());
    }
}
