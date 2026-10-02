package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

/**
 * 首页秒杀场次信息封装
 * Created by macro on 2019/1/28.
 */
@Getter
@Setter
public class HomeFlashPromotion {
    @Schema(title = "本场开始时间")
    private LocalTime startTime;
    @Schema(title = "本场结束时间")
    private LocalTime endTime;
    @Schema(title = "下场开始时间")
    private LocalTime nextStartTime;
    @Schema(title = "下场结束时间")
    private LocalTime nextEndTime;
    @Schema(title = "属于该秒杀活动的商品")
    private List<FlashPromotionProduct> productList;
}
