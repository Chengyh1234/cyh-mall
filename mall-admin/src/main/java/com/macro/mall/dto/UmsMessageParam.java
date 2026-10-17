package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 消息发布参数
 */
@Getter
@Setter
public class UmsMessageParam {
    @Schema(title = "消息类型：1->系统通知；2->订单通知；3->营销通知")
    private Integer type;
    @Schema(title = "发送类型：0->定向；1->广播")
    private Integer sendType;
    @Schema(title = "消息标题")
    private String title;
    @Schema(title = "消息内容")
    private String content;
    @Schema(title = "业务ID（如订单ID）")
    private String bizId;
    @Schema(title = "定向发送的会员ID列表（sendType=0时必填）")
    private List<Long> memberIds;
}
