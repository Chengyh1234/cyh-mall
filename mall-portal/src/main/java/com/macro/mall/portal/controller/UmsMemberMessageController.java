package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.PortalMessageDetail;
import com.macro.mall.portal.service.UmsMemberMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 会员消息管理Controller
 */
@Controller
@Tag(name = "UmsMemberMessageController", description = "会员消息管理")
@RequestMapping("/member/message")
public class UmsMemberMessageController {
    @Autowired
    private UmsMemberMessageService memberMessageService;

    @Operation(summary = "获取会员消息列表")
    @Parameter(name = "type", description = "消息类型:1->系统通知；2->订单通知；3->营销通知；为空查询全部",
            in = ParameterIn.QUERY, schema = @Schema(type = "integer", allowableValues = {"1", "2", "3"}))
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PortalMessageDetail>> list(@RequestParam(value = "type", required = false) Integer type,
                                                              @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                              @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        CommonPage<PortalMessageDetail> messagePage = memberMessageService.list(type, pageNum, pageSize);
        return CommonResult.success(messagePage);
    }

    @Operation(summary = "获取会员未读消息数量")
    @RequestMapping(value = "/unread-count", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Integer> unreadCount() {
        Integer count = memberMessageService.unreadCount();
        return CommonResult.success(count);
    }

    @Operation(summary = "将指定消息标记为已读")
    @RequestMapping(value = "/read/{messageId}", method = RequestMethod.PUT)
    @ResponseBody
    public CommonResult read(@PathVariable Long messageId) {
        memberMessageService.read(messageId);
        return CommonResult.success(null, "操作成功");
    }

    @Operation(summary = "将当前用户指定类型（或全部）消息标记为已读")
    @Parameter(name = "type", description = "消息类型:1->系统通知；2->订单通知；3->营销通知；为空标记全部已读",
            in = ParameterIn.QUERY, schema = @Schema(type = "integer", allowableValues = {"1", "2", "3"}))
    @RequestMapping(value = "/read-all", method = RequestMethod.PUT)
    @ResponseBody
    public CommonResult readAll(@RequestParam(value = "type", required = false) Integer type) {
        memberMessageService.readAll(type);
        return CommonResult.success(null, "操作成功");
    }
}
