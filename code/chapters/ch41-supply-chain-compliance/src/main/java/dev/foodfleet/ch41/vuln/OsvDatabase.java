package dev.foodfleet.ch41.vuln;

import dev.foodfleet.ch41.json.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Advisories for one ecosystem, loaded from a directory of OSV JSON files (a checkout of
 * github.com/github/advisory-database, or an OSV.dev export) and indexed by package name.
 */
public final class OsvDatabase {

    private final Map<String, List<Advisory>> byPackage = new HashMap<>();
    private int count;

    public static OsvDatabase load(Path dir, String ecosystem) throws IOException {
        OsvDatabase db = new OsvDatabase();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                String text = Files.readString(f);
                if (!text.contains("\"" + ecosystem + "\"")) {
                    continue;   // cheap pre-filter before parsing
                }
                db.add(parse(Json.object(text), ecosystem));
            }
        }
        return db;
    }

    public void add(Advisory a) {
        if (a.affected().isEmpty()) {
            return;
        }
        count++;
        a.affected().stream().map(Advisory.Affected::packageName).distinct()
                .forEach(p -> byPackage.computeIfAbsent(p, k -> new ArrayList<>()).add(a));
    }

    public List<Advisory> forPackage(String name) {
        return byPackage.getOrDefault(name, List.of());
    }

    public int size() {
        return count;
    }

    @SuppressWarnings("unchecked")
    static Advisory parse(Map<String, Object> d, String ecosystem) {
        List<Advisory.Affected> affected = new ArrayList<>();
        for (Object o : (List<Object>) d.getOrDefault("affected", List.of())) {
            Map<String, Object> a = (Map<String, Object>) o;
            Map<String, Object> pkg = (Map<String, Object>) a.get("package");
            if (pkg == null || !ecosystem.equals(pkg.get("ecosystem"))) {
                continue;
            }
            List<Advisory.Range> ranges = new ArrayList<>();
            for (Object ro : (List<Object>) a.getOrDefault("ranges", List.of())) {
                Map<String, Object> r = (Map<String, Object>) ro;
                if (!"ECOSYSTEM".equals(r.get("type"))) {
                    continue;
                }
                String introduced = null;
                for (Object eo : (List<Object>) r.get("events")) {
                    Map<String, Object> e = (Map<String, Object>) eo;
                    if (e.containsKey("introduced")) {
                        if (introduced != null) {
                            ranges.add(new Advisory.Range(introduced, null, null));   // open-ended
                        }
                        introduced = (String) e.get("introduced");
                    } else if (introduced != null && (e.containsKey("fixed") || e.containsKey("last_affected"))) {
                        ranges.add(new Advisory.Range(introduced, (String) e.get("fixed"), (String) e.get("last_affected")));
                        introduced = null;
                    }
                }
                if (introduced != null) {
                    ranges.add(new Advisory.Range(introduced, null, null));
                }
            }
            List<String> versions = new ArrayList<>();
            for (Object v : (List<Object>) a.getOrDefault("versions", List.of())) {
                versions.add((String) v);
            }
            affected.add(new Advisory.Affected((String) pkg.get("name"), ranges, versions));
        }
        Map<String, Object> db = (Map<String, Object>) d.getOrDefault("database_specific", Map.of());
        Advisory.Severity severity;
        try {
            severity = Advisory.Severity.valueOf(String.valueOf(db.get("severity")));
        } catch (IllegalArgumentException e) {
            severity = Advisory.Severity.UNKNOWN;
        }
        List<String> aliases = new ArrayList<>();
        for (Object x : (List<Object>) d.getOrDefault("aliases", List.of())) {
            aliases.add((String) x);
        }
        return new Advisory((String) d.get("id"), aliases, (String) d.getOrDefault("summary", ""), severity,
                Instant.parse((String) d.get("published")),
                d.get("withdrawn") == null ? null : Instant.parse((String) d.get("withdrawn")), affected);
    }

    static Advisory parse(String json, String ecosystem) {
        try {
            return parse(Json.object(json), ecosystem);
        } catch (RuntimeException e) {
            throw new UncheckedIOException(new IOException("not an OSV advisory", e));
        }
    }
}
