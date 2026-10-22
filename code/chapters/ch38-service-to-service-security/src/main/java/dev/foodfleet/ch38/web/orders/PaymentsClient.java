package dev.foodfleet.ch38.web.orders;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.web.client.RestClient;

/**
 * Calls payment-service. Spring Boot reloads the server side of an SSL bundle when SPIRE rotates the SVID files,
 * but a RestClient built with {@code RestClientSsl.fromBundle} keeps the key material it was built with (Spring Boot
 * 4.1.1 registers bundle-update handlers only for the embedded web servers). Without a rebuild, the client would
 * present an expired SVID one SVID lifetime after start-up; so the client is rebuilt whenever the bundle changes.
 */
public class PaymentsClient {

    private final AtomicReference<RestClient> client = new AtomicReference<>();

    public PaymentsClient(Supplier<RestClient> factory, SslBundles bundles, String bundleName) {
        client.set(factory.get());
        bundles.addBundleUpdateHandler(bundleName, updated -> client.set(factory.get()));
    }

    public void capture(long orderId) {
        client.get().post()
                .uri("/payments/{orderId}/capture", orderId)
                .attributes(clientRegistrationId("payments"))
                .retrieve()
                .toBodilessEntity();
    }
}
