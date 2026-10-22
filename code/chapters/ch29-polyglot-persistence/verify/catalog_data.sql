-- FoodFleet catalog, twice: normalized tables (R) and one JSONB document per restaurant (D),
-- built from the same rows so both hold identical content.
-- 2,000 restaurants x 8 sections x 12 items = 192,000 items; 1-2 option groups of 3 options per item.
SELECT setseed(0.29);
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP TABLE IF EXISTS options, option_groups, menu_items, menu_sections, restaurants, restaurant_menus CASCADE;

CREATE TABLE restaurants (id int PRIMARY KEY, name text NOT NULL, city text NOT NULL);
CREATE TABLE menu_sections (id int PRIMARY KEY, restaurant_id int NOT NULL REFERENCES restaurants,
                            name text NOT NULL, position int NOT NULL);
CREATE TABLE menu_items (id int PRIMARY KEY, section_id int NOT NULL REFERENCES menu_sections,
                         restaurant_id int NOT NULL REFERENCES restaurants,
                         name text NOT NULL, description text NOT NULL, base_dish text NOT NULL,
                         price_paise int NOT NULL, is_veg boolean NOT NULL, available boolean NOT NULL,
                         position int NOT NULL);
CREATE TABLE option_groups (id int PRIMARY KEY, item_id int NOT NULL REFERENCES menu_items,
                            name text NOT NULL, min_choices int NOT NULL, max_choices int NOT NULL);
CREATE TABLE options (id int PRIMARY KEY, group_id int NOT NULL REFERENCES option_groups,
                      name text NOT NULL, price_delta_paise int NOT NULL);

CREATE TEMP TABLE dishes (n int, dish text, veg boolean, words text);
INSERT INTO dishes SELECT row_number() OVER () - 1, d, v, w FROM (VALUES
 ('Paneer Butter Masala', true, 'cottage cheese tomato butter cream'), ('Paneer Tikka', true, 'cottage cheese yoghurt tandoor'),
 ('Palak Paneer', true, 'spinach cottage cheese garlic'), ('Kadai Paneer', true, 'cottage cheese capsicum kadai spice'),
 ('Shahi Paneer', true, 'cottage cheese cashew cream'), ('Dal Makhani', true, 'black lentil butter cream slow cooked'),
 ('Dal Tadka', true, 'yellow lentil cumin ghee tempering'), ('Chole Bhature', true, 'chickpea curry fried bread'),
 ('Rajma Chawal', true, 'kidney bean curry steamed rice'), ('Aloo Gobi', true, 'potato cauliflower turmeric'),
 ('Masala Dosa', true, 'rice crepe potato filling chutney sambar'), ('Plain Dosa', true, 'rice crepe chutney sambar'),
 ('Rava Dosa', true, 'semolina crepe onion chilli'), ('Idli Sambar', true, 'steamed rice cake lentil stew'),
 ('Medu Vada', true, 'lentil doughnut coconut chutney'), ('Uttapam', true, 'thick rice pancake onion tomato'),
 ('Pav Bhaji', true, 'mashed vegetable curry buttered bun'), ('Vada Pav', true, 'potato fritter bun garlic chutney'),
 ('Veg Biryani', true, 'basmati rice vegetables saffron'), ('Veg Pulao', true, 'basmati rice peas whole spice'),
 ('Veg Fried Rice', true, 'wok rice vegetables soy'), ('Veg Hakka Noodles', true, 'wok noodles cabbage carrot'),
 ('Gobi Manchurian', true, 'cauliflower fritter soy garlic sauce'), ('Veg Momos', true, 'steamed dumpling cabbage'),
 ('Samosa', true, 'pastry potato peas'), ('Pani Puri', true, 'crisp shell tamarind water'),
 ('Margherita Pizza', true, 'tomato mozzarella basil'), ('Veg Burger', true, 'vegetable patty bun lettuce'),
 ('Masala Chai', true, 'tea milk ginger cardamom'), ('Filter Coffee', true, 'south indian coffee milk'),
 ('Mango Lassi', true, 'yoghurt mango drink'), ('Gulab Jamun', true, 'milk dumpling sugar syrup'),
 ('Rasmalai', true, 'cottage cheese dumpling sweet milk'), ('Kulfi', true, 'frozen milk pistachio'),
 ('Butter Naan', true, 'tandoor bread butter'), ('Garlic Naan', true, 'tandoor bread garlic'),
 ('Tandoori Roti', true, 'whole wheat tandoor bread'), ('Jeera Rice', true, 'basmati rice cumin'),
 ('Chicken Biryani', false, 'basmati rice chicken saffron fried onion'), ('Mutton Biryani', false, 'basmati rice goat meat saffron'),
 ('Egg Biryani', false, 'basmati rice boiled egg masala'), ('Butter Chicken', false, 'chicken tomato butter cream'),
 ('Chicken Tikka Masala', false, 'grilled chicken spiced tomato gravy'), ('Chicken Tikka', false, 'chicken yoghurt tandoor'),
 ('Tandoori Chicken', false, 'whole leg chicken tandoor'), ('Chicken 65', false, 'fried chicken curry leaf chilli'),
 ('Chilli Chicken', false, 'chicken soy chilli capsicum'), ('Chicken Fried Rice', false, 'wok rice chicken egg soy'),
 ('Chicken Hakka Noodles', false, 'wok noodles chicken'), ('Chicken Momos', false, 'steamed dumpling chicken'),
 ('Chicken Shawarma', false, 'roasted chicken wrap garlic sauce'), ('Chicken Burger', false, 'chicken patty bun'),
 ('Mutton Rogan Josh', false, 'goat meat kashmiri chilli'), ('Mutton Keema', false, 'minced goat meat peas'),
 ('Fish Curry', false, 'fish coconut tamarind'), ('Fish Fry', false, 'fried fish masala crust'),
 ('Prawn Masala', false, 'prawn onion tomato masala'), ('Egg Curry', false, 'boiled egg onion gravy'),
 ('Chicken Kebab', false, 'skewered minced chicken'), ('Pepperoni Pizza', false, 'tomato mozzarella pepperoni')
) AS t(d, v, w);

