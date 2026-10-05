package com.macro.mall.portal.mq.publisher;

import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.mq.event.OrderCancelEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消事件发布者
 * <p>
 * 将订单取消请求发送到延迟队列，消息 TTL 到期后自动转入超时处理队列，
 * 由 OrderCancelConsumer 异步处理，与下单接口解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布订单超时取消延迟消息
     * <p>
     * 使用消息级别 TTL，精确控制每笔订单的超时时间。
     *
     * @param orderId    订单ID
     * @param delayMillis 延迟毫秒数（超时时间）
     */
    public void publishOrderCancel(Long orderId, long delayMillis) {
        log.info("发布订单超时取消延迟消息, orderId: {}, 延迟: {}ms", orderId, delayMillis);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_TTL_DIRECT_EXCHANGE,
                RabbitMqConfig.ORDER_CANCEL_TTL_ROUTING_KEY,
                new OrderCancelEvent().setOrderId(orderId),
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    return message;
                }
        );
    }
}
