package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 前台发表商品评论参数
 * Created by cyh on 2026/10/3.
 */
@Getter
@Setter
public class PmsCommentParam {
    /**
     * 商品ID
     */
    private Long productId;
    /**
     * 评价星数：0->5
     */
    private Integer star;
    /**
     * 评价内容
     */
    private String content;
    /**
     * 上传图片地址，以逗号隔开
     */
    private String pics;
    /**
     * 购买时的商品属性
     */
    private String productAttribute;
}
