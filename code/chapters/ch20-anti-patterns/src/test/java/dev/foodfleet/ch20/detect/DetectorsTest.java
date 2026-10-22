package dev.foodfleet.ch20.detect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch20.detect.Finding.AntiPattern;
import dev.foodfleet.ch20.model.ModelParser;
import dev.foodfleet.ch20.model.SystemModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectorsTest {

    private static List<AntiPattern> patterns(List<Finding> findings) {
        return findings.stream().map(Finding::pattern).toList();
    }

    @Test
    void lockstepDeploysAreADistributedMonolith() {
        StringBuilder text = new StringBuilder("system x\n");
        for (int day = 1; day <= 6; day++) {
            text.append("deploy 2024-01-0%dT10:00 a,b,c\n".formatted(day));
        }
        text.append("deploy 2024-01-07T10:00 d\n");

        List<Finding> findings = new DeployCouplingDetector(0.7, 5).detect(ModelParser.parse(text.toString()));

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().subject()).isEqualTo("a,b,c");
    }

    @Test
    void independentDeploysAreFine() {
        StringBuilder text = new StringBuilder("system x\n");
        for (int day = 1; day <= 9; day++) {
            text.append("deploy 2024-01-0%dT10:00 %s\n".formatted(day, "abc".charAt(day % 3)));
        }

        assertThat(new DeployCouplingDetector(0.7, 5).detect(ModelParser.parse(text.toString()))).isEmpty();
    }

    @Test
    void callGraphSmells() {
        SystemModel model = ModelParser.parse("""
                system x
                call web -> a per-request=1 sync
                call a -> b per-request=20 sync
                call b -> c per-request=1 sync
                call c -> d per-request=1 sync
                call d -> b per-request=1 sync
                call a -> e per-request=1 async
                """);

        List<Finding> findings = new CallGraphDetector(5, 6, 3, 0.999).detect(model);

        assertThat(patterns(findings)).contains(AntiPattern.CHATTY, AntiPattern.CYCLIC_DEPENDENCY, AntiPattern.SYNC_CHAIN);
        assertThat(findings.stream().filter(f -> f.pattern() == AntiPattern.CYCLIC_DEPENDENCY).toList()).hasSize(1);
    }

    @Test
    void asyncCallsDoNotCount() {
        SystemModel model = ModelParser.parse("""
                system x
                call a -> b per-request=50 async
                call b -> a per-request=1 async
                """);

        assertThat(new CallGraphDetector(5, 6, 3, 0.999).detect(model)).isEmpty();
    }

    @Test
    void aTinyServiceWithOneCallerIsANanoService() {
        SystemModel model = ModelParser.parse("""
                system x
                service big endpoints=12 loc=30000
                service tiny endpoints=1 loc=800
                service shared-tiny endpoints=1 loc=800
                call big -> tiny sync
                call big -> shared-tiny sync
                call other -> shared-tiny sync
                """);

        List<Finding> findings = new NanoServiceDetector(2, 3_000).detect(model);

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().subject()).isEqualTo("tiny");
        assertThat(findings.getFirst().remedy()).contains("merge into big");
    }

    @Test
    void sharedTablesAndLibraries() {
        SystemModel model = ModelParser.parse("""
                system x
                table orders owner=ordering writers=status
                table menu owner=catalog readers=ordering
                table zones owner=zones
                library domain kind=domain a=1.0.0 b=1.0.0
                library http kind=utility a=1.0.0 b=1.1.0 c=1.2.0 d=2.0.0
                """);

        List<Finding> findings = new SharedDataDetector(2).detect(model);

        assertThat(findings.stream().map(Finding::key).toList()).containsExactly(
                "SHARED_DATABASE:orders", "SHARED_DATABASE:menu", "SHARED_DOMAIN_LIBRARY:domain",
                "LIBRARY_VERSION_SKEW:http");
    }
}
