package dev.foodfleet.ch22.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class OrderHistoryReaderTest {

    /** Five orders served two per page, with real-looking 'next' links; records which cursors it saw. */
    static final class FakeApi implements OrderHistoryApi {
        final List<String> cursorsRequested = new ArrayList<>();

        @Override
        public OrderPage page(String customerId, int limit, String cursor) {
            cursorsRequested.add(cursor);
            int start = cursor == null ? 0 : Integer.parseInt(cursor.substring(1));
            List<OrderView> items = new ArrayList<>();
            for (int i = start; i < Math.min(start + limit, 5); i++) {
                items.add(new OrderView("o-" + (5 - i), "R", 100, "DELIVERED", "2026-10-0" + (5 - i) + "T12:00:00Z",
                        "/api/orders/o-" + (5 - i)));
            }
            String next = start + limit < 5
                    ? "/api/customers/" + customerId + "/orders?limit=" + limit + "&cursor=c" + (start + limit)
                    : null;
            return new OrderPage(items, next);
        }

        @Override
        public ResponseEntity<Order> order(String orderId, String ifNoneMatch) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void readsEveryPageByFollowingTheServersCursors() {
        FakeApi api = new FakeApi();

        List<String> ids = new OrderHistoryReader(api, 2).allOrders("c-1").map(OrderHistoryApi.OrderView::id).toList();

        assertThat(ids).containsExactly("o-5", "o-4", "o-3", "o-2", "o-1");
        assertThat(api.cursorsRequested).containsExactly(null, "c2", "c4");
    }

    @Test
    void fetchesOnlyThePagesTheCallerReads() {
        FakeApi api = new FakeApi();

        List<String> firstThree = new OrderHistoryReader(api, 2).allOrders("c-1").limit(3)
                .map(OrderHistoryApi.OrderView::id).toList();

        assertThat(firstThree).containsExactly("o-5", "o-4", "o-3");
        assertThat(api.cursorsRequested).hasSize(2);
    }

    @Test
    void theCursorIsTakenFromTheNextLinkAsIs() {
        assertThat(OrderHistoryReader.cursorOf("/api/customers/c-1/orders?limit=20&cursor=djF8MjAy"))
                .isEqualTo("djF8MjAy");
        assertThat(OrderHistoryReader.cursorOf(null)).isNull();
    }
}
