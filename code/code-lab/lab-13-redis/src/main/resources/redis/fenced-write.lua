-- The protected resource's side of a fencing token (here stored in Redis to keep the lab small; in FoodFleet it's a
-- version column in PostgreSQL). KEYS[1] the value; KEYS[2] the highest token seen; ARGV[1] our token; ARGV[2] value.
-- A writer whose lock expired holds an older token than the current holder: its write is refused.
local seen = tonumber(redis.call('GET', KEYS[2]) or '0')
if tonumber(ARGV[1]) < seen then
  return 0
end
redis.call('SET', KEYS[2], ARGV[1])
redis.call('SET', KEYS[1], ARGV[2])
return 1
