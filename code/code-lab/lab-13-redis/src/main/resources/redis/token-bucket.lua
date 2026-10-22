-- A token bucket per key, refilled continuously. Atomic: Redis runs the whole script before any other command.
-- KEYS[1] the bucket (one per tenant and endpoint)
-- ARGV[1] capacity: the burst allowed; ARGV[2] refill, tokens per second; ARGV[3] cost of this request
-- Returns {allowed (1/0), tokens left (rounded down), milliseconds until enough tokens (0 if allowed)}
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])
local t = redis.call('TIME')                         -- Redis's clock, not the callers': no skew between pods
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
local tokens = tonumber(state[1]) or capacity
local at = tonumber(state[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - at) * rate / 1000)
local allowed, retry = 0, 0
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  retry = math.ceil((cost - tokens) * 1000 / rate)
end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'at', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) + 1000)   -- an idle bucket is a full one: drop it
return {allowed, math.floor(tokens), retry}
