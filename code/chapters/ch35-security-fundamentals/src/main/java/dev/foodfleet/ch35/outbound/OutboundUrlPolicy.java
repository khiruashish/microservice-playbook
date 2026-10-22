package dev.foodfleet.ch35.outbound;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides whether the service may call a URL it was given (API7: server-side request forgery). Restaurants
 * register webhook URLs; without this check a URL like http://169.254.169.254/ makes the order service fetch
 * cloud credentials, or http://localhost:8081/actuator its own internals, for the attacker.
 *
 * <p>It checks the addresses the host name resolves to, all of them, not the URL's text: 2130706433, 0177.0.0.1,
 * [::ffff:127.0.0.1] and a DNS name pointing at 10.0.0.5 are all internal. Redirects must not be followed (the
 * fetcher refuses them), or a harmless first URL can redirect to an internal one.
 */
public final class OutboundUrlPolicy {

    public record Decision(boolean allowed, String reason, List<InetAddress> addresses) {

        static Decision deny(String reason) {
            return new Decision(false, reason, List.of());
        }
    }

    private final Set<String> schemes;
    private final Set<Integer> ports;
    private final Function<String, InetAddress[]> resolver;
    private final List<Cidr> alsoAllowed;

    public OutboundUrlPolicy(Set<String> schemes, Set<Integer> ports, List<String> alsoAllowedCidrs) {
        this(schemes, ports, alsoAllowedCidrs, OutboundUrlPolicy::resolve);
    }

    /** {@code alsoAllowedCidrs}: internal ranges that are legitimate targets (in production usually none). */
    public OutboundUrlPolicy(Set<String> schemes, Set<Integer> ports, List<String> alsoAllowedCidrs,
                             Function<String, InetAddress[]> resolver) {
        this.schemes = Set.copyOf(schemes);
        this.ports = Set.copyOf(ports);
        this.alsoAllowed = alsoAllowedCidrs.stream().map(Cidr::parse).toList();
        this.resolver = resolver;
    }

    public static OutboundUrlPolicy webhooks() {
        return new OutboundUrlPolicy(Set.of("https"), Set.of(443), List.of());
    }

    public Decision check(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Decision.deny("not a valid URL");
        }
        if (uri.getScheme() == null || !schemes.contains(uri.getScheme().toLowerCase())) {
            return Decision.deny("scheme not allowed");
        }
        if (uri.getRawUserInfo() != null) {
            return Decision.deny("credentials in URLs are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return Decision.deny("no host");
        }
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        if (!ports.contains(port)) {
            return Decision.deny("port " + port + " not allowed");
        }
        InetAddress[] addresses = resolver.apply(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        if (addresses == null || addresses.length == 0) {
            return Decision.deny("host does not resolve");
        }
        for (InetAddress a : addresses) {
            if (isInternal(a) && alsoAllowed.stream().noneMatch(c -> c.contains(a))) {
                return Decision.deny(host + " resolves to internal address " + a.getHostAddress());
            }
        }
        return new Decision(true, "ok", List.of(addresses));
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                        // "this network"
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || (first == 198 && (second == 18 || second == 19)) // benchmarking
                    || first >= 240;                                 // reserved, broadcast
        }
        if (a instanceof Inet6Address v6) {
            if ((b[0] & 0xfe) == 0xfc) {
                return true;                                         // unique local fc00::/7
            }
            if (v6.isIPv4CompatibleAddress() || isNat64(b)) {         // ::a.b.c.d, 64:ff9b::a.b.c.d
                try {
                    return isInternal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNat64(byte[] b) {
        return b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return new InetAddress[0];
        }
    }

    record Cidr(byte[] network, int bits) {

        static Cidr parse(String cidr) {
            String[] p = cidr.split("/");
            try {
                return new Cidr(InetAddress.getByName(p[0]).getAddress(), Integer.parseInt(p[1]));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(cidr, e);
            }
        }

        boolean contains(InetAddress a) {
            byte[] b = a.getAddress();
            if (b.length != network.length) {
                return false;
            }
            for (int i = 0; i < bits; i++) {
                int mask = 0x80 >> (i % 8);
                if ((b[i / 8] & mask) != (network[i / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
