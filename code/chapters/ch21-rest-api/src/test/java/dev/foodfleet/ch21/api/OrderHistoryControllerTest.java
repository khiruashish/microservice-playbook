package dev.foodfleet.ch21.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch21.orders.OrderSummary;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(OrderHistoryController.class)
@Import(OrderHistoryControllerTest.Stores.class)
class OrderHistoryControllerTest {

    @TestConfiguration
    static class Stores {
        @Bean
        InMemoryOrderStore orderStore() {
            return new InMemoryOrderStore();
        }
    }

    @Autowired MockMvcTester mvc;
    @Autowired InMemoryOrderStore store;

    @BeforeEach
    void fiveOrders() {
        for (int i = 1; i <= 5; i++) {
            store.put(new OrderSummary("o-" + i, "c-1", "Restaurant " + i, 10_000L * i, "DELIVERED",
                    Instant.parse("2026-10-0" + i + "T12:00:00Z"), null, 1));
        }
    }

    @Test
    void pagesFollowTheNextLinkToTheEnd() {
        MvcTestResult first = mvc.get().uri("/api/customers/c-1/orders?limit=2").exchange();
        assertThat(first).hasStatusOk().containsHeader("Link")
                .bodyJson().extractingPath("$.items[*].id").asArray().containsExactly("o-5", "o-4");
        String next = JsonPath.read(first, "$.next");

        MvcTestResult second = mvc.get().uri(next).exchange();
        assertThat(second).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly("o-3", "o-2");

        MvcTestResult last = mvc.get().uri(JsonPath.read(second, "$.next")).exchange();
        assertThat(last).doesNotContainHeader("Link")
                .bodyJson().extractingPath("$.items[*].id").asArray().containsExactly("o-1");
        assertThat(last).bodyJson().extractingPath("$.next").isNull();
    }

    @Test
    void aTamperedCursorIsAProblemNotA500() {
        assertThat(mvc.get().uri("/api/customers/c-1/orders?cursor=not-a-cursor"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.type").isEqualTo("https://docs.foodfleet.example/problems/invalid-cursor");
    }

    @Test
    void anOversizedPageIsRejectedWithTheAllowedRange() {
        assertThat(mvc.get().uri("/api/customers/c-1/orders?limit=500"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.maximum").isEqualTo(100);
    }

    @Test
    void anUnchangedOrderIsA304() {
        assertThat(mvc.get().uri("/api/orders/o-3")).hasStatusOk().headers().hasValue("ETag", "\"1\"");

        assertThat(mvc.get().uri("/api/orders/o-3").header("If-None-Match", "\"1\""))
                .hasStatus(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void updatesNeedIfMatch() {
        assertThat(mvc.patch().uri("/api/orders/o-3/delivery-instructions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"instructions\":\"Ring twice\"}"))
                .hasStatus(HttpStatus.PRECONDITION_REQUIRED);
    }

    @Test
    void aStaleIfMatchIsA412WithTheCurrentETag() {
        assertThat(mvc.patch().uri("/api/orders/o-3/delivery-instructions").header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"instructions\":\"Ring twice\"}"))
                .hasStatusOk().headers().hasValue("ETag", "\"2\"");

        // a second client still holding version 1
        assertThat(mvc.patch().uri("/api/orders/o-3/delivery-instructions").header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"instructions\":\"Leave at gate\"}"))
                .hasStatus(HttpStatus.PRECONDITION_FAILED)
                .bodyJson().extractingPath("$.currentEtag").isEqualTo("\"2\"");
    }

    @Test
    void anUnknownOrderIsA404Problem() {
        assertThat(mvc.get().uri("/api/orders/o-404"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Order not found");
    }

    /** Reads a JSON path from a result's body. */
    static final class JsonPath {
        static String read(MvcTestResult result, String path) {
            try {
                return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
