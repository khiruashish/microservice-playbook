package dev.foodfleet.ch44.web;

import com.netflix.concurrency.limits.Limiter;
import dev.foodfleet.ch44.degrade.Brownout;
import dev.foodfleet.ch44.limit.AdaptiveLimits;
import dev.foodfleet.ch44.limit.Priority;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** order-service's overload protection: admission control first in the filter chain, and the brownout ticker. */
@Configuration
@EnableScheduling
public class OverloadConfig {

    private final Brownout recommendations = new Brownout(0.1, 0.05);

    @Bean
    Limiter<Priority> admissionLimiter() {
        // slow above 250 ms: half of the 500 ms the edge gives order-service
        return AdaptiveLimits.byPriority("order-service", Duration.ofMillis(250));
    }

    @Bean
    FilterRegistrationBean<AdmissionFilter> admissionFilter(Limiter<Priority> admissionLimiter) {
        FilterRegistrationBean<AdmissionFilter> bean = new FilterRegistrationBean<>(new AdmissionFilter(admissionLimiter));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);   // before security and everything that costs work
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    Brownout recommendationsBrownout() {
        return recommendations;
    }

    @Scheduled(fixedRate = 1000)
    void adjustBrownout() {
        recommendations.adjust();
    }
}
