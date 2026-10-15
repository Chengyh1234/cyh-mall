--[[
 * 秒杀库存预扣 + 限购校验（Lua 原子执行，防止超卖）
 *
 * 与普通订单库存预扣区分：秒杀操作独立的秒杀库存 key 与独立的秒杀限购累计 key，
 * 与普通订单(pms:skuStock / pms:skuLimit)完全隔离，互不影响。
 *
 * KEYS[1] : 秒杀 SKU 可用库存缓存 key（值为该场次秒杀剩余可售量）
 * KEYS[2] : 用户对该 SKU 的秒杀限购累计 key
 * ARGV[1] : 本次购买数量
 * ARGV[2] : 每人限购数量（0 表示不限购）
 * ARGV[3] : 秒杀限购累计 key 的有效期（秒）
 *
 * 返回值：
 *   1  -> 预扣成功
 *  -1  -> 秒杀库存不足
 *  -2  -> 秒杀库存缓存未初始化（需先预热后重试）
 *  -3  -> 超出每人限购数量
--]]

local skuFlashStockKey = KEYS[1]
local skuFlashLimitKey = KEYS[2]
local buyQuantity = tonumber(ARGV[1])
local limitCount = tonumber(ARGV[2])

-- 1. 秒杀限购校验：已购数量 + 本次购买数量 不得超过限购值
if limitCount and limitCount > 0 then
    local boughtCount = tonumber(redis.call('get', skuFlashLimitKey) or '0')
    if boughtCount + buyQuantity > limitCount then
        return -3
    end
end

-- 2. 秒杀库存校验：秒杀剩余可售量是否充足
local availableStock = tonumber(redis.call('get', skuFlashStockKey))
if not availableStock then
    return -2
end
if availableStock < buyQuantity then
    return -1
end

-- 3. 秒杀库存预扣（原子扣减，防止并发超卖）
redis.call('decrby', skuFlashStockKey, buyQuantity)

-- 4. 累计秒杀限购购买数
if limitCount and limitCount > 0 then
    redis.call('incrby', skuFlashLimitKey, buyQuantity)
    redis.call('expire', skuFlashLimitKey, tonumber(ARGV[3]))
end

return 1