package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会员消息详情封装
 */
@Getter
@Setter
public class PortalMessageDetail {
    @Schema(title = "消息ID")
    private Long id;
    @Schema(title = "消息类型：1->系统通知；2->订单通知；3->营销通知")
    private Integer type;
    @Schema(title = "消息标题")
    private String title;
    @Schema(title = "消息内容")
    private String content;
    @Schema(title = "业务ID（如订单ID）")
    private String bizId;
    @Schema(title = "阅读状态：0->未读；1->已读")
    private Integer readStatus;
    @Schema(title = "创建时间")
    private LocalDateTime createTime;
}
