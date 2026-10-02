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
 * 首页轮播广告表
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("sms_home_advertise")
public class SmsHomeAdvertise implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    /**
     * 轮播位置：0->PC首页轮播；1->app首页轮播
     */
    @TableField("type")
    private Integer type;

    @TableField("pic")
    private String pic;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 上下线状态：0->下线；1->上线
     */
    @TableField("status")
    private Integer status;

    /**
     * 点击数
     */
    @TableField("click_count")
    private Integer clickCount;

    /**
     * 下单数
     */
    @TableField("order_count")
    private Integer orderCount;

    /**
     * 链接地址
     */
    @TableField("url")
    private String url;

    /**
     * 备注
     */
    @TableField("note")
    private String note;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;
}
