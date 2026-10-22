-- Search over menu_items (catalog_data.sql), entirely in PostgreSQL.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
ALTER TABLE menu_items DROP COLUMN IF EXISTS search;
ALTER TABLE menu_items ADD COLUMN search tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('english', name), 'A') || setweight(to_tsvector('english', description), 'B')) STORED;
CREATE INDEX IF NOT EXISTS menu_items_search ON menu_items USING gin (search);
CREATE INDEX IF NOT EXISTS menu_items_name_trgm_gin ON menu_items USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS menu_items_name_trgm_gist ON menu_items USING gist (lower(name) gist_trgm_ops);

-- "search as you type": every word must match, the last one as a prefix ("chicken bir" -> chicken & bir:*)
CREATE OR REPLACE FUNCTION typeahead_query(q text) RETURNS tsquery LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN w IS NULL OR cardinality(w) = 0 THEN NULL
         ELSE to_tsquery('english', array_to_string(w[1:cardinality(w) - 1] ||
                         (w[cardinality(w)] || ':*'), ' & ')) END
    FROM (SELECT regexp_split_to_array(trim(regexp_replace(lower(q), '[^a-z0-9 ]', ' ', 'g')), '\s+') AS w) t
$$;
ANALYZE menu_items;
