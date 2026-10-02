package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dao.SmsFlashPromotionProductRelationDao;
import com.macro.mall.dto.SmsFlashPromotionProduct;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.service.SmsFlashPromotionProductRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 限时购商品关联管理Service实现类
 * Created by macro on 2018/11/16.
 */
@Service
public class SmsFlashPromotionProductRelationServiceImpl extends ServiceImpl<SmsFlashPromotionProductRelationMapper, SmsFlashPromotionProductRelation> implements SmsFlashPromotionProductRelationService {
    @Autowired
    private SmsFlashPromotionProductRelationDao relationDao;
    @Override
    public int create(List<SmsFlashPromotionProductRelation> relationList) {
        for (SmsFlashPromotionProductRelation relation : relationList) {
            baseMapper.insert(relation);
        }
        return relationList.size();
    }

    @Override
    public int update(Long id, SmsFlashPromotionProductRelation relation) {
        relation.setId(id);
        return baseMapper.updateById(relation);
    }

    @Override
    public int delete(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public SmsFlashPromotionProductRelation getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public IPage<SmsFlashPromotionProduct> list(Long flashPromotionId, Long flashPromotionSessionId, Integer pageSize, Integer pageNum) {
        List<SmsFlashPromotionProduct> list = relationDao.getList(flashPromotionId,flashPromotionSessionId);
        Page<SmsFlashPromotionProduct> page = new Page<>(pageNum, pageSize);
        page.setRecords(list);
        page.setTotal(list.size());
        return page;
    }

    @Override
    public long getCount(Long flashPromotionId, Long flashPromotionSessionId) {
        LambdaQueryWrapper<SmsFlashPromotionProductRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsFlashPromotionProductRelation::getFlashPromotionId, flashPromotionId)
                .eq(SmsFlashPromotionProductRelation::getFlashPromotionSessionId, flashPromotionSessionId);
        return this.count(wrapper);
    }
}