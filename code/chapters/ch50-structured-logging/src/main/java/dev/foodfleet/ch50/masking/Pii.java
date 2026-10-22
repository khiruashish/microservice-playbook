package dev.foodfleet.ch50.masking;

/**
 * Personal data that masks itself: pass a {@code Pii} value to a log call (as an argument, a key-value pair or inside a
 * record) and only the masked form is ever turned into text. The real value is available to the code that needs it.
 * Masking at the source is cheap and precise, but only covers the values someone remembered to wrap.
 */
public record Pii(String kind, String value) {

    public static Pii email(String value) {
        return new Pii("email", value);
    }

    public static Pii phone(String value) {
        return new Pii("phone", value);
    }

    public static Pii vpa(String value) {
        return new Pii("upi", value);
    }

    @Override
    public String toString() {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int at = value.indexOf('@');
        return switch (kind) {
            case "email", "upi" -> at > 0 ? value.charAt(0) + "***" + value.substring(at) : "***";
            case "phone" -> "******" + value.substring(Math.max(0, value.length() - 4));
            default -> "***";
        };
    }
}
