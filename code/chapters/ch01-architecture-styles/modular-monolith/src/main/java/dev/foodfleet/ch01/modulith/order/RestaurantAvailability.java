package dev.foodfleet.ch01.modulith.order;

/**
 * Port (SPI) owned by the order module: "can this restaurant take an order right now?"
 *
 * <p>The order module defines the interface; someone else implements it. Today the kitchen
 * module implements it in-process. After extraction, an HTTP adapter inside the order module
 * implements it by calling kitchen-service. The order logic never changes. This interface is the
 * <em>extraction seam</em>.
 *
 * <p>Dependency direction: kitchen → order (kitchen implements order's port and listens to
 * order's events). Order depends on nobody, so there is no cycle.
 */
public interface RestaurantAvailability {

    boolean canAccept(String restaurantId);
}
