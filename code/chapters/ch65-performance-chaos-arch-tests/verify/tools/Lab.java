import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch65.arch.ClassGraph;
import dev.foodfleet.ch65.arch.FoodFleetRules;
import dev.foodfleet.ch65.arch.Freeze;
import dev.foodfleet.ch65.arch.Rules;
import dev.foodfleet.ch65.chaos.Experiment;
import dev.foodfleet.ch65.chaos.FaultProxy;
import dev.foodfleet.ch65.chaos.Services;
import dev.foodfleet.ch65.load.LoadModels;
import dev.foodfleet.ch65.load.StallingServer;

import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Chapter 65's experiments.
 * <ul>
 *   <li>{@code stall}: a service that freezes for 1 s every 10 s, load-tested with a closed and an open model</li>
 *   <li>{@code capacity}: the same service without freezes, pushed towards and past its capacity by each model</li>
 *   <li>{@code chaos}: order-service with three client configurations against two faults in menu-service, as a chaos
 *       experiment with a steady-state hypothesis and an abort condition</li>
 *   <li>{@code arch}: ten architecture violations planted in a small service, against five fitness functions; a
 *       frozen baseline; and the rules' cost on the JDK's own modules</li>
 * </ul>
 * Usage: Lab <sample sources dir> <violations.tsv> <work dir> modes...
 */
public final class Lab {