CREATE TEMP TABLE prefixes (n int, p text);
INSERT INTO prefixes SELECT row_number() OVER () - 1, p FROM unnest(ARRAY['', '', '', '', 'Special ', 'Classic ',
  'Hyderabadi ', 'Home-style ', 'Spicy ', 'Jumbo ', 'Mini ', 'Chef''s ', 'Lucknowi ', 'Kolkata ']) AS p;
CREATE TEMP TABLE suffixes (n int, s text);
INSERT INTO suffixes SELECT row_number() OVER () - 1, s FROM unnest(ARRAY['', '', '', '', '', ' (Half)', ' (Full)',
  ' Combo', ' Meal', ' Thali', ' Platter']) AS s;

INSERT INTO restaurants SELECT r, 'Restaurant ' || r,
  (ARRAY['Bengaluru','Mumbai','Delhi','Hyderabad','Chennai','Pune','Kolkata'])[1 + r % 7]
  FROM generate_series(1, 2000) r;
INSERT INTO menu_sections SELECT (r - 1) * 8 + s, r,
  (ARRAY['Recommended','Starters','Mains','Breads','Rice','Chinese','Desserts','Beverages'])[s], s
  FROM generate_series(1, 2000) r, generate_series(1, 8) s;

INSERT INTO menu_items
SELECT i.id, i.section_id, i.restaurant_id,
       p.p || d.dish || s.s, 'Freshly made ' || lower(d.dish) || ' with ' || d.words, d.dish,
       (49 + floor(random() * 450))::int * 100, d.veg, random() > 0.05, i.pos
  FROM (SELECT (sec.id - 1) * 12 + k AS id, sec.id AS section_id, sec.restaurant_id, k AS pos,
               floor(random() * 60)::int AS dn, floor(random() * 14)::int AS pn, floor(random() * 11)::int AS sn
          FROM menu_sections sec, generate_series(1, 12) k) i
  JOIN dishes d ON d.n = i.dn JOIN prefixes p ON p.n = i.pn JOIN suffixes s ON s.n = i.sn;

INSERT INTO option_groups
SELECT (it.id - 1) * 2 + g, it.id, (ARRAY['Portion','Add-ons'])[g], CASE g WHEN 1 THEN 1 ELSE 0 END, CASE g WHEN 1 THEN 1 ELSE 3 END
  FROM menu_items it, generate_series(1, 2) g WHERE g = 1 OR it.id % 2 = 0;
INSERT INTO options
SELECT (og.id - 1) * 3 + o, og.id,
       CASE og.name WHEN 'Portion' THEN (ARRAY['Regular','Medium','Large'])[o] ELSE (ARRAY['Extra cheese','Extra gravy','Raita'])[o] END,
       (o - 1) * 3000
  FROM option_groups og, generate_series(1, 3) o;

CREATE INDEX ON menu_sections (restaurant_id);
CREATE INDEX ON menu_items (section_id);
CREATE INDEX ON menu_items (restaurant_id);
CREATE INDEX ON option_groups (item_id);
CREATE INDEX ON options (group_id);

-- The same menu as one document per restaurant.
CREATE TABLE restaurant_menus (restaurant_id int PRIMARY KEY, version int NOT NULL DEFAULT 1, menu jsonb NOT NULL);
INSERT INTO restaurant_menus (restaurant_id, menu)
SELECT r.id, jsonb_build_object('restaurantId', r.id, 'name', r.name, 'city', r.city, 'sections',
  (SELECT jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name, 'items',
     (SELECT jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name, 'description', i.description,
              'price', i.price_paise, 'veg', i.is_veg, 'available', i.available, 'optionGroups',
              (SELECT jsonb_agg(jsonb_build_object('name', g.name, 'min', g.min_choices, 'max', g.max_choices, 'options',
                  (SELECT jsonb_agg(jsonb_build_object('name', o.name, 'priceDelta', o.price_delta_paise) ORDER BY o.id)
                     FROM options o WHERE o.group_id = g.id)) ORDER BY g.id)
                 FROM option_groups g WHERE g.item_id = i.id)) ORDER BY i.position)
        FROM menu_items i WHERE i.section_id = s.id)) ORDER BY s.position)
     FROM menu_sections s WHERE s.restaurant_id = r.id))
  FROM restaurants r;

VACUUM ANALYZE;
