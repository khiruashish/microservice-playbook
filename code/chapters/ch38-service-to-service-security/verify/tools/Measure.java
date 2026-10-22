import dev.foodfleet.ch38.authz.PeerPolicy;
import dev.foodfleet.ch38.pki.DevCa;
import dev.foodfleet.ch38.spiffe.RotatingKeyManager;
import dev.foodfleet.ch38.spiffe.SpiffeId;
import dev.foodfleet.ch38.spiffe.SpiffeTls;
import dev.foodfleet.ch38.spiffe.SpiffeTrustManager;
import dev.foodfleet.ch38.spiffe.SvidRejected;
import dev.foodfleet.ch38.spiffe.TrustBundles;
import dev.foodfleet.ch38.spiffe.X509Svid;
import dev.foodfleet.ch38.token.CertificateBinding;
import dev.foodfleet.ch38.token.ServiceTokens;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * Chapter 38 measurements, JDK only, everything on loopback with real TLS 1.3.
 *   handshake    cost of a connection: TCP, one-way TLS, mutual TLS (EC, RSA), resumed; and per request on a kept connection
 *   rejections   which certificates payment-service refuses, and at which layer
 *   resumption   what happens to a client whose SVID expired: resumed sessions, long-lived connections, the fixes
 *   rotation     short-lived SVIDs with and without rotation; CA rollover with and without an overlap
 *   binding      a stolen access token, bearer vs certificate-bound
 *   tokens       client-credentials tokens: per call, cached like Spring's client manager, keyed per user, single-flight
 */
public class Measure {