    public static void main(String[] args) throws Exception {
        for (String mode : List.of(args).subList(3, args.length)) {
            System.out.println("== " + mode);
            switch (mode) {
                case "stall" -> stall();
                case "capacity" -> capacity();
                case "chaos" -> chaos();
                case "arch" -> arch(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ load

    static String row(String name, LoadModels.Result r, boolean corrected) {
        var l = corrected ? r.corrected() : r.latencies();
        return String.format("%-48s %8d %8.0f/s %8.1f %8.1f %8.1f %8.1f %8.1f", name, l.count(), r.rate(), l.percentile(50), l.percentile(90),
                l.percentile(99), l.percentile(99.9), l.max());
    }

    static final String HEADER = String.format("%-48s %8s %10s %8s %8s %8s %8s %8s", "load model", "samples", "requests", "p50 ms", "p90", "p99", "p99.9", "max");

    static void stall() throws Exception {
        System.out.println("menu-service: 4 workers, 10 ms per request, frozen for 1 s every 10 s; 60 s of load");
        System.out.println(HEADER);
        try (StallingServer s = new StallingServer(4, 10, 10_000, 1_000)) {
            LoadModels.closed(s.url(), 2, 3, 13.3); // warm-up
            LoadModels.Result closed = LoadModels.closed(s.url(), 2, 60, 1000.0 * 2 / 150);
            System.out.println(row("closed: 2 virtual users, back to back", closed, false));
            System.out.println(row("closed, corrected for coordinated omission", closed, true));
            LoadModels.Result open = LoadModels.open(s.url(), 150, 60);
            System.out.println(row("open: 150 requests/s, timed from when due", open, false));
        }
    }

    static void capacity() throws Exception {
        System.out.println("menu-service: 4 workers, 10 ms per request (capacity about 400 requests/s), no freezes; 20 s per step");
        System.out.println(HEADER);
        try (StallingServer s = new StallingServer(4, 10, 10_000, 0)) {
            LoadModels.closed(s.url(), 4, 3, 10); // warm-up
            for (int users : new int[] {4, 8, 16, 32}) {
                System.out.println(row("closed: " + users + " virtual users", LoadModels.closed(s.url(), users, 20, 10), false));
            }
            for (int rate : new int[] {200, 320, 380, 420}) {
                System.out.println(row("open: " + rate + " requests/s", LoadModels.open(s.url(), rate, 20), false));
            }
        }
    }

    // ------------------------------------------------------------------ chaos

    static void chaos() throws Exception {
        Experiment.Hypothesis h = new Experiment.Hypothesis(0.99, 500);
        System.out.println("order-service calls menu-service through a fault proxy; 50 orders/s for 50 s: 10 s steady, 30 s of fault, 10 s after; users give up after 5 s");
        System.out.println("steady state per 2 s window: >= 99% of orders succeed and p99 < 500 ms; abort the fault when a window falls below 90% success");
        System.out.printf("%-34s %-26s %-16s %-22s %-16s %s%n", "fault", "order-service's client", "fault aborted", "orders OK during fault", "p99 during fault", "windows out of steady state");
        HttpServer menu = Services.menu();
        Map<String, FaultProxy.Fault> faults = new LinkedHashMap<>();
        faults.put("2 s latency on half the calls", new FaultProxy.Latency(2000, 0.5));
        faults.put("menu-service hangs", new FaultProxy.Down());
        Map<Services.Client, String> names = Map.of(Services.Client.NO_TIMEOUT, "no timeout", Services.Client.TIMEOUT_AND_RETRY,
                "300 ms timeout, 1 retry", Services.Client.TIMEOUT_AND_FALLBACK, "300 ms timeout, last price");
        for (Map.Entry<String, FaultProxy.Fault> f : faults.entrySet()) {
            for (Services.Client c : Services.Client.values()) {
                try (FaultProxy proxy = new FaultProxy("http://127.0.0.1:" + menu.getAddress().getPort())) {
                    HttpServer order = Services.order(proxy.url(), c);
                    Experiment.Report r = Experiment.run("http://127.0.0.1:" + order.getAddress().getPort(), proxy, f.getValue(), h, 0.90, 50, 10, 30, 10, 2);
                    order.stop(0);
                    System.out.printf("%-34s %-26s %-16s %-22s %-16s %d of %d%n", f.getKey(), names.get(c),
                            r.abortedAtS() == null ? "no" : String.format("after %.0f s", r.abortedAtS()),
                            String.format("%d of %d (%.0f%%)", r.faultOk(), r.faultRequests(), 100.0 * r.faultOk() / r.faultRequests()),
                            String.format("%.0f ms", r.faultP99()), Experiment.breaches(r, h), r.windows().size());
                }
            }
        }
        menu.stop(0);
    }

    // ------------------------------------------------------------------ arch

    record Edit(String id, String description, String file, String find, String replace) {
    }

    static void arch(Path sample, Path tsv, Path work) throws Exception {
        Map<String, List<Edit>> violations = new LinkedHashMap<>();
        for (String line : Files.readAllLines(tsv)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            violations.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new Edit(f[0], f[1], f[2], f[3].replace("\\n", "\n"), f[4].replace("\\n", "\n")));
        }
        List<Rules.Rule> rules = FoodFleetRules.all();
        Map<String, List<String>> clean = Rules.evaluate(compile(sample, List.of(), work.resolve("clean")), rules);
        System.out.println("a small service (api, application, domain, infrastructure, config; 10 classes) and 5 fitness functions");
        System.out.println("clean code: " + clean.values().stream().mapToInt(List::size).sum() + " violations");
        StringBuilder header = new StringBuilder(String.format("%-4s %-48s", "", "planted violation"));
        for (Rules.Rule r : rules) {
            header.append(String.format(" %-12s", r.name().length() > 12 ? r.name().substring(0, 12) : r.name()));
        }
        System.out.println(header);
        List<String> missed = new ArrayList<>();
        for (Map.Entry<String, List<Edit>> v : violations.entrySet()) {
            Map<String, List<String>> result = Rules.evaluate(compile(sample, v.getValue(), work.resolve(v.getKey())), rules);
            StringBuilder row = new StringBuilder(String.format("%-4s %-48s", v.getKey(), v.getValue().getFirst().description()));
            boolean any = false;
            for (Rules.Rule r : rules) {
                int n = result.get(r.name()).size() - clean.get(r.name()).size();
                any |= n > 0;
                row.append(String.format(" %-12s", n > 0 ? "caught (" + n + ")" : "-"));
            }
            System.out.println(row);
            if (!any) {
                missed.add(v.getKey());
            }
        }
        System.out.println("missed by every rule: " + missed);
        for (String id : missed) {
            ClassGraph whole = ClassGraph.of(work.resolve(id).resolve("classes"), FoodFleetRules.ROOT, ClassGraph.Mode.CONSTANT_POOL);
            int n = Rules.evaluate(whole, rules).values().stream().mapToInt(List::size).sum();
            System.out.printf("   %s read from the whole constant pool, as jdeps reads it: %d violations%n", id, n);
        }
        System.out.println("rule names: " + rules.stream().map(Rules.Rule::name).toList());

        // freeze: a legacy service already has V1 and V5; then someone adds V2
        System.out.println();
        List<Edit> legacy = new ArrayList<>(violations.get("V1"));
        legacy.addAll(violations.get("V5"));
        Map<String, List<String>> before = Rules.evaluate(compile(sample, legacy, work.resolve("legacy")), rules);
        Map<String, java.util.Set<String>> store = Freeze.store(before);
        List<Edit> later = new ArrayList<>(legacy);
        later.addAll(violations.get("V2"));
        Map<String, List<String>> after = Rules.evaluate(compile(sample, later, work.resolve("legacy-v2")), rules);
        Map<String, List<String>> fresh = Freeze.newOnly(after, store);
        System.out.printf("frozen: a legacy service with V1 and V5 has %d violations, all stored; after V2 is added, %d violations, %d of them new and reported:%n",
                before.values().stream().mapToInt(List::size).sum(), after.values().stream().mapToInt(List::size).sum(),
                fresh.values().stream().mapToInt(List::size).sum());
        fresh.forEach((rule, v) -> v.forEach(line -> System.out.println("   " + rule + ": " + line)));

        // cost
        System.out.println();
        System.out.println("the rules' cost: reading classes and checking for cycles between packages");
        long t = System.nanoTime();
        ClassGraph g = ClassGraph.of(work.resolve("clean/classes"), FoodFleetRules.ROOT);
        double read = (System.nanoTime() - t) / 1e6;
        t = System.nanoTime();
        Rules.evaluate(g, rules);
        System.out.printf("   %-34s %6d classes   read %7.1f ms   all 5 rules %7.1f ms%n", "the sample service", g.classes().size(), read, (System.nanoTime() - t) / 1e6);
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        for (String module : List.of("java.net.http", "java.base")) {
            t = System.nanoTime();
            ClassGraph m = ClassGraph.of(jrt.getPath("modules", module), "java");
            read = (System.nanoTime() - t) / 1e6;
            t = System.nanoTime();
            List<String> cycles = Rules.noCycles(module.equals("java.base") ? "java" : "java.net.http", Set.of()).check(m);
            System.out.printf("   %-34s %6d classes   read %7.1f ms   cycles %7.1f ms (%d found%s)%n", "the JDK's " + module + " module", m.classes().size(), read,
                    (System.nanoTime() - t) / 1e6, cycles.size(), cycles.size() >= Rules.MAX_CYCLES ? ", stopped at the limit" : "");
        }
    }

    /** Copies the sample's sources, applies the edits, compiles them, and reads the classes. */
    static ClassGraph compile(Path sample, List<Edit> edits, Path dir) throws Exception {
        Path src = dir.resolve("src");
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sample)) {
            for (Path f : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(f);
                for (Edit e : edits) {
                    if (e.file().equals(f.getFileName().toString())) {
                        if (!text.contains(e.find())) {
                            throw new IllegalStateException(e.id() + ": not found in " + e.file());
                        }
                        text = text.replace(e.find(), e.replace());
                    }
                }
                Path out = src.resolve(sample.relativize(f));
                Files.createDirectories(out.getParent());
                Files.writeString(out, text);
                files.add(out.toString());
            }
        }
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);
        List<String> args = new ArrayList<>(List.of("-nowarn", "-d", classes.toString()));
        args.addAll(files);
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("doesn't compile: " + edits);
        }
        return ClassGraph.of(classes, FoodFleetRules.ROOT);
    }
}
