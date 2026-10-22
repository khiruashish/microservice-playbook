package dev.foodfleet.ch01.layered.service;

import dev.foodfleet.ch01.layered.domain.Order;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Kitchen capability living in the same deployable as ordering. */
@Service
public class KitchenService {

    private final Set<String> pausedRestaurants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> ticketsByOrder = new ConcurrentHashMap<>();

    public void createTicket(Order order) {
        if (pausedRestaurants.contains(order.restaurantId())) {
            throw new KitchenRejectedException(order.restaurantId());
        }
        ticketsByOrder.put(order.id(), "TICKET-" + order.id().toString().substring(0, 8));
    }

    public void pause(String restaurantId) {
        pausedRestaurants.add(restaurantId);
    }

    public void resume(String restaurantId) {
        pausedRestaurants.remove(restaurantId);
    }

    public List<UUID> ordersWithTickets() {
        return List.copyOf(ticketsByOrder.keySet());
    }
}
