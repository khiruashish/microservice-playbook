package dev.foodfleet.ch29.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch29.catalog.SearchQuery.Typeahead;
import org.junit.jupiter.api.Test;

class SearchQueryTest {

    @Test
    void theLastWordIsAPrefix() {
        assertThat(SearchQuery.typeahead("chicken bir")).contains(new Typeahead("chicken", "bir"));
        assertThat(SearchQuery.typeahead("Hyderabadi chicken biry")).contains(new Typeahead("hyderabadi & chicken", "biry"));
        assertThat(SearchQuery.typeahead("Biryani")).contains(new Typeahead("", "biryani"));
    }

    @Test
    void operatorsAndPunctuationNeverReachTheQueryParser() {
        assertThat(SearchQuery.typeahead("paneer & !tikka | (masala):*")).contains(new Typeahead("paneer & tikka", "masala"));
        assertThat(SearchQuery.typeahead("chef's   special")).contains(new Typeahead("chef & s", "special"));
    }

    @Test
    void nothingWorthSearchingForGivesNoQuery() {
        assertThat(SearchQuery.typeahead("")).isEmpty();
        assertThat(SearchQuery.typeahead("  !! ")).isEmpty();
        assertThat(SearchQuery.typeahead("p")).isEmpty();
        assertThat(SearchQuery.typeahead(null)).isEmpty();
        assertThat(SearchQuery.typeahead("dal m")).contains(new Typeahead("dal", "m"));   // a short last word is fine after a full one
    }

    @Test
    void longInputIsCapped() {
        String typed = "a b c d e f g h i j k";
        assertThat(SearchQuery.words(typed)).hasSize(SearchQuery.MAX_WORDS);
    }

    @Test
    void theFallbackComparesPlainWords() {
        assertThat(SearchQuery.plain("  Panner-Butter  MASALA!")).isEqualTo("panner butter masala");
    }
}
