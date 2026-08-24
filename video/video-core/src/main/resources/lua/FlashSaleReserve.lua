local campaignKey = KEYS[1]
local buyerKey = KEYS[2]
local now = tonumber(ARGV[1])
local buyerId = ARGV[2]
local ttlSeconds = tonumber(ARGV[3])

if redis.call('EXISTS', campaignKey) == 0 then return -10 end
if redis.call('HGET', campaignKey, 'status') ~= 'ACTIVE' then return -4 end

local startsAt = tonumber(redis.call('HGET', campaignKey, 'startsAt') or '0')
local endsAt = tonumber(redis.call('HGET', campaignKey, 'endsAt') or '0')
if now < startsAt then return -5 end
if now >= endsAt then return -6 end
if redis.call('SISMEMBER', buyerKey, buyerId) == 1 then return -2 end

local stock = tonumber(redis.call('HGET', campaignKey, 'stock') or '0')
if stock <= 0 then return -1 end

redis.call('HINCRBY', campaignKey, 'stock', -1)
redis.call('SADD', buyerKey, buyerId)
redis.call('EXPIRE', buyerKey, ttlSeconds)
return 1

