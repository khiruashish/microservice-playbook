package dev.foodfleet.ch63.contract;

import dev.foodfleet.ch63.json.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A consumer contract in the Pact v3 file format: who the consumer and provider are, and the interactions the
 * consumer relies on, each a request and the response it expects, with matching rules saying which parts must be
 * exactly equal and which only of the right type or shape.
 */
public record Contract(String consumer, String provider, List<Interaction> interactions) {

    public record Interaction(String description, String providerState, String method, String path, Map<String, String> requestHeaders,
                              int status, Map<String, String> responseHeaders, Object body, Map<String, Map<String, Object>> bodyRules) {
    }

    public static Contract load(Path file) {
        try {
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Contract parse(String json) {
        Map<String, Object> root = Json.object(Json.parse(json));
        List<Interaction> list = new ArrayList<>();
        for (Object o : Json.array(root.get("interactions"))) {
            Map<String, Object> i = Json.object(o);
            Map<String, Object> req = Json.object(i.get("request"));
            Map<String, Object> res = Json.object(i.get("response"));
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Object mr = res.get("matchingRules");
            if (mr != null && Json.object(mr).get("body") != null) {
                for (Map.Entry<String, Object> e : Json.object(Json.object(mr).get("body")).entrySet()) {
                    // {"matchers": [{"match": "type", "min": 1}]}: one matcher per path is all these contracts use
                    rules.put(e.getKey(), Json.object(Json.array(Json.object(e.getValue()).get("matchers")).getFirst()));
                }
            }
            String state = null;
            Object states = i.get("providerStates");
            if (states != null && !Json.array(states).isEmpty()) {
                state = (String) Json.object(Json.array(states).getFirst()).get("name");
            }
            list.add(new Interaction((String) i.get("description"), state, (String) req.get("method"), (String) req.get("path"),
                    strings(req.get("headers")), ((Number) res.get("status")).intValue(), strings(res.get("headers")), res.get("body"), rules));
        }
        return new Contract((String) Json.object(root.get("consumer")).get("name"), (String) Json.object(root.get("provider")).get("name"), list);
    }

    static Map<String, String> strings(Object o) {
        Map<String, String> m = new LinkedHashMap<>();
        if (o != null) {
            Json.object(o).forEach((k, v) -> m.put(k, String.valueOf(v)));
        }
        return m;
    }
}
