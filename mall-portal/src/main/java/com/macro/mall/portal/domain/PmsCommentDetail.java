package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsCommentReplay;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 前台商品评论详情（含回复列表）
 * Created by cyh on 2026/10/3.
 */
@Getter
@Setter
public class PmsCommentDetail extends PmsComment {
    /**
     * 评论回复列表
     */
    private List<PmsCommentReplay> replayList;
    /**
     * 评论商品的展示图片
     */
    private String productPic;
}
