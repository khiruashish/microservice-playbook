package dev.foodfleet.ch06.decomposition;

import static dev.foodfleet.ch06.decomposition.DomainModel.Kind.ASYNC;
import static dev.foodfleet.ch06.decomposition.DomainModel.Kind.SYNC;

import dev.foodfleet.ch06.decomposition.DomainModel.Capability;
import dev.foodfleet.ch06.decomposition.DomainModel.Interaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FoodFleet's capabilities, owning teams and two use cases (from the Event Storming session in this
 * chapter), plus four candidate decompositions to compare. The interaction counts are per execution
 * of the use case: placing a 3-dish order, and browsing a menu of 10 dishes.
 */
public final class FoodFleetModel {

    private FoodFleetModel() {
    }

    public static final String PLACE_ORDER = "place-order";
    public static final String BROWSE_MENU = "browse-menu";

    public static DomainModel model() {
        List<Capability> capabilities = List.of(
                new Capability("cart", "Checkout"),
                new Capability("order", "Checkout"),
                new Capability("order-line", "Checkout"),
                new Capability("customer-profile", "Customer"),
                new Capability("address-book", "Customer"),
                new Capability("restaurant-profile", "Restaurant Partner"),
                new Capability("menu", "Restaurant Partner"),
                new Capability("menu-item-pricing", "Restaurant Partner"),
                new Capability("kitchen-ticket", "Restaurant Partner"),
                new Capability("zone", "Logistics"),
                new Capability("rider-assignment", "Logistics"),
                new Capability("coupon", "Growth"),
                new Capability("notification", "Growth"),
                new Capability("payment", "Payments"));

        List<Interaction> interactions = List.of(
                // place-order: 3 dishes
                new Interaction(PLACE_ORDER, "order", "cart", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "customer-profile", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "address-book", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "zone", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "restaurant-profile", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "menu", SYNC, 3),
                new Interaction(PLACE_ORDER, "menu", "menu-item-pricing", SYNC, 3),
                new Interaction(PLACE_ORDER, "order", "coupon", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "order-line", SYNC, 3),
                new Interaction(PLACE_ORDER, "order", "payment", SYNC, 1),
                new Interaction(PLACE_ORDER, "order", "kitchen-ticket", ASYNC, 1),
                new Interaction(PLACE_ORDER, "order", "notification", ASYNC, 1),
                new Interaction(PLACE_ORDER, "order", "rider-assignment", ASYNC, 1),
                // browse-menu: 10 dishes shown
                new Interaction(BROWSE_MENU, "menu", "restaurant-profile", SYNC, 1),
                new Interaction(BROWSE_MENU, "menu", "menu-item-pricing", SYNC, 10),
                new Interaction(BROWSE_MENU, "menu", "zone", SYNC, 1));

        return new DomainModel(capabilities, interactions);
    }

    /** Every capability its own service: the "nano-service" or entity-service decomposition. */
    public static Decomposition perEntity() {
        Map<String, List<String>> services = new LinkedHashMap<>();
        model().capabilities().forEach(c -> services.put(c.name() + "-service", List.of(c.name())));
        return Decomposition.of("A. One service per entity", services);
    }

    /** Services follow bounded contexts and business capabilities (chapter 5's context map). */
    public static Decomposition byBusinessCapability() {
        Map<String, List<String>> services = new LinkedHashMap<>();
        services.put("ordering", List.of("cart", "order", "order-line"));
        services.put("customer", List.of("customer-profile", "address-book"));
        services.put("catalog", List.of("restaurant-profile", "menu", "menu-item-pricing"));
        services.put("kitchen", List.of("kitchen-ticket"));
        services.put("delivery-zones", List.of("zone"));
        services.put("delivery", List.of("rider-assignment"));
        services.put("promotions", List.of("coupon"));
        services.put("notification", List.of("notification"));
        services.put("payment", List.of("payment"));
        return Decomposition.of("B. By business capability", services);
    }

    /** Three big services split by "customer side / restaurant side / fulfilment": too coarse. */
    public static Decomposition coarse() {
        Map<String, List<String>> services = new LinkedHashMap<>();
        services.put("customer-facing", List.of("cart", "order", "order-line", "customer-profile", "address-book", "coupon"));
        services.put("restaurant-side", List.of("restaurant-profile", "menu", "menu-item-pricing", "kitchen-ticket"));
        services.put("fulfilment", List.of("zone", "rider-assignment", "notification", "payment"));
        return Decomposition.of("C. Three coarse services", services);
    }

    public static Decomposition monolith() {
        Map<String, List<String>> services = new LinkedHashMap<>();
        services.put("foodfleet", model().capabilities().stream().map(Capability::name).toList());
        return Decomposition.of("D. Modular monolith (one deployable)", services);
    }

    public static List<Decomposition> candidates() {
        return List.of(perEntity(), byBusinessCapability(), coarse(), monolith());
    }
}
