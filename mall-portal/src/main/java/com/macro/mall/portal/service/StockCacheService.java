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
 *     <li>库存回补：订单取消/超时关闭时释放预扣库存。</li>
 * </ul>
 * <p>
 * 普通订单与秒杀订单完全分离：普通订单不参与限购（无每人限购数、无限购累计 key），
 * 库存取自 pms_sku_stock；秒杀订单独立限购与独立库存，库存取自
 * sms_flash_promotion_product_relation.flash_promotion_count。
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
    /** 秒杀专用预扣脚本，与普通订单区分，独立操作秒杀库存 key 与秒杀限购 key */
    private static final DefaultRedisScript<Long> DEDUCT_FLASH_SCRIPT = new DefaultRedisScript<>();
    /** 秒杀专用释放脚本，与普通订单区分 */
    private static final DefaultRedisScript<Long> RELEASE_FLASH_SCRIPT = new DefaultRedisScript<>();

    @PostConstruct
    public void init() {
        DEDUCT_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_deduct.lua")));
        DEDUCT_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_release.lua")));
        RELEASE_SCRIPT.setResultType(Long.class);
        DEDUCT_FLASH_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_flash_deduct.lua")));
        DEDUCT_FLASH_SCRIPT.setResultType(Long.class);
        RELEASE_FLASH_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/stock_flash_release.lua")));
        RELEASE_FLASH_SCRIPT.setResultType(Long.class);
    }

    /**
     * SKU 可用库存缓存 key
     */
    private String skuStockKey(Long skuId) {
        return redisDatabase + ":" + redisKeySkuStock + ":" + skuId;
    }

    /**
     * 秒杀 SKU 库存缓存 key（与普通库存 key 隔离）
     */
    private String skuFlashStockKey(Long skuId) {
        return skuStockKey(skuId) + ":flash";
    }

    /**
     * 用户对 SKU 的秒杀限购累计 key（仅秒杀场景使用，与普通库存/限购完全隔离）
     */
    private String skuFlashLimitKey(Long memberId, Long skuId) {
        return redisDatabase + ":" + redisKeySkuLimit + ":flash:" + memberId + ":" + skuId;
    }

    /**
     * 普通订单库存预扣（Lua 原子执行，防超卖）
     * <p>
     * 批量预扣：任一 SKU 预扣失败时，回滚已预扣成功的 SKU，保证全部或全部不扣。
     * 普通订单不限购（无每人限购数、不限购累计 key），限购仅秒杀场景使用（走 deductFlashStock）。
     *
     * @param skuQuantityMap SKU ID -> 购买数量
     */
    public void deductStock(Map<Long, Integer> skuQuantityMap) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        List<Long> deductedSkuIds = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
                Long skuId = entry.getKey();
                int quantity = entry.getValue();
                long result = doDeduct(skuId, quantity);
                if (result == RESULT_NO_STOCK) {
                    Asserts.fail("库存不足，无法下单");
                } else if (result != RESULT_SUCCESS) {
                    Asserts.fail("库存服务异常，请稍后再试");
                }
                deductedSkuIds.add(skuId);
            }
        } catch (Exception e) {
            // 回滚已预扣成功的库存，避免脏扣
            for (Long skuId : deductedSkuIds) {
                releaseStock(skuId, skuQuantityMap.get(skuId));
            }
            throw e;
        }
    }

    /**
     * 释放预扣库存（订单取消/超时关闭时调用）
     */
    public void releaseStock(Map<Long, Integer> skuQuantityMap) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
            releaseStock(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 释放单个 SKU 的预扣库存
     */
    public void releaseStock(Long skuId, int quantity) {
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT,
                    List.of(skuStockKey(skuId)),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.error("释放SKU库存失败, skuId: {}, quantity: {}", skuId, quantity, e);
        }
    }

    /**
     * 执行库存预扣：库存缓存未初始化时先加载数据库库存再重试一次
     */
    private long doDeduct(Long skuId, int quantity) {
        long result = executeDeduct(skuId, quantity);
        if (result == RESULT_NOT_INIT) {
            // 惰性初始化后重试
            initSkuStock(skuId);
            result = executeDeduct(skuId, quantity);
        }
        return result;
    }

    private long executeDeduct(Long skuId, int quantity) {
        return stringRedisTemplate.execute(DEDUCT_SCRIPT,
                List.of(skuStockKey(skuId)),
                String.valueOf(quantity));
    }

    /**
     * 普通的惰性初始化 SKU 可用库存缓存（数据库库存 - 锁定库存）
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


    /**
     * 秒杀库存预扣 + 限购校验（Lua 原子执行）
     * <p>
     * 秒杀库存独立预热为 min(普通可用库存, 秒杀总限量 flashPromotionCount)，
     * 预扣/释放均操作秒杀库存 key，实现"限量卖完即止"，且不影响普通订单的库存语义。
     *
     * @param memberId       当前用户
     * @param skuQuantityMap SKU ID -> 购买数量
     * @param limitPerUser   每人限购数量（0 表示不限购）
     * @param flashCountMap  SKU ID -> 秒杀总限量（缺省或小于等于 0 时退化为普通可用库存）
     */
    public void deductFlashStock(Long memberId, Map<Long, Integer> skuQuantityMap, int limitPerUser, Map<Long, Integer> flashCountMap) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        List<Long> deductedSkuIds = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
                Long skuId = entry.getKey();
                int quantity = entry.getValue();
                int flashCount = flashCountMap != null && flashCountMap.containsKey(skuId) ? flashCountMap.get(skuId) : 0;
                long result = doFlashDeduct(memberId, skuId, quantity, limitPerUser, flashCount);
                if (result == RESULT_NO_STOCK) {
                    Asserts.fail("秒杀库存不足，无法下单");
                } else if (result == RESULT_OVER_LIMIT) {
                    Asserts.fail("该商品每人限购" + limitPerUser + "件");
                } else if (result != RESULT_SUCCESS) {
                    Asserts.fail("库存服务异常，请稍后再试");
                }
                deductedSkuIds.add(skuId);
            }
        } catch (Exception e) {
            // 回滚已预扣成功的秒杀库存，避免脏扣
            for (Long skuId : deductedSkuIds) {
                releaseFlashStock(memberId, skuId, skuQuantityMap.get(skuId));
            }
            throw e;
        }
    }

    /**
     * 释放秒杀预扣库存 + 回退限购数（秒杀订单取消/超时关闭/事务回滚时调用）
     */
    public void releaseFlashStock(Long memberId, Map<Long, Integer> skuQuantityMap) {
        if (CollectionUtils.isEmpty(skuQuantityMap)) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : skuQuantityMap.entrySet()) {
            releaseFlashStock(memberId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 释放单个 SKU 的秒杀预扣库存 + 回退秒杀限购数
     */
    public void releaseFlashStock(Long memberId, Long skuId, int quantity) {
        try {
            stringRedisTemplate.execute(RELEASE_FLASH_SCRIPT,
                    List.of(skuFlashStockKey(skuId), skuFlashLimitKey(memberId, skuId)),
                    String.valueOf(quantity));
        } catch (Exception e) {
            log.error("释放秒杀SKU库存失败, memberId: {}, skuId: {}, quantity: {}", memberId, skuId, quantity, e);
        }
    }

    /**
     * 执行秒杀库存预扣：缓存未初始化时按 min(普通可用库存, 秒杀总限量) 预热后重试一次
     */
    private long doFlashDeduct(Long memberId, Long skuId, int quantity, int limitPerUser, int flashCount) {
        long result = executeFlashDeduct(memberId, skuId, quantity, limitPerUser);
        if (result == RESULT_NOT_INIT) {
            initSkuFlashStock(skuId, flashCount);
            result = executeFlashDeduct(memberId, skuId, quantity, limitPerUser);
        }
        return result;
    }

    private long executeFlashDeduct(Long memberId, Long skuId, int quantity, int limitPerUser) {
        return stringRedisTemplate.execute(DEDUCT_FLASH_SCRIPT,
                List.of(skuFlashStockKey(skuId), skuFlashLimitKey(memberId, skuId)),
                String.valueOf(quantity),
                String.valueOf(limitPerUser),
                String.valueOf(skuLimitExpire));
    }

    /**
     * 秒杀的惰性初始化秒杀 SKU 可用库存（独立池子）
     * <p>
     * 秒杀专属库存 = sms_flash_promotion_product_relation.flash_promotion_count 当前剩余量，
     * 与商品普通库存(pms_sku_stock)完全隔离；已售量已同步落库到该字段，key 过期重建读到的即最新剩余量。
     * 使用 setIfAbsent 保证并发初始化场景下不会被旧值覆盖。
     *
     * @param skuId      商品SKU ID
     * @param flashCount 秒杀剩余可售量（flash_promotion_count，来自下单时刻快照，DB 扣减兜底）
     */
    private void initSkuFlashStock(Long skuId, int flashCount) {
        String key = skuFlashStockKey(skuId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        int flashStock = Math.max(flashCount, 0);
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(flashStock), skuStockExpire, TimeUnit.SECONDS);
    }
}
