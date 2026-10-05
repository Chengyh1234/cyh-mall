package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsHomeRecommendProductMapper;
import com.macro.mall.model.SmsHomeRecommendProduct;
import com.macro.mall.service.SmsHomeRecommendProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首页人气推荐管理Service实现类
 * Created by macro on 2018/11/7.
 */
@Service
public class SmsHomeRecommendProductServiceImpl extends ServiceImpl<SmsHomeRecommendProductMapper, SmsHomeRecommendProduct> implements SmsHomeRecommendProductService {
    @Transactional
    @Override
    public int create(List<SmsHomeRecommendProduct> homeRecommendProductList) {
        for (SmsHomeRecommendProduct recommendProduct : homeRecommendProductList) {
            recommendProduct.setRecommendStatus(1);
            recommendProduct.setSort(0);
            baseMapper.insert(recommendProduct);
        }
        return homeRecommendProductList.size();
    }

    @Override
    public int updateSort(Long id, Integer sort) {
        SmsHomeRecommendProduct recommendProduct = new SmsHomeRecommendProduct();
        recommendProduct.setId(id);
        recommendProduct.setSort(sort);
        return baseMapper.updateById(recommendProduct);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<SmsHomeRecommendProduct>().in(SmsHomeRecommendProduct::getId, ids));
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        SmsHomeRecommendProduct record = new SmsHomeRecommendProduct();
        record.setRecommendStatus(recommendStatus);
        return baseMapper.update(record, new LambdaQueryWrapper<SmsHomeRecommendProduct>()
                .in(SmsHomeRecommendProduct::getId, ids));
    }

    @Override
    public IPage<SmsHomeRecommendProduct> list(String productName, Integer recommendStatus, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsHomeRecommendProduct> wrapper = new LambdaQueryWrapper<>();
        if(!StrUtil.isEmpty(productName)){
            wrapper.like(SmsHomeRecommendProduct::getProductName, productName);
        }
        if(recommendStatus!=null){
            wrapper.eq(SmsHomeRecommendProduct::getRecommendStatus, recommendStatus);
        }
        wrapper.orderByDesc(SmsHomeRecommendProduct::getSort);
        return this.page(new Page<>(pageNum,pageSize), wrapper);
    }
}