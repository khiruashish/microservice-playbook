package dev.foodfleet.ch73;

import dev.foodfleet.ch73.adr.DecisionLog;
import dev.foodfleet.ch73.fitness.Bytecode;
import dev.foodfleet.ch73.fitness.FitnessFunction;
import dev.foodfleet.ch73.fitness.Functions;
import dev.foodfleet.ch73.fitness.Radar;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static final Path RESOURCES = Path.of(System.getProperty("c73.resources", "src/main/resources"));

    static void adr(Path dir, int n, String date, String status, boolean consequences) throws Exception {
        Files.writeString(dir.resolve(String.format("%04d-decision-%d.md", n, n)), "# " + n + ". Decision " + n + "\n\nDate: " + date
                + "\n\n## Status\n\n" + status + "\n\n## Context\n\nWhy.\n\n## Decision\n\nWhat.\n" + (consequences ? "\n## Consequences\n\nSo.\n" : ""));
    }

    @Test
    void theLogChecksFindGapsDatesAndBrokenSupersession(@TempDir Path dir) throws Exception {
        adr(dir, 1, "2026-01-05", "Accepted", true);
        adr(dir, 2, "2026-01-12", "Accepted", false);
        adr(dir, 3, "2025-12-01", "Accepted", true);
        adr(dir, 5, "2026-02-02", "Accepted\n\nSupersedes [1. Decision 1](0001-decision-1.md)", true);
        List<DecisionLog.Finding> f = DecisionLog.readFiles(dir, DecisionLog.Format.NYGARD).lint();
        assertTrue(f.contains(new DecisionLog.Finding("L1 sections", 2, "missing Consequences")), f.toString());
        assertTrue(f.contains(new DecisionLog.Finding("L3 numbering", 4, "missing from the sequence")), f.toString());
        assertTrue(f.stream().anyMatch(x -> x.rule().equals("L5 chronology") && x.adr() == 3), f.toString());
        assertTrue(f.contains(new DecisionLog.Finding("L7 supersession", 1, "superseded by 5 but still Accepted")), f.toString());
        assertEquals(4, f.size(), f.toString());
    }

    @Test
    void theBaselineReleasePassesTheStaticFunctionsAndADomainToAdapterDependencyFails(@TempDir Path dir) throws Exception {
        Radar radar = Radar.read(RESOURCES.resolve("radar/foodfleet-radar.csv"), RESOURCES.resolve("radar/radar-prefixes.csv"));
        Path src = dir.resolve("src");
        try (Stream<Path> s = Files.walk(RESOURCES.resolve("orders"))) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path t = src.resolve(RESOURCES.resolve("orders").relativize(p).toString());
                Files.createDirectories(t.getParent());
                Files.copy(p, t);
            }
        }
        Bytecode before = Bytecode.read(compile(src, dir.resolve("a")));
        Path pricing = src.resolve("dev/foodfleet/orders/domain/Pricing.java");
        Files.writeString(pricing, Files.readString(pricing).replace("    public Pricing(Menu menu) {",
                "    public Pricing() {\n        this(new dev.foodfleet.orders.adapter.menu.InMemoryMenu(10));\n    }\n\n    public Pricing(Menu menu) {"));
        Bytecode after = Bytecode.read(compile(src, dir.resolve("b")));
        List<FitnessFunction> suite = Functions.suite(radar);
        for (FitnessFunction f : suite.subList(0, 3)) {
            assertTrue(f.check().run(new FitnessFunction.Release("r1", null, null, before, null), null).pass(), f.id());
        }
        FitnessFunction.Result r = suite.getFirst().check().run(new FitnessFunction.Release("r3", null, null, after, before), null);
        assertFalse(r.pass());
        assertEquals("domain.Pricing -> adapter.menu.InMemoryMenu", r.detail());
        assertTrue(suite.get(2).check().run(new FitnessFunction.Release("r3", null, null, after, before), null).pass(), "an added constructor isn't a break");
    }

    @Test
    void theRadarReadsBuildYourOwnRadarCsvAndTreatsHoldAndCautionAlike() throws Exception {
        Radar radar = Radar.read(RESOURCES.resolve("radar/foodfleet-radar.csv"), RESOURCES.resolve("radar/radar-prefixes.csv"));
        assertNotNull(radar.heldBy("java.text.SimpleDateFormat"));
        assertNull(radar.heldBy("java.time.Instant"));
        assertTrue(new Radar.Blip("x", "Caution", "tools", false, "").onHold());
        assertEquals("Mutable and not thread-safe; use java.time.", radar.blips.get("java.util.Date and SimpleDateFormat").description());
    }

    static Path compile(Path src, Path out) throws Exception {
        Files.createDirectories(out);
        List<String> args = new ArrayList<>(List.of("--release", "25", "-d", out.toString()));
        try (Stream<Path> s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        return out;
    }
}
