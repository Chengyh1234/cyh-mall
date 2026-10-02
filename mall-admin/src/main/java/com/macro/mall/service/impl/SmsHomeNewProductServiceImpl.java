package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsHomeNewProductMapper;
import com.macro.mall.model.SmsHomeNewProduct;
import com.macro.mall.service.SmsHomeNewProductService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 首页新品推荐管理Service实现类
 * Created by macro on 2018/11/6.
 */
@Service
public class SmsHomeNewProductServiceImpl extends ServiceImpl<SmsHomeNewProductMapper, SmsHomeNewProduct> implements SmsHomeNewProductService {
    @Override
    public int create(List<SmsHomeNewProduct> homeNewProductList) {
        for (SmsHomeNewProduct SmsHomeNewProduct : homeNewProductList) {
            SmsHomeNewProduct.setRecommendStatus(1);
            SmsHomeNewProduct.setSort(0);
            baseMapper.insert(SmsHomeNewProduct);
        }
        return homeNewProductList.size();
    }

    @Override
    public int updateSort(Long id, Integer sort) {
        SmsHomeNewProduct homeNewProduct = new SmsHomeNewProduct();
        homeNewProduct.setId(id);
        homeNewProduct.setSort(sort);
        return baseMapper.updateById(homeNewProduct);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<SmsHomeNewProduct>().in(SmsHomeNewProduct::getId, ids));
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        SmsHomeNewProduct record = new SmsHomeNewProduct();
        record.setRecommendStatus(recommendStatus);
        return baseMapper.update(record, new LambdaQueryWrapper<SmsHomeNewProduct>()
                .in(SmsHomeNewProduct::getId, ids));
    }

    @Override
    public IPage<SmsHomeNewProduct> list(String productName, Integer recommendStatus, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsHomeNewProduct> wrapper = new LambdaQueryWrapper<>();
        if(!StrUtil.isEmpty(productName)){
            wrapper.like(SmsHomeNewProduct::getProductName, productName);
        }
        if(recommendStatus!=null){
            wrapper.eq(SmsHomeNewProduct::getRecommendStatus, recommendStatus);
        }
        wrapper.orderByDesc(SmsHomeNewProduct::getSort);
        return this.page(new Page<>(pageNum,pageSize), wrapper);
    }
}