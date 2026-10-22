package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.detect.Finding.AntiPattern;
import dev.foodfleet.ch20.detect.Finding.Severity;
import dev.foodfleet.ch20.model.SystemModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nano-services: services so small that the network, deployment and operational cost outweighs
 * what they do. Small alone isn't a smell; small AND only ever used by (or released with) one other
 * service is: that is a module of its neighbour, paying distribution costs.
 */
public final class NanoServiceDetector implements Detector {

    private final int maxEndpoints;
    private final int maxLoc;

    public NanoServiceDetector(int maxEndpoints, int maxLoc) {
        this.maxEndpoints = maxEndpoints;
        this.maxLoc = maxLoc;
    }

    @Override
    public List<Finding> detect(SystemModel model) {
        List<Finding> findings = new ArrayList<>();
        for (SystemModel.Service s : model.services()) {
            if (s.endpoints() > maxEndpoints || s.loc() > maxLoc) {
                continue;
            }
            List<String> callers = model.calls().stream().filter(c -> c.to().equals(s.name()))
                    .map(SystemModel.Call::from).distinct().sorted().toList();
            String partner = closestDeployPartner(model, s.name());
            boolean singleCaller = callers.size() == 1;
            if (!singleCaller && partner == null) {
                continue;
            }
            String home = singleCaller ? callers.getFirst() : partner;
            findings.add(new Finding(AntiPattern.NANO_SERVICE, Severity.LOW, s.name(),
                    "%d endpoints, %,d lines; %s".formatted(s.endpoints(), s.loc(),
                            singleCaller ? "only called by " + home : "released with " + home + " most of the time"),
                    "merge into " + home + " as a module (chapter 1); split again only for a real reason"));
        }
        return findings;
    }

    /** The service deployed with this one in at least 70% of its releases, if any. */
    private static String closestDeployPartner(SystemModel model, String name) {
        List<SystemModel.Deploy> mine = model.deploys().stream().filter(d -> d.services().contains(name)).toList();
        if (mine.size() < 3) {
            return null;
        }
        Map<String, Long> partners = mine.stream().flatMap(d -> d.services().stream())
                .filter(n -> !n.equals(name)).collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        return partners.entrySet().stream().max(Comparator.comparingLong(Map.Entry::getValue))
                .filter(e -> e.getValue() >= 0.7 * mine.size()).map(Map.Entry::getKey).orElse(null);
    }
}
