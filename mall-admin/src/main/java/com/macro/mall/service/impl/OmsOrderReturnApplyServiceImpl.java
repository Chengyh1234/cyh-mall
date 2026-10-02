package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.service.OmsOrderReturnApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/18.
 */
@Service
public class OmsOrderReturnApplyServiceImpl extends ServiceImpl<OmsOrderReturnApplyMapper, OmsOrderReturnApply> implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;

    @Override
    public IPage<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum) {
        //自定义Dao返回全量数据，此处进行内存分页
        List<OmsOrderReturnApply> list = returnApplyDao.getList(queryParam);
        Page<OmsOrderReturnApply> page = new Page<>(pageNum, pageSize);
        if (list == null || list.isEmpty()) {
            page.setTotal(0);
            page.setRecords(new ArrayList<>());
            return page;
        }
        page.setTotal(list.size());
        int fromIndex = (pageNum - 1) * pageSize;
        if (fromIndex >= list.size()) {
            page.setRecords(new ArrayList<>());
        } else {
            int toIndex = Math.min(fromIndex + pageSize, list.size());
            page.setRecords(list.subList(fromIndex, toIndex));
        }
        return page;
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<OmsOrderReturnApply>()
                .in(OmsOrderReturnApply::getId, ids).eq(OmsOrderReturnApply::getStatus, 3));
    }

    @Override
    public int updateStatus(Long id, OmsUpdateStatusParam statusParam) {
        Integer status = statusParam.getStatus();
        OmsOrderReturnApply returnApply = new OmsOrderReturnApply();
        if (status.equals(1)) {
            //确认退货
            returnApply.setId(id);
            returnApply.setStatus(1);
            returnApply.setReturnAmount(statusParam.getReturnAmount());
            returnApply.setCompanyAddressId(statusParam.getCompanyAddressId());
            returnApply.setHandleTime(LocalDateTime.now());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
        } else if (status.equals(2)) {
            //完成退货
            returnApply.setId(id);
            returnApply.setStatus(2);
            returnApply.setReceiveTime(LocalDateTime.now());
            returnApply.setReceiveMan(statusParam.getReceiveMan());
            returnApply.setReceiveNote(statusParam.getReceiveNote());
        } else if (status.equals(3)) {
            //拒绝退货
            returnApply.setId(id);
            returnApply.setStatus(3);
            returnApply.setHandleTime(LocalDateTime.now());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
        } else {
            return 0;
        }
        return baseMapper.updateById(returnApply);
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        return returnApplyDao.getDetail(id);
    }
}