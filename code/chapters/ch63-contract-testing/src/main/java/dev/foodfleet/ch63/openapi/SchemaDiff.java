package dev.foodfleet.ch63.openapi;

import dev.foodfleet.ch63.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A breaking-change check between two OpenAPI 3.1 documents (JSON), for the parts menu-service uses: paths,
 * parameters, response codes and response schemas. Each finding carries oasdiff 1.34's rule id and level (ERR is
 * breaking, WARN may be, INFO isn't); {@code check_apis.sh} checks the levels in oasdiff's source.
 */
public final class SchemaDiff {

    public enum Level { ERR, WARN, INFO }

    public record Finding(Level level, String id, String where) {
        @Override
        public String toString() {
            return level + " " + id + " " + where;
        }
    }

    private final Map<String, Object> base;
    private final Map<String, Object> revised;
    private final List<Finding> findings = new ArrayList<>();

    private SchemaDiff(String base, String revised) {
        this.base = Json.object(Json.parse(base));
        this.revised = Json.object(Json.parse(revised));
    }

    public static List<Finding> diff(String base, String revised) {
        SchemaDiff d = new SchemaDiff(base, revised);
        d.run();
        return d.findings;
    }

    public static boolean breaking(List<Finding> findings) {
        return findings.stream().anyMatch(f -> f.level() == Level.ERR);
    }

    private void run() {
        Map<String, Object> bp = Json.object(base.get("paths"));
        Map<String, Object> rp = Json.object(revised.get("paths"));
        for (Map.Entry<String, Object> path : bp.entrySet()) {
            if (!rp.containsKey(path.getKey())) {
                add(Level.ERR, "api-path-removed-without-deprecation", path.getKey());
                continue;
            }
            Map<String, Object> bops = Json.object(path.getValue());
            Map<String, Object> rops = Json.object(rp.get(path.getKey()));
            for (Map.Entry<String, Object> op : bops.entrySet()) {
                String where = op.getKey().toUpperCase() + " " + path.getKey();
                if (!rops.containsKey(op.getKey())) {
                    add(Level.ERR, "api-removed-without-deprecation", where);
                    continue;
                }
                operation(Json.object(op.getValue()), Json.object(rops.get(op.getKey())), where);
            }
        }
    }

    private void operation(Map<String, Object> b, Map<String, Object> r, String where) {
        Set<String> before = new LinkedHashSet<>();
        for (Object p : list(b.get("parameters"))) {
            before.add(Json.object(p).get("in") + ":" + Json.object(p).get("name"));
        }
        for (Object p : list(r.get("parameters"))) {
            Map<String, Object> param = Json.object(p);
            if (!before.contains(param.get("in") + ":" + param.get("name")) && !"path".equals(param.get("in"))) {
                add(Boolean.TRUE.equals(param.get("required")) ? Level.ERR : Level.INFO,
                        Boolean.TRUE.equals(param.get("required")) ? "new-required-request-parameter" : "new-optional-request-parameter",
                        where + " " + param.get("in") + " " + param.get("name"));
            }
        }
        Map<String, Object> bres = Json.object(b.get("responses"));
        Map<String, Object> rres = Json.object(r.get("responses"));
        for (Map.Entry<String, Object> status : bres.entrySet()) {
            if (!rres.containsKey(status.getKey())) {
                boolean success = status.getKey().startsWith("2");
                add(success ? Level.ERR : Level.INFO, success ? "response-success-status-removed" : "response-non-success-status-removed",
                        where + " " + status.getKey());
                continue;
            }
            Object bs = schemaOf(status.getValue());
            Object rs = schemaOf(rres.get(status.getKey()));
            if (bs != null && rs != null) {
                schema(resolve(bs, base), resolve(rs, revised), where + " " + status.getKey() + " ", "$");
            }
        }
    }

    private void schema(Map<String, Object> b, Map<String, Object> r, String where, String prop) {
        Set<String> bt = types(b);
        Set<String> rt = types(r);
        boolean bNull = bt.remove("null");
        boolean rNull = rt.remove("null");
        if (!bt.isEmpty() && !rt.isEmpty() && !bt.equals(rt)) {
            add(Level.ERR, prop.equals("$") ? "response-body-type-changed" : "response-property-type-changed", where + prop + " " + bt + " -> " + rt);
            return;
        }
        if (!bNull && rNull) {
            add(Level.ERR, "response-property-became-nullable", where + prop);
        }
        List<Object> be = list(b.get("enum"));
        List<Object> re = list(r.get("enum"));
        for (Object v : re) {
            if (!be.isEmpty() && !be.contains(v)) {
                add(Level.ERR, "response-property-enum-value-added", where + prop + " " + v);
            }
        }
        for (Object v : be) {
            if (!re.isEmpty() && !re.contains(v)) {
                add(Level.INFO, "response-property-enum-value-removed", where + prop + " " + v);
            }
        }
        if (b.get("items") != null && r.get("items") != null) {
            schema(resolve(b.get("items"), base), resolve(r.get("items"), revised), where, prop + "[]");
        }
        Map<String, Object> bprops = b.get("properties") == null ? Map.of() : Json.object(b.get("properties"));
        Map<String, Object> rprops = r.get("properties") == null ? Map.of() : Json.object(r.get("properties"));
        List<Object> breq = list(b.get("required"));
        List<Object> rreq = list(r.get("required"));
        for (Map.Entry<String, Object> p : bprops.entrySet()) {
            String child = prop + "." + p.getKey();
            if (!rprops.containsKey(p.getKey())) {
                boolean required = breq.contains(p.getKey());
                add(required ? Level.ERR : Level.INFO, required ? "response-required-property-removed" : "response-optional-property-removed", where + child);
                continue;
            }
            if (breq.contains(p.getKey()) && !rreq.contains(p.getKey())) {
                add(Level.ERR, "response-property-became-optional", where + child);
            }
            schema(resolve(p.getValue(), base), resolve(rprops.get(p.getKey()), revised), where, child);
        }
        for (String p : rprops.keySet()) {
            if (!bprops.containsKey(p)) {
                add(Level.INFO, rreq.contains(p) ? "response-required-property-added" : "response-optional-property-added", where + prop + "." + p);
            }
        }
    }

    private void add(Level level, String id, String where) {
        findings.add(new Finding(level, id, where));
    }

    static Object schemaOf(Object response) {
        Object content = Json.object(response).get("content");
        if (content == null) {
            return null;
        }
        Object json = Json.object(content).get("application/json");
        return json == null ? null : Json.object(json).get("schema");
    }

    static Map<String, Object> resolve(Object schema, Map<String, Object> doc) {
        Map<String, Object> s = Json.object(schema);
        Object ref = s.get("$ref");
        if (ref == null) {
            return s;
        }
        Object node = doc;
        for (String part : ((String) ref).substring(2).split("/")) {
            node = Json.object(node).get(part);
        }
        return resolve(node, doc);
    }

    static Set<String> types(Map<String, Object> s) {
        Set<String> t = new LinkedHashSet<>();
        Object type = s.get("type");
        if (type instanceof String str) {
            t.add(str);
        } else if (type instanceof List<?> l) {
            l.forEach(x -> t.add(String.valueOf(x)));
        }
        return t;
    }

    static List<Object> list(Object o) {
        return o == null ? List.of() : Json.array(o);
    }
}
