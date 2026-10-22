package dev.foodfleet.ch41.sbom;

import dev.foodfleet.ch41.json.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads the components of a CycloneDX JSON SBOM (what Syft, the CycloneDX Maven plugin and Spring Boot produce). */
public final class CycloneDx {

    private CycloneDx() {
    }

    @SuppressWarnings("unchecked")
    public static List<Component> components(String json) {
        Map<String, Object> bom = Json.object(json);
        if (!"CycloneDX".equals(bom.get("bomFormat"))) {
            throw new IllegalArgumentException("not a CycloneDX BOM");
        }
        List<Component> out = new ArrayList<>();
        collect((List<Object>) bom.getOrDefault("components", List.of()), out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(List<Object> components, List<Component> out) {
        for (Object o : components) {
            Map<String, Object> c = (Map<String, Object>) o;
            String location = null;
            for (Object p : (List<Object>) c.getOrDefault("properties", List.of())) {
                Map<String, Object> prop = (Map<String, Object>) p;
                if ("syft:location:0:path".equals(prop.get("name"))) {
                    location = (String) prop.get("value");
                }
            }
            Component.fromPurl((String) c.get("purl"), location).ifPresent(out::add);
            collect((List<Object>) c.getOrDefault("components", List.of()), out);   // nested (shaded) components
        }
    }
}
