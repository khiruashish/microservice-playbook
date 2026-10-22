package dev.foodfleet.ch13.projection;

import dev.foodfleet.ch13.eventstore.StoredEvent;

/**
 * A read model built from events. Projections are disposable: {@link #reset()} and replaying the
 * whole log must produce the same result, which is how a projection is fixed or a new one added.
 */
public interface Projection {

    String name();

    void handle(StoredEvent event);

    void reset();
}
