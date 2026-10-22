package dev.foodfleet.ch74.roadmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A codebase's static coupling between contexts: for every main source file, each {@code import} of a class in
 * another context is one dependency edge (files × imported classes). Imports miss references by string (Spring XML,
 * reflection) and fully qualified names in code; the counts are a floor.
 */
public final class Codebase {

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);

    public final Map<String, Integer> files = new TreeMap<>();
    /** "from>to" to the number of import edges. */
    public final Map<String, Integer> edges = new TreeMap<>();

    public static Codebase read(Path root, Contexts contexts) throws IOException {
        Codebase c = new Codebase();
        root = root.toRealPath();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/java/")).sorted().toList()) {
                String from = contexts.ofPath(root.relativize(f).toString());
                if (from == null) {
                    continue;
                }
                c.files.merge(from, 1, Integer::sum);
                Matcher m = IMPORT.matcher(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1));
                while (m.find()) {
                    String name = m.group(2);
                    if (m.group(1) != null) {
                        name = name.substring(0, name.lastIndexOf('.'));   // a static import names a member
                    }
                    String to = contexts.of(name);
                    if (to != null && !to.equals(from)) {
                        c.edges.merge(from + ">" + to, 1, Integer::sum);
                    }
                }
            }
        }
        return c;
    }

    public int edges(String from, String to) {
        return edges.getOrDefault(from + ">" + to, 0);
    }
}
