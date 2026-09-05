local campaignKey = KEYS[1]
local markerKey = KEYS[2]
local markerTtlSeconds = tonumber(ARGV[1])

-- 先抢订单级幂等标记。进程在 Redis 成功后、MySQL 标记成功前崩溃时，重试不会重复回补。
if not redis.call('SET', markerKey, '1', 'NX', 'EX', markerTtlSeconds) then return 0 end

-- 活动缓存过期后无需再写缓存；下一次冷启动会以 MySQL 订单事实重建正确库存。
if redis.call('EXISTS', campaignKey) == 1 then
    redis.call('HINCRBY', campaignKey, 'stock', 1)
end
return 1
