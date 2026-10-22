\set r random(1, 2000)
SELECT menu FROM restaurant_menus WHERE restaurant_id = :r;
