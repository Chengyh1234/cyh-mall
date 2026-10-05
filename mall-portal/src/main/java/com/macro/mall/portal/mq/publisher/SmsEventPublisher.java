package com.macro.mall.portal.mq.publisher;

import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.mq.event.SmsSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 短信验证码发送事件发布者
 * <p>
 * 将短信请求入队后立即返回，由 SmsSendConsumer 异步调用阿里云下发。
 * 短信下发耗时从秒级降为毫秒级，同时 MQ 队列提供削峰缓冲能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布短信发送事件
     *
     * @param event 短信发送事件（携带接收手机号）
     */
    public void publish(SmsSendEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.SMS_EXCHANGE,
                RabbitMqConfig.SMS_SEND_ROUTING_KEY,
                event
        );
        log.info("短信发送事件已入队, phone: {}", event.getPhone());
    }
}
