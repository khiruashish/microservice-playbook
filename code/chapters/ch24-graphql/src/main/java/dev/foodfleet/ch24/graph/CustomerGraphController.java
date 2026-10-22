package dev.foodfleet.ch24.graph;

import dev.foodfleet.ch24.experiment.FoodFleetBackends;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Order;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Restaurant;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Review;
import dev.foodfleet.ch24.experiment.FoodFleetBackends.Rider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Resolvers for customer.graphqls. Top-level queries call one service each; fields that cross to
 * another service use {@code @BatchMapping}, so a list of 10 orders costs one call to
 * restaurant-service, not ten.
 */
@Controller
public class CustomerGraphController {

    static final int MAX_PAGE = 50;

    private final FoodFleetBackends services;

    public CustomerGraphController(FoodFleetBackends services) {
        this.services = services;
    }

    /** The customer comes from the gateway-verified header, never from a query argument. */
    @QueryMapping
    public List<Order> myOrders(@Argument int first, @ContextValue String customerId) {
        if (first < 1 || first > MAX_PAGE) {
            throw new IllegalArgumentException("first must be between 1 and " + MAX_PAGE);
        }
        return services.recentOrders(customerId, first);
    }

    @QueryMapping
    public Restaurant restaurant(@Argument String id) {
        return services.restaurant(id);                       // null -> "restaurant": null (nullable field)
    }

    /** One call to restaurant-service for all orders in the response (DataLoader underneath). */
    @BatchMapping
    public Map<Order, Restaurant> restaurant(List<Order> orders) {
        Map<String, Restaurant> byId = services.restaurants(orders.stream().map(Order::restaurantId).toList());
        Map<Order, Restaurant> result = new LinkedHashMap<>();
        orders.forEach(o -> result.put(o, byId.get(o.restaurantId())));
        return result;
    }

    /** Orders without a rider are left out of the map, so their rider is null. */
    @BatchMapping
    public Map<Order, Rider> rider(List<Order> orders) {
        List<String> ids = orders.stream().map(Order::riderId).filter(Objects::nonNull).toList();
        Map<Order, Rider> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        Map<String, Rider> byId = services.riders(ids);
        orders.stream().filter(o -> o.riderId() != null).forEach(o -> result.put(o, byId.get(o.riderId())));
        return result;
    }

    /**
     * Per restaurant: {@code @BatchMapping} methods can't take field arguments such as {@code first};
     * for that, register a loader with BatchLoaderRegistry. Here reviews are only read for a single
     * restaurant at a time, so the simple mapping is enough, and the complexity limit stops abuse.
     */
    @SchemaMapping
    public List<Review> reviews(Restaurant restaurant, @Argument int first) {
        return services.reviews(restaurant.id(), Math.min(first, MAX_PAGE));
    }

    @BatchMapping(typeName = "Review", field = "restaurant")
    public Map<Review, Restaurant> reviewRestaurant(List<Review> reviews) {
        Map<String, Restaurant> byId = services.restaurants(reviews.stream().map(Review::restaurantId).toList());
        Map<Review, Restaurant> result = new LinkedHashMap<>();
        reviews.forEach(r -> result.put(r, byId.get(r.restaurantId())));
        return result;
    }
}
