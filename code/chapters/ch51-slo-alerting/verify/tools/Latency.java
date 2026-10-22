import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A latency SLI ("99% of checkout requests within 300 ms") read from Micrometer's Prometheus histograms, compared with
 * the truth: each distribution is recorded into three Micrometer 1.17 timers configured the ways Spring Boot can
 * configure {@code http.server.requests}, scraped, and queried by Prometheus 3.15.0's engine.
 */
final class Latency {

    private Latency() {
    }

    record Dist(String name, DoubleSupplier millis) {
    }

    record Variant(String name, String key, boolean percentileHistogram, boolean slo) {
    }

    static final List<Variant> VARIANTS = List.of(
            new Variant("percentiles-histogram only (Micrometer's default buckets)", "ph", true, false),
            new Variant("percentiles-histogram + slo 300ms", "phslo", true, true),
            new Variant("slo 300ms only (one bucket and +Inf)", "slo", false, true));

    static void run() throws Exception {
        int n = 500_000;
        Random r = new Random(510);
        List<Dist> dists = List.of(
                new Dist("healthy: log-normal, median 110 ms", () -> 110 * Math.exp(0.399 * r.nextGaussian())),
                new Dist("slow tail: 3% of requests take 0.8-2 s", () -> r.nextDouble() < 0.03 ? 800 + 1200 * r.nextDouble()
                        : 110 * Math.exp(0.3 * r.nextGaussian())),
                new Dist("regression: median 180 ms", () -> 180 * Math.exp(0.4 * r.nextGaussian())),
                new Dist("everything between 290 and 320 ms", () -> 290 + 30 * r.nextDouble()));
        System.out.printf("%,d requests per distribution, recorded into Micrometer timers (Prometheus registry, seconds) and queried with Prometheus%n", n);
        System.out.println("SLI: share of requests within 300 ms (objective 99%)");
        StringBuilder series = new StringBuilder();
        StringBuilder exprs = new StringBuilder();
        Map<String, double[]> truth = new LinkedHashMap<>();
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        PrometheusMeterRegistry reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        for (int d = 0; d < dists.size(); d++) {
            Dist dist = dists.get(d);
            List<Timer> timers = new ArrayList<>();
            for (Variant v : VARIANTS) {
                Timer.Builder b = Timer.builder("http.server.requests").tag("dist", "d" + d).tag("variant", v.key());
                if (v.percentileHistogram()) {
                    b.publishPercentileHistogram();
                }
                if (v.slo()) {
                    b.serviceLevelObjectives(Duration.ofMillis(300));
                }
                timers.add(b.register(reg));
            }
            double[] samples = new double[n];
            for (int i = 0; i < n; i++) {
                samples[i] = dist.millis().getAsDouble();
                long nanos = Math.round(samples[i] * 1_000_000);
                for (Timer t : timers) {
                    t.record(nanos, TimeUnit.NANOSECONDS);
                }
            }
            Arrays.sort(samples);
            long within = 0;
            for (double s : samples) {
                if (s <= 300) {
                    within++;
                }
            }
            truth.put("d" + d, new double[] {(double) within / n, samples[(int) Math.ceil(0.99 * n) - 1], samples[(int) Math.ceil(0.95 * n) - 1]});
        }
        // the scrape, as Prometheus would see it, becomes the test's input: 0 at minute 0, the counts at minute 1
        Pattern line = Pattern.compile("^(http_server_requests_seconds_(?:bucket|count))\\{(.*)\\} (\\S+)$");
        for (String l : reg.scrape().split("\n")) {
            Matcher m = line.matcher(l);
            if (m.matches()) {
                series.append("      - series: '").append(m.group(1)).append('{').append(m.group(2)).append("}'\n");
                series.append("        values: '0 ").append(m.group(3)).append("'\n");
                if (m.group(1).endsWith("bucket")) {
                    String key = m.group(2).replaceAll(".*variant=\"([^\"]+)\".*", "$1");
                    String dist = m.group(2).replaceAll(".*dist=\"([^\"]+)\".*", "$1");
                    if (dist.equals("d0")) {
                        bucketCounts.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("exact", "sum by (dist, variant) (rate(http_server_requests_seconds_bucket{le=\"0.3\"}[2m])) / sum by (dist, variant) (rate(http_server_requests_seconds_count[2m]))");
        q.put("fraction", "histogram_fraction(0, 0.3, sum by (dist, variant, le) (rate(http_server_requests_seconds_bucket[2m])))");
        q.put("p99", "histogram_quantile(0.99, sum by (dist, variant, le) (rate(http_server_requests_seconds_bucket[2m])))");
        q.put("p95", "histogram_quantile(0.95, sum by (dist, variant, le) (rate(http_server_requests_seconds_bucket[2m])))");
        for (Map.Entry<String, String> e : q.entrySet()) {
            exprs.append("      - expr: 'label_replace(").append(e.getValue()).append(", \"q\", \"").append(e.getKey()).append("\", \"\", \"\")'\n");
            exprs.append("        eval_time: 1m\n");
        }
        Path test = Measure.DIR.resolve("latency.test.yml");
        Files.writeString(test, "tests:\n  - interval: 1m\n    input_series:\n" + series + "    promql_expr_test:\n" + exprs);
        Process p = new ProcessBuilder(Measure.PROMTOOL, "test", "rules", test.getFileName().toString()).directory(Measure.DIR.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        Files.writeString(Measure.DIR.resolve("latency.out"), out);
        Map<String, Double> got = new LinkedHashMap<>();
        Pattern sample = Pattern.compile("\\{([^}]*)\\} ([-+0-9.EeInfNa]+)");
        for (String l : out.lines().filter(x -> x.trim().startsWith("got:")).toList()) {
            Matcher m = sample.matcher(l);
            while (m.find()) {
                String labels = m.group(1);
                String k = labels.replaceAll(".*dist=\"([^\"]+)\".*", "$1") + "/" + labels.replaceAll(".*variant=\"([^\"]+)\".*", "$1") + "/"
                        + labels.replaceAll(".*q=\"([^\"]+)\".*", "$1");
                got.put(k, Double.parseDouble(m.group(2).replace("+Inf", "Infinity")));
            }
        }
        System.out.print("buckets per timer (with +Inf):");
        VARIANTS.forEach(v -> System.out.print("  " + v.key() + " " + bucketCounts.get(v.key())));
        System.out.println();
        for (int d = 0; d < dists.size(); d++) {
            double[] t = truth.get("d" + d);
            System.out.printf("-- %s: truly within 300 ms %.2f%% (%s the objective); true p95 %.0f ms, p99 %.0f ms%n", dists.get(d).name(),
                    t[0] * 100, t[0] >= 0.99 ? "meets" : "misses", t[2], t[1]);
            System.out.println("timer                                                       le=\"0.3\" bucket   histogram_fraction(0, 0.3)   histogram_quantile p95   p99");
            for (Variant v : VARIANTS) {
                String k = "d" + d + "/" + v.key() + "/";
                Double exact = got.get(k + "exact");
                Double frac = got.get(k + "fraction");
                System.out.printf("%-58s %17s   %26s   %19s   %9s%n", v.name(), exact == null ? "no such bucket" : pct(exact),
                        frac == null ? "-" : pct(frac), ms(got.get(k + "p95")), ms(got.get(k + "p99")));
            }
        }
        System.out.println();
    }

    static String pct(double v) {
        return String.format("%.2f%%", v * 100);
    }

    static String ms(Double v) {
        if (v == null || v.isNaN()) {
            return "-";
        }
        if (v.isInfinite()) {
            return "+Inf";
        }
        return String.format("%.0f ms", v * 1000);
    }
}
