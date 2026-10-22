package dev.foodfleet.lab13;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** The Lua scripts, loaded from the files verify/redis_lab.py exercises. Spring sends EVALSHA and falls back to EVAL. */
@Configuration
class RedisScripts {

    @Bean
    @SuppressWarnings("rawtypes")
    RedisScript<List> tokenBucket() {
        return RedisScript.of(new ClassPathResource("redis/token-bucket.lua"), List.class);
    }

    @Bean
    RedisScript<Long> unlock() {
        return RedisScript.of(new ClassPathResource("redis/unlock.lua"), Long.class);
    }
}
