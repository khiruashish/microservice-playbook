CREATE TABLE restaurants (
    id           bigint PRIMARY KEY,
    name         text   NOT NULL,
    opening_time time   NOT NULL,
    closing_time time   NOT NULL
);

CREATE TABLE items (
    item_id     bigint PRIMARY KEY,
    price_paise int    NOT NULL CHECK (price_paise > 0),
    version     int    NOT NULL DEFAULT 1
);

CREATE TABLE reviews (
    restaurant_id bigint      NOT NULL,
    rating        smallint    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX reviews_restaurant ON reviews (restaurant_id);

-- write-behind targets
CREATE TABLE item_views (item_id bigint PRIMARY KEY, views bigint NOT NULL);
CREATE TABLE flushed_batches (batch_id text PRIMARY KEY, flushed_at timestamptz NOT NULL DEFAULT now());
