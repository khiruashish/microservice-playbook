package dev.foodfleet.ch39.secret;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A Kubernetes Secret mounted as a volume, followed as it changes. The kubelet writes each version into a new
 * timestamped directory and swaps the {@code ..data} symlink to it atomically; the visible files are symlinks through
 * {@code ..data}. So a reader must re-resolve the names (not keep an open file or a resolved path, which keep
 * pointing at the old version) and must treat the {@code ..data} target, not file timestamps, as the version.
 * Changes are noticed by a directory watch and, because watches are not reliable on every volume type, by polling.
 */
public final class MountedSecrets implements AutoCloseable {

    /** One consistent version of the volume: every key read from the same {@code ..data} target. */
    public record Snapshot(String version, Map<String, Secret> values) {
        public Secret get(String key) {
            Secret s = values.get(key);
            if (s == null) {
                throw new IllegalArgumentException("no key " + key + " in mounted secret");
            }
            return s;
        }
    }

    private final Path dir;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mounted-secrets");
        t.setDaemon(true);
        return t;
    });
    private WatchService watcher;

    public MountedSecrets(Path dir) {
        this.dir = dir;
        current.set(read(dir));
    }

    public Snapshot current() {
        return current.get();
    }

    /**
     * Calls {@code onChange} with each new version (from the watcher or the poller thread). The previous snapshot's
     * secrets are destroyed after the callback returns: copy what you still need inside the callback.
     */
    public void follow(Duration pollInterval, Consumer<Snapshot> onChange) {
        poller.scheduleWithFixedDelay(() -> refresh(onChange), pollInterval.toMillis(), pollInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        try {
            watcher = dir.getFileSystem().newWatchService();
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            Thread.ofPlatform().daemon().name("mounted-secrets-watch").start(() -> {
                try {
                    while (true) {
                        var key = watcher.take();
                        key.pollEvents();
                        key.reset();
                        poller.execute(() -> refresh(onChange));
                    }
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    // closed
                }
            });
        } catch (IOException e) {
            // no watch support on this file system: polling alone
        }
    }

    private void refresh(Consumer<Snapshot> onChange) {
        Snapshot before = current.get();
        Snapshot after;
        try {
            after = read(dir);
        } catch (UncheckedIOException e) {
            return;                                   // mid-swap or unreadable: keep the last good version
        }
        if (after.version().equals(before.version())) {
            after.values().values().forEach(Secret::destroy);
            return;
        }
        current.set(after);
        try {
            onChange.accept(after);
        } finally {
            before.values().values().forEach(Secret::destroy);
        }
    }

    static Snapshot read(Path dir) {
        try {
            Path data = dir.resolve("..data");
            String version = Files.isSymbolicLink(data) ? Files.readSymbolicLink(data).toString() : null;
            Path base = version != null ? dir.resolve(version) : dir;   // read every key from one version
            Map<String, Secret> values = new TreeMap<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.toList()) {
                    String name = p.getFileName().toString();
                    if (name.startsWith("..") || Files.isDirectory(p)) {
                        continue;
                    }
                    byte[] raw = Files.readAllBytes(base.resolve(name));
                    values.put(name, Secret.of(raw));
                    java.util.Arrays.fill(raw, (byte) 0);
                }
            }
            if (version == null) {
                // not the kubelet's layout: the content is the version
                StringBuilder sb = new StringBuilder();
                for (var e : values.entrySet()) {
                    char[] v = e.getValue().reveal();
                    sb.append(e.getKey()).append('=').append(java.util.Arrays.hashCode(v)).append(';');
                    java.util.Arrays.fill(v, '\0');
                }
                version = "content:" + sb;
            }
            return new Snapshot(version, Map.copyOf(values));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        poller.shutdownNow();
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException ignored) {
            }
        }
    }
}
