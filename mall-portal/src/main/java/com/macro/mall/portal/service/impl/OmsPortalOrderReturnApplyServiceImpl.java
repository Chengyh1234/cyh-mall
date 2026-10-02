package com.macro.mall.portal.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsPortalOrderReturnApplyServiceImpl extends ServiceImpl<OmsOrderReturnApplyMapper, OmsOrderReturnApply> implements OmsPortalOrderReturnApplyService {
    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply,realApply);
        realApply.setCreateTime(LocalDateTime.now());
        realApply.setStatus(0);
        return baseMapper.insert(realApply);
    }
}