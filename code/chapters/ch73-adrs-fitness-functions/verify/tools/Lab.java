import dev.foodfleet.ch73.adr.DecisionLog;
import dev.foodfleet.ch73.fitness.Bytecode;
import dev.foodfleet.ch73.fitness.FitnessFunction;
import dev.foodfleet.ch73.fitness.Functions;
import dev.foodfleet.ch73.fitness.Probe;
import dev.foodfleet.ch73.fitness.Radar;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Chapter 73's experiments.
 * <ul>
 *   <li>{@code log}: three real decision logs (adr-tools' own, MADR's own, and this handbook's 72 ADRs) against the
 *       log checks L1 to L7, and which of the handbook's decisions any code checks</li>
 *   <li>{@code train}: eight releases of order-service, each with one change, against five fitness functions traced
 *       to ADRs: three static (bytecode), two dynamic (the release running in its own JVM)</li>
 *   <li>{@code noise}: the latency fitness function, repeated, on unchanged code and on three sizes of regression, to
 *       see how often a fixed budget and a relative one fail when nothing changed and when something did</li>
 * </ul>
 * System properties: c73.orders (release sources), c73.radar, c73.docs, c73.code, c73.sources (clones), c73.work.
 */
public final class Lab {

    static final Path ORDERS = Path.of(System.getProperty("c73.orders", "../src/main/resources/orders"));
    static final Path RADAR = Path.of(System.getProperty("c73.radar", "../src/main/resources/radar"));
    static final Path WORK = Path.of(System.getProperty("c73.work", "build/work"));
    static final String APP = "dev/foodfleet/orders/";

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "log" -> log();
                case "train" -> train();
                case "noise" -> noise(Integer.getInteger("c73.repeats", 12));
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ---------------------------------------------------------------- log

    static void log() throws Exception {
        Path src = Path.of(System.getProperty("c73.sources", "build/src"));
        Map<String, DecisionLog> logs = new LinkedHashMap<>();
        logs.put("adr-tools 3.0.0 (its own log)", DecisionLog.readFiles(src.resolve("adr-tools/doc/adr"), DecisionLog.Format.NYGARD));
        logs.put("MADR 4.0.0 (its own log)", DecisionLog.readFiles(src.resolve("madr/docs/decisions"), DecisionLog.Format.MADR));
        logs.put("this handbook (FoodFleet)", DecisionLog.readHandbook(Path.of(System.getProperty("c73.docs", "../../../../docs"))));
        String[] rules = {"L1 sections", "L2 status", "L3 numbering", "L4 date", "L5 chronology", "L6 references", "L7 supersession"};
        System.out.printf("%-32s %5s", "log", "ADRs");
        for (String r : rules) {
            System.out.printf(" %4s", r.substring(0, 2));
        }
        System.out.printf("  %s%n", "clean");
        for (var e : logs.entrySet()) {
            List<DecisionLog.Finding> f = e.getValue().lint();
            System.out.printf("%-32s %5d", e.getKey(), e.getValue().adrs.size());
            for (String r : rules) {
                System.out.printf(" %4d", f.stream().filter(x -> x.rule().equals(r)).count());
            }
            long bad = f.stream().map(DecisionLog.Finding::adr).distinct().count();
            System.out.printf("  %d of %d%n", e.getValue().adrs.size() - bad, e.getValue().adrs.size());
        }
        for (var e : logs.entrySet()) {
            DecisionLog l = e.getValue();
            Map<String, Long> statuses = new TreeMap<>();
            l.adrs.values().forEach(a -> statuses.merge(a.status() == null ? "(none)" : a.status().toLowerCase(), 1L, Long::sum));
            long linked = l.adrs.values().stream().filter(a -> !a.references().isEmpty() || !a.supersedes().isEmpty() || a.supersededBy() != null).count();
            System.out.printf("%s: statuses %s; %d of %d refer to another decision%n", e.getKey(), statuses, linked, l.adrs.size());
            for (DecisionLog.Finding f : l.lint()) {
                if (!f.rule().startsWith("L2") && !f.rule().startsWith("L4") || e.getKey().startsWith("adr-tools") || f.detail().startsWith("unknown")) {
                    System.out.printf("   %-16s %3d  %s%n", f.rule(), f.adr(), f.detail());
                }
            }
        }
        DecisionLog handbook = logs.get("this handbook (FoodFleet)");
        Set<Integer> out = handbook.outOfOrder();
        System.out.printf("handbook dates: %d of %d out of order (the fewest to re-date); first %s, last %s%n", out.size(), handbook.adrs.size(),
                handbook.adrs.values().stream().map(DecisionLog.Adr::date).min(Comparable::compareTo).orElseThrow(),
                handbook.adrs.values().stream().map(DecisionLog.Adr::date).max(Comparable::compareTo).orElseThrow());
        Map<Integer, Set<String>> code = DecisionLog.mentionedInCode(Path.of(System.getProperty("c73.code", "../..")));
        Map<Integer, Set<String>> before = new TreeMap<>();
        code.forEach((adr, mods) -> mods.stream().filter(m -> !m.startsWith("ch73")).forEach(m -> before.computeIfAbsent(adr, k -> new java.util.TreeSet<>()).add(m)));
        System.out.printf("decisions named by code in the handbook's chapters: %d of %d before this chapter %s; %d with it %s%n",
                before.keySet().stream().filter(handbook.adrs::containsKey).count(), handbook.adrs.size(), before,
                code.keySet().stream().filter(handbook.adrs::containsKey).count(), code.keySet());
    }

    // ---------------------------------------------------------------- releases

    record Edit(String file, String from, String to) {
    }

    static final Edit CURRENCY_1 = new Edit("api/OrderApi.java", "long totalInPaise, String placedAt", "long totalInPaise, String currency, String placedAt");
    static final Edit CURRENCY_2 = new Edit("app/OrderService.java", "pricing.total(o), o.placedAt()", "pricing.total(o), \"INR\", o.placedAt()");

    /** The releases: each is release 2 (which added a currency to the API's view) plus one change of its own. */
    static Map<String, List<Edit>> releases() {
        Map<String, List<Edit>> r = new LinkedHashMap<>();
        r.put("r1 baseline", List.of());
        r.put("r2 adds a currency to OrderView", List.of(CURRENCY_1, CURRENCY_2));
        r.put("r3 Pricing gets a default menu", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("domain/Pricing.java", "    public Pricing(Menu menu) {",
                        "    /** For tests and scripts: the full city menu. */\n    public Pricing() {\n        this(new dev.foodfleet.orders.adapter.menu.InMemoryMenu(50_000));\n    }\n\n    public Pricing(Menu menu) {")));
        r.put("r4 formats placedAt with SimpleDateFormat", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("app/OrderService.java", "o.placedAt().toString()",
                        "new java.text.SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ssXXX\").format(java.util.Date.from(o.placedAt()))")));
        r.put("r5 renames cancel to cancelOrder", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("api/OrderApi.java", "boolean cancel(String id);", "boolean cancelOrder(String id);"),
                new Edit("app/OrderService.java", "public boolean cancel(String id)", "public boolean cancelOrder(String id)")));
        r.put("r6 keeps the menu in display order", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("adapter/menu/InMemoryMenu.java", "private final Map<String, Long> prices = new HashMap<>();",
                        "private final java.util.List<Map.Entry<String, Long>> prices = new java.util.ArrayList<>(); // in display order"),
                new Edit("adapter/menu/InMemoryMenu.java", "prices.put(\"item-\" + i, 4_900L + (i % 400) * 100L);",
                        "prices.add(Map.entry(\"item-\" + i, 4_900L + (i % 400) * 100L));"),
                new Edit("adapter/menu/InMemoryMenu.java", "Long p = prices.get(item);",
                        "Long p = prices.stream().filter(e -> e.getKey().equals(item)).map(Map.Entry::getValue).findFirst().orElse(null);")));
        r.put("r7 builds a search index at startup", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("Application.java", "        new HttpAdapter(service).start(",
                        "        java.util.TreeMap<String, String> index = new java.util.TreeMap<>(); // every prefix of every item name, for search\n"
                                + "        for (int i = 0; i < 150_000; i++) {\n            String name = \"item-\" + i + \"-\" + Integer.toHexString(i * 2_654_435_761L > 0 ? i * 31 : i);\n"
                                + "            for (int k = 1; k <= name.length(); k++) {\n                index.merge(name.substring(0, k), name, (a, b) -> a);\n            }\n        }\n"
                                + "        new HttpAdapter(service).start(")));
        r.put("r8 adds byRestaurant", List.of(CURRENCY_1, CURRENCY_2,
                new Edit("api/OrderApi.java", "    boolean cancel(String id);", "    boolean cancel(String id);\n\n    java.util.List<OrderView> byRestaurant(String restaurant);"),
                new Edit("app/OrderService.java", "    @Override\n    public boolean cancel(String id) {",
                        "    @Override\n    public java.util.List<OrderView> byRestaurant(String restaurant) {\n"
                                + "        return orders.values().stream().filter(o -> o.restaurant().equals(restaurant)).map(o -> get(o.id()).orElseThrow()).toList();\n    }\n\n"
                                + "    @Override\n    public boolean cancel(String id) {")));
        return r;
    }

    /** Copies the release sources with its edits applied and compiles them; returns the classes directory. */
    static Path build(String name, List<Edit> edits) throws Exception {
        Path dir = WORK.resolve(name.substring(0, name.indexOf(' ') < 0 ? name.length() : name.indexOf(' ')));
        Path src = dir.resolve("src");
        Path classes = dir.resolve("classes");
        deleteTree(dir);
        try (Stream<Path> s = Files.walk(ORDERS)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path t = src.resolve(ORDERS.relativize(p).toString());
                Files.createDirectories(t.getParent());
                Files.copy(p, t);
            }
        }
        for (Edit e : edits) {
            Path f = src.resolve(APP + e.file());
            String text = Files.readString(f);
            if (!text.contains(e.from())) {
                throw new IllegalStateException(name + ": no '" + e.from() + "' in " + e.file());
            }
            Files.writeString(f, text.replace(e.from(), e.to()));
        }
        Files.createDirectories(classes);
        List<String> args = new ArrayList<>(List.of("--release", "25", "-nowarn", "-d", classes.toString()));
        try (Stream<Path> s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        if (ToolProvider.getSystemJavaCompiler().run(null, null, err, args.toArray(String[]::new)) != 0) {
            throw new IllegalStateException(name + " does not compile: " + err);
        }
        return classes;
    }

    static void train() throws Exception {
        Radar radar = Radar.read(RADAR.resolve("foodfleet-radar.csv"), RADAR.resolve("radar-prefixes.csv"));
        List<FitnessFunction> suite = Functions.suite(radar);
        for (FitnessFunction f : suite) {
            System.out.printf("%-26s %-8s %-8s %s%n", f.id(), f.adr(), f.kind(), f.what());
        }
        System.out.printf("radar: %d blips, %d on hold%n", radar.blips.size(), radar.blips.values().stream().filter(Radar.Blip::onHold).count());
        Map<String, double[]> times = new LinkedHashMap<>();
        // a release ships when every function passes, or when a failure is accepted by a recorded decision;
        // the API check compares with the last release that shipped
        Map<String, String> accepted = Map.of("r2", "F3: OrderView v2 announced to its two consumers; recorded as an amendment to ADR-027");
        Path previous = null;
        Bytecode previousBytecode = null;
        try (Probe warm = Probe.start(build("r0 warm-up", List.of()), "dev.foodfleet.orders.Application")) {
            System.out.printf("warm-up launch (not counted): %.0f ms%n", warm.startupMillis());
        }
        List<String> details = new ArrayList<>();
        System.out.printf("%-44s", "release");
        suite.forEach(f -> System.out.printf(" %-4s", f.id().substring(0, 2)));
        System.out.println("  ships  first answer, p99");
        for (var rel : releases().entrySet()) {
            Path classes = build(rel.getKey(), rel.getValue());
            long t = System.nanoTime();
            Bytecode bc = Bytecode.read(classes);
            double readMs = (System.nanoTime() - t) / 1e6;
            FitnessFunction.Release release = new FitnessFunction.Release(rel.getKey(), classes, previous, bc, previousBytecode);
            System.out.printf("%-44s", rel.getKey());
            String startup = "";
            String p99 = "";
            String key = rel.getKey().substring(0, 2);
            boolean ships = true;
            long probeStart = System.nanoTime();
            try (Probe probe = Probe.start(classes, "dev.foodfleet.orders.Application")) {
                times.computeIfAbsent("launch until healthy", k -> new double[0]);
                times.put("launch until healthy", append(times.get("launch until healthy"), (System.nanoTime() - probeStart) / 1e6));
                times.put("read the classes", append(times.getOrDefault("read the classes", new double[0]), readMs));
                for (FitnessFunction f : suite) {
                    long s = System.nanoTime();
                    FitnessFunction.Result res = f.check().run(release, probe);
                    times.put(f.id(), append(times.getOrDefault(f.id(), new double[0]), (System.nanoTime() - s) / 1e6));
                    System.out.printf(" %-4s", res.pass() ? "+" : "FAIL");
                    if (!res.pass()) {
                        boolean ok = accepted.getOrDefault(key, "").startsWith(f.id().substring(0, 2));
                        ships &= ok;
                        details.add(String.format("%-44s %s: %s%s", rel.getKey(), f.id(), res.detail(), ok ? " (accepted)" : ""));
                    }
                    if (f.id().startsWith("F4")) {
                        startup = res.detail();
                    }
                    if (f.id().startsWith("F5")) {
                        p99 = res.detail();
                    }
                }
            }
            System.out.printf("  %-5s  %s; %s%n", ships ? "yes" : "no", startup, p99);
            if (ships) {
                previous = classes;
                previousBytecode = bc;
            }
        }
        accepted.forEach((k, v) -> System.out.println("accepted failure: " + k + " " + v));
        details.forEach(System.out::println);
        System.out.println("time per release (min-max over the 8 releases):");
        times.forEach((k, v) -> System.out.printf("  %-26s %8.1f - %8.1f ms%n", k, Arrays.stream(v).min().orElse(0), Arrays.stream(v).max().orElse(0)));
    }

    // ---------------------------------------------------------------- noise

    static void noise(int repeats) throws Exception {
        // the regression: a scan of the first N menu items before the map lookup (a "featured items first" change)
        Map<String, List<Edit>> variants = new LinkedHashMap<>();
        variants.put("unchanged", List.of());
        for (int n : new int[] {1_000, 5_000, 20_000}) {
            variants.put("scan " + n, List.of(new Edit("adapter/menu/InMemoryMenu.java", "        Long p = prices.get(item);",
                    "        Long p = null;\n        for (int i = 0; i < " + n + " && p == null; i++) { // featured items first\n"
                            + "            if (item.equals(\"item-\" + i)) {\n                p = prices.get(item);\n            }\n        }\n"
                            + "        if (p == null) {\n            p = prices.get(item);\n        }")));
        }
        // the baseline comes from separate calibration runs of the unchanged release, as it would from earlier builds
        Path calibration = build("noise-calibration", List.of());
        double[] cal = new double[6];
        for (int i = 0; i < cal.length; i++) {
            try (Probe probe = Probe.start(calibration, "dev.foodfleet.orders.Application")) {
                probe.load(Functions.RATE, 2, k -> "/orders/o-" + (k * 7 % 1_000));
                cal[i] = probe.load(Functions.RATE, 5, k -> "/orders/o-" + (k * 13 % 1_000)).percentile(99);
            }
        }
        double base = median(cal);
        System.out.printf("calibration: p99 of %d runs of the unchanged release %s; baseline (median) %.2f ms%n", cal.length, fmt(cal), base);
        Map<String, double[]> p99s = new LinkedHashMap<>();
        Map<String, double[]> p50s = new LinkedHashMap<>();
        for (var v : variants.entrySet()) {
            Path classes = build("noise-" + v.getKey().replace(' ', '-'), v.getValue());
            double[] p99 = new double[repeats];
            double[] p50 = new double[repeats];
            for (int i = 0; i < repeats; i++) {
                try (Probe probe = Probe.start(classes, "dev.foodfleet.orders.Application")) {
                    probe.load(Functions.RATE, 2, k -> "/orders/o-" + (k * 7 % 1_000));
                    Probe.Latencies l = probe.load(Functions.RATE, 5, k -> "/orders/o-" + (k * 13 % 1_000));
                    p99[i] = l.percentile(99);
                    p50[i] = l.percentile(50);
                }
            }
            p99s.put(v.getKey(), p99);
            p50s.put(v.getKey(), p50);
            double[] s = p99.clone();
            Arrays.sort(s);
            double[] m = p50.clone();
            Arrays.sort(m);
            System.out.printf("%-10s p50 %5.2f - %5.2f ms (median %5.2f)   p99 %6.2f - %6.2f ms (median %6.2f)   p99 runs %s%n", v.getKey(), m[0], m[repeats - 1],
                    m[repeats / 2], s[0], s[repeats - 1], s[repeats / 2], fmt(p99));
        }
        Map<String, Double> budgets = new LinkedHashMap<>();
        budgets.put("fixed " + (int) Functions.P99_BUDGET_MS + " ms (ADR budget)", Functions.P99_BUDGET_MS);
        budgets.put("baseline +10%", base * 1.10);
        budgets.put("baseline +25%", base * 1.25);
        budgets.put("baseline +50%", base * 1.50);
        System.out.printf("%-28s", "budget");
        variants.keySet().forEach(k -> System.out.printf(" %20s", k));
        System.out.println();
        for (var b : budgets.entrySet()) {
            System.out.printf("%-28s", String.format("%s (%.1f ms)", b.getKey(), b.getValue()));
            for (String k : variants.keySet()) {
                double[] p = p99s.get(k);
                int single = 0;
                int bestOf3 = 0;
                for (double x : p) {
                    single += x > b.getValue() ? 1 : 0;
                }
                for (int i = 0; i + 2 < p.length; i += 3) {
                    bestOf3 += Math.min(p[i], Math.min(p[i + 1], p[i + 2])) > b.getValue() ? 1 : 0;
                }
                System.out.printf(" %20s", single + "/" + p.length + ", best-of-3 " + bestOf3 + "/" + p.length / 3);
            }
            System.out.println();
        }
    }

    static double median(double[] v) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
    }

    static String fmt(double[] v) {
        StringBuilder b = new StringBuilder();
        for (double x : v) {
            b.append(String.format("%.1f ", x));
        }
        return b.toString().trim();
    }

    static double[] append(double[] a, double x) {
        double[] b = Arrays.copyOf(a, a.length + 1);
        b[a.length] = x;
        return b;
    }

    static void deleteTree(Path p) throws Exception {
        if (Files.exists(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path x : s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                    Files.delete(x);
                }
            }
        }
    }
}
