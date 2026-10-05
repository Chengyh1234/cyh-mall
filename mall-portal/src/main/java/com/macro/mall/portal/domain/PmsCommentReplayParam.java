package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 前台回复商品评论参数
 * Created by cyh on 2026/10/3.
 */
@Getter
@Setter
public class PmsCommentReplayParam {
    /**
     * 评论ID
     */
    private Long commentId;
    /**
     * 回复内容
     */
    private String content;
}
