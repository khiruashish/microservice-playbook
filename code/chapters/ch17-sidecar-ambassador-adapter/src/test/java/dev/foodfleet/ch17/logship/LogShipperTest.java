package dev.foodfleet.ch17.logship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogShipperTest {

    private final Path dir;
    private final Path log;
    private final Path position;
    private final List<String> shipped = new ArrayList<>();
    private boolean sinkDown;

    LogShipperTest() throws IOException {
        dir = Files.createTempDirectory("logs");
        log = dir.resolve("kitchen-service.log");
        position = dir.resolve("shipper.position");
    }

    private LogShipper shipper() {
        return new LogShipper(log, position, lines -> {
            if (sinkDown) {
                throw new IOException("collector unavailable");
            }
            shipped.addAll(lines);
        }, 2);
    }

    private void write(String text) throws IOException {
        Files.writeString(log, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void shipsCompleteLinesAndWaitsForTheRest() throws IOException {
        write("a\nb\nc\npartial");

        assertThat(shipper().pollOnce()).isEqualTo(3);
        write(" line\n");
        shipper().pollOnce();

        assertThat(shipped).containsExactly("a", "b", "c", "partial line");
    }

    @Test
    void aRestartedShipperResumesWhereItStopped() throws IOException {
        write("a\nb\n");
        shipper().pollOnce();
        write("c\n");

        shipper().pollOnce();                                // a new instance, same position file

        assertThat(shipped).containsExactly("a", "b", "c");
    }

    @Test
    void whenTheSinkFailsTheSameLinesAreSentLater() throws IOException {
        write("a\nb\n");
        sinkDown = true;
        assertThatThrownBy(() -> shipper().pollOnce()).isInstanceOf(IOException.class);

        sinkDown = false;
        shipper().pollOnce();

        assertThat(shipped).containsExactly("a", "b");
    }

    @Test
    void aRotatedFileIsReadFromTheStart() throws IOException {
        write("a\nb\n");
        shipper().pollOnce();
        Files.move(log, dir.resolve("kitchen-service.log.1"));     // logrotate: rename, then a new file
        write("c\n");

        shipper().pollOnce();

        assertThat(shipped).containsExactly("a", "b", "c");
    }

    @Test
    void aFileTruncatedInPlaceIsReadFromTheStart() throws IOException {
        write("first line\nsecond line\n");
        shipper().pollOnce();
        Files.writeString(log, "x\n");                             // copytruncate, then one new line

        shipper().pollOnce();

        assertThat(shipped).containsExactly("first line", "second line", "x");
    }
}