    static final String TD = "foodfleet.internal";
    static final SpiffeId ORDERS = SpiffeId.ofWorkload(TD, "orders", "order-service");
    static final SpiffeId RIDERS = SpiffeId.ofWorkload(TD, "riders", "rider-service");
    static final SpiffeId PAYMENTS = SpiffeId.ofWorkload(TD, "payments", "payment-service");
    static final SpiffeId SETTLEMENT = SpiffeId.ofWorkload(TD, "payments", "settlement-job");

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        switch (args[0]) {
            case "handshake" -> handshake();
            case "rejections" -> rejections();
            case "resumption" -> resumption(args.length > 1 && args[1].equals("stateful"));
            case "rotation" -> rotation();
            case "binding" -> binding();
            case "tokens" -> tokens();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------ handshake

    static final int FRAME = 512;

    static void echo(Socket s) {
        try (s) {
            s.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            byte[] buf = new byte[FRAME];
            while (true) {
                in.readFully(buf);
                out.write(buf);
                out.flush();
            }
        } catch (IOException ignored) {
            // client closed
        }
    }

    static int serve(ServerSocket server, Consumer<Socket> handler) {
        Thread.ofPlatform().daemon().start(() -> {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    Thread.ofVirtual().start(() -> handler.accept(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
        return server.getLocalPort();
    }

    interface Connector {
        Socket open() throws IOException;
    }

    static void roundTrip(Socket s, byte[] frame) throws IOException {
        s.getOutputStream().write(frame);
        s.getOutputStream().flush();
        new DataInputStream(s.getInputStream()).readFully(frame);
    }

    static double msPerConnection(Connector c, int n, boolean invalidateSession) throws IOException {
        byte[] frame = new byte[FRAME];
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            try (Socket s = c.open()) {
                roundTrip(s, frame);
                if (invalidateSession && s instanceof SSLSocket ssl) {
                    ssl.getSession().invalidate();      // no resumption: every connection is a full handshake
                }
            }
        }
        return (System.nanoTime() - start) / 1e6 / n;
    }

    static double usPerRequest(Connector c, int n) throws IOException {
        byte[] frame = new byte[FRAME];
        try (Socket s = c.open()) {
            for (int i = 0; i < 500; i++) {
                roundTrip(s, frame);
            }
            long start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                roundTrip(s, frame);
            }
            return (System.nanoTime() - start) / 1e3 / n;
        }
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    static SSLContext mtls(DevCa ca, SpiffeId id, TrustBundles bundles, boolean rsa) {
        var svid = ca.issue(new DevCa.Spec().uris(id.toString()).rsa(rsa)
                .validity(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1))));
        return SpiffeTls.context(new RotatingKeyManager(svid.privateKey(), svid.chain()),
                SpiffeTrustManager.anyMember(bundles, r -> { }));
    }

    static void handshake() throws Exception {
        DevCa ca = DevCa.create(TD, Duration.ofHours(2));
        TrustBundles bundles = TrustBundles.of(TD, ca.certificate());

        int tcpPort = serve(new ServerSocket(0, 512, java.net.InetAddress.getLoopbackAddress()), Measure::echo);

        SSLContext serverEc = mtls(ca, PAYMENTS, bundles, false);
        SSLContext serverRsa = mtls(ca, PAYMENTS, bundles, true);
        int mtlsEcPort = serve(SpiffeTls.mutualTlsServer(serverEc, 0), Measure::echo);
        int mtlsRsaPort = serve(SpiffeTls.mutualTlsServer(serverRsa, 0), Measure::echo);
        SSLServerSocket oneWay = (SSLServerSocket) serverEc.getServerSocketFactory().createServerSocket();
        oneWay.bind(new InetSocketAddress("127.0.0.1", 0));
        SSLParameters p = oneWay.getSSLParameters();
        p.setProtocols(new String[] {"TLSv1.3"});
        oneWay.setSSLParameters(p);
        int oneWayPort = serve(oneWay, Measure::echo);

        SSLContext clientEc = mtls(ca, ORDERS, bundles, false);
        SSLContext clientRsa = mtls(ca, ORDERS, bundles, true);
        SSLContext clientNoCert = SSLContext.getInstance("TLSv1.3");
        clientNoCert.init(null, new TrustManager[] {SpiffeTrustManager.anyMember(bundles, r -> { })}, null);

        record Mode(String name, Connector c, boolean fullHandshake) {
        }
        List<Mode> modes = List.of(
                new Mode("plain TCP (no TLS)", () -> new Socket("127.0.0.1", tcpPort), false),
                new Mode("TLS 1.3, server certificate only (EC P-256)", () -> SpiffeTls.connect(clientNoCert, oneWayPort), true),
                new Mode("mutual TLS 1.3, EC P-256 SVIDs", () -> SpiffeTls.connect(clientEc, mtlsEcPort), true),
                new Mode("mutual TLS 1.3, RSA-2048 SVIDs", () -> SpiffeTls.connect(clientRsa, mtlsRsaPort), true),
                new Mode("mutual TLS 1.3, EC, session resumed", () -> SpiffeTls.connect(clientEc, mtlsEcPort), false));

        int n = 200;
        int rounds = 5;
        System.out.println("each connection: connect, handshake, one 512-byte request and response, close; "
                + n + " connections x " + rounds + " rounds, median round; one client thread");
        System.out.printf("%-48s %14s %16s%n", "connection", "ms/connection", "connections/s");
        for (Mode m : modes) {
            msPerConnection(m.c(), 100, m.fullHandshake());          // warm-up
            List<Double> r = new ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                r.add(msPerConnection(m.c(), n, m.fullHandshake()));
            }
            double ms = median(r);
            System.out.printf("%-48s %14.3f %16.0f%n", m.name(), ms, 1000 / ms);
        }

        System.out.println();
        System.out.println("one kept-alive connection, 512-byte request and response, 20,000 round trips, median of 5");
        System.out.printf("%-48s %14s%n", "connection", "us/request");
        for (Mode m : List.of(modes.get(0), modes.get(2))) {
            List<Double> r = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                r.add(usPerRequest(m.c(), 20_000));
            }
            System.out.printf("%-48s %14.1f%n", m.name(), median(r));
        }
    }

    // ------------------------------------------------------------------ payment-service, for rejections/resumption

    /** payment-service's request handling on a mutual-TLS connection: one request line per round trip. */
    static final class PaymentService {
        final PeerPolicy policy = PeerPolicy.builder()
                .allow("capture", "POST", "/payments/*/capture", ORDERS)
                .allow("settle", "POST", "/internal/settlements/run", SETTLEMENT)
                .build();
        final LinkedBlockingQueue<String> handshakeFailures = new LinkedBlockingQueue<>();
        final boolean checkEachRequest;

