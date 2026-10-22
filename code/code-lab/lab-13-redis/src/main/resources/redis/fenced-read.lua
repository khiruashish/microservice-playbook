-- The protected resource's side of a fencing token, on read. KEYS[1] the value; KEYS[2] the highest token seen;
-- ARGV[1] our token. Every access presents the token, reads included: once a newer holder has read, a paused older
-- holder can no longer write a value computed from what it read before.
local seen = tonumber(redis.call('GET', KEYS[2]) or '0')
if tonumber(ARGV[1]) < seen then
  return false
end
redis.call('SET', KEYS[2], ARGV[1])
return redis.call('GET', KEYS[1]) or '0'
