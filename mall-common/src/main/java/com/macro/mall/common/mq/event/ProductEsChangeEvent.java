package com.macro.mall.common.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 商品 ES 索引变更事件
 * <p>
 * 既作为 RabbitMQ 消息体跨 mall-admin / mall-search 传输，也作为 Spring 本地
 * 事务事件（经 {@code @TransactionalEventListener} 在事务提交后转发到 MQ）。
 * <p>
 * {@code action} 描述变更类型，{@code productIds} 承载受影响的商品 ID（支持批量变更）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ProductEsChangeEvent {

    /** 变更动作 */
    private ProductEsChangeAction action;

    /** 受影响的商品 ID 集合 */
    private List<Long> productIds;

    public static ProductEsChangeEvent of(ProductEsChangeAction action, List<Long> productIds) {
        return new ProductEsChangeEvent(action, productIds);
    }

    public static ProductEsChangeEvent of(ProductEsChangeAction action, Long productId) {
        return new ProductEsChangeEvent(action, List.of(productId));
    }
}