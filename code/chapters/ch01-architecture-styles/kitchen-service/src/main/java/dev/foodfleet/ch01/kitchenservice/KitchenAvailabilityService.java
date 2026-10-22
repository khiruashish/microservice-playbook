package dev.foodfleet.ch01.kitchenservice;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Availability rules for the extracted kitchen. Tickets arrive from Kafka in later chapters;
 * chapter 1 only needs the availability query and the pause switch.
 */
@Service
public class KitchenAvailabilityService {

    private final int maxActiveTickets;
    private final Set<String> knownRestaurants;
    private final Set<String> paused = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> activeTickets = new ConcurrentHashMap<>();

    public KitchenAvailabilityService(
            @Value("${foodfleet.kitchen.max-active-tickets:20}") int maxActiveTickets,
            @Value("${foodfleet.kitchen.known-restaurants}") Set<String> knownRestaurants) {
        this.maxActiveTickets = maxActiveTickets;
        this.knownRestaurants = Set.copyOf(knownRestaurants);
    }

    public boolean isKnown(String restaurantId) {
        return knownRestaurants.contains(restaurantId);
    }

    public AvailabilityResponse availability(String restaurantId) {
        int active = activeTickets.getOrDefault(restaurantId, new AtomicInteger()).get();
        boolean accepting = !paused.contains(restaurantId) && active < maxActiveTickets;
        return new AvailabilityResponse(restaurantId, accepting, active);
    }

    public void pause(String restaurantId) {
        paused.add(restaurantId);
    }

    public void resume(String restaurantId) {
        paused.remove(restaurantId);
    }

    public void ticketOpened(String restaurantId) {
        activeTickets.computeIfAbsent(restaurantId, id -> new AtomicInteger()).incrementAndGet();
    }
}
