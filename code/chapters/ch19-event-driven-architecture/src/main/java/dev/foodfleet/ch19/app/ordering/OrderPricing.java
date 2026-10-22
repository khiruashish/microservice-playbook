package dev.foodfleet.ch19.app.ordering;

import org.springframework.stereotype.Service;

/** Prices an order line from the local replica: no network call, works while catalog is down. */
@Service
public class OrderPricing {

    public static final class ItemUnavailable extends RuntimeException {
        public ItemUnavailable(String itemId) {
            super("item " + itemId + " is sold out or unknown");
        }
    }

    private final MenuReplica menu;

    public OrderPricing(MenuReplica menu) {
        this.menu = menu;
    }

    public long linePricePaise(String itemId, int quantity) {
        MenuReplica.Item item = menu.get(itemId).filter(MenuReplica.Item::available)
                .orElseThrow(() -> new ItemUnavailable(itemId));
        return item.pricePaise() * quantity;
    }
}
