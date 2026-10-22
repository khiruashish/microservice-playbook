package dev.foodfleet.lab02;

/** What the API returns. The version travels as an ETag so clients can update safely. */
public record MenuItem(String id, String name, long pricePaise, boolean vegetarian, boolean available, long version) {
}
