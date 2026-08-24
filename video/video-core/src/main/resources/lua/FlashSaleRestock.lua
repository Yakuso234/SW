local campaignKey = KEYS[1]

if redis.call('EXISTS', campaignKey) == 0 then return 0 end
redis.call('HINCRBY', campaignKey, 'stock', 1)
return 1
