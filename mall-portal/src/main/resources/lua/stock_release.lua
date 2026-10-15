--[[
 * 释放普通订单预扣库存（订单取消 / 超时关闭时调用）
 *
 * 普通订单不维护限购累计 key，仅回补库存（限购回退仅秒杀场景，
 * 见 stock_flash_release.lua）。
 *
 * KEYS[1] : SKU 可用库存缓存 key
 * ARGV[1] : 回补数量
 *
 * 返回值：1 -> 回补成功
--]]

local skuStockKey = KEYS[1]
local buyQuantity = tonumber(ARGV[1])

-- 回补预扣库存（key 不存在说明缓存已过期，无需处理）
if redis.call('exists', skuStockKey) == 1 then
    redis.call('incrby', skuStockKey, buyQuantity)
end

return 1