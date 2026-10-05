package com.macro.mall.portal.mq.consumer;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.portal.config.RabbitMqConfig;
import com.macro.mall.portal.mq.event.SmsSendEvent;
import com.macro.mall.portal.service.SmsService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 短信验证码发送消费者
 * <p>
 * 异步消费短信队列，调用 SmsService 生成验证码并下发短信，下发成功后把验证码写入 Redis。
 * 消费成功后手动 ACK，失败后 NACK（不重回队列，重试耗尽自动进入 sms.send.dlq 死信队列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsSendConsumer {

    private final SmsService smsService;
    private final RedisService redisService;

    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.key.smsCode}")
    private String REDIS_KEY_SMS_CODE;
    @Value("${redis.expire.smsCode}")
    private Long SMS_CODE_EXPIRE_SECONDS;

    @RabbitListener(queues = RabbitMqConfig.SMS_SEND_QUEUE)
    public void handleSmsSend(SmsSendEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String phone = event.getPhone();
        String redisKey = REDIS_DATABASE + ":" + REDIS_KEY_SMS_CODE + ":" + phone;
        try {
            // 防止有效期内重复发送：验证码仍存在则直接 ACK 跳过
            if (Boolean.TRUE.equals(redisService.hasKey(redisKey))) {
                channel.basicAck(deliveryTag, false);
                log.info("短信验证码仍在有效期内，跳过重复发送, phone: {}", phone);
                return;
            }

            // 阿里云生成验证码并下发短信，返回验证码
            String code = smsService.sendCode(phone);

            // 验证码写入 Redis，供登录/注册校验接口使用
            redisService.set(redisKey, code, SMS_CODE_EXPIRE_SECONDS);

            channel.basicAck(deliveryTag, false);
            log.info("短信发送成功, phone: {}", phone);
        } catch (Exception e) {
            log.error("短信发送失败, phone: {}, 将转入死信队列", phone, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
