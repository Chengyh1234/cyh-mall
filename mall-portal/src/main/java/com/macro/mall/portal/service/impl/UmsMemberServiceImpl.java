package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.mq.event.SmsSendEvent;
import com.macro.mall.portal.mq.publisher.SmsEventPublisher;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员管理Service实现类
 * Created by macro on 2018/8/3.
 */
@Service
public class UmsMemberServiceImpl extends ServiceImpl<UmsMemberMapper, UmsMember> implements UmsMemberService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsMemberServiceImpl.class);
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UmsMemberLevelMapper memberLevelMapper;
    @Autowired
    private UmsMemberCacheService memberCacheService;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.key.captcha}")
    private String REDIS_KEY_CAPTCHA;
    @Value("${redis.key.smsCode}")
    private String REDIS_KEY_PREFIX_SMS_CODE;
    @Value("${redis.expire.smsCode}")
    private Long SMS_CODE_EXPIRE_SECONDS;
    @Autowired
    private RedisService redisService;
    @Autowired
    private SmsEventPublisher smsEventPublisher;

    @Override
    public UmsMember getByPhone(String phone) {
        UmsMember member = memberCacheService.getMember(phone);
        if(member!=null) return member;
        List<UmsMember> memberList = this.list(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getPhone, phone));
        if (!CollectionUtils.isEmpty(memberList)) {
            member = memberList.get(0);
            memberCacheService.setMember(member);
            return member;
        }
        return null;
    }

    @Override
    public UmsMember getById(Long id) {
        return baseMapper.selectById(id);
    }

    @Transactional
    @Override
    public void register(String phone, String password, String smsCode) {
        //验证短信验证码（一次性使用）
        String redisKey = REDIS_DATABASE + ":" + REDIS_KEY_PREFIX_SMS_CODE + ":" + phone;
        String storedCode = (String) redisService.get(redisKey);
        if (StrUtil.isEmpty(storedCode) || !storedCode.equals(smsCode)) {
            Asserts.fail("短信验证码错误或已过期");
        }
        redisService.del(redisKey);
        //查询是否已有该用户
        List<UmsMember> umsMembers = this.list(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getPhone, phone));
        if (!CollectionUtils.isEmpty(umsMembers)) {
            Asserts.fail("该用户已经存在");
        }
        //没有该用户进行添加操作
        UmsMember umsMember = new UmsMember();
        umsMember.setPhone(phone);
        umsMember.setNickname(phone);
        umsMember.setPassword(passwordEncoder.encode(password));
        umsMember.setCreateTime(LocalDateTime.now());
        umsMember.setStatus(1);
        //获取默认会员等级并设置
        List<UmsMemberLevel> memberLevelList = memberLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberLevel>()
                .eq(UmsMemberLevel::getDefaultStatus, 1));
        if (!CollectionUtils.isEmpty(memberLevelList)) {
            umsMember.setMemberLevelId(memberLevelList.get(0).getId());
        }
        baseMapper.insert(umsMember);
        umsMember.setPassword(null);
    }

    @Transactional
    @Override
    public void updatePassword(String telephone, String password, String smsCode) {
        List<UmsMember> memberList = this.list(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getPhone, telephone));
        if(CollectionUtils.isEmpty(memberList)){
            Asserts.fail("该账号不存在");
        }
        //验证短信验证码（一次性使用）
        verifySmsCode(telephone, smsCode);
        UmsMember umsMember = memberList.get(0);
        umsMember.setPassword(passwordEncoder.encode(password));
        baseMapper.updateById(umsMember);
        memberCacheService.delMember(umsMember.getId());
    }

    @Transactional
    @Override
    public void forgetPassword(String phone, String password, String smsCode) {
        //验证短信验证码（一次性使用）
        verifySmsCode(phone, smsCode);
        //查询账号
        List<UmsMember> memberList = this.list(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getPhone, phone));
        if (CollectionUtils.isEmpty(memberList)) {
            Asserts.fail("该账号不存在");
        }
        UmsMember umsMember = memberList.get(0);
        umsMember.setPassword(passwordEncoder.encode(password));
        baseMapper.updateById(umsMember);
        memberCacheService.delMember(umsMember.getId());
    }

    @Override
    public UmsMember getCurrentMember() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
        return memberDetails.getUmsMember();
    }

    @Override
    public void updateIntegration(Long id, Integer integration) {
        UmsMember record=new UmsMember();
        record.setId(id);
        record.setIntegration(integration);
        baseMapper.updateById(record);
        memberCacheService.delMember(id);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UmsMember member = getByPhone(username);
        if(member!=null){
            return new MemberDetails(member);
        }
        throw new UsernameNotFoundException("用户名或密码错误");
    }

    //    @Override
//    public String refreshToken(String token) {
//        return jwtTokenUtil.refreshHeadToken(token);
//    }

    @Override
    public String login(String phone, String password) {
        String token = null;
        //密码需要客户端加密后传递
        try {
            UserDetails userDetails = loadUserByUsername(phone);
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new BadCredentialsException("密码不正确");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
        } catch (AuthenticationException e) {
            LOGGER.warn("登录异常:{}", e.getMessage());
        }
        return token;
    }

    @Override
    public void sendLoginSmsCode(String phone, String captchaKey, String captchaText) {
        // 校验图形验证码（一次性使用，防止短信接口被批量调用）
        String captchaRedisKey = REDIS_DATABASE + ":" + REDIS_KEY_CAPTCHA + ":" + captchaKey;
        String storedCaptcha = (String) redisService.get(captchaRedisKey);
        if (StrUtil.isEmpty(storedCaptcha) || !storedCaptcha.equalsIgnoreCase(captchaText)) {
            Asserts.fail("图形验证码错误或已过期");
        }
        redisService.del(captchaRedisKey);
        // 短信请求入队 MQ，由 SmsSendConsumer 异步生成验证码并下发，接口立即返回（异步削峰）
        // 登录/注册/改密均复用该流程，仅校验图形验证码，不限制手机号注册状态
        smsEventPublisher.publish(new SmsSendEvent().setPhone(phone));
    }

    @Override
    public String loginBySmsCode(String phone, String smsCode) {
        // 校验短信验证码（一次性使用）
        String redisKey = REDIS_DATABASE + ":" + REDIS_KEY_PREFIX_SMS_CODE + ":" + phone;
        String storedCode = (String) redisService.get(redisKey);
        if (StrUtil.isEmpty(storedCode) || !storedCode.equals(smsCode)) {
            Asserts.fail("短信验证码错误或已过期");
        }
        redisService.del(redisKey);
        // 复用密码登录的 token 签发逻辑
        String token = null;
        try {
            UserDetails userDetails = loadUserByUsername(phone);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
        } catch (AuthenticationException e) {
            LOGGER.warn("短信验证码登录异常:{}", e.getMessage());
        }
        return token;
    }

    //校验短信验证码（一次性使用，通过后删除）
    private void verifySmsCode(String phone, String smsCode) {
        String redisKey = REDIS_DATABASE + ":" + REDIS_KEY_PREFIX_SMS_CODE + ":" + phone;
        String storedCode = (String) redisService.get(redisKey);
        if (StrUtil.isEmpty(storedCode) || !storedCode.equals(smsCode)) {
            Asserts.fail("短信验证码错误或已过期");
        }
        redisService.del(redisKey);
    }
}