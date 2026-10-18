package com.macro.mall.dao;

import com.macro.mall.dto.HomeStatisticResult;
import com.macro.mall.dto.OrderTrendResult;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页统计查询自定义Dao
 * Created by macro on 2026/10/18.
 */
public interface HomeDao {
    /**
     * 统计指定日期区间内的订单数
     */
    Long countOrderByTimeRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 统计指定日期区间内的销售总额
     */
    BigDecimal sumOrderAmountByTimeRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 统计指定状态的订单数（排除已删除订单）
     */
    Long countOrderByStatus(@Param("status") Integer status);

    /**
     * 统计指定状态的退货申请数
     */
    Long countReturnApplyByStatus(@Param("status") Integer status);

    /**
     * 统计即将到期的广告位数量
     */
    Long countExpiringAdvertise();

    /**
     * 商品总览统计
     */
    HomeStatisticResult countProductOverview();

    /**
     * 用户总览统计
     *
     * @param todayStart     今日起始日期（yyyy-MM-dd）
     * @param yesterdayStart 昨日起始日期（yyyy-MM-dd）
     * @param monthStart     本月起始日期（yyyy-MM-dd）
     */
    HomeStatisticResult countMemberOverview(@Param("todayStart") String todayStart,
                                            @Param("yesterdayStart") String yesterdayStart,
                                            @Param("monthStart") String monthStart);

    /**
     * 按天统计指定日期区间内的订单趋势
     */
    List<OrderTrendResult> listOrderTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
