package com.macro.mall.service;

import com.macro.mall.dto.HomeStatisticResult;
import com.macro.mall.dto.OrderTrendResult;

import java.util.List;

/**
 * 首页统计Service
 * Created by macro on 2026/10/18.
 */
public interface HomeService {
    /**
     * 获取首页统计数据：订单信息、待处理事务、商品总览、用户总览、订单统计
     */
    HomeStatisticResult index();

    /**
     * 获取指定日期区间内的订单趋势（按天分组）
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     */
    List<OrderTrendResult> listOrderTrend(String startDate, String endDate);
}
