package dev.foodfleet.ch20.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the architecture model format: one fact per line, {@code #} comments.
 *
 * <pre>
 * system  foodfleet-2022
 * service ordering team=orders endpoints=14 loc=42000
 * call    ordering -> menu per-request=12 sync
 * call    ordering -> notification per-request=1 async
 * table   orders owner=ordering writers=order-status readers=reporting,support
 * library foodfleet-common kind=domain ordering=3.2.0 menu=2.9.1
 * deploy  2022-11-03T10:15 ordering,menu,pricing
 * </pre>
 * Errors name the line, so the file can be maintained by hand in code review.
 */
public final class ModelParser {

    private ModelParser() {
    }

    public static SystemModel parse(String text) {
        String name = "unnamed";
        List<SystemModel.Service> services = new ArrayList<>();
        List<SystemModel.Call> calls = new ArrayList<>();
        List<SystemModel.Table> tables = new ArrayList<>();
        List<SystemModel.Library> libraries = new ArrayList<>();
        List<SystemModel.Deploy> deploys = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("#.*", "").strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            try {
                switch (words[0]) {
                    case "system" -> name = words[1];
                    case "service" -> {
                        Map<String, String> a = attributes(words, 2);
                        services.add(new SystemModel.Service(words[1], a.getOrDefault("team", "?"),
                                Integer.parseInt(a.getOrDefault("endpoints", "0")),
                                Integer.parseInt(a.getOrDefault("loc", "0"))));
                    }
                    case "call" -> {
                        if (!words[2].equals("->")) {
                            throw new IllegalArgumentException("expected '->'");
                        }
                        Map<String, String> a = attributes(words, 4);
                        boolean sync = Arrays.asList(words).contains("sync");
                        calls.add(new SystemModel.Call(words[1], words[3],
                                Double.parseDouble(a.getOrDefault("per-request", "1")), sync));
                    }
                    case "table" -> {
                        Map<String, String> a = attributes(words, 2);
                        tables.add(new SystemModel.Table(words[1], required(a, "owner"), list(a.get("writers")),
                                list(a.get("readers"))));
                    }
                    case "library" -> {
                        Map<String, String> a = attributes(words, 2);
                        String kind = a.getOrDefault("kind", "utility");
                        a.remove("kind");
                        libraries.add(new SystemModel.Library(words[1], kind, a));
                    }
                    case "deploy" -> deploys.add(new SystemModel.Deploy(LocalDateTime.parse(words[1]), list(words[2])));
                    default -> throw new IllegalArgumentException("unknown keyword '" + words[0] + "'");
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage() + " in: " + lines[i], e);
            }
        }
        return new SystemModel(name, services, calls, tables, libraries, deploys);
    }

    private static Map<String, String> attributes(String[] words, int from) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = from; i < words.length; i++) {
            int eq = words[i].indexOf('=');
            if (eq > 0) {
                map.put(words[i].substring(0, eq), words[i].substring(eq + 1));
            }
        }
        return map;
    }

    private static String required(Map<String, String> a, String key) {
        String value = a.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key + "=");
        }
        return value;
    }

    private static Set<String> list(String csv) {
        return csv == null || csv.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(csv.split(",")));
    }
}
