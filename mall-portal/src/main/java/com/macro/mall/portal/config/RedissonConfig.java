package com.macro.mall.portal.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 * <p>
 * 仅用于获取分布式锁（RLock），独立连接 Redis，不接管 Spring Data Redis 的连接工厂，
 * 避免影响现有的 {@code RedisTemplate}/{@code RedisService} 序列化行为。
 * 连接信息复用 {@code spring.data.redis.*} 配置。
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16);
        // 设置密码（空则不设置）
        if (StrUtil.isNotEmpty(redisProperties.getPassword())) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }
}
