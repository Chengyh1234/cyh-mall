package com.macro.mall.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 搜索服务启动类
 * <p>
 * {@code scanBasePackages = "com.macro.mall"}：扫描 mall-common（标识、常量）与本模块
 * 的配置 / MQ 组件。{@code @EnableScheduling} 开启全量兜底同步定时任务。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.macro.mall")
public class MallSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSearchApplication.class, args);
    }
}
