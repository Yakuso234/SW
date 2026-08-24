local campaignKey = KEYS[1]
local buyerKey = KEYS[2]
local buyerId = ARGV[1]

if redis.call('SISMEMBER', buyerKey, buyerId) == 0 then return 0 end
redis.call('SREM', buyerKey, buyerId)
if redis.call('EXISTS', campaignKey) == 1 then
    redis.call('HINCRBY', campaignKey, 'stock', 1)
end
return 1

