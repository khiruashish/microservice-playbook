package dev.foodfleet.ch19.consumer;

import dev.foodfleet.ch19.bus.Events.MenuItemUpdated;
import java.util.function.Consumer;

/** Consumes state-carrying events: the event IS the data. No calls back, nothing to wait for. */
public final class StateTransferConsumer implements Consumer<MenuItemUpdated> {

    private final Replica replica;

    public StateTransferConsumer(Replica replica) {
        this.replica = replica;
    }

    @Override
    public void accept(MenuItemUpdated event) {
        replica.apply(event.item());
    }
}
