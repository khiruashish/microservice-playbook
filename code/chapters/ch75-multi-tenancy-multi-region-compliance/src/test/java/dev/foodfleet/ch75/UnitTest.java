package dev.foodfleet.ch75;

import dev.foodfleet.ch75.erasure.ByteScan;
import dev.foodfleet.ch75.erasure.CryptoShredder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static Connection keyStore(String name) throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=PostgreSQL");
    }

    @Test
    void shreddingASubjectsKeyMakesEveryCopyOfTheirDataUnreadable() throws Exception {
        try (Connection k = keyStore("a")) {
            CryptoShredder s = new CryptoShredder(k);
            byte[] email = s.encrypt("subject-1", "asha@example.in");
            byte[] other = s.encrypt("subject-2", "ravi@example.in");
            byte[] backupCopy = email.clone();
            assertFalse(new String(email, StandardCharsets.ISO_8859_1).contains("asha"), "no plaintext in the stored field");
            assertEquals("asha@example.in", s.decrypt("subject-1", email));
            assertTrue(s.shred("subject-1"));
            assertNull(s.decrypt("subject-1", email));
            assertNull(s.decrypt("subject-1", backupCopy));
            assertEquals("ravi@example.in", s.decrypt("subject-2", other), "other subjects are untouched");
        }
    }

    @Test
    void aFieldCannotBeDecryptedAsAnotherSubject() throws Exception {
        try (Connection k = keyStore("b")) {
            CryptoShredder s = new CryptoShredder(k);
            byte[] email = s.encrypt("subject-1", "asha@example.in");
            s.encrypt("subject-2", "ravi@example.in");
            assertThrows(AEADBadTagException.class, () -> s.decrypt("subject-2", email));
            byte[] again = s.encrypt("subject-1", "asha@example.in");
            assertFalse(java.util.Arrays.equals(email, again), "a fresh nonce per encryption");
        }
    }

    @Test
    void byteScanCountsEveryOccurrenceInEveryFile(@TempDir Path dir) throws Exception {
        byte[] needle = "erase-me".getBytes(StandardCharsets.UTF_8);
        assertEquals(2, ByteScan.count("xxerase-meyyerase-me".getBytes(StandardCharsets.UTF_8), needle));
        assertEquals(2, ByteScan.count("aaa".getBytes(StandardCharsets.UTF_8), "aa".getBytes(StandardCharsets.UTF_8)), "overlapping");
        Files.createDirectories(dir.resolve("base/1"));
        Files.write(dir.resolve("base/1/16384"), "....erase-me....".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("wal"), "erase-meerase-me".getBytes(StandardCharsets.UTF_8));
        assertEquals(3, ByteScan.count(dir, needle));
        assertEquals(0, ByteScan.count(dir.resolve("missing"), needle));
        assertArrayEquals(needle, "erase-me".getBytes(StandardCharsets.UTF_8));
    }
}
