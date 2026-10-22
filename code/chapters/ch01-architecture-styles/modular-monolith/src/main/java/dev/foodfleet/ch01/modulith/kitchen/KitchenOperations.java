package dev.foodfleet.ch01.modulith.kitchen;

import dev.foodfleet.ch01.modulith.kitchen.internal.KitchenProperties;
import dev.foodfleet.ch01.modulith.kitchen.internal.TicketRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Public API of the kitchen module.
 *
 * <p>Pause state lives in memory to keep chapter 1 small. With several replicas it must move to
 * the database or Redis, otherwise each instance would hold its own copy (chapter 68,
 * "statelessness").
 */
@Service
public class KitchenOperations {

    private final TicketRepository tickets;
    private final KitchenProperties properties;
    private final Set<String> pausedRestaurants = ConcurrentHashMap.newKeySet();

    public KitchenOperations(TicketRepository tickets, KitchenProperties properties) {
        this.tickets = tickets;
        this.properties = properties;
    }

    public KitchenAvailability availability(String restaurantId) {
        int active = tickets.countActive(restaurantId);
        boolean accepting = !pausedRestaurants.contains(restaurantId) && active < properties.maxActiveTickets();
        return new KitchenAvailability(restaurantId, accepting, active);
    }

    public void pause(String restaurantId) {
        pausedRestaurants.add(restaurantId);
    }

    public void resume(String restaurantId) {
        pausedRestaurants.remove(restaurantId);
    }

    public Optional<TicketView> ticketFor(UUID orderId) {
        return tickets.findByOrderId(orderId);
    }
}
