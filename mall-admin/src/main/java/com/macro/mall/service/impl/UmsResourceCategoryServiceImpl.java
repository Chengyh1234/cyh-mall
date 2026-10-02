package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.UmsResourceCategoryMapper;
import com.macro.mall.model.UmsResourceCategory;
import com.macro.mall.service.UmsResourceCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台资源分类管理Service实现类
 * Created by macro on 2020/2/5.
 */
@Service
public class UmsResourceCategoryServiceImpl extends ServiceImpl<UmsResourceCategoryMapper, UmsResourceCategory> implements UmsResourceCategoryService {

    @Override
    public List<UmsResourceCategory> listAll() {
        return this.list(new LambdaQueryWrapper<UmsResourceCategory>().orderByDesc(UmsResourceCategory::getSort));
    }

    @Override
    public int create(UmsResourceCategory umsResourceCategory) {
        umsResourceCategory.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(umsResourceCategory);
    }

    @Override
    public int update(Long id, UmsResourceCategory umsResourceCategory) {
        umsResourceCategory.setId(id);
        return baseMapper.updateById(umsResourceCategory);
    }

    @Override
    public int delete(Long id) {
        return baseMapper.deleteById(id);
    }
}
