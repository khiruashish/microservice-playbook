package dev.foodfleet.ch22.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/** The generated client against a mocked server: URLs, headers, problem mapping, 304 handling. */
class OrderHistoryApiTest {

    private MockRestServiceServer server;
    private OrderHistoryApi api;

    @BeforeEach
    void client() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://order-history")
                .defaultStatusHandler(HttpStatusCode::isError, new ProblemDetailErrorHandler(JsonMapper.builder().build()));
        server = MockRestServiceServer.bindTo(builder).build();
        api = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build()
                .createClient(OrderHistoryApi.class);
    }

    @Test
    void aProblemBecomesAnExceptionTheCallerCanSwitchOn() {
        server.expect(requestTo("http://order-history/api/customers/c-1/orders?limit=20&cursor=bad"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                        {"type":"https://docs.foodfleet.example/problems/invalid-cursor","title":"Invalid page cursor",
                         "status":400,"detail":"Use the 'next' link from the previous page as is."}"""));

        assertThatThrownBy(() -> api.page("c-1", 20, "bad"))
                .isInstanceOfSatisfying(ApiProblemException.class, p -> {
                    assertThat(p.is("invalid-cursor")).isTrue();
                    assertThat(p.status()).isEqualTo(400);
                });
    }

    @Test
    void anythingElseKeepsItsStatusAndBody() {
        server.expect(requestTo("http://order-history/api/orders/o-1"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                        .body("<html>upstream connect error</html>"));

        assertThatThrownBy(() -> api.order("o-1", null))
                .isInstanceOf(RestClientResponseException.class)
                .hasMessageContaining("502")
                .hasMessageContaining("upstream connect error");
    }

    @Test
    void asecondReadRevalidatesWithTheETagAndUsesTheCacheOn304() {
        OrderHistoryReader reader = new OrderHistoryReader(api);
        HttpHeaders etag = new HttpHeaders();
        etag.setETag("\"3\"");
        server.expect(requestTo("http://order-history/api/orders/o-1"))
                .andRespond(withSuccess("""
                        {"id":"o-1","customerId":"c-1","restaurant":"Truffles","totalPaise":32000,"status":"DELIVERED",
                         "placedAt":"2026-10-02T13:00:00Z","deliveryInstructions":"Ring twice","version":3}""",
                        MediaType.APPLICATION_JSON).headers(etag));
        server.expect(requestTo("http://order-history/api/orders/o-1"))
                .andExpect(header("If-None-Match", "\"3\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED).headers(etag));

        assertThat(reader.order("o-1").orElseThrow().deliveryInstructions()).isEqualTo("Ring twice");
        assertThat(reader.order("o-1").orElseThrow().deliveryInstructions()).isEqualTo("Ring twice");
        server.verify();
    }
}
