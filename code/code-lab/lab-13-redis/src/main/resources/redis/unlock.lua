-- Release a lock only if we still hold it. KEYS[1] the lock; ARGV[1] the token we set when acquiring.
-- A plain DEL would also delete a lock that expired while we were paused and someone else then took.
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
