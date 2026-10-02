package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.service.OmsOrderSettingService;
import org.springframework.stereotype.Service;

/**
 * 订单设置管理Service实现类
 * Created by macro on 2018/10/16.
 */
@Service
public class OmsOrderSettingServiceImpl extends ServiceImpl<OmsOrderSettingMapper, OmsOrderSetting> implements OmsOrderSettingService {

    @Override
    public OmsOrderSetting getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public int update(Long id, OmsOrderSetting orderSetting) {
        orderSetting.setId(id);
        return baseMapper.updateById(orderSetting);
    }
}