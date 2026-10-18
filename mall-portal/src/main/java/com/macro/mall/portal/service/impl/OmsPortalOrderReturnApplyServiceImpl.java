package com.macro.mall.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsPortalOrderReturnApplyServiceImpl extends ServiceImpl<OmsOrderReturnApplyMapper, OmsOrderReturnApply> implements OmsPortalOrderReturnApplyService {
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        // 同一订单不允许重复申请售后
        Long count = baseMapper.selectCount(new QueryWrapper<OmsOrderReturnApply>()
                .eq("order_id", returnApply.getOrderId()));
        if (count != null && count > 0) {
            return -1;
        }
        // 订单信息从数据库获取，防止前端篡改金额与订单号
        OmsOrder order = orderMapper.selectById(returnApply.getOrderId());
        if (order == null || order.getPayAmount() == null) {
            return 0;
        }
        // 商品信息从订单快照（oms_order_item）获取，以下单时记录为准
        List<OmsOrderItem> orderItems = orderItemMapper.selectList(new QueryWrapper<OmsOrderItem>()
                .eq("order_id", order.getId()));
        OmsOrderItem item = orderItems.stream()
                .filter(i -> i.getProductId().equals(returnApply.getProductId()))
                .findFirst().orElse(null);
        if (item == null) {
            return 0;
        }
        // 会员信息从当前登录态获取（订单表member_username即登录手机号），防止伪造
        UmsMember member = memberService.getCurrentMember();
        if (member == null) {
            return 0;
        }
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply, realApply);
        // 以下关键字段全部以数据库/登录态为准，覆盖前端传入值
        realApply.setMemberUsername(member.getPhone());
        realApply.setOrderSn(order.getOrderSn());
        realApply.setReturnAmount(order.getPayAmount());
        realApply.setProductId(item.getProductId());
        realApply.setProductPic(item.getProductPic());
        realApply.setProductName(item.getProductName());
        realApply.setProductBrand(item.getProductBrand());
        realApply.setProductAttr(item.getProductAttr());
        realApply.setProductCount(item.getProductQuantity());
        realApply.setProductPrice(item.getProductPrice());
        realApply.setProductRealPrice(item.getRealAmount());
        realApply.setCreateTime(LocalDateTime.now());
        realApply.setStatus(0);
        return baseMapper.insert(realApply);
    }

    @Override
    public List<OmsOrderReturnApply> listByMember(UmsMember member) {
        // 通过当前会员的订单关联查询其售后申请（按申请时间倒序）
        List<Long> orderIds = orderMapper.selectList(new QueryWrapper<OmsOrder>()
                .eq("member_id", member.getId()))
                .stream().map(OmsOrder::getId).collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectList(new QueryWrapper<OmsOrderReturnApply>()
                .in("order_id", orderIds)
                .orderByDesc("create_time"));
    }
}