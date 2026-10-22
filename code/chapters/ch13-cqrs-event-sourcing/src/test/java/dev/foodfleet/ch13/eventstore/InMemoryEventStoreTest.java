package dev.foodfleet.ch13.eventstore;

import java.time.Clock;

class InMemoryEventStoreTest extends EventStoreContract {

    @Override
    EventStore store() {
        return new InMemoryEventStore(Clock.systemUTC());
    }
}
