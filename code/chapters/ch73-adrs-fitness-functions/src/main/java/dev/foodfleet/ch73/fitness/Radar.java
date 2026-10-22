package dev.foodfleet.ch73.fitness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FoodFleet's technology radar in Build Your Own Radar's CSV format ({@code name,ring,quadrant,isNew,description}),
 * and which classes or packages each blip covers ({@code name,prefix}), so the radar can be checked against code.
 */
public final class Radar {

    public record Blip(String name, String ring, String quadrant, boolean isNew, String description) {
        /** BYOR renamed the fourth ring from Hold to Caution; either means: don't start new work with this. */
        public boolean onHold() {
            return ring.equalsIgnoreCase("hold") || ring.equalsIgnoreCase("caution");
        }
    }

    public final Map<String, Blip> blips = new LinkedHashMap<>();
    public final Map<String, List<String>> prefixes = new LinkedHashMap<>();

    public static Radar read(Path radarCsv, Path prefixesCsv) throws IOException {
        Radar r = new Radar();
        List<String> lines = Files.readAllLines(radarCsv);
        if (!lines.getFirst().equals("name,ring,quadrant,isNew,description")) {
            throw new IllegalArgumentException("not a BYOR CSV: " + lines.getFirst());
        }
        for (String line : lines.subList(1, lines.size())) {
            List<String> f = csv(line);
            r.blips.put(f.get(0), new Blip(f.get(0), f.get(1), f.get(2), Boolean.parseBoolean(f.get(3)), f.get(4)));
        }
        for (String line : Files.readAllLines(prefixesCsv).subList(1, Files.readAllLines(prefixesCsv).size())) {
            List<String> f = csv(line);
            if (!r.blips.containsKey(f.get(0))) {
                throw new IllegalArgumentException("no such blip: " + f.get(0));
            }
            r.prefixes.computeIfAbsent(f.get(0), k -> new ArrayList<>()).add(f.get(1));
        }
        return r;
    }

    /** The blip on hold that a class name falls under, or null. */
    public Blip heldBy(String className) {
        for (var e : prefixes.entrySet()) {
            Blip b = blips.get(e.getKey());
            if (b.onHold() && e.getValue().stream().anyMatch(className::startsWith)) {
                return b;
            }
        }
        return null;
    }

    static List<String> csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
