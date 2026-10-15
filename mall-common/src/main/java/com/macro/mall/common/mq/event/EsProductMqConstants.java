package com.macro.mall.common.mq.event;

/**
 * 商品 ES 索引变更的 RabbitMQ 常量
 * <p>
 * 交换机、队列、路由键与死信队列命名。生产端（mall-admin）发布到交换机，
 * 消费端（mall-search）声明交换机 / 队列 / 绑定 / 死信并监听消费。
 */
public final class EsProductMqConstants {

    /** 商品索引变更交换机 */
    public static final String EXCHANGE = "mall.es.product.direct";

    /** 商品索引变更队列 */
    public static final String QUEUE = "mall.es.product.queue";

    /** 商品索引变更路由键 */
    public static final String ROUTING_KEY = "mall.es.product";

    /** 商品索引变更死信队列（消费失败后人工补偿） */
    public static final String DLQ = "mall.es.product.dlq";

    private EsProductMqConstants() {
    }
}