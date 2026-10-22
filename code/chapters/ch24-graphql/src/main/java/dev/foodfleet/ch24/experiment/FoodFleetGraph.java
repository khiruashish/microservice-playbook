package dev.foodfleet.ch24.experiment;

import dev.foodfleet.ch24.experiment.FoodFleetBackends.Order;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Restaurant;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Review;
import dev.foodfleet.ch24.mini.Engine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The customer graph wired onto the mini engine: per-item resolvers for every object field, and
 * batch resolvers for the same fields (used in BATCHED mode), exactly like a {@code @SchemaMapping}
 * method vs a {@code @BatchMapping} method in Spring for GraphQL.
 */
public final class FoodFleetGraph {

    public static final String CUSTOMER = "c-1001";

    private FoodFleetGraph() {
    }

    public static Engine engine(FoodFleetBackends b) {
        return new Engine()
                .type("Query.myOrders", "Order").list("Query.myOrders", 10)
                .resolver("Query.myOrders", (root, args) -> b.recentOrders(CUSTOMER, first(args, 10)))
                .type("Query.restaurant", "Restaurant")
                .resolver("Query.restaurant", (root, args) -> b.restaurant((String) args.get("id")))

                .type("Order.items", "OrderItem").list("Order.items", 3)
                .type("Order.restaurant", "Restaurant")
                .resolver("Order.restaurant", (o, args) -> b.restaurant(((Order) o).restaurantId()))
                .batchResolver("Order.restaurant", (orders, args) -> {
                    Map<String, Restaurant> byId = b.restaurants(orders.stream().map(o -> ((Order) o).restaurantId()).toList());
                    return orders.stream().map(o -> (Object) byId.get(((Order) o).restaurantId())).toList();
                })
                .type("Order.rider", "Rider")
                .resolver("Order.rider", (o, args) -> ((Order) o).riderId() == null ? null : b.rider(((Order) o).riderId()))
                .batchResolver("Order.rider", (orders, args) -> {
                    List<String> ids = orders.stream().map(o -> ((Order) o).riderId()).filter(id -> id != null).toList();
                    var byId = ids.isEmpty() ? Map.<String, FoodFleetBackends.Rider>of() : b.riders(ids);
                    List<Object> out = new ArrayList<>();
                    for (Object o : orders) {
                        out.add(((Order) o).riderId() == null ? null : byId.get(((Order) o).riderId()));
                    }
                    return out;
                })

                .type("Restaurant.reviews", "Review").list("Restaurant.reviews", 10)
                .resolver("Restaurant.reviews", (r, args) -> b.reviews(((Restaurant) r).id(), first(args, 10)))
                .batchResolver("Restaurant.reviews", (rs, args) -> {
                    var byId = b.reviews(rs.stream().map(r -> ((Restaurant) r).id()).toList(), first(args, 10));
                    return rs.stream().map(r -> (Object) byId.get(((Restaurant) r).id())).toList();
                })
                .type("Review.restaurant", "Restaurant")
                .resolver("Review.restaurant", (v, args) -> b.restaurant(((Review) v).restaurantId()))
                .batchResolver("Review.restaurant", (vs, args) -> {
                    var byId = b.restaurants(vs.stream().map(v -> ((Review) v).restaurantId()).toList());
                    return vs.stream().map(v -> (Object) byId.get(((Review) v).restaurantId())).toList();
                });
    }

    static int first(Map<String, Object> args, int fallback) {
        return args.get("first") instanceof Integer n ? n : fallback;
    }
}
