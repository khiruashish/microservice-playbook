package dev.foodfleet.ch29.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns what a customer typed into the parts of a PostgreSQL tsquery for search-as-you-type: every word
 * must match, the last one as a prefix. Only letters and digits survive, so nothing the user types can
 * reach the tsquery parser as an operator.
 *
 * <p>The last word is matched both stemmed and as typed ({@link DishSearch#BY_WORDS}): the English
 * stemmer turns a half-typed "biry" into "biri", which is not a prefix of the indexed "biryani".
 */
public final class SearchQuery {

    static final int MAX_WORDS = 8;

    /** {@code complete} is the finished words joined with {@code &} (empty if none); {@code partial} the last one. */
    public record Typeahead(String complete, String partial) {
    }

    private SearchQuery() {
    }

    /** The words, lower-cased, at most {@value #MAX_WORDS}. */
    public static List<String> words(String typed) {
        if (typed == null) {
            return List.of();
        }
        String cleaned = typed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split(" ")).limit(MAX_WORDS).toList();
    }

    /** Empty when there is nothing worth searching for (no words, or a single one-letter word). */
    public static Optional<Typeahead> typeahead(String typed) {
        List<String> words = words(typed);
        if (words.isEmpty() || (words.size() == 1 && words.getFirst().length() < 2)) {
            return Optional.empty();
        }
        return Optional.of(new Typeahead(String.join(" & ", words.subList(0, words.size() - 1)), words.getLast()));
    }

    /** The text the trigram fallback compares with item names. */
    public static String plain(String typed) {
        return String.join(" ", words(typed));
    }
}
