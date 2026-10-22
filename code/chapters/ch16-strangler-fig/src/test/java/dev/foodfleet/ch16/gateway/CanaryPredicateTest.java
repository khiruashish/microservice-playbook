package dev.foodfleet.ch16.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch16.strangler.Cohorts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

class CanaryPredicateTest {

    private static ServerRequest request(String customerId) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/api/pricing/quote");
        if (customerId != null) {
            servlet.addHeader(CanaryPredicate.CUSTOMER_ID, customerId);
        }
        return ServerRequest.create(servlet, List.of());
    }

    @Test
    void customersInTheCohortGoToTheNewService() {
        String inside = customer(true);
        String outside = customer(false);

        assertThat(new CanaryPredicate(10).test(request(inside))).isTrue();
        assertThat(new CanaryPredicate(10).test(request(outside))).isFalse();
    }

    @Test
    void zeroAndHundredPercentMeanNobodyAndEverybody() {
        assertThat(new CanaryPredicate(0).test(request("c-1"))).isFalse();
        assertThat(new CanaryPredicate(100).test(request("c-1"))).isTrue();
    }

    @Test
    void anonymousRequestsStayOnTheMonolith() {
        assertThat(new CanaryPredicate(100).test(request(null))).isFalse();
    }

    private static String customer(boolean inCanary) {
        for (int i = 0; ; i++) {
            if (Cohorts.inRollout("c-" + i, 10) == inCanary) {
                return "c-" + i;
            }
        }
    }
}
