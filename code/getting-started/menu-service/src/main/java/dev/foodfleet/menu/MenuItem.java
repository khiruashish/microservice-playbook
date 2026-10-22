package dev.foodfleet.menu;

/** One dish on a restaurant's menu. Prices are whole paise: money is never a floating-point number. */
public record MenuItem(String id, String name, long pricePaise, boolean vegetarian) {

    public MenuItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (pricePaise <= 0) {
            throw new IllegalArgumentException("pricePaise must be positive");
        }
    }

    String json() {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name.replace("\"", "\\\"") + "\",\"pricePaise\":" + pricePaise + ",\"vegetarian\":" + vegetarian + "}";
    }
}
