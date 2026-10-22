package dev.foodfleet.ch71;

import dev.foodfleet.ch71.deprecation.Deprecations;
import dev.foodfleet.ch71.lint.Baseline;
import dev.foodfleet.ch71.lint.Linter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static final String API = """
            {"openapi": "3.1.0", "info": {"title": "t", "version": "1", "contact": {"name": "menu"}, "x-owner": "menu"},
             "servers": [{"url": "https://api.foodfleet.dev"}], "tags": [{"name": "menu"}],
             "paths": {"/restaurants/{restaurantId}/getMenu": {"get": {"operationId": "m", "description": "d", "tags": ["menu"],
               "parameters": [{"name": "restaurantId", "in": "path", "required": true}],
               "responses": {"200": {"description": "ok"}, "404": {"description": "nf", "content": {"application/json": {}}}}}}},
             "components": {"schemas": {"Item": {"type": "object", "properties": {"unit_price": {"type": "number"}}}}}}
            """;

    @Test
    void findsDesignProblemsAndNamesTheRules() {
        List<String> rules = Linter.lint(API).stream().map(f -> f.rule().id).sorted().toList();
        assertEquals(List.of("camel-case-properties", "kebab-case-paths", "money-as-decimal-string", "problem-json", "verb-free-urls"), rules);
    }

    @Test
    void aRatchetReportsOnlyNewFindings() {
        List<Linter.Finding> before = Linter.lint(API);
        List<Linter.Finding> after = Linter.lint(API.replace("\"tags\": [\"menu\"]", "\"tags\": [\"undeclared\"]"));
        Baseline.Result r = Baseline.of(before).compare(after);
        assertEquals(1, r.added().size());
        assertEquals("operation-tag-defined", r.added().get(0).rule().id);
        assertEquals(before.size(), r.kept().size());
    }

    @Test
    void deprecationHeadersFollowTheRfcs() {
        Map<String, String> h = Deprecations.headers(new Deprecations.Deprecated("GET", "/x", Instant.parse("2026-01-05T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"), "https://developer.foodfleet.dev/x"));
        assertEquals("@1767571200", h.get("Deprecation"));
        assertEquals("Mon, 06 Jul 2026 00:00:00 GMT", h.get("Sunset"), "IMF-fixdate: two-digit day");
        assertTrue(h.get("Link").endsWith("rel=\"deprecation\"; type=\"text/html\""));
    }
}
