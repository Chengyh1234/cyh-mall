package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.service.SmsFlashPromotionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 限时购活动管理Service实现类
 * Created by macro on 2018/11/16.
 */
@Service
public class SmsFlashPromotionServiceImpl extends ServiceImpl<SmsFlashPromotionMapper, SmsFlashPromotion> implements SmsFlashPromotionService {

    @Override
    public int create(SmsFlashPromotion flashPromotion) {
        flashPromotion.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(flashPromotion);
    }

    @Override
    public int update(Long id, SmsFlashPromotion flashPromotion) {
        flashPromotion.setId(id);
        return baseMapper.updateById(flashPromotion);
    }

    @Override
    public int delete(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        SmsFlashPromotion flashPromotion = new SmsFlashPromotion();
        flashPromotion.setId(id);
        flashPromotion.setStatus(status);
        return baseMapper.updateById(flashPromotion);
    }

    @Override
    public SmsFlashPromotion getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public IPage<SmsFlashPromotion> list(String keyword, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<SmsFlashPromotion> wrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(keyword)) {
            wrapper.like(SmsFlashPromotion::getTitle, keyword);
        }
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }
}