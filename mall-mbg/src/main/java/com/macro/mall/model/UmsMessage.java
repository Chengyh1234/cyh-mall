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
 * 消息主体表
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("ums_message")
public class UmsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息类型：1->系统通知；2->订单通知；3->营销通知
     */
    @TableField("type")
    private Integer type;

    /**
     * 发送类型：0->定向；1->广播
     */
    @TableField("send_type")
    private Integer sendType;

    /**
     * 消息标题
     */
    @TableField("title")
    private String title;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 业务ID（如订单ID）
     */
    @TableField("biz_id")
    private String bizId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}