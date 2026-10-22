-- GCRA (token bucket) in Redis: one key per client holding the theoretical arrival time in microseconds.
-- Atomic because Redis runs a script without interleaving other commands; uses the server's clock, so gateway
-- instances with skewed clocks still agree.
-- KEYS[1] = rate-limit key, ARGV[1] = interval between requests (µs), ARGV[2] = burst tolerance (µs)
-- returns { allowed (1/0), retry after (µs), reset in (µs) }
local interval = tonumber(ARGV[1])
local tolerance = tonumber(ARGV[2])
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000000 + tonumber(t[2])
local tat = tonumber(redis.call('GET', KEYS[1]))
if tat == nil or tat < now then
  tat = now
end
if now < tat - tolerance then
  return { 0, tat - tolerance - now, tat - now }
end
local new_tat = tat + interval
redis.call('SET', KEYS[1], new_tat, 'PX', math.ceil((new_tat - now) / 1000) + 1)
return { 1, 0, new_tat - now }
