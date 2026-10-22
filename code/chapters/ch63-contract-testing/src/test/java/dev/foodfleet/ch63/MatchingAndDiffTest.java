package dev.foodfleet.ch63;

import dev.foodfleet.ch63.contract.Matching;
import dev.foodfleet.ch63.json.Json;
import dev.foodfleet.ch63.openapi.SchemaDiff;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingAndDiffTest {

    static final Map<String, Map<String, Object>> RULES = Map.of(
            "$.items", Map.of("match", "type", "min", 1),
            "$.items[*].price", Map.of("match", "regex", "regex", "^\\d+\\.\\d{2}$"));

    static List<String> match(String expected, String actual) {
        return Matching.match(Json.parse(expected), Json.parse(actual), RULES);
    }

    @Test
    void extraFieldsAndMoreElementsAreAllowed() {
        assertTrue(match("{\"items\":[{\"name\":\"A\",\"price\":\"1.00\"}]}",
                "{\"items\":[{\"name\":\"X\",\"price\":\"9.50\",\"new\":1},{\"name\":\"Y\",\"price\":\"2.00\"}],\"next\":\"/p2\"}").isEmpty());
    }

    @Test
    void typesCascadeAndRegexesApplyToEveryElement() {
        List<String> m = match("{\"items\":[{\"name\":\"A\",\"price\":\"1.00\"}]}", "{\"items\":[{\"name\":7,\"price\":\"100\"}]}");
        assertEquals(List.of("$.items[0].name: expected a string, got number 7", "$.items[0].price: \"100\" doesn't match /^\\d+\\.\\d{2}$/"), m);
    }

    @Test
    void withoutARuleValuesMustBeEqual() {
        assertFalse(Matching.match(Json.parse("{\"available\":false}"), Json.parse("{\"available\":true}"), Map.of()).isEmpty());
    }

    @Test
    void schemaDiffRanksChangesLikeOasdiff() throws Exception {
        String spec = Files.readString(Path.of("src/main/resources/openapi/menu-service.json"));
        List<SchemaDiff.Finding> removed = SchemaDiff.diff(spec, spec.replace("\"spice\", \"calories\"]", "\"spice\"]")
                .replace("\"description\": {\"type\": \"string\"},\n          \"calories\": {\"type\": \"integer\"}", "\"description\": {\"type\": \"string\"}"));
        assertTrue(SchemaDiff.breaking(removed), removed.toString());
        assertEquals("response-required-property-removed", removed.getFirst().id());
        List<SchemaDiff.Finding> optional = SchemaDiff.diff(spec, spec.replace("          \"description\": {\"type\": \"string\"},\n", ""));
        assertFalse(SchemaDiff.breaking(optional), optional.toString());
        assertTrue(SchemaDiff.diff(spec, spec).isEmpty());
    }
}
