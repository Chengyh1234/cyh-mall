package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 秒杀下单条目（独立于普通购物车促销模型）
 * <p>
 * 秒杀价格、每人限购、总限量均取自 sms_flash_promotion_product_relation，
 * 不叠加满减/优惠券/积分，因此不复用 CartPromotionItem。
 */
@Getter
@Setter
public class FlashPromotionOrderItem {

    @Schema(title = "商品ID")
    private Long productId;

    @Schema(title = "商品名称")
    private String productName;

    @Schema(title = "商品图片")
    private String productPic;

    @Schema(title = "商品销售属性")
    private String productAttr;

    @Schema(title = "商品品牌")
    private String productBrand;

    @Schema(title = "商品货号")
    private String productSn;

    @Schema(title = "商品SKU ID")
    private Long productSkuId;

    @Schema(title = "商品SKU条码")
    private String productSkuCode;

    @Schema(title = "商品分类ID")
    private Long productCategoryId;

    @Schema(title = "秒杀价格")
    private BigDecimal price;

    @Schema(title = "购买数量")
    private Integer quantity;

    @Schema(title = "秒杀商品关系ID（取消时回补秒杀专属库存）")
    private Long flashPromotionRelationId;

    @Schema(title = "秒杀总限量（独立池子，flash_promotion_count 剩余量）")
    private Integer flashPromotionCount;

    @Schema(title = "每人限购数量")
    private Integer flashPromotionLimit;
}
