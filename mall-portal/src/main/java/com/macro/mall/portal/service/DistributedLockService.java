package com.macro.mall.portal.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务
 * <p>
 * 基于 Redisson 提供两种锁执行模式：
 * <ul>
 *     <li>{@link #executeWithLock}：无事务场景，业务执行完立即释放锁；</li>
 *     <li>{@link #executeInTransaction}：事务感知场景，锁保持到事务提交/回滚后才释放，
 *         并支持注册事务提交后的回调（如下单成功后打防重标记），保证「订单落库 → 标记写入 → 释放锁」的严格顺序。</li>
 * </ul>
 * leaseTime 传 0 或负数时使用 Redisson watchdog 自动续期（锁生命周期跟随业务执行）。
 */
public interface DistributedLockService {

    /**
     * 无事务场景：获取锁执行业务，业务返回后立即释放锁
     *
     * @param key       锁key（需保证业务内唯一）
     * @param waitTime  获取锁最大等待时间
     * @param leaseTime 锁持有时间（&lt;=0 表示 watchdog 自动续期）
     * @param unit      时间单位
     * @param supplier  锁保护的业务逻辑
     * @return 业务结果
     */
    <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier);

    /**
     * 事务感知场景：获取锁执行事务内业务，锁在事务提交/回滚后释放
     * <p>
     * 适用于「临界区跨越数据库事务」的场景（如下单），
     * 保证：事务提交 → afterCommit 回调（如写防重标记）→ 释放锁。
     *
     * @param key         锁key
     * @param waitTime    获取锁最大等待时间
     * @param leaseTime   锁持有时间（&lt;=0 表示 watchdog 自动续期）
     * @param unit        时间单位
     * @param supplier    锁保护的事务内业务逻辑
     * @param afterCommit 事务提交后的回调（可为 null）
     * @return 业务结果
     */
    <T> T executeInTransaction(String key, long waitTime, long leaseTime, TimeUnit unit,
                               Supplier<T> supplier, Runnable afterCommit);
}
