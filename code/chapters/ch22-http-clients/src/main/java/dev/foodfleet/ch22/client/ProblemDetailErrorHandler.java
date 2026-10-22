package dev.foodfleet.ch22.client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns 4xx/5xx responses into exceptions that carry the server's explanation: an
 * {@link ApiProblemException} for {@code application/problem+json}, otherwise Spring's
 * {@link RestClientResponseException} with the (truncated) body. Registered once per client group.
 */
public final class ProblemDetailErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JsonMapper json;

    public ProblemDetailErrorHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        byte[] body = response.getBody().readAllBytes();
        MediaType type = response.getHeaders().getContentType();
        int status = response.getStatusCode().value();
        if (type != null && type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)) {
            Map<String, Object> problem = new HashMap<>(json.readValue(body, MAP));
            Object typeUri = problem.remove("type");
            Object title = problem.remove("title");
            Object detail = problem.remove("detail");
            problem.remove("status");
            problem.remove("instance");
            throw new ApiProblemException(typeUri == null ? URI.create("about:blank") : URI.create(typeUri.toString()),
                    title == null ? response.getStatusText() : title.toString(), status,
                    detail == null ? null : detail.toString(), problem);
        }
        String text = new String(body, StandardCharsets.UTF_8);
        throw new RestClientResponseException(request.getMethod() + " " + request.getURI() + " -> " + status + ": "
                + (text.length() > 300 ? text.substring(0, 300) + "..." : text),
                response.getStatusCode(), response.getStatusText(), response.getHeaders(), body, StandardCharsets.UTF_8);
    }
}
