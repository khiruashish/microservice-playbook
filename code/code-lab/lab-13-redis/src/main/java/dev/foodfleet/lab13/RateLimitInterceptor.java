package dev.foodfleet.lab13;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 429 with Retry-After when a tenant's bucket is empty. The tenant comes from the gateway's X-Tenant header. */
@Configuration
class RateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final TenantRateLimiter.Limit MENU_READS = new TenantRateLimiter.Limit(20, 10);

    private final TenantRateLimiter limiter;

    RateLimitInterceptor(TenantRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenant = request.getHeader("X-Tenant");
        TenantRateLimiter.Decision decision = limiter.tryAcquire(tenant == null ? "anonymous" : tenant, "menus", MENU_READS);
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, (decision.retryAfterMillis() + 999) / 1000)));
        return false;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/menus/**");
    }
}
