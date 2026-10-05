package com.macro.mall.portal.mq.event;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 短信验证码发送事件消息体
 * <p>
 * 二维码校验通过后，Controller 仅需将短信请求入队即可立即返回，
 * 由 SmsSendConsumer 异步调用阿里云下发短信，并把生成的验证码写入 Redis，
 * 实现请求毫秒级响应 + MQ 削峰缓冲。
 * 因阿里云短信接口是"生成验证码 + 下发"一体完成，事件只需携带手机号即可。
 */
@Data
@Accessors(chain = true)
public class SmsSendEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收短信的手机号 */
    private String phone;
}
