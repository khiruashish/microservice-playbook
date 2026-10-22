package dev.foodfleet.ch05.ordering.domain;

import static dev.foodfleet.ch05.ordering.domain.Fixtures.BIRYANI_HOUSE;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.BIRYANI_HOUSE_TERMS;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.DELIVERY_FEE;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.DUM_BIRYANI;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.KORAMANGALA;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.MASALA_DOSA_ELSEWHERE;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.NOW;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.RAITA;
import static dev.foodfleet.ch05.ordering.domain.Fixtures.SEEKH_KEBAB_SOLD_OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.DeliveryAddressChanged;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderCancelled;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderPlaced;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The aggregate's invariants, one test each, written in the ubiquitous language. No Spring, no mocks. */
class OrderTest {

    private final PricingPolicy pricing = new PricingPolicy();

    @Test
    void placingAnOrderFixesThePriceAndRecordsOrderPlaced() {
        Order order = Fixtures.draft();
        order.addItem(DUM_BIRYANI, Quantity.of(2));
        order.addItem(RAITA, Quantity.of(1));

        order.place(pricing, BIRYANI_HOUSE_TERMS, DELIVERY_FEE, NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.price()).contains(new PriceBreakdown(
                Money.ofRupees("767.00"), Money.ofRupees("30.00"), Money.ofRupees("39.85"),
                Money.ofRupees("35.00"), Money.ofRupees("871.85")));
        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderPlaced.class, e -> {
            assertThat(e.total()).isEqualTo(Money.ofRupees("871.85"));
            assertThat(e.itemCount()).isEqualTo(3);
        });
        assertThat(order.pullEvents()).as("events are handed out once").isEmpty();
    }

    @Test
    void addingTheSameDishTwiceMergesTheLine() {
        Order order = Fixtures.draft();
        order.addItem(DUM_BIRYANI, Quantity.of(1));
        order.addItem(DUM_BIRYANI, Quantity.of(2));

        assertThat(order.lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(Quantity.of(3)));
    }

    @Test
    void oneRestaurantPerOrder() {
        Order order = Fixtures.draft();
        assertThatThrownBy(() -> order.addItem(MASALA_DOSA_ELSEWHERE, Quantity.of(1)))
                .isInstanceOf(DomainException.class)
                .hasMessage("An order can contain dishes from one restaurant only");
    }

    @Test
    void soldOutDishesCannotBeAdded() {
        assertThatThrownBy(() -> Fixtures.draft().addItem(SEEKH_KEBAB_SOLD_OUT, Quantity.of(1)))
                .isInstanceOf(DomainException.class)
                .hasMessage("Seekh Kebab is not available right now");
    }

    @Test
    void atMostFiftyItemsInTotal() {
        Order order = Fixtures.draft();
        order.addItem(DUM_BIRYANI, Quantity.of(20));
        order.addItem(RAITA, Quantity.of(20));
        OrderableItem lassi = new OrderableItem(new MenuItemId("lassi"), BIRYANI_HOUSE, "Lassi", Money.ofRupees("99"), true);

        assertThatThrownBy(() -> order.addItem(lassi, Quantity.of(11)))
                .isInstanceOf(DomainException.class)
                .hasMessage("An order can have at most 50 items");
    }

    @Test
    void minimumOrderValueIsEnforced() {
        Order order = Fixtures.draft();
        order.addItem(RAITA, Quantity.of(2)); // ₹138 < ₹199

        assertThatThrownBy(() -> order.place(pricing, BIRYANI_HOUSE_TERMS, DELIVERY_FEE, NOW))
                .isInstanceOf(DomainException.class)
                .hasMessage("Minimum order is ₹199.00; your items total ₹138.00");
        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void closedRestaurantCannotReceiveOrders() {
        Order order = Fixtures.draft();
        order.addItem(DUM_BIRYANI, Quantity.of(1));
        RestaurantTerms closed = new RestaurantTerms(BIRYANI_HOUSE, false, Money.ofRupees("199"), Money.ofRupees("10"));

        assertThatThrownBy(() -> order.place(pricing, closed, DELIVERY_FEE, NOW))
                .isInstanceOf(DomainException.class)
                .hasMessage("The restaurant is not accepting orders right now");
    }

    @Test
    void linesAreFrozenOncePlaced() {
        Order order = Fixtures.placed();
        assertThatThrownBy(() -> order.addItem(RAITA, Quantity.of(1)))
                .isInstanceOf(DomainException.class)
                .hasMessage("Can't add dishes: the order is PLACED");
    }

    @Test
    void addressChangeAfterPlacingIsAnnouncedButBlockedOnceAccepted() {
        Order order = Fixtures.placed();
        DeliveryAddress office = new DeliveryAddress("Tower B, 7th Floor", "Bellandur", "Bengaluru", "560103", "");

        order.changeDeliveryAddress(office, NOW);
        assertThat(order.pullEvents()).singleElement().isInstanceOf(DeliveryAddressChanged.class);

        order.accept(NOW);
        assertThatThrownBy(() -> order.changeDeliveryAddress(KORAMANGALA, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void fullLifecycle() {
        Order order = Fixtures.placed();
        order.accept(NOW);
        order.startPreparing();
        order.dispatch("rider-7", NOW);
        order.deliver(NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("OrderAccepted", "OrderDispatched", "OrderDelivered");
    }

    @Test
    void statusesCannotBeSkipped() {
        Order order = Fixtures.placed();
        assertThatThrownBy(() -> order.dispatch("rider-7", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessage("An order can't go from PLACED to OUT_FOR_DELIVERY");
    }

    @Test
    void cancellingAPlacedOrderRefundsTheFullTotal() {
        Order order = Fixtures.placed();
        order.cancel(CancellationReason.RESTAURANT_REJECTED, NOW);

        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderCancelled.class,
                e -> assertThat(e.refund()).isEqualTo(Money.ofRupees("871.85")));
    }

    @Test
    void cancellingADraftRefundsNothing() {
        Order order = Fixtures.draft();
        order.cancel(CancellationReason.CUSTOMER_CHANGED_MIND, NOW);

        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderCancelled.class,
                e -> assertThat(e.refund()).isEqualTo(Money.ZERO));
    }

    @Test
    void outForDeliveryCannotBeCancelled() {
        Order order = Fixtures.placed();
        order.accept(NOW);
        order.startPreparing();
        order.dispatch("rider-7", NOW);

        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_CHANGED_MIND, NOW))
                .isInstanceOf(DomainException.class)
                .hasMessage("An order that is OUT_FOR_DELIVERY can no longer be cancelled");
    }

    @Test
    void linesAreReadOnlyFromOutside() {
        List<OrderLine> lines = Fixtures.placed().lines();
        assertThatThrownBy(() -> lines.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
