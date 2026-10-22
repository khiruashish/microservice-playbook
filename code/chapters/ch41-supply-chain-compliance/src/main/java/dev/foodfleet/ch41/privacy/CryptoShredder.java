package dev.foodfleet.ch41.privacy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Erasure for data that can't be deleted in place: Kafka topics, event stores, backups, analytics copies. Each data
 * subject (a customer) gets a data key; personal fields are encrypted with it wherever they are written; erasing
 * the subject deletes the key, and every copy everywhere becomes unreadable at once ("crypto-shredding").
 * <p>
 * Data keys are stored wrapped (encrypted) by a key-encryption key that lives in a KMS or OpenBao transit and never
 * in the database. Ciphertexts are bound to subject and field (AES-GCM associated data), so a value can't be moved
 * to another customer's record and still decrypt.
 */
public final class CryptoShredder {

    /** Where wrapped data keys live: a table with one row per subject, replicated but never in backups of the data. */
    public interface KeyStore {
        Optional<byte[]> get(String subject);

        void put(String subject, byte[] wrappedKey);

        boolean delete(String subject);
    }

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "ff1:";

    private final SecretKey kek;
    private final KeyStore store;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKey> cache;

    public CryptoShredder(SecretKey kek, KeyStore store, int cacheSize) {
        this.kek = kek;
        this.store = store;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SecretKey> eldest) {
                return size() > cacheSize;
            }
        };
    }

    public synchronized String encrypt(String subject, String field, String plaintext) throws GeneralSecurityException {
        SecretKey dek = dataKey(subject, true).orElseThrow();
        return PREFIX + Base64.getEncoder().encodeToString(seal(dek, aad(subject, field), plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    /** The plaintext, or empty when the subject has been erased. A tampered or misplaced value is an error. */
    public synchronized Optional<String> decrypt(String subject, String field, String token) throws GeneralSecurityException {
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not an encrypted field");
        }
        Optional<SecretKey> dek = dataKey(subject, false);
        if (dek.isEmpty()) {
            return Optional.empty();
        }
        byte[] pt = open(dek.get(), aad(subject, field), Base64.getDecoder().decode(token.substring(PREFIX.length())));
        return Optional.of(new String(pt, StandardCharsets.UTF_8));
    }

    /** Erases the subject: deletes the data key (and the cached copy). Irreversible by design. */
    public synchronized boolean erase(String subject) {
        cache.remove(subject);
        return store.delete(subject);
    }

    private Optional<SecretKey> dataKey(String subject, boolean create) throws GeneralSecurityException {
        SecretKey cached = cache.get(subject);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<byte[]> wrapped = store.get(subject);
        SecretKey dek;
        if (wrapped.isPresent()) {
            dek = new SecretKeySpec(open(kek, aad(subject, "dek"), wrapped.get()), "AES");
        } else if (create) {
            KeyGenerator g = KeyGenerator.getInstance("AES");
            g.init(256, random);
            dek = g.generateKey();
            store.put(subject, seal(kek, aad(subject, "dek"), dek.getEncoded()));
        } else {
            return Optional.empty();
        }
        cache.put(subject, dek);
        return Optional.of(dek);
    }

    private byte[] seal(SecretKey key, byte[] aad, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(aad);
        byte[] ct = c.doFinal(plaintext);
        return ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array();
    }

    private static byte[] open(SecretKey key, byte[] aad, byte[] sealed) throws GeneralSecurityException {
        if (sealed.length < IV_BYTES + TAG_BITS / 8) {
            throw new AEADBadTagException("truncated");
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
        c.updateAAD(aad);
        return c.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
    }

    private static byte[] aad(String subject, String field) {
        return (subject + "\u0000" + field).getBytes(StandardCharsets.UTF_8);
    }
}
