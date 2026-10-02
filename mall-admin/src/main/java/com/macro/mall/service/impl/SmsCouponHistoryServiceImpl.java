package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.service.SmsCouponHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 优惠券领取记录管理Service实现类
 * Created by macro on 2018/11/6.
 */
@Service
public class SmsCouponHistoryServiceImpl extends ServiceImpl<SmsCouponHistoryMapper, SmsCouponHistory> implements SmsCouponHistoryService {
    @Override
    public IPage<SmsCouponHistory> list(Long couponId, Integer useStatus, String orderSn, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsCouponHistory> wrapper = new LambdaQueryWrapper<>();
        if(couponId!=null){
            wrapper.eq(SmsCouponHistory::getCouponId, couponId);
        }
        if(useStatus!=null){
            wrapper.eq(SmsCouponHistory::getUseStatus, useStatus);
        }
        if(!StrUtil.isEmpty(orderSn)){
            wrapper.eq(SmsCouponHistory::getOrderSn, orderSn);
        }
        return this.page(new Page<>(pageNum,pageSize), wrapper);
    }
}