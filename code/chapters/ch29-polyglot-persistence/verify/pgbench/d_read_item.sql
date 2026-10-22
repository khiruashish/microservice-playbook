\set r random(1, 2000)
\set s random(0, 7)
\set k random(0, 11)
SELECT menu #> ARRAY['sections', :s::text, 'items', :k::text] FROM restaurant_menus WHERE restaurant_id = :r;
