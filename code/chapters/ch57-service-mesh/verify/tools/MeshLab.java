import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The two workloads of the service-mesh experiments, in one class.
 * <ul>
 *   <li>{@code serve}: menu-service. {@code /menu} answers with the pod's name; {@code FAIL=true} makes it answer 503
 *       (a bad pod). It listens on {@code BIND}: 0.0.0.0, or 127.0.0.1 so only its sidecar can reach it.</li>
 *   <li>{@code call}: the calling service, driven over {@code :9090}. {@code /run} sends open-loop load to a URL,
 *       over a pooled keep-alive client or a new connection per request, and reports per-pod counts per 10 s window and
 *       latency; {@code /probe} makes one request in a given way (plain, TLS with or without a client certificate) and
 *       reports what happened.</li>
 * </ul>
 */
public final class MeshLab {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("call")) {
            call();
        } else {
            serve();
        }
    }

    // ======================================================================================================= serve

    static void serve() throws IOException {
        String pod = System.getenv().getOrDefault("POD_NAME", "local");
        boolean fail = Boolean.parseBoolean(System.getenv().getOrDefault("FAIL", "false"));
        String bind = System.getenv().getOrDefault("BIND", "0.0.0.0");
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, 8080), 1024);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu", ex -> respond(ex, fail ? 503 : 200, fail ? "pod=" + pod + " error" : "pod=" + pod));
        server.createContext("/healthz", ex -> respond(ex, 200, "ok"));
        server.start();
        System.out.println("menu-service on " + bind + ":8080" + (fail ? " (failing)" : ""));
    }

    // ======================================================================================================== call

    static void call() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(9090), 64);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/run", ex -> {
            Map<String, String> q = query(ex);
            String out;
            try {
                out = run(q.get("url"), Integer.parseInt(q.get("rps")), Integer.parseInt(q.get("seconds")), Boolean.parseBoolean(q.get("keepalive")));
            } catch (Exception e) {
                out = "error " + e;
            }
            respond(ex, 200, out);
        });
        server.createContext("/probe", ex -> {
            Map<String, String> q = query(ex);
            respond(ex, 200, probe(q.get("mode"), q.get("host"), Integer.parseInt(q.get("port")), q.get("keystore")));
        });
        server.createContext("/handshakes", ex -> {
            Map<String, String> q = query(ex);
            respond(ex, 200, handshakes(q.get("host"), Integer.parseInt(q.get("plainPort")), Integer.parseInt(q.get("tlsPort")), q.get("keystore"),
                    Integer.parseInt(q.get("n"))));
        });
        server.start();
        System.out.println("caller ready on :9090");
    }

    record Done(double t, double ms, int status, String pod) {
    }

    static String run(String url, int rps, int seconds, boolean keepAlive) throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
        URI uri = URI.create(url);
        ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
        long t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long n = (long) rps * seconds;
            for (long i = 0; i < n; i++) {
                long wait = t0 + i * 1_000_000_000L / rps - System.nanoTime();
                if (wait > 0) {
                    TimeUnit.NANOSECONDS.sleep(wait);
                }
                pool.submit(() -> {
                    long s = System.nanoTime();
                    int status;
                    String body;
                    try {
                        if (keepAlive) {
                            HttpResponse<String> r = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                            status = r.statusCode();
                            body = r.body();
                        } else {
                            String[] r = once(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort(), uri.getRawPath());
                            status = Integer.parseInt(r[0]);
                            body = r[1];
                        }
                    } catch (Exception e) {
                        status = 0;
                        body = "";
                    }
                    String pod = body.startsWith("pod=") ? body.substring(4).split(" ")[0] : "-";
                    done.add(new Done((s - t0) / 1e9, (System.nanoTime() - s) / 1e6, status, pod));
                });
            }
        }
        client.close();
        // per-pod counts per 10 s window, then overall latency
        Map<String, Integer> ids = new TreeMap<>();
        List<Done> all = new ArrayList<>(done);
        all.stream().map(Done::pod).filter(p -> !p.equals("-")).sorted().distinct().forEach(p -> ids.put(p, ids.size() + 1));
        StringBuilder sb = new StringBuilder();
        for (int w = 0; w < seconds; w += 10) {
            final int from = w;
            Map<String, Integer> counts = new TreeMap<>();
            all.stream().filter(d -> d.t() >= from && d.t() < from + 10 && d.status() == 200).forEach(d -> counts.merge(d.pod(), 1, Integer::sum));
            sb.append("window ").append(from).append(' ');
            ids.forEach((pod, id) -> sb.append(counts.getOrDefault(pod, 0)).append(' '));
            sb.append('\n');
        }
        List<Double> ms = all.stream().filter(d -> d.status() == 200).map(Done::ms).sorted().toList();
        long failed = all.stream().filter(d -> d.status() != 200).count();
        sb.append(String.format("total %d ok %d failed %d p50 %.2f p99 %.2f p999 %.2f max %.2f pods %d%n", all.size(), ms.size(), failed, pct(ms, 0.5),
                pct(ms, 0.99), pct(ms, 0.999), ms.isEmpty() ? Double.NaN : ms.getLast(), ids.size()));
        Map<Integer, Long> statuses = new TreeMap<>();
        all.forEach(d -> statuses.merge(d.status(), 1L, Long::sum));
        sb.append("statuses ").append(statuses).append('\n');
        return sb.toString();
    }

    static double pct(List<Double> sorted, double q) {
        return sorted.isEmpty() ? Double.NaN : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(q * sorted.size()) - 1));
    }

    /** One request on a new connection: status and body. */
    static String[] once(String host, int port, String path) throws IOException {
        try (Socket s = new Socket()) {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), 2000);
            s.setSoTimeout(10_000);
            return exchange(s, path);
        }
    }

    static String[] exchange(Socket s, String path) throws IOException {
        s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: menu\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        if (r.length() < 12) {
            throw new IOException("empty response");
        }
        return new String[] {r.substring(9, 12), r.contains("\r\n\r\n") ? r.substring(r.indexOf("\r\n\r\n") + 4) : ""};
    }

    /** One request made a particular way; the outcome as text. */
    static String probe(String mode, String host, int port, String keystore) {
        try {
            if (mode.equals("plain")) {
                String[] r = once(host, port, "/menu");
                return "HTTP " + r[0] + " " + r[1].strip();
            }
            SSLContext ctx = context(mode.equals("tls-cert") ? keystore : null);
            try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket()) {
                s.connect(new InetSocketAddress(host, port), 2000);
                s.setSoTimeout(5000);
                s.startHandshake();
                String[] r = exchange(s, "/menu");
                return "HTTP " + r[0] + " " + r[1].strip();
            }
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            return e.getClass().getSimpleName() + ": " + m.lines().findFirst().orElse("");
        }
    }

    /** Median time for a request on a new connection, plain against mutual TLS (full handshake each time). */
    static String handshakes(String host, int plainPort, int tlsPort, String keystore, int n) {
        try {
            SSLContext ctx = context(keystore);
            List<Double> plain = new ArrayList<>();
            List<Double> tls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                long t = System.nanoTime();
                once(host, plainPort, "/menu");
                plain.add((System.nanoTime() - t) / 1e6);
                t = System.nanoTime();
                try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket()) {
                    s.setTcpNoDelay(true);                            // else a delayed ACK adds ~40 ms after the handshake
                    s.connect(new InetSocketAddress(host, tlsPort), 2000);
                    s.startHandshake();
                    exchange(s, "/menu");
                    s.getSession().invalidate();                      // no resumption: a full handshake every time
                }
                tls.add((System.nanoTime() - t) / 1e6);
            }
            plain.sort(null);
            tls.sort(null);
            return String.format("plain p50 %.2f p99 %.2f mtls p50 %.2f p99 %.2f", pct(plain, 0.5), pct(plain, 0.99), pct(tls, 0.5), pct(tls, 0.99));
        } catch (Exception e) {
            return "error " + e;
        }
    }

    /** Trusts the mesh CA (/certs/ca.crt); presents the client certificate in {@code keystore} (PKCS#12), if any. */
    static SSLContext context(String keystore) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        try (InputStream in = new FileInputStream("/certs/ca.crt")) {
            trust.setCertificateEntry("mesh-ca", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        KeyManagerFactory kmf = null;
        if (keystore != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(keystore)) {
                ks.load(in, "changeit".toCharArray());
            }
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "changeit".toCharArray());
        }
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String kv : q.split("&")) {
                int i = kv.indexOf('=');
                m.put(kv.substring(0, i), java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    static void respond(HttpExchange ex, int status, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        } finally {
            ex.close();
        }
    }
}
