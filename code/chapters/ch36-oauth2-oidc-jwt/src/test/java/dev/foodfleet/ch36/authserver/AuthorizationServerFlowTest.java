package dev.foodfleet.ch36.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch36.exchange.TokenExchangePolicy;
import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.pkce.Pkce;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The flows over real HTTP against the chapter's authorization server on a loopback port. */
class AuthorizationServerFlowTest {

    MiniAuthorizationServer as;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        as = new MiniAuthorizationServer(Clock.systemUTC(), Duration.ofMinutes(5), new TokenExchangePolicy(
                List.of(new TokenExchangePolicy.ClientRules("order-service",
                        Map.of("payment-service", Set.of("payments:capture")))), Duration.ofMinutes(5)));
        as.register(new MiniAuthorizationServer.Client("customer-app", MiniAuthorizationServer.sha256("app-secret"),
                Set.of("foodfleet://callback"), Set.of("authorization_code"),
                Set.of("orders:read", "orders:write", "payments:capture"), "order-service"));
        as.register(new MiniAuthorizationServer.Client("order-service", MiniAuthorizationServer.sha256("svc-secret"),
                Set.of(), Set.of("client_credentials", MiniAuthorizationServer.TOKEN_EXCHANGE),
                Set.of("menu:read"), "menu-service"));
        as.start(0);
    }

    @AfterEach
    void stop() {
        as.close();
    }

    Map<String, Object> post(String path, String client, String secret, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(as.issuer() + path))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((client + ":" + secret).getBytes()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return r.body().isEmpty() ? Map.of("status", r.statusCode()) : Json.object(r.body());
    }

    Map<String, Object> redeem(String code, String verifier) throws Exception {
        return post("/token", "customer-app", "app-secret", Map.of("grant_type", "authorization_code", "code", code,
                "redirect_uri", "foodfleet://callback", "code_verifier", verifier));
    }

    @Test
    void authorizationCodeWithPkce() throws Exception {
        String verifier = Pkce.newVerifier();
        String code = as.authorize("customer-app", "foodfleet://callback", "c01", Set.of("orders:read"),
                Pkce.challenge(verifier), "S256");
        assertThat(redeem(code, verifier).get("access_token")).isNotNull();
    }

    @Test
    void aCodeIsUselessWithoutTheVerifierAndWorksOnlyOnce() throws Exception {
        String verifier = Pkce.newVerifier();
        String code = as.authorize("customer-app", "foodfleet://callback", "c01", Set.of("orders:read"),
                Pkce.challenge(verifier), "S256");
        assertThat(redeem(code, Pkce.newVerifier()).get("error")).isEqualTo("invalid_grant");
        String code2 = as.authorize("customer-app", "foodfleet://callback", "c01", Set.of("orders:read"),
                Pkce.challenge(verifier), "S256");
        String token = (String) redeem(code2, verifier).get("access_token");
        assertThat(redeem(code2, verifier).get("error")).isEqualTo("invalid_grant");          // replayed code
        // ... and the token the code produced is revoked, since the code was evidently stolen
        assertThat(post("/introspect", "order-service", "svc-secret", Map.of("token", token)).get("active"))
                .isEqualTo(false);
    }

    @Test
    void tokenExchangeNarrowsTheUsersToken() throws Exception {
        String verifier = Pkce.newVerifier();
        String code = as.authorize("customer-app", "foodfleet://callback", "c01",
                Set.of("orders:write", "payments:capture"), Pkce.challenge(verifier), "S256");
        String userToken = (String) redeem(code, verifier).get("access_token");
        Map<String, Object> exchanged = post("/token", "order-service", "svc-secret", Map.of(
                "grant_type", MiniAuthorizationServer.TOKEN_EXCHANGE, "subject_token", userToken,
                "subject_token_type", MiniAuthorizationServer.ACCESS_TOKEN_TYPE, "audience", "payment-service",
                "scope", "payments:capture"));
        assertThat(exchanged.get("scope")).isEqualTo("payments:capture");
        Map<String, Object> info = post("/introspect", "order-service", "svc-secret",
                Map.of("token", (String) exchanged.get("access_token")));
        assertThat(info.get("aud")).isEqualTo("payment-service");
        assertThat(info.get("sub")).isEqualTo("c01");
    }

    @Test
    void revokedTokensIntrospectAsInactiveAndBadClientsAreRefused() throws Exception {
        String token = (String) post("/token", "order-service", "svc-secret",
                Map.of("grant_type", "client_credentials", "scope", "menu:read")).get("access_token");
        assertThat(post("/introspect", "order-service", "svc-secret", Map.of("token", token)).get("active")).isEqualTo(true);
        post("/revoke", "order-service", "svc-secret", Map.of("token", token));
        assertThat(post("/introspect", "order-service", "svc-secret", Map.of("token", token)).get("active")).isEqualTo(false);
        assertThat(post("/token", "order-service", "wrong", Map.of("grant_type", "client_credentials"))
                .get("error")).isEqualTo("invalid_client");
    }
}
