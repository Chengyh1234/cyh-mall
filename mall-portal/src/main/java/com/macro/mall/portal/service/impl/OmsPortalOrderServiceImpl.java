package com.macro.mall.portal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.mq.event.StockOperateEvent;
import com.macro.mall.portal.mq.publisher.OrderCancelEventPublisher;
import com.macro.mall.portal.mq.publisher.StockOperateEventPublisher;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 前台订单管理Service
 * Created by macro on 2018/8/30.
 */
@Slf4j
@Service
public class OmsPortalOrderServiceImpl extends ServiceImpl<OmsOrderMapper, OmsOrder> implements OmsPortalOrderService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsCartItemService cartItemService;
    @Autowired
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Autowired
    private UmsMemberCouponService memberCouponService;
    @Autowired
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private SmsCouponHistoryDao couponHistoryDao;
    @Autowired
    private PortalOrderItemDao orderItemDao;
    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Autowired
    private RedisService redisService;
    @Value("${redis.key.orderId}")
    private String REDIS_KEY_ORDER_ID;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private OrderCancelEventPublisher orderCancelEventPublisher;
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private StockCacheService stockCacheService;
    @Autowired
    private StockOperateEventPublisher stockOperateEventPublisher;
    @Value("${redis.key.orderDedup}")
    private String REDIS_KEY_ORDER_DEDUP;
    @Value("${purchase.order-dedup-seconds:5}")
    private long ORDER_DEDUP_SECONDS;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper flashPromotionSessionMapper;
    @Autowired
    private SmsFlashPromotionProductRelationMapper flashPromotionRelationMapper;

    @Override
    public ConfirmOrderResult generateConfirmOrder(List<Long> cartIds) {
        //获取购物车信息
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(),cartIds);
        return buildConfirmOrderResult(cartPromotionItemList);
    }

    @Override
    public ConfirmOrderResult generateBuyNowConfirmOrder(BuyNowConfirmParam buyNowConfirmParam) {
        UmsMember currentMember = memberService.getCurrentMember();
        CartPromotionItem cartPromotionItem = getBuyNowCartPromotionItem(buyNowConfirmParam.getProductSkuId(), buyNowConfirmParam.getQuantity(), currentMember);
        return buildConfirmOrderResult(Collections.singletonList(cartPromotionItem));
    }

    /**
     * 构建确认单信息
     */
    private ConfirmOrderResult buildConfirmOrderResult(List<CartPromotionItem> cartPromotionItemList) {
        ConfirmOrderResult result = new ConfirmOrderResult();
        result.setCartPromotionItemList(cartPromotionItemList);
        //获取用户收货地址列表
        List<UmsMemberReceiveAddress> memberReceiveAddressList = memberReceiveAddressService.list();
        result.setMemberReceiveAddressList(memberReceiveAddressList);
        //获取用户可用优惠券列表
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //获取用户积分
        UmsMember currentMember = memberService.getCurrentMember();
        result.setMemberIntegration(currentMember.getIntegration());
        //获取积分使用规则
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectById(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);
        //计算总金额、活动优惠、应付金额
        ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(cartPromotionItemList);
        result.setCalcAmount(calcAmount);
        return result;
    }

    @Transactional
    @Override
    public Map<String, Object> generateOrder(OrderParam orderParam) {
        //获取购物车及优惠信息（锁外查询，避免持锁等待DB）
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(), orderParam.getCartIds());
        return generateOrderWithLock(orderParam, cartPromotionItemList, currentMember, true);
    }

    @Transactional
    @Override
    public Map<String, Object> generateBuyNowOrder(BuyNowParam buyNowParam) {
        UmsMember currentMember = memberService.getCurrentMember();
        CartPromotionItem cartPromotionItem = getBuyNowCartPromotionItem(buyNowParam.getProductSkuId(), buyNowParam.getQuantity(), currentMember);
        return generateOrderWithLock(buyNowParam, Collections.singletonList(cartPromotionItem), currentMember, false);
    }

    @Transactional
    @Override
    public Map<String, Object> generateFlashOrder(FlashPromotionOrderParam flashParam) {
        //获取当前用户
        UmsMember currentMember = memberService.getCurrentMember();
        //校验秒杀活动、场次与商品关系，组装秒杀下单条目
        CartPromotionItem cartPromotionItem = getFlashBuyNowCartPromotionItem(flashParam, currentMember);
        //按秒杀固定限购数下单（orderType=1 秒杀订单），不走购物车
        return generateOrderWithLock(flashParam, Collections.singletonList(cartPromotionItem), currentMember, false,
                1, cartPromotionItem.getFlashPromotionLimit());
    }

    /**
     * 防重复下单：基于 Redisson 分布式锁 + 防重标记
     * <p>
     * 同一用户对同一批SKU在防重窗口内只允许成功提交一次订单，
     * 防止用户误点、网络卡顿等导致的多次点击产生重复订单。
     * 由 {@link #generateOrder} / {@link #generateBuyNowOrder} 在事务内调用，
     * 默认生成普通订单（orderType=0）且不限购（limitPerUser=0）。
     */
    public Map<String, Object> generateOrderWithLock(OrderParam orderParam, List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember, boolean needDeleteCart) {
        return generateOrderWithLock(orderParam, cartPromotionItemList, currentMember, needDeleteCart, 0, 0);
    }

    /**
     * 防重复下单：基于 Redisson 分布式锁 + 防重标记（支持指定订单类型与限购数）
     * <p>
     * 秒杀下单调用此重载，传入 orderType=1 与固定的每人限购数，
     * 由 Lua 脚本在库存预扣时同步完成限购校验（超买返回 -3）。
     */
    public Map<String, Object> generateOrderWithLock(OrderParam orderParam, List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember, boolean needDeleteCart, int orderType, int limitPerUser) {
        //构建下单防重key前缀：mall:oms:dedup:{memberId}:{skuIds}
        String dedupPrefix = buildOrderDedupPrefix(currentMember.getId(), cartPromotionItemList);
        // 锁key与防重标记key分离，避免与Redisson内部结构冲突
        String lockKey = dedupPrefix + ":lock";
        String dedupFlagKey = dedupPrefix + ":flag";
        // 事务感知锁：锁保持到事务提交后释放，事务提交后打防重标记
        //锁 = 保证"同一时刻只有一个下单在跑" → 防并发
        return distributedLockService.executeInTransaction(
                lockKey, 0L, -1L, TimeUnit.SECONDS,
                () -> {
                    // 防重窗口校验：短时间内已成功提交过订单则直接拒绝
                    if (Boolean.TRUE.equals(redisService.hasKey(dedupFlagKey))) {
                        Asserts.fail("操作过于频繁，请勿重复提交！");
                    }
                    return generateOrderInternal(orderParam, cartPromotionItemList, currentMember, needDeleteCart, orderType, limitPerUser);
                },
                //防重标记 =保证"5 秒窗口内只能成功一次" → 防连点
                () -> {
                    // 下单成功（事务提交后）：打防重标记（防重窗口内拦截重复提交）
                    redisService.set(dedupFlagKey, "1", ORDER_DEDUP_SECONDS);
                });
    }

    /**
     * 生成订单的公共逻辑
     *
     * @param orderParam            下单参数
     * @param cartPromotionItemList 下单商品(含促销信息)
     * @param currentMember         当前会员
     * @param needDeleteCart        是否删除购物车中已下单的商品
     * @param orderType             订单类型：0->普通订单；1->秒杀订单
     * @param limitPerUser          购买限购数（0 表示不限购）
     */
    private Map<String, Object> generateOrderInternal(OrderParam orderParam, List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember, boolean needDeleteCart, int orderType, int limitPerUser) {
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        //校验收货地址
        if(orderParam.getMemberReceiveAddressId()==null){
            Asserts.fail("请选择收货地址！");
        }
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            //生成下单商品信息
            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductId(cartPromotionItem.getProductId());
            orderItem.setProductName(cartPromotionItem.getProductName());
            orderItem.setProductPic(cartPromotionItem.getProductPic());
            orderItem.setProductAttr(cartPromotionItem.getProductAttr());
            orderItem.setProductBrand(cartPromotionItem.getProductBrand());
            orderItem.setProductSn(cartPromotionItem.getProductSn());
            orderItem.setProductPrice(cartPromotionItem.getPrice());
            orderItem.setProductQuantity(cartPromotionItem.getQuantity());
            orderItem.setProductSkuId(cartPromotionItem.getProductSkuId());
            orderItem.setProductSkuCode(cartPromotionItem.getProductSkuCode());
            orderItem.setProductCategoryId(cartPromotionItem.getProductCategoryId());
            orderItem.setPromotionAmount(cartPromotionItem.getReduceAmount());
            orderItem.setPromotionName(cartPromotionItem.getPromotionMessage());
            orderItem.setGiftIntegration(cartPromotionItem.getIntegration());
            orderItem.setGiftGrowth(cartPromotionItem.getGrowth());
            orderItemList.add(orderItem);
        }
        //判断购物车中商品是否都有库存
        if (!hasStock(cartPromotionItemList)) {
            Asserts.fail("库存不足，无法下单");
        }
        //判断使用使用了优惠券
        if (orderParam.getCouponId() == null) {
            //不用优惠券
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setCouponAmount(new BigDecimal(0));
            }
        } else {
            //使用优惠券
            SmsCouponHistoryDetail couponHistoryDetail = getUseCoupon(cartPromotionItemList, orderParam.getCouponId());
            if (couponHistoryDetail == null) {
                Asserts.fail("该优惠券不可用");
            }
            //对下单商品的优惠券进行处理
            handleCouponAmount(orderItemList, couponHistoryDetail);
        }
        //判断是否使用积分
        if (orderParam.getUseIntegration() == null||orderParam.getUseIntegration().equals(0)) {
            //不使用积分
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setIntegrationAmount(new BigDecimal(0));
            }
        } else {
            //使用积分
            BigDecimal totalAmount = calcTotalAmount(orderItemList);
            BigDecimal integrationAmount = getUseIntegrationAmount(orderParam.getUseIntegration(), totalAmount, currentMember, orderParam.getCouponId() != null);
            if (integrationAmount.compareTo(new BigDecimal(0)) == 0) {
                Asserts.fail("积分不可用");
            } else {
                //可用情况下分摊到可用商品中
                for (OmsOrderItem orderItem : orderItemList) {
                    BigDecimal perAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(integrationAmount);
                    orderItem.setIntegrationAmount(perAmount);
                }
            }
        }
        //计算order_item的实付金额
        handleRealAmount(orderItemList);
        //Redis库存预扣（Lua原子防超卖）+ 限购校验
        Map<Long, Integer> skuQuantityMap = cartPromotionItemList.stream()
                .collect(Collectors.toMap(CartPromotionItem::getProductSkuId, CartPromotionItem::getQuantity, Integer::sum));
        stockCacheService.deductStock(currentMember.getId(), skuQuantityMap, limitPerUser);
        //根据商品合计、运费、活动优惠、优惠券、积分计算应付金额
        OmsOrder order = new OmsOrder();
        order.setDiscountAmount(new BigDecimal(0));
        order.setTotalAmount(calcTotalAmount(orderItemList));
        order.setFreightAmount(new BigDecimal(0));
        order.setPromotionAmount(calcPromotionAmount(orderItemList));
        order.setPromotionInfo(getOrderPromotionInfo(orderItemList));
        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(new BigDecimal(0));
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(calcCouponAmount(orderItemList));
        }
        if (orderParam.getUseIntegration() == null) {
            order.setIntegration(0);
            order.setIntegrationAmount(new BigDecimal(0));
        } else {
            order.setIntegration(orderParam.getUseIntegration());
            order.setIntegrationAmount(calcIntegrationAmount(orderItemList));
        }
        order.setPayAmount(calcPayAmount(order));
        //转化为订单信息并插入数据库
        order.setMemberId(currentMember.getId());
        order.setCreateTime(LocalDateTime.now());
        order.setMemberUsername(currentMember.getPhone());
        //支付方式：0->未支付；1->支付宝；2->微信
        order.setPayType(orderParam.getPayType());
        //订单来源：0->PC订单；1->app订单
        order.setSourceType(1);
        //订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单
        order.setStatus(0);
        //订单类型：0->正常订单；1->秒杀订单
        order.setOrderType(orderType);
        //收货人信息：姓名、电话、地址
        UmsMemberReceiveAddress address = memberReceiveAddressService.getItem(orderParam.getMemberReceiveAddressId());
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverRegion(address.getRegion());
        order.setReceiverDetailAddress(address.getDetailAddress());
        //0->未确认；1->已确认
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        //计算赠送积分
        order.setIntegration(calcGifIntegration(orderItemList));
        //计算赠送成长值
        order.setGrowth(calcGiftGrowth(orderItemList));
        //生成订单号
        order.setOrderSn(generateOrderSn(order));
        //设置自动收货天数
        List<OmsOrderSetting> orderSettings = orderSettingMapper.selectList(new LambdaQueryWrapper<>());
        if(CollUtil.isNotEmpty(orderSettings)){
            order.setAutoConfirmDay(orderSettings.get(0).getConfirmOvertime());
        }
        // TODO: 2018/9/3 bill_*,delivery_*
        //插入order表和order_item表
        baseMapper.insert(order);
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemDao.insertList(orderItemList);
        //如使用优惠券更新优惠券使用状态
        if (orderParam.getCouponId() != null) {
            updateCouponStatus(orderParam.getCouponId(), currentMember.getId(), 1);
        }
        //如使用积分需要扣除积分
        if (orderParam.getUseIntegration() != null) {
            order.setUseIntegration(orderParam.getUseIntegration());
            if(currentMember.getIntegration()==null){
                currentMember.setIntegration(0);
            }
            memberService.updateIntegration(currentMember.getId(), currentMember.getIntegration() - orderParam.getUseIntegration());
        }
        //删除购物车中的下单商品(立即购买不涉及购物车,跳过)
        if (needDeleteCart) {
            deleteCartItemList(cartPromotionItemList, currentMember);
        }
        //注册库存事务回调：提交后发MQ将预扣库存转为数据库锁定库存，回滚时回补Redis预扣库存
        registerStockOperateSync(currentMember.getId(), skuQuantityMap, StockOperateEventPublisher.TYPE_LOCK, order.getId(), orderItemList);
        //发送延迟消息取消订单
        sendDelayMessageCancelOrder(order.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);
        return result;
    }

    @Transactional
    @Override
    public Integer paySuccess(Long orderId, Integer payType) {
        //修改订单支付状态
        OmsOrder order = new OmsOrder();
        order.setId(orderId);
        order.setStatus(1);
        order.setPaymentTime(LocalDateTime.now());
        order.setPayType(payType);
        baseMapper.updateById(order);
        //异步扣减真实库存（stock与lock_stock同时扣减，RabbitMQ解耦）
        OmsOrderDetail orderDetail = portalOrderDao.getDetail(orderId);
        if (orderDetail != null && CollUtil.isNotEmpty(orderDetail.getOrderItemList())) {
            registerStockOperateSync(null, null, StockOperateEventPublisher.TYPE_DEDUCT, orderId, orderDetail.getOrderItemList());
        }
        return 1;
    }

    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        //查询未付款的取消订单
        List<OmsOrder> cancelOrderList = baseMapper.selectList(new LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getId, orderId)
                .eq(OmsOrder::getStatus, 0)
                .eq(OmsOrder::getDeleteStatus, 0));
        if (CollectionUtils.isEmpty(cancelOrderList)) {
            return;
        }
        OmsOrder cancelOrder = cancelOrderList.get(0);
        if (cancelOrder != null) {
            //修改订单状态为取消
            cancelOrder.setStatus(4);
            baseMapper.updateById(cancelOrder);
            List<OmsOrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OmsOrderItem>()
                    .eq(OmsOrderItem::getOrderId, orderId));
            //释放Redis预扣库存并异步释放数据库锁定库存
            if (!CollectionUtils.isEmpty(orderItemList)) {
                Map<Long, Integer> skuQuantityMap = orderItemList.stream()
                        .collect(Collectors.toMap(OmsOrderItem::getProductSkuId, OmsOrderItem::getProductQuantity, Integer::sum));
                stockCacheService.releaseStock(cancelOrder.getMemberId(), skuQuantityMap);
                registerStockOperateSync(null, null, StockOperateEventPublisher.TYPE_RELEASE, orderId, orderItemList);
            }
            //修改优惠券使用状态
            updateCouponStatus(cancelOrder.getCouponId(), cancelOrder.getMemberId(), 0);
            //返还使用积分
            if (cancelOrder.getUseIntegration() != null) {
                UmsMember member = memberService.getById(cancelOrder.getMemberId());
                memberService.updateIntegration(cancelOrder.getMemberId(), member.getIntegration() + cancelOrder.getUseIntegration());
            }
        }
    }

    @Override
    public void sendDelayMessageCancelOrder(Long orderId) {
        //获取订单信息，按订单类型选择超时时间（秒杀订单更短）
        OmsOrderSetting orderSetting = orderSettingMapper.selectById(1L);
        OmsOrder order = baseMapper.selectById(orderId);
        int orderType = (order != null && order.getOrderType() != null) ? order.getOrderType() : 0;
        int overtime = (orderType == 1) ? orderSetting.getFlashOrderOvertime() : orderSetting.getNormalOrderOvertime();
        long delayTimes = overtime * 60 * 1000L;
        //发布延迟消息，TTL 到期后由 OrderCancelConsumer 异步取消订单
        orderCancelEventPublisher.publishOrderCancel(orderId, delayTimes);
    }

    @Override
    public void confirmReceiveOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = baseMapper.selectById(orderId);
        if(!member.getId().equals(order.getMemberId())){
            Asserts.fail("不能确认他人订单！");
        }
        if(order.getStatus()!=2){
            Asserts.fail("该订单还未发货！");
        }
        order.setStatus(3);
        order.setConfirmStatus(1);
        order.setReceiveTime(LocalDateTime.now());
        baseMapper.updateById(order);
    }

    @Override
    public CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize) {
        if(status==-1){
            status = null;
        }
        UmsMember member = memberService.getCurrentMember();
        LambdaQueryWrapper<OmsOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(OmsOrder::getDeleteStatus, 0)
                .eq(OmsOrder::getMemberId, member.getId())
                .orderByDesc(OmsOrder::getCreateTime);
        if(status!=null){
            orderWrapper.eq(OmsOrder::getStatus, status);
        }
        IPage<OmsOrder> orderPage = baseMapper.selectPage(new Page<>(pageNum, pageSize), orderWrapper);
        List<OmsOrder> orderList = orderPage.getRecords();
        //设置分页信息
        CommonPage<OmsOrderDetail> resultPage = new CommonPage<>();
        resultPage.setPageNum((int) orderPage.getCurrent());
        resultPage.setPageSize((int) orderPage.getSize());
        resultPage.setTotal(orderPage.getTotal());
        resultPage.setTotalPage((int) orderPage.getPages());
        if(CollUtil.isEmpty(orderList)){
            return resultPage;
        }
        //设置数据信息
        List<Long> orderIds = orderList.stream().map(OmsOrder::getId).collect(Collectors.toList());
        List<OmsOrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OmsOrderItem>()
                .in(OmsOrderItem::getOrderId, orderIds));
        List<OmsOrderDetail> orderDetailList = new ArrayList<>();
        for (OmsOrder omsOrder : orderList) {
            OmsOrderDetail orderDetail = new OmsOrderDetail();
            BeanUtil.copyProperties(omsOrder,orderDetail);
            List<OmsOrderItem> relatedItemList = orderItemList.stream().filter(item -> item.getOrderId().equals(orderDetail.getId())).collect(Collectors.toList());
            orderDetail.setOrderItemList(relatedItemList);
            orderDetailList.add(orderDetail);
        }
        resultPage.setList(orderDetailList);
        return resultPage;
    }

    @Override
    public OmsOrderDetail detail(Long orderId) {
        OmsOrder omsOrder = baseMapper.selectById(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OmsOrderItem>()
                .eq(OmsOrderItem::getOrderId, orderId));
        OmsOrderDetail orderDetail = new OmsOrderDetail();
        BeanUtil.copyProperties(omsOrder,orderDetail);
        orderDetail.setOrderItemList(orderItemList);
        return orderDetail;
    }

    @Override
    public void deleteOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = baseMapper.selectById(orderId);
        if(!member.getId().equals(order.getMemberId())){
            Asserts.fail("不能删除他人订单！");
        }
        if(order.getStatus()==3||order.getStatus()==4){
            order.setDeleteStatus(1);
            baseMapper.updateById(order);
        }else{
            Asserts.fail("只能删除已完成或已关闭的订单！");
        }
    }

    @Transactional
    @Override
    public void paySuccessByOrderSn(String orderSn, Integer payType) {
        OmsOrder order = getOrderByOrderSn(orderSn);
        if(order!=null){
            this.paySuccess(order.getId(),payType);
        }
    }

    @Override
    public OmsOrder getOrderByOrderSn(String orderSn) {
        List<OmsOrder> orderList = baseMapper.selectList(new LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderSn, orderSn)
                .eq(OmsOrder::getStatus, 0)
                .eq(OmsOrder::getDeleteStatus, 0));
        if(CollUtil.isNotEmpty(orderList)){
            return orderList.get(0);
        }else return null;
    }

    /**
     * 生成18位订单编号:8位日期+2位平台号码+2位支付方式+6位以上自增id
     */
    private String generateOrderSn(OmsOrder order) {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = REDIS_DATABASE+":"+ REDIS_KEY_ORDER_ID + date;
        Long increment = redisService.incr(key, 1);
        sb.append(date);
        sb.append(String.format("%02d", order.getSourceType()));
        sb.append(String.format("%02d", order.getPayType()));
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }

    /**
     * 根据SKU和购买数量构造立即购买的临时购物车条目(不落库)
     */
    private CartPromotionItem getBuyNowCartPromotionItem(Long productSkuId, Integer quantity, UmsMember currentMember) {
        //参数校验
        if (productSkuId == null || quantity == null || quantity <= 0) {
            Asserts.fail("请选择商品规格和购买数量！");
        }
        //查询SKU库存信息
        PmsSkuStock skuStock = skuStockMapper.selectById(productSkuId);
        if (skuStock == null) {
            Asserts.fail("商品SKU不存在！");
        }
        //查询商品信息并校验上架状态
        PmsProduct product = productMapper.selectById(skuStock.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getPublishStatus())) {
            Asserts.fail("商品已下架！");
        }
        //组装临时购物车条目
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setProductId(product.getId());
        cartItem.setProductSkuId(skuStock.getId());
        cartItem.setMemberId(currentMember.getId());
        cartItem.setQuantity(quantity);
        cartItem.setPrice(skuStock.getPrice());
        cartItem.setProductPic(skuStock.getPic() != null ? skuStock.getPic() : product.getPic());
        cartItem.setProductName(product.getName());
        cartItem.setProductSubTitle(product.getSubTitle());
        cartItem.setProductSkuCode(skuStock.getSkuCode());
        cartItem.setProductCategoryId(product.getProductCategoryId());
        cartItem.setProductBrand(product.getBrandName());
        cartItem.setProductSn(product.getProductSn());
        cartItem.setProductAttr(skuStock.getSpData());
        //计算促销信息
        List<CartPromotionItem> promotionItemList = promotionService.calcCartPromotion(Collections.singletonList(cartItem));
        if (CollUtil.isEmpty(promotionItemList)) {
            Asserts.fail("商品信息异常，无法购买！");
        }
        return promotionItemList.get(0);
    }

    /**
     * 校验秒杀活动/场次并组装秒杀下单条目
     * <p>
     * 秒杀下单走独立的限时购入口，价格取秒杀价，不叠加满减/优惠券/积分，
     * 限购数为场次商品配置的固定值 {@code flashPromotionLimit}。
     *
     * @param flashParam    秒杀下单参数（含活动ID、场次ID、SKU、数量）
     * @param currentMember 当前会员
     * @return 秒杀下单条目
     */
    private CartPromotionItem getFlashBuyNowCartPromotionItem(FlashPromotionOrderParam flashParam, UmsMember currentMember) {
        //参数校验
        if (flashParam.getFlashPromotionId() == null || flashParam.getSessionId() == null
                || flashParam.getProductSkuId() == null || flashParam.getQuantity() == null || flashParam.getQuantity() <= 0) {
            Asserts.fail("请选择限时购商品、规格和购买数量！");
        }
        //校验秒杀活动
        SmsFlashPromotion flashPromotion = flashPromotionMapper.selectById(flashParam.getFlashPromotionId());
        if (flashPromotion == null || !Integer.valueOf(1).equals(flashPromotion.getStatus())
                || flashPromotion.getStartDate() == null || flashPromotion.getEndDate() == null) {
            Asserts.fail("限时购活动不存在或未启用！");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(flashPromotion.getStartDate()) || today.isAfter(flashPromotion.getEndDate())) {
            Asserts.fail("限时购活动未开始或已结束！");
        }
        //校验秒杀场次（当前时间需处于场次时间段内）
        SmsFlashPromotionSession session = flashPromotionSessionMapper.selectById(flashParam.getSessionId());
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())) {
            Asserts.fail("限时购场次不存在或未启用！");
        }
        LocalTime nowTime = LocalTime.now();
        if (session.getStartTime() != null && session.getEndTime() != null
                && (nowTime.isBefore(session.getStartTime()) || nowTime.isAfter(session.getEndTime()))) {
            Asserts.fail("当前不在限时购场次时间段内！");
        }
        //查询SKU库存信息
        PmsSkuStock skuStock = skuStockMapper.selectById(flashParam.getProductSkuId());
        if (skuStock == null) {
            Asserts.fail("商品SKU不存在！");
        }
        //查询商品信息并校验上架状态
        PmsProduct product = productMapper.selectById(skuStock.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getPublishStatus())) {
            Asserts.fail("商品已下架！");
        }
        //查询秒杀商品关系配置，取得秒杀价与固定限购数
        SmsFlashPromotionProductRelation relation = flashPromotionRelationMapper.selectList(
                new LambdaQueryWrapper<SmsFlashPromotionProductRelation>()
                        .eq(SmsFlashPromotionProductRelation::getFlashPromotionId, flashPromotion.getId())
                        .eq(SmsFlashPromotionProductRelation::getFlashPromotionSessionId, session.getId())
                        .eq(SmsFlashPromotionProductRelation::getProductId, product.getId()))
                .stream().findFirst().orElse(null);
        if (relation == null || relation.getFlashPromotionPrice() == null) {
            Asserts.fail("该商品不在本次限时购活动中！");
        }
        int limitPerUser = relation.getFlashPromotionLimit() == null ? 0 : relation.getFlashPromotionLimit();
        //组装秒杀下单条目（价格=秒杀价，限购=固定限购数）
        CartPromotionItem cartPromotionItem = new CartPromotionItem();
        cartPromotionItem.setProductId(product.getId());
        cartPromotionItem.setProductSkuId(skuStock.getId());
        cartPromotionItem.setMemberId(currentMember.getId());
        cartPromotionItem.setQuantity(flashParam.getQuantity());
        cartPromotionItem.setPrice(relation.getFlashPromotionPrice());
        cartPromotionItem.setProductPic(skuStock.getPic() != null ? skuStock.getPic() : product.getPic());
        cartPromotionItem.setProductName(product.getName());
        cartPromotionItem.setProductSubTitle(product.getSubTitle());
        cartPromotionItem.setProductSkuCode(skuStock.getSkuCode());
        cartPromotionItem.setProductCategoryId(product.getProductCategoryId());
        cartPromotionItem.setProductBrand(product.getBrandName());
        cartPromotionItem.setProductSn(product.getProductSn());
        cartPromotionItem.setProductAttr(skuStock.getSpData());
        //可用库存（库存-锁定库存）用于本地库存校验
        int lockStock = skuStock.getLockStock() == null ? 0 : skuStock.getLockStock();
        cartPromotionItem.setRealStock(Math.max(skuStock.getStock() - lockStock, 0));
        //秒杀不叠加促销，限购数为固定值
        cartPromotionItem.setFlashPromotionLimit(limitPerUser);
        cartPromotionItem.setPromotionMessage("限时购");
        cartPromotionItem.setReduceAmount(new BigDecimal(0));
        cartPromotionItem.setIntegration(0);
        cartPromotionItem.setGrowth(0);
        return cartPromotionItem;
    }

    /**
     * 删除下单商品的购物车信息
     */
    private void deleteCartItemList(List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember) {
        List<Long> ids = new ArrayList<>();
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            ids.add(cartPromotionItem.getId());
        }
        cartItemService.delete(currentMember.getId(), ids);
    }

    /**
     * 计算该订单赠送的成长值
     */
    private Integer calcGiftGrowth(List<OmsOrderItem> orderItemList) {
        Integer sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum = sum + orderItem.getGiftGrowth() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 计算该订单赠送的积分
     */
    private Integer calcGifIntegration(List<OmsOrderItem> orderItemList) {
        int sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum += orderItem.getGiftIntegration() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 将优惠券信息更改为指定状态
     *
     * @param couponId  优惠券id
     * @param memberId  会员id
     * @param useStatus 0->未使用；1->已使用
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) return;
        //查询第一张优惠券
        List<SmsCouponHistory> couponHistoryList = couponHistoryMapper.selectList(new LambdaQueryWrapper<SmsCouponHistory>()
                .eq(SmsCouponHistory::getMemberId, memberId)
                .eq(SmsCouponHistory::getCouponId, couponId)
                .eq(SmsCouponHistory::getUseStatus, useStatus == 0 ? 1 : 0));
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);
            couponHistory.setUseTime(LocalDateTime.now());
            couponHistory.setUseStatus(useStatus);
            couponHistoryMapper.updateById(couponHistory);
        }
    }

    private void handleRealAmount(List<OmsOrderItem> orderItemList) {
        for (OmsOrderItem orderItem : orderItemList) {
            //原价-促销优惠-优惠券抵扣-积分抵扣
            BigDecimal realAmount = orderItem.getProductPrice()
                    .subtract(orderItem.getPromotionAmount())
                    .subtract(orderItem.getCouponAmount())
                    .subtract(orderItem.getIntegrationAmount());
            orderItem.setRealAmount(realAmount);
        }
    }

    /**
     * 获取订单促销信息
     */
    private String getOrderPromotionInfo(List<OmsOrderItem> orderItemList) {
        StringBuilder sb = new StringBuilder();
        for (OmsOrderItem orderItem : orderItemList) {
            sb.append(orderItem.getPromotionName());
            sb.append(";");
        }
        String result = sb.toString();
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 计算订单应付金额
     */
    private BigDecimal calcPayAmount(OmsOrder order) {
        //总金额+运费-促销优惠-优惠券优惠-积分抵扣
        BigDecimal payAmount = order.getTotalAmount()
                .add(order.getFreightAmount())
                .subtract(order.getPromotionAmount())
                .subtract(order.getCouponAmount())
                .subtract(order.getIntegrationAmount());
        return payAmount;
    }

    /**
     * 计算订单优惠券金额
     */
    private BigDecimal calcIntegrationAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal integrationAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getIntegrationAmount() != null) {
                integrationAmount = integrationAmount.add(orderItem.getIntegrationAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return integrationAmount;
    }

    /**
     * 计算订单优惠券金额
     */
    private BigDecimal calcCouponAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal couponAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getCouponAmount() != null) {
                couponAmount = couponAmount.add(orderItem.getCouponAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return couponAmount;
    }

    /**
     * 计算订单活动优惠
     */
    private BigDecimal calcPromotionAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal promotionAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getPromotionAmount() != null) {
                promotionAmount = promotionAmount.add(orderItem.getPromotionAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return promotionAmount;
    }

    /**
     * 获取可用积分抵扣金额
     *
     * @param useIntegration 使用的积分数量
     * @param totalAmount    订单总金额
     * @param currentMember  使用的用户
     * @param hasCoupon      是否已经使用优惠券
     */
    private BigDecimal getUseIntegrationAmount(Integer useIntegration, BigDecimal totalAmount, UmsMember currentMember, boolean hasCoupon) {
        BigDecimal zeroAmount = new BigDecimal(0);
        //判断用户是否有这么多积分
        if (useIntegration.compareTo(currentMember.getIntegration()) > 0) {
            return zeroAmount;
        }
        //根据积分使用规则判断是否可用
        //是否可与优惠券共用
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectById(1L);
        if (hasCoupon && integrationConsumeSetting.getCouponStatus().equals(0)) {
            //不可与优惠券共用
            return zeroAmount;
        }
        //是否达到最低使用积分门槛
        if (useIntegration.compareTo(integrationConsumeSetting.getUseUnit()) < 0) {
            return zeroAmount;
        }
        //是否超过订单抵用最高百分比
        BigDecimal integrationAmount = new BigDecimal(useIntegration).divide(new BigDecimal(integrationConsumeSetting.getUseUnit()), 2, RoundingMode.HALF_EVEN);
        BigDecimal maxPercent = new BigDecimal(integrationConsumeSetting.getMaxPercentPerOrder()).divide(new BigDecimal(100), 2, RoundingMode.HALF_EVEN);
        if (integrationAmount.compareTo(totalAmount.multiply(maxPercent)) > 0) {
            return zeroAmount;
        }
        return integrationAmount;
    }

    /**
     * 对优惠券优惠进行处理
     *
     * @param orderItemList       order_item列表
     * @param couponHistoryDetail 可用优惠券详情
     */
    private void handleCouponAmount(List<OmsOrderItem> orderItemList, SmsCouponHistoryDetail couponHistoryDetail) {
        SmsCoupon coupon = couponHistoryDetail.getCoupon();
        if (coupon.getUseType().equals(0)) {
            //全场通用
            calcPerCouponAmount(orderItemList, coupon);
        } else if (coupon.getUseType().equals(1)) {
            //指定分类
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 0);
            calcPerCouponAmount(couponOrderItemList, coupon);
        } else if (coupon.getUseType().equals(2)) {
            //指定商品
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 1);
            calcPerCouponAmount(couponOrderItemList, coupon);
        }
    }

    /**
     * 对每个下单商品进行优惠券金额分摊的计算
     *
     * @param orderItemList 可用优惠券的下单商品商品
     */
    private void calcPerCouponAmount(List<OmsOrderItem> orderItemList, SmsCoupon coupon) {
        BigDecimal totalAmount = calcTotalAmount(orderItemList);
        for (OmsOrderItem orderItem : orderItemList) {
            //(商品价格/可用商品总价)*优惠券面额
            BigDecimal couponAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(coupon.getAmount());
            orderItem.setCouponAmount(couponAmount);
        }
    }

    /**
     * 获取与优惠券有关系的下单商品
     *
     * @param couponHistoryDetail 优惠券详情
     * @param orderItemList       下单商品
     * @param type                使用关系类型：0->相关分类；1->指定商品
     */
    private List<OmsOrderItem> getCouponOrderItemByRelation(SmsCouponHistoryDetail couponHistoryDetail, List<OmsOrderItem> orderItemList, int type) {
        List<OmsOrderItem> result = new ArrayList<>();
        if (type == 0) {
            List<Long> categoryIdList = new ArrayList<>();
            for (SmsCouponProductCategoryRelation productCategoryRelation : couponHistoryDetail.getCategoryRelationList()) {
                categoryIdList.add(productCategoryRelation.getProductCategoryId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (categoryIdList.contains(orderItem.getProductCategoryId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        } else if (type == 1) {
            List<Long> productIdList = new ArrayList<>();
            for (SmsCouponProductRelation productRelation : couponHistoryDetail.getProductRelationList()) {
                productIdList.add(productRelation.getProductId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (productIdList.contains(orderItem.getProductId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        }
        return result;
    }

    /**
     * 获取该用户可以使用的优惠券
     *
     * @param cartPromotionItemList 购物车优惠列表
     * @param couponId              使用优惠券id
     */
    private SmsCouponHistoryDetail getUseCoupon(List<CartPromotionItem> cartPromotionItemList, Long couponId) {
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        for (SmsCouponHistoryDetail couponHistoryDetail : couponHistoryDetailList) {
            if (couponHistoryDetail.getCoupon().getId().equals(couponId)) {
                return couponHistoryDetail;
            }
        }
        return null;
    }

    /**
     * 计算总金额
     */
    private BigDecimal calcTotalAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal totalAmount = new BigDecimal("0");
        for (OmsOrderItem item : orderItemList) {
            totalAmount = totalAmount.add(item.getProductPrice().multiply(new BigDecimal(item.getProductQuantity())));
        }
        return totalAmount;
    }

    /**
     * 构建下单防重key前缀：{db}:{orderDedup}:{memberId}:{skuIds}
     * 锁key与防重标记key均基于此前缀
     */
    private String buildOrderDedupPrefix(Long memberId, List<CartPromotionItem> cartPromotionItemList) {
        String skuIds = cartPromotionItemList.stream()
                .map(CartPromotionItem::getProductSkuId)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
        return REDIS_DATABASE + ":" + REDIS_KEY_ORDER_DEDUP + ":" + memberId + ":" + skuIds;
    }

    /**
     * 构建库存异步操作事件
     */
    private StockOperateEvent buildStockOperateEvent(Integer operateType, Long orderId, List<OmsOrderItem> orderItemList) {
        List<StockOperateEvent.StockItem> itemList = orderItemList.stream()
                .map(item -> new StockOperateEvent.StockItem().setProductSkuId(item.getProductSkuId()).setQuantity(item.getProductQuantity()))
                .collect(Collectors.toList());
        return new StockOperateEvent().setOperateType(operateType).setOrderId(orderId).setItemList(itemList);
    }

    /**
     * 注册库存事务回调，统一处理「提交后发MQ」与「回滚时回补预扣库存」
     * <p>
     * - 事务提交（afterCommit）：发送 RabbitMQ 消息异步落库（预扣转锁定 / 扣减 / 释放数据库锁定库存）；
     * - 事务回滚（afterCompletion 非提交状态）：回补 Redis 预扣库存，保证 Redis 与数据库最终一致；
     * - 无事务上下文时直接执行提交动作，避免消息丢失。
     *
     * @param memberId       当前用户（仅回滚回补用；无预扣场景传 null）
     * @param skuQuantityMap 本次预扣数量（仅回滚回补用；无预扣场景传 null）
     * @param operateType    库存操作类型（{@link StockOperateEventPublisher#TYPE_LOCK} 锁定 / TYPE_DEDUCT 扣减 / TYPE_RELEASE 释放）
     * @param orderId        订单ID
     * @param orderItemList  订单条目（用于构建异步落库消息）
     */
    private void registerStockOperateSync(Long memberId, Map<Long, Integer> skuQuantityMap, Integer operateType, Long orderId, List<OmsOrderItem> orderItemList) {
        Runnable commitAction = () -> stockOperateEventPublisher.publishStockOperate(buildStockOperateEvent(operateType, orderId, orderItemList));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    commitAction.run();
                }

                @Override
                public void afterCompletion(int status) {
                    //状态非提交（回滚/未知）即回补Redis预扣库存，保证Redis与数据库最终一致；无预扣场景跳过
                    if (status != TransactionSynchronization.STATUS_COMMITTED && !CollectionUtils.isEmpty(skuQuantityMap)) {
                        stockCacheService.releaseStock(memberId, skuQuantityMap);
                    }
                }
            });
        } else {
            commitAction.run();
        }
    }

    /**
     * 判断下单商品是否都有库存
     */
    private boolean hasStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            if (cartPromotionItem.getRealStock()==null //判断真实库存是否为空
                    ||cartPromotionItem.getRealStock() <= 0 //判断真实库存是否小于0
                    || cartPromotionItem.getRealStock() < cartPromotionItem.getQuantity()) //判断真实库存是否小于下单的数量
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算购物车中商品的价格
     */
    private ConfirmOrderResult.CalcAmount calcCartAmount(List<CartPromotionItem> cartPromotionItemList) {
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();
        calcAmount.setFreightAmount(new BigDecimal(0));
        BigDecimal totalAmount = new BigDecimal("0");
        BigDecimal promotionAmount = new BigDecimal("0");
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            totalAmount = totalAmount.add(cartPromotionItem.getPrice().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
            promotionAmount = promotionAmount.add(cartPromotionItem.getReduceAmount().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
        }
        calcAmount.setTotalAmount(totalAmount);
        calcAmount.setPromotionAmount(promotionAmount);
        calcAmount.setPayAmount(totalAmount.subtract(promotionAmount));
        return calcAmount;
    }

}