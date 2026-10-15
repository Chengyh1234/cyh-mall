package com.macro.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 商品限时购与商品关系表 Mapper 接口
 * </p>
 *
 * @author cyh
 * @since 2026-10-02
 */
public interface SmsFlashPromotionProductRelationMapper extends BaseMapper<SmsFlashPromotionProductRelation> {

    /**
     * 原子扣减秒杀专属库存（flash_promotion_count），剩余量不足时影响行数为 0
     *
     * @param id       秒杀商品关系ID
     * @param quantity 扣减数量
     * @return 受影响行数（0 表示库存不足）
     */
    int deductFlashCount(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 回补秒杀专属库存（订单取消/超时关闭时）
     *
     * @param id       秒杀商品关系ID
     * @param quantity 回补数量
     * @return 受影响行数
     */
    int increaseFlashCount(@Param("id") Long id, @Param("quantity") Integer quantity);
}
