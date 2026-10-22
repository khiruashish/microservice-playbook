package dev.foodfleet.ch38.authz;

import dev.foodfleet.ch38.spiffe.SpiffeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which workloads may call which routes of this service, by SPIFFE ID: the service-level half of authorization
 * (the user-level half is the token). Deny by default: a route no rule names is closed to every caller.
 * Paths are matched on the raw request URI segment by segment ({@code *} matches exactly one segment); a raw path
 * that contains percent-encoding, a semicolon, an empty, {@code .} or {@code ..} segment is denied outright, so a
 * path that would be decoded or normalised differently by another layer never reaches a rule.
 */
public final class PeerPolicy {

    public record Rule(String name, Set<SpiffeId> callers, String method, String pathPattern) {
    }

    public record Decision(boolean allowed, String reason) {
        static Decision allow(String rule) {
            return new Decision(true, "allowed by " + rule);
        }

        static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    private final List<Rule> rules;

    private PeerPolicy(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Rule> rules() {
        return rules;
    }

    public Decision decide(SpiffeId caller, String method, String rawPath) {
        String[] path = segments(rawPath);
        if (path == null) {
            return Decision.deny("ambiguous path " + rawPath);
        }
        boolean routeKnown = false;
        for (Rule rule : rules) {
            if (rule.method().equals(method.toUpperCase(Locale.ROOT)) && matches(segments(rule.pathPattern()), path)) {
                routeKnown = true;
                if (rule.callers().contains(caller)) {
                    return Decision.allow(rule.name());
                }
            }
        }
        return Decision.deny(routeKnown ? caller + " may not call " + method + " " + rawPath
                : "no rule for " + method + " " + rawPath);
    }

    private static String[] segments(String path) {
        if (path == null || !path.startsWith("/") || path.contains("%") || path.contains(";")
                || path.contains("\\")) {
            return null;
        }
        String[] parts = path.substring(1).split("/", -1);
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".") || p.equals("..")) {
                return path.equals("/") ? new String[0] : null;
            }
        }
        return parts;
    }

    private static boolean matches(String[] pattern, String[] path) {
        if (pattern.length != path.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (!pattern[i].equals("*") && !pattern[i].equals(path[i])) {
                return false;
            }
        }
        return true;
    }

    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();

        public Builder allow(String name, String method, String pathPattern, SpiffeId... callers) {
            if (segments(pathPattern) == null) {
                throw new IllegalArgumentException("invalid path pattern " + pathPattern);
            }
            rules.add(new Rule(name, Set.of(callers), method.toUpperCase(Locale.ROOT), pathPattern));
            return this;
        }

        public PeerPolicy build() {
            return new PeerPolicy(rules);
        }
    }
}
