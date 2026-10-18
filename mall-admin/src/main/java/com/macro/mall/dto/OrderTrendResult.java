package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 订单趋势统计（按天分组）
 * Created by macro on 2026/10/18.
 */
@Getter
@Setter
public class OrderTrendResult {
    @Schema(title = "日期（yyyy-MM-dd）")
    private String date;
    @Schema(title = "订单数量")
    private Long orderCount;
    @Schema(title = "订单金额")
    private BigDecimal orderAmount;
}
