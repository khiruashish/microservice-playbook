package dev.foodfleet.ch13.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every event store implementation must pass these. */
abstract class EventStoreContract {

    abstract EventStore store();

    private static NewEvent event(String type, String value) {
        return new NewEvent(type, 1, Map.of("value", value, "note", "a=b&c d"));
    }

    @Test
    void appendsAndReadsAStreamInOrder() {
        EventStore store = store();
        assertThat(store.append("s-1", EventStore.NO_STREAM, List.of(event("A", "1"), event("B", "2")))).isEqualTo(2);
        assertThat(store.append("s-1", 2, List.of(event("C", "3")))).isEqualTo(3);

        assertThat(store.readStream("s-1", 0)).extracting(StoredEvent::type).containsExactly("A", "B", "C");
        assertThat(store.readStream("s-1", 2)).extracting(StoredEvent::streamVersion).containsExactly(3L);
        assertThat(store.readStream("s-1", 0).get(0).payload()).containsEntry("note", "a=b&c d");
    }

    @Test
    void rejectsAWrongExpectedVersion() {
        EventStore store = store();
        store.append("s-1", EventStore.NO_STREAM, List.of(event("A", "1")));

        assertThatThrownBy(() -> store.append("s-1", EventStore.NO_STREAM, List.of(event("B", "2"))))
                .isInstanceOf(EventStore.ConcurrencyException.class);
        assertThat(store.readStream("s-1", 0)).hasSize(1);
    }

    @Test
    void readAllInterleavesStreamsByGlobalPosition() {
        EventStore store = store();
        store.append("s-1", 0, List.of(event("A", "1")));
        store.append("s-2", 0, List.of(event("B", "2")));
        store.append("s-1", 1, List.of(event("C", "3")));

        assertThat(store.readAll(0, 10)).extracting(StoredEvent::type).containsExactly("A", "B", "C");
        assertThat(store.readAll(1, 1)).extracting(StoredEvent::type).containsExactly("B");
        assertThat(store.readAll(3, 10)).isEmpty();
    }
}
