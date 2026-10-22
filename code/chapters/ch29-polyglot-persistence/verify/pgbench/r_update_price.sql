\set i random(1, 192000)
UPDATE menu_items SET price_paise = price_paise + 100 WHERE id = :i;
