package dev.foodfleet.ch16.acl;

import java.time.Instant;
import java.util.Optional;

/** The new order-history service's model: what an order means today, in today's words. */
public record Order(String id, Status status, long totalPaise, Tier tier, Address address,
                    Optional<Instant> deliveredAt) {

    public enum Status { PLACED, PREPARING, DELIVERED, CANCELLED, REFUNDED }

    public enum Tier { NONE, SILVER, GOLD }

    public record Address(String flat, String street, String area, Optional<String> pincode) {
    }
}
