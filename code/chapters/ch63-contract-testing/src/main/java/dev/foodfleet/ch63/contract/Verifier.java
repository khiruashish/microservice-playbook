package dev.foodfleet.ch63.contract;

import dev.foodfleet.ch63.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provider verification: replay each interaction of a consumer's contract against the running provider and check the
 * response with the contract's matching rules. What Pact's provider verifier and Spring Cloud Contract's generated
 * tests do. Provider states are assumed to be set up by the provider's test data.
 */
public final class Verifier {

    public record Result(String interaction, List<String> mismatches) {
        public boolean ok() {
            return mismatches.isEmpty();
        }
    }

    private Verifier() {
    }

    public static List<Result> verify(Contract contract, String providerBase) {
        HttpClient http = HttpClient.newHttpClient();
        List<Result> results = new ArrayList<>();
        for (Contract.Interaction i : contract.interactions()) {
            List<String> mismatches = new ArrayList<>();
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(providerBase + i.path())).method(i.method(), HttpRequest.BodyPublishers.noBody());
                i.requestHeaders().forEach(req::header);
                HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != i.status()) {
                    mismatches.add("status: expected " + i.status() + ", got " + r.statusCode());
                }
                for (Map.Entry<String, String> h : i.responseHeaders().entrySet()) {
                    String actual = r.headers().firstValue(h.getKey()).orElse(null);
                    if (actual == null || !actual.startsWith(h.getValue())) {
                        mismatches.add("header " + h.getKey() + ": expected " + h.getValue() + ", got " + actual);
                    }
                }
                if (i.body() != null) {
                    Object actual;
                    try {
                        actual = Json.parse(r.body());
                    } catch (IllegalArgumentException e) {
                        mismatches.add("body: not JSON");
                        actual = null;
                    }
                    if (actual != null) {
                        mismatches.addAll(Matching.match(i.body(), actual, i.bodyRules()));
                    }
                }
            } catch (Exception e) {
                mismatches.add("request failed: " + e);
            }
            results.add(new Result(i.description(), mismatches));
        }
        return results;
    }
}
