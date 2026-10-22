package dev.foodfleet.ch37.ordersvc.orders;

public record Order(long id, String customerId, long restaurantId, String status, long amountPaise) {
}
