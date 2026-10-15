package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动场次信息封装
 */
@Getter
@Setter
public class FlashPromotionItem {
    @Schema(title = "场次ID")
    private Long id;
    @Schema(title = "场次名称")
    private String name;
    @Schema(title = "本场开始时间")
    private LocalDateTime startTime;
    @Schema(title = "本场结束时间")
    private LocalDateTime endTime;
    @Schema(title = "本场秒杀商品")
    private List<FlashPromotionProduct> productList;
}
