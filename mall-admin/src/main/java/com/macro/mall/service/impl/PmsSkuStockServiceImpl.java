package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dao.PmsSkuStockDao;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.service.PmsSkuStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品SKU库存管理Service实现类
 * Created by macro on 2018/4/27.
 */
@Service
public class PmsSkuStockServiceImpl extends ServiceImpl<PmsSkuStockMapper, PmsSkuStock> implements PmsSkuStockService {
    @Autowired
    private PmsSkuStockDao skuStockDao;

    @Override
    public List<PmsSkuStock> getList(Long pid, String keyword) {
        LambdaQueryWrapper<PmsSkuStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsSkuStock::getProductId, pid);
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.like(PmsSkuStock::getSkuCode, keyword);
        }
        return this.list(wrapper);
    }

    @Override
    public int update(Long pid, List<PmsSkuStock> skuStockList) {
        List<PmsSkuStock> filterSkuList = skuStockList.stream()
                .filter(item -> pid.equals(item.getProductId()))
                .collect(Collectors.toList());
        return skuStockDao.replaceList(filterSkuList);
    }
}