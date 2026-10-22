package dev.foodfleet.ch41.sbom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CycloneDxTest {

    @Test
    void readsPurlsIncludingNestedComponents() {
        String bom = """
                {"bomFormat":"CycloneDX","specVersion":"1.6","components":[
                  {"type":"library","name":"order-service","version":"1.4.0","purl":"pkg:maven/in.foodfleet/order-service@1.4.0",
                   "components":[{"type":"library","name":"jackson-core","version":"3.1.5",
                                  "purl":"pkg:maven/tools.jackson.core/jackson-core@3.1.5?type=jar"}]},
                  {"type":"file","name":"/opt/app/start.sh"},
                  {"type":"library","name":"netty-codec","purl":"pkg:maven/io.netty/netty-codec@4.1.100.Final",
                   "properties":[{"name":"syft:location:0:path","value":"/lib/netty-codec.jar"}]}]}
                """;
        List<Component> c = CycloneDx.components(bom);
        assertThat(c).extracting(Component::mavenName)
                .containsExactly("in.foodfleet:order-service", "tools.jackson.core:jackson-core", "io.netty:netty-codec");
        assertThat(c.get(2).version()).isEqualTo("4.1.100.Final");
        assertThat(c.get(2).location()).isEqualTo("/lib/netty-codec.jar");
    }

    @Test
    void purlParsing() {
        Component c = Component.fromPurl("pkg:maven/org.example/a%2Bb@1.0%2Bbuild.1?classifier=sources#x", null).orElseThrow();
        assertThat(c.namespace()).isEqualTo("org.example");
        assertThat(c.name()).isEqualTo("a+b");
        assertThat(c.version()).isEqualTo("1.0+build.1");
        assertThat(Component.fromPurl("pkg:maven/org.example/nover", null)).isEmpty();
        assertThat(Component.fromPurl("not-a-purl", null)).isEmpty();
        assertThatThrownBy(() -> CycloneDx.components("{\"bomFormat\":\"SPDX\"}")).isInstanceOf(IllegalArgumentException.class);
    }
}
