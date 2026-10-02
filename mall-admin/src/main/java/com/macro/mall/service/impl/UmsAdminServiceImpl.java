package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.bo.AdminUserDetails;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.util.RequestUtil;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.security.util.JwtTokenUtil;
import com.macro.mall.security.util.SpringUtil;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台用户管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class UmsAdminServiceImpl extends ServiceImpl<UmsAdminMapper, UmsAdmin> implements UmsAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsAdminServiceImpl.class);
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Autowired
    private UmsAdminLoginLogMapper loginLogMapper;

    @Override
    public UmsAdmin getAdminByUsername(String username) {
        //先从缓存中获取数据
        UmsAdmin admin = getCacheService().getAdmin(username);
        if (admin != null) return admin;
        //缓存中没有从数据库中获取
        List<UmsAdmin> adminList = this.list(new LambdaQueryWrapper<UmsAdmin>().eq(UmsAdmin::getUsername, username));
        if (adminList != null && adminList.size() > 0) {
            admin = adminList.get(0);
            //将数据库中的数据存入缓存中
            getCacheService().setAdmin(admin);
            return admin;
        }
        return null;
    }

    @Override
    public UmsAdmin register(UmsAdminParam umsAdminParam) {
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(umsAdminParam, umsAdmin);
        umsAdmin.setCreateTime(LocalDateTime.now());
        umsAdmin.setStatus(1);
        //查询是否有相同用户名的用户
        List<UmsAdmin> umsAdminList = this.list(new LambdaQueryWrapper<UmsAdmin>().eq(UmsAdmin::getUsername, umsAdmin.getUsername()));
        if (umsAdminList.size() > 0) {
            return null;
        }
        //将密码进行加密操作
        String encodePassword = passwordEncoder.encode(umsAdmin.getPassword());
        umsAdmin.setPassword(encodePassword);
        baseMapper.insert(umsAdmin);
        return umsAdmin;
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        //密码需要客户端加密后传递
        try {
            UserDetails userDetails = loadUserByUsername(username);
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                Asserts.fail("密码不正确");
            }
            if(!userDetails.isEnabled()){
                Asserts.fail("帐号已被禁用");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtTokenUtil.generateToken(userDetails);
//            updateLoginTimeByUsername(username);
            insertLoginLog(username);
        } catch (AuthenticationException e) {
            LOGGER.warn("登录异常:{}", e.getMessage());
        }
        return token;
    }

    /**
     * 添加登录记录
     * @param username 用户名
     */
    private void insertLoginLog(String username) {
        UmsAdmin admin = getAdminByUsername(username);
        if(admin==null) return;
        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();
        loginLog.setAdminId(admin.getId());
        loginLog.setCreateTime(LocalDateTime.now());
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        loginLog.setIp(RequestUtil.getRequestIp(request));
        loginLogMapper.insert(loginLog);
    }

    /**
     * 根据用户名修改登录时间
     */
    private void updateLoginTimeByUsername(String username) {
        UmsAdmin record = new UmsAdmin();
        record.setLoginTime(LocalDateTime.now());
        baseMapper.update(record, new LambdaQueryWrapper<UmsAdmin>().eq(UmsAdmin::getUsername, username));
    }

    @Override
    public String refreshToken(String oldToken) {
        return jwtTokenUtil.refreshHeadToken(oldToken);
    }

    @Override
    public UmsAdmin getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public IPage<UmsAdmin> list(String keyword, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<UmsAdmin> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.and(w -> w.like(UmsAdmin::getUsername, keyword)
                    .or()
                    .like(UmsAdmin::getNickName, keyword));
        }
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public int update(Long id, UmsAdmin admin) {
        admin.setId(id);
        UmsAdmin rawAdmin = this.getById(id);
        if(rawAdmin.getPassword().equals(admin.getPassword())){
            //与原加密密码相同的不需要修改
            admin.setPassword(null);
        }else{
            //与原加密密码不同的需要加密修改
            if(StrUtil.isEmpty(admin.getPassword())){
                admin.setPassword(null);
            }else{
                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
            }
        }
        int count = baseMapper.updateById(admin);
        getCacheService().delAdmin(id);
        return count;
    }

    @Override
    public int delete(Long id) {
        getCacheService().delAdmin(id);
        getCacheService().delResourceList(id);
        int count = baseMapper.deleteById(id);
        return count;
    }

    @Override
    public int updateRole(Long adminId, List<Long> roleIds) {
        int count = roleIds == null ? 0 : roleIds.size();
        //先删除原来的关系
        adminRoleRelationMapper.delete(new LambdaQueryWrapper<UmsAdminRoleRelation>().eq(UmsAdminRoleRelation::getAdminId, adminId));
        //建立新关系
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<UmsAdminRoleRelation> list = new ArrayList<>();
            for (Long roleId : roleIds) {
                UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();
                roleRelation.setAdminId(adminId);
                roleRelation.setRoleId(roleId);
                list.add(roleRelation);
            }
            adminRoleRelationDao.insertList(list);
        }
        getCacheService().delResourceList(adminId);
        return count;
    }

    @Override
    public List<UmsRole> getRoleList(Long adminId) {
        return adminRoleRelationDao.getRoleList(adminId);
    }

    @Override
    public List<UmsResource> getResourceList(Long adminId) {
        //先从缓存中获取数据
        List<UmsResource> resourceList = getCacheService().getResourceList(adminId);
        if(CollUtil.isNotEmpty(resourceList)){
            return  resourceList;
        }
        //缓存中没有从数据库中获取
        resourceList = adminRoleRelationDao.getResourceList(adminId);
        if(CollUtil.isNotEmpty(resourceList)){
            //将数据库中的数据存入缓存中
            getCacheService().setResourceList(adminId,resourceList);
        }
        return resourceList;
    }

    @Override
    public int updatePassword(UpdateAdminPasswordParam param) {
        if(StrUtil.isEmpty(param.getUsername())
                ||StrUtil.isEmpty(param.getOldPassword())
                ||StrUtil.isEmpty(param.getNewPassword())){
            return -1;
        }
        List<UmsAdmin> adminList = this.list(new LambdaQueryWrapper<UmsAdmin>().eq(UmsAdmin::getUsername, param.getUsername()));
        if(CollUtil.isEmpty(adminList)){
            return -2;
        }
        UmsAdmin umsAdmin = adminList.get(0);
        if(!passwordEncoder.matches(param.getOldPassword(),umsAdmin.getPassword())){
            return -3;
        }
        umsAdmin.setPassword(passwordEncoder.encode(param.getNewPassword()));
        baseMapper.updateById(umsAdmin);
        getCacheService().delAdmin(umsAdmin.getId());
        return 1;
    }

    @Override
    public UserDetails loadUserByUsername(String username){
        //获取用户信息
        UmsAdmin admin = getAdminByUsername(username);
        if (admin != null) {
            List<UmsResource> resourceList = getResourceList(admin.getId());
            return new AdminUserDetails(admin,resourceList);
        }
        throw new UsernameNotFoundException("用户名或密码错误");
    }

    @Override
    public UmsAdminCacheService getCacheService() {
        return SpringUtil.getBean(UmsAdminCacheService.class);
    }

    @Override
    public void logout(String username) {
        //清空缓存中的用户相关数据
        UmsAdmin admin = getCacheService().getAdmin(username);
        getCacheService().delAdmin(admin.getId());
        getCacheService().delResourceList(admin.getId());
    }
}
