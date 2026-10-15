package com.macro.mall.mq.publisher;

import com.macro.mall.common.mq.event.EsProductMqConstants;
import com.macro.mall.common.mq.event.ProductEsChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 商品 ES 索引变更事件发布者
 * <p>
 * 将商品变更事件发送到交换机，由 mall-search 消费后维护 ES 索引。与数据库事务解耦，
 * 由 {@code ProductEsChangeTransactionalListener} 在事务提交后调用，避免"事务回滚但消息已发"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEsChangePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布商品 ES 索引变更事件
     *
     * @param event 变更事件（含动作与商品 ID 集合）
     */
    public void publish(ProductEsChangeEvent event) {
        log.info("发布商品 ES 索引变更消息, action: {}, productIds: {}", event.getAction(), event.getProductIds());
        rabbitTemplate.convertAndSend(EsProductMqConstants.EXCHANGE, EsProductMqConstants.ROUTING_KEY, event);
    }
}