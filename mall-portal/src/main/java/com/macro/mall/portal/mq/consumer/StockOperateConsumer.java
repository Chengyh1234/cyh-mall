package com.macro.mall.portal.mq.consumer;

import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
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
 * 按订单类型区分库存落库目标：
 * <ul>
 *     <li>普通订单：更新 {@code pms_sku_stock} 表
 *         <ul>
 *             <li>LOCK   ：下单预扣落库，lock_stock 增加（锁定库存）；</li>
 *             <li>DEDUCT ：支付成功，stock 与 lock_stock 同时扣减（真实扣库存）；</li>
 *             <li>RELEASE：取消/超时，lock_stock 回退（释放锁定库存）。</li>
 *         </ul>
 *     </li>
 *     <li>秒杀订单：更新 {@code sms_flash_promotion_product_relation.flash_promotion_count}
 *         <ul>
 *             <li>LOCK   ：下单占用秒杀库存，flash_promotion_count 扣减（DB 防超卖兜底）；</li>
 *             <li>DEDUCT ：秒杀库存已在下单时占用，支付无需再扣，直接确认；</li>
 *             <li>RELEASE：取消/超时，flash_promotion_count 回补。</li>
 *         </ul>
 *     </li>
 * </ul>
 * 消费成功后手动 ACK，失败后 NACK（不重回队列，转入死信队列人工补偿）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockOperateConsumer {

    private final PortalOrderDao portalOrderDao;
    private final SmsFlashPromotionProductRelationMapper flashPromotionRelationMapper;

    @RabbitListener(queues = RabbitMqConfig.STOCK_OPERATE_QUEUE)
    public void handleStockOperate(StockOperateEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (event == null || CollectionUtils.isEmpty(event.getItemList())) {
                log.warn("库存操作消息为空, 直接确认, deliveryTag: {}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }
            boolean flashOrder = Boolean.TRUE.equals(event.getFlashOrder());
            int count;
            if (flashOrder) {
                // 秒杀订单：操作秒杀专属库存，面单位是"秒杀关系ID"，不支持多条批量的单条扣减，故循环处理
                count = handleFlashOperate(event);
            } else {
                List<OmsOrderItem> orderItemList = toOrderItemList(event.getItemList());
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
            }
            channel.basicAck(deliveryTag, false);
            log.info("库存异步操作完成, orderId: {}, flashOrder: {}, operateType: {}, 影响行数: {}",
                    event.getOrderId(), flashOrder, event.getOperateType(), count);
        } catch (Exception e) {
            log.error("库存异步操作失败, orderId: {}, operateType: {}, 异常: {}",
                    event.getOrderId(), event.getOperateType(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 处理秒杀订单库存操作（更新 sms_flash_promotion_product_relation.flash_promotion_count）
     */
    private int handleFlashOperate(StockOperateEvent event) {
        switch (event.getOperateType()) {
            case StockOperateEventPublisher.TYPE_LOCK:
                // 下单占用秒杀库存：DB 条件 flash_promotion_count >= quantity 兜底防超卖（与 Redis 预扣最终一致）
                int total = 0;
                for (StockOperateEvent.StockItem item : event.getItemList()) {
                    if (item.getFlashPromotionRelationId() == null) {
                        continue;
                    }
                    total += flashPromotionRelationMapper.deductFlashCount(item.getFlashPromotionRelationId(), item.getQuantity());
                }
                return total;
            case StockOperateEventPublisher.TYPE_RELEASE:
                // 取消/超时回补秒杀库存
                int released = 0;
                for (StockOperateEvent.StockItem item : event.getItemList()) {
                    if (item.getFlashPromotionRelationId() == null) {
                        continue;
                    }
                    released += flashPromotionRelationMapper.increaseFlashCount(item.getFlashPromotionRelationId(), item.getQuantity());
                }
                return released;
            case StockOperateEventPublisher.TYPE_DEDUCT:
                // 秒杀库存下单即扣（TYPE_LOCK 已占用），支付成功不再重复扣减，返回 0 表示未影响任何 DB 行
                return 0;
            default:
                throw new IllegalArgumentException("秒杀订单未知库存操作类型: " + event.getOperateType());
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