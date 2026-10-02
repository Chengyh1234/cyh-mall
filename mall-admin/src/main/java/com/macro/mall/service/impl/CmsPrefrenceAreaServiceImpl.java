package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.CmsPrefrenceAreaMapper;
import com.macro.mall.model.CmsPrefrenceArea;
import com.macro.mall.service.CmsPrefrenceAreaService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品优选管理Service实现类
 * Created by macro on 2018/6/1.
 */
@Service
public class CmsPrefrenceAreaServiceImpl extends ServiceImpl<CmsPrefrenceAreaMapper, CmsPrefrenceArea> implements CmsPrefrenceAreaService {

    @Override
    public List<CmsPrefrenceArea> listAll() {
        return this.list();
    }
}
