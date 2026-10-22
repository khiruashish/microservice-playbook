package dev.foodfleet.ch22.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Two client-side habits that pay for themselves:
 * <ul>
 *   <li><b>Follow the server's cursors</b>: {@link #allOrders} pages lazily, fetching the next page only
 *       when the caller reads that far, using the cursor from the previous response.</li>
 *   <li><b>Revalidate, don't refetch</b>: {@link #order} keeps the last representation and its ETag and
 *       sends If-None-Match; a 304 costs no body and no JSON parsing.</li>
 * </ul>
 */
@Service
public class OrderHistoryReader {

    private record Cached(String etag, OrderHistoryApi.Order order) {
    }

    private final OrderHistoryApi api;
    private final int pageSize;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public OrderHistoryReader(OrderHistoryApi api) {
        this(api, 50);
    }

    OrderHistoryReader(OrderHistoryApi api, int pageSize) {
        this.api = api;
        this.pageSize = pageSize;
    }

    public Stream<OrderHistoryApi.OrderView> allOrders(String customerId) {
        Iterator<OrderHistoryApi.OrderView> pages = new Iterator<>() {
            private List<OrderHistoryApi.OrderView> buffer = new ArrayList<>();
            private int index;
            private String cursor;
            private boolean last;

            @Override
            public boolean hasNext() {
                while (index >= buffer.size() && !last) {
                    OrderHistoryApi.OrderPage page = api.page(customerId, pageSize, cursor);
                    buffer = page.items();
                    index = 0;
                    cursor = cursorOf(page.next());
                    last = cursor == null;
                }
                return index < buffer.size();
            }

            @Override
            public OrderHistoryApi.OrderView next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return buffer.get(index++);
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(pages, Spliterator.ORDERED), false);
    }

    /** The cursor parameter of a 'next' link, which the client passes back without interpreting it. */
    static String cursorOf(String nextLink) {
        if (nextLink == null) {
            return null;
        }
        for (String pair : nextLink.substring(nextLink.indexOf('?') + 1).split("&")) {
            if (pair.startsWith("cursor=")) {
                return pair.substring("cursor=".length());
            }
        }
        return null;
    }

    public Optional<OrderHistoryApi.Order> order(String orderId) {
        Cached cached = cache.get(orderId);
        ResponseEntity<OrderHistoryApi.Order> response = api.order(orderId, cached == null ? null : cached.etag());
        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_MODIFIED) && cached != null) {
            return Optional.of(cached.order());
        }
        OrderHistoryApi.Order fresh = response.getBody();
        String etag = response.getHeaders().getETag();
        if (fresh != null && etag != null) {
            cache.put(orderId, new Cached(etag, fresh));
        }
        return Optional.ofNullable(fresh);
    }
}
