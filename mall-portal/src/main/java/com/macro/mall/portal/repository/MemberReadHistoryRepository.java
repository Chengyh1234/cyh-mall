package com.macro.mall.portal.repository;

import com.macro.mall.portal.domain.MemberReadHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 会员商品浏览历史Repository
 * Created by macro on 2018/8/3.
 */
public interface MemberReadHistoryRepository extends MongoRepository<MemberReadHistory, String> {
    /**
     * 根据会员ID分页查找记录
     */
    Page<MemberReadHistory> findByMemberIdOrderByCreateTimeDesc(Long memberId, Pageable pageable);

    /**
     * 根据会员ID和商品ID查找记录（用于浏览去重，重复浏览时更新创建时间）
     */
    Optional<MemberReadHistory> findByMemberIdAndProductId(Long memberId, Long productId);

    /**
     * 根据会员ID删除记录
     */
    void deleteAllByMemberId(Long memberId);
}
