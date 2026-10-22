import dev.foodfleet.ch74.roadmap.Codebase;
import dev.foodfleet.ch74.roadmap.Contexts;
import dev.foodfleet.ch74.roadmap.History;
import dev.foodfleet.ch74.roadmap.Roadmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chapter 74's experiments on a real monolith: Broadleaf Commerce, an open-source Java e-commerce platform, at the tag
 * broadleaf-7.1.0-GA, with ten years of its history.
 * <ul>
 *   <li>{@code coupling}: import edges between its bounded contexts, and the cycles they form</li>
 *   <li>{@code history}: how many commits changed one context and how many several; change coupling as Code Maat
 *       computes it, against the imports</li>
 *   <li>{@code roadmap}: four extraction orders, step by step: what each split costs (remote dependencies, cycles,
 *       coordinated changes) and what it buys (changes one service can ship alone)</li>
 * </ul>
 * System properties: c74.repo (the clone), c74.tag, c74.since, c74.until, c74.contexts.
 */
public final class Lab {

    static final Path REPO = Path.of(System.getProperty("c74.repo", "build/src/broadleaf"));
    static final String TAG = System.getProperty("c74.tag", "broadleaf-7.1.0-GA");
    static final String SINCE = System.getProperty("c74.since", "2016-10-14");
    static final String UNTIL = System.getProperty("c74.until", "2026-10-15");

    static Contexts contexts;
    static Codebase code;
    static History history;

