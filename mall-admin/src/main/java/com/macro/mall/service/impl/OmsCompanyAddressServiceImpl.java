package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsCompanyAddressMapper;
import com.macro.mall.model.OmsCompanyAddress;
import com.macro.mall.service.OmsCompanyAddressService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收货地址管理Service实现类
 * Created by macro on 2018/10/18.
 */
@Service
public class OmsCompanyAddressServiceImpl extends ServiceImpl<OmsCompanyAddressMapper, OmsCompanyAddress> implements OmsCompanyAddressService {

    @Override
    public List<OmsCompanyAddress> list() {
        return baseMapper.selectList(new LambdaQueryWrapper<OmsCompanyAddress>());
    }
}
