package dev.foodfleet.ch41.vuln;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MavenVersionTest {

    static int cmp(String a, String b) {
        return Integer.signum(MavenVersion.parse(a).compareTo(MavenVersion.parse(b)));
    }

    @Test
    void ordersLikeMaven() {
        // each strictly before the next (Maven's documented order, plus versions from real advisories)
        List<String> ascending = List.of("1-alpha", "1-alpha2", "1-beta", "1-milestone", "1-rc", "1-snapshot", "1",
                "1-sp", "1-abc", "1-1", "1.1", "1.2", "1.10", "2.0-beta9", "2.0-rc1", "2.0", "2.0.1", "2.3.1", "2.12.2",
                "2.14.1", "2.15.0", "2.17.1");
        for (int i = 0; i + 1 < ascending.size(); i++) {
            assertThat(cmp(ascending.get(i), ascending.get(i + 1))).as(ascending.get(i) + " < " + ascending.get(i + 1)).isEqualTo(-1);
            assertThat(cmp(ascending.get(i + 1), ascending.get(i))).isEqualTo(1);
        }
    }

    @Test
    void equalSpellings() {
        assertThat(cmp("1", "1.0")).isEqualTo(0);
        assertThat(cmp("1.0.0", "1")).isEqualTo(0);
        assertThat(cmp("6.0.0.Final", "6.0.0")).isEqualTo(0);
        assertThat(cmp("1-ga", "1")).isEqualTo(0);
        assertThat(cmp("1-cr1", "1-rc1")).isEqualTo(0);
        assertThat(cmp("1.0.RELEASE", "1.0")).isEqualTo(0);
        assertThat(cmp("1.0-M1", "1.0-milestone-1")).isEqualTo(0);
    }

    @Test
    void stringComparisonWouldBeWrong() {
        assertThat("2.10.0".compareTo("2.9.0")).isLessThan(0);                   // as strings: 2.10 < 2.9
        assertThat(cmp("2.10.0", "2.9.0")).isEqualTo(1);                          // as Maven versions: newer
        assertThat(cmp("9.4.51.v20230217", "9.4.5")).isEqualTo(1);               // Jetty's style
        assertThat(cmp("1.2.1.2-jre17", "1.2.1.2")).isEqualTo(1);                // unknown qualifier after release
    }
}
