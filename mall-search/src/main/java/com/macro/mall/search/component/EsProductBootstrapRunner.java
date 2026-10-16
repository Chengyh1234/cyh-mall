package com.macro.mall.search.component;

import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.service.EsProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * ES 商品索引启动懒初始化任务
 * <p>
 * 启动时检查 {@code pms} 索引是否存在，仅当索引缺失（首次部署 / 索引被误删）时才创建
 * 索引并全量导入，日常重启直接跳过。初始化失败只告警不阻塞启动，交由
 * {@link EsProductSyncTask} 每日 03:30 兜底全量同步修复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsProductBootstrapRunner implements ApplicationRunner {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final EsProductService esProductService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOperations = elasticsearchTemplate.indexOps(EsProduct.class);
            if (indexOperations.exists()) {
                log.info("ES 商品索引已存在, 跳过启动初始化");
                return;
            }
            // 索引缺失：先创建索引与 mapping，再全量导入
            log.info("ES 商品索引不存在, 开始创建索引并全量导入...");
            indexOperations.create();
            indexOperations.putMapping(indexOperations.createMapping());
            int count = esProductService.importAll();
            log.info("ES 商品索引启动初始化完成, 导入 {} 条", count);
        } catch (Exception e) {
            // 初始化失败不阻塞服务启动，缺口由每日兜底全量同步任务修复
            log.error("ES 商品索引启动初始化失败, 异常: {}", e.getMessage(), e);
        }
    }
}
