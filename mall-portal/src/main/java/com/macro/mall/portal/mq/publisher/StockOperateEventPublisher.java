package com.macro.mall.portal.mq.publisher;

import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.mq.event.StockOperateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 库存异步扣减事件发布者
 * <p>
 * 将库存落库操作(锁定/扣减/释放)发送到库存队列，由 StockOperateConsumer 异步执行，
 * 避免在订单事务内同步更新库存造成锁竞争与性能损耗。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockOperateEventPublisher {

    /** 库存操作类型：下单预扣落库（lock_stock 增加） */
    public static final int TYPE_LOCK = 1;
    /** 库存操作类型：支付成功真实扣减（stock 与 lock_stock 同时扣减） */
    public static final int TYPE_DEDUCT = 2;
    /** 库存操作类型：取消/超时释放（lock_stock 回退） */
    public static final int TYPE_RELEASE = 3;

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布库存异步操作消息
     *
     * @param event 库存操作事件
     */
    public void publishStockOperate(StockOperateEvent event) {
        log.info("发布库存异步操作消息, orderId: {}, operateType: {}, itemSize: {}",
                event.getOrderId(), event.getOperateType(),
                event.getItemList() == null ? 0 : event.getItemList().size());
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.STOCK_EXCHANGE,
                RabbitMqConfig.STOCK_OPERATE_ROUTING_KEY,
                event);
    }
}
