package com.macro.mall.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列、交换机、死信队列配置
 * <p>
 * 统一以常量定义交换机/队列/路由键，消费者通过 {@code @RabbitListener(queues = RabbitMqConfig.XXX_QUEUE)}
 * 注解标记监听队列。消息统一使用 JSON 序列化（{@link Jackson2JsonMessageConverter}）。
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │ 订单超时取消（延迟队列）                               │
 * │   mall.order.direct.ttl → mall.order.cancel.ttl      │
 * │      （无消费者，消息 TTL 到期）                        │
 * │                ↓ 死信转发                              │
 * │   mall.order.direct → mall.order.cancel（消费者消费）   │
 * ├─────────────────────────────────────────────────────┤
 * │ 短信验证码发送（异步削峰）                              │
 * │   sms.direct → sms.send.queue（消费者消费）            │
 * │        失败 ↓（重试耗尽）                              │
 * │   mall.dead.direct → sms.send.dlq（人工排查）          │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    // ==================== 订单超时取消 ====================

    /** 订单取消交换机 */
    public static final String ORDER_DIRECT_EXCHANGE = "mall.order.direct";
    /** 订单取消队列（实际消费） */
    public static final String ORDER_CANCEL_QUEUE = "mall.order.cancel";
    /** 订单取消路由键 */
    public static final String ORDER_CANCEL_ROUTING_KEY = "mall.order.cancel";
    /** 订单延迟队列交换机（TTL 缓冲） */
    public static final String ORDER_TTL_DIRECT_EXCHANGE = "mall.order.direct.ttl";
    /** 订单延迟队列（无消费者，TTL 到期后转入死信） */
    public static final String ORDER_CANCEL_TTL_QUEUE = "mall.order.cancel.ttl";
    /** 订单延迟队列路由键 */
    public static final String ORDER_CANCEL_TTL_ROUTING_KEY = "mall.order.cancel.ttl";

    // ==================== 短信验证码发送 - 异步削峰 ====================

    /** 短信交换机 */
    public static final String SMS_EXCHANGE = "sms.direct";
    /** 短信发送队列 */
    public static final String SMS_SEND_QUEUE = "sms.send.queue";
    /** 短信发送死信队列（重试耗尽后人工排查） */
    public static final String SMS_SEND_DLQ = "sms.send.dlq";
    /** 短信发送路由键 */
    public static final String SMS_SEND_ROUTING_KEY = "sms.send";

    /** 统一死信交换机（订单超时复用 + 各业务 DLQ） */
    public static final String DEAD_EXCHANGE = "mall.dead.direct";

    // ==================== 库存异步扣减（下单/支付/取消） ====================

    /** 库存操作交换机 */
    public static final String STOCK_EXCHANGE = "mall.stock.direct";
    /** 库存操作队列 */
    public static final String STOCK_OPERATE_QUEUE = "mall.stock.operate.queue";
    /** 库存操作路由键 */
    public static final String STOCK_OPERATE_ROUTING_KEY = "mall.stock.operate";
    /** 库存操作死信队列（消费失败后人工补偿） */
    public static final String STOCK_OPERATE_DLQ = "mall.stock.operate.dlq";

    // ==================== 订单超时取消 - 交换机 & 队列 ====================

    @Bean
    public DirectExchange orderDirect() {
        return ExchangeBuilder.directExchange(ORDER_DIRECT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange orderTtlDirect() {
        return ExchangeBuilder.directExchange(ORDER_TTL_DIRECT_EXCHANGE).durable(true).build();
    }

    /**
     * 订单实际消费队列：消费者 {@code @RabbitListener(queues = RabbitMqConfig.ORDER_CANCEL_QUEUE)}
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    /**
     * 订单延迟队列：消息在此等待 TTL 到期后转入死信交换机 {@link #orderDirect()}
     */
    @Bean
    public Queue orderTtlQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_TTL_QUEUE)
                .deadLetterExchange(ORDER_DIRECT_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderBinding(DirectExchange orderDirect, Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderDirect).with(ORDER_CANCEL_ROUTING_KEY);
    }

    @Bean
    public Binding orderTtlBinding(DirectExchange orderTtlDirect, Queue orderTtlQueue) {
        return BindingBuilder.bind(orderTtlQueue).to(orderTtlDirect).with(ORDER_CANCEL_TTL_ROUTING_KEY);
    }

    // ==================== 短信验证码发送 - 异步削峰 ====================

    @Bean
    public DirectExchange smsExchange() {
        return ExchangeBuilder.directExchange(SMS_EXCHANGE).durable(true).build();
    }

    /**
     * 短信发送队列：消息入队后由 SmsSendConsumer 异步调用阿里云下发短信
     */
    @Bean
    public Queue smsSendQueue() {
        return QueueBuilder.durable(SMS_SEND_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(SMS_SEND_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding smsSendBinding(DirectExchange smsExchange, Queue smsSendQueue) {
        return BindingBuilder.bind(smsSendQueue).to(smsExchange).with(SMS_SEND_ROUTING_KEY);
    }

    /**
     * 短信发送死信队列：消费重试耗尽后落入此队列，人工排查
     */
    @Bean
    public Queue smsSendDlq() {
        return QueueBuilder.durable(SMS_SEND_DLQ).build();
    }

    @Bean
    public Binding smsSendDlqBinding(Queue smsSendDlq) {
        return BindingBuilder.bind(smsSendDlq).to(deadExchange()).with(SMS_SEND_ROUTING_KEY);
    }

    // ==================== 库存异步扣减 - 交换机 & 队列 ====================

    @Bean
    public DirectExchange stockExchange() {
        return ExchangeBuilder.directExchange(STOCK_EXCHANGE).durable(true).build();
    }

    /**
     * 库存操作队列：消费者 {@code @RabbitListener(queues = RabbitMqConfig.STOCK_OPERATE_QUEUE)}
     * 消费失败重试耗尽后转入死信队列人工补偿
     */
    @Bean
    public Queue stockOperateQueue() {
        return QueueBuilder.durable(STOCK_OPERATE_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(STOCK_OPERATE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding stockOperateBinding(DirectExchange stockExchange, Queue stockOperateQueue) {
        return BindingBuilder.bind(stockOperateQueue).to(stockExchange).with(STOCK_OPERATE_ROUTING_KEY);
    }

    @Bean
    public Queue stockOperateDlq() {
        return QueueBuilder.durable(STOCK_OPERATE_DLQ).build();
    }

    @Bean
    public Binding stockOperateDlqBinding(Queue stockOperateDlq) {
        return BindingBuilder.bind(stockOperateDlq).to(deadExchange()).with(STOCK_OPERATE_ROUTING_KEY);
    }

    // ==================== 死信交换机 ====================

    @Bean
    public DirectExchange deadExchange() {
        return ExchangeBuilder.directExchange(DEAD_EXCHANGE).durable(true).build();
    }

    // ==================== 消息序列化 ====================

    /**
     * 统一 JSON 序列化：消息体为 typed event，支持跨模块反序列化
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 自定义 RabbitTemplate：JSON 序列化 + 消息路由失败回调
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.warn("消息路由失败, exchange: {}, routingKey: {}, replyText: {}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
