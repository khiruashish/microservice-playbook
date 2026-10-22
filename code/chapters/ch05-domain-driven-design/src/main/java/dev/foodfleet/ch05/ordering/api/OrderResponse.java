package dev.foodfleet.ch05.ordering.api;

import dev.foodfleet.ch05.ordering.domain.Order;
import dev.foodfleet.ch05.ordering.domain.PriceBreakdown;
import java.util.List;

/** Published representation of an order. Money is exposed in paise, the unit in FoodFleet's API style guide. */
record OrderResponse(String orderId, String status, String restaurantId, List<Line> lines, Price price) {

    record Line(String menuItemId, String name, int quantity, long unitPricePaise, long lineTotalPaise) {
    }

    record Price(long itemTotalPaise, long packagingPaise, long gstPaise, long deliveryFeePaise, long totalPaise) {

        static Price from(PriceBreakdown p) {
            return new Price(p.itemTotal().paise(), p.packaging().paise(), p.gst().paise(),
                    p.deliveryFee().paise(), p.total().paise());
        }
    }

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id().value().toString(),
                order.status().name(),
                order.restaurantId().value(),
                order.lines().stream()
                        .map(l -> new Line(l.menuItemId().value(), l.name(), l.quantity().value(),
                                l.unitPrice().paise(), l.lineTotal().paise()))
                        .toList(),
                order.price().map(Price::from).orElse(null));
    }
}
