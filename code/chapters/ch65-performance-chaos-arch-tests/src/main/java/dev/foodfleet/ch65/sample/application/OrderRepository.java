package dev.foodfleet.ch65.sample.application;

import dev.foodfleet.ch65.sample.domain.order.Order;

/** The port the use case stores orders through; infrastructure implements it. */
public interface OrderRepository {
    long save(Order order);
}
