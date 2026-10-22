import java.io.IOException;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Real PostgreSQL 16 clusters for the chapter's experiments: a primary, streaming standbys built with pg_basebackup,
 * WAL archiving and restores, started and killed with pg_ctl. Distance between "regions" is Toxiproxy latency on the
 * replication connection. Environment: PG_BIN (default: pg_config --bindir), TOXIPROXY_BIN; when run as root, the
 * PostgreSQL commands run as the postgres user.
 */
final class Lab {

    private Lab() {
    }

    static final Path BASE = Path.of(System.getProperty("c46.dir", "/tmp/c46pg"));
    static final String PG_BIN = System.getenv().getOrDefault("PG_BIN", "/usr/lib/postgresql/16/bin");
    static final boolean ROOT = "root".equals(System.getProperty("user.name"));

    static String run(String... cmd) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>();
        if (ROOT) {
            Collections.addAll(full, "runuser", "-u", "postgres", "--");
        }
        Collections.addAll(full, cmd);
        Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException(String.join(" ", cmd) + " failed:\n" + out);
        }
        return out;
    }

    static String pg(String tool) {
        return PG_BIN + "/" + tool;
    }

    static void reset() throws IOException, InterruptedException {
        if (Files.exists(BASE)) {
            for (Path d : Files.list(BASE).toList()) {
                if (Files.exists(d.resolve("postmaster.pid"))) {
                    try {
                        run(pg("pg_ctl"), "-D", d.toString(), "-m", "immediate", "stop");
                    } catch (IOException ignored) {
                        // already gone
                    }
                }
            }
            new ProcessBuilder("rm", "-rf", BASE.toString()).start().waitFor();
        }
        Files.createDirectories(BASE);
        if (ROOT) {
            new ProcessBuilder("chown", "postgres", BASE.toString()).start().waitFor();
        }
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** A PostgreSQL instance in BASE/name. */
    record Node(String name, int port) {
        Path dir() {
            return BASE.resolve(name);
        }

        void start() throws IOException, InterruptedException {
            run(pg("pg_ctl"), "-D", dir().toString(), "-l", BASE.resolve(name + ".log").toString(), "-w", "-t", "120", "start");
        }

        /** Power off: no checkpoint, no goodbye to the standbys. */
        void crash() throws IOException, InterruptedException {
            run(pg("pg_ctl"), "-D", dir().toString(), "-m", "immediate", "stop");
        }

        Pg connect() throws IOException {
            return new Pg("127.0.0.1", port, "postgres", "", "postgres", 5000);
        }

        void sql(String... statements) throws IOException {
            try (Pg c = connect()) {
                for (String s : statements) {
                    c.query(s);
                }
            }
        }

        String value(String query) throws IOException {
            try (Pg c = connect()) {
                return c.query(query).get(0).get(0);
            }
        }

        void conf(String settings) throws IOException {
            Files.writeString(dir().resolve("postgresql.conf"), "\n" + settings + "\n", java.nio.file.StandardOpenOption.APPEND);
        }
    }

    static Node primary(String name, String extraConf) throws IOException, InterruptedException {
        Node n = new Node(name, freePort());
        run(pg("initdb"), "-D", n.dir().toString(), "-A", "trust", "-U", "postgres", "--no-sync");
        n.conf("port = " + n.port() + "\nlisten_addresses = '127.0.0.1'\nunix_socket_directories = '" + BASE + "'\n"
                + "wal_level = replica\nmax_wal_senders = 10\nfsync = on\n" + extraConf);
        Files.writeString(n.dir().resolve("pg_hba.conf"), "\nhost replication all 127.0.0.1/32 trust\n",
                java.nio.file.StandardOpenOption.APPEND);
        n.start();
        return n;
    }

    /** A streaming standby of {@code primary}, replicating through {@code viaPort} (a Toxiproxy proxy, or the primary). */
    static Node standby(String name, int viaPort, String applicationName) throws IOException, InterruptedException {
        Node n = new Node(name, freePort());
        run(pg("pg_basebackup"), "-h", "127.0.0.1", "-p", Integer.toString(viaPort), "-U", "postgres",
                "-D", n.dir().toString(), "-R", "-X", "stream", "-c", "fast");
        n.conf("port = " + n.port());
        // pg_basebackup -R wrote a primary_conninfo into postgresql.auto.conf, which is read last: append there
        Files.writeString(n.dir().resolve("postgresql.auto.conf"), "\nprimary_conninfo = 'host=127.0.0.1 port=" + viaPort
                + " user=postgres application_name=" + applicationName + "'\n", java.nio.file.StandardOpenOption.APPEND);
        n.start();
        return n;
    }

    // ------------------------------------------------------------------------------------------------ Toxiproxy

    static final class Toxiproxy implements AutoCloseable {
        final Process process;
        final int port;
        final HttpClient http = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();

        Toxiproxy() throws Exception {
            port = freePort();
            process = new ProcessBuilder(System.getenv().getOrDefault("TOXIPROXY_BIN", "toxiproxy-server"),
                    "-host", "127.0.0.1", "-port", Integer.toString(port))
                    .redirectErrorStream(true).redirectOutput(BASE.resolve("toxiproxy.log").toFile()).start();
            for (int i = 0; i < 100; i++) {
                try {
                    new java.net.Socket("127.0.0.1", port).close();
                    return;
                } catch (IOException e) {
                    Thread.sleep(50);
                }
            }
            throw new IllegalStateException("toxiproxy did not start");
        }

        /** A proxy to {@code upstreamPort} with {@code oneWayMillis} of latency in each direction; returns its port. */
        int link(String name, int upstreamPort, long oneWayMillis) throws Exception {
            int listen = freePort();
            post("/proxies", "{\"name\":\"" + name + "\",\"listen\":\"127.0.0.1:" + listen + "\",\"upstream\":\"127.0.0.1:"
                    + upstreamPort + "\",\"enabled\":true}");
            if (oneWayMillis > 0) {
                for (String stream : List.of("upstream", "downstream")) {
                    post("/proxies/" + name + "/toxics", "{\"name\":\"lat-" + stream + "\",\"type\":\"latency\",\"stream\":\""
                            + stream + "\",\"attributes\":{\"latency\":" + oneWayMillis + ",\"jitter\":0}}");
                }
            }
            return listen;
        }

        /** Cuts the link: open connections are dropped with whatever was still in flight. */
        void cut(String name) throws Exception {
            post("/proxies/" + name, "{\"enabled\":false}");
        }

        private void post(String path, String body) throws Exception {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException(path + " -> " + r.statusCode() + " " + r.body());
            }
        }

        @Override
        public void close() {
            process.destroy();
        }
    }

    // ------------------------------------------------------------------------------------------------ stats

    static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return -1;
        }
        List<Long> s = new ArrayList<>(values);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }
}
