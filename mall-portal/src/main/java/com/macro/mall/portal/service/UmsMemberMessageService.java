package com.macro.mall.portal.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.model.UmsMemberMessage;
import com.macro.mall.portal.domain.PortalMessageDetail;

import java.util.List;

/**
 * 会员消息管理Service
 */
public interface UmsMemberMessageService extends IService<UmsMemberMessage> {

    /**
     * 分页获取当前用户的消息列表
     *
     * @param type     消息类型：1->系统通知；2->订单通知；3->营销通知，为空查询全部
     * @param pageNum  页码
     * @param pageSize 每页数量
     */
    CommonPage<PortalMessageDetail> list(Integer type, Integer pageNum, Integer pageSize);

    /**
     * 获取当前用户的未读消息数量
     */
    Integer unreadCount();

    /**
     * 将指定消息标记为已读
     */
    void read(Long messageId);

    /**
     * 将当前用户指定类型（或全部）消息标记为已读
     */
    void readAll(Integer type);

    /**
     * 发送订单通知（定向消息）
     *
     * @param memberId 接收会员ID
     * @param orderId  订单ID（作为业务ID）
     * @param title    消息标题
     * @param content  消息内容
     */
    void sendOrderNotice(Long memberId, Long orderId, String title, String content);

    /**
     * 发送营销通知（定向，如通知引导领取优惠券）
     *
     * @param memberId 接收会员ID
     * @param bizId    业务ID（如优惠券ID，前端据此跳转领券页）
     * @param title    消息标题
     * @param content  消息内容
     */
    void sendMarketingNotice(Long memberId, Long bizId, String title, String content);

    /**
     * 批量发送营销通知（定向）
     *
     * @param memberIds 接收会员ID列表
     * @param bizId     业务ID（如优惠券ID）
     * @param title     消息标题
     * @param content   消息内容
     */
    void sendMarketingNoticeToMembers(List<Long> memberIds, Long bizId, String title, String content);

    /**
     * 发送营销通知（广播，如促销开售提醒，用户首查时惰性物化）
     *
     * @param title   消息标题
     * @param content 消息内容
     * @param bizId   业务ID（如优惠券ID）
     */
    void sendMarketingBroadcast(String title, String content, Long bizId);
}
