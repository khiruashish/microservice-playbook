package dev.foodfleet.ch35.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    final OrderStore store = new OrderStore();
    final OrderService service = new OrderService(store, new OrderAccessPolicy(), PageLimits.DEFAULT);
    final Caller asha = new Caller("c01", Set.of(Role.CUSTOMER), Set.of());
    final Caller ravi = new Caller("c02", Set.of(Role.CUSTOMER), Set.of());
    final Caller kitchen = new Caller("owner1", Set.of(Role.RESTAURANT_OWNER), Set.of(7L));
    final Caller support = new Caller("s1", Set.of(Role.SUPPORT), Set.of());
    final Caller admin = new Caller("a1", Set.of(Role.ADMIN), Set.of());

    OrderServiceTest() {
        store.save(new Order(1, "c01", 7, "rider1", OrderStatus.PLACED, 45000, "12 MG Road", "+919812345678", null, 12,
                "tok_1"));
        store.save(new Order(2, "c01", 9, "rider2", OrderStatus.DELIVERED, 30000, "12 MG Road", "+919812345678", null,
                3, "tok_2"));
        store.save(new Order(3, "c02", 7, "rider1", OrderStatus.PLACED, 20000, "4 Park St", "+919800000001", null, 50,
                "tok_3"));
    }

    @Test
    void anotherCustomersOrderLooksExactlyLikeOneThatDoesNotExist() {
        assertThatThrownBy(() -> service.get(ravi, 1)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("order 1 not found");
        assertThatThrownBy(() -> service.get(ravi, 99)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("order 99 not found");
    }

    @Test
    void eachAudienceSeesOnlyTheFieldsItsJobNeeds() {
        OrderView own = service.get(asha, 1);
        assertThat(own.contactPhone()).isEqualTo("+919812345678");
        OrderView forKitchen = service.get(kitchen, 1);
        assertThat(forKitchen.deliveryAddress()).isNull();
        assertThat(forKitchen.contactPhone()).isNull();
        assertThat(service.get(support, 1).contactPhone()).isEqualTo("*********5678");
    }

    @Test
    void aCustomerChangesOnlyTheAllowedFieldsOfTheirOwnOpenOrder() {
        assertThat(service.update(asha, 1, OrderPatch.from(Map.of("deliveryNote", "Ring twice"))).deliveryNote())
                .isEqualTo("Ring twice");
        assertThatThrownBy(() -> service.update(asha, 2, new OrderPatch("late", null)))
                .isInstanceOf(ForbiddenException.class);                       // delivered: no longer changeable
        assertThatThrownBy(() -> service.update(ravi, 1, new OrderPatch("mine now", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void fieldsOutsideThePatchAreRefusedNotIgnored() {
        assertThatThrownBy(() -> OrderPatch.from(Map.of("status", "REFUNDED")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("'status' cannot be changed");
        assertThatThrownBy(() -> OrderPatch.from(Map.of("deliveryNote", "x", "amountPaise", 1)))
                .isInstanceOf(BadRequestException.class);
        assertThat(store.find(1).get().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(store.find(1).get().amountPaise()).isEqualTo(45000L);
    }

    @Test
    void refundsNeedTheSupportRoleWhateverTheClientShows() {
        assertThatThrownBy(() -> service.refund(asha, 2)).isInstanceOf(ForbiddenException.class);
        assertThat(service.refund(support, 2).status()).isEqualTo("REFUNDED");
    }

    @Test
    void myOrdersIsScopedByWhoIsAskingNotByAParameter() {
        assertThat(service.list(asha, null, null).stream().map(OrderView::id).toList()).isEqualTo(List.of(1L, 2L));
        assertThat(service.list(kitchen, null, null).stream().map(OrderView::id).toList()).isEqualTo(List.of(1L, 3L));
    }

    @Test
    void exportIsAnAdminFunctionAndNeverIncludesInternalFields() {
        assertThatThrownBy(() -> service.exportAll(support)).isInstanceOf(ForbiddenException.class);
        assertThat(service.exportAll(admin)).hasSize(3);
        // OrderView has no risk score or payment token at all
        assertThat(java.util.Arrays.stream(OrderView.class.getRecordComponents()).map(c -> c.getName())
                .filter(n -> n.equals("riskScore") || n.equals("paymentToken")).count()).isEqualTo(0L);
    }
}
