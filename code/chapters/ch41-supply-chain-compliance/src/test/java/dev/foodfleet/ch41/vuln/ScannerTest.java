package dev.foodfleet.ch41.vuln;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch41.sbom.Component;
import dev.foodfleet.ch41.vuln.Advisory.Severity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTest {

    // the Log4Shell advisory as the GitHub Advisory Database has it (trimmed)
    static final String LOG4SHELL = """
            {"schema_version":"1.4.0","id":"GHSA-jfh8-c2jp-5v3q","published":"2021-12-10T00:40:56Z","aliases":["CVE-2021-44228"],
             "summary":"Remote code injection in Log4j","database_specific":{"severity":"CRITICAL"},
             "affected":[
              {"package":{"ecosystem":"Maven","name":"org.apache.logging.log4j:log4j-core"},
               "ranges":[{"type":"ECOSYSTEM","events":[{"introduced":"2.13.0"},{"fixed":"2.15.0"}]}]},
              {"package":{"ecosystem":"Maven","name":"org.apache.logging.log4j:log4j-core"},
               "ranges":[{"type":"ECOSYSTEM","events":[{"introduced":"2.0-beta9"},{"fixed":"2.3.1"}]}]},
              {"package":{"ecosystem":"Maven","name":"org.apache.logging.log4j:log4j-core"},
               "ranges":[{"type":"ECOSYSTEM","events":[{"introduced":"2.4"},{"fixed":"2.12.2"}]}]},
              {"package":{"ecosystem":"Maven","name":"org.xbib.elasticsearch:log4j"},"versions":["6.3.2.1"]},
              {"package":{"ecosystem":"npm","name":"log4j-core"},"ranges":[{"type":"SEMVER","events":[{"introduced":"0"}]}]}]}
            """;

    static final String LAST_AFFECTED = """
            {"id":"GHSA-test-0002","published":"2024-05-01T00:00:00Z","database_specific":{"severity":"MODERATE"},
             "affected":[{"package":{"ecosystem":"Maven","name":"com.example:lib"},
               "ranges":[{"type":"ECOSYSTEM","events":[{"introduced":"0"},{"last_affected":"1.4.2"}]}]}]}
            """;

    static final String WITHDRAWN = """
            {"id":"GHSA-test-0003","published":"2024-05-01T00:00:00Z","withdrawn":"2024-06-01T00:00:00Z",
             "database_specific":{"severity":"HIGH"},
             "affected":[{"package":{"ecosystem":"Maven","name":"com.example:lib"},
               "ranges":[{"type":"ECOSYSTEM","events":[{"introduced":"0"},{"fixed":"9.0"}]}]}]}
            """;

    static Scanner scanner() {
        OsvDatabase db = new OsvDatabase();
        db.add(OsvDatabase.parse(LOG4SHELL, "Maven"));
        db.add(OsvDatabase.parse(LAST_AFFECTED, "Maven"));
        db.add(OsvDatabase.parse(WITHDRAWN, "Maven"));
        return new Scanner(db);
    }

    static Component maven(String purl) {
        return Component.fromPurl(purl, "app.jar").orElseThrow();
    }

    @Test
    void matchesByCoordinatesAndVersionRange() {
        List<Scanner.Finding> f = scanner().scan(List.of(
                maven("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1?type=jar"),
                maven("pkg:maven/org.apache.logging.log4j/log4j-core@2.12.2"),
                maven("pkg:maven/org.apache.logging.log4j/log4j-core@2.17.1"),
                maven("pkg:maven/org.apache.logging.log4j/log4j-api@2.14.1"),
                maven("pkg:maven/org.xbib.elasticsearch/log4j@6.3.2.1")), null);
        assertThat(f).extracting(x -> x.component().mavenName() + "@" + x.component().version())
                .containsExactly("org.apache.logging.log4j:log4j-core@2.14.1", "org.xbib.elasticsearch:log4j@6.3.2.1");
        assertThat(f.get(0).fixedIn()).isEqualTo("2.15.0");
        assertThat(f.get(0).advisory().displayId()).isEqualTo("CVE-2021-44228");
        assertThat(f.get(1).fixedIn()).isNull();
    }

    @Test
    void lastAffectedIsInclusiveAndWithdrawnAdvisoriesAreIgnored() {
        List<Scanner.Finding> f = scanner().scan(List.of(maven("pkg:maven/com.example/lib@1.4.2"),
                maven("pkg:maven/com.example/lib@1.4.3")), null);
        assertThat(f).hasSize(1);
        assertThat(f.get(0).component().version()).isEqualTo("1.4.2");
        assertThat(f.get(0).advisory().id()).isEqualTo("GHSA-test-0002");
    }

    @Test
    void whatWasKnownAtReleaseTime() {
        List<Component> c = List.of(maven("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"));
        assertThat(scanner().scan(c, Instant.parse("2021-12-01T00:00:00Z"))).isEmpty();
        assertThat(scanner().scan(c, Instant.parse("2021-12-11T00:00:00Z"))).hasSize(1);
    }

    @Test
    void theGateBlocksFixableSeriousFindingsUnlessWaived() {
        List<Scanner.Finding> f = scanner().scan(List.of(maven("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"),
                maven("pkg:maven/org.xbib.elasticsearch/log4j@6.3.2.1"), maven("pkg:maven/com.example/lib@1.0")), null);
        Gate.Result strict = new Gate(Severity.HIGH, List.of()).evaluate(f, LocalDate.of(2026, 10, 13));
        assertThat(strict.passed()).isFalse();
        assertThat(strict.blocking()).hasSize(1);                     // log4j-core: critical, fix available
        assertThat(strict.reported()).hasSize(2);                     // no fix (xbib), moderate (example)
        Gate.Waiver w = new Gate.Waiver("GHSA-jfh8-c2jp-5v3q", "org.apache.logging.log4j:log4j-core",
                "JNDI lookups disabled by log4j2.formatMsgNoLookups; upgrade in sprint 42", LocalDate.of(2026, 11, 1));
        assertThat(new Gate(Severity.HIGH, List.of(w)).evaluate(f, LocalDate.of(2026, 10, 13)).passed()).isTrue();
        assertThat(new Gate(Severity.HIGH, List.of(w)).evaluate(f, LocalDate.of(2026, 11, 2)).passed()).isFalse();
    }
}
