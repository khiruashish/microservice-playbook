package dev.foodfleet.ch42.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch42.resilience.Deadline;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DeadlineFilterTest {

    @Test
    void expiredRequestsAreRefusedBeforeAnyWork() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/orders/1");
        req.addHeader(Deadline.HEADER, System.currentTimeMillis() - 10);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new DeadlineFilter(Duration.ofSeconds(2)).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(req.getAttribute(DeadlineFilter.ATTRIBUTE)).isNull();
    }

    @Test
    void aCallersDeadlineIsKeptAndOurBudgetCapsIt() throws Exception {
        long now = System.currentTimeMillis();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/orders/1");
        req.addHeader(Deadline.HEADER, now + 60_000);
        new DeadlineFilter(Duration.ofSeconds(2)).doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        Deadline d = (Deadline) req.getAttribute(DeadlineFilter.ATTRIBUTE);
        assertThat(d.epochMillis()).isBetween(now + 1_900, now + 2_100);
        MockHttpServletRequest none = new MockHttpServletRequest("GET", "/orders/1");
        new DeadlineFilter(Duration.ofSeconds(2)).doFilter(none, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(none.getAttribute(DeadlineFilter.ATTRIBUTE)).isNotNull();
    }
}
