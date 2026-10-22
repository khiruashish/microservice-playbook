package dev.foodfleet.ch71.lint;

import dev.foodfleet.ch71.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FoodFleet's API style guide as code: rules over an OpenAPI 3.1 document. Two sets:
 * <ul>
 *   <li>{@link Source#SPECTRAL}: rules from Spectral's recommended {@code spectral:oas} ruleset, same names and
 *       severities: is the document complete and consistent?</li>
 *   <li>{@link Source#HOUSE}: FoodFleet's design rules, most from Zalando's RESTful API guidelines (rule numbers in
 *       brackets): is the API designed the way FoodFleet designs APIs?</li>
 * </ul>
 */
public final class Linter {

    public enum Source { SPECTRAL, HOUSE }

    public enum Severity { ERROR, WARN }

    public enum Rule {
        OPERATION_OPERATION_ID("operation-operationId", Source.SPECTRAL, Severity.WARN, "Operation must have \"operationId\"."),
        OPERATION_OPERATION_ID_UNIQUE("operation-operationId-unique", Source.SPECTRAL, Severity.ERROR, "Every operation must have unique \"operationId\"."),
        OPERATION_DESCRIPTION("operation-description", Source.SPECTRAL, Severity.WARN, "Operation \"description\" must be present and non-empty string."),
        OPERATION_TAGS("operation-tags", Source.SPECTRAL, Severity.WARN, "Operation must have non-empty \"tags\" array."),
        OPERATION_TAG_DEFINED("operation-tag-defined", Source.SPECTRAL, Severity.WARN, "Operation tags must be defined in global tags."),
        INFO_CONTACT("info-contact", Source.SPECTRAL, Severity.WARN, "Info object must have \"contact\" object."),
        PATH_PARAMS("path-params", Source.SPECTRAL, Severity.ERROR, "Path parameters must be defined and valid."),
        OPERATION_SUCCESS_RESPONSE("operation-success-response", Source.SPECTRAL, Severity.WARN, "Operation must have at least one \"2xx\" or \"3xx\" response."),
        PATH_KEYS_NO_TRAILING_SLASH("path-keys-no-trailing-slash", Source.SPECTRAL, Severity.WARN, "Path must not end with slash."),
        OAS3_API_SERVERS("oas3-api-servers", Source.SPECTRAL, Severity.WARN, "OpenAPI \"servers\" must be present and non-empty array."),

        KEBAB_CASE_PATHS("kebab-case-paths", Source.HOUSE, Severity.ERROR, "[129] Path segments are kebab-case."),
        VERB_FREE_URLS("verb-free-urls", Source.HOUSE, Severity.ERROR, "[141] URLs name resources, not actions."),
        PLURAL_COLLECTIONS("plural-collections", Source.HOUSE, Severity.WARN, "[134] A segment followed by an identifier is a plural collection."),
        NO_URL_VERSIONING("no-url-versioning", Source.HOUSE, Severity.ERROR, "[115] No version in the URL: FoodFleet versions by the API-Version header."),
        PROBLEM_JSON("problem-json", Source.HOUSE, Severity.ERROR, "[176] 4xx and 5xx responses are application/problem+json (RFC 9457)."),
        CURSOR_PAGINATION("cursor-pagination", Source.HOUSE, Severity.WARN, "[160] Collections are paginated by cursor and limit."),
        CAMEL_CASE_PROPERTIES("camel-case-properties", Source.HOUSE, Severity.WARN, "Properties are camelCase (FoodFleet's choice; Zalando's [118] is snake_case)."),
        DEPRECATION_SUNSET("deprecation-sunset", Source.HOUSE, Severity.ERROR, "[187] A deprecated operation states its sunset date (x-sunset)."),
        MONEY_AS_DECIMAL_STRING("money-as-decimal-string", Source.HOUSE, Severity.ERROR, "[173] Money is a decimal string with a currency, never a float."),
        OWNER("api-owner", Source.HOUSE, Severity.ERROR, "info.x-owner names the owning team, as in the catalog.");

        public final String id;
        public final Source source;
        public final Severity severity;
        public final String description;

        Rule(String id, Source source, Severity severity, String description) {
            this.id = id;
            this.source = source;
            this.severity = severity;
            this.description = description;
        }
    }

    /** A rule broken at a JSON pointer, such as {@code /paths/~1menus/get}. */
    public record Finding(Rule rule, String pointer) {
    }

    static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
    static final Pattern PARAM = Pattern.compile("\\{([^}]+)}");
    static final Set<String> VERBS = Set.of("get", "create", "update", "delete", "cancel", "search", "find", "list", "add", "remove", "do", "process", "calculate",
            "submit", "fetch", "apply", "assign", "notify", "send", "make");
    static final Pattern MONEY = Pattern.compile("(?i).*(price|amount|total|fee|refund|tip)$");

    private Linter() {
    }

    public static List<Finding> lint(String json) {
        return lint(Json.object(Json.parse(json)));
    }

    public static List<Finding> lint(Map<String, Object> api) {
        List<Finding> out = new ArrayList<>();
        Map<String, Object> info = map(api.get("info"));
        if (info.get("contact") == null) {
            out.add(new Finding(Rule.INFO_CONTACT, "/info"));
        }
        if (info.get("x-owner") == null) {
            out.add(new Finding(Rule.OWNER, "/info"));
        }
        if (!(api.get("servers") instanceof List<?> servers) || servers.isEmpty()) {
            out.add(new Finding(Rule.OAS3_API_SERVERS, ""));
        }
        Set<String> tags = new HashSet<>();
        for (Object t : list(api.get("tags"))) {
            tags.add(String.valueOf(map(t).get("name")));
        }
        Map<String, Integer> operationIds = new HashMap<>();
        for (Map.Entry<String, Object> p : map(api.get("paths")).entrySet()) {
            String path = p.getKey();
            String pp = "/paths/" + path.replace("~", "~0").replace("/", "~1");
            pathRules(path, pp, out);
            Map<String, Object> item = map(p.getValue());
            for (Map.Entry<String, Object> m : item.entrySet()) {
                if (!METHODS.contains(m.getKey())) {
                    continue;
                }
                String op = pp + "/" + m.getKey();
                operationRules(path, m.getKey(), map(m.getValue()), item, op, tags, operationIds, out);
            }
        }
        schemaRules(map(map(api.get("components")).get("schemas")), "/components/schemas", out);
        return out;
    }

    static void pathRules(String path, String pointer, List<Finding> out) {
        if (path.length() > 1 && path.endsWith("/")) {
            out.add(new Finding(Rule.PATH_KEYS_NO_TRAILING_SLASH, pointer));
        }
        String[] segments = path.substring(1).split("/");
        for (int i = 0; i < segments.length; i++) {
            String s = segments[i];
            if (s.isEmpty() || s.startsWith("{")) {
                continue;
            }
            if (s.matches("v\\d+")) {
                out.add(new Finding(Rule.NO_URL_VERSIONING, pointer));
                continue;
            }
            if (!s.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
                out.add(new Finding(Rule.KEBAB_CASE_PATHS, pointer));
            }
            // the first word, whether the segment is kebab-case or camelCase
            String first = s.split("-")[0].split("(?=[A-Z])")[0].toLowerCase();
            if (VERBS.contains(first)) {
                out.add(new Finding(Rule.VERB_FREE_URLS, pointer));
            }
            if (i + 1 < segments.length && segments[i + 1].startsWith("{") && !s.endsWith("s")) {
                out.add(new Finding(Rule.PLURAL_COLLECTIONS, pointer));
            }
        }
    }

    static void operationRules(String path, String method, Map<String, Object> op, Map<String, Object> item, String pointer, Set<String> tags,
            Map<String, Integer> operationIds, List<Finding> out) {
        Object id = op.get("operationId");
        if (id == null || String.valueOf(id).isBlank()) {
            out.add(new Finding(Rule.OPERATION_OPERATION_ID, pointer));
        } else if (operationIds.merge(String.valueOf(id), 1, Integer::sum) > 1) {
            out.add(new Finding(Rule.OPERATION_OPERATION_ID_UNIQUE, pointer + "/operationId"));
        }
        if (op.get("description") == null || String.valueOf(op.get("description")).isBlank()) {
            out.add(new Finding(Rule.OPERATION_DESCRIPTION, pointer));
        }
        List<Object> opTags = list(op.get("tags"));
        if (opTags.isEmpty()) {
            out.add(new Finding(Rule.OPERATION_TAGS, pointer));
        }
        for (Object t : opTags) {
            if (!tags.contains(String.valueOf(t))) {
                out.add(new Finding(Rule.OPERATION_TAG_DEFINED, pointer + "/tags"));
            }
        }
        Set<String> declared = new HashSet<>();
        for (Object p : concat(list(item.get("parameters")), list(op.get("parameters")))) {
            Map<String, Object> param = map(p);
            if ("path".equals(param.get("in"))) {
                declared.add(String.valueOf(param.get("name")));
            }
        }
        Set<String> inTemplate = new HashSet<>();
        Matcher m = PARAM.matcher(path);
        while (m.find()) {
            inTemplate.add(m.group(1));
        }
        if (!declared.equals(inTemplate)) {
            out.add(new Finding(Rule.PATH_PARAMS, pointer));
        }
        Map<String, Object> responses = map(op.get("responses"));
        if (responses.keySet().stream().noneMatch(k -> k.startsWith("2") || k.startsWith("3"))) {
            out.add(new Finding(Rule.OPERATION_SUCCESS_RESPONSE, pointer + "/responses"));
        }
        for (Map.Entry<String, Object> r : responses.entrySet()) {
            if ((r.getKey().startsWith("4") || r.getKey().startsWith("5")) && !map(map(r.getValue()).get("content")).containsKey("application/problem+json")) {
                out.add(new Finding(Rule.PROBLEM_JSON, pointer + "/responses/" + r.getKey()));
            }
        }
        if ("get".equals(method) && !path.endsWith("}") && returnsArray(responses)) {
            Set<String> query = new HashSet<>();
            for (Object p : list(op.get("parameters"))) {
                if ("query".equals(map(p).get("in"))) {
                    query.add(String.valueOf(map(p).get("name")));
                }
            }
            if (!query.contains("cursor") || !query.contains("limit")) {
                out.add(new Finding(Rule.CURSOR_PAGINATION, pointer));
            }
        }
        if (Boolean.TRUE.equals(op.get("deprecated")) && op.get("x-sunset") == null) {
            out.add(new Finding(Rule.DEPRECATION_SUNSET, pointer));
        }
    }

    static boolean returnsArray(Map<String, Object> responses) {
        Map<String, Object> ok = map(responses.get("200"));
        Map<String, Object> schema = map(map(map(ok.get("content")).get("application/json")).get("schema"));
        Map<String, Object> props = map(schema.get("properties"));
        return "array".equals(schema.get("type")) || "array".equals(map(props.get("items")).get("type"));
    }

    static void schemaRules(Map<String, Object> schemas, String pointer, List<Finding> out) {
        for (Map.Entry<String, Object> s : schemas.entrySet()) {
            properties(map(s.getValue()), pointer + "/" + s.getKey(), out);
        }
    }

    static void properties(Map<String, Object> schema, String pointer, List<Finding> out) {
        for (Map.Entry<String, Object> p : map(schema.get("properties")).entrySet()) {
            String at = pointer + "/properties/" + p.getKey();
            Map<String, Object> prop = map(p.getValue());
            if (!p.getKey().matches("[a-z][a-zA-Z0-9]*")) {
                out.add(new Finding(Rule.CAMEL_CASE_PROPERTIES, at));
            }
            if (MONEY.matcher(p.getKey()).matches() && !("string".equals(prop.get("type")) && "decimal".equals(prop.get("format")))
                    && prop.get("$ref") == null) {
                out.add(new Finding(Rule.MONEY_AS_DECIMAL_STRING, at));
            }
            properties(prop, at, out);
            properties(map(prop.get("items")), at + "/items", out);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static List<Object> concat(List<Object> a, List<Object> b) {
        List<Object> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
