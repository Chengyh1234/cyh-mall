package com.macro.mall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dto.UmsMessageParam;
import com.macro.mall.model.UmsMessage;
import com.macro.mall.service.UmsMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 消息管理Controller
 */
@Controller
@Tag(name = "UmsMessageController", description = "消息管理")
@RequestMapping("/message")
public class UmsMessageController {
    @Autowired
    private UmsMessageService messageService;

    /**
     * 发布消息：管理员指定消息类型、发送类型（0定向/1广播）、标题、内容；
     * 定向发送需在 memberIds 中指定接收会员ID列表
     *
     * @param param 消息发布参数（type、sendType、title、content、bizId、memberIds）
     * @return 消息主体影响行数
     */
    @Operation(summary = "发布消息（定向/广播）")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult create(@RequestBody UmsMessageParam param) {
        int count = messageService.create(param);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    /**
     * 分页查询已发布消息列表（按创建时间倒序）
     *
     * @param pageSize 每页数量
     * @param pageNum  页码
     * @return 已发布消息分页列表
     */
    @Operation(summary = "查询已发布消息列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<UmsMessage>> list(@RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize,
                                                     @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        IPage<UmsMessage> messageList = messageService.list(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(messageList));
    }
}
