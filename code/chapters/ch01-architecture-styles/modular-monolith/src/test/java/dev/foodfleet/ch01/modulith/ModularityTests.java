package dev.foodfleet.ch01.modulith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture fitness function: the build fails if any module reaches into another module's
 * {@code internal} package, if a dependency cycle appears, or if a module uses a module outside
 * its {@code allowedDependencies}. This is the "modular" in modular monolith. Without this test,
 * module boundaries erode within a few sprints.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ModularMonolithApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Generates C4/PlantUML component diagrams and module canvases into target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
