package dev.foodfleet.ch04.zones.admin;

import dev.foodfleet.ch04.zones.zone.Zone;
import dev.foodfleet.ch04.zones.zone.ZoneStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XII. Admin processes: a one-off task that runs with the <em>same code, config and backing
 * services</em> as the web process, as a separate process, for example a Kubernetes Job using the
 * service's image. No SSH-ing into a pod to run scripts by hand.
 */
@Component
@ConditionalOnProperty(name = "foodfleet.admin.task", havingValue = "import-zones")
class ZoneImportTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ZoneImportTask.class);

    private final ZoneStore zones;
    private final Path file;
    private final ZoneCsvParser parser = new ZoneCsvParser();

    ZoneImportTask(ZoneStore zones, @Value("${foodfleet.admin.file}") Path file) {
        this.zones = zones;
        this.file = file;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read zone file " + file, e);
        }
        List<Zone> parsed = parser.parse(lines);
        int written = zones.upsertAll(parsed);
        log.info("zone.import.completed file={} rows={} written={} total={}", file, parsed.size(), written, zones.count());
    }
}
