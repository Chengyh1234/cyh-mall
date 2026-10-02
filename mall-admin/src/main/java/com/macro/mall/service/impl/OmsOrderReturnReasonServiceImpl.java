package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsOrderReturnReasonMapper;
import com.macro.mall.model.OmsOrderReturnReason;
import com.macro.mall.service.OmsOrderReturnReasonService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单原因管理Service实现类
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsOrderReturnReasonServiceImpl extends ServiceImpl<OmsOrderReturnReasonMapper, OmsOrderReturnReason> implements OmsOrderReturnReasonService {

    @Override
    public int create(OmsOrderReturnReason returnReason) {
        returnReason.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(returnReason);
    }

    @Override
    public int update(Long id, OmsOrderReturnReason returnReason) {
        returnReason.setId(id);
        return baseMapper.updateById(returnReason);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<OmsOrderReturnReason>().in(OmsOrderReturnReason::getId, ids));
    }

    @Override
    public IPage<OmsOrderReturnReason> list(Integer pageSize, Integer pageNum) {
        return this.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OmsOrderReturnReason>().orderByDesc(OmsOrderReturnReason::getSort));
    }

    @Override
    public int updateStatus(List<Long> ids, Integer status) {
        if (!status.equals(0) && !status.equals(1)) {
            return 0;
        }
        OmsOrderReturnReason record = new OmsOrderReturnReason();
        record.setStatus(status);
        return baseMapper.update(record, new LambdaQueryWrapper<OmsOrderReturnReason>().in(OmsOrderReturnReason::getId, ids));
    }

    @Override
    public OmsOrderReturnReason getItem(Long id) {
        return this.getById(id);
    }
}