package dev.foodfleet.ch51.rules;

import dev.foodfleet.ch51.slo.BurnRateAlert;
import dev.foodfleet.ch51.slo.BurnRateAlert.Severity;
import dev.foodfleet.ch51.slo.Slo;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Generates Prometheus recording and alerting rules for one SLO, the way Sloth and Pyrra do: the error ratio and the
 * request rate over every window the alerts need, the budget left over the SLO's window, and a page and a ticket alert
 * built from multi-window, multi-burn-rate conditions. The SLI is given as two PromQL templates with a
 * {@code {{window}}} placeholder: the rate of bad events and the rate of all events.
 *
 * <p>Generated, not hand-written, so every SLO gets the same windows, the same thresholds and the same labels, and
 * changing the objective changes every threshold with it.
 */
public final class PrometheusSloRules {

    /**
     * @param slo                the objective
     * @param errorRate          PromQL for the per-second rate of bad events, with {@code {{window}}}
     * @param totalRate          PromQL for the per-second rate of all events, with {@code {{window}}}
     * @param interval           how often Prometheus evaluates the group
     * @param minEventsLongWindow alerts stay quiet unless the long window saw at least this many events (0: no guard);
     *                           for low-traffic services, where one failure is a large error ratio
     * @param runbook            linked from every alert
     */
    public record Spec(Slo slo, String errorRate, String totalRate, Duration interval, long minEventsLongWindow,
                       String runbook) {
    }

    private PrometheusSloRules() {
    }

    /** One rule file with a group per SLO. */
    public static String generate(Spec... specs) {
        StringBuilder y = new StringBuilder();
        for (Spec spec : specs) {
            y.append(header(spec.slo()));
        }
        y.append("groups:\n");
        for (Spec spec : specs) {
            group(spec, y);
        }
        return y.toString();
    }

    private static String header(Slo slo) {
        return "# " + slo.service() + " / " + slo.name() + ": " + num(slo.objective() * 100) + "% over " + window(slo.window())
                + "; error budget " + num(slo.errorBudget() * 100) + "%, " + num(slo.budgetAsOutage().toSeconds() / 60.0)
                + " minutes of total outage\n";
    }

    private static void group(Spec spec, StringBuilder y) {
        Slo slo = spec.slo();
        List<BurnRateAlert> alerts = BurnRateAlert.defaults(slo);
        TreeSet<Duration> windows = new TreeSet<>();
        for (BurnRateAlert a : alerts) {
            windows.add(a.longWindow());
            windows.add(a.shortWindow());
        }
        String labels = "{slo: %s, service: %s}".formatted(slo.name(), slo.service());
        String sel = "{slo=\"%s\"}".formatted(slo.name());
        y.append("  - name: slo-").append(slo.service()).append("-").append(slo.name()).append("\n");
        y.append("    interval: ").append(window(spec.interval())).append("\n");
        y.append("    rules:\n");
        for (Duration w : windows) {
            String ws = window(w);
            y.append("      - record: slo:sli_error:ratio_rate").append(ws).append("\n");
            y.append("        expr: (").append(spec.errorRate().replace("{{window}}", ws)).append(") / (")
                    .append(spec.totalRate().replace("{{window}}", ws)).append(")\n");
            y.append("        labels: ").append(labels).append("\n");
            y.append("      - record: slo:sli_total:rate").append(ws).append("\n");
            y.append("        expr: ").append(spec.totalRate().replace("{{window}}", ws)).append("\n");
            y.append("        labels: ").append(labels).append("\n");
        }
        y.append("      - record: slo:objective:ratio\n");
        y.append("        expr: vector(").append(num(slo.objective())).append(")\n");
        y.append("        labels: ").append(labels).append("\n");
        for (Severity severity : Severity.values()) {
            List<BurnRateAlert> conditions = alerts.stream().filter(a -> a.severity() == severity).toList();
            String name = camel(slo.name()) + "ErrorBudgetBurn";
            y.append("      - alert: ").append(name).append("\n");
            y.append("        expr: |\n");
            for (int i = 0; i < conditions.size(); i++) {
                BurnRateAlert a = conditions.get(i);
                String threshold = num(a.burnRate()) + " * " + num(slo.errorBudget());
                y.append("          ").append(i == 0 ? "(" : "or (")
                        .append("slo:sli_error:ratio_rate").append(window(a.longWindow())).append(sel).append(" > ").append(threshold)
                        .append(" and slo:sli_error:ratio_rate").append(window(a.shortWindow())).append(sel).append(" > ").append(threshold);
                if (spec.minEventsLongWindow() > 0) {
                    y.append(" and slo:sli_total:rate").append(window(a.longWindow())).append(sel).append(" * ")
                            .append(a.longWindow().toSeconds()).append(" >= ").append(spec.minEventsLongWindow());
                }
                y.append(")\n");
            }
            y.append("        labels:\n");
            y.append("          severity: ").append(severity.name().toLowerCase(Locale.ROOT)).append("\n");
            y.append("          slo: ").append(slo.name()).append("\n");
            y.append("          service: ").append(slo.service()).append("\n");
            y.append("        annotations:\n");
            y.append("          summary: \"").append(slo.service()).append(" ").append(slo.name()).append(" is spending its error budget ")
                    .append(severity == Severity.PAGE ? "fast" : "steadily").append("\"\n");
            StringBuilder desc = new StringBuilder();
            for (BurnRateAlert a : conditions) {
                desc.append(desc.isEmpty() ? "" : "; or ").append(num(a.budgetSpentAtThreshold(slo) * 100)).append("% of the ")
                        .append(window(slo.window())).append(" budget in ").append(window(a.longWindow())).append(" (burn rate ")
                        .append(num(a.burnRate())).append(")");
            }
            y.append("          description: \"Fires on ").append(desc).append(". Error ratio over the long window: {{ $value | humanizePercentage }}.\"\n");
            y.append("          runbook_url: ").append(spec.runbook()).append("\n");
        }
        // the budget over the whole window reads 30 days of samples: its own group, evaluated less often
        y.append("  - name: slo-").append(slo.service()).append("-").append(slo.name()).append("-budget\n");
        y.append("    interval: 5m\n");
        y.append("    rules:\n");
        String sw = window(slo.window());
        y.append("      - record: slo:error_budget:remaining_ratio").append("\n");
        y.append("        expr: 1 - ((").append(spec.errorRate().replace("{{window}}", sw)).append(") / (")
                .append(spec.totalRate().replace("{{window}}", sw)).append(")) / ").append(num(slo.errorBudget())).append("\n");
        y.append("        labels: ").append(labels).append("\n");
    }

    /** Prometheus duration syntax: the largest unit that divides evenly. */
    static String window(Duration d) {
        long s = d.toSeconds();
        if (s % 86400 == 0) {
            return s / 86400 + "d";
        }
        if (s % 3600 == 0) {
            return s / 3600 + "h";
        }
        if (s % 60 == 0) {
            return s / 60 + "m";
        }
        return s + "s";
    }

    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String camel(String kebab) {
        StringBuilder b = new StringBuilder();
        for (String part : kebab.split("[-_]")) {
            if (!part.isEmpty()) {
                b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return b.toString();
    }
}
