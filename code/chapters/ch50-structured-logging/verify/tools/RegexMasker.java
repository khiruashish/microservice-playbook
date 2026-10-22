

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first version of {@code Masker}: one regular expression per kind of personal data (e-mail, UPI, mobile, card with
 * Luhn, JWT, bearer token), with cheap pre-checks. Kept in the lab to compare its cost with the single-pass scanner.
 */
final class RegexMasker {

    /** One kind of sensitive value and how to mask it. */
    record Rule(String name, Pattern pattern, java.util.function.Function<String, String> mask) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("jwt", Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b"), s -> "[jwt]"),
            new Rule("bearer", Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{16,}"), s -> "Bearer [token]"),
            new Rule("email", Pattern.compile("\\b([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b"),
                    s -> s.charAt(0) + "***@" + s.substring(s.indexOf('@') + 1)),
            new Rule("upi", Pattern.compile("\\b([A-Za-z0-9._-])[A-Za-z0-9._-]{1,}@(ok[a-z]+|ybl|ibl|axl|paytm|upi|apl|axisbank|icici|sbi)\\b"),
                    s -> s.charAt(0) + "***@" + s.substring(s.indexOf('@') + 1)),
            new Rule("card", Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b"), RegexMasker::maskCard),
            new Rule("phone", Pattern.compile("(?<![\\d])(?:\\+91[ -]?|0)?[6-9]\\d{4}[ -]?\\d{5}(?![\\d])"),
                    s -> "******" + s.replaceAll("\\D", "").substring(s.replaceAll("\\D", "").length() - 4)));

    private RegexMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // cheap checks first: most log lines contain nothing any rule could match, and the regexes are the cost
        boolean at = text.indexOf('@') >= 0;
        boolean jwt = text.contains("eyJ");
        boolean bearer = text.contains("earer");
        boolean digits = hasDigitRun(text, 10);
        if (!at && !jwt && !bearer && !digits) {
            return text;
        }
        String out = text;
        for (Rule r : RULES) {
            boolean worth = switch (r.name()) {
                case "jwt" -> jwt;
                case "bearer" -> bearer;
                case "email", "upi" -> at;
                default -> digits;
            };
            if (!worth) {
                continue;
            }
            Matcher m = r.pattern().matcher(out);
            if (!m.find()) {
                continue;
            }
            StringBuilder b = new StringBuilder(out.length());
            do {
                m.appendReplacement(b, Matcher.quoteReplacement(r.mask().apply(m.group())));
            } while (m.find());
            m.appendTail(b);
            out = b.toString();
        }
        return out;
    }

    /** Whether {@code text} has at least {@code n} digits in a row, allowing single spaces or dashes between them. */
    static boolean hasDigitRun(String text, int n) {
        int run = 0;
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                if (++run >= n) {
                    return true;
                }
            } else if (!((c == ' ' || c == '-') && prev >= '0' && prev <= '9')) {
                run = 0;
            }
            prev = c;
        }
        return false;
    }

    /** Card numbers keep their last four digits; digit runs that fail the Luhn check (order IDs) are left alone. */
    static String maskCard(String candidate) {
        String digits = candidate.replaceAll("[ -]", "");
        if (!luhn(digits)) {
            return candidate;
        }
        return "**** **** **** " + digits.substring(digits.length() - 4);
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
}
