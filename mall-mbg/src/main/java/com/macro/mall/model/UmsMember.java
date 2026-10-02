package com.macro.mall.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 会员表
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("ums_member")
public class UmsMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("member_level_id")
    private Long memberLevelId;

    /**
     * 手机号码
     */
    @TableField("phone")
    private String phone;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 帐号启用状态:0->禁用；1->启用
     */
    @TableField("status")
    private Integer status;

    /**
     * 注册时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 头像
     */
    @TableField("icon")
    private String icon;

    /**
     * 性别：0->未知；1->男；2->女
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 生日
     */
    @TableField("birthday")
    private LocalDate birthday;

    /**
     * 所做城市
     */
    @TableField("city")
    private String city;

    /**
     * 职业
     */
    @TableField("job")
    private String job;

    /**
     * 个性签名
     */
    @TableField("personalized_signature")
    private String personalizedSignature;

    /**
     * 用户来源
     */
    @TableField("source_type")
    private Integer sourceType;

    /**
     * 积分
     */
    @TableField("integration")
    private Integer integration;

    /**
     * 成长值
     */
    @TableField("growth")
    private Integer growth;

    /**
     * 剩余抽奖次数
     */
    @TableField("luckey_count")
    private Integer luckeyCount;

    /**
     * 历史积分数量
     */
    @TableField("history_integration")
    private Integer historyIntegration;
}
