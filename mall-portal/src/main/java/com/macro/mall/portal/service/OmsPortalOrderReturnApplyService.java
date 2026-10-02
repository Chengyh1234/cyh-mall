package com.macro.mall.portal.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;

/**
 * 前台订单退货管理Service
 * Created by macro on 2018/10/17.
 */
public interface OmsPortalOrderReturnApplyService extends IService<OmsOrderReturnApply> {
    /**
     * 提交申请
     */
    int create(OmsOrderReturnApplyParam returnApply);
}
