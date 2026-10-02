package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsHomeRecommendSubjectMapper;
import com.macro.mall.model.SmsHomeRecommendSubject;
import com.macro.mall.service.SmsHomeRecommendSubjectService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 首页专题推荐管理Service实现类
 * Created by macro on 2018/11/7.
 */
@Service
public class SmsHomeRecommendSubjectServiceImpl extends ServiceImpl<SmsHomeRecommendSubjectMapper, SmsHomeRecommendSubject> implements SmsHomeRecommendSubjectService {
    @Override
    public int create(List<SmsHomeRecommendSubject> recommendSubjectList) {
        for (SmsHomeRecommendSubject recommendSubject : recommendSubjectList) {
            recommendSubject.setRecommendStatus(1);
            recommendSubject.setSort(0);
            baseMapper.insert(recommendSubject);
        }
        return recommendSubjectList.size();
    }

    @Override
    public int updateSort(Long id, Integer sort) {
        SmsHomeRecommendSubject recommendSubject = new SmsHomeRecommendSubject();
        recommendSubject.setId(id);
        recommendSubject.setSort(sort);
        return baseMapper.updateById(recommendSubject);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<SmsHomeRecommendSubject>().in(SmsHomeRecommendSubject::getId, ids));
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        SmsHomeRecommendSubject record = new SmsHomeRecommendSubject();
        record.setRecommendStatus(recommendStatus);
        return baseMapper.update(record, new LambdaQueryWrapper<SmsHomeRecommendSubject>()
                .in(SmsHomeRecommendSubject::getId, ids));
    }

    @Override
    public IPage<SmsHomeRecommendSubject> list(String subjectName, Integer recommendStatus, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsHomeRecommendSubject> wrapper = new LambdaQueryWrapper<>();
        if(!StrUtil.isEmpty(subjectName)){
            wrapper.like(SmsHomeRecommendSubject::getSubjectName, subjectName);
        }
        if(recommendStatus!=null){
            wrapper.eq(SmsHomeRecommendSubject::getRecommendStatus, recommendStatus);
        }
        wrapper.orderByDesc(SmsHomeRecommendSubject::getSort);
        return this.page(new Page<>(pageNum,pageSize), wrapper);
    }
}