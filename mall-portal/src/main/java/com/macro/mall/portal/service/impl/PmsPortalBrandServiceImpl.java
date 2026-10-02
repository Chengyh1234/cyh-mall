package com.macro.mall.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.service.PmsPortalBrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前台品牌管理Service实现类
 * Created by macro on 2020/5/15.
 */
@Service
public class PmsPortalBrandServiceImpl extends ServiceImpl<PmsBrandMapper, PmsBrand> implements PmsPortalBrandService {
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private PmsProductMapper productMapper;

    @Override
    public List<PmsBrand> recommendList(Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return homeDao.getRecommendBrandList(offset, pageSize);
    }

    @Override
    public PmsBrand detail(Long brandId) {
        return baseMapper.selectById(brandId);
    }

    @Override
    public CommonPage<PmsProduct> productList(Long brandId, Integer pageNum, Integer pageSize) {
        IPage<PmsProduct> page = productMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PmsProduct>()
                        .eq(PmsProduct::getDeleteStatus, 0)
                        .eq(PmsProduct::getPublishStatus, 1)
                        .eq(PmsProduct::getBrandId, brandId));
        return CommonPage.restPage(page);
    }
}