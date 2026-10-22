package dev.foodfleet.ch35.orders;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The only order fields a customer may change, validated (API3: no mass assignment). Status, amount, owner and
 * restaurant are not in this type, so no request can set them, however the JSON is crafted. Unknown fields are
 * rejected rather than ignored, so a client that thinks it changed something learns that it didn't.
 */
public record OrderPatch(String deliveryNote, String contactPhone) {

    static final Set<String> FIELDS = Set.of("deliveryNote", "contactPhone");
    static final Pattern PHONE = Pattern.compile("\\+?[0-9]{10,13}");
    static final int MAX_NOTE = 200;

    public OrderPatch {
        if (deliveryNote != null
                && (deliveryNote.length() > MAX_NOTE || deliveryNote.chars().anyMatch(Character::isISOControl))) {
            throw new BadRequestException("deliveryNote: at most " + MAX_NOTE + " characters, no control characters");
        }
        if (contactPhone != null && !PHONE.matcher(contactPhone).matches()) {
            throw new BadRequestException("contactPhone: 10 to 13 digits, optional leading +");
        }
    }

    /** From a parsed JSON object (the lab server); Spring binds the record directly, with unknown fields failing. */
    public static OrderPatch from(Map<String, ?> json) {
        for (String field : json.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new BadRequestException("field '" + field + "' cannot be changed");
            }
        }
        Object note = json.get("deliveryNote");
        Object phone = json.get("contactPhone");
        if ((note != null && !(note instanceof String)) || (phone != null && !(phone instanceof String))) {
            throw new BadRequestException("fields must be strings");
        }
        return new OrderPatch((String) note, (String) phone);
    }
}
