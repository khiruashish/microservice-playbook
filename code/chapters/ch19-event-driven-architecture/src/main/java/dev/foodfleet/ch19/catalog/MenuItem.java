package dev.foodfleet.ch19.catalog;

/** catalog-service's menu item. {@code version} increases with every change to the item. */
public record MenuItem(String id, String restaurantId, String name, long pricePaise, boolean available,
                       long version) {

    public MenuItem withPrice(long newPrice) {
        return new MenuItem(id, restaurantId, name, newPrice, available, version + 1);
    }

    public MenuItem withAvailable(boolean nowAvailable) {
        return new MenuItem(id, restaurantId, name, pricePaise, nowAvailable, version + 1);
    }

    /** The JSON a state-carrying event or an API response would carry (size matters on the bus). */
    public String json() {
        return "{\"itemId\":\"" + id + "\",\"restaurantId\":\"" + restaurantId + "\",\"name\":\"" + name
                + "\",\"pricePaise\":" + pricePaise + ",\"available\":" + available + ",\"version\":" + version
                + ",\"currency\":\"INR\",\"occurredAt\":\"2026-10-06T07:30:00.000Z\"}";
    }
}
