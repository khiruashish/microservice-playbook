package dev.foodfleet.ch11.simulation;

import java.util.Locale;

/** Prints the comparison table used in chapter 11. Run: {@code java ... FailoverReport}. */
public final class FailoverReport {

    public static void main(String[] args) {
        System.out.print(render(new FailoverSimulation.Scenario(100, 4, 200_000, 42)));
    }

    public static String render(FailoverSimulation.Scenario scenario) {
        FailoverSimulation simulation = new FailoverSimulation();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "One of %d instances dies; %.0f req/s spread evenly (%.0f req/s to the dead one)%n%n",
                scenario.instances(), scenario.totalRps(), scenario.rpsToDeadInstance()));
        out.append(String.format(Locale.ROOT, "%-42s %8s %8s %8s %10s %10s%n",
                "Mechanism", "mean s", "p50 s", "p99 s", "failed", "slowed"));
        for (Mechanism m : Mechanisms.all()) {
            FailoverSimulation.Result r = simulation.run(m, scenario);
            out.append(String.format(Locale.ROOT, "%-42s %8.1f %8.1f %8.1f %10.0f %10.0f%n",
                    r.mechanism(), r.meanSeconds(), r.p50Seconds(), r.p99Seconds(), r.failedRequests(), r.slowedRequests()));
        }
        return out.toString();
    }
}
