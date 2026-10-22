package dev.foodfleet.ch35.threatmodel;

import dev.foodfleet.ch35.threatmodel.ThreatModel.Status;
import dev.foodfleet.ch35.threatmodel.ThreatModel.Threat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Summaries of a threat model for reviews and the build log. */
public final class ThreatReport {

    private ThreatReport() {
    }

    public static Map<Stride, Map<Status, Integer>> byCategory(ThreatModel model) {
        Map<Stride, Map<Status, Integer>> out = new EnumMap<>(Stride.class);
        for (Stride s : Stride.values()) {
            out.put(s, new EnumMap<>(Status.class));
            for (Status st : Status.values()) {
                out.get(s).put(st, 0);
            }
        }
        for (Threat t : model.threats()) {
            out.get(t.category()).merge(t.status(), 1, Integer::sum);
        }
        return out;
    }

    public static String text(ThreatModel model) {
        StringBuilder sb = new StringBuilder();
        List<Threat> threats = model.threats();
        sb.append(String.format("%s: %d elements, %d threats%n%n", model.name(), model.elements().size(), threats.size()));
        sb.append(String.format("%-24s%11s%10s%7s%n", "STRIDE category", "mitigated", "accepted", "open"));
        byCategory(model).forEach((s, m) -> sb.append(String.format("%-24s%11d%10d%7d%n", s,
                m.get(Status.MITIGATED), m.get(Status.ACCEPTED), m.get(Status.OPEN))));
        for (Threat t : threats) {
            if (t.status() == Status.OPEN) {
                sb.append(String.format("OPEN  %-22s %s%n", t.element().id(), t.category()));
            }
        }
        return sb.toString();
    }
}
