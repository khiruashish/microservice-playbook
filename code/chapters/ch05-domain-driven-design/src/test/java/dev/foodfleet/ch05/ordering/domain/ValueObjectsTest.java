package dev.foodfleet.ch05.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void moneyIsComparedByValueAndNeverNegative() {
        assertThat(Money.ofRupees("349.00")).isEqualTo(Money.ofPaise(34_900));
        assertThat(Money.ofRupees("349")).isEqualTo(Money.ofRupees("349.00"));
        assertThat(Money.ofRupees("0.5")).isEqualTo(Money.ofPaise(50));
        assertThatThrownBy(() -> Money.ofPaise(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.ofRupees("349.999")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.ofRupees("three hundred")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyArithmeticRoundsHalfUpToThePaisa() {
        assertThat(Money.ofRupees("797.00").percentage(500)).isEqualTo(Money.ofRupees("39.85"));
        assertThat(Money.ofPaise(10).percentage(500)).isEqualTo(Money.ofPaise(1));   // 0.5 → 1
        assertThat(Money.ofPaise(9).percentage(500)).isEqualTo(Money.ofPaise(0));    // 0.45 → 0
        assertThat(Money.ofRupees("349.00").times(2).toString()).isEqualTo("₹698.00");
    }

    @Test
    void quantityEnforcesItsRangeEvenWhenAdded() {
        assertThat(Quantity.of(15).plus(Quantity.of(5))).isEqualTo(Quantity.of(20));
        assertThatThrownBy(() -> Quantity.of(15).plus(Quantity.of(6))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantity.of(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressValidatesPincode() {
        assertThatThrownBy(() -> new DeliveryAddress("1 Main Rd", "Area", "Bengaluru", "56003", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DeliveryAddress("1 Main Rd", "Area", "Bengaluru", "560034", null).landmark()).isEmpty();
    }
}
