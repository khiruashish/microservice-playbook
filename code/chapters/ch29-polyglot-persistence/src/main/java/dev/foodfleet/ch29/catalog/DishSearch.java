package dev.foodfleet.ch29.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Dish search in the catalog's own PostgreSQL: ranked full-text search with a prefix on the last word,
 * and only when that finds nothing, the items whose names are most similar by trigrams (typos, spelling
 * variants). Measured on 192,000 items: 29 of 29 test queries found the dish at a median of 14 ms; the
 * trigram step on its own had a median of 55-61 ms, so it runs only when the words find nothing.
 */
@Repository
public class DishSearch {

    public enum MatchedBy { WORDS, SIMILAR_NAME }

    public record Hit(long itemId, long restaurantId, String name, int pricePaise, MatchedBy matchedBy) {
    }

    // complete words stemmed; the partial last word as a prefix, stemmed OR as typed
    static final String BY_WORDS = """
            WITH q AS (
                SELECT CASE WHEN :complete = '' THEN p ELSE to_tsquery('english', :complete) && p END AS q
                  FROM (SELECT to_tsquery('english', :partial || ':*') || to_tsquery('simple', :partial || ':*') AS p) last_word)
            SELECT i.id, i.restaurant_id, i.name, i.price_paise
              FROM menu_items i, q
             WHERE i.search @@ q.q AND i.available
             ORDER BY ts_rank(i.search, q.q) DESC, i.id
             LIMIT :limit""";

    // <% : word similarity of the typed text to some part of the name >= pg_trgm.word_similarity_threshold
    // (default 0.6); <<-> : 1 - word similarity, nearest first. Both use the trigram index.
    static final String BY_SIMILAR_NAME = """
            SELECT i.id, i.restaurant_id, i.name, i.price_paise
              FROM menu_items i
             WHERE i.available AND :text <% lower(i.name)
             ORDER BY :text <<-> lower(i.name), i.id
             LIMIT :limit""";

    private final JdbcClient jdbc;

    public DishSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> search(String typed, int limit) {
        Optional<SearchQuery.Typeahead> query = SearchQuery.typeahead(typed);
        if (query.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = jdbc.sql(BY_WORDS).param("complete", query.get().complete())
                .param("partial", query.get().partial()).param("limit", limit)
                .query((rs, n) -> new Hit(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), MatchedBy.WORDS))
                .list();
        if (!hits.isEmpty()) {
            return hits;
        }
        return jdbc.sql(BY_SIMILAR_NAME).param("text", SearchQuery.plain(typed)).param("limit", limit)
                .query((rs, n) -> new Hit(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), MatchedBy.SIMILAR_NAME))
                .list();
    }
}
