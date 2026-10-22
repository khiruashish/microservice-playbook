package dev.foodfleet.ch35.outbound;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls restaurants' webhook URLs: checked by {@link OutboundUrlPolicy}, never following redirects, with short
 * timeouts and a cap on how much of the response is read (API10: a partner's API is input too).
 *
 * <p>Residual risk: the HTTP client resolves the host again when it connects, so a DNS name that changes its
 * answer between the check and the call (DNS rebinding) could still reach an internal address. Production
 * deployments also send outbound calls through an egress proxy that applies the same rules (chapter 38).
 */
public final class WebhookClient {

    public record Result(int status, String body) {
    }

    public static final class Refused extends RuntimeException {
        public Refused(String reason) {
            super(reason);
        }
    }

    private static final int MAX_BODY = 64 * 1024;
    private final OutboundUrlPolicy policy;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public WebhookClient(OutboundUrlPolicy policy) {
        this.policy = policy;
    }

    public Result post(String url, String json) throws IOException, InterruptedException {
        OutboundUrlPolicy.Decision decision = policy.check(url);
        if (!decision.allowed()) {
            throw new Refused(decision.reason());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw new Refused("redirects are not followed");
            }
            byte[] body = in.readNBytes(MAX_BODY);
            return new Result(response.statusCode(), new String(body, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
