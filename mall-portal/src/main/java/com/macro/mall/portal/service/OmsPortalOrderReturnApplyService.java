package com.macro.mall.portal.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;

import java.util.List;

/**
 * 前台订单退货管理Service
 * Created by macro on 2018/10/17.
 */
public interface OmsPortalOrderReturnApplyService extends IService<OmsOrderReturnApply> {
    /**
     * 提交申请（同一订单重复申请时返回 -1）
     */
    int create(OmsOrderReturnApplyParam returnApply);

    /**
     * 获取当前会员的售后申请列表（按申请时间倒序）
     */
    List<OmsOrderReturnApply> listByMember(UmsMember member);
}