package dev.foodfleet.ch05.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch05.catalog.InMemoryCatalogApi;
import dev.foodfleet.ch05.ordering.domain.CancellationReason;
import dev.foodfleet.ch05.ordering.domain.DeliveryAddress;
import dev.foodfleet.ch05.ordering.domain.DomainException;
import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.Money;
import dev.foodfleet.ch05.ordering.domain.Order;
import dev.foodfleet.ch05.ordering.domain.OrderEvent;
import dev.foodfleet.ch05.ordering.domain.OrderStatus;
import dev.foodfleet.ch05.ordering.domain.PricingPolicy;
import dev.foodfleet.ch05.ordering.domain.Quantity;
import dev.foodfleet.ch05.ordering.infrastructure.CatalogAntiCorruptionLayer;
import dev.foodfleet.ch05.ordering.infrastructure.CatalogTranslator;
import dev.foodfleet.ch05.ordering.infrastructure.InMemoryOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The use case end to end, through the real ACL and repository adapters, without Spring. */
class OrderApplicationServiceTest {

    private final List<OrderEvent> published = new ArrayList<>();
    private final OrderApplicationService service = new OrderApplicationService(
            new InMemoryOrderRepository(),
            new CatalogAntiCorruptionLayer(new InMemoryCatalogApi(), new CatalogTranslator()),
            address -> Money.ofRupees("35.00"),
            new PricingPolicy(),
            published::addAll,
            Clock.fixed(Instant.parse("2026-10-01T13:30:00Z"), ZoneOffset.UTC));

    private static final DeliveryAddress HOME =
            new DeliveryAddress("12, 5th Cross", "Koramangala", "Bengaluru", "560034", "");

    @Test
    void placesAnOrderThroughTheAntiCorruptionLayer() {
        Order order = service.placeOrder(command("rest-biryani-house",
                item("dum-biryani", 2), item("boondi-raita", 1)));

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.price().orElseThrow().total()).isEqualTo(Money.ofRupees("871.85"));
        assertThat(published).singleElement().isInstanceOf(OrderEvent.OrderPlaced.class);
        assertThat(service.find(order.id())).contains(order);
    }

    @Test
    void seasonalItemIsUnavailableBecauseTheAclSaysSo() {
        assertThatThrownBy(() -> service.placeOrder(command("rest-biryani-house",
                item("dum-biryani", 1), item("mango-lassi", 1))))
                .isInstanceOf(DomainException.class)
                .hasMessage("Mango Lassi is not available right now");
        assertThat(published).isEmpty();
    }

    @Test
    void closedRestaurantIsRejected() {
        assertThatThrownBy(() -> service.placeOrder(command("rest-midnight-dosa", item("masala-dosa", 2))))
                .isInstanceOf(DomainException.class)
                .hasMessage("The restaurant is not accepting orders right now");
    }

    @Test
    void cancellationPublishesTheRefundOwed() {
        Order order = service.placeOrder(command("rest-biryani-house", item("dum-biryani", 1)));
        published.clear();

        service.cancel(order.id(), CancellationReason.CUSTOMER_CHANGED_MIND);

        assertThat(published).singleElement().isInstanceOfSatisfying(OrderEvent.OrderCancelled.class,
                e -> assertThat(e.refund()).isEqualTo(order.price().orElseThrow().total()));
    }

    private static PlaceOrderCommand command(String restaurant, PlaceOrderCommand.Item... items) {
        return new PlaceOrderCommand(new CustomerId("cust-42"), new RestaurantId(restaurant), HOME, List.of(items));
    }

    private static PlaceOrderCommand.Item item(String id, int qty) {
        return new PlaceOrderCommand.Item(new MenuItemId(id), Quantity.of(qty));
    }
}
