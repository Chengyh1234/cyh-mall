package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 立即购买生成确认单时传入的参数
 * Created by macro on 2026/10/5.
 */
@Data
@EqualsAndHashCode
public class BuyNowConfirmParam {
    @Schema(title = "商品SKU ID")
    private Long productSkuId;
    @Schema(title = "购买数量")
    private Integer quantity;
}
