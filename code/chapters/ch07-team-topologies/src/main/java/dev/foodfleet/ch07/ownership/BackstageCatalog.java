package dev.foodfleet.ch07.ownership;

import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Component;
import dev.foodfleet.ch07.topology.Topology.Dependency;
import dev.foodfleet.ch07.topology.Topology.Team;
import java.util.List;
import java.util.Locale;

/**
 * Writes a Backstage software catalog (Group and Component entities, {@code backstage.io/v1alpha1})
 * from the topology, so the developer portal shows the same owners and dependencies the analyses
 * use. One source of truth, three outputs: the report, CODEOWNERS, and the catalog.
 */
public final class BackstageCatalog {

    private final String system;

    public BackstageCatalog(String system) {
        this.system = system;
    }

    public static String entityName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    public String write(Topology topology) {
        StringBuilder out = new StringBuilder();
        for (Team team : topology.teams()) {
            out.append("---\n")
                    .append("apiVersion: backstage.io/v1alpha1\n")
                    .append("kind: Group\n")
                    .append("metadata:\n")
                    .append("  name: ").append(entityName(team.name())).append('\n')
                    .append("  title: ").append(team.name()).append('\n')
                    .append("  annotations:\n")
                    .append("    foodfleet.dev/team-type: ").append(team.type().name().toLowerCase(Locale.ROOT).replace('_', '-')).append('\n')
                    .append("spec:\n")
                    .append("  type: team\n")
                    .append("  children: []\n");
        }
        for (Component c : topology.components()) {
            List<String> dependsOn = topology.dependencies().stream()
                    .filter(d -> d.from().equals(c.name()))
                    .map(Dependency::to)
                    .distinct()
                    .sorted()
                    .toList();
            out.append("---\n")
                    .append("apiVersion: backstage.io/v1alpha1\n")
                    .append("kind: Component\n")
                    .append("metadata:\n")
                    .append("  name: ").append(c.name()).append('\n')
                    .append("  annotations:\n")
                    .append("    backstage.io/source-location: url:./").append(c.path()).append('\n')
                    .append("spec:\n")
                    .append("  type: ").append(c.path().startsWith("apps/") ? "website" : "service").append('\n')
                    .append("  lifecycle: production\n")
                    .append("  owner: group:").append(entityName(c.owner())).append('\n')
                    .append("  system: ").append(system).append('\n');
            if (!dependsOn.isEmpty()) {
                out.append("  dependsOn:\n");
                dependsOn.forEach(d -> out.append("    - component:").append(d).append('\n'));
            }
        }
        return out.toString();
    }
}
