-- Catalog: PostgreSQL is the source of truth. Tables for writing and querying across restaurants,
-- one JSONB menu card per restaurant for reading a whole menu, full-text + trigram indexes for search,
-- and an outbox for the copies other stores keep.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE restaurants (
    id   bigint PRIMARY KEY,
    name text   NOT NULL,
    city text   NOT NULL
);

CREATE TABLE menu_sections (
    id            bigint PRIMARY KEY,
    restaurant_id bigint NOT NULL REFERENCES restaurants,
    name          text   NOT NULL,
    position      int    NOT NULL
);
CREATE INDEX menu_sections_restaurant ON menu_sections (restaurant_id);

CREATE TABLE menu_items (
    id            bigint  PRIMARY KEY,
    section_id    bigint  NOT NULL REFERENCES menu_sections,
    restaurant_id bigint  NOT NULL REFERENCES restaurants,
    name          text    NOT NULL,
    description   text    NOT NULL DEFAULT '',
    price_paise   int     NOT NULL CHECK (price_paise > 0),
    is_veg        boolean NOT NULL,
    available     boolean NOT NULL DEFAULT true,
    position      int     NOT NULL,
    version       int     NOT NULL DEFAULT 1,
    search        tsvector GENERATED ALWAYS AS (
                      setweight(to_tsvector('english', name), 'A') ||
                      setweight(to_tsvector('english', description), 'B')) STORED
);
CREATE INDEX menu_items_section    ON menu_items (section_id);
CREATE INDEX menu_items_restaurant ON menu_items (restaurant_id);
CREATE INDEX menu_items_search     ON menu_items USING gin (search);                       -- words, stems, prefixes
CREATE INDEX menu_items_name_trgm  ON menu_items USING gist (lower(name) gist_trgm_ops);    -- typos, nearest by similarity
CREATE INDEX menu_items_veg_price  ON menu_items (price_paise) WHERE is_veg AND available;  -- cross-restaurant browsing

CREATE TABLE option_groups (
    id          bigint PRIMARY KEY,
    item_id     bigint NOT NULL REFERENCES menu_items,
    name        text   NOT NULL,
    min_choices int    NOT NULL,
    max_choices int    NOT NULL
);
CREATE INDEX option_groups_item ON option_groups (item_id);

CREATE TABLE options (
    id                bigint PRIMARY KEY,
    group_id          bigint NOT NULL REFERENCES option_groups,
    name              text   NOT NULL,
    price_delta_paise int    NOT NULL DEFAULT 0
);
CREATE INDEX options_group ON options (group_id);

-- The read model: the whole menu as the app shows it, rebuilt in the same transaction as every change.
CREATE TABLE menu_cards (
    restaurant_id bigint      PRIMARY KEY REFERENCES restaurants,
    version       int         NOT NULL,
    card          jsonb       NOT NULL,
    built_at      timestamptz NOT NULL DEFAULT now()
);

-- Changes for other stores (the Redis price cache), written in the same transaction as the change.
CREATE TABLE catalog_outbox (
    id            bigserial   PRIMARY KEY,
    item_id       bigint      NOT NULL,
    restaurant_id bigint      NOT NULL,
    price_paise   int         NOT NULL,
    available     boolean     NOT NULL,
    version       int         NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Rider position history: append-only, queried by time, kept 7 days. One partition per day (created and
-- dropped by PartitionMaintenance), a BRIN index on time instead of a B-tree.
CREATE TABLE rider_pings (
    rider_id    bigint           NOT NULL,
    recorded_at timestamptz      NOT NULL,
    lat         double precision NOT NULL,
    lon         double precision NOT NULL,
    speed_kmh   real
) PARTITION BY RANGE (recorded_at);
CREATE INDEX rider_pings_time  ON rider_pings USING brin (recorded_at);
CREATE INDEX rider_pings_rider ON rider_pings (rider_id, recorded_at);
