package dev.foodfleet.ch17.ambassador;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch17.fake.FakePaymentProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Real HTTP on loopback: the app, the ambassador and a fake provider, as in a pod. */
class AmbassadorTest {

    private final HttpClient app = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private FakePaymentProvider provider;
    private Ambassador ambassador;
    private Path token;

    private void start(double unavailable, int breakerThreshold) throws IOException {
        token = Files.createTempFile("token", "");
        Files.writeString(token, "sk_live_first");
        provider = new FakePaymentProvider(unavailable, 0, 0, 1);
        ambassador = new Ambassador(new AmbassadorConfig(0, URI.create(provider.baseUrl()), Duration.ofMillis(400),
                3, Duration.ofMillis(1), token, breakerThreshold, Duration.ofSeconds(30)));
    }

    @AfterEach
    void stop() throws IOException {
        ambassador.close();
        provider.close();
        app.close();
        Files.deleteIfExists(token);
    }

    private HttpResponse<String> authorize(String idempotencyKey, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + ambassador.port() + "/v1/authorizations"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"amountPaise\":45000}"));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return app.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void addsTheProvidersTokenAndDropsWhateverTheAppSent() throws Exception {
        start(0, 50);

        HttpResponse<String> response = authorize("k-1", "Bearer something-else");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(provider.lastAuthorizationHeader()).isEqualTo("Bearer sk_live_first");
    }

    @Test
    void picksUpARotatedToken() throws Exception {
        start(0, 50);
        authorize("k-1", null);

        Files.writeString(token, "sk_live_second");
        Files.setLastModifiedTime(token, FileTime.from(Instant.now().plusSeconds(5)));
        authorize("k-2", null);

        assertThat(provider.lastAuthorizationHeader()).isEqualTo("Bearer sk_live_second");
    }

    @Test
    void retriesAPostWithAnIdempotencyKey() throws Exception {
        start(1.0, 50);                                       // every call fails

        HttpResponse<String> response = authorize("k-1", null);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(provider.calls()).isEqualTo(3);
        assertThat(response.headers().firstValue("X-Ambassador-Attempts").orElseThrow()).isEqualTo("3");
    }

    @Test
    void neverRetriesAPostWithoutOne() throws Exception {
        start(1.0, 50);

        authorize(null, null);

        assertThat(provider.calls()).isEqualTo(1);           // repeating it could charge twice
    }

    @Test
    void retriesUntilItSucceeds() throws Exception {
        start(0, 50);
        provider.behave(0.5, 0, 0);
        int ok = 0;
        for (int i = 0; i < 40; i++) {
            if (authorize("k-" + i, null).statusCode() == 201) {
                ok++;
            }
        }
        assertThat(ok).isGreaterThan(30);                   // ~87.5% with 3 attempts at 50% each
        assertThat(provider.authorizationsCreated()).isEqualTo(ok);
    }

    @Test
    void anOpenCircuitAnswersAtOnceWithoutCallingTheProvider() throws Exception {
        start(1.0, 3);
        authorize("k-1", null);                             // 3 failed attempts: the circuit opens

        HttpResponse<String> response = authorize("k-2", null);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("X-Ambassador").orElseThrow()).isEqualTo("circuit-open");
        assertThat(provider.calls()).isEqualTo(3);
        assertThat(ambassador.metrics().shortCircuited().get()).isEqualTo(1L);
    }
}
