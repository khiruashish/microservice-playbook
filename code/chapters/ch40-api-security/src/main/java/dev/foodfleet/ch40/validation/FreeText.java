package dev.foodfleet.ch40.validation;

import java.text.Normalizer;

/**
 * Free text from customers (order notes, reviews, addresses, support messages): anything a person might type, in
 * any script, but nothing that isn't text. Normalised first, then checked, then measured, so the checks see what
 * will be stored:
 * <ul>
 * <li>NFC normalisation: one encoding for "é" instead of two, so length limits, uniqueness and search agree</li>
 * <li>no control characters (except newline and tab where the field is multi-line), no lone surrogates, no
 * noncharacters</li>
 * <li>no bidirectional overrides and isolates (U+202A–U+202E, U+2066–U+2069): they make text display differently
 * from what it is (the "Trojan Source" trick), which matters in anything a support agent or a restaurant reads</li>
 * <li>a length limit in code points (what a person counts), after trimming</li>
 * </ul>
 * Deliberately absent: rejecting quotes, angle brackets, "select", "--". "Don't add onions -- select the mild
 * one" is a valid order note; SQL injection is prevented by bind parameters and XSS by output encoding, not by
 * refusing apostrophes.
 */
public final class FreeText {

    private FreeText() {
    }

    public static String clean(String field, String raw, int maxCodePoints, boolean multiline) {
        if (raw == null) {
            throw new InvalidInput(field, "required");
        }
        // checked before normalising: a huge value is rejected without spending time on it
        if (raw.length() > maxCodePoints * 4L + 16) {
            throw new InvalidInput(field, "too long");
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        int count = 0;
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (Character.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c) || i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    throw new InvalidInput(field, "malformed text");
                }
            }
            int cp = s.codePointAt(i);
            if (cp == '\n' || cp == '\t') {
                if (!multiline) {
                    throw new InvalidInput(field, "single line expected");
                }
            } else if (cp == '\r') {
                if (!multiline) {
                    throw new InvalidInput(field, "single line expected");
                }
            } else if (Character.getType(cp) == Character.CONTROL) {
                throw new InvalidInput(field, "control character");
            } else if ((cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069)) {
                throw new InvalidInput(field, "bidirectional control");
            } else if ((cp & 0xFFFE) == 0xFFFE || (cp >= 0xFDD0 && cp <= 0xFDEF)) {
                throw new InvalidInput(field, "noncharacter");
            }
            if (++count > maxCodePoints) {
                throw new InvalidInput(field, "too long");
            }
            i += Character.charCount(cp);
        }
        return multiline ? s.replace("\r\n", "\n").replace('\r', '\n') : s;
    }
}
