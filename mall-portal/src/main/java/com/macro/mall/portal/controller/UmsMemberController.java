package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.UmsMemberUpdateParam;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 会员管理Controller
 * Created by macro on 2018/8/3.
 */
@Controller
@Tag(name = "UmsMemberController", description = "会员登录注册管理")
@RequestMapping("/sso")
public class UmsMemberController {
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "会员注册（短信验证码）")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult register(@RequestParam String phone,
                                 @RequestParam String password,
                                 @RequestParam String smsCode) {
        memberService.register(phone, password, smsCode);
        return CommonResult.success(null,"注册成功");
    }

    @Operation(summary = "手机号密码登录")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestParam String phone,
                              @RequestParam String password) {
        String token = memberService.login(phone, password);
        if (token == null) {
            return CommonResult.validateFailed("用户名或密码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "获取会员信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult info() {
        UmsMember member = memberService.getCurrentMember();

        return CommonResult.success(member);
    }

    @Operation(summary = "修改当前登录会员的个人信息")
    @RequestMapping(value = "/info", method = RequestMethod.PUT)
    @ResponseBody
    public CommonResult updateInfo(@RequestBody UmsMemberUpdateParam param) {
        memberService.updateCurrentMemberInfo(param);
        return CommonResult.success(null, "修改成功");
    }

    @Operation(summary = "获取个人积分/成长值资产")
    @RequestMapping(value = "/points", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult points() {
        UmsMember member = memberService.getCurrentMember();

        Map<String, Object> data = new HashMap<>();
        data.put("integration", member.getIntegration() == null ? 0 : member.getIntegration());
        data.put("growth", member.getGrowth() == null ? 0 : member.getGrowth());
        data.put("historyIntegration", member.getHistoryIntegration() == null ? 0 : member.getHistoryIntegration());
        return CommonResult.success(data);
    }

    @Operation(summary = "校验图形验证码并发送短信验证码")
    @RequestMapping(value = "/verifyQrCode", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult verifyQrCode(@RequestParam String phone,
                                     @RequestParam String captchaKey,
                                     @RequestParam String captchaText) {
        // 图形验证码校验通过后短信请求入队 MQ，异步下发，接口立即返回
        memberService.sendLoginSmsCode(phone, captchaKey, captchaText);
        return CommonResult.success(null,"验证码已发送");
    }

    @Operation(summary = "短信验证码登录")
    @RequestMapping(value = "/loginBySmsCode", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult loginBySmsCode(@RequestParam String phone,
                                       @RequestParam String smsCode) {
        String token = memberService.loginBySmsCode(phone, smsCode);
        if (token == null) {
            return CommonResult.validateFailed("短信验证码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "忘记密码（重置密码，无需登录）")
    @RequestMapping(value = "/forgetPassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult forgetPassword(@RequestParam String phone,
                                       @RequestParam String password,
                                       @RequestParam String smsCode) {
        memberService.forgetPassword(phone, password, smsCode);
        return CommonResult.success(null,"密码重置成功");
    }

    @Operation(summary = "会员修改密码")
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updatePassword(@RequestParam String telephone,
                                 @RequestParam String password,
                                 @RequestParam String smsCode) {
        memberService.updatePassword(telephone,password,smsCode);
        return CommonResult.success(null,"密码修改成功");
    }


    //    @Operation(summary = "刷新token")
//    @RequestMapping(value = "/refreshToken", method = RequestMethod.GET)
//    @ResponseBody
//    public CommonResult refreshToken(HttpServletRequest request) {
//        String token = request.getHeader(tokenHeader);
//        String refreshToken = memberService.refreshToken(token);
//        if (refreshToken == null) {
//            return CommonResult.failed("token已经过期！");
//        }
//        Map<String, String> tokenMap = new HashMap<>();
//        tokenMap.put("token", refreshToken);
//        tokenMap.put("tokenHead", tokenHead);
//        return CommonResult.success(tokenMap);
//    }
}
