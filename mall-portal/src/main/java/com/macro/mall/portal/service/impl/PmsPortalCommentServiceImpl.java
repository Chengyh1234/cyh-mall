package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.PmsCommentMapper;
import com.macro.mall.mapper.PmsCommentReplayMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsCommentReplay;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.PmsCommentDetail;
import com.macro.mall.portal.domain.PmsCommentParam;
import com.macro.mall.portal.domain.PmsCommentReplayParam;
import com.macro.mall.portal.service.PmsPortalCommentService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 前台商品评论管理Service实现类
 * Created by cyh on 2026/10/3.
 */
@Service
public class PmsPortalCommentServiceImpl extends ServiceImpl<PmsCommentMapper, PmsComment> implements PmsPortalCommentService {
    @Autowired
    private PmsCommentReplayMapper commentReplayMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public IPage<PmsCommentDetail> list(Long productId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PmsComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsComment::getProductId, productId)
                .eq(PmsComment::getShowStatus, 1)
                .orderByDesc(PmsComment::getCreateTime);
        IPage<PmsComment> commentPage = this.page(new Page<>(pageNum, pageSize), wrapper);
        // 将评论转为详情并填充回复列表
        List<PmsCommentDetail> detailList = commentPage.getRecords().stream()
                .map(comment -> convertDetail(comment))
                .collect(Collectors.toList());
        Page<PmsCommentDetail> detailPage = new Page<>(commentPage.getCurrent(), commentPage.getSize(), commentPage.getTotal());
        detailPage.setRecords(detailList);
        return detailPage;
    }

    @Override
    public PmsCommentDetail detail(Long id) {
        PmsComment comment = this.getById(id);
        if (comment == null) {
            return null;
        }
        // 累加阅读数
        comment.setReadCount((comment.getReadCount() == null ? 0 : comment.getReadCount()) + 1);
        this.updateById(comment);
        return convertDetail(comment);
    }

    @Override
    public IPage<PmsCommentDetail> my(int pageNum, int pageSize) {
        UmsMember member = memberService.getCurrentMember();
        LambdaQueryWrapper<PmsComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsComment::getMemberId, member.getId())
                .orderByDesc(PmsComment::getCreateTime);
        Page<PmsComment> commentPage = this.page(new Page<>(pageNum, pageSize), wrapper);
        return commentPage.convert(this::toDetailWithProduct);
    }

    /**
     * 评论转换为详情并填充商品展示图片
     */
    private PmsCommentDetail toDetailWithProduct(PmsComment comment) {
        PmsCommentDetail detail = new PmsCommentDetail();
        BeanUtils.copyProperties(comment, detail);
        PmsProduct product = productMapper.selectById(comment.getProductId());
        if (product != null) {
            detail.setProductPic(product.getPic());
        }
        return detail;
    }

    @Override
    public String checkCommentPermission(PmsCommentParam commentParam) {
        if (commentParam.getProductId() == null) {
            return "商品ID不能为空";
        }
        UmsMember member = memberService.getCurrentMember();
        if (findEligibleOrder(member.getId(), commentParam.getProductId()) != null) {
            return null;
        }
        // 区分"已评价过"与"无购买资格"
        if (hasCommented(member.getId(), commentParam.getProductId())) {
            return "该商品已评价过，请勿重复评价";
        }
        return "仅购买过该商品且订单已完成的用户可评价";
    }

    @Override
    @Transactional
    public int add(PmsCommentParam commentParam) {
        // 基础参数校验
        if (commentParam.getProductId() == null || StrUtil.isEmpty(commentParam.getContent())) {
            return 0;
        }
        if (commentParam.getStar() == null || commentParam.getStar() < 0 || commentParam.getStar() > 5) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        // 购买资格校验：仅已完成且未评价过的订单用户可评价
        OmsOrder eligibleOrder = findEligibleOrder(member.getId(), commentParam.getProductId());
        if (eligibleOrder == null) {
            return 0;
        }
        PmsComment comment = new PmsComment();
        comment.setMemberId(member.getId());
        comment.setProductId(commentParam.getProductId());
        comment.setMemberNickName(member.getNickname());
        comment.setMemberIcon(member.getIcon());
        comment.setStar(commentParam.getStar());
        comment.setContent(commentParam.getContent());
        comment.setPics(commentParam.getPics());
        comment.setProductAttribute(commentParam.getProductAttribute());
        comment.setCreateTime(LocalDateTime.now());
        // 前台发表默认展示，待管理员审核可另行调整
        comment.setShowStatus(1);
        comment.setCollectCouont(0);
        comment.setReadCount(0);
        comment.setReplayCount(0);
        // 填充商品名称，便于评论展示
        PmsProduct product = productMapper.selectById(commentParam.getProductId());
        if (product != null) {
            comment.setProductName(product.getName());
        }
        int count = this.save(comment) ? 1 : 0;
        if (count > 0) {
            // 标记订单已评价，防止重复评价
            eligibleOrder.setCommentTime(LocalDateTime.now());
            orderMapper.updateById(eligibleOrder);
        }
        return count;
    }

    /**
     * 查找当前会员"已完成（status=3）且未评价（comment_time为空）"的订单
     */
    private OmsOrder findEligibleOrder(Long memberId, Long productId) {
        LambdaQueryWrapper<OmsOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OmsOrderItem::getProductId, productId);
        List<OmsOrderItem> orderItems = orderItemMapper.selectList(itemWrapper);
        for (OmsOrderItem orderItem : orderItems) {
            OmsOrder order = orderMapper.selectById(orderItem.getOrderId());
            if (order == null) {
                continue;
            }
            if (!Objects.equals(order.getMemberId(), memberId)) {
                continue;
            }
            if (order.getDeleteStatus() != null && order.getDeleteStatus() == 1) {
                continue;
            }
            if (order.getStatus() == null || order.getStatus() != 3) {
                continue;
            }
            if (order.getCommentTime() != null) {
                continue;
            }
            return order;
        }
        return null;
    }

    /**
     * 当前会员是否已评价过该商品（存在已完成且已评价的订单）
     */
    private boolean hasCommented(Long memberId, Long productId) {
        LambdaQueryWrapper<OmsOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OmsOrderItem::getProductId, productId);
        List<OmsOrderItem> orderItems = orderItemMapper.selectList(itemWrapper);
        for (OmsOrderItem orderItem : orderItems) {
            OmsOrder order = orderMapper.selectById(orderItem.getOrderId());
            if (order == null) {
                continue;
            }
            if (!Objects.equals(order.getMemberId(), memberId)) {
                continue;
            }
            if (order.getStatus() != null && order.getStatus() == 3 && order.getCommentTime() != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public int replay(PmsCommentReplayParam replayParam) {
        if (replayParam.getCommentId() == null || StrUtil.isEmpty(replayParam.getContent())) {
            return 0;
        }
        PmsComment comment = this.getById(replayParam.getCommentId());
        if (comment == null) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        PmsCommentReplay replay = new PmsCommentReplay();
        replay.setCommentId(replayParam.getCommentId());
        replay.setMemberNickName(member.getNickname());
        replay.setMemberIcon(member.getIcon());
        replay.setContent(replayParam.getContent());
        replay.setCreateTime(LocalDateTime.now());
        // 评论人员类型：0->会员
        replay.setType(0);
        int count = commentReplayMapper.insert(replay);
        if (count > 0) {
            // 同步累加评论的回复数
            comment.setReplayCount((comment.getReplayCount() == null ? 0 : comment.getReplayCount()) + 1);
            this.updateById(comment);
        }
        return count;
    }

    @Override
    public int like(Long id) {
        LambdaUpdateWrapper<PmsComment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PmsComment::getId, id)
                .setSql("collect_couont = collect_couont + 1");
        return this.update(wrapper) ? 1 : 0;
    }

    /**
     * 将评论实体转为详情对象，并填充其回复列表
     */
    private PmsCommentDetail convertDetail(PmsComment comment) {
        PmsCommentDetail detail = new PmsCommentDetail();
        BeanUtils.copyProperties(comment, detail);
        detail.setReplayList(queryReplayList(comment.getId()));
        return detail;
    }

    /**
     * 按创建时间正序查询评论的回复列表
     */
    private List<PmsCommentReplay> queryReplayList(Long commentId) {
        LambdaQueryWrapper<PmsCommentReplay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmsCommentReplay::getCommentId, commentId)
                .orderByAsc(PmsCommentReplay::getCreateTime);
        return commentReplayMapper.selectList(wrapper);
    }
}
