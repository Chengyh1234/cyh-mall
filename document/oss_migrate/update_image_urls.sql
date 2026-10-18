-- ============================================================================
-- mall 存量图片 URL 域名替换脚本
-- ----------------------------------------------------------------------------
-- 前提：已执行 migrate_oss_images.py 将图片上传到自己的 OSS 桶
-- 作用：把数据库中所有指向原作者桶的图片 URL 域名，替换为自己的桶
--
--   源域名: macro-oss.oss-cn-shenzhen.aliyuncs.com （深圳，原作者）
--   目标域名: mall-oss-cyh.oss-cn-beijing.aliyuncs.com （你的桶）
--
-- 执行方式（任选其一）:
--   mysql -uroot -p mall < update_image_urls.sql
--   或在 Navicat / DataGrip 中打开本文件直接运行
--
-- 说明:
--   * REPLACE 只匹配域名字符串（不含协议），http/https 前缀均可覆盖
--   * pms_product.detail_html / detail_mobile_html 为富文本，内含多张图，
--     REPLACE 会一次替换全部
--   * 所有 UPDATE 均带 WHERE 条件，只更新受影响行，可放心重复执行（幂等）
--   * 下方 ALTER 为前置修复：新域名比旧域名长 2 个字符，pms_product.album_pics
--     为 varchar(255) 却要存多张图 URL 拼接，替换会超长报错，故先扩到 1000。
--     该语句幂等，重复执行无副作用。
-- ============================================================================

-- 0. 前置：扩大 pms_product.album_pics 字段长度
ALTER TABLE pms_product
  MODIFY COLUMN `album_pics` varchar(1000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '画册图片，连产品图片限制为5张，以逗号分割';

-- 1. oms_cart_item 购物车表
UPDATE oms_cart_item SET product_pic = REPLACE(product_pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE product_pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 2. oms_order_item 订单商品表
UPDATE oms_order_item SET product_pic = REPLACE(product_pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE product_pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 3. oms_order_return_apply 退货申请单表
UPDATE oms_order_return_apply SET product_pic = REPLACE(product_pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE product_pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';
UPDATE oms_order_return_apply SET proof_pics = REPLACE(proof_pics, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE proof_pics LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 4. pms_brand 品牌表
UPDATE pms_brand SET logo = REPLACE(logo, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE logo LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';
UPDATE pms_brand SET big_pic = REPLACE(big_pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE big_pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 5. pms_product 商品表（含主图、相册 JSON、PC/移动端富文本详情）
UPDATE pms_product SET pic = REPLACE(pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';
UPDATE pms_product SET album_pics = REPLACE(album_pics, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE album_pics LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';
UPDATE pms_product SET detail_html = REPLACE(detail_html, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE detail_html LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';
UPDATE pms_product SET detail_mobile_html = REPLACE(detail_mobile_html, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE detail_mobile_html LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 6. pms_product_category 商品分类表
UPDATE pms_product_category SET icon = REPLACE(icon, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE icon LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 7. pms_sku_stock 商品 SKU 库存表
UPDATE pms_sku_stock SET pic = REPLACE(pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 8. sms_home_advertise 首页轮播广告表
UPDATE sms_home_advertise SET pic = REPLACE(pic, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE pic LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 9. ums_admin 后台用户表
UPDATE ums_admin SET icon = REPLACE(icon, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE icon LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- 10. ums_member 会员表
UPDATE ums_member SET icon = REPLACE(icon, 'macro-oss.oss-cn-shenzhen.aliyuncs.com', 'mall-oss-cyh.oss-cn-beijing.aliyuncs.com')
WHERE icon LIKE '%macro-oss.oss-cn-shenzhen.aliyuncs.com%';

-- ============================================================================
-- 验证：执行下面查询，应返回 0 行（无残留引用即替换成功）
--   SELECT 'oms_cart_item' t, COUNT(*) c FROM oms_cart_item WHERE product_pic LIKE '%macro-oss%'
--   UNION ALL SELECT 'oms_order_item', COUNT(*) FROM oms_order_item WHERE product_pic LIKE '%macro-oss%'
--   UNION ALL SELECT 'oms_order_return_apply', COUNT(*) FROM oms_order_return_apply WHERE product_pic LIKE '%macro-oss%' OR proof_pics LIKE '%macro-oss%'
--   UNION ALL SELECT 'pms_brand', COUNT(*) FROM pms_brand WHERE logo LIKE '%macro-oss%' OR big_pic LIKE '%macro-oss%'
--   UNION ALL SELECT 'pms_product', COUNT(*) FROM pms_product WHERE pic LIKE '%macro-oss%' OR album_pics LIKE '%macro-oss%' OR detail_html LIKE '%macro-oss%' OR detail_mobile_html LIKE '%macro-oss%'
--   UNION ALL SELECT 'pms_product_category', COUNT(*) FROM pms_product_category WHERE icon LIKE '%macro-oss%'
--   UNION ALL SELECT 'pms_sku_stock', COUNT(*) FROM pms_sku_stock WHERE pic LIKE '%macro-oss%'
--   UNION ALL SELECT 'sms_home_advertise', COUNT(*) FROM sms_home_advertise WHERE pic LIKE '%macro-oss%'
--   UNION ALL SELECT 'ums_admin', COUNT(*) FROM ums_admin WHERE icon LIKE '%macro-oss%'
--   UNION ALL SELECT 'ums_member', COUNT(*) FROM ums_member WHERE icon LIKE '%macro-oss%';
-- ============================================================================
