package com.macro.mall.portal.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.RedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 图形验证码Controller
 * <p>
 * 基于 Hutool 生成图形验证码，验证码文本存入 Redis（一次性使用），
 * 前端提交登录/短信请求时需携带 captchaKey 与用户输入的验证码内容，由业务侧自行校验。
 */
@Slf4j
@Tag(name = "图形验证码")
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final RedisService redisService;

    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.key.captcha}")
    private String REDIS_KEY_CAPTCHA;
    @Value("${redis.expire.captcha}")
    private Long CAPTCHA_EXPIRE_SECONDS;

    /**
     * 获取图形验证码
     * <p>
     * 生成一个 4 位数字的图形验证码，返回 Base64 格式的图片和唯一标识 captchaKey。
     * 前端在登录/获取二维码时需要提交该 captchaKey 和用户输入的验证码内容。
     *
     * @return 包含 captchaKey 和 captchaImage 的响应结果
     */
    @Operation(summary = "获取图形验证码")
    @GetMapping
    public CommonResult<Map<String, String>> getCaptcha() {
        // 1. 使用 Hutool 生成图形验证码：4位数字，宽120px，高40px，10个干扰圆圈
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(120, 40, 4, 10);

        // 2. 生成唯一 Key 作为 Redis 存储标识
        String captchaKey = IdUtil.fastSimpleUUID();

        // 3. 将验证码文本存入 Redis（一次性使用，1分钟过期）
        redisService.set(REDIS_DATABASE + ":" + REDIS_KEY_CAPTCHA + ":" + captchaKey,
                captcha.getCode(), CAPTCHA_EXPIRE_SECONDS);

        log.debug("生成图形验证码: key={}, code={}", captchaKey, captcha.getCode());

        // 4. 将图片转为 Base64 格式返回
        Map<String, String> data = new HashMap<>();
        data.put("captchaKey", captchaKey);
        data.put("captchaImage", "data:image/png;base64," + captcha.getImageBase64());

        return CommonResult.success(data);
    }
}
