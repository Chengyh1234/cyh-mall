package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dto.SmsFlashPromotionSessionDetail;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.service.SmsFlashPromotionProductRelationService;
import com.macro.mall.service.SmsFlashPromotionSessionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 限时购场次管理Service实现类
 * Created by macro on 2018/11/16.
 */
@Service
public class SmsFlashPromotionSessionServiceImpl extends ServiceImpl<SmsFlashPromotionSessionMapper, SmsFlashPromotionSession> implements SmsFlashPromotionSessionService {
    @Autowired
    private SmsFlashPromotionProductRelationService relationService;

    @Override
    public int create(SmsFlashPromotionSession promotionSession) {
        promotionSession.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(promotionSession);
    }

    @Override
    public int update(Long id, SmsFlashPromotionSession promotionSession) {
        promotionSession.setId(id);
        return baseMapper.updateById(promotionSession);
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        SmsFlashPromotionSession promotionSession = new SmsFlashPromotionSession();
        promotionSession.setId(id);
        promotionSession.setStatus(status);
        return baseMapper.updateById(promotionSession);
    }

    @Override
    public int delete(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public SmsFlashPromotionSession getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public List<SmsFlashPromotionSession> list() {
        return this.list(new LambdaQueryWrapper<>());
    }

    @Override
    public List<SmsFlashPromotionSessionDetail> selectList(Long flashPromotionId) {
        List<SmsFlashPromotionSessionDetail> result = new ArrayList<>();
        LambdaQueryWrapper<SmsFlashPromotionSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsFlashPromotionSession::getStatus, 1);
        List<SmsFlashPromotionSession> list = this.list(wrapper);
        for (SmsFlashPromotionSession promotionSession : list) {
            SmsFlashPromotionSessionDetail detail = new SmsFlashPromotionSessionDetail();
            BeanUtils.copyProperties(promotionSession, detail);
            long count = relationService.getCount(flashPromotionId, promotionSession.getId());
            detail.setProductCount(count);
            result.add(detail);
        }
        return result;
    }
}