    public static void main(String[] args) throws Exception {
        contexts = Contexts.read(Path.of(System.getProperty("c74.contexts", "../src/main/resources/broadleaf-contexts.csv")));
        long t = System.nanoTime();
        code = Codebase.read(REPO, contexts);
        double readMs = (System.nanoTime() - t) / 1e6;
        t = System.nanoTime();
        history = History.parse(git("log", "--no-merges", "--since=" + SINCE, "--until=" + UNTIL, "--name-only", "--format=@%h", TAG), contexts);
        double logMs = (System.nanoTime() - t) / 1e6;
        System.out.printf("Broadleaf Commerce %s (commit %s): %d main source files in %d contexts (%d candidates); read in %.0f ms; history %s to %s read in %.0f ms%n",
                TAG, git("rev-parse", "--short=10", TAG + "^{commit}").trim(), code.files.values().stream().mapToInt(Integer::intValue).sum(),
                code.files.size(), contexts.candidates.size(), readMs, SINCE, UNTIL, logMs);
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "coupling" -> coupling();
                case "history" -> history();
                case "roadmap" -> roadmap();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    static List<String> order() {
        List<String> all = new ArrayList<>(contexts.candidates);
        for (String c : contexts.all) {
            if (!all.contains(c)) {
                all.add(c);
            }
        }
        return all;
    }

    static void coupling() {
        List<String> all = order();
        System.out.printf("%-15s %5s", "from \\ to", "files");
        all.forEach(c -> System.out.printf(" %6s", abbrev(c)));
        System.out.printf(" %7s%n", "out");
        int total = 0;
        int toPlatform = 0;
        for (String a : all) {
            System.out.printf("%-15s %5d", a, code.files.getOrDefault(a, 0));
            int out = 0;
            for (String b : all) {
                int n = code.edges(a, b);
                out += n;
                System.out.printf(" %6s", a.equals(b) ? "." : String.valueOf(n));
            }
            total += out;
            toPlatform += code.edges(a, Contexts.PLATFORM);
            System.out.printf(" %7d%n", out);
        }
        Roadmap r = new Roadmap(code, history, contexts);
        System.out.printf("%d import edges between contexts; %d of them into the platform; %d from the platform into business contexts%n", total, toPlatform,
                all.stream().filter(c -> !c.equals(Contexts.PLATFORM)).mapToInt(c -> code.edges(Contexts.PLATFORM, c)).sum());
        System.out.println("cycles (strongly connected contexts), with the platform:    " + r.cycles(true));
        System.out.println("cycles (strongly connected contexts), without the platform: " + r.cycles(false));
        List<String> pairs = new ArrayList<>();
        for (String a : contexts.candidates) {
            for (String b : all) {
                if (a.compareTo(b) < 0 && code.edges(a, b) > 0 && code.edges(b, a) > 0 && !b.equals(Contexts.PLATFORM)) {
                    pairs.add(a + "<->" + b + " (" + code.edges(a, b) + "/" + code.edges(b, a) + ")");
                }
            }
        }
        System.out.println("pairs that import each other: " + pairs.size() + " " + pairs);
        for (boolean withPlatform : new boolean[] {false, true}) {
            long t = System.nanoTime();
            Roadmap.Layering l = r.layering(withPlatform);
            System.out.printf("layering %s the platform (lowest first): %s%n  %d import edges point upwards (fewest possible; found in %.0f ms): %s%n",
                    withPlatform ? "with" : "without", l.order(), l.against(), (System.nanoTime() - t) / 1e6, l.edges());
        }
    }

    static void history() {
        System.out.printf("commits read %d; more than %d files %d; no main sources %d; analysed %d%n", history.read, History.MAX_CHANGESET,
                history.tooLarge, history.noMainSources, history.commits.size());
        Map<Integer, Integer> spread = new java.util.TreeMap<>();
        history.commits.forEach(c -> spread.merge(c.size(), 1, Integer::sum));
        System.out.println("contexts per commit: " + spread);
        System.out.printf("one context: %.1f%%; two or more: %.1f%%%n", 100.0 * spread.getOrDefault(1, 0) / history.commits.size(),
                100.0 * (history.commits.size() - spread.getOrDefault(1, 0)) / history.commits.size());
        System.out.print("revisions per context:");
        order().forEach(c -> System.out.printf(" %s %d,", c, history.revisions(c)));
        System.out.println();
        System.out.println("change coupling (Code Maat's degree; min revisions 5, min shared 5, degree >= 30%), against imports a>b / b>a:");
        List<String> all = order();
        int hidden = 0;
        int shown = 0;
        List<String[]> rows = new ArrayList<>();
        for (String a : all) {
            for (String b : all) {
                if (a.compareTo(b) >= 0) {
                    continue;
                }
                int ra = history.revisions(a);
                int rb = history.revisions(b);
                int shared = history.shared(a, b);
                double degree = history.degree(a, b);
                if (ra >= 5 && rb >= 5 && shared >= 5 && degree >= 30) {
                    rows.add(new String[] {String.format("%05.1f", degree), a, b, String.valueOf(shared), String.valueOf(ra), String.valueOf(rb)});
                }
            }
        }
        rows.sort((x, y) -> y[0].compareTo(x[0]));
        List<String[]> top = new ArrayList<>();
        for (String a : all) {
            for (String b : all) {
                if (a.compareTo(b) < 0 && history.shared(a, b) >= 5) {
                    top.add(new String[] {String.format("%05.1f", history.degree(a, b)), a, b, String.valueOf(history.shared(a, b))});
                }
            }
        }
        top.sort((x, y) -> y[0].compareTo(x[0]));
        System.out.print("strongest pairs (any degree):");
        top.stream().limit(8).forEach(x -> System.out.printf(" %s-%s %.1f%% (%s shared, imports %d/%d);", x[1], x[2], Double.parseDouble(x[0]), x[3],
                code.edges(x[1], x[2]), code.edges(x[2], x[1])));
        System.out.println();
        for (String[] row : rows) {
            int ab = code.edges(row[1], row[2]);
            int ba = code.edges(row[2], row[1]);
            boolean none = ab == 0 && ba == 0;
            hidden += none ? 1 : 0;
            shown++;
            System.out.printf("  %-15s %-15s degree %5.1f%%  shared %4s of %4s / %4s   imports %4d / %4d%s%n", row[1], row[2], Double.parseDouble(row[0]), row[3],
                    row[4], row[5], ab, ba, none ? "   <- no import either way" : "");
        }
        System.out.printf("%d coupled pairs; %d with no import between them%n", shown, hidden);
    }

    static void roadmap() {
        Roadmap r = new Roadmap(code, history, contexts);
        Map<String, List<Roadmap.Step>> plans = new LinkedHashMap<>();
        plans.put("fewest dependants first", r.plan(r.order(c -> -r.inbound(c))));
        plans.put("most changed first", r.plan(r.order(history::revisions)));
        plans.put("largest first", r.plan(r.order(c -> code.files.getOrDefault(c, 0))));
        plans.put("greedy (fewest remote edges)", r.greedy());
        plans.put("value per remote edge", r.valuePerCost());
        Roadmap.Cost start = r.cost(Set.of());
        int n = history.commits.size();
        System.out.printf("before any extraction: remote %d, coordinated %d of %d commits (%.1f%%: platform library plus monolith)%n", start.remote(), start.coordinated(),
                n, 100.0 * start.coordinated() / n);
        System.out.print("inbound import edges per candidate:");
        contexts.candidates.forEach(c -> System.out.printf(" %s %d,", c, r.inbound(c)));
        System.out.println();
        for (var p : plans.entrySet()) {
            System.out.println("-- " + p.getKey());
            System.out.printf("   %-3s %-10s %7s %6s %6s %12s %12s%n", "#", "extract", "remote", "inv", "bidir", "coordinated", "independent");
            int step = 0;
            for (Roadmap.Step s : p.getValue()) {
                Roadmap.Cost c = s.cost();
                System.out.printf("   %-3d %-10s %7d %6d %6d %11.1f%% %11.1f%%%n", ++step, s.extracted(), c.remote(), c.inversions(), c.bidirectional(),
                        100.0 * c.coordinated() / n, 100.0 * c.independent() / n);
            }
        }
        System.out.println("summary: remote edges after 3 and 6 steps; most pairs depending both ways; independent changes after 3 and 6 steps");
        for (var p : plans.entrySet()) {
            List<Roadmap.Step> s = p.getValue();
            System.out.printf("   %-30s remote %5d / %5d   bidir max %2d   independent %5.1f%% / %5.1f%%   coordinated %5.1f%% / %5.1f%%   order %s%n", p.getKey(),
                    s.get(2).cost().remote(), s.get(5).cost().remote(), s.stream().mapToInt(x -> x.cost().bidirectional()).max().orElse(0),
                    100.0 * s.get(2).cost().independent() / n, 100.0 * s.get(5).cost().independent() / n,
                    100.0 * s.get(2).cost().coordinated() / n, 100.0 * s.get(5).cost().coordinated() / n,
                    s.stream().map(Roadmap.Step::extracted).toList());
        }
        Roadmap.Cost end = plans.values().iterator().next().getLast().cost();
        System.out.printf("all %d extracted: remote %d, platform-to-service inversions %d, pairs both ways %d, coordinated %.1f%%, independent %.1f%%%n",
                contexts.candidates.size(), end.remote(), end.inversions(), end.bidirectional(), 100.0 * end.coordinated() / n, 100.0 * end.independent() / n);
    }

    static String abbrev(String c) {
        return c.length() <= 6 ? c : c.substring(0, 5) + ".";
    }

    static String git(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", REPO.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", cmd) + ": " + out);
        }
        return out;
    }
}
