package dev.foodfleet.ch12.composition;

import java.util.List;

/** @param degraded which sources failed; their fields are null in every entry */
public record OrderHistory(String customerId, List<OrderHistoryEntry> entries, List<String> degraded) {
}
