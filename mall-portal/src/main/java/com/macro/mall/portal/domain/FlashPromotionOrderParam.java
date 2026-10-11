package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 限时购/秒杀下单时传入的参数
 * <p>
 * 秒杀下单复用立即购买的商品维度（productSkuId + quantity），
 * 额外携带秒杀活动与场次标识，用于校验活动有效性并取得固定限购数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlashPromotionOrderParam extends BuyNowParam {
    @Schema(title = "限时购活动ID")
    private Long flashPromotionId;
    @Schema(title = "限时购场次ID")
    private Long sessionId;
}