package dev.foodfleet.ch37.ordersvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.foodfleet.ch37.ordersvc.orders.Order;
import dev.foodfleet.ch37.ordersvc.orders.OrderStore;
import dev.foodfleet.ch37.ordersvc.security.TokenAuthoritiesConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole filter chain and method security, with tokens shaped like Keycloak's. No identity provider is needed:
 * spring-security-test puts a decoded JWT in place of a real one; the decoder (signature, issuer, audience) is
 * Spring Boot's and is covered by chapter 36's verifier tests.
 */
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://id.foodfleet.example/realms/foodfleet")
@AutoConfigureMockMvc
class OrderServiceSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired OrderStore store;

    @BeforeEach
    void orders() {
        store.save(new Order(1, "c01", 7, "DELIVERED", 45000));
    }

    static JwtRequestPostProcessor token(String subject, String scope, String... roles) {
        return jwt().jwt(j -> j.subject(subject).claim("scope", scope)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new TokenAuthoritiesConverter());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/orders/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void theRightScopeIsNeededForTheUrl() throws Exception {
        mvc.perform(get("/orders/1").with(token("c01", "profile:read", "CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/orders/1").with(token("c01", "orders:read", "CUSTOMER"))).andExpect(status().isOk());
    }

    @Test
    void anotherCustomersOrderIsNotFound() throws Exception {
        mvc.perform(get("/orders/1").with(token("c02", "orders:read", "CUSTOMER"))).andExpect(status().isNotFound());
    }

    @Test
    void refundsNeedTheSupportRole() throws Exception {
        mvc.perform(post("/orders/1/refund").with(token("c01", "orders:write", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/1/refund").with(token("s1", "orders:write", "SUPPORT"))).andExpect(status().isOk());
    }

    @Test
    void anEndpointWithoutARuleIsClosed() throws Exception {
        mvc.perform(get("/internal/debug").with(token("a1", "orders:read", "ADMIN"))).andExpect(status().isForbidden());
    }
}
