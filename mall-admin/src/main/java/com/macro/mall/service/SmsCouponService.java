package com.macro.mall.service;

import com.baomidou.mybatisplus.spring.service.IService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.model.SmsCoupon;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 优惠券管理Service
 * Created by macro on 2018/8/28.
 */
public interface SmsCouponService extends IService<SmsCoupon> {
    /**
     * 添加优惠券
     */
    int create(SmsCouponParam couponParam);

    /**
     * 根据优惠券id删除优惠券
     */
    int delete(Long id);

    /**
     * 根据优惠券id更新优惠券信息
     */
    int update(Long id, SmsCouponParam couponParam);

    /**
     * 分页获取优惠券列表
     */
    IPage<SmsCoupon> list(String name, Integer type, Integer pageSize, Integer pageNum);

    /**
     * 获取优惠券详情
     * @param id 优惠券表id
     */
    SmsCouponParam getItem(Long id);
}
