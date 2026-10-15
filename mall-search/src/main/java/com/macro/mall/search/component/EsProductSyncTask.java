package com.macro.mall.search.component;

import com.macro.mall.search.service.EsProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ES 商品索引兜底全量同步任务
 * <p>
 * 每天凌晨执行一次全量从 MySQL {@code importAll}，修复 MQ 丢消息 / 漏消费导致的增量
 * 索引不一致，作为增量同步的对账兜底。需配合 {@code @EnableScheduling}（已在启动类开启）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsProductSyncTask {

    private final EsProductService esProductService;

    /**
     * 每天 03:30 全量同步商品索引
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void syncAllProduct() {
        try {
            log.info("开始兜底全量同步商品 ES 索引...");
            int count = esProductService.importAll();
            log.info("兜底全量同步商品 ES 索引完成, 同步 {} 条", count);
        } catch (Exception e) {
            log.error("兜底全量同步商品 ES 索引失败, 异常: {}", e.getMessage(), e);
        }
    }
}