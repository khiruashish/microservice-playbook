package dev.foodfleet.ch65.arch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Architecture fitness functions over a {@link ClassGraph}, the kinds ArchUnit's library offers: layer access rules,
 * cycles between slices, forbidden dependencies, naming, and general coding rules. Each returns its violations as
 * lines of text, which is also what a freeze store keeps.
 */
public final class Rules {

    public interface Rule {
        String name();

        List<String> check(ClassGraph g);
    }

    private Rules() {
    }

    /** {@code layers}: layer name to package prefix. {@code mayAccess}: layer to the layers it may use. */
    public static Rule layers(Map<String, String> layers, Map<String, Set<String>> mayAccess) {
        return rule("layered architecture", g -> {
            List<String> v = new ArrayList<>();
            for (ClassGraph.JavaClass c : g.classes().values()) {
                String from = layerOf(c.name(), layers);
                if (from == null) {
                    continue;
                }
                for (String d : c.dependencies()) {
                    String to = layerOf(d, layers);
                    if (to != null && !to.equals(from) && !mayAccess.getOrDefault(from, Set.of()).contains(to)) {
                        v.add(c.name() + " (" + from + ") depends on " + d + " (" + to + ")");
                    }
                }
            }
            return v;
        });
    }

    static String layerOf(String cls, Map<String, String> layers) {
        String best = null;
        int len = -1;
        for (Map.Entry<String, String> l : layers.entrySet()) {
            if (cls.startsWith(l.getValue() + ".") && l.getValue().length() > len) {
                best = l.getKey();
                len = l.getValue().length();
            }
        }
        return best;
    }

    /** Slices are the packages directly below {@code root} (and one level deeper for {@code deeper} ones); no cycles between them. */
    public static Rule noCycles(String root, Set<String> deeper) {
        return rule("slices free of cycles", g -> {
            Function<String, String> slice = cls -> {
                if (!cls.startsWith(root + ".")) {
                    return null;
                }
                String[] parts = cls.substring(root.length() + 1).split("\\.");
                if (parts.length < 2) {
                    return null;
                }
                return deeper.contains(parts[0]) && parts.length > 2 ? parts[0] + "." + parts[1] : parts[0];
            };
            Map<String, Set<String>> edges = new TreeMap<>();
            for (ClassGraph.JavaClass c : g.classes().values()) {
                String from = slice.apply(c.name());
                if (from == null) {
                    continue;
                }
                for (String d : c.dependencies()) {
                    String to = slice.apply(d);
                    if (to != null && !to.equals(from)) {
                        edges.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
                    }
                }
            }
            return cycles(edges);
        });
    }

    /** Stop after this many cycles, as ArchUnit does by default ({@code cycles.maxNumberToDetect=100}). */
    public static final int MAX_CYCLES = 100;

    /** Elementary cycles (up to 8 slices long), each reported once as "a -> b -> a", at most {@link #MAX_CYCLES}. */
    public static List<String> cycles(Map<String, Set<String>> edges) {
        Set<String> found = new TreeSet<>();
        for (String start : edges.keySet()) {
            walk(start, start, new ArrayList<>(List.of(start)), edges, found);
        }
        return new ArrayList<>(found);
    }

    static void walk(String start, String at, List<String> path, Map<String, Set<String>> edges, Set<String> found) {
        if (found.size() >= MAX_CYCLES) {
            return;
        }
        for (String next : edges.getOrDefault(at, Set.of())) {
            if (next.equals(start)) {
                // report each cycle once, starting from its smallest slice
                int min = 0;
                for (int i = 1; i < path.size(); i++) {
                    if (path.get(i).compareTo(path.get(min)) < 0) {
                        min = i;
                    }
                }
                List<String> rotated = new ArrayList<>(path.subList(min, path.size()));
                rotated.addAll(path.subList(0, min));
                rotated.add(rotated.getFirst());
                found.add("cycle: " + String.join(" -> ", rotated));
            } else if (!path.contains(next) && path.size() < 8) {
                path.add(next);
                walk(start, next, path, edges, found);
                path.removeLast();
            }
        }
    }

    /** Classes in {@code pkg} may depend only on these package prefixes (the JDK's own packages included). */
    public static Rule onlyDependOn(String name, String pkg, Set<String> allowed) {
        return rule(name, g -> {
            List<String> v = new ArrayList<>();
            for (ClassGraph.JavaClass c : g.classes().values()) {
                if (!c.name().startsWith(pkg + ".")) {
                    continue;
                }
                for (String d : c.dependencies()) {
                    if (allowed.stream().noneMatch(d::startsWith)) {
                        v.add(c.name() + " depends on " + d);
                    }
                }
            }
            return v;
        });
    }

    /** Class names in {@code pkg} must end with one of the suffixes. */
    public static Rule naming(String pkg, Set<String> suffixes) {
        return rule("naming in " + pkg.substring(pkg.lastIndexOf('.') + 1), g -> {
            List<String> v = new ArrayList<>();
            for (ClassGraph.JavaClass c : g.classes().values()) {
                if (c.packageName().equals(pkg) && !c.simpleName().contains("$") && suffixes.stream().noneMatch(c.simpleName()::endsWith)) {
                    v.add(c.name() + " doesn't end with " + suffixes);
                }
            }
            return v;
        });
    }

    /** ArchUnit's GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS. */
    public static Rule noStandardStreams() {
        return rule("no standard streams", g -> {
            List<String> v = new ArrayList<>();
            for (ClassGraph.JavaClass c : g.classes().values()) {
                for (String a : c.accesses()) {
                    if (a.equals("java.lang.System.out") || a.equals("java.lang.System.err") || a.equals("java.lang.Throwable.printStackTrace()")) {
                        v.add(c.name() + " accesses " + a);
                    }
                }
            }
            return v;
        });
    }

    public static Rule rule(String name, Function<ClassGraph, List<String>> check) {
        return new Rule() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<String> check(ClassGraph g) {
                return check.apply(g);
            }
        };
    }

    /** All rules' violations, by rule. */
    public static Map<String, List<String>> evaluate(ClassGraph g, List<Rule> rules) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Rule r : rules) {
            out.put(r.name(), r.check(g));
        }
        return out;
    }
}
