package com.macro.mall.portal.mq.consumer;

import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.mq.event.OrderCancelEvent;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消消费者
 * <p>
 * 消费延迟队列到期的消息，调用 OmsPortalOrderService.cancelOrder 处理超时未支付订单。
 * 消费成功后手动 ACK，失败后 NACK（不重回队列，转入死信交换机）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelConsumer {

    private final OmsPortalOrderService portalOrderService;

    @RabbitListener(queues = RabbitMqConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderCancel(OrderCancelEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("消费订单超时取消消息, orderId: {}", event.getOrderId());
            portalOrderService.cancelOrder(event.getOrderId());
            channel.basicAck(deliveryTag, false);
            log.info("订单超时取消消息消费完成, orderId: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("消费订单超时取消消息失败, orderId: {}, 异常: {}", event.getOrderId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
