package dev.foodfleet.ch37.ordersvc.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderRulesTest {

    final Order delivered = new Order(1, "c01", 7, "DELIVERED", 45000);
    final Order placed = new Order(2, "c01", 7, "PLACED", 30000);

    @Test
    void customersReadAndChangeOnlyTheirOwnOpenOrders() {
        Set<String> customer = Set.of("ROLE_CUSTOMER", "SCOPE_orders:read");
        assertThat(OrderRules.canRead("c01", customer, Set.of(), delivered)).isTrue();
        assertThat(OrderRules.canRead("c02", customer, Set.of(), delivered)).isFalse();
        assertThat(OrderRules.canChange("c01", customer, placed)).isTrue();
        assertThat(OrderRules.canChange("c01", customer, delivered)).isFalse();
    }

    @Test
    void ownersSeeTheirRestaurantsOrdersAndStaffRefundDeliveredOnes() {
        assertThat(OrderRules.canRead("o1", Set.of("ROLE_RESTAURANT_OWNER"), Set.of(7L), delivered)).isTrue();
        assertThat(OrderRules.canRead("o2", Set.of("ROLE_RESTAURANT_OWNER"), Set.of(8L), delivered)).isFalse();
        assertThat(OrderRules.canRefund(Set.of("ROLE_SUPPORT"), delivered)).isTrue();
        assertThat(OrderRules.canRefund(Set.of("ROLE_SUPPORT"), placed)).isFalse();
        assertThat(OrderRules.canRefund(Set.of("ROLE_CUSTOMER"), delivered)).isFalse();
    }
}
