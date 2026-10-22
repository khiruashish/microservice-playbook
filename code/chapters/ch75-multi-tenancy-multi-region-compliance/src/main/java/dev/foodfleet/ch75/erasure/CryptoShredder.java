package dev.foodfleet.ch75.erasure;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Crypto-shredding: each data subject's personal fields are encrypted with a key of their own (AES-256-GCM), kept in a
 * separate key store. Erasing the subject deletes the key; every copy of the ciphertext (tables, WAL, backups, event
 * logs) becomes unreadable at once. The key store is the one place left to erase from, so it must be small, separately
 * backed up with short retention, or a KMS.
 */
public final class CryptoShredder {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Connection keys;

    public CryptoShredder(Connection keyStore) throws SQLException {
        this.keys = keyStore;
        try (Statement s = keys.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS subject_keys (subject varchar(200) PRIMARY KEY, secret bytea NOT NULL)");
        }
    }

    private SecretKey key(String subject, boolean create) throws SQLException, GeneralSecurityException {
        try (PreparedStatement s = keys.prepareStatement("SELECT secret FROM subject_keys WHERE subject = ?")) {
            s.setString(1, subject);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) {
                    return new SecretKeySpec(r.getBytes(1), "AES");
                }
            }
        }
        if (!create) {
            return null;
        }
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        SecretKey k = g.generateKey();
        try (PreparedStatement s = keys.prepareStatement("INSERT INTO subject_keys VALUES (?, ?)")) {
            s.setString(1, subject);
            s.setBytes(2, k.getEncoded());
            s.execute();
        }
        return k;
    }

    /** Encrypts a field for a subject: a 12-byte nonce followed by the ciphertext and tag. */
    public byte[] encrypt(String subject, String plaintext) throws SQLException, GeneralSecurityException {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(subject, true), new GCMParameterSpec(128, nonce));
        c.updateAAD(subject.getBytes(StandardCharsets.UTF_8));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(12 + ct.length).put(nonce).put(ct).array();
    }

    /** Decrypts a field, or returns null when the subject's key has been shredded. */
    public String decrypt(String subject, byte[] field) throws SQLException, GeneralSecurityException {
        SecretKey k = key(subject, false);
        if (k == null) {
            return null;
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, field, 0, 12));
        c.updateAAD(subject.getBytes(StandardCharsets.UTF_8));
        return new String(c.doFinal(field, 12, field.length - 12), StandardCharsets.UTF_8);
    }

    /** Erases a subject: deletes their key. */
    public boolean shred(String subject) throws SQLException {
        try (PreparedStatement s = keys.prepareStatement("DELETE FROM subject_keys WHERE subject = ?")) {
            s.setString(1, subject);
            return s.executeUpdate() == 1;
        }
    }
}
