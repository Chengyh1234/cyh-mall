package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.CmsSubjectMapper;
import com.macro.mall.model.CmsSubject;
import com.macro.mall.service.CmsSubjectService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品专题管理Service实现类
 * Created by macro on 2018/6/1.
 */
@Service
public class CmsSubjectServiceImpl extends ServiceImpl<CmsSubjectMapper, CmsSubject> implements CmsSubjectService {

    @Override
    public List<CmsSubject> listAll() {
        return this.list();
    }

    @Override
    public IPage<CmsSubject> list(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CmsSubject> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.like(CmsSubject::getTitle, keyword);
        }
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }
}
