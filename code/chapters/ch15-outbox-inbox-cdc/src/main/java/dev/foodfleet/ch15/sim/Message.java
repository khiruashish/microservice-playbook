package dev.foodfleet.ch15.sim;

/**
 * A message on the broker. {@code id} is unique per business event and is what consumers
 * deduplicate on; {@code key} (the aggregate id) decides the partition and so the ordering.
 */
public record Message(String id, String key, String type, String payload) {
}
