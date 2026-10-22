\set dlat random(-400, 400)
\set dlon random(-400, 400)
SELECT rider_id FROM rider_positions
 WHERE earth_box(ll_to_earth(12.97 + :dlat / 4000.0, 77.59 + :dlon / 4000.0), 3000) @> ll_to_earth(lat, lon)
 ORDER BY ll_to_earth(lat, lon) <-> ll_to_earth(12.97 + :dlat / 4000.0, 77.59 + :dlon / 4000.0)
 LIMIT 10;
