package dev.foodfleet.ch65.sample.config;

import dev.foodfleet.ch65.sample.api.OrderController;
import dev.foodfleet.ch65.sample.application.PlaceOrder;
import dev.foodfleet.ch65.sample.infrastructure.JdbcOrderRepository;

import java.sql.Connection;
import java.util.function.Supplier;

/** Puts the pieces together; the only place that knows them all. */
public final class Wiring {

    private Wiring() {
    }

    public static OrderController controller(Supplier<Connection> db) {
        return new OrderController(new PlaceOrder(new JdbcOrderRepository(db)));
    }
}
