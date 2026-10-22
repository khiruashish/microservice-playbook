\set i random(1, 192000)
SELECT name, price_paise, available FROM menu_items WHERE id = :i;
