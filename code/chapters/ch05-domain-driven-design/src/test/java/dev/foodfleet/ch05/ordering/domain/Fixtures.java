package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import java.time.Instant;

/** Test data in the ubiquitous language. */
public final class Fixtures {

    public static final RestaurantId BIRYANI_HOUSE = new RestaurantId("rest-biryani-house");
    public static final Instant NOW = Instant.parse("2026-10-01T13:30:00Z");

    public static final OrderableItem DUM_BIRYANI =
            new OrderableItem(new MenuItemId("dum-biryani"), BIRYANI_HOUSE, "Hyderabadi Dum Biryani", Money.ofRupees("349.00"), true);
    public static final OrderableItem RAITA =
            new OrderableItem(new MenuItemId("boondi-raita"), BIRYANI_HOUSE, "Boondi Raita", Money.ofRupees("69.00"), true);
    public static final OrderableItem SEEKH_KEBAB_SOLD_OUT =
            new OrderableItem(new MenuItemId("seekh-kebab"), BIRYANI_HOUSE, "Seekh Kebab", Money.ofRupees("289.00"), false);
    public static final OrderableItem MASALA_DOSA_ELSEWHERE =
            new OrderableItem(new MenuItemId("masala-dosa"), new RestaurantId("rest-midnight-dosa"), "Masala Dosa", Money.ofRupees("120.00"), true);

    public static final RestaurantTerms BIRYANI_HOUSE_TERMS =
            new RestaurantTerms(BIRYANI_HOUSE, true, Money.ofRupees("199.00"), Money.ofRupees("10.00"));

    public static final Money DELIVERY_FEE = Money.ofRupees("35.00");

    public static final DeliveryAddress KORAMANGALA =
            new DeliveryAddress("12, 5th Cross", "Koramangala 4th Block", "Bengaluru", "560034", "Near Sony World");

    private Fixtures() {
    }

    public static Order draft() {
        return Order.startDraft(OrderId.newId(), new CustomerId("cust-42"), BIRYANI_HOUSE, KORAMANGALA);
    }

    /** 2 × biryani + 1 × raita, placed: ₹767.00 items, ₹30.00 packaging, ₹39.85 GST, ₹35.00 delivery = ₹871.85. */
    public static Order placed() {
        Order order = draft();
        order.addItem(DUM_BIRYANI, Quantity.of(2));
        order.addItem(RAITA, Quantity.of(1));
        order.place(new PricingPolicy(), BIRYANI_HOUSE_TERMS, DELIVERY_FEE, NOW);
        order.pullEvents();
        return order;
    }
}
