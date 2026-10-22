\set r random(1, 50000)
\set dlat random(-500, 500)
\set dlon random(-500, 500)
UPDATE rider_positions SET lat = 12.97 + :dlat / 4000.0, lon = 77.59 + :dlon / 4000.0, updated_at = now()
 WHERE rider_id = :r;
