package dev.foodfleet.platform;

import dev.foodfleet.ch51.rules.CheckoutSlos;
import dev.foodfleet.ch51.rules.PrometheusSloRules;
import dev.foodfleet.ch51.rules.PrometheusSloRules.Spec;
import dev.foodfleet.ch51.slo.Slo;

import java.time.Duration;

/**
 * All of ADR-051's journey SLOs as Prometheus rules, generated with chapter 51's {@link PrometheusSloRules}. Checkout
 * reuses chapter 51's specs unchanged. Browse and tracking are measured where this page chose to measure them (no
 * chapter fixed their SLIs): browse at menu-service's menu endpoint, tracking at tracking-gateway's stream endpoint.
 *
 * <pre>run.sh</pre> compiles chapter 51's classes and this one with a bare JDK and writes foodfleet-slo.rules.yml.
 */
public final class FoodFleetSlos {

    private FoodFleetSlos() {
    }

    static final Duration THIRTY_DAYS = Duration.ofDays(30);
    static final String RUNBOOKS = "https://runbooks.foodfleet.internal/";

    static Spec availability(String service, String name, double objective, String selector) {
        return new Spec(new Slo(service, name, objective, THIRTY_DAYS),
                "sum(rate(http_server_requests_seconds_count{" + selector + ", outcome=\"SERVER_ERROR\"}[{{window}}])) or vector(0)",
                "sum(rate(http_server_requests_seconds_count{" + selector + "}[{{window}}]))",
                Duration.ofSeconds(30), 100, RUNBOOKS + service + "/" + name);
    }

    static final Spec BROWSE = availability("menu-service", "browse-availability", 0.999,
            "job=\"menu-service\", uri=\"/restaurants/{id}/menu\", method=\"GET\"");

    static final Spec TRACKING = availability("tracking-gateway", "tracking-availability", 0.995,
            "job=\"tracking-gateway\", uri=\"/orders/{id}/tracking\", method=\"GET\"");

    public static void main(String[] args) {
        Spec[] all = {BROWSE, CheckoutSlos.AVAILABILITY, CheckoutSlos.LATENCY, TRACKING};
        if (args.length > 0 && args[0].equals("--budgets")) {
            System.out.println("| SLO | Objective | Error budget | As total outage, per 30 days |");
            System.out.println("|---|---|---|---|");
            for (Spec s : all) {
                Slo slo = s.slo();
                long sec = slo.budgetAsOutage().toSeconds();
                System.out.printf("| `%s` / %s | %s%% | %s%% | %d min %02d s |%n", slo.service(), slo.name(),
                        trim(slo.objective() * 100), trim(slo.errorBudget() * 100), sec / 60, sec % 60);
            }
            return;
        }
        System.out.print(PrometheusSloRules.generate(all));
    }

    static String trim(double v) {
        String s = String.format("%.3f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
