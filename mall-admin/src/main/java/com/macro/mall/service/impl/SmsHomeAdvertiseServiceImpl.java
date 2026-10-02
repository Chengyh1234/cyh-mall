package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsHomeAdvertiseMapper;
import com.macro.mall.model.SmsHomeAdvertise;
import com.macro.mall.service.SmsHomeAdvertiseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首页广告管理Service实现类
 * Created by macro on 2018/11/7.
 */
@Service
public class SmsHomeAdvertiseServiceImpl extends ServiceImpl<SmsHomeAdvertiseMapper, SmsHomeAdvertise> implements SmsHomeAdvertiseService {

    @Override
    public int create(SmsHomeAdvertise advertise) {
        advertise.setClickCount(0);
        advertise.setOrderCount(0);
        return baseMapper.insert(advertise);
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<SmsHomeAdvertise>().in(SmsHomeAdvertise::getId, ids));
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        SmsHomeAdvertise record = new SmsHomeAdvertise();
        record.setId(id);
        record.setStatus(status);
        return baseMapper.updateById(record);
    }

    @Override
    public SmsHomeAdvertise getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public int update(Long id, SmsHomeAdvertise advertise) {
        advertise.setId(id);
        return baseMapper.updateById(advertise);
    }

    @Override
    public IPage<SmsHomeAdvertise> list(String name, Integer type, String endTime, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsHomeAdvertise> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(name)) {
            wrapper.like(SmsHomeAdvertise::getName, name);
        }
        if (type != null) {
            wrapper.eq(SmsHomeAdvertise::getType, type);
        }
        if (!StrUtil.isEmpty(endTime)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime start = LocalDateTime.parse(endTime + " 00:00:00", formatter);
            LocalDateTime end = LocalDateTime.parse(endTime + " 23:59:59", formatter);
            wrapper.between(SmsHomeAdvertise::getEndTime, start, end);
        }
        wrapper.orderByDesc(SmsHomeAdvertise::getSort);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }
}