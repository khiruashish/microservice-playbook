package dev.foodfleet.ch35.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Uses a fixed resolver, so the tests decide what each host name resolves to and need no network. */
class OutboundUrlPolicyTest {

    static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static final Map<String, List<InetAddress>> DNS = Map.of(
            "hooks.restaurant.example", List.of(ip("203.0.113.10")),
            "internal.restaurant.example", List.of(ip("10.0.0.5")),
            "mixed.restaurant.example", List.of(ip("203.0.113.11"), ip("192.168.1.20")));

    final OutboundUrlPolicy policy = new OutboundUrlPolicy(Set.of("https"), Set.of(443), List.of(), host -> {
        if (DNS.containsKey(host)) {
            return DNS.get(host).toArray(InetAddress[]::new);
        }
        try {
            return InetAddress.getAllByName(host);       // IP literals only in these tests
        } catch (UnknownHostException e) {
            return new InetAddress[0];
        }
    });

    @Test
    void aPublicHttpsEndpointIsAllowed() {
        assertThat(policy.check("https://hooks.restaurant.example/foodfleet").allowed()).isTrue();
    }

    @Test
    void namesThatResolveToPrivateAddressesAreRefused() {
        assertThat(policy.check("https://internal.restaurant.example/").allowed()).isFalse();
        assertThat(policy.check("https://mixed.restaurant.example/").allowed()).isFalse();   // any address counts
    }

    @Test
    void everyInternalAddressRangeIsRefused() {
        for (String internal : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "[::1]", "[fd00::1]", "[fe80::1]")) {
            assertThat(policy.check("https://" + internal + "/").allowed()).isFalse();
        }
    }

    @Test
    void otherSchemesPortsAndEmbeddedCredentialsAreRefused() {
        assertThat(policy.check("http://hooks.restaurant.example/").reason()).isEqualTo("scheme not allowed");
        assertThat(policy.check("https://hooks.restaurant.example:8443/").reason()).isEqualTo("port 8443 not allowed");
        assertThat(policy.check("https://user:pw@hooks.restaurant.example/").reason())
                .isEqualTo("credentials in URLs are not allowed");
        assertThat(policy.check("https://unknown.example/").reason()).isEqualTo("host does not resolve");
    }
}
