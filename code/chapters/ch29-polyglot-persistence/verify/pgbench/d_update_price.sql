\set r random(1, 2000)
\set s random(0, 7)
\set k random(0, 11)
UPDATE restaurant_menus
   SET menu = jsonb_set(menu, ARRAY['sections', :s::text, 'items', :k::text, 'price'],
                        to_jsonb((menu #>> ARRAY['sections', :s::text, 'items', :k::text, 'price'])::int + 100)),
       version = version + 1
 WHERE restaurant_id = :r;
