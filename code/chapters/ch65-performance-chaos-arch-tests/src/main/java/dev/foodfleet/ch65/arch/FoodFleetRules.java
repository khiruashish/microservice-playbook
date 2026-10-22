package dev.foodfleet.ch65.arch;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** FoodFleet's fitness functions for a service laid out as api, application, domain, infrastructure and config. */
public final class FoodFleetRules {

    public static final String ROOT = "dev.foodfleet.ch65.sample";

    private FoodFleetRules() {
    }

    public static List<Rules.Rule> all() {
        return List.of(
                Rules.layers(Map.of("api", ROOT + ".api", "application", ROOT + ".application", "domain", ROOT + ".domain",
                                "infrastructure", ROOT + ".infrastructure", "config", ROOT + ".config"),
                        Map.of("api", Set.of("application", "domain"),
                                "application", Set.of("domain"),
                                "infrastructure", Set.of("application", "domain"),
                                "config", Set.of("api", "application", "domain", "infrastructure"))),
                Rules.noCycles(ROOT, Set.of("domain")),
                Rules.onlyDependOn("domain free of I/O and frameworks", ROOT + ".domain",
                        Set.of(ROOT + ".domain", "java.lang", "java.math", "java.util", "java.time")),
                Rules.naming(ROOT + ".api", Set.of("Controller", "Request", "Response")),
                Rules.noStandardStreams());
    }
}
