package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dto.UmsMessageParam;
import com.macro.mall.mapper.UmsMemberMessageMapper;
import com.macro.mall.mapper.UmsMessageMapper;
import com.macro.mall.model.UmsMemberMessage;
import com.macro.mall.model.UmsMessage;
import com.macro.mall.service.UmsMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息管理Service实现类
 */
@Service
public class UmsMessageServiceImpl extends ServiceImpl<UmsMessageMapper, UmsMessage> implements UmsMessageService {
    @Autowired
    private UmsMemberMessageMapper memberMessageMapper;

    /**
     * 发布消息：写入消息主体；定向消息（sendType=0）额外为每个指定会员写入未读接收记录，
     * 广播消息（sendType=1）不写接收记录，由用户端首次查询时惰性物化
     *
     * @param param 消息发布参数（见接口定义）
     * @return 消息主体影响行数
     */
    @Transactional
    @Override
    public int create(UmsMessageParam param) {
        UmsMessage message = new UmsMessage();
        message.setType(param.getType());
        message.setSendType(param.getSendType());
        message.setTitle(param.getTitle());
        message.setContent(param.getContent());
        message.setBizId(param.getBizId());
        message.setCreateTime(LocalDateTime.now());
        int count = baseMapper.insert(message);
        // 定向消息：立即为指定会员写入接收记录；广播消息由用户端首次查询时惰性物化
        if (param.getSendType() != null && param.getSendType() == 0 && CollUtil.isNotEmpty(param.getMemberIds())) {
            LocalDateTime now = LocalDateTime.now();
            for (Long memberId : param.getMemberIds()) {
                UmsMemberMessage memberMessage = new UmsMemberMessage();
                memberMessage.setMemberId(memberId);
                memberMessage.setMessageId(message.getId());
                memberMessage.setReadStatus(0);
                memberMessage.setDeleted(0);
                memberMessage.setCreateTime(now);
                memberMessageMapper.insert(memberMessage);
            }
        }
        return count;
    }

    /**
     * 分页查询已发布消息列表（按创建时间倒序）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 已发布消息分页结果
     */
    @Override
    public IPage<UmsMessage> list(Integer pageNum, Integer pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UmsMessage>().orderByDesc(UmsMessage::getCreateTime));
    }
}
