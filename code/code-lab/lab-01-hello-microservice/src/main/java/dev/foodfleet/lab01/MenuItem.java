package dev.foodfleet.lab01;

/**
 * One dish on a restaurant's menu. Prices are whole paise: floating-point money rounds wrongly.
 * The compact constructor makes an invalid item impossible to create, wherever it comes from.
 */
public record MenuItem(String id, String name, long pricePaise, boolean vegetarian) {

    public MenuItem {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("id and name are required");
        }
        if (pricePaise <= 0) {
            throw new IllegalArgumentException("a menu item can't cost nothing: " + pricePaise);
        }
    }
}
