package com.macro.mall.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * sku的库存
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("pms_sku_stock")
public class PmsSkuStock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    /**
     * sku编码
     */
    @TableField("sku_code")
    private String skuCode;

    @TableField("price")
    private BigDecimal price;

    /**
     * 库存
     */
    @TableField("stock")
    private Integer stock;

    /**
     * 预警库存
     */
    @TableField("low_stock")
    private Integer lowStock;

    /**
     * 展示图片
     */
    @TableField("pic")
    private String pic;

    /**
     * 销量
     */
    @TableField("sale")
    private Integer sale;

    /**
     * 单品促销价格
     */
    @TableField("promotion_price")
    private BigDecimal promotionPrice;

    /**
     * 锁定库存
     */
    @TableField("lock_stock")
    private Integer lockStock;

    /**
     * 商品销售属性，json格式
     */
    @TableField("sp_data")
    private String spData;
}
