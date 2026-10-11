package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 购物车管理Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl extends ServiceImpl<OmsCartItemMapper, OmsCartItem> implements OmsCartItemService {
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisService redisService;
    @Value("${redis.database}")
    private String redisDatabase;
    @Value("${redis.key.cartDedup}")
    private String redisKeyCartDedup;
    @Value("${redis.key.cart}")
    private String redisKeyCart;
    @Value("${redis.expire.cart}")
    private Long redisCartExpire;
    @Value("${purchase.cart-dedup-seconds:3}")
    private long cartDedupSeconds;

    @Transactional
    @Override
    public int add(OmsCartItem cartItem) {
        int count;
        UmsMember currentMember =memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        //加购防抖：同一用户对同一SKU在防抖窗口内重复点击只生效一次
        if (cartItem.getProductSkuId() != null) {
            String dedupKey = redisDatabase + ":" + redisKeyCartDedup + ":" + currentMember.getId() + ":" + cartItem.getProductSkuId();
            Boolean firstClick = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", cartDedupSeconds, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(firstClick)) {
                Asserts.fail("操作过于频繁，请稍后再试！");
            }
        }
        OmsCartItem existCartItem = getCartItem(cartItem);
        if (existCartItem == null) {
            cartItem.setCreateDate(LocalDateTime.now());
            count = baseMapper.insert(cartItem);
        } else {
            cartItem.setModifyDate(LocalDateTime.now());
            existCartItem.setQuantity(existCartItem.getQuantity() + cartItem.getQuantity());
            count = baseMapper.updateById(existCartItem);
        }
        if (count > 0) {
            // 写库成功后删除购物车缓存，下次读取自动回填（Cache Aside）
            delCartCache(currentMember.getId());
        }
        return count;
    }

    /**
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        LambdaQueryWrapper<OmsCartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OmsCartItem::getMemberId, cartItem.getMemberId())
                .eq(OmsCartItem::getProductId, cartItem.getProductId())
                .eq(OmsCartItem::getDeleteStatus, 0);
        if (cartItem.getProductSkuId()!=null) {
            wrapper.eq(OmsCartItem::getProductSkuId, cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = baseMapper.selectList(wrapper);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        String key = getCartKey(memberId);
        // 1. 先读缓存，命中直接返回（空列表也被缓存，避免缓存穿透）
        try {
            Object cached = redisService.get(key);
            if (cached != null) {
                return (List<OmsCartItem>) cached;
            }
        } catch (Exception e) {
            // Redis 异常时降级查库，保证购物车核心功能可用
        }
        // 2. 缓存未命中，查 MySQL 并回填缓存
        List<OmsCartItem> cartItemList = baseMapper.selectList(new LambdaQueryWrapper<OmsCartItem>()
                .eq(OmsCartItem::getDeleteStatus, 0)
                .eq(OmsCartItem::getMemberId, memberId));
        try {
            redisService.set(key, cartItemList, redisCartExpire);
        } catch (Exception e) {
            // 回填失败不影响主流程，下次读取重试
        }
        return cartItemList;
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        int count = baseMapper.update(cartItem, new LambdaQueryWrapper<OmsCartItem>()
                .eq(OmsCartItem::getDeleteStatus, 0)
                .eq(OmsCartItem::getId, id)
                .eq(OmsCartItem::getMemberId, memberId));
        if (count > 0) {
            delCartCache(memberId);
        }
        return count;
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        int count = baseMapper.update(record, new LambdaQueryWrapper<OmsCartItem>()
                .in(OmsCartItem::getId, ids)
                .eq(OmsCartItem::getMemberId, memberId));
        if (count > 0) {
            delCartCache(memberId);
        }
        return count;
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Transactional
    @Override
    public int updateAttr(OmsCartItem cartItem) {
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(LocalDateTime.now());
        updateCart.setDeleteStatus(1);
        baseMapper.updateById(updateCart);
        cartItem.setId(null);
        add(cartItem);
        return 1;
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        int count = baseMapper.update(record, new LambdaQueryWrapper<OmsCartItem>()
                .eq(OmsCartItem::getMemberId, memberId));
        if (count > 0) {
            delCartCache(memberId);
        }
        return count;
    }

    /**
     * 获取用户购物车缓存的 Redis key
     */
    private String getCartKey(Long memberId) {
        return redisDatabase + ":" + redisKeyCart + ":" + memberId;
    }

    /**
     * 删除用户购物车缓存（Cache Aside：写库成功后删除缓存，下次读取自动回填）
     */
    private void delCartCache(Long memberId) {
        try {
            redisService.del(getCartKey(memberId));
        } catch (Exception e) {
            // 删除失败最多导致短暂脏读，TTL 兜底
        }
    }
}