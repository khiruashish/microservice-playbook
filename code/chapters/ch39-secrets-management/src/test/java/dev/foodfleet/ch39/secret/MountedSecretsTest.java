package dev.foodfleet.ch39.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The volume layout the kubelet writes: timestamped directories, a ..data symlink, key symlinks through it. */
class MountedSecretsTest {

    static void kubeletWrite(Path dir, Map<String, String> values) throws IOException {
        Path version = dir.resolve("..2026_10_08_" + System.nanoTime());
        Files.createDirectory(version);
        for (var e : values.entrySet()) {
            Files.writeString(version.resolve(e.getKey()), e.getValue());
        }
        Path tmp = dir.resolve("..data_tmp");
        Files.deleteIfExists(tmp);
        Files.createSymbolicLink(tmp, version.getFileName());
        Files.move(tmp, dir.resolve("..data"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        for (String key : values.keySet()) {
            if (!Files.exists(dir.resolve(key), LinkOption.NOFOLLOW_LINKS)) {
                Files.createSymbolicLink(dir.resolve(key), Path.of("..data", key));
            }
        }
    }

    @Test
    void readsEveryKeyOfOneVersion() throws IOException {
        Path dir = Files.createTempDirectory("mounted");
        kubeletWrite(dir, Map.of("username", "orders_v1", "password", "p1"));
        try (MountedSecrets secrets = new MountedSecrets(dir)) {
            MountedSecrets.Snapshot s = secrets.current();
            assertThat(s.get("username").revealAsString()).isEqualTo("orders_v1");
            assertThat(s.get("password").revealAsString()).isEqualTo("p1");
            assertThat(s.values().keySet()).isEqualTo(java.util.Set.of("username", "password"));
            assertThat(s.version()).startsWith("..2026_10_08_");
        }
    }

    @Test
    void followsTheAtomicSwapAndWipesTheOldVersion() throws Exception {
        Path dir = Files.createTempDirectory("mounted");
        kubeletWrite(dir, Map.of("password", "p1"));
        try (MountedSecrets secrets = new MountedSecrets(dir)) {
            Secret old = secrets.current().get("password");
            CountDownLatch changed = new CountDownLatch(1);
            AtomicReference<String> seen = new AtomicReference<>();
            secrets.follow(Duration.ofMillis(200), snap -> {
                seen.set(snap.get("password").revealAsString());
                changed.countDown();
            });
            kubeletWrite(dir, Map.of("password", "p2"));
            assertThat(changed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get()).isEqualTo("p2");
            assertThat(secrets.current().get("password").revealAsString()).isEqualTo("p2");
            Thread.sleep(100);
            assertThat(old.isDestroyed()).isTrue();
        }
    }

    @Test
    void aPlainDirectoryWorksToo() throws IOException {
        Path dir = Files.createTempDirectory("plain");
        Files.writeString(dir.resolve("api-key"), "k1");
        try (MountedSecrets secrets = new MountedSecrets(dir)) {
            assertThat(secrets.current().get("api-key").revealAsString()).isEqualTo("k1");
            assertThat(secrets.current().version()).startsWith("content:");
        }
    }
}
