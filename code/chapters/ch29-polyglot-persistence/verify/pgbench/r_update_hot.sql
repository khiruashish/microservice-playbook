\set i random(1, 96)
UPDATE menu_items SET price_paise = price_paise + 100 WHERE id = :i;
