package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.portal.domain.PmsCommentDetail;
import com.macro.mall.portal.domain.PmsCommentParam;
import com.macro.mall.portal.domain.PmsCommentReplayParam;
import com.macro.mall.portal.service.PmsPortalCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 前台商品评论管理Controller
 * Created by cyh on 2026/10/3.
 */
@Controller
@Tag(name = "PmsPortalCommentController", description = "前台商品评论管理")
@RequestMapping("/comment")
public class PmsPortalCommentController {

    @Autowired
    private PmsPortalCommentService commentService;

    @Operation(summary = "分页获取商品评论列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsCommentDetail>> list(@RequestParam Long productId,
                                                           @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                           @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        IPage<PmsCommentDetail> commentPage = commentService.list(productId, pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(commentPage));
    }

    @Operation(summary = "分页获取我的评论")
    @RequestMapping(value = "/my", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsCommentDetail>> my(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                         @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        IPage<PmsCommentDetail> commentPage = commentService.my(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(commentPage));
    }

    @Operation(summary = "获取评论详情")
    @RequestMapping(value = "/detail/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<PmsCommentDetail> detail(@PathVariable Long id) {
        PmsCommentDetail detail = commentService.detail(id);
        if (detail == null) {
            return CommonResult.failed("评论不存在");
        }
        return CommonResult.success(detail);
    }

    @Operation(summary = "发表评论")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult add(@RequestBody PmsCommentParam commentParam) {
        // 购买资格校验：仅已完成且未评价过的订单用户可评价
        String error = commentService.checkCommentPermission(commentParam);
        if (error != null) {
            return CommonResult.failed(error);
        }
        int count = commentService.add(commentParam);
        if (count > 0) {
            return CommonResult.success(null, "评论成功");
        }
        return CommonResult.failed("评论失败");
    }

    @Operation(summary = "回复评论")
    @RequestMapping(value = "/replay/add", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult replay(@RequestBody PmsCommentReplayParam replayParam) {
        int count = commentService.replay(replayParam);
        if (count > 0) {
            return CommonResult.success(null, "回复成功");
        }
        return CommonResult.failed("回复失败");
    }

    @Operation(summary = "点赞评论")
    @RequestMapping(value = "/like/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult like(@PathVariable Long id) {
        int count = commentService.like(id);
        if (count > 0) {
            return CommonResult.success(null, "点赞成功");
        }
        return CommonResult.failed("点赞失败");
    }
}
