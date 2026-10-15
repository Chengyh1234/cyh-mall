package com.macro.mall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mall-admin RabbitMQ 生产端配置
 * <p>
 * 本模块作为商品 ES 索引变更事件的发布方，仅需「JSON 序列化 + RabbitTemplate」即可向
 * 交换机发送消息；交换机 / 队列 / 绑定 / 死信由消费端 mall-search 统一声明，避免重复声明同名队列。
 */
@Slf4j
@Configuration
public class MallAdminRabbitConfig {

    /**
     * JSON 序列化：消息体为 {@code ProductEsChangeEvent}，支持跨模块反序列化
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