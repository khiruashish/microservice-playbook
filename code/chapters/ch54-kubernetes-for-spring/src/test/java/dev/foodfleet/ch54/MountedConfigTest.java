package dev.foodfleet.ch54;

import dev.foodfleet.ch54.config.MountedConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MountedConfigTest {

    /** What the kubelet's atomic writer does: a new timestamped directory, then ..data swapped to it. */
    static void kubeletWrite(Path dir, Map<String, String> data, String stamp) throws Exception {
        Path ts = dir.resolve("..2026_10_15_" + stamp);
        Files.createDirectories(ts);
        for (Map.Entry<String, String> e : data.entrySet()) {
            Files.writeString(ts.resolve(e.getKey()), e.getValue());
        }
        Path tmp = dir.resolve("..data_tmp");
        Files.deleteIfExists(tmp);
        Files.createSymbolicLink(tmp, ts.getFileName());
        Files.move(tmp, dir.resolve("..data"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        for (String key : data.keySet()) {
            Path link = dir.resolve(key);
            if (!Files.isSymbolicLink(link)) {
                Files.createSymbolicLink(link, Path.of("..data").resolve(key));
            }
        }
    }

    @Test
    void readsKeysAndSkipsTheKubeletsOwnEntries() throws Exception {
        Path dir = Files.createTempDirectory("cm");
        kubeletWrite(dir, Map.of("discount", "10\n", "currency", "INR"), "01");
        assertThat(MountedConfig.readOnce(dir)).isEqualTo(Map.of("currency", "INR", "discount", "10"));
    }

    @Test
    void aMissingDirectoryIsEmpty() throws Exception {
        assertThat(MountedConfig.readOnce(Path.of("/nonexistent/menu-config")).isEmpty()).isTrue();
    }

    @Test
    void seesTheSymlinkSwapAndReportsTheChangeOnce() throws Exception {
        Path dir = Files.createTempDirectory("cm");
        kubeletWrite(dir, Map.of("discount", "10"), "01");
        List<Map<String, String>> changes = new CopyOnWriteArrayList<>();
        try (MountedConfig c = MountedConfig.watch(dir, 50, TimeUnit.MILLISECONDS, changes::add)) {
            assertThat(c.get("discount", "?")).isEqualTo("10");
            kubeletWrite(dir, Map.of("discount", "15"), "02");
            for (int i = 0; i < 100 && changes.isEmpty(); i++) {
                Thread.sleep(20);
            }
            Thread.sleep(200);
            assertThat(c.get("discount", "?")).isEqualTo("15");
            assertThat(changes.size()).isEqualTo(1);
        }
    }

    @Test
    void aValueReadOnceDoesNotChange() throws Exception {
        Path dir = Files.createTempDirectory("cm");
        kubeletWrite(dir, Map.of("discount", "10"), "01");
        Map<String, String> once = MountedConfig.readOnce(dir);
        kubeletWrite(dir, Map.of("discount", "15"), "02");
        assertThat(once.get("discount")).isEqualTo("10");
        assertThat(MountedConfig.readOnce(dir).get("discount")).isEqualTo("15");
    }
}
