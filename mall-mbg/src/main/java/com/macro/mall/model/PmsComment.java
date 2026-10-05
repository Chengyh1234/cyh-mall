package com.macro.mall.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 商品评价表
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("pms_comment")
public class PmsComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("member_id")
    private Long memberId;

    @TableField("product_id")
    private Long productId;

    @TableField("member_nick_name")
    private String memberNickName;

    @TableField("product_name")
    private String productName;

    /**
     * 评价星数：0->5
     */
    @TableField("star")
    private Integer star;

    /**
     * 评价的ip
     */
    @TableField("member_ip")
    private String memberIp;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("show_status")
    private Integer showStatus;

    /**
     * 购买时的商品属性
     */
    @TableField("product_attribute")
    private String productAttribute;

    @TableField("collect_couont")
    private Integer collectCouont;

    @TableField("read_count")
    private Integer readCount;

    @TableField("content")
    private String content;

    /**
     * 上传图片地址，以逗号隔开
     */
    @TableField("pics")
    private String pics;

    /**
     * 评论用户头像
     */
    @TableField("member_icon")
    private String memberIcon;

    @TableField("replay_count")
    private Integer replayCount;
}
