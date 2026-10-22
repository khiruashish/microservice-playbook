package dev.foodfleet.ch65;

import dev.foodfleet.ch65.arch.ClassGraph;
import dev.foodfleet.ch65.arch.FoodFleetRules;
import dev.foodfleet.ch65.arch.Rules;
import dev.foodfleet.ch65.sample.api.OrderController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The fitness functions as a test in the build: the sample service's compiled classes must satisfy every rule. */
@Tag("arch")
class ArchitectureTest {

    @Test
    void theServiceKeepsItsArchitecture() throws Exception {
        Path classes = Path.of(OrderController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassGraph graph = ClassGraph.of(classes, FoodFleetRules.ROOT);
        assertEquals(10, graph.classes().size());
        Map<String, List<String>> violations = Rules.evaluate(graph, FoodFleetRules.all());
        violations.values().forEach(v -> assertEquals(List.of(), v));
    }
}
