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
 * 限时购通知记录
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("sms_flash_promotion_log")
public class SmsFlashPromotionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("member_id")
    private Integer memberId;

    @TableField("product_id")
    private Long productId;

    @TableField("member_phone")
    private String memberPhone;

    @TableField("product_name")
    private String productName;

    /**
     * 会员订阅时间
     */
    @TableField("subscribe_time")
    private LocalDateTime subscribeTime;

    @TableField("send_time")
    private LocalDateTime sendTime;
}
