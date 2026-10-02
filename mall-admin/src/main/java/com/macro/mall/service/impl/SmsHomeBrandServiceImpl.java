package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsHomeBrandMapper;
import com.macro.mall.model.SmsHomeBrand;
import com.macro.mall.service.SmsHomeBrandService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 首页品牌管理Service实现类
 * Created by macro on 2018/11/6.
 */
@Service
public class SmsHomeBrandServiceImpl extends ServiceImpl<SmsHomeBrandMapper, SmsHomeBrand> implements SmsHomeBrandService {
    @Override
    public int create(List<SmsHomeBrand> homeBrandList) {
        for (SmsHomeBrand smsHomeBrand : homeBrandList) {
            smsHomeBrand.setRecommendStatus(1);
            smsHomeBrand.setSort(0);
            baseMapper.insert(smsHomeBrand);
        }
        return homeBrandList.size();
    }

    @Override
    public int updateSort(Long id, Integer sort) {
        SmsHomeBrand homeBrand = new SmsHomeBrand();
        homeBrand.setId(id);
        homeBrand.setSort(sort);
        return baseMapper.updateById(homeBrand);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<SmsHomeBrand>().in(SmsHomeBrand::getId, ids));
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        SmsHomeBrand record = new SmsHomeBrand();
        record.setRecommendStatus(recommendStatus);
        return baseMapper.update(record, new LambdaQueryWrapper<SmsHomeBrand>()
                .in(SmsHomeBrand::getId, ids));
    }

    @Override
    public IPage<SmsHomeBrand> list(String brandName, Integer recommendStatus, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsHomeBrand> wrapper = new LambdaQueryWrapper<>();
        if(!StrUtil.isEmpty(brandName)){
            wrapper.like(SmsHomeBrand::getBrandName, brandName);
        }
        if(recommendStatus!=null){
            wrapper.eq(SmsHomeBrand::getRecommendStatus, recommendStatus);
        }
        wrapper.orderByDesc(SmsHomeBrand::getSort);
        return this.page(new Page<>(pageNum,pageSize), wrapper);
    }
}