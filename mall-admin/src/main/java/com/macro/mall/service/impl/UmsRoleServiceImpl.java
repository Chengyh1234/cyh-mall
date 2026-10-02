package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dao.UmsRoleDao;
import com.macro.mall.mapper.UmsRoleMapper;
import com.macro.mall.mapper.UmsRoleMenuRelationMapper;
import com.macro.mall.mapper.UmsRoleResourceRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台角色管理Service实现类
 * Created by macro on 2018/9/30.
 */
@Service
public class UmsRoleServiceImpl extends ServiceImpl<UmsRoleMapper, UmsRole> implements UmsRoleService {
    @Autowired
    private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Autowired
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Autowired
    private UmsRoleDao roleDao;
    @Autowired
    private UmsAdminCacheService adminCacheService;
    @Override
    public int create(UmsRole role) {
        role.setCreateTime(LocalDateTime.now());
        role.setAdminCount(0);
        role.setSort(0);
        return baseMapper.insert(role);
    }

    @Override
    public int update(Long id, UmsRole role) {
        role.setId(id);
        return baseMapper.updateById(role);
    }

    @Override
    public int delete(List<Long> ids) {
        int count = baseMapper.delete(new LambdaQueryWrapper<UmsRole>().in(UmsRole::getId, ids));
        adminCacheService.delResourceListByRoleIds(ids);
        return count;
    }

    @Override
    public List<UmsRole> list() {
        return this.list(new LambdaQueryWrapper<>());
    }

    @Override
    public IPage<UmsRole> list(String keyword, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<UmsRole> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.like(UmsRole::getName, keyword);
        }
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<UmsMenu> getMenuList(Long adminId) {
        return roleDao.getMenuList(adminId);
    }

    @Override
    public List<UmsMenu> listMenu(Long roleId) {
        return roleDao.getMenuListByRoleId(roleId);
    }

    @Override
    public List<UmsResource> listResource(Long roleId) {
        return roleDao.getResourceListByRoleId(roleId);
    }

    @Override
    public int allocMenu(Long roleId, List<Long> menuIds) {
        //先删除原有关系
        roleMenuRelationMapper.delete(new LambdaQueryWrapper<UmsRoleMenuRelation>().eq(UmsRoleMenuRelation::getRoleId, roleId));
        //批量插入新关系
        for (Long menuId : menuIds) {
            UmsRoleMenuRelation relation = new UmsRoleMenuRelation();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuRelationMapper.insert(relation);
        }
        return menuIds.size();
    }

    @Override
    public int allocResource(Long roleId, List<Long> resourceIds) {
        //先删除原有关系
        roleResourceRelationMapper.delete(new LambdaQueryWrapper<UmsRoleResourceRelation>().eq(UmsRoleResourceRelation::getRoleId, roleId));
        //批量插入新关系
        for (Long resourceId : resourceIds) {
            UmsRoleResourceRelation relation = new UmsRoleResourceRelation();
            relation.setRoleId(roleId);
            relation.setResourceId(resourceId);
            roleResourceRelationMapper.insert(relation);
        }
        adminCacheService.delResourceListByRole(roleId);
        return resourceIds.size();
    }
}
