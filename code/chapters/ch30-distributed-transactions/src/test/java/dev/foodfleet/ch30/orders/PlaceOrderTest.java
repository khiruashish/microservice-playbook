package dev.foodfleet.ch30.orders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlaceOrderTest {

    @Test
    void nonsenseIsRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> new PlaceOrder(0, 5, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceOrder(1, 5, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountPaise");
    }
}
