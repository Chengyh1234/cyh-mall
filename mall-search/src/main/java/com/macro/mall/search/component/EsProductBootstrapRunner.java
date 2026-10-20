package com.macro.mall.search.component;

import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.service.EsProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * ES 商品索引启动懒初始化任务
 * <p>
 * 启动时检查 {@code pms} 索引是否存在，仅当索引缺失（首次部署 / 索引被误删）或
 * 存在但无数据时才创建索引并全量导入，日常重启索引有数据则直接跳过。初始化失败
 * 只告警不阻塞启动，交由 {@link EsProductSyncTask} 每日 03:30 兜底全量同步修复。
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
            // 存在且有数据才跳过；存在但无数据或缺失均需初始化
            if (indexOperations.exists() && elasticsearchTemplate.count(Query.findAll(), EsProduct.class) > 0) {
                log.info("ES 商品索引已存在且有数据, 跳过启动初始化");
                return;
            }
            log.info("ES 商品索引不存在或为空, 开始初始化...");
            if (!indexOperations.exists()) {
                // 索引缺失：先创建索引与 mapping，再全量导入
                indexOperations.create();
                indexOperations.putMapping(indexOperations.createMapping());
            }
            int count = esProductService.importAll();
            log.info("ES 商品索引启动初始化完成, 导入 {} 条", count);
        } catch (Exception e) {
            // 初始化失败不阻塞服务启动，缺口由每日兜底全量同步任务修复
            log.error("ES 商品索引启动初始化失败, 异常: {}", e.getMessage(), e);
        }
    }
}
