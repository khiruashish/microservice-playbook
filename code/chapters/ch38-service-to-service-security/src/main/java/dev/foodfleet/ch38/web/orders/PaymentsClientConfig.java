package dev.foodfleet.ch38.web.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.web.client.RestClient;

/**
 * order-service's client for payment-service: mutual TLS with the workload's SVID (SSL bundle "svid") and a
 * client-credentials access token (registration "payments") on every request.
 * Two Spring defaults are replaced, both read from the Spring Security 7.1.1 sources:
 * the authorized-client manager that needs an HTTP request and session is replaced by the service one, and the
 * principal is no longer "whoever's request is running" (which caches one client-credentials token per user).
 */
@Configuration(proxyBeanMethods = false)
public class PaymentsClientConfig {

    @Bean
    OAuth2AuthorizedClientManager serviceTokenManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    PaymentsClient paymentsClient(RestClient.Builder builder, RestClientSsl ssl, SslBundles bundles,
            OAuth2AuthorizedClientManager serviceTokenManager, @Value("${app.payments.base-url}") String baseUrl) {
        var tokens = new OAuth2ClientHttpRequestInterceptor(serviceTokenManager);
        // no principal attribute on our requests → the interceptor uses one fixed anonymous principal for all of them
        tokens.setPrincipalResolver(new RequestAttributePrincipalResolver());
        return new PaymentsClient(() -> builder.clone()
                .baseUrl(baseUrl)
                .apply(ssl.fromBundle(bundles.getBundle("svid")))   // the bundle as it is now; rebuilt on reload
                .requestInterceptor(tokens)
                .build(), bundles, "svid");
    }
}
