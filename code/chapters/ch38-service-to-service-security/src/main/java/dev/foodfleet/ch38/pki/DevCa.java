package dev.foodfleet.ch38.pki;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A development certificate authority for one SPIFFE trust domain, using only the JDK. It issues X.509-SVIDs the way
 * SPIRE does (a URI SAN, {@code cA=false}, {@code digitalSignature}, client and server auth) so tests and the
 * measurements in {@code verify/} can run real mutual TLS without SPIRE. Not for production: production SVIDs come
 * from SPIRE (or the mesh's CA), with keys that never leave the workload.
 */
public final class DevCa {

    private static final String SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String trustDomain;
    private final KeyPair keys;
    private final X509Certificate certificate;
    private final byte[] subject;

    private DevCa(String trustDomain, String commonName, KeyPair keys, Instant notBefore, Instant notAfter)
            throws GeneralSecurityException {
        this.trustDomain = trustDomain;
        this.keys = keys;
        this.subject = name("FoodFleet", commonName);
        Spec spec = new Spec().uris("spiffe://" + trustDomain).ca(true).validity(notBefore, notAfter);
        this.certificate = sign(spec, keys.getPublic().getEncoded(), subject, subject, keys.getPrivate());
    }

    /** A self-signed root for {@code trustDomain}, valid from one minute ago for {@code lifetime}. */
    public static DevCa create(String trustDomain, Duration lifetime) {
        Instant now = Instant.now();
        return create(trustDomain, "dev ca " + trustDomain, now.minusSeconds(60), now.plus(lifetime));
    }

    public static DevCa create(String trustDomain, String commonName, Instant notBefore, Instant notAfter) {
        try {
            return new DevCa(trustDomain, commonName, ecKeys(), notBefore, notAfter);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String trustDomain() {
        return trustDomain;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    /** An X.509-SVID for {@code spiffeId}, valid from one minute ago (clock skew) for {@code lifetime}. */
    public Credential issue(String spiffeId, Duration lifetime) {
        Instant now = Instant.now();
        return issue(new Spec().uris(spiffeId).validity(now.minusSeconds(60), now.plus(lifetime)));
    }

    /** A certificate exactly as {@code spec} describes, including deliberately invalid ones for tests. */
    public Credential issue(Spec spec) {
        try {
            KeyPair leaf = spec.rsa ? rsaKeys() : ecKeys();
            X509Certificate cert = sign(spec, leaf.getPublic().getEncoded(), subject,
                    name("FoodFleet", spec.commonName), keys.getPrivate());
            return new Credential(leaf.getPrivate(), new X509Certificate[] {cert});
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A private key with its certificate chain, leaf first: what a key manager serves. */
    public record Credential(PrivateKey privateKey, X509Certificate[] chain) {
        public X509Certificate leaf() {
            return chain[0];
        }
    }

    /** What to put in a certificate. Defaults: a leaf SVID for client and server authentication. */
    public static final class Spec {
        private final List<String> uris = new ArrayList<>();
        private final List<String> dnsNames = new ArrayList<>();
        private boolean ca;
        private boolean rsa;
        private String commonName;
        private Instant notBefore = Instant.now().minusSeconds(60);
        private Instant notAfter = Instant.now().plus(Duration.ofHours(1));

        public Spec uris(String... values) {
            uris.addAll(List.of(values));
            return this;
        }

        public Spec dnsNames(String... values) {
            dnsNames.addAll(List.of(values));
            return this;
        }

        public Spec ca(boolean value) {
            ca = value;
            return this;
        }

        public Spec rsa(boolean value) {
            rsa = value;
            return this;
        }

        public Spec commonName(String value) {
            commonName = value;
            return this;
        }

        public Spec validity(Instant from, Instant until) {
            notBefore = from;
            notAfter = until;
            return this;
        }
    }

    private static X509Certificate sign(Spec spec, byte[] subjectPublicKeyInfo, byte[] issuer, byte[] subject,
            PrivateKey issuerKey) throws GeneralSecurityException {
        byte[] algorithm = Der.sequence(Der.oid(SHA256_WITH_ECDSA));
        byte[] tbs = Der.sequence(
                Der.explicit(0, Der.integer(BigInteger.TWO)),                       // version 3
                Der.integer(new BigInteger(127, RANDOM).add(BigInteger.ONE)),       // positive serial
                algorithm,
                issuer,
                Der.sequence(Der.time(spec.notBefore), Der.time(spec.notAfter)),
                subject,
                subjectPublicKeyInfo,
                Der.explicit(3, Der.sequence(extensions(spec))));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(issuerKey);
        signer.update(tbs);
        byte[] der = Der.sequence(tbs, algorithm, Der.bitString(signer.sign(), 0));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    private static byte[][] extensions(Spec spec) {
        List<byte[]> ext = new ArrayList<>();
        // basicConstraints, critical: cA true for signing certificates, false (empty sequence) for leaves
        ext.add(extension("2.5.29.19", true, spec.ca ? Der.sequence(Der.bool(true)) : Der.sequence()));
        // keyUsage, critical: keyCertSign + cRLSign for CAs, digitalSignature for leaves (X509-SVID §4.3)
        ext.add(extension("2.5.29.15", true,
                spec.ca ? Der.bitString(new byte[] {0x06}, 1) : Der.bitString(new byte[] {(byte) 0x80}, 7)));
        if (!spec.ca) {
            // extKeyUsage: serverAuth and clientAuth (X509-SVID §4.4)
            ext.add(extension("2.5.29.37", false,
                    Der.sequence(Der.oid("1.3.6.1.5.5.7.3.1"), Der.oid("1.3.6.1.5.5.7.3.2"))));
        }
        List<byte[]> names = new ArrayList<>();
        spec.uris.forEach(u -> names.add(Der.tlv(0x86, u.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        spec.dnsNames.forEach(d -> names.add(Der.tlv(0x82, d.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        if (!names.isEmpty()) {
            ext.add(extension("2.5.29.17", false, Der.sequence(names.toArray(byte[][]::new))));
        }
        return ext.toArray(byte[][]::new);
    }

    private static byte[] extension(String oid, boolean critical, byte[] value) {
        return critical
                ? Der.sequence(Der.oid(oid), Der.bool(true), Der.octetString(value))
                : Der.sequence(Der.oid(oid), Der.octetString(value));
    }

    private static byte[] name(String organization, String commonName) {
        byte[] o = Der.set(Der.sequence(Der.oid("2.5.4.10"), Der.utf8(organization)));
        return commonName == null ? Der.sequence(o)
                : Der.sequence(o, Der.set(Der.sequence(Der.oid("2.5.4.3"), Der.utf8(commonName))));
    }

    private static KeyPair ecKeys() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static KeyPair rsaKeys() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }
}
