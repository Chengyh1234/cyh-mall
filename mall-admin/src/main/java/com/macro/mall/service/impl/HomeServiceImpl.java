package com.macro.mall.service.impl;

import com.macro.mall.dao.HomeDao;
import com.macro.mall.dto.HomeStatisticResult;
import com.macro.mall.dto.OrderTrendResult;
import com.macro.mall.service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 首页统计Service实现类
 * Created by macro on 2026/10/18.
 */
@Service
public class HomeServiceImpl implements HomeService {
    @Autowired
    private HomeDao homeDao;

    @Override
    public HomeStatisticResult index() {
        HomeStatisticResult result = new HomeStatisticResult();
        LocalDate today = LocalDate.now();
        // 关键时间节点
        String todayStr = today.toString();
        String yesterdayStr = today.minusDays(1).toString();
        String monthStartStr = today.withDayOfMonth(1).toString();
        String lastMonthStartStr = today.withDayOfMonth(1).minusMonths(1).toString();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String mondayStr = monday.toString();
        String lastMondayStr = monday.minusDays(7).toString();
        // 商品总览
        HomeStatisticResult productOverview = homeDao.countProductOverview();
        // 用户总览
        HomeStatisticResult memberOverview = homeDao.countMemberOverview(todayStr, yesterdayStr, monthStartStr);

        // 订单信息
        result.setTodayOrderCount(homeDao.countOrderByTimeRange(todayStr, todayStr));
        result.setTodayOrderAmount(homeDao.sumOrderAmountByTimeRange(todayStr, todayStr));
        result.setYesterdayOrderAmount(homeDao.sumOrderAmountByTimeRange(yesterdayStr, yesterdayStr));

        // 待处理事务
        result.setUnPayCount(homeDao.countOrderByStatus(0));
        result.setFinishCount(homeDao.countOrderByStatus(3));
        result.setUnReceiveCount(homeDao.countOrderByStatus(2));
        result.setUnDeliverCount(homeDao.countOrderByStatus(1));
        result.setNewStockCount(productOverview.getLowStockCount());
        result.setReturnApplyPendingCount(homeDao.countReturnApplyByStatus(0));
        result.setShipCount(homeDao.countOrderByStatus(2));
        result.setReturningCount(homeDao.countReturnApplyByStatus(1));
        result.setAdvertiseCount(homeDao.countExpiringAdvertise());

        // 商品总览
        result.setOffShelfCount(productOverview.getOffShelfCount());
        result.setOnShelfCount(productOverview.getOnShelfCount());
        result.setLowStockCount(productOverview.getLowStockCount());
        result.setProductCount(productOverview.getProductCount());

        // 用户总览
        result.setMemberTodayAdd(memberOverview.getMemberTodayAdd());
        result.setMemberYesterdayAdd(memberOverview.getMemberYesterdayAdd());
        result.setMemberMonthAdd(memberOverview.getMemberMonthAdd());
        result.setMemberCount(memberOverview.getMemberCount());

        // 订单统计
        Long monthOrderCount = homeDao.countOrderByTimeRange(monthStartStr, todayStr);
        Long lastMonthOrderCount = homeDao.countOrderByTimeRange(lastMonthStartStr, today.minusMonths(1).toString());
        Long weekOrderCount = homeDao.countOrderByTimeRange(mondayStr, todayStr);
        Long lastWeekOrderCount = homeDao.countOrderByTimeRange(lastMondayStr, monday.minusDays(1).toString());
        BigDecimal monthOrderAmount = homeDao.sumOrderAmountByTimeRange(monthStartStr, todayStr);
        BigDecimal lastMonthOrderAmount = homeDao.sumOrderAmountByTimeRange(lastMonthStartStr, today.minusMonths(1).toString());
        BigDecimal weekOrderAmount = homeDao.sumOrderAmountByTimeRange(mondayStr, todayStr);
        BigDecimal lastWeekOrderAmount = homeDao.sumOrderAmountByTimeRange(lastMondayStr, monday.minusDays(1).toString());

        result.setMonthOrderCount(monthOrderCount);
        result.setWeekOrderCount(weekOrderCount);
        result.setMonthOrderAmount(monthOrderAmount);
        result.setWeekOrderAmount(weekOrderAmount);
        result.setMonthOrderCountGrowth(calcGrowth(monthOrderCount, lastMonthOrderCount));
        result.setWeekOrderCountGrowth(calcGrowth(weekOrderCount, lastWeekOrderCount));
        result.setMonthOrderAmountGrowth(calcGrowth(monthOrderAmount, lastMonthOrderAmount));
        result.setWeekOrderAmountGrowth(calcGrowth(weekOrderAmount, lastWeekOrderAmount));
        return result;
    }

    @Override
    public List<OrderTrendResult> listOrderTrend(String startDate, String endDate) {
        return homeDao.listOrderTrend(startDate, endDate);
    }

    /**
     * 计算环比增长率（%），上期为0时本期有增长则按100%计算
     */
    private Long calcGrowth(Long current, Long last) {
        if (last == null || last == 0) {
            return (current != null && current > 0) ? 100L : 0L;
        }
        return (current - last) * 100 / last;
    }

    /**
     * 计算销售金额环比增长率（%），上期为0时本期有增长则按100%计算
     */
    private Long calcGrowth(BigDecimal current, BigDecimal last) {
        if (last == null || last.compareTo(BigDecimal.ZERO) == 0) {
            return (current != null && current.compareTo(BigDecimal.ZERO) > 0) ? 100L : 0L;
        }
        return current.subtract(last)
                .multiply(BigDecimal.valueOf(100))
                .divide(last, 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
