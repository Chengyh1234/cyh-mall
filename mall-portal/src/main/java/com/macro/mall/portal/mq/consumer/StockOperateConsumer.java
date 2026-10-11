package com.macro.mall.portal.mq.consumer;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.mq.event.StockOperateEvent;
import com.macro.mall.portal.mq.publisher.StockOperateEventPublisher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存异步扣减消费者
 * <p>
 * 按操作类型更新 {@code pms_sku_stock} 表：
 * <ul>
 *     <li>LOCK   ：下单预扣落库，lock_stock 增加（锁定库存）；</li>
 *     <li>DEDUCT ：支付成功，stock 与 lock_stock 同时扣减（真实扣库存）；</li>
 *     <li>RELEASE：取消/超时，lock_stock 回退（释放锁定库存）。</li>
 * </ul>
 * 消费成功后手动 ACK，失败后 NACK（不重回队列，转入死信队列人工补偿）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockOperateConsumer {

    private final PortalOrderDao portalOrderDao;

    @RabbitListener(queues = RabbitMqConfig.STOCK_OPERATE_QUEUE)
    public void handleStockOperate(StockOperateEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (event == null || CollectionUtils.isEmpty(event.getItemList())) {
                log.warn("库存操作消息为空, 直接确认, deliveryTag: {}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }
            List<OmsOrderItem> orderItemList = toOrderItemList(event.getItemList());
            int count;
            switch (event.getOperateType()) {
                case StockOperateEventPublisher.TYPE_LOCK:
                    count = portalOrderDao.updateSkuStockLock(orderItemList);
                    break;
                case StockOperateEventPublisher.TYPE_DEDUCT:
                    count = portalOrderDao.updateSkuStock(orderItemList);
                    break;
                case StockOperateEventPublisher.TYPE_RELEASE:
                    count = portalOrderDao.releaseSkuStockLock(orderItemList);
                    break;
                default:
                    log.warn("未知库存操作类型: {}, orderId: {}", event.getOperateType(), event.getOrderId());
                    channel.basicAck(deliveryTag, false);
                    return;
            }
            channel.basicAck(deliveryTag, false);
            log.info("库存异步操作完成, orderId: {}, operateType: {}, 影响行数: {}", event.getOrderId(), event.getOperateType(), count);
        } catch (Exception e) {
            log.error("库存异步操作失败, orderId: {}, operateType: {}, 异常: {}",
                    event.getOrderId(), event.getOperateType(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 将事件中的库存明细转换为 OmsOrderItem 列表（仅需要 skuId 与数量）
     */
    private List<OmsOrderItem> toOrderItemList(List<StockOperateEvent.StockItem> itemList) {
        List<OmsOrderItem> result = new ArrayList<>();
        for (StockOperateEvent.StockItem item : itemList) {
            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductSkuId(item.getProductSkuId());
            orderItem.setProductQuantity(item.getQuantity());
            result.add(orderItem);
        }
        return result;
    }
}
