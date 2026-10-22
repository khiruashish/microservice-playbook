package dev.foodfleet.ch54.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads a ConfigMap or Secret mounted as a directory (one file per key, like Spring Boot's {@code configtree:}) and
 * notices when Kubernetes updates it. The kubelet doesn't rewrite the files: it writes a new timestamped directory and
 * swaps the {@code ..data} symlink to it, atomically. So this watches the content of the keys, by polling, and never
 * holds file handles across the swap. Values mounted with {@code subPath} and environment variables are never
 * updated; only a restart changes them.
 */
public final class MountedConfig implements AutoCloseable {

    private final Path dir;
    private final ScheduledExecutorService poller;
    private volatile Map<String, String> values;

    private MountedConfig(Path dir, Map<String, String> initial, ScheduledExecutorService poller) {
        this.dir = dir;
        this.values = initial;
        this.poller = poller;
    }

    /** Reads the directory once; nothing changes afterwards (Spring Boot's configtree behaviour). */
    public static Map<String, String> readOnce(Path dir) throws IOException {
        Map<String, String> m = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return m;
        }
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("..") || Files.isDirectory(p)) {
                    continue;                                   // ..data, ..2026_10_15_... : the kubelet's own entries
                }
                try {
                    m.put(name, Files.readString(p).strip());
                } catch (NoSuchFileException e) {
                    // swapped away between list and read: the next poll sees the new set
                }
            }
        }
        return m;
    }

    /** Reads now, then every {@code every}; {@code onChange} gets the whole new map whenever any key changes. */
    public static MountedConfig watch(Path dir, long every, TimeUnit unit, Consumer<Map<String, String>> onChange) throws IOException {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mounted-config");
            t.setDaemon(true);
            return t;
        });
        MountedConfig c = new MountedConfig(dir, readOnce(dir), ex);
        ex.scheduleWithFixedDelay(() -> {
            try {
                Map<String, String> now = readOnce(dir);
                if (!Objects.equals(now, c.values)) {
                    c.values = now;
                    onChange.accept(now);
                }
            } catch (IOException e) {
                // keep the last good values
            }
        }, every, every, unit);
        return c;
    }

    public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public Map<String, String> values() {
        return values;
    }

    public Path dir() {
        return dir;
    }

    @Override
    public void close() {
        poller.shutdownNow();
    }
}
