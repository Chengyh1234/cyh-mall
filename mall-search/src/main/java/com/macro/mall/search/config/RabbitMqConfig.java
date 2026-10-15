package com.macro.mall.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.mq.event.EsProductMqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mall-search RabbitMQ 消费端配置
 * <p>
 * 商品 ES 索引变更：声明交换机、消费队列、路由绑定与死信队列。
 * 消息由 mall-admin 发布，本模块的 {@code ProductEsChangeConsumer} 监听消费，
 * 消费失败重试耗尽后自动转入死信队列，人工排查补偿。
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    /** 商品索引变更交换机 */
    @Bean
    public DirectExchange esProductExchange() {
        return ExchangeBuilder.directExchange(EsProductMqConstants.EXCHANGE).durable(true).build();
    }

    /**
     * 商品索引变更消费队列，消费者 {@code @RabbitListener(queues = RabbitMqConfig#ES_PRODUCT_QUEUE)}
     */
    @Bean
    public Queue esProductQueue() {
        return QueueBuilder.durable(EsProductMqConstants.QUEUE)
                .deadLetterExchange(EsProductMqConstants.EXCHANGE)
                .deadLetterRoutingKey(EsProductMqConstants.DLQ)
                .build();
    }

    @Bean
    public Binding esProductBinding(DirectExchange esProductExchange, Queue esProductQueue) {
        return BindingBuilder.bind(esProductQueue).to(esProductExchange).with(EsProductMqConstants.ROUTING_KEY);
    }

    /** 商品索引变更死信队列（消费失败后人工补偿） */
    @Bean
    public Queue esProductDlq() {
        return QueueBuilder.durable(EsProductMqConstants.DLQ).build();
    }

    @Bean
    public Binding esProductDlqBinding(DirectExchange esProductExchange, Queue esProductDlq) {
        return BindingBuilder.bind(esProductDlq).to(esProductExchange).with(EsProductMqConstants.DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}