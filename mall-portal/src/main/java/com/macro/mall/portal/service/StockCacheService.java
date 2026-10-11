package com.macro.mall.portal.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 库存缓存服务
 * <p>
 * 核心能力：
 * <ul>
 *     <li>库存预热：将数据库可售库存(库存-锁定库存)惰性加载到 Redis；</li>
 *     <li>库存预扣：通过 Lua 脚本原子预扣，防止并发超卖；</li>
 *     <li>限购校验：同一用户对同一 SKU 的累计购买数限制；</li>
 *     <li>库存回补：订单取消/超时关闭时释放预扣库存并回退限购数。</li>
 * </ul>
 */
@Slf4j
@Service
public class StockCacheService {

    /** Lua 执行结果：成功 */
    public static final long RESULT_SUCCESS = 1L;
    /** Lua 执行结果：库存不足 */
    public static final long RESULT_NO_STOCK = -1L;
    /** Lua 执行结果：库存缓存未初始化 */
    public static final long RESULT_NOT_INIT = -2L;
    /** Lua 执行结果：超出限购数量 */
    public static final long RESULT_OVER_LIMIT = -3L;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    @Value("${redis.database}")
    private String redisDatabase;
    @Value("${redis.key.skuStock}")
    private String redisKeySkuStock;
    @Value("${redis.key.skuLimit}")
    private String redisKeySkuLimit;
    @Value("${redis.expire.skuStock}")
    private long skuStockExpire;
    @Value("${redis.expire.skuLimit}")
    private long skuLimitExpire;

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    @PostConstruct
    public void init() {
        DEDUCT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_deduct.lua")));
        DEDUCT_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_release.lua")));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    /**
     * SKU 可用库存缓存 key
     */
    private String skuStockKey(Long skuId) {
        return redisDatabase + ":" + redisKeySkuStock + ":" + skuId;
    }

    /**
     * 用户对 SKU 的限购累计 key
     */
    private String skuLimitKey(Long memberId, Long skuId) {
        return redisDatabase + ":" + redisKeySkuLimit + ":" + memberId + ":" + skuId;
    }

    /**
     * 库存预扣 + 限购校验（Lua 原子执行，防超卖）
     * <p>
     * 批量预扣：任一 SKU 预扣失败时，回滚已预扣成功的 SKU，保证全部或全部不扣。
     * 默认不限购，供普通 / 立即购买下单路径使用（普通购买不做限购）。
     *
     * @param memberId       当前用户
     * @param skuQuantityMap SKU ID -> 购买数量
     */
    public void deductStock(Long memberId, Map<Long, Integer> skuQuantityMap) {
        deductStock(memberId, skuQuantityMap, 0);
    }

    /**
     * 库存预扣 + 限购校验（Lua 原子执行，防超卖）
     * <p>
     * 批量预扣：任一 SKU 预扣失败时，回滚已预扣成功的 SKU，保证全部或全部不扣。
     *
     * @param memberId       当前用户
     * @param skuQuantityMap SKU ID -> 购买数量
     * @param limitPerUser   每人限购数量（0 表示不限购，供秒杀等限定场景传入固定限购数）
     */
    public void deductStock(Long memberId, Map<Long, Integer> skuQuantityMap, int limitPerUser) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        List<Long> deductedSkuIds = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
                Long skuId = entry.getKey();
                int quantity = entry.getValue();
                long result = doDeduct(memberId, skuId, quantity, limitPerUser);
                if (result == RESULT_NO_STOCK) {
                    Asserts.fail("库存不足，无法下单");
                } else if (result == RESULT_OVER_LIMIT) {
                    Asserts.fail("该商品每人限购" + limitPerUser + "件");
                } else if (result != RESULT_SUCCESS) {
                    Asserts.fail("库存服务异常，请稍后再试");
                }
                deductedSkuIds.add(skuId);
            }
        } catch (Exception e) {
            // 回滚已预扣成功的库存，避免脏扣
            for (Long skuId : deductedSkuIds) {
                releaseStock(memberId, skuId, skuQuantityMap.get(skuId));
            }
            throw e;
        }
    }

    /**
     * 释放预扣库存 + 回退限购数（订单取消/超时关闭时调用）
     */
    public void releaseStock(Long memberId, Map<Long, Integer> skuQuantityMap) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
            releaseStock(memberId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 释放单个 SKU 的预扣库存 + 回退限购数
     */
    public void releaseStock(Long memberId, Long skuId, int quantity) {
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT,
                    List.of(skuStockKey(skuId), skuLimitKey(memberId, skuId)),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.error("释放SKU库存失败, memberId: {}, skuId: {}, quantity: {}", memberId, skuId, quantity, e);
        }
    }

    /**
     * 执行库存预扣：库存缓存未初始化时先加载数据库库存再重试一次
     */
    private long doDeduct(Long memberId, Long skuId, int quantity, int limitPerUser) {
        long result = executeDeduct(memberId, skuId, quantity, limitPerUser);
        if (result == RESULT_NOT_INIT) {
            // 惰性初始化后重试
            initSkuStock(skuId);
            result = executeDeduct(memberId, skuId, quantity, limitPerUser);
        }
        return result;
    }


    private long executeDeduct(Long memberId, Long skuId, int quantity, int limitPerUser) {
        return stringRedisTemplate.execute(DEDUCT_SCRIPT,
                List.of(skuStockKey(skuId), skuLimitKey(memberId, skuId)),
                String.valueOf(quantity),
                String.valueOf(limitPerUser),
                String.valueOf(skuLimitExpire));
    }

    /**
     * 惰性初始化 SKU 可用库存缓存（数据库库存 - 锁定库存）
     * <p>
     * 使用 setIfAbsent 保证并发初始化场景下不会被旧值覆盖。
     */
    private void initSkuStock(Long skuId) {
        String key = skuStockKey(skuId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        PmsSkuStock skuStock = skuStockMapper.selectById(skuId);
        if (skuStock == null) {
            log.warn("SKU不存在, 无法初始化库存, skuId: {}", skuId);
            return;
        }
        int lockStock = skuStock.getLockStock() == null ? 0 : skuStock.getLockStock();
        int availableStock = Math.max(skuStock.getStock() - lockStock, 0);
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(availableStock), skuStockExpire, TimeUnit.SECONDS);
    }
}
