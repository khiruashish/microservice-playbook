package dev.foodfleet.ch04.zones.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch04.zones.admin.ZoneCsvParser.InvalidZoneFileException;
import dev.foodfleet.ch04.zones.zone.InMemoryZoneStore;
import dev.foodfleet.ch04.zones.zone.Zone;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

/** XII. The admin task is ordinary, testable code, not a script someone pastes into a pod. */
class ZoneImportTaskTest {

    @TempDir
    Path dir;

    @Test
    void importsAndIsSafeToRunTwice() throws Exception {
        Path file = Files.writeString(dir.resolve("zones.csv"), """
                pincode,areaName,serviceable,deliveryFeePaise
                560001,MG Road,true,2500
                560066,Whitefield,true,4500
                """);
        InMemoryZoneStore store = new InMemoryZoneStore();
        ZoneImportTask task = new ZoneImportTask(store, file);

        task.run(new DefaultApplicationArguments());
        task.run(new DefaultApplicationArguments()); // a retried Job must not duplicate data

        assertThat(store.count()).isEqualTo(2);
        assertThat(store.find("560066")).contains(new Zone("560066", "Whitefield", true, 4_500L));
    }

    @Test
    void badFileChangesNothing() throws Exception {
        Path file = Files.writeString(dir.resolve("zones.csv"), """
                pincode,areaName,serviceable,deliveryFeePaise
                560001,MG Road,true,2500
                5600XX,Broken,true,2500
                """);
        InMemoryZoneStore store = new InMemoryZoneStore();

        assertThatThrownBy(() -> new ZoneImportTask(store, file).run(new DefaultApplicationArguments()))
                .isInstanceOf(InvalidZoneFileException.class);
        assertThat(store.count()).isZero();
    }
}
