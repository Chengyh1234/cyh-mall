package com.macro.mall.mq.listener;

import com.macro.mall.common.mq.event.ProductEsChangeEvent;
import com.macro.mall.mq.publisher.ProductEsChangePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 商品 ES 索引变更事务监听器
 * <p>
 * 监听业务层发布的本地区域事件 {@link ProductEsChangeEvent}，在<b>事务提交后</b>
 * 转发到 RabbitMQ，由 mall-search 异步更新 ES 索引，保证消息只在数据库操作成功后发出。
 * <p>
 * {@code fallbackExecution=true}：当来源方法不处于事务中（如单条 SQL 的状态更新）时直接触发，
 * 避免事件被遗漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEsChangeTransactionalListener {

    private final ProductEsChangePublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductChange(ProductEsChangeEvent event) {
        try {
            publisher.publish(event);
        } catch (Exception e) {
            log.error("发布商品 ES 索引变更消息失败, action: {}, productIds: {}, 异常: {}",
                    event.getAction(), event.getProductIds(), e.getMessage(), e);
        }
    }
}