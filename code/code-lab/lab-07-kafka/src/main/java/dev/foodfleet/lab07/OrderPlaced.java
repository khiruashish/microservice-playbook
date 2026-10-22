package dev.foodfleet.lab07;

import java.util.List;

/** A fact, in the past tense, small: what consumers need to react, not the whole order. Ids, never personal data. */
public record OrderPlaced(String orderId, String restaurantId, List<String> itemIds) {
}
