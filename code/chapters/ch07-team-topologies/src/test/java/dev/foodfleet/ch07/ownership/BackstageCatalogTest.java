package dev.foodfleet.ch07.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch07.topology.FoodFleetTeams;
import org.junit.jupiter.api.Test;

class BackstageCatalogTest {

    @Test
    void writesOneGroupPerTeamAndOneComponentPerDeployable() {
        String yaml = new BackstageCatalog("foodfleet").write(FoodFleetTeams.after());

        assertThat(yaml.split("\n---\n|^---\n")).hasSizeGreaterThan(1);
        assertThat(yaml).containsSubsequence(
                "kind: Group", "name: restaurant-partner", "foodfleet.dev/team-type: stream-aligned", "type: team");
        assertThat(yaml).containsSubsequence(
                "kind: Component", "name: ordering", "type: service", "owner: group:checkout", "system: foodfleet",
                "dependsOn:", "- component:catalog", "- component:customer");
        assertThat(yaml).contains("name: app-checkout\n").contains("type: website");
        assertThat(yaml.split("kind: Component", -1)).hasSize(FoodFleetTeams.after().components().size() + 1);
    }
}
