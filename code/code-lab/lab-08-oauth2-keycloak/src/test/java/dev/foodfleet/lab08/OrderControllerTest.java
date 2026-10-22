package dev.foodfleet.lab08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.foodfleet.lab08.security.SecurityConfig;

/** Each authorization rule, with tokens built in the test: no Keycloak needed to prove the rules. */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, Orders.class, OrderAccess.class})
class OrderControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MenuClient menus;

    @Test
    void noTokenIs401() {
        assertThat(mvc.get().uri("/orders/o-1")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theRightScopeIsRequired() {
        assertThat(mvc.get().uri("/orders/o-1").with(jwt().jwt(j -> j.subject("customer-asha"))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aCustomerReadsTheirOwnOrder() {
        assertThat(mvc.get().uri("/orders/o-1").with(asCustomer("customer-asha")))
                .hasStatusOk().bodyJson().extractingPath("$.totalPaise").isEqualTo(64700);
    }

    @Test
    void anotherCustomersOrderLooksLikeNoOrder() {
        assertThat(mvc.get().uri("/orders/o-2").with(asCustomer("customer-asha"))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/orders/o-404").with(asCustomer("customer-asha"))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void supportReadsAnyOrder() {
        assertThat(mvc.get().uri("/orders/o-2").with(jwt().jwt(j -> j.subject("agent-7"))
                .authorities(new SimpleGrantedAuthority("SCOPE_orders:read"), new SimpleGrantedAuthority("ROLE_SUPPORT"))))
                .hasStatusOk();
    }

    @Test
    void unlistedEndpointsAreClosed() {
        assertThat(mvc.delete().uri("/orders/o-1").with(asCustomer("customer-asha"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor asCustomer(String id) {
        return jwt().jwt(j -> j.subject(id)).authorities(new SimpleGrantedAuthority("SCOPE_orders:read"));
    }
}
