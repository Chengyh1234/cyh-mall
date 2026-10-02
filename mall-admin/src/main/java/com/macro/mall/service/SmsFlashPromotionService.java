package com.macro.mall.service;

import com.baomidou.mybatisplus.spring.service.IService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.model.SmsFlashPromotion;

/**
 * 限时购活动管理Service
 * Created by macro on 2018/11/16.
 */
public interface SmsFlashPromotionService extends IService<SmsFlashPromotion> {
    /**
     * 添加活动
     */
    int create(SmsFlashPromotion flashPromotion);

    /**
     * 修改指定活动
     */
    int update(Long id, SmsFlashPromotion flashPromotion);

    /**
     * 删除单个活动
     */
    int delete(Long id);

    /**
     * 修改上下线状态
     */
    int updateStatus(Long id, Integer status);

    /**
     * 获取活动详情
     */
    SmsFlashPromotion getItem(Long id);

    /**
     * 分页查询活动
     */
    IPage<SmsFlashPromotion> list(String keyword, Integer pageSize, Integer pageNum);
}
