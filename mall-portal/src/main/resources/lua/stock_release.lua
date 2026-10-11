--[[
 * 释放预扣库存 + 回退限购累计数（订单取消 / 超时关闭时调用）
 *
 * KEYS[1] : SKU 可用库存缓存 key
 * KEYS[2] : 用户对 SKU 的限购累计 key
 * ARGV[1] : 回补数量
 *
 * 返回值：1 -> 回补成功
--]]

local skuStockKey = KEYS[1]
local skuLimitKey = KEYS[2]
local buyQuantity = tonumber(ARGV[1])

-- 1. 回补预扣库存（key 不存在说明缓存已过期，无需处理）
if redis.call('exists', skuStockKey) == 1 then
    redis.call('incrby', skuStockKey, buyQuantity)
end

-- 2. 回退限购累计数
if redis.call('exists', skuLimitKey) == 1 then
    local boughtCount = tonumber(redis.call('get', skuLimitKey) or '0')
    local remainCount = boughtCount - buyQuantity
    if remainCount <= 0 then
        redis.call('del', skuLimitKey)
    else
        redis.call('set', skuLimitKey, remainCount)
    end
end

return 1
