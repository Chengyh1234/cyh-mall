package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.model.UmsMemberLevel;
import com.macro.mall.service.UmsMemberLevelService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会员等级管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class UmsMemberLevelServiceImpl extends ServiceImpl<UmsMemberLevelMapper, UmsMemberLevel> implements UmsMemberLevelService{
    @Override
    public List<UmsMemberLevel> list(Integer defaultStatus) {
        return this.list(new LambdaQueryWrapper<UmsMemberLevel>().eq(UmsMemberLevel::getDefaultStatus, defaultStatus));
    }
}
