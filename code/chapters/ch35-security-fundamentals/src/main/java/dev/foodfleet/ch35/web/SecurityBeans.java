package dev.foodfleet.ch35.web;

import dev.foodfleet.ch35.auth.LoginService;
import dev.foodfleet.ch35.auth.LoginThrottle;
import dev.foodfleet.ch35.auth.PasswordHasher;
import dev.foodfleet.ch35.auth.UserDirectory;
import dev.foodfleet.ch35.identity.TokenService;
import dev.foodfleet.ch35.orders.OrderAccessPolicy;
import dev.foodfleet.ch35.orders.OrderService;
import dev.foodfleet.ch35.orders.OrderStore;
import dev.foodfleet.ch35.orders.PageLimits;
import dev.foodfleet.ch35.outbound.OutboundUrlPolicy;
import dev.foodfleet.ch35.outbound.WebhookClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free security code into the Spring application. */
@Configuration(proxyBeanMethods = false)
public class SecurityBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The key comes from the environment (chapter 39: from Vault), never from the repository. */
    @Bean
    TokenService tokenService(@Value("${app.security.token-key}") String base64Key, Clock clock) {
        return new TokenService(Base64.getDecoder().decode(base64Key), clock, Duration.ofMinutes(15));
    }

    @Bean
    OrderStore orderStore() {
        return new OrderStore();
    }

    @Bean
    OrderService orderService(OrderStore store) {
        return new OrderService(store, new OrderAccessPolicy(), PageLimits.DEFAULT);
    }

    @Bean
    UserDirectory userDirectory() {
        return new UserDirectory();
    }

    @Bean
    LoginService loginService(UserDirectory users, TokenService tokens, Clock clock) {
        return new LoginService(users, new PasswordHasher(), LoginThrottle.standard(clock), tokens);
    }

    @Bean
    WebhookClient webhookClient() {
        return new WebhookClient(OutboundUrlPolicy.webhooks());
    }
}
