package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dto.HomeStatisticResult;
import com.macro.mall.dto.OrderTrendResult;
import com.macro.mall.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 首页统计管理Controller
 * Created by macro on 2026/10/18.
 */
@Controller
@Tag(name = "HomeController", description = "首页统计管理")
@RequestMapping("/home/statistic")
public class HomeController {
    @Autowired
    private HomeService homeService;

    @Operation(summary = "获取首页统计数据：订单信息、待处理事务、商品总览、用户总览、订单统计")
    @RequestMapping(value = "/index", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<HomeStatisticResult> index() {
        return CommonResult.success(homeService.index());
    }

    @Operation(summary = "获取指定日期区间内的订单趋势（按天分组）")
    @RequestMapping(value = "/orderTrend", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<OrderTrendResult>> orderTrend(@RequestParam(value = "startDate", defaultValue = "") String startDate,
                                                           @RequestParam(value = "endDate", defaultValue = "") String endDate) {
        return CommonResult.success(homeService.listOrderTrend(startDate, endDate));
    }
}
