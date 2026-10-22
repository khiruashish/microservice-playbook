package dev.foodfleet.ch30.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchesTest {

    final List<OrderPlaced> batch = List.of(
            new OrderPlaced(11, 7, 900, 25_000), new OrderPlaced(12, 3, 100, 18_000),
            new OrderPlaced(13, 7, 100, 25_000));

    @Test
    void totalsAreSortedByKeySoRowsAreLockedInTheSameOrderEverywhere() {
        assertThat(Batches.portionsPerItem(batch)).isEqualTo(Map.of(100L, 2, 900L, 1));
        assertThat(List.copyOf(Batches.portionsPerItem(batch).keySet())).containsExactly(100L, 900L);
        assertThat(List.copyOf(Batches.paisePerCustomer(batch).keySet())).containsExactly(3L, 7L);
        assertThat(Batches.paisePerCustomer(batch).get(7L)).isEqualTo(50_000L);
    }

    @Test
    void theJsonMatchesTheRecordsetColumns() {
        assertThat(Batches.orders(batch.subList(0, 1)))
                .isEqualTo("[{\"order_id\":11,\"customer_id\":7,\"item_id\":900,\"amount_paise\":25000}]");
        assertThat(Batches.items(Batches.portionsPerItem(batch))).isEqualTo("[{\"item_id\":100,\"n\":2},{\"item_id\":900,\"n\":1}]");
        assertThat(Batches.customers(Batches.paisePerCustomer(batch)))
                .isEqualTo("[{\"customer_id\":3,\"amount\":18000},{\"customer_id\":7,\"amount\":50000}]");
    }
}
