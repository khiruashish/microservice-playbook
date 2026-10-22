package dev.foodfleet.ch39.vault;

import dev.foodfleet.ch39.json.Json;
import dev.foodfleet.ch39.secret.Secret;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of the OpenBao / HashiCorp Vault HTTP API a service needs: log in with its Kubernetes service-account
 * token, read and write KV v2 secrets, obtain dynamic database credentials, and renew or revoke their leases.
 * JDK only. The token is kept as a {@link Secret}; responses that carry secrets are never logged by this class.
 */
public final class VaultClient {

    /** Dynamic credentials and the lease that governs them. */
    public record DatabaseLease(String leaseId, String username, Secret password, Duration ttl, boolean renewable) {
    }

    /** A KV v2 secret version. */
    public record KvSecret(Map<String, Object> data, long version) {
    }

    public static final class VaultException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int status;

        VaultException(int status, String message) {
            super("HTTP " + status + ": " + message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final URI address;
    private final HttpClient http;
    private volatile Secret token;

    public VaultClient(URI address, Secret token) {
        this.address = address;
        this.token = token;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /**
     * Kubernetes auth: exchange the pod's projected service-account token for a Vault token bound to a role
     * (which limits it to the policies of that service). No secret has to be delivered to the pod for this.
     */
    public static VaultClient loginWithKubernetes(URI address, String mount, String role, Path serviceAccountToken)
            throws IOException, InterruptedException {
        VaultClient anonymous = new VaultClient(address, null);
        String jwt = Files.readString(serviceAccountToken).trim();
        Map<String, Object> body = anonymous.call("POST", "/v1/auth/" + mount + "/login",
                Map.of("role", role, "jwt", jwt));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) body.get("auth");
        return new VaultClient(address, Secret.of(((String) auth.get("client_token")).toCharArray()));
    }

    public KvSecret kvRead(String mount, String path) throws IOException, InterruptedException {
        Map<String, Object> body = call("GET", "/v1/" + mount + "/data/" + path, null);
        Map<String, Object> data = object(body, "data");
        return new KvSecret(object(data, "data"), ((Number) object(data, "metadata").get("version")).longValue());
    }

    /** Check-and-set write: fails unless the current version is {@code expectedVersion} (0 = must not exist). */
    public long kvWrite(String mount, String path, Map<String, Object> values, long expectedVersion)
            throws IOException, InterruptedException {
        Map<String, Object> body = call("POST", "/v1/" + mount + "/data/" + path,
                Map.of("data", values, "options", Map.of("cas", expectedVersion)));
        return ((Number) object(body, "data").get("version")).longValue();
    }

    public DatabaseLease databaseCredentials(String mount, String role) throws IOException, InterruptedException {
        Map<String, Object> body = call("GET", "/v1/" + mount + "/creds/" + role, null);
        Map<String, Object> data = object(body, "data");
        return new DatabaseLease((String) body.get("lease_id"), (String) data.get("username"),
                Secret.of(((String) data.get("password")).toCharArray()),
                Duration.ofSeconds(((Number) body.get("lease_duration")).longValue()),
                Boolean.TRUE.equals(body.get("renewable")));
    }

    /** Asks for {@code increment} more; the answer can be shorter (capped by max_ttl). Returns what was granted. */
    public Duration renew(String leaseId, Duration increment) throws IOException, InterruptedException {
        Map<String, Object> body = call("PUT", "/v1/sys/leases/renew",
                Map.of("lease_id", leaseId, "increment", increment.toSeconds()));
        return Duration.ofSeconds(((Number) body.get("lease_duration")).longValue());
    }

    public void revoke(String leaseId) throws IOException, InterruptedException {
        call("PUT", "/v1/sys/leases/revoke", Map.of("lease_id", leaseId));
    }

    /** Revokes every lease under a prefix, e.g. all credentials ever issued for one database role. */
    public void revokePrefix(String prefix) throws IOException, InterruptedException {
        call("PUT", "/v1/sys/leases/revoke-prefix/" + prefix, Map.of());
    }

    Map<String, Object> call(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(address.resolve(path)).timeout(Duration.ofSeconds(5));
        Secret t = token;
        if (t != null) {
            req.header("X-Vault-Token", t.revealAsString());
        }
        req.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.write(body)));
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 300) {
            String message = "request failed";
            try {
                Object errors = Json.object(res.body()).get("errors");
                if (errors instanceof List<?> l && !l.isEmpty()) {
                    message = String.valueOf(l);
                }
            } catch (IllegalArgumentException ignored) {
                // not JSON
            }
            throw new VaultException(res.statusCode(), method + " " + path + ": " + message);
        }
        return res.body().isBlank() ? new LinkedHashMap<>() : Json.object(res.body());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }
}
