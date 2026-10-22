package dev.foodfleet.ch35.threatmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A threat model kept as code, next to the system it describes, reviewed in pull requests and checked in the
 * build: every element of the data-flow diagram gets the STRIDE threats that apply to its type, and every threat
 * needs a mitigation (a control and where it is implemented or tested) or a recorded acceptance. Adding a flow
 * or a store without thinking about it fails the build, which is the point.
 */
public final class ThreatModel {

    public record Element(String id, ElementType type, String description, String trustZone) {
    }

    public record Mitigation(String control, String reference) {
    }

    public record Acceptance(String reason, String owner, String reviewBy) {
    }

    public enum Status { MITIGATED, ACCEPTED, OPEN }

    public record Threat(Element element, Stride category, Status status, Mitigation mitigation, Acceptance acceptance) {
    }

    private final String name;
    private final Map<String, Element> elements = new LinkedHashMap<>();
    private final Map<String, Mitigation> mitigations = new LinkedHashMap<>();
    private final Map<String, Acceptance> acceptances = new LinkedHashMap<>();

    public ThreatModel(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public ThreatModel element(String id, ElementType type, String trustZone, String description) {
        if (elements.putIfAbsent(id, new Element(id, type, description, trustZone)) != null) {
            throw new IllegalArgumentException("duplicate element " + id);
        }
        return this;
    }

    /** A data flow between two elements; only flows that cross a trust boundary are modelled. */
    public ThreatModel flow(String id, String from, String to, String description) {
        Element a = require(from);
        Element b = require(to);
        if (a.trustZone().equals(b.trustZone())) {
            throw new IllegalArgumentException(id + " does not cross a trust boundary");
        }
        return element(id, ElementType.DATA_FLOW, a.trustZone() + " -> " + b.trustZone(), description);
    }

    public ThreatModel mitigate(Stride category, String control, String reference, String... elementIds) {
        for (String id : elementIds) {
            Element e = require(id);
            if (!e.type().applicable().contains(category)) {
                throw new IllegalArgumentException(category + " does not apply to " + id + " (" + e.type() + ")");
            }
            mitigations.put(key(id, category), new Mitigation(control, reference));
        }
        return this;
    }

    public ThreatModel accept(Stride category, String reason, String owner, String reviewBy, String... elementIds) {
        for (String id : elementIds) {
            require(id);
            acceptances.put(key(id, category), new Acceptance(reason, owner, reviewBy));
        }
        return this;
    }

    public List<Threat> threats() {
        List<Threat> out = new ArrayList<>();
        for (Element e : elements.values()) {
            for (Stride s : e.type().applicable()) {
                Mitigation m = mitigations.get(key(e.id(), s));
                Acceptance a = acceptances.get(key(e.id(), s));
                Status status = m != null ? Status.MITIGATED : a != null ? Status.ACCEPTED : Status.OPEN;
                out.add(new Threat(e, s, status, m, a));
            }
        }
        return out;
    }

    public List<Threat> open() {
        return threats().stream().filter(t -> t.status() == Status.OPEN).toList();
    }

    public Optional<Element> find(String id) {
        return Optional.ofNullable(elements.get(id));
    }

    public List<Element> elements() {
        return List.copyOf(elements.values());
    }

    private Element require(String id) {
        Element e = elements.get(id);
        if (e == null) {
            throw new IllegalArgumentException("unknown element " + id);
        }
        return e;
    }

    private static String key(String id, Stride s) {
        return id + "#" + s;
    }
}
