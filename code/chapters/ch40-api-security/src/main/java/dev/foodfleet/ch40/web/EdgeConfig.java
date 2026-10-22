package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.cors.CorsPolicy;
import dev.foodfleet.ch40.csrf.FetchMetadataPolicy;
import dev.foodfleet.ch40.ratelimit.GcraLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Layer;
import dev.foodfleet.ch40.ratelimit.RedisGcraLimiter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EdgeProperties.class)
class EdgeConfig {

    /** Validates the configured origins (exact, https, no patterns) before Spring ever sees them. */
    @Bean
    CorsPolicy corsPolicy(EdgeProperties edge) {
        EdgeProperties.Cors c = edge.cors();
        return CorsPolicy.of(c.allowedOrigins(), c.allowedMethods(), c.allowedHeaders(), c.exposedHeaders(), true, c.maxAge());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsPolicy policy, EdgeProperties edge) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(new ArrayList<>(policy.origins()));      // exact origins, not allowedOriginPatterns
        cors.setAllowedMethods(new ArrayList<>(edge.cors().allowedMethods()));
        cors.setAllowedHeaders(new ArrayList<>(edge.cors().allowedHeaders()));
        cors.setExposedHeaders(edge.cors().exposedHeaders());
        cors.setAllowCredentials(true);
        cors.setMaxAge(edge.cors().maxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    FetchMetadataPolicy fetchMetadataPolicy(CorsPolicy trusted) {
        return new FetchMetadataPolicy(trusted);
    }

    /** Per client, then per tenant, shared by every instance through Redis. */
    @Bean
    LayeredLimiter sharedLimits(EdgeProperties edge, StringRedisTemplate redis) {
        RedisScript<List> script = RedisScript.of(RedisGcraLimiter.SCRIPT, List.class);
        @SuppressWarnings("unchecked")
        RedisGcraLimiter.ScriptRunner runner = (s, keys, args) -> (List<Long>) redis.execute(script, keys, args.toArray());
        return new LayeredLimiter(List.of(
                new Layer(redisLimiter("client", edge.perClient(), runner), c -> c.tenant() + "/" + c.client()),
                new Layer(redisLimiter("tenant", edge.perTenant(), runner), LayeredLimiter.Caller::tenant)));
    }

    /**
     * What this instance enforces on its own while Redis is unreachable: the shared limits divided by the number
     * of instances. Not exact (traffic is not spread perfectly), but bounded, which failing open is not.
     */
    @Bean
    LayeredLimiter fallbackLimits(EdgeProperties edge) {
        int n = Math.max(1, edge.instances());
        return new LayeredLimiter(List.of(
                new Layer(localLimiter("client", edge.perClient(), n), c -> c.tenant() + "/" + c.client()),
                new Layer(localLimiter("tenant", edge.perTenant(), n), LayeredLimiter.Caller::tenant)));
    }

    @Bean
    GcraLimiter anonymousLimits(EdgeProperties edge) {
        EdgeProperties.Limit l = edge.anonymous();
        return new GcraLimiter("anonymous", l.requests(), l.window(), l.burst());
    }

    private static RedisGcraLimiter redisLimiter(String name, EdgeProperties.Limit l, RedisGcraLimiter.ScriptRunner runner) {
        return new RedisGcraLimiter(name, l.requests(), l.window(), l.burst(), runner);
    }

    private static GcraLimiter localLimiter(String name, EdgeProperties.Limit l, int instances) {
        return new GcraLimiter(name, Math.max(1, l.requests() / instances), l.window(), Math.max(1, l.burst() / instances));
    }
}
