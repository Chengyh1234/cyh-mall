package com.macro.mall.portal.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.macro.mall.common.exception.Asserts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 短信发送服务
 * <p>
 * 基于阿里云号码认证服务（dypnsapi）的短信验证码能力实现验证码下发。
 * 验证码由阿里云生成，通过 {@code returnVerifyCode} 返回给调用方，业务侧自行校验。
 * 配置项（AK、签名、模板）从 application.yml 的 aliyun.sms 读取。
 */
@Slf4j
@Service
public class SmsService {

    /** 阿里云号码认证服务 Endpoint */
    private static final String ENDPOINT = "dypnsapi.aliyuncs.com";

    /** 短信模板变量：##code## 由阿里云填充当次验证码，##min## 为有效期（分钟） */
    private static final String TEMPLATE_PARAM = "{\"code\":\"##code##\",\"min\":\"5\"}";

    /** 验证码类型：1 纯数字 */
    private static final long CODE_TYPE = 1L;

    /** 同号码重发最短间隔（秒） */
    private static final long SEND_INTERVAL_SECONDS = 60L;

    /** 同号码是否允许重复策略：1=上次验证码过期前不发新码 */
    private static final long DUPLICATE_POLICY = 1L;

    /** 验证码有效期（秒） */
    private static final long CODE_VALID_SECONDS = 300L;

    /** 验证码长度 */
    private static final long CODE_LENGTH = 6L;

    /** 失败自动重试次数：0 不自动重试 */
    private static final long AUTO_RETRY = 0L;

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    /** 阿里云短信方案名 */
    @Value("${aliyun.sms.scheme-name}")
    private String schemeName;

    /** 短信签名 */
    @Value("${aliyun.sms.sign-name}")
    private String signName;

    /** 短信模板 ID */
    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    /** 国家码 */
    @Value("${aliyun.sms.country-code:86}")
    private String countryCode;

    /**
     * 发送登录短信验证码
     * <p>
     * 验证码由阿里云生成并直接返回，不落库；调用方负责将验证码存入 Redis 供后续校验。
     *
     * @param phoneNumber 手机号
     * @return 阿里云生成的 6 位验证码
     */
    public String sendCode(String phoneNumber) {
        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setSchemeName(schemeName)
                    .setCountryCode(countryCode)
                    .setPhoneNumber(phoneNumber)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(TEMPLATE_PARAM)
                    .setCodeType(CODE_TYPE)
                    .setInterval(SEND_INTERVAL_SECONDS)
                    .setDuplicatePolicy(DUPLICATE_POLICY)
                    .setValidTime(CODE_VALID_SECONDS)
                    .setCodeLength(CODE_LENGTH)
                    .setReturnVerifyCode(true)
                    .setAutoRetry(AUTO_RETRY);

            SendSmsVerifyCodeResponse response =
                    createClient().sendSmsVerifyCodeWithOptions(request, new RuntimeOptions());

            // body.code 为 "OK" 表示下发成功，否则为错误码
            String code = response.getBody().getCode();
            if (!"OK".equals(code)) {
                log.warn("短信下发失败，手机号: {}, 错误码: {}, 信息: {}",
                        phoneNumber, code, response.getBody().getMessage());
                Asserts.fail("验证码发送失败，请稍后重试");
            }
            // 验证码位于 body.model.verifyCode（returnVerifyCode=true 时返回）
            return response.getBody().getModel().getVerifyCode();
        } catch (TeaException e) {
            log.error("阿里云短信下发异常，手机号: {}", phoneNumber, e);
            Asserts.fail("短信发送失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("短信下发未知异常，手机号: {}", phoneNumber, e);
            Asserts.fail("短信发送失败，请稍后重试");
        }
        return null;
    }

    /**
     * 创建阿里云客户端
     *
     * @return dypnsapi Client
     */
    private Client createClient() throws Exception {
        Config config = new Config();
        config.accessKeyId = accessKeyId;
        config.accessKeySecret = accessKeySecret;
        config.endpoint = ENDPOINT;
        return new Client(config);
    }
}
