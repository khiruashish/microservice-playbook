package dev.foodfleet.ch20.fitness;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch20.detect.AntiPatternReport;
import dev.foodfleet.ch20.detect.Baseline;
import dev.foodfleet.ch20.detect.Finding;
import dev.foodfleet.ch20.model.SystemModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The architecture fitness function (chapter 73): runs on every build of the architecture repo.
 * The current model may contain only findings the team has accepted, and the accepted list may
 * only shrink.
 */
class ArchitectureFitnessTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = ArchitectureFitnessTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theCurrentArchitectureHasNoUnacceptedAntiPatterns() throws IOException {
        SystemModel current = AntiPatternReport.load("/architecture/foodfleet-2024.model");
        Baseline baseline = Baseline.parse(resource("/architecture/accepted-findings.txt"));

        List<String> violations = baseline.violations(AntiPatternReport.analyse(current), LocalDate.now());

        assertThat(violations).isEmpty();
    }

    @Test
    void the2023ArchitectureWouldHaveFailedTheBuild() throws IOException {
        SystemModel old = AntiPatternReport.load("/architecture/foodfleet-2023.model");
        Baseline baseline = Baseline.parse(resource("/architecture/accepted-findings.txt"));

        List<String> violations = baseline.violations(AntiPatternReport.analyse(old), LocalDate.of(2023, 11, 1));

        assertThat(violations.stream().filter(v -> v.startsWith("NEW")).count()).isGreaterThan(10L);
    }

    @Test
    void fixedFindingsMustLeaveTheBaselineAndAcceptancesExpire() {
        Baseline baseline = Baseline.parse("""
                SHARED_DATABASE:customers owner=growth ticket=FF-1 until=2025-03-31
                CHATTY:a->b owner=orders ticket=FF-2
                """);
        Finding stillThere = new Finding(Finding.AntiPattern.SHARED_DATABASE, Finding.Severity.MEDIUM, "customers",
                "read directly", "events");

        List<String> violations = baseline.violations(List.of(stillThere), LocalDate.of(2025, 4, 1));

        assertThat(violations).containsExactly(
                "EXPIRED SHARED_DATABASE:customers was accepted until 2025-03-31 (FF-1)",
                "FIXED CHATTY:a->b: remove it from the baseline so it can't return unnoticed");
    }
}
