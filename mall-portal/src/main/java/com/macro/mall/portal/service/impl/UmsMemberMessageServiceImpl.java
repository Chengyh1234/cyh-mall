package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.mapper.UmsMemberMessageMapper;
import com.macro.mall.mapper.UmsMessageMapper;
import com.macro.mall.model.UmsMemberMessage;
import com.macro.mall.model.UmsMessage;
import com.macro.mall.portal.domain.PortalMessageDetail;
import com.macro.mall.portal.service.UmsMemberMessageService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员消息管理Service实现类
 */
@Service
public class UmsMemberMessageServiceImpl extends ServiceImpl<UmsMemberMessageMapper, UmsMemberMessage> implements UmsMemberMessageService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private UmsMessageMapper messageMapper;

    /**
     * 获取当前登录会员ID
     *
     * @return 当前登录会员ID
     */
    private Long getMemberId() {
        return memberService.getCurrentMember().getId();
    }

    /**
     * 广播消息惰性物化：将当前用户尚未有接收记录的广播消息写入其接收表，
     * 记录时间取消息创建时间，保证与定向消息统一按时间倒序排列
     * 简单意思为：发一条广播给全站用户，用户使用才开始加载消息
     *
     * @param memberId 当前用户ID
     */
    private void materializeBroadcast(Long memberId) {
        // 当前用户已存在的消息ID集合
        List<Long> existMessageIds = this.list(new LambdaQueryWrapper<UmsMemberMessage>()
                        .eq(UmsMemberMessage::getMemberId, memberId))
                .stream().map(UmsMemberMessage::getMessageId).collect(Collectors.toList());
        // 查询当前用户尚未接收的广播消息
        LambdaQueryWrapper<UmsMessage> wrapper = new LambdaQueryWrapper<UmsMessage>()
                .eq(UmsMessage::getSendType, 1);
        if (CollUtil.isNotEmpty(existMessageIds)) {
            wrapper.notIn(UmsMessage::getId, existMessageIds);
        }
        List<UmsMessage> broadcastMessages = messageMapper.selectList(wrapper);
        if (CollUtil.isEmpty(broadcastMessages)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (UmsMessage message : broadcastMessages) {
            UmsMemberMessage memberMessage = new UmsMemberMessage();
            memberMessage.setMemberId(memberId);
            memberMessage.setMessageId(message.getId());
            memberMessage.setReadStatus(0);
            memberMessage.setDeleted(0);
            memberMessage.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : now);
            this.save(memberMessage);
        }
    }

    /**
     * 分页获取当前用户的消息列表：先物化广播消息，再按类型（可选）过滤、按时间倒序分页，
     * 并组装消息详情返回
     *
     * @param type     消息类型：1->系统通知；2->订单通知；3->营销通知，为空查询全部
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 消息详情分页结果
     */
    @Override
    public CommonPage<PortalMessageDetail> list(Integer type, Integer pageNum, Integer pageSize) {
        Long memberId = getMemberId();
        materializeBroadcast(memberId);
        // 按类型过滤：先查出该类型消息ID集合，再按接收记录分页
        List<Long> typeMessageIds = null;
        LambdaQueryWrapper<UmsMemberMessage> wrapper = new LambdaQueryWrapper<UmsMemberMessage>()
                .eq(UmsMemberMessage::getMemberId, memberId)
                .eq(UmsMemberMessage::getDeleted, 0)
                .orderByDesc(UmsMemberMessage::getCreateTime);
        if (type != null) {
            typeMessageIds = messageMapper.selectList(new LambdaQueryWrapper<UmsMessage>()
                            .eq(UmsMessage::getType, type))
                    .stream().map(UmsMessage::getId).collect(Collectors.toList());
            // 指定类型下没有消息：直接返回空分页，避免过滤条件失效而退化查出全部类型
            if (CollUtil.isEmpty(typeMessageIds)) {
                return CommonPage.restPage(new Page<>(pageNum, pageSize, 0));
            }
            wrapper.in(UmsMemberMessage::getMessageId, typeMessageIds);
        }
        IPage<UmsMemberMessage> result = this.page(new Page<>(pageNum, pageSize), wrapper);
        // 组装消息详情
        List<PortalMessageDetail> detailList = buildDetailList(result.getRecords());
        Page<PortalMessageDetail> detailPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        detailPage.setRecords(detailList);
        return CommonPage.restPage(detailPage);
    }

    /**
     * 将接收记录与消息主体组装为消息详情列表：批量查询消息主体并按ID建立映射，逐条填充详情
     *
     * @param records 当前用户的消息接收记录列表
     * @return 消息详情列表
     */
    private List<PortalMessageDetail> buildDetailList(List<UmsMemberMessage> records) {
        if (CollUtil.isEmpty(records)) {
            return new ArrayList<>();
        }
        List<Long> messageIds = records.stream().map(UmsMemberMessage::getMessageId).collect(Collectors.toList());
        Map<Long, UmsMessage> messageMap = messageMapper.selectBatchIds(messageIds)
                .stream().collect(Collectors.toMap(UmsMessage::getId, item -> item));
        return records.stream().map(record -> {
            UmsMessage message = messageMap.get(record.getMessageId());
            PortalMessageDetail detail = new PortalMessageDetail();
            detail.setId(record.getMessageId());
            detail.setType(message != null ? message.getType() : null);
            detail.setTitle(message != null ? message.getTitle() : null);
            detail.setContent(message != null ? message.getContent() : null);
            detail.setBizId(message != null ? message.getBizId() : null);
            detail.setReadStatus(record.getReadStatus());
            detail.setCreateTime(record.getCreateTime());
            return detail;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前用户的未读消息数量：先物化广播消息，再统计未读且未删除的接收记录
     *
     * @return 未读消息数量
     */
    @Override
    public Integer unreadCount() {
        Long memberId = getMemberId();
        materializeBroadcast(memberId);
        Long count = this.count(new LambdaQueryWrapper<UmsMemberMessage>()
                .eq(UmsMemberMessage::getMemberId, memberId)
                .eq(UmsMemberMessage::getReadStatus, 0)
                .eq(UmsMemberMessage::getDeleted, 0));
        return count.intValue();
    }

    /**
     * 将当前用户的指定消息标记为已读
     *
     * @param messageId 消息ID
     */
    @Override
    public void read(Long messageId) {
        Long memberId = getMemberId();
        this.update(new LambdaUpdateWrapper<UmsMemberMessage>()
                .eq(UmsMemberMessage::getMemberId, memberId)
                .eq(UmsMemberMessage::getMessageId, messageId)
                .set(UmsMemberMessage::getReadStatus, 1));
    }

    /**
     * 将当前用户指定类型（或全部）的未读消息标记为已读
     *
     * @param type 消息类型：1->系统通知；2->订单通知；3->营销通知，为空标记全部已读
     */
    @Override
    public void readAll(Integer type) {
        Long memberId = getMemberId();
        LambdaUpdateWrapper<UmsMemberMessage> wrapper = new LambdaUpdateWrapper<UmsMemberMessage>()
                .eq(UmsMemberMessage::getMemberId, memberId)
                .eq(UmsMemberMessage::getReadStatus, 0)
                .set(UmsMemberMessage::getReadStatus, 1);
        if (type != null) {
            List<Long> typeMessageIds = messageMapper.selectList(new LambdaQueryWrapper<UmsMessage>()
                            .eq(UmsMessage::getType, type))
                    .stream().map(UmsMessage::getId).collect(Collectors.toList());
            if (CollUtil.isEmpty(typeMessageIds)) {
                return;
            }
            wrapper.in(UmsMemberMessage::getMessageId, typeMessageIds);
        }
        this.update(wrapper);
    }

    /**
     * 发送订单通知：写入一条 type=2（订单通知）的定向消息主体（bizId 为订单ID），
     * 并立即为该会员写入一条未读接收记录
     *
     * @param memberId 接收会员ID
     * @param orderId  订单ID（作为业务ID，前端据此跳转订单详情）
     * @param title    消息标题
     * @param content  消息内容
     */
    @Override
    public void sendOrderNotice(Long memberId, Long orderId, String title, String content) {
        // 写入消息主体（定向）
        UmsMessage message = new UmsMessage();
        message.setType(2);
        message.setSendType(0);
        message.setTitle(title);
        message.setContent(content);
        message.setBizId(String.valueOf(orderId));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        // 写入当前用户的接收记录
        UmsMemberMessage memberMessage = new UmsMemberMessage();
        memberMessage.setMemberId(memberId);
        memberMessage.setMessageId(message.getId());
        memberMessage.setReadStatus(0);
        memberMessage.setDeleted(0);
        memberMessage.setCreateTime(LocalDateTime.now());
        this.save(memberMessage);
    }

    @Override
    public void sendMarketingNotice(Long memberId, Long bizId, String title, String content) {
        // 写入消息主体（定向，type=3 营销通知）
        UmsMessage message = new UmsMessage();
        message.setType(3);
        message.setSendType(0);
        message.setTitle(title);
        message.setContent(content);
        message.setBizId(String.valueOf(bizId));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        // 写入接收会员的接收记录
        UmsMemberMessage memberMessage = new UmsMemberMessage();
        memberMessage.setMemberId(memberId);
        memberMessage.setMessageId(message.getId());
        memberMessage.setReadStatus(0);
        memberMessage.setDeleted(0);
        memberMessage.setCreateTime(LocalDateTime.now());
        this.save(memberMessage);
    }

    @Override
    public void sendMarketingNoticeToMembers(List<Long> memberIds, Long bizId, String title, String content) {
        if (CollUtil.isEmpty(memberIds)) {
            return;
        }
        for (Long memberId : memberIds) {
            sendMarketingNotice(memberId, bizId, title, content);
        }
    }

    @Override
    public void sendMarketingBroadcast(String title, String content, Long bizId) {
        // 仅写入消息主体（type=3 广播），用户首查时惰性物化
        UmsMessage message = new UmsMessage();
        message.setType(3);
        message.setSendType(1);
        message.setTitle(title);
        message.setContent(content);
        message.setBizId(String.valueOf(bizId));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
