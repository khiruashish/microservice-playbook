package dev.foodfleet.ch73.fitness;

import dev.foodfleet.ch73.fitness.FitnessFunction.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static dev.foodfleet.ch73.fitness.FitnessFunction.Kind.DYNAMIC;
import static dev.foodfleet.ch73.fitness.FitnessFunction.Kind.STATIC;

/** order-service's fitness functions: each one a decision from the log, made executable. */
public final class Functions {

    public static final String BASE = "dev.foodfleet.orders.";
    public static final double STARTUP_BUDGET_MS = 1_000;
    public static final int RATE = 200;
    public static final double P99_BUDGET_MS = 20;

    private Functions() {
    }

    public static List<FitnessFunction> suite(Radar radar) {
        return List.of(
                new FitnessFunction("F1 domain-is-independent", "ADR-009", STATIC,
                        "domain classes depend only on the domain, java.lang, java.util and java.time", (r, p) -> {
                    List<String> bad = new ArrayList<>();
                    r.bytecode().dependencies.forEach((cls, deps) -> {
                        if (cls.startsWith(BASE + "domain.")) {
                            for (String d : deps) {
                                if (!(d.startsWith(BASE + "domain.") || d.startsWith("java.lang.") || d.startsWith("java.util.") || d.startsWith("java.time."))) {
                                    bad.add(cls.substring(BASE.length()) + " -> " + shorten(d));
                                }
                            }
                        }
                    });
                    return new Result(bad.isEmpty(), bad.isEmpty() ? "" : String.join(", ", bad));
                }),
                new FitnessFunction("F2 radar-hold", "ADR-073", STATIC,
                        "no class depends on a technology the radar puts on hold", (r, p) -> {
                    Set<String> bad = new TreeSet<>();
                    r.bytecode().dependencies.forEach((cls, deps) -> {
                        for (String d : deps) {
                            Radar.Blip b = radar.heldBy(d);
                            if (b != null) {
                                bad.add(cls.substring(BASE.length()) + " -> " + d + " (" + b.name() + ")");
                            }
                        }
                    });
                    return new Result(bad.isEmpty(), String.join(", ", bad));
                }),
                new FitnessFunction("F3 api-compatible", "ADR-027", STATIC,
                        "no public member of the published API (package api) removed or changed since the last release that shipped", (r, p) -> {
                    if (r.previousBytecode() == null) {
                        return new Result(true, "first release");
                    }
                    List<String> removed = new ArrayList<>();
                    r.previousBytecode().api.forEach((cls, members) -> {
                        if (!cls.startsWith(BASE + "api.")) {
                            return;
                        }
                        Set<String> now = r.bytecode().api.get(cls);
                        if (now == null) {
                            removed.add(shorten(cls) + " (class)");
                            return;
                        }
                        for (String m : members) {
                            if (!now.contains(m)) {
                                removed.add(shorten(cls) + "." + m);
                            }
                        }
                    });
                    return new Result(removed.isEmpty(), removed.isEmpty() ? "" : "removed: " + String.join(", ", removed));
                }),
                new FitnessFunction("F4 startup", "ADR-060", DYNAMIC,
                        "healthy within " + (int) STARTUP_BUDGET_MS + " ms of launch", (r, p) ->
                        new Result(p.startupMillis() <= STARTUP_BUDGET_MS, String.format("%.0f ms", p.startupMillis()))),
                new FitnessFunction("F5 latency", "ADR-066", DYNAMIC,
                        "GET /orders/{id}: p99 at most " + (int) P99_BUDGET_MS + " ms at " + RATE + " requests/s", (r, p) -> {
                    p.load(RATE, 2, i -> "/orders/o-" + (i * 7 % 1_000));
                    Probe.Latencies l = p.load(RATE, 5, i -> "/orders/o-" + (i * 13 % 1_000));
                    double p99 = l.percentile(99);
                    return new Result(p99 <= P99_BUDGET_MS && l.errors() == 0, String.format("p50 %.1f ms, p99 %.1f ms, errors %d", l.percentile(50), p99, l.errors()));
                }));
    }

    static String shorten(String cls) {
        return cls.startsWith(BASE) ? cls.substring(BASE.length()) : cls;
    }
}
