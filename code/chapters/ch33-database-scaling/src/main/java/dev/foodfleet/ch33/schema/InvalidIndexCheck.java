package dev.foodfleet.ch33.schema;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reports indexes left INVALID by a failed CREATE INDEX CONCURRENTLY. Such an index is updated on every write but
 * never used by the planner, and CREATE INDEX ... IF NOT EXISTS treats it as present, so nothing else notices.
 */
@Component
public class InvalidIndexCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvalidIndexCheck.class);

    static final String INVALID = """
            SELECT i.indexrelid::regclass::text
              FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE NOT i.indisvalid AND n.nspname = current_schema()
             ORDER BY 1""";

    private final JdbcClient jdbc;

    public InvalidIndexCheck(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> invalidIndexes() {
        return jdbc.sql(INVALID).query(String.class).list();
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String index : invalidIndexes()) {
            log.error("index {} is INVALID (a concurrent build failed): DROP INDEX CONCURRENTLY {} and build it again",
                    index, index);
        }
    }
}
