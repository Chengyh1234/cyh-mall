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
 * 后台菜单表
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("ums_menu")
public class UmsMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父级ID
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 菜单名称
     */
    @TableField("title")
    private String title;

    /**
     * 菜单级数
     */
    @TableField("level")
    private Integer level;

    /**
     * 菜单排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 前端名称
     */
    @TableField("name")
    private String name;

    /**
     * 前端图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 前端隐藏
     */
    @TableField("hidden")
    private Integer hidden;
}
