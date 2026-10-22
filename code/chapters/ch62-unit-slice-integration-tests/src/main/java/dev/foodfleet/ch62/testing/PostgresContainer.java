package dev.foodfleet.ch62.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A PostgreSQL container for tests, doing what Testcontainers' {@code PostgreSQLContainer} does with the Docker CLI:
 * start the image on a random host port, wait until the database accepts connections, hand out a JDBC URL, and remove
 * the container afterwards. With {@code reuse}, an already running container with the same label is used instead, as
 * Testcontainers does with {@code testcontainers.reuse.enable=true}.
 */
public final class PostgresContainer implements AutoCloseable {

    public static final String LABEL = "dev.foodfleet.test=postgres";

    private final String id;
    private final int port;
    private final boolean reused;

    private PostgresContainer(String id, int port, boolean reused) {
        this.id = id;
        this.port = port;
        this.reused = reused;
    }

    public static PostgresContainer start(String image, boolean reuse, Duration timeout) {
        if (reuse) {
            String running = docker("ps", "-q", "--filter", "label=" + LABEL + "-reusable", "--filter", "status=running").strip();
            if (!running.isEmpty()) {
                String id = running.lines().findFirst().orElseThrow();
                return new PostgresContainer(id, port(id), true);
            }
        }
        List<String> run = new ArrayList<>(List.of("run", "-d", "-p", "127.0.0.1::5432", "--label", reuse ? LABEL + "-reusable" : LABEL,
                "-e", "POSTGRES_HOST_AUTH_METHOD=trust")); // the official image's switch for passwordless test databases
        if (!reuse) {
            run.add("--rm");
        }
        run.add(image);
        run.addAll(List.of("-c", "fsync=off")); // as Testcontainers' PostgreSQLContainer does: test data needn't survive a crash
        String id = docker(run.toArray(String[]::new)).strip();
        int port = port(id);
        PostgresContainer c = new PostgresContainer(id, port, false);
        c.awaitReady(timeout);
        return c;
    }

    /** Ready means a JDBC connection succeeds: the log line can come before the server listens, so test it directly. */
    void awaitReady(Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            try (Connection c = DriverManager.getConnection(jdbcUrl("postgres"), "postgres", "")) {
                if (c.isValid(1)) {
                    return;
                }
            } catch (SQLException e) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        throw new IllegalStateException("PostgreSQL container " + id + " not ready in " + timeout);
    }

    public String jdbcUrl(String database) {
        return "jdbc:postgresql://127.0.0.1:" + port + "/" + database;
    }

    public boolean reused() {
        return reused;
    }

    /** Removes the container unless it's a reusable one. */
    @Override
    public void close() {
        if (!reused && !docker("inspect", "-f", "{{index .Config.Labels \"dev.foodfleet.test\"}}", id).contains("reusable")) {
            docker("rm", "-f", id);
        }
    }

    static int port(String id) {
        String p = docker("port", id, "5432/tcp").strip().lines().findFirst().orElseThrow();
        return Integer.parseInt(p.substring(p.lastIndexOf(':') + 1));
    }

    public static String docker(String... args) {
        List<String> cmd = new ArrayList<>(List.of("docker"));
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) {
                throw new IllegalStateException("docker " + String.join(" ", args) + ": " + out);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