        PaymentService(boolean checkEachRequest) {
            this.checkEachRequest = checkEachRequest;
        }

        void handle(Socket socket) {
            SSLSocket s = (SSLSocket) socket;
            try (s) {
                try {
                    s.startHandshake();
                } catch (IOException e) {
                    handshakeFailures.add(e.getMessage());
                    return;
                }
                var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    String[] req = line.split(" ");
                    var peer = (X509Certificate) s.getSession().getPeerCertificates()[0];
                    String answer;
                    try {
                        SpiffeId id = checkEachRequest ? X509Svid.spiffeIdValidAt(peer, Instant.now()) : X509Svid.spiffeId(peer);
                        var d = policy.decide(id, req[0], req[1]);
                        answer = (d.allowed() ? "200 " : "403 ") + d.reason();
                    } catch (SvidRejected e) {
                        answer = "403 " + e.getMessage();
                    }
                    s.getOutputStream().write((answer + "\n").getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                }
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    static String request(SSLContext ctx, int port, String line) throws IOException {
        try (SSLSocket s = SpiffeTls.connect(ctx, port)) {
            s.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            String answer = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            if (answer == null) {
                throw new IOException("connection closed");
            }
            return answer;
        }
    }

    static SSLContext clientWith(DevCa.Credential c, TrustBundles bundles) {
        return SpiffeTls.context(new RotatingKeyManager(c.privateKey(), c.chain()),
                SpiffeTrustManager.expecting(bundles, PAYMENTS, r -> { }));
    }

    // ------------------------------------------------------------------ rejections

    static void rejections() throws Exception {
        DevCa ca = DevCa.create(TD, Duration.ofHours(2));
        DevCa partner = DevCa.create("partner.example", Duration.ofHours(2));
        DevCa rogue = DevCa.create(TD, Duration.ofHours(2));
        TrustBundles bundles = TrustBundles.of(TD, ca.certificate());
        var server = ca.issue(PAYMENTS.toString(), Duration.ofHours(1));
        var payments = new PaymentService(true);
        List<String> trustRejections = Collections.synchronizedList(new ArrayList<>());
        SSLContext sctx = SpiffeTls.context(new RotatingKeyManager(server.privateKey(), server.chain()),
                SpiffeTrustManager.anyMember(bundles, trustRejections::add));
        int port = serve(SpiffeTls.mutualTlsServer(sctx, 0), payments::handle);

        Instant now = Instant.now();
        String capture = "POST /payments/42/capture";
        SSLContext noCert = SSLContext.getInstance("TLSv1.3");
        noCert.init(null, new TrustManager[] {SpiffeTrustManager.expecting(bundles, PAYMENTS, r -> { })}, null);
        List<Case> cases = List.of(
                new Case("order-service SVID, capture", clientWith(ca.issue(ORDERS.toString(), Duration.ofMinutes(5)), bundles), capture),
                new Case("settlement-job SVID, run settlements", clientWith(ca.issue(SETTLEMENT.toString(), Duration.ofMinutes(5)), bundles), "POST /internal/settlements/run"),
                new Case("rider-service SVID (valid), capture", clientWith(ca.issue(RIDERS.toString(), Duration.ofMinutes(5)), bundles), capture),
                new Case("order-service SVID, run settlements", clientWith(ca.issue(ORDERS.toString(), Duration.ofMinutes(5)), bundles), "POST /internal/settlements/run"),
                new Case("order-service SVID, encoded path", clientWith(ca.issue(ORDERS.toString(), Duration.ofMinutes(5)), bundles), "POST /payments/42/%63apture"),
                new Case("no client certificate", noCert, capture),
                new Case("partner.example SVID (not federated)", clientWith(partner.issue("spiffe://partner.example/ns/couriers/sa/gateway", Duration.ofMinutes(5)), bundles), capture),
                new Case("order-service ID signed by another CA", clientWith(rogue.issue(ORDERS.toString(), Duration.ofMinutes(5)), bundles), capture),
                new Case("order-service SVID, expired", clientWith(ca.issue(new DevCa.Spec().uris(ORDERS.toString()).validity(now.minusSeconds(7200), now.minusSeconds(3600))), bundles), capture),
                new Case("two URI SANs", clientWith(ca.issue(new DevCa.Spec().uris(ORDERS.toString(), SETTLEMENT.toString())), bundles), capture),
                new Case("CA certificate as client certificate", clientWith(ca.issue(new DevCa.Spec().uris(ORDERS.toString()).ca(true)), bundles), capture),
                new Case("ordinary TLS certificate (DNS SAN only)", clientWith(ca.issue(new DevCa.Spec().dnsNames("order-service.orders.svc")), bundles), capture));

        // the same service behind a standard PKIX trust manager whose trust store holds FoodFleet's CA: what
        // server.ssl.client-auth=need with a trust store (Tomcat, Spring Boot SSL bundles) checks at the handshake
        var pkixPayments = new PaymentService(true);
        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("foodfleet-ca", ca.certificate());
        var tmf = javax.net.ssl.TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        SSLContext pkix = SSLContext.getInstance("TLSv1.3");
        pkix.init(new javax.net.ssl.KeyManager[] {new RotatingKeyManager(server.privateKey(), server.chain())},
                tmf.getTrustManagers(), null);
        int pkixPort = serve(SpiffeTls.mutualTlsServer(pkix, 0), pkixPayments::handle);

        System.out.println("A: SpiffeTrustManager at the handshake (any " + TD + " SVID), then PeerPolicy per request");
        System.out.println("B: standard PKIX trust manager with FoodFleet's CA in the trust store, then the same per-request checks");
        System.out.printf("%-42s %-32s %-58s %s%n", "client presents", "request", "A: SpiffeTrustManager", "B: PKIX trust manager");
        for (Case c : cases) {
            String a = outcome(c, port, payments, trustRejections);
            String b = outcome(c, pkixPort, pkixPayments, new ArrayList<>());
            System.out.printf("%-42s %-32s %-58s %s%n", c.name(), c.request(), shortOutcome(a), shortOutcome(b));
        }
    }

    record Case(String name, SSLContext client, String request) {
    }

    static String outcome(Case c, int port, PaymentService service, List<String> trustRejections) throws InterruptedException {
        service.handshakeFailures.clear();
        trustRejections.clear();
        try {
            return request(c.client(), port, c.request());
        } catch (IOException e) {
            String serverSaid = service.handshakeFailures.poll(2, TimeUnit.SECONDS);
            String why = !trustRejections.isEmpty() ? trustRejections.getFirst()
                    : serverSaid != null ? serverSaid.replaceFirst("^\\([a-z_]+\\) ", "") : e.getMessage();
            return "refused: " + why;
        }
    }

    static String shortOutcome(String s) {
        s = s.replace("spiffe://foodfleet.internal/ns/", "").replace("200 allowed by ", "200, rule ");
        s = s.replaceAll("^403 ([a-z-]+/sa/[a-z-]+) may not call .*", "403 by policy");
        s = s.replaceAll("^403 ambiguous path .*", "403 ambiguous path");
        s = s.replaceAll("more than one URI SAN .*", "more than one URI SAN");
        s = s.replaceAll(": signing certificates must not authenticate", "");
        s = s.replaceAll("no URI SAN: not an X.509-SVID", "no URI SAN");
        s = s.replaceAll("^refused: PKIX path (building|validation) failed: .*", "refused: PKIX path failed");
        return s.length() > 58 ? s.substring(0, 58) : s;
    }

    // ------------------------------------------------------------------ resumption

    static void resumption(boolean statefulOnly) throws Exception {
        DevCa ca = DevCa.create(TD, Duration.ofHours(2));
        TrustBundles bundles = TrustBundles.of(TD, ca.certificate());
        var server = ca.issue(PAYMENTS.toString(), Duration.ofHours(1));
        String capture = "POST /payments/42/capture";

        System.out.println("JDK " + System.getProperty("java.version") + "; server session tickets "
                + (statefulOnly ? "off (jdk.tls.server.enableSessionTicketExtension=false)" : "on (default)")
                + "; default session timeouts " + SSLContext.getDefault().getServerSessionContext().getSessionTimeout()
                + " s (server) / " + SSLContext.getDefault().getClientSessionContext().getSessionTimeout() + " s (client)");
        System.out.println("order-service's SVID expires 3 s after its first connection; it keeps calling: a new connection "
                + "every 250 ms, and one kept connection, until 6 s after expiry");
        record Setup(String name, boolean perRequestCheck, int serverTimeout, int clientTimeout) {
        }
        List<Setup> setups = statefulOnly
                ? List.of(new Setup("server session timeout 2 s", false, 2, 0))
                : List.of(
                        new Setup("JDK defaults", false, 0, 0),
                        new Setup("server session timeout 2 s", false, 2, 0),
                        new Setup("client session timeout 2 s", false, 0, 2),
                        new Setup("JDK defaults + validity checked per request", true, 0, 0));
        System.out.printf("%-46s %-36s %-30s%n", "setup", "new connections after expiry", "kept connection after expiry");
        for (Setup setup : setups) {
            var payments = new PaymentService(setup.perRequestCheck());
            SSLContext sctx = SpiffeTls.context(new RotatingKeyManager(server.privateKey(), server.chain()),
                    SpiffeTrustManager.anyMember(bundles, r -> { }));
            if (setup.serverTimeout() > 0) {
                sctx.getServerSessionContext().setSessionTimeout(setup.serverTimeout());
            }
            SSLServerSocket ss = SpiffeTls.mutualTlsServer(sctx, 0);
            int port = serve(ss, payments::handle);

            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(3);
            var shortLived = ca.issue(new DevCa.Spec().uris(ORDERS.toString()).validity(now.minusSeconds(60), expiry));
            SSLContext cctx = clientWith(shortLived, bundles);
            if (setup.clientTimeout() > 0) {
                cctx.getClientSessionContext().setSessionTimeout(setup.clientTimeout());
            }
            SSLSocket kept = SpiffeTls.connect(cctx, port);
            var keptIn = new BufferedReader(new InputStreamReader(kept.getInputStream(), StandardCharsets.UTF_8));
            kept.getOutputStream().write((capture + "\n").getBytes(StandardCharsets.UTF_8));
            kept.getOutputStream().flush();
            keptIn.readLine();

            int accepted = 0;
            int refused = 0;
            int forbidden = 0;
            double lastAccepted = -1;
            Instant stop = expiry.plusSeconds(6);
            while (Instant.now().isBefore(stop)) {
                double t = Duration.between(expiry, Instant.now()).toMillis() / 1000.0;
                String answer;
                try {
                    answer = request(cctx, port, capture);
                } catch (IOException e) {
                    answer = "refused";
                }
                if (t > 0) {
                    if (answer.startsWith("200")) {
                        accepted++;
                        lastAccepted = t;
                    } else if (answer.startsWith("403")) {
                        forbidden++;
                    } else {
                        refused++;
                    }
                }
                Thread.sleep(250);
            }
            kept.getOutputStream().write((capture + "\n").getBytes(StandardCharsets.UTF_8));
            kept.getOutputStream().flush();
            String keptAnswer = keptIn.readLine();
            kept.close();
            ss.close();
            String fresh = accepted + " accepted, " + forbidden + " 403, " + refused + " refused";
            System.out.printf("%-46s %-36s %-30s%n", setup.name(), fresh, shorten(keptAnswer));
        }
    }

    static String shorten(String answer) {
        if (answer == null) {
            return "closed";
        }
        if (answer.startsWith("200")) {
            return "200 accepted as order-service";
        }
        if (answer.contains("expired")) {
            return "403 SVID expired";
        }
        return answer.length() > 30 ? answer.substring(0, 30) : answer;
    }

    // ------------------------------------------------------------------ rotation

    static final class Workload {
        final RotatingKeyManager keys;
        final SpiffeTrustManager trust;
        final SpiffeId id;
        volatile DevCa issuer;

        Workload(SpiffeId id, DevCa issuer, TrustBundles bundles, Duration lifetime) {
            this.id = id;
            this.issuer = issuer;
            var svid = issuer.issue(id.toString(), lifetime);
            this.keys = new RotatingKeyManager(svid.privateKey(), svid.chain());
            this.trust = SpiffeTrustManager.anyMember(bundles, r -> { });
        }

        void rotate(Duration lifetime) {
            var svid = issuer.issue(new DevCa.Spec().uris(id.toString())
                    .validity(Instant.now().minusSeconds(1), Instant.now().plus(lifetime)));
            keys.update(svid.privateKey(), svid.chain());
        }
    }

    record Run(int ok, int failed, double firstFailureAt) {
    }

    static Run drive(SSLContext client, int port, Duration length) throws InterruptedException {
        int ok = 0;
        int failed = 0;
        double first = -1;
        long start = System.nanoTime();
        byte[] frame = new byte[FRAME];
        while (System.nanoTime() - start < length.toNanos()) {
            try (SSLSocket s = SpiffeTls.connect(client, port)) {
                roundTrip(s, frame);
                s.getSession().invalidate();          // full handshakes: the worst case for certificate lifetimes
                ok++;
            } catch (IOException e) {
                failed++;
                if (first < 0) {
                    first = (System.nanoTime() - start) / 1e9;
                }
            }
            Thread.sleep(20);
        }
        return new Run(ok, failed, first);
    }

    static void rotation() throws Exception {
        Duration life = Duration.ofSeconds(4);
        Duration run = Duration.ofSeconds(12);
        System.out.println("SVID lifetime " + life.toSeconds() + " s (SPIRE default: 1 h), rotation at half the lifetime "
                + "(SPIRE's default), " + run.toSeconds() + " s of new connections every 20 ms, full handshakes");
        System.out.printf("%-58s %8s %8s %16s%n", "scenario", "ok", "failed", "first failure");

        for (boolean rotate : new boolean[] {false, true}) {
            DevCa ca = DevCa.create(TD, Duration.ofHours(1));
            TrustBundles bundles = TrustBundles.of(TD, ca.certificate());
            Workload server = new Workload(PAYMENTS, ca, bundles, life);
            Workload client = new Workload(ORDERS, ca, bundles, life);
            ServerSocket ss = SpiffeTls.mutualTlsServer(SpiffeTls.context(server.keys, server.trust), 0);
            int port = serve(ss, Measure::echo);
            ScheduledExecutorService rotator = Executors.newSingleThreadScheduledExecutor();
            if (rotate) {
                rotator.scheduleAtFixedRate(() -> server.rotate(life), 2000, 2000, TimeUnit.MILLISECONDS);
                rotator.scheduleAtFixedRate(() -> client.rotate(life), 1300, 2000, TimeUnit.MILLISECONDS);
            }
            Run r = drive(SpiffeTls.context(client.keys, client.trust), port, run);
            rotator.shutdownNow();
            ss.close();
            System.out.printf("%-58s %8d %8d %16s%n",
                    rotate ? "SVIDs rotated at half-life (key manager updated)" : "SVIDs loaded once (SSLContext never updated)",
                    r.ok(), r.failed(), r.firstFailureAt() < 0 ? "-" : String.format("%.1f s", r.firstFailureAt()));
        }

        for (boolean overlap : new boolean[] {false, true}) {
            DevCa oldCa = DevCa.create(TD, Duration.ofHours(1));
            DevCa newCa = DevCa.create(TD, Duration.ofHours(1));
            TrustBundles before = TrustBundles.of(TD, oldCa.certificate());
            Workload server = new Workload(PAYMENTS, oldCa, before, life);
            Workload client = new Workload(ORDERS, oldCa, before, life);
            ServerSocket ss = SpiffeTls.mutualTlsServer(SpiffeTls.context(server.keys, server.trust), 0);
            int port = serve(ss, Measure::echo);
            ScheduledExecutorService rotator = Executors.newScheduledThreadPool(2);
            rotator.scheduleAtFixedRate(() -> server.rotate(life), 2000, 2000, TimeUnit.MILLISECONDS);
            rotator.scheduleAtFixedRate(() -> client.rotate(life), 1300, 2000, TimeUnit.MILLISECONDS);
            rotator.schedule(() -> {
                // the new CA starts signing; bundles change everywhere at once
                server.issuer = newCa;
                client.issuer = newCa;
                TrustBundles next = overlap ? TrustBundles.of(TD, oldCa.certificate(), newCa.certificate())
                        : TrustBundles.of(TD, newCa.certificate());
                server.trust.updateBundles(next);
                client.trust.updateBundles(next);
            }, 3500, TimeUnit.MILLISECONDS);
            Run r = drive(SpiffeTls.context(client.keys, client.trust), port, run);
            rotator.shutdownNow();
            ss.close();
            System.out.printf("%-58s %8d %8d %16s%n",
                    overlap ? "CA rollover at 3.5 s, bundles hold old + new CA" : "CA rollover at 3.5 s, bundles switched to the new CA",
                    r.ok(), r.failed(), r.firstFailureAt() < 0 ? "-" : String.format("%.1f s", r.firstFailureAt()));
        }
    }

    // ------------------------------------------------------------------ binding

    static void binding() throws Exception {
        DevCa ca = DevCa.create(TD, Duration.ofHours(2));
        TrustBundles bundles = TrustBundles.of(TD, ca.certificate());
        var orders = ca.issue(ORDERS.toString(), Duration.ofMinutes(10));
        var riders = ca.issue(RIDERS.toString(), Duration.ofMinutes(10));
        var server = ca.issue(PAYMENTS.toString(), Duration.ofMinutes(10));

        Map<String, Map<String, Object>> issued = new ConcurrentHashMap<>();
        String bearer = "bearer-" + UUID.randomUUID();
        String bound = "bound-" + UUID.randomUUID();
        issued.put(bearer, Map.of("sub", "order-service", "scope", "payments:capture"));
        issued.put(bound, Map.of("sub", "order-service", "scope", "payments:capture",
                "cnf", CertificateBinding.confirmation(orders.leaf())));

        SSLContext sctx = SpiffeTls.context(new RotatingKeyManager(server.privateKey(), server.chain()),
                SpiffeTrustManager.anyMember(bundles, r -> { }));
        int port = serve(SpiffeTls.mutualTlsServer(sctx, 0), sock -> {
            SSLSocket s = (SSLSocket) sock;
            try (s) {
                var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String token = in.readLine();
                boolean noCert = token.startsWith("no-cert ");
                token = token.replaceFirst("^no-cert ", "");
                X509Certificate peer = noCert ? null : (X509Certificate) s.getSession().getPeerCertificates()[0];
                var r = CertificateBinding.check(issued.get(token), peer);
                s.getOutputStream().write(((r.accepted() ? "accepted: " : "401 invalid_token: ") + r.reason() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        });

        System.out.println("order-service's access token for payment-service, presented by different connections");
        System.out.printf("%-14s %-58s %s%n", "token", "presented over", "payment-service");
        String[][] cases = {
            {"bearer", "order-service's mutual-TLS connection", ""},
            {"bearer", "rider-service's connection (token stolen from a log)", ""},
            {"bearer", "TLS ended at a proxy; application sees no certificate", "no-cert "},
            {"cert-bound", "order-service's mutual-TLS connection", ""},
            {"cert-bound", "rider-service's connection (token stolen from a log)", ""},
            {"cert-bound", "TLS ended at a proxy; application sees no certificate", "no-cert "},
        };
        for (String[] c : cases) {
            var who = c[1].startsWith("rider") ? riders : orders;
            String token = c[0].equals("bearer") ? bearer : bound;
            try (SSLSocket s = SpiffeTls.connect(clientWith(who, bundles), port)) {
                s.getOutputStream().write((c[2] + token + "\n").getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                String answer = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                System.out.printf("%-14s %-58s %s%n", c[0], c[1], answer);
            }
        }
    }

    // ------------------------------------------------------------------ tokens

    /** Spring Security's AuthorizedClientServiceOAuth2AuthorizedClientManager + client-credentials provider, in miniature:
     *  load the stored token for (registration, principal); if missing or within the clock skew of expiry, fetch and save.
     *  No lock around load-check-fetch-save. */
    static final class StoreLikeSpring {
        final Map<String, ServiceTokens.Token> store = new ConcurrentHashMap<>();
        final Duration skew;
        final ServiceTokens.TokenEndpoint endpoint;

        StoreLikeSpring(ServiceTokens.TokenEndpoint endpoint, Duration skew) {
            this.endpoint = endpoint;
            this.skew = skew;
        }

        String token(String principal) throws IOException {
            ServiceTokens.Token t = store.get(principal);
            if (t == null || !Instant.now().isBefore(t.expiresAt().minus(skew))) {
                t = endpoint.fetch();
                store.put(principal, t);
            }
            return t.value();
        }
    }

    static void tokens() throws Exception {
        AtomicLong requests = new AtomicLong();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        int ttl = 3;
        HttpServer as = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 512);
        as.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        as.createContext("/oauth2/token", ex -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            requests.incrementAndGet();
            try {
                ex.getRequestBody().readAllBytes();
                // signing a JWT costs the authorization server about this much CPU per token (chapter 36)
                Thread.sleep(2);
                byte[] body = ("{\"access_token\":\"" + UUID.randomUUID() + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                        + ttl + "}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
                ex.close();
            }
        });
        as.start();
        URI tokenUri = URI.create("http://127.0.0.1:" + as.getAddress().getPort() + "/oauth2/token");
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        Pattern accessToken = Pattern.compile("\"access_token\":\"([^\"]+)\".*\"expires_in\":(\\d+)");
        ServiceTokens.TokenEndpoint endpoint = () -> {
            try {
                var res = http.send(HttpRequest.newBuilder(tokenUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=payments%3Acapture"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                Matcher m = accessToken.matcher(res.body());
                if (!m.find()) {
                    throw new IOException("bad token response " + res.body());
                }
                Instant now = Instant.now();
                return new ServiceTokens.Token(m.group(1), now, now.plusSeconds(Long.parseLong(m.group(2))));
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        };

        int threads = 64;
        Duration run = Duration.ofSeconds(9);
        System.out.println(threads + " request threads for " + run.toSeconds() + " s, each: get a token, then 2 ms of "
                + "downstream work; token lifetime " + ttl + " s; loopback token endpoint");
        System.out.printf("%-60s %10s %12s %12s %12s %12s%n", "strategy", "calls", "token reqs", "max in flight",
                "p50 get us", "p99 get us");

        StoreLikeSpring springLike = new StoreLikeSpring(endpoint, Duration.ofMillis(ttl * 1000 / 5));
        StoreLikeSpring springPerUser = new StoreLikeSpring(endpoint, Duration.ofMillis(ttl * 1000 / 5));
        ServiceTokens singleFlight = new ServiceTokens(endpoint, Clock.systemUTC(), 0.8);
        AtomicLong userCounter = new AtomicLong();
        record Strategy(String name, ThrowingSupplier supplier) {
        }
        List<Strategy> strategies = List.of(
                new Strategy("fetch a token for every call", () -> endpoint.fetch().value()),
                new Strategy("Spring-style store, skew 20% of lifetime, one principal", () -> springLike.token("order-service")),
                new Strategy("Spring-style store keyed by the user (5,000 users)",
                        () -> springPerUser.token("user-" + (userCounter.incrementAndGet() % 5000))),
                new Strategy("ServiceTokens: one renewal at 80% of lifetime", singleFlight::token));
        for (Strategy s : strategies) {
            requests.set(0);
            maxInFlight.set(0);
            AtomicLong calls = new AtomicLong();
            List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
            long end = System.nanoTime() + run.toNanos();
            List<Thread> ts = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ts.add(Thread.ofVirtual().start(() -> {
                    while (System.nanoTime() < end) {
                        long t0 = System.nanoTime();
                        try {
                            s.supplier().get();
                        } catch (IOException e) {
                            continue;
                        }
                        latencies.add((System.nanoTime() - t0) / 1000);
                        calls.incrementAndGet();
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }));
            }
            for (Thread t : ts) {
                t.join();
            }
            long[] l = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            System.out.printf("%-60s %10d %12d %12d %12d %12d%n", s.name(), calls.get(), requests.get(), maxInFlight.get(),
                    l[l.length / 2], l[(int) (l.length * 0.99)]);
        }
        System.out.println("stored tokens, keyed by user: " + springPerUser.store.size() + "; one principal: "
                + springLike.store.size());
        as.stop(0);
    }

    @FunctionalInterface
    interface ThrowingSupplier {
        String get() throws IOException;
    }
}
