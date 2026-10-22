package dev.foodfleet.ch39.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch39.json.Json;
import dev.foodfleet.ch39.secret.Secret;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client against a stand-in that answers like the OpenBao/Vault HTTP API (verified live by verify/run.sh). */
class VaultClientTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String token = ex.getRequestHeaders().getFirst("X-Vault-Token");
            calls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " token=" + token + " " + body);
            String path = ex.getRequestURI().getPath();
            int status = 200;
            String out;
            if (path.equals("/v1/auth/kubernetes/login")) {
                Map<String, Object> req = Json.object(body);
                out = req.get("jwt").equals("sa-jwt") ? "{\"auth\":{\"client_token\":\"s.issued\",\"lease_duration\":3600}}"
                        : "{\"errors\":[\"permission denied\"]}";
                status = req.get("jwt").equals("sa-jwt") ? 200 : 403;
            } else if (token == null || !token.equals("s.issued")) {
                status = 403;
                out = "{\"errors\":[\"permission denied\"]}";
            } else if (path.equals("/v1/database/creds/order-service")) {
                out = "{\"lease_id\":\"database/creds/order-service/abc\",\"lease_duration\":3600,\"renewable\":true,"
                        + "\"data\":{\"username\":\"v-k8s-order-abc\",\"password\":\"A1a-secret\"}}";
            } else if (path.equals("/v1/sys/leases/renew")) {
                out = "{\"lease_id\":\"database/creds/order-service/abc\",\"lease_duration\":1200,\"renewable\":true}";
            } else if (path.equals("/v1/secret/data/order-service/gateway") && ex.getRequestMethod().equals("GET")) {
                out = "{\"data\":{\"data\":{\"apiKey\":\"k1\"},\"metadata\":{\"version\":3}}}";
            } else if (path.equals("/v1/secret/data/order-service/gateway")) {
                out = "{\"data\":{\"version\":4}}";
            } else {
                status = 204;
                out = "";
            }
            byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void logsInWithTheServiceAccountTokenAndUsesTheIssuedToken() throws Exception {
        Path sa = Files.createTempFile("sa", ".jwt");
        Files.writeString(sa, "sa-jwt\n");
        VaultClient client = VaultClient.loginWithKubernetes(uri(), "kubernetes", "order-service", sa);
        VaultClient.DatabaseLease lease = client.databaseCredentials("database", "order-service");
        assertThat(lease.username()).isEqualTo("v-k8s-order-abc");
        assertThat(lease.password().revealAsString()).isEqualTo("A1a-secret");
        assertThat(lease.password().toString()).isEqualTo("Secret[****]");
        assertThat(lease.ttl()).isEqualTo(Duration.ofHours(1));
        assertThat(calls.get(0)).contains("\"role\":\"order-service\"");
        assertThat(calls.get(1)).startsWith("GET /v1/database/creds/order-service token=s.issued");
    }

    @Test
    void renewalReturnsWhatWasGrantedNotWhatWasAsked() throws Exception {
        VaultClient client = new VaultClient(uri(), Secret.of("s.issued".toCharArray()));
        assertThat(client.renew("database/creds/order-service/abc", Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(20));
        assertThat(calls.get(0)).contains("\"increment\":3600");
    }

    @Test
    void kvV2ReadsDataAndVersionAndWritesWithCheckAndSet() throws Exception {
        VaultClient client = new VaultClient(uri(), Secret.of("s.issued".toCharArray()));
        VaultClient.KvSecret s = client.kvRead("secret", "order-service/gateway");
        assertThat(s.data().get("apiKey")).isEqualTo("k1");
        assertThat(s.version()).isEqualTo(3);
        assertThat(client.kvWrite("secret", "order-service/gateway", Map.of("apiKey", "k2"), 3)).isEqualTo(4);
        assertThat(calls.get(1)).contains("\"cas\":3");
    }

    @Test
    void errorsCarryTheStatusButNeverTheToken() throws Exception {
        VaultClient client = new VaultClient(uri(), Secret.of("s.wrong-token".toCharArray()));
        assertThatThrownBy(() -> client.databaseCredentials("database", "order-service"))
                .isInstanceOf(VaultClient.VaultException.class)
                .hasMessageContaining("HTTP 403");
        try {
            client.databaseCredentials("database", "order-service");
        } catch (VaultClient.VaultException e) {
            assertThat(e.status()).isEqualTo(403);
            assertThat(e.getMessage().contains("s.wrong-token")).isFalse();
        }
    }
}
