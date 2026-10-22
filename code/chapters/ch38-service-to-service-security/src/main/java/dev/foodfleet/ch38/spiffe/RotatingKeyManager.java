package dev.foodfleet.ch38.spiffe;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * Serves the workload's current SVID and lets a new one be installed while connections are being made, so an
 * {@link javax.net.ssl.SSLContext} built once keeps presenting fresh certificates (SPIRE rotates at half the SVID
 * lifetime). The JDK asks for an alias, then for the chain and the key in separate calls; each SVID gets its own
 * alias so a rotation between those calls cannot pair a new certificate with an old key.
 */
public final class RotatingKeyManager extends X509ExtendedKeyManager {

    private record Material(String alias, PrivateKey key, X509Certificate[] chain) {
    }

    private final AtomicLong version = new AtomicLong();
    private final AtomicReference<Material> current = new AtomicReference<>();
    private final Map<String, Material> recent = new ConcurrentHashMap<>();

    public RotatingKeyManager(PrivateKey key, X509Certificate[] chain) {
        update(key, chain);
    }

    /** Installs a new SVID; handshakes that already chose the previous one can still finish with it. */
    public void update(PrivateKey key, X509Certificate[] chain) {
        Material next = new Material("svid-" + version.incrementAndGet(), key, chain.clone());
        recent.put(next.alias(), next);
        Material previous = current.getAndSet(next);
        if (previous != null) {
            // keep only the two newest: enough for a handshake in flight during one rotation
            recent.keySet().removeIf(a -> !a.equals(next.alias()) && !a.equals(previous.alias()));
        }
    }

    public X509Certificate currentLeaf() {
        return current.get().chain()[0];
    }

    private String aliasFor(String... keyTypes) {
        Material m = current.get();
        for (String type : keyTypes) {
            if (type != null && type.equals(m.key().getAlgorithm())) {
                return m.alias();
            }
        }
        return null;
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
        return aliasFor(keyTypes);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
        return aliasFor(keyTypes);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return aliasFor(keyType);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return aliasFor(keyType);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        String a = aliasFor(keyType);
        return a == null ? null : new String[] {a};
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return getClientAliases(keyType, issuers);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        Material m = recent.get(alias);
        return m == null ? null : m.chain().clone();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        Material m = recent.get(alias);
        return m == null ? null : m.key();
    }
}
