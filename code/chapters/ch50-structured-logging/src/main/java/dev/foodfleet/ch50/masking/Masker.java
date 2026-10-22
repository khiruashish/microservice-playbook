package dev.foodfleet.ch50.masking;

/**
 * Finds and masks the personal data and secrets FoodFleet's logs must never contain: e-mail addresses, UPI addresses,
 * Indian mobile numbers, payment card numbers (checked with Luhn, so order IDs and timestamps survive) and JWTs /
 * bearer tokens. Used as the last line of defence on every serialized log line ({@link MaskingEncoder}); types that
 * know they are personal data ({@link Pii}) mask themselves earlier.
 *
 * <p>A single pass over the text instead of a regular expression per kind: log lines are mostly digits and words that
 * match nothing, and the first version, six regexes, cost tens of microseconds per line (measured in chapter 50).
 */
public final class Masker {

    private static final String[] UPI_HANDLES = {"okhdfc", "okicici", "oksbi", "okaxis", "ybl", "ibl", "axl", "paytm", "upi",
            "apl", "axisbank", "icici", "sbi"};

    private Masker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = null;
        int copied = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            int[] hit = null;                                   // {start, end} of a match
            String replacement = null;
            if (c == '@') {
                int s = i;
                while (s > 0 && isLocal(text.charAt(s - 1))) {
                    s--;
                }
                int e = i + 1;
                while (e < n && isDomain(text.charAt(e))) {
                    e++;
                }
                while (e > i + 1 && text.charAt(e - 1) == '.') {
                    e--;
                }
                String domain = text.substring(i + 1, e);
                if (s < i && !domain.isEmpty() && (domain.indexOf('.') > 0 || isUpiHandle(domain))) {
                    hit = new int[] {s, e};
                    replacement = text.charAt(s) + "***@" + domain;
                }
            } else if (c == 'e' && text.startsWith("eyJ", i) && (i == 0 || !isBase64Url(text.charAt(i - 1)))) {
                int e = i;
                int dots = 0;
                while (e < n && (isBase64Url(text.charAt(e)) || (text.charAt(e) == '.' && dots < 2))) {
                    if (text.charAt(e) == '.') {
                        dots++;
                    }
                    e++;
                }
                if (dots == 2 && e - i > 20) {
                    hit = new int[] {i, e};
                    replacement = "[jwt]";
                }
            } else if ((c == 'B' || c == 'b') && text.regionMatches(true, i, "bearer ", 0, 7)) {
                int e = i + 7;
                while (e < n && (isBase64Url(text.charAt(e)) || "./+=~".indexOf(text.charAt(e)) >= 0)) {
                    e++;
                }
                if (e - (i + 7) >= 16) {
                    hit = new int[] {i, e};
                    replacement = "Bearer [token]";
                }
            } else if (isDigit(c) || (c == '+' && i + 1 < n && isDigit(text.charAt(i + 1)))) {
                if (i == 0 || !isDigit(text.charAt(i - 1))) {
                    int s = i;
                    int e = c == '+' ? i + 1 : i;
                    StringBuilder digits = new StringBuilder();
                    while (e < n) {
                        char d = text.charAt(e);
                        if (isDigit(d)) {
                            digits.append(d);
                            e++;
                        } else if ((d == ' ' || d == '-') && e + 1 < n && isDigit(text.charAt(e + 1)) && digits.length() > 0) {
                            e++;
                        } else {
                            break;
                        }
                    }
                    replacement = classify(digits.toString(), c == '+');
                    if (replacement != null) {
                        hit = new int[] {s, e};
                    } else {
                        i = Math.max(e, i + 1);                  // skip the whole run: no match starts inside it
                        continue;
                    }
                }
            }
            if (hit != null) {
                if (out == null) {
                    out = new StringBuilder(n);
                }
                out.append(text, copied, hit[0]).append(replacement);
                copied = hit[1];
                i = hit[1];
            } else {
                i++;
            }
        }
        if (out == null) {
            return text;
        }
        return out.append(text, copied, n).toString();
    }

    /**
     * A card (13–19 digits, a card network's first digit, valid Luhn) or an Indian mobile number; anything else, such as
     * epoch-millisecond timestamps (13 digits starting with 1) and order IDs, is left alone.
     */
    private static String classify(String digits, boolean plus) {
        int len = digits.length();
        char first = digits.charAt(0);
        boolean cardPrefix = first >= '2' && first <= '6' || first == '8';   // Visa, Mastercard, RuPay, Amex, Discover...
        if (len >= 13 && len <= 19 && cardPrefix && luhn(digits)) {
            return "**** **** **** " + digits.substring(len - 4);
        }
        boolean mobile = (len == 10 && digits.charAt(0) >= '6')
                || (len == 12 && digits.startsWith("91") && digits.charAt(2) >= '6')
                || (len == 11 && digits.charAt(0) == '0' && digits.charAt(1) >= '6');
        if (mobile && (plus || len <= 12)) {
            return "******" + digits.substring(len - 4);
        }
        return null;
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private static boolean isUpiHandle(String domain) {
        for (String h : UPI_HANDLES) {
            if (h.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLocal(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '%' || c == '+' || c == '-';
    }

    private static boolean isDomain(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-';
    }

    private static boolean isBase64Url(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}
