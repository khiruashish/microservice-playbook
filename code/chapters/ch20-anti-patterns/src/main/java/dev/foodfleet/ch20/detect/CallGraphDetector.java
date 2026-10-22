package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.detect.Finding.AntiPattern;
import dev.foodfleet.ch20.detect.Finding.Severity;
import dev.foodfleet.ch20.model.SystemModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Smells in the synchronous call graph:
 * <ul>
 *   <li><b>chatty</b>: one service calling another many times per user request (N+1);</li>
 *   <li><b>fan-out</b>: one request needing synchronous answers from many services;</li>
 *   <li><b>deep chains</b>: A calls B calls C calls D..., each hop multiplying latency and failure;</li>
 *   <li><b>cycles</b>: A needs B needs A: neither can be deployed, scaled or reasoned about alone.</li>
 * </ul>
 * Asynchronous edges (events) are ignored here: they don't block the caller.
 */
public final class CallGraphDetector implements Detector {

    private final double chattyCallsPerRequest;
    private final int maxFanOut;
    private final int maxDepth;
    private final double serviceAvailability;

    public CallGraphDetector(double chattyCallsPerRequest, int maxFanOut, int maxDepth, double serviceAvailability) {
        this.chattyCallsPerRequest = chattyCallsPerRequest;
        this.maxFanOut = maxFanOut;
        this.maxDepth = maxDepth;
        this.serviceAvailability = serviceAvailability;
    }

    @Override
    public List<Finding> detect(SystemModel model) {
        Map<String, List<SystemModel.Call>> out = new TreeMap<>();
        for (SystemModel.Call c : model.calls()) {
            if (c.sync()) {
                out.computeIfAbsent(c.from(), k -> new ArrayList<>()).add(c);
            }
        }
        List<Finding> findings = new ArrayList<>();
        out.forEach((from, calls) -> {
            for (SystemModel.Call c : calls) {
                if (c.perRequest() >= chattyCallsPerRequest) {
                    findings.add(new Finding(AntiPattern.CHATTY, Severity.MEDIUM, from + "->" + c.to(),
                            "%.0f synchronous calls per request".formatted(c.perRequest()),
                            "batch endpoint (GET /items?ids=...), or keep a local copy via events (chapter 19)"));
                }
            }
            if (calls.size() >= maxFanOut) {
                findings.add(new Finding(AntiPattern.FAN_OUT, Severity.MEDIUM, from,
                        "%d synchronous dependencies per request; all up at once: %.2f%%".formatted(calls.size(),
                                100 * Math.pow(serviceAvailability, calls.size())),
                        "compose in parallel with timeouts and fallbacks (chapter 18), or replicate data"));
            }
        });
        for (String start : out.keySet()) {
            List<String> longest = longestPath(start, out, new LinkedHashSet<>());
            if (longest.size() - 1 >= maxDepth) {
                findings.add(new Finding(AntiPattern.SYNC_CHAIN, Severity.MEDIUM, String.join("->", longest),
                        "%d synchronous hops; at %.2f%% each the chain is up %.2f%% of the time".formatted(
                                longest.size() - 1, serviceAvailability * 100,
                                100 * Math.pow(serviceAvailability, longest.size())),
                        "shorten: replicate data, or make downstream steps asynchronous"));
            }
        }
        for (List<String> cycle : cycles(out)) {
            findings.add(new Finding(AntiPattern.CYCLIC_DEPENDENCY, Severity.HIGH, String.join("->", cycle),
                    "synchronous calls form a cycle of %d services".formatted(cycle.size() - 1),
                    "move the shared responsibility into one service, or replace one direction with events"));
        }
        // A chain finding for every start would repeat itself: keep only the longest per end-to-end path.
        return dedupeChains(findings);
    }

    private static List<String> longestPath(String node, Map<String, List<SystemModel.Call>> out, Set<String> onPath) {
        onPath.add(node);
        List<String> best = new ArrayList<>(List.of(node));
        for (SystemModel.Call c : out.getOrDefault(node, List.of())) {
            if (onPath.contains(c.to())) {
                continue;                                         // cycles are reported separately
            }
            List<String> candidate = new ArrayList<>(List.of(node));
            candidate.addAll(longestPath(c.to(), out, onPath));
            if (candidate.size() > best.size()) {
                best = candidate;
            }
        }
        onPath.remove(node);
        return best;
    }

    private static List<List<String>> cycles(Map<String, List<SystemModel.Call>> out) {
        List<List<String>> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String start : out.keySet()) {
            dfs(start, start, out, new ArrayList<>(List.of(start)), found, seen);
        }
        return found;
    }

    private static void dfs(String start, String node, Map<String, List<SystemModel.Call>> out, List<String> path,
                            List<List<String>> found, Set<String> seen) {
        for (SystemModel.Call c : out.getOrDefault(node, List.of())) {
            if (c.to().equals(start)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(start);
                String canonical = new java.util.TreeSet<>(path).toString();
                if (seen.add(canonical)) {
                    found.add(cycle);
                }
            } else if (!path.contains(c.to()) && c.to().compareTo(start) > 0) {
                path.add(c.to());
                dfs(start, c.to(), out, path, found, seen);
                path.removeLast();
            }
        }
    }

    private static List<Finding> dedupeChains(List<Finding> findings) {
        List<Finding> chains = findings.stream().filter(f -> f.pattern() == AntiPattern.SYNC_CHAIN).toList();
        List<Finding> result = new ArrayList<>(findings.stream().filter(f -> f.pattern() != AntiPattern.SYNC_CHAIN).toList());
        for (Finding chain : chains) {
            boolean contained = chains.stream().anyMatch(other -> other != chain
                    && other.subject().length() > chain.subject().length() && other.subject().endsWith(chain.subject()));
            if (!contained) {
                result.add(chain);
            }
        }
        return result;
    }
}
