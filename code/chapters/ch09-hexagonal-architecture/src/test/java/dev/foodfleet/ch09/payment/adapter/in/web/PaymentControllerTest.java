package dev.foodfleet.ch09.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch09.payment.adapter.out.gateway.FakePaymentGateway;
import dev.foodfleet.ch09.payment.application.service.PaymentService;
import dev.foodfleet.ch09.payment.support.InMemoryPaymentRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The web adapter with the real core behind it and fake driven adapters: tests the HTTP contract
 * and the mapping of core outcomes to status codes, without a database or a provider.
 */
@WebMvcTest(PaymentController.class)
@Import(PaymentControllerTest.Hexagon.class)
class PaymentControllerTest {

    @TestConfiguration
    static class Hexagon {
        @Bean
        PaymentService paymentService() {
            return new PaymentService(new InMemoryPaymentRepository(), new FakePaymentGateway(), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvcTester mvc;

    private static final String BODY = """
            {"orderId": "o-1", "amountPaise": 87185, "method": {"type": "UPI", "reference": "ravi@okaxis"}}""";

    @Test
    void authorizeIsIdempotentOverHttp() {
        var first = mvc.post().uri("/api/payments").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();
        var retry = mvc.post().uri("/api/payments").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();

        assertThat(first).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("AUTHORIZED");
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(retry.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
    }

    @Test
    void reusingAKeyForADifferentAmountIs409() {
        mvc.post().uri("/api/payments").header("Idempotency-Key", "k-2")
                .contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();

        assertThat(mvc.post().uri("/api/payments").header("Idempotency-Key", "k-2")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("87185", "99000")))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void domainRulesAre422AndMissingKeyIs400() {
        assertThat(mvc.post().uri("/api/payments").header("Idempotency-Key", "k-3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("ravi@okaxis", "not-a-upi-id")))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .bodyJson().extractingPath("$.title").isEqualTo("Payment rule violated");

        assertThat(mvc.post().uri("/api/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownPaymentIs404() {
        assertThat(mvc.get().uri("/api/payments/pay_missing")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
