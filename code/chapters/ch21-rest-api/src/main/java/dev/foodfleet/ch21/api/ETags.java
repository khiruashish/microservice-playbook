package dev.foodfleet.ch21.api;

/** Strong ETags derived from the order's version column. */
public final class ETags {

    private ETags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * The version an If-Match header asks for. If-Match uses strong comparison (RFC 9110): a weak
     * ETag (W/"3") never matches, nor does anything unparseable (-1 is never a real version). The
     * first ETag of a list is used; FoodFleet clients send exactly one.
     */
    public static long versionIn(String ifMatch) {
        String value = ifMatch.split(",")[0].strip();
        if (value.startsWith("W/") || !value.startsWith("\"") || !value.endsWith("\"") || value.length() < 3) {
            return -1;
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
