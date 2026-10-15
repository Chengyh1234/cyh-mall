package com.macro.mall.portal.mq.event;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 库存异步扣减事件消息体
 * <p>
 * 下单预扣 / 支付成功 / 订单取消三处库存落库操作统一通过该消息异步执行，
 * 与下单接口解耦，实现削峰。操作类型见 {@code StockOperateEventPublisher} 常量。
 */
@Data
@Accessors(chain = true)
public class StockOperateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 库存操作类型：1->锁定(下单预扣落库)；2->扣减(支付成功)；3->释放(取消/超时) */
    private Integer operateType;

    /** 是否秒杀订单。true 时消费者更新 sms_flash_promotion_product_relation.flash_promotion_count，否则更新 pms_sku_stock */
    private Boolean flashOrder;

    /** 订单ID（用于日志排查） */
    private Long orderId;

    /** 库存操作明细：SKU ID -> 数量 */
    private List<StockItem> itemList;

    @Data
    @Accessors(chain = true)
    public static class StockItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商品SKU ID */
        private Long productSkuId;

        /** 操作数量 */
        private Integer quantity;

        /** 秒杀关系ID（秒杀订单更新 flash_promotion_count 时使用，普通订单为 null） */
        private Long flashPromotionRelationId;
    }
}
