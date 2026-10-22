package dev.foodfleet.ch32.info;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    /**
     * Spring Boot builds the RedisCacheManager from spring.cache.redis.*; this makes it transaction-aware: puts
     * and evictions made inside a transaction happen after it commits. Without it, an eviction inside
     * {@code @Transactional} runs before the commit, and a concurrent reader can put the old row straight back.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer transactionAwareCaches() {
        return builder -> builder.transactionAware();
    }
}
