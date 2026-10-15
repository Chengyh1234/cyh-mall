package com.macro.mall.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionItem;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页内容管理Service实现类
 * Created by macro on 2019/1/28.
 */
@Service
public class HomeServiceImpl extends ServiceImpl<PmsProductMapper, PmsProduct> implements HomeService {
    @Autowired
    private SmsHomeAdvertiseMapper advertiseMapper;
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private CmsSubjectMapper subjectMapper;

    @Override
    public HomeContentResult content() {
        HomeContentResult result = new HomeContentResult();
        //获取首页广告
        result.setAdvertiseList(getHomeAdvertiseList());
        //获取推荐品牌，有限制数量
        result.setBrandList(homeDao.getRecommendBrandList(0,6));
        //获取秒杀信息
        result.setHomeFlashPromotion(getHomeFlashPromotion());
        //获取新品推荐，有限制数量
        result.setNewProductList(homeDao.getNewProductList(0,4));
        //获取人气推荐，有限制数量
        result.setHotProductList(homeDao.getHotProductList(0,4));
        //获取推荐专题，有限制数量
        result.setSubjectList(homeDao.getRecommendSubjectList(0,4));
        return result;
    }

    @Override
    public IPage<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum) {
        // TODO: 2019/1/29 暂时默认推荐所有商品
        LambdaQueryWrapper<PmsProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsProduct::getDeleteStatus, 0)
                .eq(PmsProduct::getPublishStatus, 1);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<PmsProductCategory> getProductCateList(Long parentId) {
        return productCategoryMapper.selectList(new LambdaQueryWrapper<PmsProductCategory>()
                .eq(PmsProductCategory::getShowStatus, 1)
                .eq(PmsProductCategory::getParentId, parentId)
                .orderByDesc(PmsProductCategory::getSort));
    }

    @Override
    public IPage<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<CmsSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CmsSubject::getShowStatus, 1);
        if(cateId!=null){
            wrapper.eq(CmsSubject::getCategoryId, cateId);
        }
        return subjectMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getHotProductList(offset, pageSize);
    }

    @Override
    public List<PmsProduct> newProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getNewProductList(offset, pageSize);
    }

    @Override
    public List<FlashPromotionItem> flashPromotionList() {
        List<FlashPromotionItem> itemList = new ArrayList<>();
        //获取近期秒杀活动
        SmsFlashPromotion flashPromotion = getRecentFlashPromotion();
        if (flashPromotion == null) {
            return itemList;
        }
        //场次表只存每日固定时间，需与日期拼成完整时间，基准日期见 getBaseDate
        LocalDate baseDate = getBaseDate(flashPromotion);
        List<SmsFlashPromotionSession> sessionList = promotionSessionMapper.selectList(new LambdaQueryWrapper<SmsFlashPromotionSession>()
                .eq(SmsFlashPromotionSession::getStatus, 1)
                .orderByAsc(SmsFlashPromotionSession::getStartTime));
        for (SmsFlashPromotionSession session : sessionList) {
            FlashPromotionItem item = new FlashPromotionItem();
            item.setId(session.getId());
            item.setName(session.getName());
            item.setStartTime(LocalDateTime.of(baseDate, session.getStartTime()));
            item.setEndTime(LocalDateTime.of(baseDate, session.getEndTime()));
            item.setProductList(homeDao.getFlashProductListAll(flashPromotion.getId(), session.getId()));
            itemList.add(item);
        }
        return itemList;
    }

    //获取近期秒杀活动：已上线且未结束的活动，取开始日期最近的一个
    private SmsFlashPromotion getRecentFlashPromotion() {
        LocalDate currDate = LocalDate.now();
        List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectList(new LambdaQueryWrapper<SmsFlashPromotion>()
                .eq(SmsFlashPromotion::getStatus, 1)
                .ge(SmsFlashPromotion::getEndDate, currDate)
                .orderByAsc(SmsFlashPromotion::getStartDate));
        if (!CollectionUtils.isEmpty(flashPromotionList)) {
            return flashPromotionList.get(0);
        }
        return null;
    }

    //计算场次完整时间的基准日期：活动进行中用今天；活动未开始则用活动开始日期
    private LocalDate getBaseDate(SmsFlashPromotion flashPromotion) {
        LocalDate currDate = LocalDate.now();
        if (flashPromotion.getStartDate() != null
                && flashPromotion.getEndDate() != null
                && !currDate.isBefore(flashPromotion.getStartDate())
                && !currDate.isAfter(flashPromotion.getEndDate())) {
            return currDate;
        }
        return flashPromotion.getStartDate() != null ? flashPromotion.getStartDate() : currDate;
    }

    private HomeFlashPromotion getHomeFlashPromotion() {
        HomeFlashPromotion homeFlashPromotion = new HomeFlashPromotion();
        //获取当前秒杀活动
        LocalTime now = LocalTime.now();
        SmsFlashPromotion flashPromotion = getFlashPromotion();
        if (flashPromotion != null) {
            //获取当前秒杀场次
            SmsFlashPromotionSession flashPromotionSession = getFlashPromotionSession(now);
            if (flashPromotionSession != null) {
                homeFlashPromotion.setStartTime(flashPromotionSession.getStartTime());
                homeFlashPromotion.setEndTime(flashPromotionSession.getEndTime());
                //获取下一个秒杀场次
                SmsFlashPromotionSession nextSession = getNextFlashPromotionSession(homeFlashPromotion.getStartTime());
                if(nextSession!=null){
                    homeFlashPromotion.setNextStartTime(nextSession.getStartTime());
                    homeFlashPromotion.setNextEndTime(nextSession.getEndTime());
                }
                //获取秒杀商品
                List<FlashPromotionProduct> flashProductList = homeDao.getFlashProductList(flashPromotion.getId(), flashPromotionSession.getId());
                homeFlashPromotion.setProductList(flashProductList);
            }
        }
        return homeFlashPromotion;
    }

    //获取下一个场次信息
    private SmsFlashPromotionSession getNextFlashPromotionSession(LocalTime date) {
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectList(new LambdaQueryWrapper<SmsFlashPromotionSession>()
                .gt(SmsFlashPromotionSession::getStartTime, date)
                .orderByAsc(SmsFlashPromotionSession::getStartTime));
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }

    private List<SmsHomeAdvertise> getHomeAdvertiseList() {
        return advertiseMapper.selectList(new LambdaQueryWrapper<SmsHomeAdvertise>()
                .eq(SmsHomeAdvertise::getType, 1)
                .eq(SmsHomeAdvertise::getStatus, 1)
                .orderByDesc(SmsHomeAdvertise::getSort));
    }

    //根据时间获取秒杀活动
    private SmsFlashPromotion getFlashPromotion() {
        LocalDate currDate = LocalDate.now();
        List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectList(new LambdaQueryWrapper<SmsFlashPromotion>()
                .eq(SmsFlashPromotion::getStatus, 1)
                .le(SmsFlashPromotion::getStartDate, currDate)
                .ge(SmsFlashPromotion::getEndDate, currDate));
        if (!CollectionUtils.isEmpty(flashPromotionList)) {
            return flashPromotionList.get(0);
        }
        return null;
    }

    //根据时间获取秒杀场次
    private SmsFlashPromotionSession getFlashPromotionSession(LocalTime currTime) {
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectList(new LambdaQueryWrapper<SmsFlashPromotionSession>()
                .le(SmsFlashPromotionSession::getStartTime, currTime)
                .ge(SmsFlashPromotionSession::getEndTime, currTime));
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }
}