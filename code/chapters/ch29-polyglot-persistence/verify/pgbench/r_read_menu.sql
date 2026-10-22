\set r random(1, 2000)
SELECT jsonb_build_object('restaurantId', r.id, 'name', r.name, 'city', r.city, 'sections',
  (SELECT jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name, 'items',
     (SELECT jsonb_agg(jsonb_build_object('id', i.id, 'name', i.name, 'description', i.description,
              'price', i.price_paise, 'veg', i.is_veg, 'available', i.available, 'optionGroups',
              (SELECT jsonb_agg(jsonb_build_object('name', g.name, 'min', g.min_choices, 'max', g.max_choices, 'options',
                  (SELECT jsonb_agg(jsonb_build_object('name', o.name, 'priceDelta', o.price_delta_paise) ORDER BY o.id)
                     FROM options o WHERE o.group_id = g.id)) ORDER BY g.id)
                 FROM option_groups g WHERE g.item_id = i.id)) ORDER BY i.position)
        FROM menu_items i WHERE i.section_id = s.id)) ORDER BY s.position)
     FROM menu_sections s WHERE s.restaurant_id = r.id))
  FROM restaurants r WHERE r.id = :r;
