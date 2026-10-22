package dev.foodfleet.ch29.catalog;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The menu card: one JSONB document per restaurant, built from the tables. Reading a whole menu from the
 * document was 13-14× faster than assembling it from five tables (0.9-1.0 ms vs 13 ms), so apps read the
 * card; writes go to the tables, where a price change costs 0.7 ms instead of 3.2-3.4 ms and 15× less WAL.
 */
@Repository
public class MenuCards {

    public record Card(long restaurantId, int version, String json) {
    }

    static final String REBUILD = """
            INSERT INTO menu_cards (restaurant_id, version, card)
            SELECT r.id, 1, jsonb_build_object('restaurantId', r.id, 'name', r.name, 'city', r.city, 'sections',
              coalesce((SELECT jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name, 'items',
                 coalesce((SELECT jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name, 'description', i.description,
                          'price', i.price_paise, 'veg', i.is_veg, 'available', i.available, 'optionGroups',
                          coalesce((SELECT jsonb_agg(jsonb_build_object('name', g.name, 'min', g.min_choices,
                                       'max', g.max_choices, 'options',
                                       coalesce((SELECT jsonb_agg(jsonb_build_object('name', o.name,
                                                    'priceDelta', o.price_delta_paise) ORDER BY o.id)
                                                   FROM options o WHERE o.group_id = g.id), '[]'::jsonb)) ORDER BY g.id)
                                      FROM option_groups g WHERE g.item_id = i.id), '[]'::jsonb)) ORDER BY i.position)
                            FROM menu_items i WHERE i.section_id = s.id), '[]'::jsonb)) ORDER BY s.position)
                 FROM menu_sections s WHERE s.restaurant_id = r.id), '[]'::jsonb))
              FROM restaurants r WHERE r.id = :restaurantId
            ON CONFLICT (restaurant_id) DO UPDATE
               SET card = EXCLUDED.card, version = menu_cards.version + 1, built_at = now()
            RETURNING version""";

    private final JdbcClient jdbc;

    public MenuCards(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Rebuilds the card; call inside the transaction that changed the menu. Returns the card's version. */
    public int rebuild(long restaurantId) {
        return jdbc.sql(REBUILD).param("restaurantId", restaurantId).query(Integer.class).single();
    }

    public Optional<Card> find(long restaurantId) {
        return jdbc.sql("SELECT restaurant_id, version, card::text FROM menu_cards WHERE restaurant_id = :id")
                .param("id", restaurantId)
                .query((rs, n) -> new Card(rs.getLong(1), rs.getInt(2), rs.getString(3)))
                .optional();
    }
}
