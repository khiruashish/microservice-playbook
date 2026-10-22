package dev.foodfleet.ch71.catalog;

import dev.foodfleet.ch71.json.Json;
import dev.foodfleet.ch71.lint.Linter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Governance checks over FoodFleet's Backstage catalog: Backstage's own required fields (from its entity schemas), and
 * what the platform needs on top: owners that exist, APIs that resolve, API documents that lint clean, runbooks.
 * Entity files are JSON, which YAML parsers (Backstage's included) accept as YAML.
 */
public final class Catalog {

    public record Report(int entities, int components, int apis, int groups, Map<String, List<String>> failures, int passing) {
    }

    static final Set<String> LIFECYCLES = Set.of("experimental", "production", "deprecated");

    private Catalog() {
    }

    public static Report check(Path catalogDir, Path openapiDir) throws IOException {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Path f : Files.list(catalogDir).sorted().toList()) {
            entities.add(Json.object(Json.parse(Files.readString(f))));
        }
        Set<String> groups = new TreeSet<>();
        Map<String, Map<String, Object>> apis = new HashMap<>();
        for (Map<String, Object> e : entities) {
            if ("Group".equals(e.get("kind"))) {
                groups.add(name(e));
            } else if ("API".equals(e.get("kind"))) {
                apis.put(name(e), spec(e));
            }
        }
        Map<String, List<String>> failures = new LinkedHashMap<>();
        for (String c : List.of("required: type, lifecycle, owner (Backstage)", "required: definition (Backstage, APIs)", "owner is a group in the catalog",
                "lifecycle is experimental, production or deprecated", "providesApis names an API in the catalog", "API's owner owns the component that provides it",
                "API's OpenAPI document has no lint errors", "production component has a runbook annotation")) {
            failures.put(c, new ArrayList<>());
        }
        Set<String> failing = new TreeSet<>();
        int components = 0;
        for (Map<String, Object> e : entities) {
            String kind = String.valueOf(e.get("kind"));
            if (kind.equals("Group")) {
                continue;
            }
            String name = kind.toLowerCase() + ":" + name(e);
            Map<String, Object> spec = spec(e);
            List<String> broken = new ArrayList<>();
            if (spec.get("type") == null || spec.get("lifecycle") == null || spec.get("owner") == null) {
                broken.add("required: type, lifecycle, owner (Backstage)");
            }
            if (spec.get("owner") != null && !groups.contains(String.valueOf(spec.get("owner")))) {
                broken.add("owner is a group in the catalog");
            }
            if (spec.get("lifecycle") != null && !LIFECYCLES.contains(String.valueOf(spec.get("lifecycle")))) {
                broken.add("lifecycle is experimental, production or deprecated");
            }
            if (kind.equals("Component")) {
                components++;
                for (Object api : list(spec.get("providesApis"))) {
                    Map<String, Object> a = apis.get(String.valueOf(api));
                    if (a == null) {
                        broken.add("providesApis names an API in the catalog");
                    } else if (!String.valueOf(a.get("owner")).equals(String.valueOf(spec.get("owner")))) {
                        broken.add("API's owner owns the component that provides it");
                    }
                }
                Map<String, Object> annotations = Json.object(Json.object(e.get("metadata")).getOrDefault("annotations", Map.of()));
                if ("production".equals(spec.get("lifecycle")) && annotations.get("foodfleet.dev/runbook") == null) {
                    broken.add("production component has a runbook annotation");
                }
            }
            if (kind.equals("API")) {
                Object def = spec.get("definition");
                if (def == null) {
                    broken.add("required: definition (Backstage, APIs)");
                } else {
                    String ref = String.valueOf(Json.object(def).get("$text"));
                    Path doc = openapiDir.resolve(Path.of(ref).getFileName());
                    boolean errors = Files.exists(doc) && Linter.lint(Files.readString(doc)).stream().anyMatch(f -> f.rule().severity == Linter.Severity.ERROR);
                    if (!Files.exists(doc) || errors) {
                        broken.add("API's OpenAPI document has no lint errors");
                    }
                }
            }
            for (String b : broken) {
                failures.get(b).add(name);
            }
            if (!broken.isEmpty()) {
                failing.add(name);
            }
        }
        int checked = entities.size() - groups.size();
        return new Report(entities.size(), components, apis.size(), groups.size(), failures, checked - failing.size());
    }

    static String name(Map<String, Object> e) {
        return String.valueOf(Json.object(e.get("metadata")).get("name"));
    }

    static Map<String, Object> spec(Map<String, Object> e) {
        return Json.object(e.getOrDefault("spec", Map.of()));
    }

    static List<Object> list(Object o) {
        return o instanceof List<?> l ? Json.array(l) : List.of();
    }
}
