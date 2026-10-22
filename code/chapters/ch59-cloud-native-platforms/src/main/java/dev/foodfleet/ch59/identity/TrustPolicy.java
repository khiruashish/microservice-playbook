package dev.foodfleet.ch59.identity;

import java.time.Duration;
import java.util.Set;

/**
 * The cloud side of workload identity federation, as an IAM role's trust policy (AWS), a workload identity pool's
 * attribute condition (Google Cloud) or a federated identity credential (Azure) expresses it: which issuer, which
 * audience, and which service accounts may assume the role. A verified token for any other subject is refused.
 */
public record TrustPolicy(String role, String audience, Set<String> subjects, Duration sessionDuration) {

    public record Credentials(String role, String subject, long expiresAt) {
    }

    /** Exchange a verified token for short-lived role credentials, if this policy admits its subject. */
    public Credentials assume(TokenVerifier verifier, String jwt, long nowEpochSeconds) {
        TokenVerifier.Claims c = verifier.verify(jwt, audience, nowEpochSeconds);
        if (!subjects.contains(c.subject())) {
            throw new SecurityException("not authorized to assume " + role + ": " + c.subject());
        }
        return new Credentials(role, c.subject(), nowEpochSeconds + sessionDuration.toSeconds());
    }

    public static String serviceAccount(String namespace, String name) {
        return "system:serviceaccount:" + namespace + ":" + name;
    }
}
