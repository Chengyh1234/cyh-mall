package com.macro.mall.search.mq.consumer;

import com.macro.mall.common.mq.event.EsProductMqConstants;
import com.macro.mall.common.mq.event.ProductEsChangeAction;
import com.macro.mall.common.mq.event.ProductEsChangeEvent;
import com.macro.mall.search.service.EsProductService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

/**
 * 商品 ES 索引变更消费者
 * <p>
 * 监听 mall-admin 发布的商品变更消息，维护 ES 索引：
 * <ul>
 *     <li>{@link ProductEsChangeAction#CREATED README} / {@link ProductEsChangeAction#UPDATED}：重新拉取并写入/更新索引</li>
 *     <li>{@link ProductEsChangeAction#DELETED}：从索引移除</li>
 * </ul>
 * 消费成功后手动 ACK；失败后 NACK（不重回队列，转入死信队列人工补偿）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEsChangeConsumer {

    private final EsProductService esProductService;

    @RabbitListener(queues = EsProductMqConstants.QUEUE)
    public void handleProductChange(ProductEsChangeEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (event == null || CollectionUtils.isEmpty(event.getProductIds())) {
                log.warn("商品 ES 索引变更消息为空或商品ID集合为空, 直接 ACK, action: {}",
                        event == null ? null : event.getAction());
                channel.basicAck(deliveryTag, false);
                return;
            }

            List<Long> productIds = event.getProductIds();
            ProductEsChangeAction action = event.getAction();
            log.info("消费商品 ES 索引变更消息, action: {}, productIds: {}", action, productIds);

            if (action == ProductEsChangeAction.DELETED) {
                // 删除 / 下架：从索引移除
                esProductService.delete(productIds);
            } else {
                // 创建 / 更新：逐个重新同步索引
                for (Long id : productIds) {
                    esProductService.create(id);
                }
            }
            channel.basicAck(deliveryTag, false);
            log.info("商品 ES 索引变更消息消费完成, action: {}, productIds: {}", action, productIds);
        } catch (Exception e) {
            log.error("消费商品 ES 索引变更消息失败, productIds: {}, 异常: {}",
                    event == null ? null : event.getProductIds(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}