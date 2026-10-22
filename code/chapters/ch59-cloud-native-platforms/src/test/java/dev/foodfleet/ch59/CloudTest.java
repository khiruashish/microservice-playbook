package dev.foodfleet.ch59;

import dev.foodfleet.ch59.identity.TokenVerifier;
import dev.foodfleet.ch59.identity.TrustPolicy;
import dev.foodfleet.ch59.retry.ClientRateLimiter;
import dev.foodfleet.ch59.retry.RetryQuota;
import dev.foodfleet.ch59.retry.RetryStrategy;
import dev.foodfleet.ch59.retry.Strategies;
import dev.foodfleet.ch59.serverless.FunctionPool;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTest {

    static final String ISS = "https://kubernetes.default.svc.cluster.local";
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    static String b64(BigInteger i) {
        byte[] b = i.toByteArray();
        if (b[0] == 0) {
            b = java.util.Arrays.copyOfRange(b, 1, b.length);
        }
        return B64.encodeToString(b);
    }

    static String jwt(KeyPair kp, String kid, String claims) throws Exception {
        String h = B64.encodeToString(("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
        String c = B64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update((h + "." + c).getBytes(StandardCharsets.US_ASCII));
        return h + "." + c + "." + B64.encodeToString(s.sign());
    }

    static String claims(String aud, String sub, long exp) {
        return "{\"aud\":[\"" + aud + "\"],\"exp\":" + exp + ",\"iat\":1000,\"iss\":\"" + ISS + "\",\"kubernetes.io\":{\"namespace\":\"menu\","
                + "\"pod\":{\"name\":\"menu-1\",\"uid\":\"u\"},\"serviceaccount\":{\"name\":\"menu-service\",\"uid\":\"s\"}},\"nbf\":1000,\"sub\":\"" + sub + "\"}";
    }

    @Test
    void federatedIdentityChecksSignatureIssuerAudienceExpiryAndSubject() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        String jwks = "{\"keys\":[{\"use\":\"sig\",\"kty\":\"RSA\",\"kid\":\"k1\",\"alg\":\"RS256\",\"n\":\"" + b64(pub.getModulus()) + "\",\"e\":\""
                + b64(pub.getPublicExponent()) + "\"}]}";
        TokenVerifier v = new TokenVerifier(ISS, jwks);
        String menu = TrustPolicy.serviceAccount("menu", "menu-service");
        TrustPolicy policy = new TrustPolicy("menu-reader", "sts.foodfleet.local", Set.of(menu), Duration.ofHours(1));

        String ok = jwt(kp, "k1", claims("sts.foodfleet.local", menu, 5000));
        assertThat(policy.assume(v, ok, 2000).expiresAt()).isEqualTo(2000 + 3600);
        assertThat(v.verify(ok, "sts.foodfleet.local", 2000).pod()).isEqualTo("menu-1");

        String apiServerAudience = jwt(kp, "k1", claims("https://kubernetes.default.svc", menu, 5000));
        String reporting = jwt(kp, "k1", claims("sts.foodfleet.local", TrustPolicy.serviceAccount("menu", "reporting"), 5000));
        assertThat(rejected(() -> policy.assume(v, apiServerAudience, 2000))).contains("audience");
        assertThat(rejected(() -> policy.assume(v, reporting, 2000))).contains("not authorized");
        assertThat(rejected(() -> policy.assume(v, ok, 5000))).contains("expired");
        String tampered = ok.substring(0, ok.indexOf('.') + 1) + B64.encodeToString(claims("sts.foodfleet.local", menu, 9_999_999).getBytes(StandardCharsets.UTF_8))
                + ok.substring(ok.lastIndexOf('.'));
        assertThat(rejected(() -> policy.assume(v, tampered, 2000))).contains("signature");
    }

    static String rejected(Runnable r) {
        try {
            r.run();
            return "accepted";
        } catch (SecurityException e) {
            return e.getMessage();
        }
    }

    @Test
    void retryQuotaStopsRetriesWhenEmptyAndSuccessesRefillIt() {
        RetryQuota q = new RetryQuota(10);
        assertThat(q.tryAcquire(5)).isTrue();
        assertThat(q.tryAcquire(5)).isTrue();
        assertThat(q.tryAcquire(5)).isFalse();
        q.release(1);
        q.release(100);
        assertThat(q.available()).isEqualTo(10);
    }

    @Test
    void standardModeGivesUpAfterThreeAttemptsAndBacksOffLongerWhenThrottled() {
        RetryStrategy s = Strategies.standard();
        assertThat(s.onFailure(1, false, 0).get().toMillis() < 100).isTrue();      // full jitter: [0, 100 ms)
        assertThat(s.onFailure(2, true, 0).get().toMillis() < 2000).isTrue();      // throttled: [0, 2 s)
        assertThat(s.onFailure(3, false, 0).isPresent()).isFalse();
        RetryStrategy legacy = Strategies.legacy();
        assertThat(legacy.onFailure(3, true, 0).isPresent()).isTrue();             // 4 attempts
    }

    @Test
    void adaptiveRateLimiterCutsToSeventyPercentOnThrottlingThenRecovers() {
        ClientRateLimiter l = new ClientRateLimiter();
        assertThat(Double.isInfinite(l.fillRate())).isTrue();                     // off until throttled
        for (int i = 0; i < 200; i++) {
            l.update(i * 0.01, false);                                            // ~100 calls/s measured
        }
        l.update(2.0, true);
        double cut = l.fillRate();
        assertThat(cut > 30 && cut < 100).isTrue();
        for (int i = 1; i <= 600; i++) {
            l.update(2.0 + i * 0.01, false);                                      // successes at the same pace: the rate climbs back
        }
        assertThat(l.fillRate() > cut).isTrue();
    }

    @Test
    void functionPoolPaysColdStartsAfterIdleGaps() {
        double[] arrivals = {0, 5, 6, 100, 105, 1000};
        FunctionPool.Result r = FunctionPool.replay(arrivals, 0.05, 2.0, 60, 0);
        assertThat(r.coldStarts()).isEqualTo(3L);                                 // at 0, 100 (idle 94 s > 60), 1000
        assertThat(FunctionPool.replay(arrivals, 0.05, 2.0, 600, 0).coldStarts()).isEqualTo(2L);
        assertThat(FunctionPool.replay(arrivals, 0.05, 2.0, 60, 1).coldStarts()).isEqualTo(0L);
        // a request arriving while the only instance is still starting needs a second instance, and a second cold start
        assertThat(FunctionPool.replay(new double[] {5, 6}, 0.05, 2.0, 60, 0).coldStarts()).isEqualTo(2L);
    }
}
