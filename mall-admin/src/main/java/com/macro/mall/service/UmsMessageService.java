package com.macro.mall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.macro.mall.dto.UmsMessageParam;
import com.macro.mall.model.UmsMessage;

/**
 * 消息管理Service
 */
public interface UmsMessageService extends IService<UmsMessage> {

    /**
     * 发布消息：定向消息（sendType=0）立即为指定会员写入接收记录，广播消息（sendType=1）只写消息主体，
     * 由用户端首次查询时惰性物化生成接收记录
     *
     * @param param 消息发布参数：type 消息类型、sendType 发送类型（0定向/1广播）、title 消息标题、
     *              content 消息内容、bizId 业务ID、memberIds 定向发送的会员ID列表（sendType=0时必填）
     * @return 消息主体影响行数
     */
    int create(UmsMessageParam param);

    /**
     * 分页查询已发布消息列表（按创建时间倒序）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 已发布消息分页结果
     */
    IPage<UmsMessage> list(Integer pageNum, Integer pageSize);
}
