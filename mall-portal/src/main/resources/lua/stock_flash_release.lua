--[[
 * 释放秒杀预扣库存 + 回退秒杀限购累计数（秒杀订单取消 / 超时关闭时调用）
 *
 * 与普通订单释放区分：仅操作秒杀库存 key 与秒杀限购累计 key，互不影响。
 *
 * KEYS[1] : 秒杀 SKU 可用库存缓存 key
 * KEYS[2] : 用户对 SKU 的秒杀限购累计 key
 * ARGV[1] : 回补数量
 *
 * 返回值：1 -> 回补成功
--]]

local skuFlashStockKey = KEYS[1]
local skuFlashLimitKey = KEYS[2]
local buyQuantity = tonumber(ARGV[1])

-- 1. 回补秒杀预扣库存（key 不存在说明缓存已过期，无需处理）
if redis.call('exists', skuFlashStockKey) == 1 then
    redis.call('incrby', skuFlashStockKey, buyQuantity)
end

-- 2. 回退秒杀限购累计数
if redis.call('exists', skuFlashLimitKey) == 1 then
    local boughtCount = tonumber(redis.call('get', skuFlashLimitKey) or '0')
    local remainCount = boughtCount - buyQuantity
    if remainCount <= 0 then
        redis.call('del', skuFlashLimitKey)
    else
        redis.call('set', skuFlashLimitKey, remainCount)
    end
end

return 1