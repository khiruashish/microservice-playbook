package dev.foodfleet.ch03.refund;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(RefundController.class)
@Import({RefundLedger.class, RefundConfiguration.class})
class RefundControllerTest {

    private final MockMvcTester mvc;
    private final RefundLedger ledger;

    RefundControllerTest(@Autowired MockMvcTester mvc, @Autowired RefundLedger ledger) {
        this.mvc = mvc;
        this.ledger = ledger;
    }

    @Test
    void clientRetryAfterTimeoutGetsTheSameRefund_moneyMovesOnce() throws Exception {
        MvcTestResult first = refund("rfnd-7f3a9c21", "order-101", 34_900);
        MvcTestResult retry = refund("rfnd-7f3a9c21", "order-101", 34_900);

        assertThat(first).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "false");
        assertThat(retry).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
        assertThat(refundId(retry)).isEqualTo(refundId(first));
        assertThat(ledger.issued()).filteredOn(r -> r.orderId().equals("order-101")).hasSize(1);
    }

    @Test
    void sameKeyDifferentAmountIs422() {
        refund("rfnd-0b5e44d1", "order-202", 12_000);

        assertThat(refund("rfnd-0b5e44d1", "order-202", 99_000))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .bodyJson().extractingPath("$.title").isEqualTo("Idempotency-Key reused with a different request");
    }

    @Test
    void missingKeyIs400() {
        assertThat(mvc.post().uri("/api/v1/refunds").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "order-303", "amountPaise": 5000}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedKeyIs400() {
        assertThat(refund("x", "order-404", 5_000)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult refund(String key, String orderId, long amountPaise) {
        return mvc.post().uri("/api/v1/refunds")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "%s", "amountPaise": %d}
                        """.formatted(orderId, amountPaise))
                .exchange();
    }

    private static String refundId(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refundId");
    }
}
