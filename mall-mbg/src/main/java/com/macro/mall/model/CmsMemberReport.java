package com.macro.mall.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户举报表
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
@Getter
@Setter
@ToString
@TableName("cms_member_report")
public class CmsMemberReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableField("id")
    private Long id;

    /**
     * 举报类型：0->商品评价；1->话题内容；2->用户评论
     */
    @TableField("report_type")
    private Integer reportType;

    /**
     * 举报人
     */
    @TableField("report_member_name")
    private String reportMemberName;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("report_object")
    private String reportObject;

    /**
     * 举报状态：0->未处理；1->已处理
     */
    @TableField("report_status")
    private Integer reportStatus;

    /**
     * 处理结果：0->无效；1->有效；2->恶意
     */
    @TableField("handle_status")
    private Integer handleStatus;

    @TableField("note")
    private String note;
}
