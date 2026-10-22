package dev.foodfleet.ch72;

import dev.foodfleet.ch72.conformance.Conformance;
import dev.foodfleet.ch72.scaffold.Template;
import dev.foodfleet.ch72.scaffold.Upgrade;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static final Path TEMPLATES = Path.of(System.getProperty("c72.templates", "src/main/resources/templates"));
    static final Template.Values V = new Template.Values("rider-ratings", "delivery", "Ratings.", "1.0.0");

    @Test
    void theCurrentTemplatePassesEveryCheck(@TempDir Path dir) throws Exception {
        Template.scaffold(TEMPLATES.resolve("v2"), V, dir);
        assertTrue(Files.exists(dir.resolve("src/main/java/dev/foodfleet/riderratings/Application.java")));
        Map<String, Boolean> r = Conformance.check(Conformance.read(dir));
        assertEquals(Conformance.CHECKS.length, r.values().stream().filter(b -> b).count(), r.toString());
    }

    @Test
    void anUpgradeKeepsTheServicesOwnChanges(@TempDir Path dir) throws Exception {
        Template.scaffold(TEMPLATES.resolve("v1"), V, dir);
        Path app = dir.resolve("src/main/java/dev/foodfleet/riderratings/Application.java");
        Files.writeString(app, Files.readString(app).replace("        server.start();", "        server.createContext(\"/ratings\", ex -> reply(ex, 200, \"[]\"));\n        server.start();"));
        Map<String, Upgrade.Outcome> out = Upgrade.apply(dir, Template.render(TEMPLATES.resolve("v1"), V), Template.render(TEMPLATES.resolve("v2"), V));
        assertEquals(Upgrade.Outcome.MERGED, out.get("src/main/java/dev/foodfleet/riderratings/Application.java"));
        String merged = Files.readString(app);
        assertTrue(merged.contains("/ratings") && merged.contains("/actuator/health/readiness"), "both changes kept");
    }

    @Test
    void conflictMarkersFailTheChecksOfThatFile(@TempDir Path dir) throws Exception {
        Template.scaffold(TEMPLATES.resolve("v2"), V, dir);
        Path docker = dir.resolve("Dockerfile");
        Files.writeString(docker, "<<<<<<< service\n" + Files.readString(docker) + "=======\n>>>>>>> new template\n");
        assertFalse(Conformance.check(Conformance.read(dir)).get("jvm-collector"));
    }
}
