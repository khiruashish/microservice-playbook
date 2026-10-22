package dev.foodfleet.ch35.orders;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;

/**
 * What a caller may see of an order (API3: object property level authorization). Each audience gets the fields
 * its job needs: the kitchen doesn't need the customer's address or phone; support sees the phone masked; nobody
 * outside the order service sees the risk score or the payment token. Null fields are left out of responses.
 */
public record OrderView(long id, long restaurantId, String status, long amountPaise, String deliveryAddress,
                        String contactPhone, String deliveryNote) {

    public static OrderView of(Order o, Caller caller) {
        boolean customer = caller.id().equals(o.customerId());
        boolean rider = caller.id().equals(o.riderId());
        boolean support = caller.has(Role.SUPPORT) || caller.has(Role.ADMIN);
        String address = customer || rider || support ? o.deliveryAddress() : null;
        String phone = customer || rider ? o.contactPhone() : support ? mask(o.contactPhone()) : null;
        return new OrderView(o.id(), o.restaurantId(), o.status().name(), o.amountPaise(), address, phone,
                o.deliveryNote());
    }

    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) {
            return phone == null ? null : "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
