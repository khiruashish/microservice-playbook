package dev.foodfleet.ch74;

import dev.foodfleet.ch74.roadmap.Codebase;
import dev.foodfleet.ch74.roadmap.Contexts;
import dev.foodfleet.ch74.roadmap.History;
import dev.foodfleet.ch74.roadmap.Roadmap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UnitTest {

    static Contexts contexts(Path dir) throws Exception {
        Path csv = dir.resolve("c.csv");
        Files.writeString(csv, "prefix,context,candidate\nx.a,a,true\nx.b,b,true\nx.c,c,true\nx.web,web,false\nx,platform,false\n");
        return Contexts.read(csv);
    }

    @Test
    void historyKeepsMainSourcesAndDropsHugeCommits(@TempDir Path dir) throws Exception {
        StringBuilder log = new StringBuilder("@1\nm/src/main/java/x/a/A.java\nm/src/test/java/x/b/BTest.java\n\n@2\nm/src/main/java/x/a/A.java\nm/src/main/java/x/b/B.java\n\n@3\nREADME.md\n\n@4\n");
        for (int i = 0; i < 31; i++) {
            log.append("m/src/main/java/x/c/C").append(i).append(".java\n");
        }
        History h = History.parse(log.toString(), contexts(dir));
        assertEquals(4, h.read);
        assertEquals(1, h.tooLarge);
        assertEquals(1, h.noMainSources);
        assertEquals(List.of(Set.of("a"), Set.of("a", "b")), h.commits);
        assertEquals(100.0 * 1 / 1.5, h.degree("a", "b"), 1e-9);
    }

    @Test
    void costCountsRemoteEdgesInversionsAndPairsBothWays(@TempDir Path dir) throws Exception {
        Contexts c = contexts(dir);
        Codebase code = new Codebase();
        code.edges.put("a>b", 10);
        code.edges.put("b>a", 2);
        code.edges.put("a>platform", 50);
        code.edges.put("platform>b", 3);
        code.edges.put("web>a", 7);
        History h = History.parse("@1\nm/src/main/java/x/a/A.java\n@2\nm/src/main/java/x/a/A.java\nm/src/main/java/x/platform/P.java\n", c);
        Roadmap r = new Roadmap(code, h, c);
        assertEquals(new Roadmap.Cost(0, 0, 0, 1, 0), r.cost(Set.of()));
        // a becomes a service: a>b, b>a and web>a cross; the platform library is free to use; one commit is a's alone
        assertEquals(new Roadmap.Cost(19, 0, 1, 1, 1), r.cost(Set.of("a")));
        // b too: the platform now depends on a service
        assertEquals(new Roadmap.Cost(19, 3, 1, 1, 1), r.cost(Set.of("a", "b")));
    }

    @Test
    void theLayeringLeavesTheFewestEdgesPointingUp(@TempDir Path dir) throws Exception {
        Contexts c = contexts(dir);
        Codebase code = new Codebase();
        code.edges.put("a>b", 5);
        code.edges.put("b>c", 4);
        code.edges.put("c>a", 1);
        code.edges.put("web>a", 9);
        Roadmap.Layering l = new Roadmap(code, History.parse("", c), c).layering(false);
        assertEquals(1, l.against());
        assertEquals(List.of("c>a 1"), l.edges());
        assertEquals(List.of("a", "b", "c", "web").size(), l.order().size());
    }
}
