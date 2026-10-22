package dev.foodfleet.ch02.menu.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Exercises the real {@code @Retryable} proxy (a tiny Spring context, no Boot) against a mocked
 * pricing-service. Each test pins one rule: what is retried, what is not, and when we give up.
 */
@SpringJUnitConfig(RemoteSurgeLookupTest.TestConfig.class)
class RemoteSurgeLookupTest {

    private static final String URL = "http://pricing-service/api/v1/surge/BLR-KORAMANGALA";

    private final SurgeLookup lookup;
    private final MockRestServiceServer pricingService;

    RemoteSurgeLookupTest(@Autowired SurgeLookup lookup, @Autowired MockRestServiceServer pricingService) {
        this.lookup = lookup;
        this.pricingService = pricingService;
    }

    @BeforeEach
    void reset() {
        pricingService.reset();
    }

    @Test
    void transient5xxIsRetriedThenSucceeds() {
        pricingService.expect(once(), requestTo(URL)).andRespond(withServerError());
        pricingService.expect(once(), requestTo(URL)).andRespond(withSuccess(surgeJson(12_500), MediaType.APPLICATION_JSON));

        assertThat(lookup.multiplierBps("BLR-KORAMANGALA")).isEqualTo(12_500);
        pricingService.verify();
    }

    @Test
    void givesUpAfterTwoRetries() {
        pricingService.expect(times(3), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> lookup.multiplierBps("BLR-KORAMANGALA"))
                .isInstanceOf(HttpServerErrorException.class);
        pricingService.verify();
    }

    @Test
    void clientErrorsAreNotRetried() {
        pricingService.expect(once(), requestTo(URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> lookup.multiplierBps("BLR-KORAMANGALA"))
                .isInstanceOf(HttpClientErrorException.class);
        pricingService.verify();
    }

    @Test
    void outOfContractValueIsRejectedAndNotRetried() {
        pricingService.expect(once(), requestTo(URL)).andRespond(withSuccess(surgeJson(50_000), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> lookup.multiplierBps("BLR-KORAMANGALA"))
                .isInstanceOf(InvalidSurgeException.class);
        pricingService.verify();
    }

    @Test
    void tolerantReaderIgnoresFieldsItDoesNotUse() {
        pricingService.expect(once(), requestTo(URL)).andRespond(withSuccess("""
                {"zone": "BLR-KORAMANGALA", "multiplierBps": 11000, "validUntil": "2026-10-01T13:05:00Z",
                 "reason": "IPL final + rain", "currency": "INR", "experiment": {"id": "surge-v7", "arm": "B"}}
                """, MediaType.APPLICATION_JSON));

        assertThat(lookup.multiplierBps("BLR-KORAMANGALA")).isEqualTo(11_000);
    }

    private static String surgeJson(int bps) {
        return """
                {"zone": "BLR-KORAMANGALA", "multiplierBps": %d, "validUntil": "2026-10-01T13:05:00Z"}
                """.formatted(bps);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class TestConfig {

        @Bean
        RestClient.Builder pricingRestClientBuilder() {
            return RestClient.builder().baseUrl("http://pricing-service");
        }

        @Bean
        MockRestServiceServer pricingService(RestClient.Builder pricingRestClientBuilder) {
            return MockRestServiceServer.bindTo(pricingRestClientBuilder).build();
        }

        @Bean
        PricingClient pricingClient(RestClient.Builder pricingRestClientBuilder, MockRestServiceServer pricingService) {
            // pricingService is a parameter only to guarantee it is bound before build() is called
            return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(pricingRestClientBuilder.build()))
                    .build()
                    .createClient(PricingClient.class);
        }

        @Bean
        SurgeLookup surgeLookup(PricingClient pricingClient) {
            return new RemoteSurgeLookup(pricingClient);
        }
    }
}
