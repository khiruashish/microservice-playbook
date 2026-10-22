package dev.foodfleet.ch40.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address check that takes time proportional to the input, whatever the input. Length limits come first
 * (RFC 5321: 64 for the local part, 254 for the whole path), then the address is split at the last '@' and each
 * part is matched by a pattern with no nested quantifiers, so the regex engine has nothing to backtrack over.
 * Whether the address exists is a different question, answered by sending it a confirmation link.
 */
public final class EmailAddress {

    // dot-atom local part (RFC 5322 atext): possessive, and no group repeated inside a repeated group
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]++(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]++)*+");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private EmailAddress() {
    }

    /** Returns the address with the domain lower-cased (the local part is case-sensitive by the RFC). */
    public static String parse(String field, String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 254) {
            throw new InvalidInput(field, "not an email address");
        }
        int at = raw.lastIndexOf('@');
        if (at < 1 || at > 64 || at == raw.length() - 1) {
            throw new InvalidInput(field, "not an email address");
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at + 1).toLowerCase(Locale.ROOT);
        if (!LOCAL.matcher(local).matches()) {
            throw new InvalidInput(field, "not an email address");
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            throw new InvalidInput(field, "not an email address");
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw new InvalidInput(field, "not an email address");
            }
        }
        return local + "@" + domain;
    }
}
