package dev.foodfleet.ch01.modulith.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Bootstraps ONLY the order module (standalone mode). The kitchen module is not running, so the
 * port it normally implements is mocked. That is exactly how a team tests a service whose
 * dependencies belong to other teams.
 */
@ApplicationModuleTest
class OrderModuleTests {

    @MockitoBean
    RestaurantAvailability availability;

    private final OrderManagement orders;

    OrderModuleTests(@Autowired OrderManagement orders) {
        this.orders = orders;
    }

    @Test
    void placingAnOrderPublishesOrderPlaced(Scenario scenario) {
        given(availability.canAccept("rest-order-1")).willReturn(true);

        scenario.stimulate(() -> orders.placeOrder(command("rest-order-1")))
                .andWaitForEventOfType(OrderPlaced.class)
                .matching(event -> event.restaurantId().equals("rest-order-1"))
                .toArriveAndVerify(event -> {
                    assertThat(event.totalPaise()).isEqualTo(76_700L);
                    assertThat(event.itemCount()).isEqualTo(3);
                    assertThat(orders.findOrder(event.orderId()))
                            .hasValueSatisfying(o -> assertThat(o.status()).isEqualTo(OrderStatus.PLACED));
                });
    }

    @Test
    void unavailableRestaurantPublishesNothing(AssertablePublishedEvents events) {
        given(availability.canAccept("rest-order-2")).willReturn(false);

        assertThatThrownBy(() -> orders.placeOrder(command("rest-order-2")))
                .isInstanceOf(RestaurantUnavailableException.class);

        List<OrderPlaced> published = new ArrayList<>();
        events.ofType(OrderPlaced.class).forEach(published::add);
        assertThat(published).isEmpty();
    }

    static PlaceOrder command(String restaurantId) {
        return new PlaceOrder("cust-42", restaurantId, List.of(
                new PlaceOrder.Line("dum-biryani", "Hyderabadi Dum Biryani", 2, 34_900),
                new PlaceOrder.Line("raita", "Boondi Raita", 1, 6_900)));
    }
}
