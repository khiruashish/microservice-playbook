-- menu-service owns these tables; no other service reads them (ADR-012)
CREATE TABLE restaurant (
    id      text PRIMARY KEY,
    name    text NOT NULL
);

CREATE TABLE menu_item (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id text NOT NULL REFERENCES restaurant (id),
    name         text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    price_paise  bigint NOT NULL CHECK (price_paise > 0),
    vegetarian   boolean NOT NULL,
    version      bigint NOT NULL DEFAULT 0
);

-- the menu query reads by restaurant; the id makes the order stable (keyset-ready, chapter 21)
CREATE INDEX menu_item_restaurant ON menu_item (restaurant_id, id);

INSERT INTO restaurant (id, name) VALUES ('r-1', 'Biryani House');
INSERT INTO menu_item (restaurant_id, name, price_paise, vegetarian) VALUES
    ('r-1', 'Paneer tikka', 24900, true),
    ('r-1', 'Chicken biryani', 32900, false),
    ('r-1', 'Masala dosa', 14900, true);
