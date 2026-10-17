package com.macro.mall.portal.service;

import com.baomidou.mybatisplus.spring.service.IService;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.UmsMemberUpdateParam;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 会员管理Service
 * Created by macro on 2018/8/3.
 */
public interface UmsMemberService extends IService<UmsMember> {
    /**
     * 根据手机号获取会员
     */
    UmsMember getByPhone(String phone);

    /**
     * 根据会员编号获取会员
     */
    UmsMember getById(Long id);

    /**
     * 用户注册（短信验证码校验）
     */
    void register(String phone, String password, String smsCode);

    /**
     * 修改密码（短信验证码校验）
     */
    void updatePassword(String telephone, String password, String smsCode);

    /**
     * 忘记密码（短信验证码校验，无需登录）
     */
    void forgetPassword(String phone, String password, String smsCode);

    /**
     * 获取当前登录会员
     */
    UmsMember getCurrentMember();

    /**
     * 修改当前登录会员的个人信息（昵称/头像/性别/生日/签名）
     */
    void updateCurrentMemberInfo(UmsMemberUpdateParam param);

    /**
     * 根据会员id修改会员积分
     */
    void updateIntegration(Long id,Integer integration);


    /**
     * 获取用户信息
     */
    UserDetails loadUserByUsername(String username);


    String login(String phone, String password);

    void sendLoginSmsCode(String phone, String captchaKey, String captchaText);

    /**
     * 短信验证码登录
     *
     * @param phone   手机号
     * @param smsCode 短信验证码
     * @return JWT token，登录失败返回 null
     */
    String loginBySmsCode(String phone, String smsCode);

    //    /**
//     * 刷新token
//     */
//    String refreshToken(String token);
}
