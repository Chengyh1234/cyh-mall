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
 * 会员消息接收记录表
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("ums_member_message")
public class UmsMemberMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会员ID
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 消息ID
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 阅读状态：0->未读；1->已读
     */
    @TableField("read_status")
    private Integer readStatus;

    /**
     * 删除状态：0->未删除；1->已删除
     */
    @TableField("deleted")
    private Integer deleted;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
