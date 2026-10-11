--[[
 * Redis 库存预扣 + 限购校验（Lua 原子执行，防止超卖）
 *
 * KEYS[1] : SKU 可用库存缓存 key（值为可售库存 = 数据库库存 - 锁定库存）
 * KEYS[2] : 用户对 SKU 的限购累计 key
 * ARGV[1] : 本次购买数量
 * ARGV[2] : 限购数量（0 表示不限购）
 * ARGV[3] : 限购累计 key 的有效期（秒）
 *
 * 返回值：
 *   1  -> 预扣成功
 *  -1  -> 库存不足
 *  -2  -> 库存缓存未初始化（需先加载数据库库存后重试）
 *  -3  -> 超出限购数量
--]]

local skuStockKey = KEYS[1]
local skuLimitKey = KEYS[2]
local buyQuantity = tonumber(ARGV[1])
local limitCount = tonumber(ARGV[2])

-- 1. 限购校验：已购数量 + 本次购买数量 不得超过限购值
if limitCount and limitCount > 0 then
    local boughtCount = tonumber(redis.call('get', skuLimitKey) or '0')
    if boughtCount + buyQuantity > limitCount then
        return -3
    end
end

-- 2. 库存校验：可用库存是否充足
local availableStock = tonumber(redis.call('get', skuStockKey))
if not availableStock then
    return -2
end
if availableStock < buyQuantity then
    return -1
end

-- 3. 库存预扣（原子扣减，防止并发超卖）
redis.call('decrby', skuStockKey, buyQuantity)

-- 4. 累计限购购买数
if limitCount and limitCount > 0 then
    redis.call('incrby', skuLimitKey, buyQuantity)
    redis.call('expire', skuLimitKey, tonumber(ARGV[3]))
end

return 1
