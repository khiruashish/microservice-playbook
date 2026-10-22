import dev.foodfleet.ch63.consumer.AppMenuClient;
import dev.foodfleet.ch63.consumer.OrderMenuClient;
import dev.foodfleet.ch63.contract.Contract;
import dev.foodfleet.ch63.contract.Verifier;
import dev.foodfleet.ch63.menu.MenuService;
import dev.foodfleet.ch63.openapi.SchemaDiff;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chapter 63's experiment. Twelve changes a menu-service developer might make, each applied to the provider's code
 * (and to its OpenAPI document, as the developer would update it), compiled, and started. Then four checks, each
 * asking "does this break a consumer?":
 * <ol>
 *   <li>an OpenAPI breaking-change diff of the old and new document (oasdiff's rules)</li>
 *   <li>provider verification of order-service's contract</li>
 *   <li>provider verification of app-bff's contract</li>
 *   <li>an end-to-end suite: both real clients against the real provider, on the test restaurant (7)</li>
 * </ol>
 * and the answer: both clients run against every restaurant and item in the data, compared with the old provider.
 * Then the same contracts written with no matchers, type matchers only, and type matchers plus formats.
 * Usage: Lab <provider source dir> <openapi.json> <contracts dir> <changes.tsv> <work dir> <data-changes.tsv>
 */
public final class Lab {

    record Edit(String id, String description, String file, String find, String replace) {
    }

    record Verdict(boolean flagged, String detail, long micros) {
    }

    public static void main(String[] args) throws Exception {
        Path src = Path.of(args[0]);
        String spec = Files.readString(Path.of(args[1]));
        Contract orderContract = Contract.load(Path.of(args[2], "order-service-menu-service.json"));
        Contract appContract = Contract.load(Path.of(args[2], "app-bff-menu-service.json"));
        Map<String, List<Edit>> changes = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(args[3]))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            changes.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new Edit(f[0], f[1], f[2], unescape(f[3]), unescape(f[4])));
        }
        Path work = Path.of(args[4]);
        Map<String, List<Edit>> dataChanges = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(args[5]))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            dataChanges.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new Edit(f[0], f[1], f[2], unescape(f[3]), unescape(f[4])));
        }
        Map<String, Boolean> breaksByChange = new LinkedHashMap<>();

        // the old provider, for the baseline every consumer output is compared with
        Map<String, String> baseline;
        try (Provider p = Provider.start(compile(src, List.of(), work.resolve("base")))) {
            baseline = outputs(p.url(), true);
            List<Verifier.Result> o = Verifier.verify(orderContract, p.url());
            List<Verifier.Result> a = Verifier.verify(appContract, p.url());
            System.out.printf("baseline: order-service contract %d/%d interactions verified, app-bff %d/%d; %d consumer outputs recorded%n",
                    o.stream().filter(Verifier.Result::ok).count(), o.size(), a.stream().filter(Verifier.Result::ok).count(), a.size(), baseline.size());
        }
        System.out.println();
        System.out.printf("%-3s %-42s %-10s %-10s %-10s %-10s | %-24s%n", "", "change", "OpenAPI", "contract:", "contract:", "E2E", "actually breaks");
        System.out.printf("%-3s %-42s %-10s %-10s %-10s %-10s | %-24s%n", "", "", "diff", "order-svc", "app-bff", "(rest. 7)", "(all data)");
        int[] caught = new int[4];
        int[] falseAlarms = new int[4];
        int breaking = 0;
        long[] time = new long[4];
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, List<Edit>> change : changes.entrySet()) {
            String id = change.getKey();
            List<Edit> edits = change.getValue();
            String newSpec = spec;
            for (Edit e : edits) {
                if (e.file().endsWith(".json")) {
                    newSpec = apply(newSpec, e);
                }
            }
            long t = System.nanoTime();
            List<SchemaDiff.Finding> findings = SchemaDiff.diff(spec, newSpec);
            Verdict diff = new Verdict(SchemaDiff.breaking(findings), findings.stream()
                    .map(f -> f.level() + " " + f.id()).distinct().reduce((x, y) -> x + ", " + y).orElse("no changes found"), (System.nanoTime() - t) / 1000);
            Verdict order;
            Verdict app;
            Verdict e2e;
            List<String> broken = new ArrayList<>();
            try (Provider p = Provider.start(compile(src, edits.stream().filter(e -> e.file().endsWith(".java")).toList(), work.resolve(id)))) {
                outputs(p.url(), true); // warm the new JVM up, so no check pays for its first requests
                order = verify(orderContract, p.url());
                app = verify(appContract, p.url());
                t = System.nanoTime();
                Map<String, String> e2eOut = outputs(p.url(), false);
                List<String> e2eDiffs = differences(baseline, e2eOut);
                e2e = new Verdict(!e2eDiffs.isEmpty(), e2eDiffs.isEmpty() ? "all journeys as before" : e2eDiffs.getFirst(), (System.nanoTime() - t) / 1000);
                Map<String, String> all = outputs(p.url(), true);
                for (String d : differences(baseline, all)) {
                    String consumer = d.startsWith("order-service") ? "order-service" : "app-bff";
                    if (!broken.contains(consumer)) {
                        broken.add(consumer);
                    }
                }
                details.add(id + " " + edits.getFirst().description() + "\n      diff:      " + diff.detail() + "\n      order-svc: " + order.detail()
                        + "\n      app-bff:   " + app.detail() + "\n      E2E:       " + e2e.detail() + "\n      truth:     "
                        + differences(baseline, all).stream().limit(2).toList() + (differences(baseline, all).size() > 2 ? " and " + (differences(baseline, all).size() - 2) + " more" : ""));
            }
            boolean breaks = !broken.isEmpty();
            breaksByChange.put(id, breaks);
            breaking += breaks ? 1 : 0;
            Verdict[] v = {diff, order, app, e2e};
            for (int k = 0; k < 4; k++) {
                time[k] += v[k].micros();
                if (v[k].flagged() && breaks) {
                    caught[k]++;
                }
                if (v[k].flagged() && !breaks) {
                    falseAlarms[k]++;
                }
            }
            System.out.printf("%-3s %-42s %-10s %-10s %-10s %-10s | %-24s%n", id, edits.getFirst().description(), mark(diff), mark(order), mark(app), mark(e2e),
                    breaks ? String.join(", ", broken) : "nothing");
        }
        System.out.println();
        System.out.printf("%d of %d changes break a consumer%n", breaking, changes.size());
        String[] names = {"OpenAPI diff", "order-service contract", "app-bff contract", "E2E on restaurant 7"};
        for (int k = 0; k < 4; k++) {
            System.out.printf("   %-24s caught %2d of %d, false alarms %d, %6.1f ms per change%n", names[k], caught[k], breaking, falseAlarms[k], time[k] / 1000.0 / changes.size());
        }
        System.out.println();
        System.out.println("details:");
        details.forEach(d -> System.out.println("   " + d));
        matchers(src, orderContract, appContract, changes, dataChanges, breaksByChange, work);
    }

    /**
     * The same two contracts written three ways: examples recorded from the provider with no matchers; type matchers
     * only; type matchers plus formats (regexes), as written. Each verified against the 12 API changes and 5 changes
     * to data no consumer should care about.
     */
    static void matchers(Path src, Contract order, Contract app, Map<String, List<Edit>> changes, Map<String, List<Edit>> dataChanges,
                         Map<String, Boolean> breaks, Path work) throws Exception {
        List<Contract> recorded;
        try (Provider p = Provider.start(compile(src, List.of(), work.resolve("base-rec")))) {
            recorded = List.of(record(order, p.url()), record(app, p.url()));
        }
        Map<String, List<Contract>> variants = new LinkedHashMap<>();
        variants.put("examples recorded from the provider, no matchers", recorded);
        variants.put("type matchers only", List.of(typesOnly(order), typesOnly(app)));
        variants.put("type matchers + formats (as written)", List.of(order, app));
        Map<String, List<Edit>> all = new LinkedHashMap<>(changes);
        all.putAll(dataChanges);
        Map<String, Map<String, Boolean>> flagged = new LinkedHashMap<>();
        for (Map.Entry<String, List<Edit>> change : all.entrySet()) {
            try (Provider p = Provider.start(compile(src, change.getValue().stream().filter(e -> e.file().endsWith(".java")).toList(), work.resolve("m-" + change.getKey())))) {
                for (Map.Entry<String, List<Contract>> v : variants.entrySet()) {
                    boolean f = v.getValue().stream().anyMatch(c -> Verifier.verify(c, p.url()).stream().anyMatch(r -> !r.ok()));
                    flagged.computeIfAbsent(v.getKey(), k -> new LinkedHashMap<>()).put(change.getKey(), f);
                }
            }
        }
        System.out.println();
        System.out.println("== matchers");
        System.out.println("both consumers' contracts, written three ways, against the 12 API changes and 5 changes to menu data");
        System.out.printf("%-50s %-24s %-28s %-24s %s%n", "contracts", "breaking changes caught", "harmless API changes flagged", "data changes flagged", "flagged");
        long breaking = breaks.values().stream().filter(b -> b).count();
        for (Map.Entry<String, Map<String, Boolean>> v : flagged.entrySet()) {
            int caught = 0;
            int harmless = 0;
            int data = 0;
            List<String> ids = new ArrayList<>();
            for (Map.Entry<String, Boolean> f : v.getValue().entrySet()) {
                if (!f.getValue()) {
                    continue;
                }
                ids.add(f.getKey());
                if (f.getKey().startsWith("D")) {
                    data++;
                } else if (breaks.get(f.getKey())) {
                    caught++;
                } else {
                    harmless++;
                }
            }
            System.out.printf("%-50s %-24s %-28s %-24s %s%n", v.getKey(), caught + " of " + breaking, harmless + " of " + (breaks.size() - breaking),
                    data + " of " + dataChanges.size(), ids);
        }
        for (Map.Entry<String, List<Edit>> d : dataChanges.entrySet()) {
            System.out.println("   " + d.getKey() + " " + d.getValue().getFirst().description());
        }
    }

    /** A contract whose examples are what the provider answers today, with every matching rule removed. */
    static Contract record(Contract c, String url) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        List<Contract.Interaction> list = new ArrayList<>();
        for (Contract.Interaction i : c.interactions()) {
            java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url + i.path()));
            i.requestHeaders().forEach(req::header);
            String body = http.send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            Object example = i.body() == null ? null : dev.foodfleet.ch63.json.Json.parse(body);
            list.add(new Contract.Interaction(i.description(), i.providerState(), i.method(), i.path(), i.requestHeaders(), i.status(),
                    i.responseHeaders(), example, Map.of()));
        }
        return new Contract(c.consumer(), c.provider(), list);
    }

    /** The same contract with every regex replaced by a type match. */
    static Contract typesOnly(Contract c) {
        List<Contract.Interaction> list = new ArrayList<>();
        for (Contract.Interaction i : c.interactions()) {
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            i.bodyRules().forEach((k, v) -> rules.put(k, "regex".equals(v.get("match")) ? Map.of("match", "type") : v));
            list.add(new Contract.Interaction(i.description(), i.providerState(), i.method(), i.path(), i.requestHeaders(), i.status(),
                    i.responseHeaders(), i.body(), rules));
        }
        return new Contract(c.consumer(), c.provider(), list);
    }

    static String mark(Verdict v) {
        return v.flagged() ? "FLAGS" : "passes";
    }

    static Verdict verify(Contract c, String url) {
        long t = System.nanoTime();
        List<Verifier.Result> results = Verifier.verify(c, url);
        List<String> failed = results.stream().filter(r -> !r.ok()).map(r -> r.interaction() + ": " + r.mismatches().getFirst()).toList();
        return new Verdict(!failed.isEmpty(), failed.isEmpty() ? "all " + results.size() + " interactions verified" : failed.getFirst()
                + (failed.size() > 1 ? " (+" + (failed.size() - 1) + " more)" : ""), (System.nanoTime() - t) / 1000);
    }

    /** What each consumer gets: per item for order-service, per restaurant for app-bff. The E2E suite covers restaurant 7 only. */
    static Map<String, String> outputs(String url, boolean allData) {
        Map<String, String> out = new LinkedHashMap<>();
        OrderMenuClient order = new OrderMenuClient(url);
        AppMenuClient app = new AppMenuClient(url);
        for (Map.Entry<Integer, List<MenuService.Item>> r : MenuService.DATA.entrySet()) {
            if (!allData && r.getKey() != 7) {
                continue;
            }
            List<String> items = new ArrayList<>(r.getValue().stream().map(MenuService.Item::id).toList());
            items.add("pizza");
            for (String item : allData ? items : List.of("paneer-tikka", "kulfi", "pizza")) {
                out.put("order-service " + r.getKey() + "/" + item, attempt(() -> order.price(r.getKey(), item).toString()));
            }
            out.put("app-bff menu " + r.getKey(), attempt(() -> app.menu(r.getKey()).toString()));
        }
        return out;
    }

    interface Call {
        String run();
    }

    static String attempt(Call c) {
        try {
            return c.run();
        } catch (RuntimeException e) {
            return "FAILED: " + e;
        }
    }

    static List<String> differences(Map<String, String> before, Map<String, String> after) {
        List<String> d = new ArrayList<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            String old = before.get(e.getKey());
            if (!Objects.equals(old, e.getValue())) {
                d.add(e.getKey() + ": " + summarize(old, e.getValue()));
            }
        }
        return d;
    }

    static String summarize(String old, String now) {
        if (now.startsWith("FAILED")) {
            return now.length() > 110 ? now.substring(0, 110) + "..." : now;
        }
        if (old.length() != now.length() && now.length() < old.length() / 2) {
            return "shorter result (" + now.length() + " chars instead of " + old.length() + ")";
        }
        int i = 0;
        while (i < Math.min(old.length(), now.length()) && old.charAt(i) == now.charAt(i)) {
            i++;
        }
        int from = Math.max(0, i - 20);
        return "..." + now.substring(from, Math.min(now.length(), i + 40)) + "... (was ..." + old.substring(from, Math.min(old.length(), i + 40)) + "...)";
    }

    static String unescape(String s) {
        return s.replace("\\n", "\n");
    }

    static String apply(String text, Edit e) {
        if (!text.contains(e.find())) {
            throw new IllegalStateException(e.id() + ": text not found in " + e.file() + ": " + e.find());
        }
        return text.replace(e.find(), e.replace());
    }

    /** Copies MenuService.java, applies the edits, compiles it. */
    static Path compile(Path src, List<Edit> edits, Path dir) throws IOException {
        Path file = src.resolve("dev/foodfleet/ch63/menu/MenuService.java");
        String code = Files.readString(file);
        for (Edit e : edits) {
            code = apply(code, e);
        }
        Path out = dir.resolve("src/dev/foodfleet/ch63/menu/MenuService.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, code);
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-nowarn", "-d", classes.toString(), out.toString());
        if (rc != 0) {
            throw new IllegalStateException("doesn't compile");
        }
        return classes;
    }

    /** The provider in its own JVM, so each change runs its own compiled code. */
    record Provider(Process process, int port) implements AutoCloseable {
        static Provider start(Path classes) throws Exception {
            int port;
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            }
            ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classes.toString(),
                    "dev.foodfleet.ch63.menu.MenuService", String.valueOf(port)).inheritIO();
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            Process p = pb.start();
            for (int i = 0; i < 200; i++) {
                try (Socket s = new Socket("127.0.0.1", port)) {
                    return new Provider(p, port);
                } catch (IOException e) {
                    Thread.sleep(25);
                }
            }
            p.destroyForcibly();
            throw new IllegalStateException("provider didn't start");
        }

        String url() {
            return "http://127.0.0.1:" + port;
        }

        @Override
        public void close() throws Exception {
            process.destroy();
            process.waitFor();
        }
    }
}
