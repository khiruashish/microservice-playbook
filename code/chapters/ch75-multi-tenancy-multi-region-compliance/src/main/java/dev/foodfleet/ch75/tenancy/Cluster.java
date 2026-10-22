package dev.foodfleet.ch75.tenancy;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A throwaway PostgreSQL instance: initdb into a directory, start with pg_ctl, connect with JDBC. When run as root, the
 * server tools run as the postgres user.
 */
public final class Cluster implements AutoCloseable {

    static final String BIN = System.getenv().getOrDefault("PG_BIN", "/usr/lib/postgresql/16/bin");
    static final boolean ROOT = "root".equals(System.getProperty("user.name"));

    public final Path dir;
    public final int port;

    private Cluster(Path dir, int port) {
        this.dir = dir;
        this.port = port;
    }

    public static Cluster start(Path dir, String extraConf) throws IOException, InterruptedException {
        if (Files.exists(dir)) {
            new ProcessBuilder("rm", "-rf", dir.toString()).start().waitFor();
        }
        Files.createDirectories(dir.getParent());
        if (ROOT) {
            new ProcessBuilder("chown", "postgres", dir.getParent().toString()).start().waitFor();
        }
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        run(BIN + "/initdb", "-D", dir.toString(), "-A", "trust", "-U", "postgres", "--no-sync");
        Files.writeString(dir.resolve("postgresql.conf"), "\nport = " + port + "\nlisten_addresses = '127.0.0.1'\nunix_socket_directories = '"
                + dir.getParent() + "'\n" + extraConf + "\n", StandardOpenOption.APPEND);
        Cluster c = new Cluster(dir, port);
        run(BIN + "/pg_ctl", "-D", dir.toString(), "-l", dir + ".log", "-w", "-t", "120", "start");
        return c;
    }

    public Connection connect(String database, String user) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/" + database + "?user=" + user);
    }

    public Connection connect(String database) throws SQLException {
        return connect(database, "postgres");
    }

    public static String run(String... cmd) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>();
        if (ROOT) {
            full.addAll(List.of("runuser", "-u", "postgres", "--"));
        }
        full.addAll(List.of(cmd));
        Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException(String.join(" ", cmd) + " failed:\n" + out);
        }
        return out;
    }

    public static String bin(String tool) {
        return BIN + "/" + tool;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        run(BIN + "/pg_ctl", "-D", dir.toString(), "-m", "fast", "-w", "stop");
    }
}
