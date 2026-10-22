import dev.foodfleet.ch51.rules.PrometheusSloRules;
import dev.foodfleet.ch51.rules.PrometheusSloRules.Spec;
import dev.foodfleet.ch51.slo.Slo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 51's experiments, evaluated by Prometheus 3.15.0's own rule engine ({@code promtool test rules}), so alert
 * timing includes {@code for}, rule evaluation order and range-vector extrapolation exactly as a Prometheus server
 * would apply them.
 * <ul>
 *   <li>{@code alerts}: eight incidents and two healthy services, five alerting strategies; when each alert fires,
 *       how much error budget is gone by then, how long it keeps firing after recovery, and how often it pages</li>
 *   <li>{@code latency}: a latency SLI from a Micrometer histogram with and without a bucket at the threshold, and
 *       what {@code histogram_quantile} says about the same data (see {@link Latency})</li>
 *   <li>{@code routing}: the chapter's alertmanager.yml on a real Alertmanager (see {@link Routing})</li>
 * </ul>
 * Needs -Dc51.promtool; work files in -Dc51.dir (default /tmp/c51).
 */
public final class Measure {

    static final Path DIR = Path.of(System.getProperty("c51.dir", "/tmp/c51"));
    static final String PROMTOOL = System.getProperty("c51.promtool", "build/bin/promtool");
    static final String SERIES = "http_server_requests_seconds_count{job=\"order-service\", uri=\"/orders\", method=\"POST\", outcome=\"%s\"}";
    static final Slo SLO = new Slo("order-service", "checkout", 0.999, Duration.ofDays(30));
    static final int DAY = 1440;
    /** Three days of normal traffic before anything is measured, so even the 3-day windows start full. */
    static final int LEAD = 3 * DAY;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DIR);
        for (String mode : args) {
            switch (mode) {
                case "alerts" -> alerts();
                case "latency" -> Latency.run();
                case "routing" -> Routing.run();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ========================================================================================================== alerts

    /** Requests and failures per minute, from minute 0; the incident is [start, end) in minutes. */
    record Scenario(String name, double[] total, double[] errors, int start, int end) {

        /** The same traffic with {@code count} short incidents of {@code length} minutes, {@code every} minutes apart. */
        Scenario withBlips(int from, int count, int every, int length, double ratio) {
            double[] e = errors.clone();
            for (int k = 0; k < count; k++) {
                for (int m = from + k * every; m < from + k * every + length; m++) {
                    e[m] = total[m] * ratio;
                }
            }
            return new Scenario(name, total, e, from, from + (count - 1) * every + length);
        }
    }

    /** Builds a scenario: steady traffic with a baseline error ratio, and an incident with its own ratio. */
    static Scenario steady(String name, int minutes, double perMinute, double baseline, int start, int end, double incident) {
        double[] t = new double[minutes];
        double[] e = new double[minutes];
        for (int i = 0; i < minutes; i++) {
            t[i] = perMinute;
            e[i] = perMinute * (i >= start && i < end ? incident : baseline);
        }
        return new Scenario(name, t, e, start, end);
    }

    /** A healthy service: each request fails independently with probability {@code p} (seeded, so runs repeat). */
    static Scenario noisy(String name, int minutes, int perMinute, double p, long seed) {
        Random r = new Random(seed);
        double[] t = new double[minutes];
        double[] e = new double[minutes];
        for (int i = 0; i < minutes; i++) {
            t[i] = perMinute;
            int bad = 0;
            for (int k = 0; k < perMinute; k++) {
                if (r.nextDouble() < p) {
                    bad++;
                }
            }
            e[i] = bad;
        }
        return new Scenario(name, t, e, -1, -1);
    }

    record Strategy(String label, String alertname, String severity) {
    }

    static final List<Strategy> STRATEGIES = List.of(
            new Strategy("error ratio > 1% for 5m", "StaticErrorRatio", null),
            new Strategy("error ratio > 0.1% (the SLO) for 5m", "SloThresholdErrorRatio", null),
            new Strategy("burn rate > 14.4 over 1h only", "BurnRate1hOnly", null),
            new Strategy("multi-window burn rate: page", "CheckoutErrorBudgetBurn", "page"),
            new Strategy("multi-window burn rate: ticket", "CheckoutErrorBudgetBurn", "ticket"),
            new Strategy("...page, with a 100-request minimum", "CheckoutGuardedErrorBudgetBurn", "page"));

    static String rules() {
        String ratio5m = "sum(rate(http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))";
        String naive = """
                  - name: naive
                    interval: 1m
                    rules:
                      - alert: StaticErrorRatio
                        expr: %s > 0.01
                        for: 5m
                      - alert: SloThresholdErrorRatio
                        expr: %s > 0.001
                        for: 5m
                      - alert: BurnRate1hOnly
                        expr: sum(rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[1h])) / sum(rate(http_server_requests_seconds_count[1h])) > 14.4 * 0.001
                """.formatted(ratio5m, ratio5m);
        String err = "sum(rate(http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"}[{{window}}])) or vector(0)";
        String tot = "sum(rate(http_server_requests_seconds_count[{{window}}]))";
        String mwmb = PrometheusSloRules.generate(
                new Spec(SLO, err, tot, Duration.ofMinutes(1), 0, "https://runbooks.example/checkout"),
                new Spec(new Slo("order-service", "checkout-guarded", 0.999, Duration.ofDays(30)), err, tot, Duration.ofMinutes(1), 100,
                        "https://runbooks.example/checkout"));
        // promtool's test engine stops any query that loads more than 10,000 samples; the 30-day budget rule (not used
        // by any alert) would, so it's left out here. A Prometheus server's limit is 50 million.
        mwmb = mwmb.replaceAll("(?s)  - name: slo-[a-z-]+-budget\n.*?(?=  - name: |$)", "");
        return mwmb + naive;
    }

    static void alerts() throws Exception {
        int lead = LEAD;
        List<Scenario> scenarios = List.of(
                steady("total outage, 3 minutes", lead + DAY, 1000, 0.0002, lead, lead + 3, 1.0),
                steady("bad deploy: 10% fail for 20 minutes", lead + DAY, 1000, 0.0002, lead, lead + 20, 0.10),
                steady("2% fail for 2 hours", lead + DAY, 1000, 0.0002, lead, lead + 120, 0.02),
                steady("0.6% fail for 18 hours", lead + 2 * DAY, 1000, 0.0002, lead, lead + 18 * 60, 0.006),
                steady("0.25% fail for 4 days", lead + 5 * DAY, 1000, 0.0002, lead, lead + 4 * DAY, 0.0025),
                steady("0.15% fail for 10 days", lead + 11 * DAY, 1000, 0.0002, lead, lead + 10 * DAY, 0.0015),
                steady("5% fail for 1 minute, six times, an hour apart", lead + DAY, 1000, 0.0002, 0, 0, 0)
                        .withBlips(lead, 6, 60, 1, 0.05),
                noisy("healthy, 1,000 requests/min, each fails with p=0.0005", lead + 7 * DAY, 1000, 0.0005, 51),
                noisy("healthy but quiet, 1 request/min, p=0.0005", lead + 30 * DAY, 1, 0.0005, 52));
        Files.writeString(DIR.resolve("rules.yml"), rules());
        Process check = new ProcessBuilder(PROMTOOL, "check", "rules", DIR.resolve("rules.yml").toString()).redirectErrorStream(true).start();
        String checked = new String(check.getInputStream().readAllBytes()).trim();
        check.waitFor();
        System.out.println("rules: " + checked.lines().reduce((a, b) -> b).orElse("") + "  (promtool check rules)");
        System.out.printf("SLO: %.1f%% of checkout requests succeed over %d days; error budget %.1f%%, %d failed requests at 1,000 requests/min%n",
                SLO.objective() * 100, SLO.window().toDays(), SLO.errorBudget() * 100, SLO.allowedBadEvents(1000L * 30 * DAY));
        System.out.println("normal traffic fails 0.02% of requests (burn rate 0.2); every incident starts after 3 days of it, and nothing is measured before then");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        List<Future<Map<String, double[]>>> results = new ArrayList<>();
        for (int i = 0; i < scenarios.size(); i++) {
            results.add(null);
        }
        // longest first, so the two workers finish together
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < scenarios.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> scenarios.get(b).total().length - scenarios.get(a).total().length);
        for (int n : order) {
            results.set(n, pool.submit(() -> evaluate(n, scenarios.get(n))));
        }
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario s = scenarios.get(i);
            Map<String, double[]> r = results.get(i).get();
            double budget = 0.001 * 1000L * 30 * DAY;
            if (s.start() >= 0) {
                double used = 0;
                for (int m = s.start(); m < s.errors().length; m++) {
                    used += s.errors()[m] - s.total()[m] * 0.0002;
                }
                System.out.printf("-- %s: the incident itself spends %.1f%% of the 30-day budget%n", s.name(), used / budget * 100);
                System.out.println("strategy                                   fires after   budget gone by then   still firing after recovery   firings   minutes firing");
            } else {
                double bad = 0;
                double all = 0;
                for (int m = LEAD; m < s.total().length; m++) {
                    bad += s.errors()[m];
                    all += s.total()[m];
                }
                System.out.printf("-- %s: %,.0f of %,.0f requests failed over %d days (%.3f%%, burn rate %.2f): every alert is a false alarm%n",
                        s.name(), bad, all, (s.total().length - LEAD) / DAY, bad / all * 100, bad / all / 0.001);
                System.out.println("strategy                                   firings   minutes firing   first firing (day)");
            }
            for (Strategy st : STRATEGIES) {
                double[] v = r.get(key(st));
                if (s.start() >= 0) {
                    if (v == null) {
                        System.out.printf("%-42s %11s%n", st.label(), "never");
                        continue;
                    }
                    int first = (int) v[0];
                    int last = (int) v[1];
                    double gone = 0;
                    for (int m = s.start(); m < first && m < s.errors().length; m++) {
                        gone += s.errors()[m] - s.total()[m] * 0.0002;
                    }
                    System.out.printf("%-42s %11s   %18.1f%%   %27s   %7d   %14d%n", st.label(), dur(first - s.start()), gone / budget * 100,
                            last >= s.end() ? dur(last - s.end() + 1) : "(stopped before)", (int) v[3], (int) v[2]);
                } else {
                    if (v == null) {
                        System.out.printf("%-42s %7d   %14d   %18s%n", st.label(), 0, 0, "-");
                    } else {
                        System.out.printf("%-42s %7d   %14d   %18.1f%n", st.label(), (int) v[3], (int) v[2], (v[0] - LEAD) / DAY);
                    }
                }
            }
            System.out.println();
        }
        pool.shutdown();
    }

    static String key(Strategy s) {
        return s.alertname() + "/" + (s.severity() == null ? "" : s.severity());
    }

    static String dur(int minutes) {
        if (minutes < 0) {
            return "before";
        }
        if (minutes < 120) {
            return minutes + " min";
        }
        if (minutes < 2 * DAY) {
            return String.format(Locale.ROOT, "%.1f h", minutes / 60.0);
        }
        return String.format(Locale.ROOT, "%.1f d", minutes / (double) DAY);
    }

    /**
     * Runs the scenario through promtool and returns, per alert (alertname/severity): first firing minute, last firing
     * minute, minutes firing, number of separate firings.
     */
    static Map<String, double[]> evaluate(int n, Scenario s) throws IOException, InterruptedException {
        int minutes = s.total().length;
        StringBuilder good = new StringBuilder();
        StringBuilder bad = new StringBuilder();
        double g = 0;
        double b = 0;
        // sample i is the counter at minute i: everything counted in minutes before it
        for (int i = 0; i <= minutes; i++) {
            good.append(num(g)).append(' ');
            bad.append(num(b)).append(' ');
            if (i < minutes) {
                g += s.total()[i] - s.errors()[i];
                b += s.errors()[i];
            }
        }
        String firing = "ALERTS{alertstate=\"firing\"}";
        StringBuilder exprs = new StringBuilder();
        // the same 10,000-sample limit applies to these queries (a subquery holds every step's samples): measure in
        // 12-hour chunks and combine. Only the time after the 3-day lead is measured, when every window is full.
        int chunk = 720;
        for (int from = LEAD, c = 0; from < minutes; from += chunk, c++) {
            int to = Math.min(minutes, from + chunk);
            String range = (to - from) + "m";
            Map<String, String> q = new LinkedHashMap<>();
            // promtool prints three significant digits: every number goes out as minutes, split into thousands and the rest
            q.put("first", "min_over_time(timestamp(" + firing + ")[" + range + ":1m]) / 60");
            q.put("last", "max_over_time(timestamp(" + firing + ")[" + range + ":1m]) / 60");
            q.put("minutes", "count_over_time(" + firing + "[" + range + "])");
            q.put("starts", "sum_over_time((" + firing + " unless " + firing + " offset 1m)[" + range + ":1m])");
            for (Map.Entry<String, String> e : q.entrySet()) {
                for (String part : List.of("k", "r")) {
                    String v = part.equals("k") ? "floor((" + e.getValue() + ") / 1000)" : "round(" + e.getValue() + ") % 1000";
                    exprs.append("      - expr: 'label_replace(").append(v.replace("'", "''")).append(", \"q\", \"")
                            .append(e.getKey()).append('_').append(part).append('_').append(c).append("\", \"\", \"\")'\n");
                    exprs.append("        eval_time: ").append(to).append("m\n");
                }
            }
        }
        Path test = DIR.resolve("scenario-" + n + ".test.yml");
        Files.writeString(test, """
                rule_files: [rules.yml]
                evaluation_interval: 1m
                tests:
                  - interval: 1m
                    input_series:
                      - series: '%s'
                        values: '%s'
                      - series: '%s'
                        values: '%s'
                    promql_expr_test:
                %s""".formatted(SERIES.formatted("SUCCESS"), good.toString().trim(), SERIES.formatted("SERVER_ERROR"),
                bad.toString().trim(), exprs));
        Process p = new ProcessBuilder(PROMTOOL, "test", "rules", test.getFileName().toString()).directory(DIR.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        Files.writeString(DIR.resolve("scenario-" + n + ".out"), out);
        Map<String, double[]> res = new LinkedHashMap<>();
        Map<String, Map<String, double[]>> chunks = new LinkedHashMap<>();
        Pattern sample = Pattern.compile("\\{([^}]*)\\} ([-+0-9.Ee]+)");
        Pattern label = Pattern.compile("(\\w+)=\"([^\"]*)\"");
        for (String line : out.lines().filter(l -> l.trim().startsWith("got:")).toList()) {
            Matcher m = sample.matcher(line);
            while (m.find()) {
                Map<String, String> labels = new LinkedHashMap<>();
                Matcher lm = label.matcher(m.group(1));
                while (lm.find()) {
                    labels.put(lm.group(1), lm.group(2));
                }
                String k = labels.get("alertname") + "/" + labels.getOrDefault("severity", "");
                String[] qk = labels.get("q").split("_");
                Map<String, double[]> byChunk = chunks.computeIfAbsent(k, x -> new LinkedHashMap<>());
                double[] v = byChunk.computeIfAbsent(qk[2], x -> new double[4]);
                v[List.of("first", "last", "minutes", "starts").indexOf(qk[0])] += Double.parseDouble(m.group(2)) * (qk[1].equals("k") ? 1000 : 1);
            }
        }
        chunks.forEach((k, byChunk) -> {
            double[] total = {Double.MAX_VALUE, -1, 0, 0};
            byChunk.values().forEach(v -> {
                total[0] = Math.min(total[0], v[0]);
                total[1] = Math.max(total[1], v[1]);
                total[2] += v[2];
                total[3] += v[3];
            });
            res.put(k, total);
        });
        if (out.contains("err:") || (!out.contains("got:") && !out.contains("SUCCESS"))) {
            throw new IllegalStateException("promtool failed for " + s.name() + ":\n" + out);
        }
        return res;
    }

    static String num(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
