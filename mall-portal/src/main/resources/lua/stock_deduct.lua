--[[
 * 普通订单库存预扣（Lua 原子执行，防止超卖）
 *
 * 普通订单不进行每人限购校验，也不维护限购累计 key（限购仅秒杀场景，
 * 见 stock_flash_deduct.lua）。
 *
 * KEYS[1] : SKU 可用库存缓存 key（值为可售库存 = 数据库库存 - 锁定库存）
 * ARGV[1] : 本次购买数量
 *
 * 返回值：
 *   1  -> 预扣成功
 *  -1  -> 库存不足
 *  -2  -> 库存缓存未初始化（需先加载数据库库存后重试）
--]]

local skuStockKey = KEYS[1]
local buyQuantity = tonumber(ARGV[1])

-- 库存校验：可用库存是否充足
local availableStock = tonumber(redis.call('get', skuStockKey))
if not availableStock then
    return -2
end
if availableStock < buyQuantity then
    return -1
end

-- 库存预扣（原子扣减，防止并发超卖）
redis.call('decrby', skuStockKey, buyQuantity)

return 1