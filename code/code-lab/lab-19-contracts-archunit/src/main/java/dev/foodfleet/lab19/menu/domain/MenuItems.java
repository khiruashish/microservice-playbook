package dev.foodfleet.lab19.menu.domain;

import java.util.Optional;

/** A port: the domain says what it needs; infrastructure provides it. */
public interface MenuItems {

    Optional<MenuItem> find(String restaurantId, String itemId);
}
