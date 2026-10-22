package dev.foodfleet.ch26.tracking;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * The rider endpoint. Allowed origins are left at the default (same origin only), which blocks
 * other websites' pages from opening it in a browser; the native rider app sends no Origin header.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RiderLocationHandler riderLocations;

    public WebSocketConfig(RiderLocationHandler riderLocations) {
        this.riderLocations = riderLocations;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(riderLocations, "/ws/rider").addInterceptors(new RiderIdentityInterceptor());
    }
}
