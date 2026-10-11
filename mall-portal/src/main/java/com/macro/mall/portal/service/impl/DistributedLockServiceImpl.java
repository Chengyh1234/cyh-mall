package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.portal.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的分布式锁服务实现
 * <p>
 * 关键设计：事务感知模式将锁的释放绑定到事务生命周期，
 * 避免「业务方法返回即释放锁、事务却未提交」导致的重复下单问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 无事务场景的锁执行：
     * <ol>
     *     <li>以 waitTime 为限尝试抢锁，抢不到直接抛 {@link ApiException} 快速失败；</li>
     *     <li>抢到锁后执行 {@code supplier} 业务逻辑；</li>
     *     <li>无论成功或异常，进入 finally 立即释放锁（业务返回即放锁）。</li>
     * </ol>
     * <p>适用于「临界区 = 单个方法调用、不依赖数据库事务」的场景，
     * 例如库存缓存增减、幂等写入等。若业务跨越事务，请改用 {@link #executeInTransaction}。
     */
    @Override
    public <T> T executeWithLock(String key, long waitTime, long leaseTime,
                                 TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            // 抢锁：等待上限为 waitTime，超时未获得则视为并发冲突，直接失败
            acquired = tryAcquire(lock, waitTime, leaseTime, unit);
            if (!acquired) {
                log.warn("获取分布式锁失败, key: {}, waitTime: {} {}", key, waitTime, unit);
                throw new ApiException("操作太频繁，请稍后再试");
            }
            log.debug("获取分布式锁成功, key: {}", key);
            // 获得锁，进入临界区执行业务
            return supplier.get();
        } catch (ApiException e) {
            // ApiException 为预期的业务拒绝（如防重命中），原样抛出，不包装
            throw e;
        } catch (Exception e) {
            // 其他未知异常统一包装为系统繁忙，避免把原始异常直接抛出
            log.error("分布式锁执行异常, key: {}", key, e);
            throw new ApiException("系统繁忙，请稍后重试");
        } finally {
            // 业务返回即释放锁；仅当前线程仍持有锁时才释放（防误释放他人/已过期的锁）
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁, key: {}", key);
            }
        }
    }

    /**
     * 事务感知场景的锁执行：
     * <ol>
     *     <li>以 waitTime 为限尝试抢锁，抢不到快速失败；</li>
     *     <li>抢到锁后在事务内执行 {@code supplier} 业务逻辑；</li>
     *     <li>锁<b>不立即释放</b>，而是绑定到事务生命周期：
     *         事务提交后先执行 {@code afterCommit} 回调，提交/回滚后由事务回调释放锁；</li>
     *     <li>若业务在事务内抛异常（事务将回滚），则立即释放锁并抛出。</li>
     * </ol>
     * <p>核心价值：保证「订单落库 → afterCommit 写防重标记 → 释放锁」的严格顺序，
     * 避免锁已释放但事务未提交、防重标记未写入时，并发请求穿透产生重复订单。
     * 若当前线程无事务上下文（外部直接调用），则退化为「执行回调 + 立即释放锁」。
     *
     * @param afterCommit 事务提交成功后执行的回调（可为 null）
     */
    @Override
    public <T> T executeInTransaction(String key, long waitTime, long leaseTime,
                                      TimeUnit unit, Supplier<T> supplier, Runnable afterCommit) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = tryAcquire(lock, waitTime, leaseTime, unit);
            if (!acquired) {
                log.warn("获取分布式锁失败, key: {}, waitTime: {} {}", key, waitTime, unit);
                throw new ApiException("操作太频繁，请稍后再试");
            }
            log.debug("获取分布式锁成功, key: {}", key);
            // 事务内执行下单等业务（此时锁仍被持有，临界区覆盖整个事务）
            T result = supplier.get();
            // 将锁的释放绑定到事务提交/回滚之后，保证临界区真正覆盖事务
            releaseLockAfterTransaction(lock, afterCommit);
            return result;
        } catch (ApiException e) {
            // 业务异常直接抛出；事务将回滚已无标记可写，立即释放锁避免后续请求等待
            releaseLockNow(lock, acquired);
            throw e;
        } catch (Exception e) {
            log.error("分布式锁执行异常, key: {}", key, e);
            // 未知异常同样立即释放锁后抛出，防止持锁超时
            releaseLockNow(lock, acquired);
            throw new ApiException("系统繁忙，请稍后重试");
        }
    }

    /**
     * 尝试获取锁
     * <p>leaseTime &gt; 0：使用显式租约，到期自动释放锁（声明式超时）；</p>
     * <p>leaseTime &lt;= 0：不指定租约，由 Redisson 看门狗 watchdog 自动续期，
     * 锁生命周期跟随业务执行，业务结束调用 {@code unlock()} 才释放。</p>
     *
     * @return true 表示抢锁成功；被中断时恢复中断标记并返回 false
     */
    private boolean tryAcquire(RLock lock, long waitTime, long leaseTime, TimeUnit unit) {
        try {
            if (leaseTime > 0) {
                // 显式指定租约：锁持有超过 leaseTime 自动过期，即使业务卡死也能兜底释放
                return lock.tryLock(waitTime, leaseTime, unit);
            }
            // 未指定租约：Redisson 启动 watchdog，默认租约 30s，每 10s 自动续期一次；
            // 只要业务未结束且客户端存活，锁就不会因业务超时而误过期，直到 unlock() 才真正释放
            return lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            // 中断时恢复中断标记，交由上层感知，避免吞掉中断信号
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断, key: {}", lock.getName(), e);
            return false;
        }
    }

    /**
     * 注册事务同步回调，把锁的释放时机交给事务管理：
     * <ul>
     *     <li><b>事务提交成功后</b>：先执行 afterCommit 回调（如下单后写防重标记），再在 afterCompletion 中释放锁；</li>
     *     <li><b>事务回滚后</b>：直接在 afterCompletion 中释放锁（不执行回调）；</li>
     *     <li><b>无事务上下文</b>：立即执行回调并释放锁，保证调用方不需关心是否存在事务。</li>
     * </ul>
     */
    private void releaseLockAfterTransaction(RLock lock, Runnable afterCommit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功：先执行回调（打防重标记等），与事务提交保持同一时间窗
                    if (afterCommit != null) {
                        afterCommit.run();
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // 提交或回滚后统一放锁，保证下一个请求能进入、且能看到标记
                    releaseLockNow(lock, true);
                }
            });
        } else {
            // 无事务上下文：走兜底路径，立即执行回调并释放锁
            if (afterCommit != null) {
                afterCommit.run();
            }
            releaseLockNow(lock, true);
        }
    }

    /**
     * 立即释放锁（仅当真正持有锁时才释放）
     * <p>通过 {@code isHeldByCurrentThread()} 双重校验：一是锁已成功获得（acquired），
     * 二是当前线程确持有该锁，避免释放他人成功续期的锁造成锁失效。</p>
     *
     * @param acquired 本次是否成功获得锁
     */
    private void releaseLockNow(RLock lock, boolean acquired) {
        if (acquired && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放分布式锁, key: {}", lock.getName());
        }
    }
}
