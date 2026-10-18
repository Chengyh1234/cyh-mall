package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 首页统计信息
 * Created by macro on 2026/10/18.
 */
@Getter
@Setter
public class HomeStatisticResult {
    @Schema(title = "今日订单总数")
    private Long todayOrderCount;
    @Schema(title = "今日销售总额")
    private BigDecimal todayOrderAmount;
    @Schema(title = "昨日销售总额")
    private BigDecimal yesterdayOrderAmount;

    @Schema(title = "待付款订单数")
    private Long unPayCount;
    @Schema(title = "已完成订单数")
    private Long finishCount;
    @Schema(title = "待确认收货订单数")
    private Long unReceiveCount;
    @Schema(title = "待发货订单数")
    private Long unDeliverCount;
    @Schema(title = "新缺货登记数")
    private Long newStockCount;
    @Schema(title = "待处理退款申请数")
    private Long returnApplyPendingCount;
    @Schema(title = "已发货订单数")
    private Long shipCount;
    @Schema(title = "待处理退货订单数")
    private Long returningCount;
    @Schema(title = "广告位即将到期数量")
    private Long advertiseCount;

    @Schema(title = "商品总览：已下架")
    private Long offShelfCount;
    @Schema(title = "商品总览：已上架")
    private Long onShelfCount;
    @Schema(title = "商品总览：库存紧张")
    private Long lowStockCount;
    @Schema(title = "商品总览：全部商品")
    private Long productCount;

    @Schema(title = "用户总览：今日新增")
    private Long memberTodayAdd;
    @Schema(title = "用户总览：昨日新增")
    private Long memberYesterdayAdd;
    @Schema(title = "用户总览：本月新增")
    private Long memberMonthAdd;
    @Schema(title = "用户总览：会员总数")
    private Long memberCount;

    @Schema(title = "订单统计：本月订单总数")
    private Long monthOrderCount;
    @Schema(title = "订单统计：本周订单总数")
    private Long weekOrderCount;
    @Schema(title = "订单统计：本月销售总额")
    private BigDecimal monthOrderAmount;
    @Schema(title = "订单统计：本周销售总额")
    private BigDecimal weekOrderAmount;
    @Schema(title = "订单统计：本月订单总数同比上月增长率(%)")
    private Long monthOrderCountGrowth;
    @Schema(title = "订单统计：本周订单总数同比上周增长率(%)")
    private Long weekOrderCountGrowth;
    @Schema(title = "订单统计：本月销售总额同比上月增长率(%)")
    private Long monthOrderAmountGrowth;
    @Schema(title = "订单统计：本周销售总额同比上周增长率(%)")
    private Long weekOrderAmountGrowth;
}
