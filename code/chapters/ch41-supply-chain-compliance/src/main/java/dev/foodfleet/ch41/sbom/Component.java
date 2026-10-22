package dev.foodfleet.ch41.sbom;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A component from an SBOM, identified by its package URL (purl), e.g.
 * {@code pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1?type=jar}. The purl, not the file name, is what lets a
 * scanner find the right advisories: "log4j-core-2.14.1.jar" renamed to "logging.jar" is still log4j-core.
 */
public record Component(String type, String namespace, String name, String version, String location) {

    /** Parses {@code pkg:type/namespace/name@version?qualifiers#subpath}; empty if it is not a purl with a version. */
    public static Optional<Component> fromPurl(String purl, String location) {
        if (purl == null || !purl.startsWith("pkg:")) {
            return Optional.empty();
        }
        String s = purl.substring(4);
        int hash = s.indexOf('#');
        if (hash >= 0) {
            s = s.substring(0, hash);
        }
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int at = s.lastIndexOf('@');
        if (at < 0) {
            return Optional.empty();
        }
        String version = decode(s.substring(at + 1));
        String[] path = s.substring(0, at).split("/");
        if (path.length < 2) {
            return Optional.empty();
        }
        String type = path[0].toLowerCase(java.util.Locale.ROOT);
        String name = decode(path[path.length - 1]);
        String namespace = path.length > 2 ? decode(String.join("/", java.util.Arrays.copyOfRange(path, 1, path.length - 1))) : null;
        return Optional.of(new Component(type, namespace, name, version, location));
    }

    /** The package name in OSV's Maven form, "groupId:artifactId". */
    public String mavenName() {
        return namespace == null ? name : namespace + ":" + name;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
