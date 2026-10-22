package dev.foodfleet.ch17.logship;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A log-shipping sidecar: the app writes to a file on a volume shared with this container; the
 * shipper tails it and sends complete lines in batches. It survives the three things that break
 * naive tailers:
 * <ul>
 *   <li><b>its own restart</b>: the position (file identity + byte offset) is saved after every batch
 *       the sink accepted, so it neither re-sends everything nor skips lines;</li>
 *   <li><b>rotation</b>: a new file under the same name (different identity), or the file truncated
 *       in place ({@code copytruncate}), restarts from offset 0;</li>
 *   <li><b>a slow or failing sink</b>: the offset only moves after a successful send (at-least-once),
 *       and partial last lines wait until the app finishes writing them.</li>
 * </ul>
 * In Kubernetes the usual choice is logging to stdout and a node-level agent (chapter 50); a sidecar
 * like this is for apps that can only write files, or logs that need per-pod handling.
 */
public final class LogShipper {

    private final Path logFile;
    private final Path positionFile;
    private final Sink sink;
    private final int batchSize;

    private record Position(String fileKey, long offset) {
    }

    public LogShipper(Path logFile, Path positionFile, Sink sink, int batchSize) {
        this.logFile = logFile;
        this.positionFile = positionFile;
        this.sink = sink;
        this.batchSize = batchSize;
    }

    /** Ships everything complete that's new. Returns the number of lines shipped. */
    public int pollOnce() throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(logFile, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return 0;                                     // app hasn't written yet, or mid-rotation
        }
        String key = String.valueOf(Objects.requireNonNullElse(attributes.fileKey(), "no-key")).replace(' ', '_');
        Position position = loadPosition();
        long offset = position.fileKey().equals(key) && position.offset() <= attributes.size() ? position.offset() : 0;

        int shipped = 0;
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(offset);
            byte[] rest = new byte[(int) Math.min(Integer.MAX_VALUE, file.length() - offset)];
            file.readFully(rest);
            List<String> batch = new ArrayList<>();
            int lineStart = 0;
            for (int i = 0; i < rest.length; i++) {
                if (rest[i] == '\n') {
                    batch.add(new String(rest, lineStart, i - lineStart, StandardCharsets.UTF_8).stripTrailing());
                    lineStart = i + 1;
                    if (batch.size() == batchSize) {
                        sink.send(batch);                // throws: offset not saved, same lines next time
                        shipped += batch.size();
                        savePosition(new Position(key, offset + lineStart));
                        batch = new ArrayList<>();
                    }
                }
            }
            if (!batch.isEmpty()) {
                sink.send(batch);
                shipped += batch.size();
                savePosition(new Position(key, offset + lineStart));
            } else if (offset != position.offset() || !key.equals(position.fileKey())) {
                savePosition(new Position(key, offset + lineStart));
            }
        }
        return shipped;
    }

    private Position loadPosition() throws IOException {
        if (!Files.exists(positionFile)) {
            return new Position("", 0);
        }
        String[] parts = Files.readString(positionFile).strip().split(" ");
        return parts.length == 2 ? new Position(parts[0], Long.parseLong(parts[1])) : new Position("", 0);
    }

    /** Write-then-rename, so a crash mid-write never leaves a corrupt position. */
    private void savePosition(Position position) throws IOException {
        Path tmp = positionFile.resolveSibling(positionFile.getFileName() + ".tmp");
        Files.writeString(tmp, position.fileKey() + " " + position.offset());
        try {
            Files.move(tmp, positionFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, positionFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
