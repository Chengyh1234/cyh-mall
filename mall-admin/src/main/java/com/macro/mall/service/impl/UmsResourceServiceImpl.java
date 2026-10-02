package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.UmsResourceMapper;
import com.macro.mall.model.UmsResource;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台资源管理Service实现类
 * Created by macro on 2020/2/2.
 */
@Service
public class UmsResourceServiceImpl extends ServiceImpl<UmsResourceMapper, UmsResource> implements UmsResourceService {
    @Autowired
    private UmsAdminCacheService adminCacheService;
    @Override
    public int create(UmsResource umsResource) {
        umsResource.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(umsResource);
    }

    @Override
    public int update(Long id, UmsResource umsResource) {
        umsResource.setId(id);
        int count = baseMapper.updateById(umsResource);
        adminCacheService.delResourceListByResource(id);
        return count;
    }

    @Override
    public UmsResource getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public int delete(Long id) {
        int count = baseMapper.deleteById(id);
        adminCacheService.delResourceListByResource(id);
        return count;
    }

    @Override
    public IPage<UmsResource> list(Long categoryId, String nameKeyword, String urlKeyword, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<UmsResource> wrapper = new LambdaQueryWrapper<>();
        if(categoryId!=null){
            wrapper.eq(UmsResource::getCategoryId, categoryId);
        }
        if(StrUtil.isNotEmpty(nameKeyword)){
            wrapper.like(UmsResource::getName, nameKeyword);
        }
        if(StrUtil.isNotEmpty(urlKeyword)){
            wrapper.like(UmsResource::getUrl, urlKeyword);
        }
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<UmsResource> listAll() {
        return this.list(new LambdaQueryWrapper<>());
    }
}
