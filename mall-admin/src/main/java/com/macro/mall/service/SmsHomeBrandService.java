package com.macro.mall.service;

import com.baomidou.mybatisplus.spring.service.IService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.macro.mall.model.SmsHomeBrand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首页品牌管理Service
 * Created by macro on 2018/11/6.
 */
public interface SmsHomeBrandService extends IService<SmsHomeBrand> {
    /**
     * 添加首页品牌推荐
     */
    @Transactional
    int create(List<SmsHomeBrand> homeBrandList);

    /**
     * 修改品牌推荐排序
     */
    int updateSort(Long id, Integer sort);

    /**
     * 批量删除品牌推荐
     */
    int delete(List<Long> ids);

    /**
     * 批量更新推荐状态
     */
    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    /**
     * 分页查询品牌推荐
     */
    IPage<SmsHomeBrand> list(String brandName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
