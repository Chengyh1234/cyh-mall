package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;

/**
 * 会员信息缓存业务类
 * Created by macro on 2020/3/14.
 */
public interface UmsMemberCacheService {
    /**
     * 删除会员用户缓存
     */
    void delMember(Long memberId);

    /**
     * 获取会员用户缓存
     */
    UmsMember getMember(String username);

    /**
     * 设置会员用户缓存
     */
    void setMember(UmsMember member);
}
