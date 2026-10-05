package com.macro.mall.portal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.macro.mall.model.PmsComment;
import com.macro.mall.portal.domain.PmsCommentDetail;
import com.macro.mall.portal.domain.PmsCommentParam;
import com.macro.mall.portal.domain.PmsCommentReplayParam;

/**
 * 前台商品评论管理Service
 * Created by cyh on 2026/10/3.
 */
public interface PmsPortalCommentService extends IService<PmsComment> {
    /**
     * 分页获取商品评论列表（含回复列表）
     */
    IPage<PmsCommentDetail> list(Long productId, Integer pageNum, Integer pageSize);

    /**
     * 获取评论详情（含回复列表），并累加阅读数
     */
    PmsCommentDetail detail(Long id);

    /**
     * 分页获取当前会员的评论（含商品图片）
     */
    IPage<PmsCommentDetail> my(int pageNum, int pageSize);

    /**
     * 校验当前会员是否可评价该商品（仅已完成且未评价过的订单用户可评价）
     *
     * @return null 表示可评价，否则返回不可评价的原因
     */
    String checkCommentPermission(PmsCommentParam commentParam);

    /**
     * 发表评论
     */
    int add(PmsCommentParam commentParam);

    /**
     * 回复评论
     */
    int replay(PmsCommentReplayParam replayParam);

    /**
     * 点赞评论（点赞数+1）
     */
    int like(Long id);
}
