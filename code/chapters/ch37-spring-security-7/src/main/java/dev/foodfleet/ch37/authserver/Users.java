package dev.foodfleet.ch37.authserver;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Development users and the password encoder. New hashes are bcrypt at strength 12; hashes stored as
 * {@code {bcrypt}} at lower strength or as {@code {pbkdf2@SpringSecurity_v5_8}} still match and are upgraded
 * on the next successful login (measured in this chapter with Spring Security's own encoders).
 */
@Configuration(proxyBeanMethods = false)
public class Users {

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /** Passwords from the environment, never from the repository; development only. */
    @Bean
    UserDetailsService users(PasswordEncoder encoder, @Value("${app.dev-password}") String password) {
        return new InMemoryUserDetailsManager(
                User.withUsername("c01").password(encoder.encode(password)).roles("CUSTOMER").build(),
                User.withUsername("support1").password(encoder.encode(password)).roles("SUPPORT").build());
    }
}
