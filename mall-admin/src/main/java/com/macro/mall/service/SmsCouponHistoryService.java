package com.macro.mall.service;

import com.baomidou.mybatisplus.spring.service.IService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.model.SmsCouponHistory;

/**
 * 优惠券领取记录管理Service
 * Created by macro on 2018/11/6.
 */
public interface SmsCouponHistoryService extends IService<SmsCouponHistory> {
    /**
     * 分页查询优惠券领取记录
     * @param couponId 优惠券id
     * @param useStatus 使用状态
     * @param orderSn 使用订单号码
     */
    IPage<SmsCouponHistory> list(Long couponId, Integer useStatus, String orderSn, Integer pageSize, Integer pageNum);
}
