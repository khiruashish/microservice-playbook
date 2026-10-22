package dev.foodfleet.ch74.roadmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which bounded context a class belongs to, by the longest matching package prefix ({@code prefix,context,candidate}).
 * Candidates are contexts that could become services; the rest stay: the web layer as the strangler's facade, and
 * {@link #PLATFORM} as a shared library linked into every deployable.
 */
public final class Contexts {

    public static final String PLATFORM = "platform";

    record Rule(String prefix, String context, boolean candidate) {
    }

    private final List<Rule> rules = new ArrayList<>();
    public final Set<String> candidates = new LinkedHashSet<>();
    public final Set<String> all = new LinkedHashSet<>();

    public static Contexts read(Path csv) throws IOException {
        Contexts c = new Contexts();
        for (String line : Files.readAllLines(csv).subList(1, Files.readAllLines(csv).size())) {
            String[] f = line.split(",");
            Rule r = new Rule(f[0], f[1], Boolean.parseBoolean(f[2]));
            c.rules.add(r);
            if (r.candidate()) {
                c.candidates.add(r.context());
            }
        }
        c.rules.sort(Comparator.comparingInt((Rule r) -> r.prefix().length()).reversed());
        c.all.addAll(c.candidates);
        c.rules.stream().map(Rule::context).forEach(c.all::add);
        return c;
    }

    /** The context of a fully qualified class name, or null when no prefix matches. */
    public String of(String className) {
        for (Rule r : rules) {
            if (className.equals(r.prefix()) || className.startsWith(r.prefix() + ".")) {
                return r.context();
            }
        }
        return null;
    }

    /** The context of a source file path such as {@code core/x/src/main/java/org/broadleafcommerce/core/order/A.java}. */
    public String ofPath(String path) {
        int i = path.indexOf("/src/main/java/");
        if (i < 0 || !path.endsWith(".java")) {
            return null;
        }
        return of(path.substring(i + "/src/main/java/".length(), path.length() - ".java".length()).replace('/', '.'));
    }
}
