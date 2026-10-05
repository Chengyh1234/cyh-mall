package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dto.PmsBrandParam;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.service.PmsBrandService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品品牌管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class PmsBrandServiceImpl extends ServiceImpl<PmsBrandMapper, PmsBrand> implements PmsBrandService {
    @Autowired
    private PmsProductMapper productMapper;

    @Override
    public List<PmsBrand> listAllBrand() {
        return this.list();
    }

    @Override
    public int createBrand(PmsBrandParam pmsBrandParam) {
        PmsBrand pmsBrand = new PmsBrand();
        BeanUtils.copyProperties(pmsBrandParam, pmsBrand);
        //如果创建时首字母为空，取名称的第一个为首字母
        if (StrUtil.isEmpty(pmsBrand.getFirstLetter())) {
            pmsBrand.setFirstLetter(pmsBrand.getName().substring(0, 1));
        }
        return baseMapper.insert(pmsBrand);
    }

    @Transactional
    @Override
    public int updateBrand(Long id, PmsBrandParam pmsBrandParam) {
        PmsBrand pmsBrand = new PmsBrand();
        BeanUtils.copyProperties(pmsBrandParam, pmsBrand);
        pmsBrand.setId(id);
        //如果创建时首字母为空，取名称的第一个为首字母
        if (StrUtil.isEmpty(pmsBrand.getFirstLetter())) {
            pmsBrand.setFirstLetter(pmsBrand.getName().substring(0, 1));
        }
        //更新品牌时要更新商品中的品牌名称
        PmsProduct product = new PmsProduct();
        product.setBrandName(pmsBrand.getName());
        productMapper.update(product, new LambdaQueryWrapper<PmsProduct>()
                .eq(PmsProduct::getBrandId, id));
        return baseMapper.updateById(pmsBrand);
    }

    @Override
    public int deleteBrand(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public int deleteBrand(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<PmsBrand>().in(PmsBrand::getId, ids));
    }

    @Override
    public IPage<PmsBrand> listBrand(String keyword, Integer showStatus, int pageNum, int pageSize) {
        LambdaQueryWrapper<PmsBrand> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.like(PmsBrand::getName, keyword);
        }
        if (showStatus != null) {
            wrapper.eq(PmsBrand::getShowStatus, showStatus);
        }
        wrapper.orderByDesc(PmsBrand::getSort);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public PmsBrand getBrand(Long id) {
        return this.getById(id);
    }

    @Override
    public int updateShowStatus(List<Long> ids, Integer showStatus) {
        PmsBrand pmsBrand = new PmsBrand();
        pmsBrand.setShowStatus(showStatus);
        return baseMapper.update(pmsBrand, new LambdaQueryWrapper<PmsBrand>()
                .in(PmsBrand::getId, ids));
    }

    @Override
    public int updateFactoryStatus(List<Long> ids, Integer factoryStatus) {
        PmsBrand pmsBrand = new PmsBrand();
        pmsBrand.setFactoryStatus(factoryStatus);
        return baseMapper.update(pmsBrand, new LambdaQueryWrapper<PmsBrand>()
                .in(PmsBrand::getId, ids));
    }
}