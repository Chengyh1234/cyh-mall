package com.macro.mall.common.mq.event;

/**
 * 商品 ES 索引变更动作
 * <p>
 * 由商品业务侧在数据库变更后发出，搜索服务据此维护 ES 索引：
 * <ul>
 *     <li>{@link #CREATED} 商品创建 / 重新上架：写入或更新索引</li>
 *     <li>{@link #UPDATED} 商品信息更新：重新拉取并更新索引</li>
 *     <li>{@link #DELETED} 商品删除 / 下架：从索引移除</li>
 * </ul>
 */
public enum ProductEsChangeAction {

    /** 商品新增 / 上架，写入或更新索引 */
    CREATED,

    /** 商品信息变更，重新同步索引 */
    UPDATED,

    /** 商品删除 / 下架，从索引移除 */
    DELETED
}