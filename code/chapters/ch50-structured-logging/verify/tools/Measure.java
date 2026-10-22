import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import dev.foodfleet.ch50.masking.MaskingEncoder;
import dev.foodfleet.ch50.masking.Pii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Chapter 50's experiments, on Logback 1.5.38 and SLF4J 2.0.18 (built from their tags) and the OpenTelemetry Java
 * agent 2.32.0.
 * <ul>
 *   <li>{@code mdc}: which of nine places a request's work runs keeps its request ID and trace ID in the log line</li>
 *   <li>{@code async}: a burst into a slow appender, synchronous and through AsyncAppender's settings</li>
 *   <li>{@code cost}: nanoseconds and bytes per event for text, JSON and masked JSON</li>
 *   <li>{@code masking}: personal data in six places of a log event, and what each defence catches</li>
 * </ul>
 */
public final class Measure {

    static final Path DIR = Path.of(System.getProperty("c50.dir", "/tmp/c50"));

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        new ProcessBuilder("rm", "-rf", DIR.toString()).start().waitFor();
        Files.createDirectories(DIR);
        switch (args[0]) {
            case "mdc" -> mdc();
            case "async" -> async();
            case "cost" -> cost();
            case "masking" -> masking();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // =========================================================================================================== mdc

    static void mdc() throws Exception {
        int orders = 200;
        System.out.printf("order-service: %d requests, each with request.id in the MDC, logging once from each place its work runs%n", orders);
        for (boolean agent : new boolean[] {false, true}) {
            Path log = DIR.resolve(agent ? "agent.json" : "plain.json");
            int port;
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            }
            List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m"));
            if (agent) {
                cmd.add("-javaagent:" + System.getProperty("c50.agent"));
                cmd.addAll(List.of("-Dotel.traces.exporter=none", "-Dotel.metrics.exporter=none", "-Dotel.logs.exporter=none"));
            }
            cmd.addAll(List.of("-Dff.port=" + port, "-Dff.log=" + log, "-cp", System.getProperty("c50.cp"), "MdcService"));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(DIR.resolve("service.log").toFile()).start();
            HttpClient http = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
            for (int i = 0; i < 600; i++) {
                try {
                    http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).build(), HttpResponse.BodyHandlers.discarding());
                    break;
                } catch (IOException e) {
                    Thread.sleep(100);
                }
            }
            for (int n = 1; n <= orders; n++) {
                http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/orders?id=" + n)).build(),
                        HttpResponse.BodyHandlers.discarding());
            }
            p.destroy();
            p.waitFor(20, TimeUnit.SECONDS);

            Map<Integer, String> traceOf = new HashMap<>();
            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : Files.readAllLines(log)) {
                if (!line.startsWith("{")) {
                    continue;
                }
                Map<String, Object> e = Json.obj(Json.parse(line));
                if (!String.valueOf(e.get("message")).startsWith("order {}")) {
                    continue;
                }
                events.add(e);
                int n = Integer.parseInt(String.valueOf(Json.arr(e.get("arguments")).get(0)));
                if (String.valueOf(e.get("message")).endsWith("same thread")) {
                    Object t = Json.at(e, "mdc", "trace_id");
                    if (t != null) {
                        traceOf.put(n, (String) t);
                    }
                }
            }
            Map<String, int[]> byPlace = new LinkedHashMap<>();     // request.id right, missing, wrong; trace_id right, missing, wrong
            for (Map<String, Object> e : events) {
                int n = Integer.parseInt(String.valueOf(Json.arr(e.get("arguments")).get(0)));
                String place = String.valueOf(e.get("message")).substring("order {} ".length());
                int[] c = byPlace.computeIfAbsent(place, k -> new int[6]);
                Object rid = Json.at(e, "mdc", "request.id");
                c[rid == null ? 1 : rid.equals("r-" + n) ? 0 : 2]++;
                Object tid = Json.at(e, "mdc", "trace_id");
                c[3 + (tid == null ? 1 : tid.equals(traceOf.get(n)) ? 0 : 2)]++;
            }
            System.out.println(agent ? "-- with the OpenTelemetry agent (its Logback instrumentation adds trace_id to the MDC)" : "-- without an agent");
            System.out.println("where the line was logged                                  request.id: right  missing  WRONG" + (agent ? "    trace_id: right  missing  wrong" : ""));
            byPlace.forEach((place, c) -> System.out.printf("%-58s %17d %8d %6d%s%n", place, c[0], c[1], c[2],
                    agent ? String.format(" %16d %8d %6d", c[3], c[4], c[5]) : ""));
        }
        System.out.println();
    }

    // =========================================================================================================== async

    /** A sink that takes {@code micros} per event, like a slow disk or a log shipper's socket. */
    static final class SlowAppender extends AppenderBase<ILoggingEvent> {
        final long micros;
        final AtomicLong info = new AtomicLong();
        final AtomicLong warn = new AtomicLong();

        SlowAppender(long micros) {
            this.micros = micros;
        }

        @Override
        protected void append(ILoggingEvent e) {
            long end = System.nanoTime() + micros * 1000;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
            (e.getLevel() == Level.WARN ? warn : info).incrementAndGet();
        }
    }

    static void async() throws Exception {
        int threads = 8;
        int perThread = 2_000;
        System.out.printf("%d threads each log %,d events as fast as they can (every tenth is WARN, the rest INFO) into a sink that "
                + "takes 50 us per event%n", threads, perThread);
        System.out.println("appender                                                  INFO delivered   WARN delivered   log call p50   log call p99    slowest call   burst took");
        record V(String name, Integer queue, Integer threshold, Boolean neverBlock) {
        }
        for (V v : List.of(new V("synchronous (no AsyncAppender)", null, null, null),
                new V("AsyncAppender defaults (queue 256, discard below 20%)", 256, null, false),
                new V("AsyncAppender, discardingThreshold 0 (never discard)", 256, 0, false),
                new V("AsyncAppender, neverBlock true", 256, null, true),
                new V("AsyncAppender, queue 8192, neverBlock true", 8192, null, true))) {
            SlowAppender sink = new SlowAppender(50);
            sink.setContext(Lab.context());
            sink.start();
            AsyncAppender async = null;
            if (v.queue() != null) {
                async = new AsyncAppender();
                async.setContext(Lab.context());
                async.setQueueSize(v.queue());
                if (v.threshold() != null) {
                    async.setDiscardingThreshold(v.threshold());
                }
                async.setNeverBlock(v.neverBlock());
                async.setMaxFlushTime(60_000);
                async.addAppender(sink);
                async.start();
                Lab.root(async);
            } else {
                Lab.root(sink);
            }
            Logger log = LoggerFactory.getLogger("burst");
            ConcurrentLinkedQueue<Double> callMicros = new ConcurrentLinkedQueue<>();
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                Thread.ofPlatform().start(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < perThread; i++) {
                        long s = System.nanoTime();
                        if (i % 10 == 0) {
                            log.warn("payment retry {}", i);
                        } else {
                            log.info("order event {}", i);
                        }
                        callMicros.add((System.nanoTime() - s) / 1000.0);
                    }
                    done.countDown();
                });
            }
            long t0 = System.nanoTime();
            go.countDown();
            done.await();
            double burstMs = (System.nanoTime() - t0) / 1e6;
            if (async != null) {
                async.stop();                                         // drains what is queued
            }
            List<Double> calls = new ArrayList<>(callMicros);
            long sentWarn = threads * (long) (perThread / 10);
            long sentInfo = threads * (long) perThread - sentWarn;
            System.out.printf("%-57s %8.1f%% %15.1f%% %12.1f us %12.0f us %12.1f ms %10.0f ms%n", v.name(), 100.0 * sink.info.get() / sentInfo,
                    100.0 * sink.warn.get() / sentWarn, Lab.percentile(calls, 50), Lab.percentile(calls, 99),
                    Lab.percentile(calls, 100) / 1000, burstMs);
            sink.stop();
        }
        System.out.println();
    }

    // =========================================================================================================== cost

    static void cost() throws Exception {
        int threads = 4;
        int perThread = 100_000;
        Map<String, Supplier<Encoder<ILoggingEvent>>> encoders = new LinkedHashMap<>();
        encoders.put("text pattern", () -> Lab.pattern("%d{ISO8601} %-5level [%thread] %logger{36} %X{request.id} - %msg%n"));
        encoders.put("Logback JsonEncoder (defaults)", Lab::json);
        encoders.put("Logback JsonEncoder, trimmed (no context, sequence, nanos)", () -> {
            JsonEncoder e = new JsonEncoder();
            e.setContext(Lab.context());
            e.setWithContext(false);
            e.setWithSequenceNumber(false);
            e.setWithNanoseconds(false);
            e.setWithArguments(false);
            e.setWithFormattedMessage(true);
            e.start();
            return e;
        });
        encoders.put("trimmed JSON + MaskingEncoder", () -> {
            JsonEncoder e = new JsonEncoder();
            e.setContext(Lab.context());
            e.setWithContext(false);
            e.setWithSequenceNumber(false);
            e.setWithNanoseconds(false);
            e.setWithArguments(false);
            e.setWithFormattedMessage(true);
            MaskingEncoder m = new MaskingEncoder();
            m.setContext(Lab.context());
            m.setDelegate(e);
            m.start();
            return m;
        });
        System.out.printf("%d threads x %,d events to a file (buffered: immediateFlush false, and Logback's default true); "
                + "MDC request.id + tenant; one argument%n", threads, perThread);
        System.out.println("encoder                                                        bytes/event   ns/event (buffered)   ns/event (flush each)");
        for (Map.Entry<String, Supplier<Encoder<ILoggingEvent>>> e : encoders.entrySet()) {
            double[] ns = new double[2];
            long bytes = 0;
            for (int f = 0; f < 2; f++) {
                Path file = DIR.resolve("cost-" + f + ".log");
                FileAppender<ILoggingEvent> a = Lab.file(file, e.getValue().get(), f == 1);
                Lab.root(a);
                Logger log = LoggerFactory.getLogger("dev.foodfleet.order.OrderService");
                run(threads, perThread / 5, log);                       // warm-up
                List<Double> rounds = new ArrayList<>();
                for (int r = 0; r < 3; r++) {
                    long t0 = System.nanoTime();
                    run(threads, perThread, log);
                    rounds.add((System.nanoTime() - t0) / (double) (threads * (long) perThread));
                }
                a.stop();
                ns[f] = Lab.percentile(rounds, 50);
                long events = threads * (long) perThread * 3 + threads * (long) (perThread / 5);
                bytes = Files.size(file) / events;
            }
            System.out.printf("%-62s %11d %21.0f %23.0f%n", e.getKey(), bytes, ns[0], ns[1]);
        }
        System.out.println();
    }

    static void run(int threads, int perThread, Logger log) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread.ofPlatform().start(() -> {
                MDC.put("request.id", "r-" + id);
                MDC.put("tenant", "chain-7");
                for (int i = 0; i < perThread; i++) {
                    log.info("order {} accepted by the restaurant", 100_000 + i);
                }
                MDC.clear();
                done.countDown();
            });
        }
        done.await();
    }

    // =========================================================================================================== masking

    /** Where personal data enters a log event. */
    record Leak(String place, String secret) {
    }

    static final String EMAIL = "priya.sharma@example.com";
    static final String PHONE = "+91 98765 43210";
    static final String CARD = "4111 1111 1111 1111";
    static final String VPA = "priya.sharma@okhdfc";
    static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1LTQyIn0.c2lnbmF0dXJlLWRlbW8";

    record Customer(String name, String email, String phone) {
    }

    record SafeCustomer(String name, Pii email, Pii phone) {
    }

    static void masking() throws Exception {
        String[] secrets = {EMAIL, PHONE, CARD, VPA, JWT};
        Map<String, Boolean> approaches = new LinkedHashMap<>();
        approaches.put("no masking", false);
        approaches.put("Pii types at the source (where the code used them)", false);
        approaches.put("MaskingEncoder on every serialized line", true);
        approaches.put("both", true);
        String[] places = {"message text (concatenated)", "argument ({} placeholder)", "object argument (toString)",
                "key-value pair (SLF4J 2 fluent API)", "MDC value", "exception message"};
        System.out.println("each event carries an e-mail, a mobile number, a card number, a UPI address and a JWT in one place; "
                + "JSON lines by Logback's JsonEncoder (defaults); timed after 1,000 warm-up rounds");
        System.out.println("approach                                                 " + String.join(" | ", places) + "   ns/event  ns/event with a stack trace");
        for (Map.Entry<String, Boolean> a : approaches.entrySet()) {
            boolean source = a.getKey().startsWith("Pii") || a.getKey().equals("both");
            Path file = DIR.resolve("masking.log");
            Encoder<ILoggingEvent> enc = Lab.json();
            if (a.getValue()) {
                MaskingEncoder m = new MaskingEncoder();
                m.setContext(Lab.context());
                m.setDelegate(enc);
                m.start();
                enc = m;
            }
            FileAppender<ILoggingEvent> appender = Lab.file(file, enc, false);
            Lab.root(appender);
            Logger log = LoggerFactory.getLogger("dev.foodfleet.checkout");
            int rounds = 2_000;
            for (int r = 0; r < 1_000; r++) {                         // warm-up, into the same file
                for (int i = 0; i < places.length; i++) {
                    emit(log, i, source);
                }
            }
            long plain = 0;
            long withException = 0;
            for (int r = 0; r < rounds; r++) {
                for (int i = 0; i < places.length; i++) {
                    long t0 = System.nanoTime();
                    emit(log, i, source);
                    long took = System.nanoTime() - t0;
                    if (i == places.length - 1) {
                        withException += took;
                    } else {
                        plain += took;
                    }
                }
            }
            double ns = plain / (double) (rounds * (places.length - 1));
            double nsException = withException / (double) rounds;
            appender.stop();
            List<String> lines = Files.readAllLines(file);
            if (a.getKey().equals("no masking")) {
                Files.write(DIR.resolve("masking-raw.log"), lines.subList(0, Math.min(lines.size(), 600)));
            }
            StringBuilder row = new StringBuilder(String.format("%-56s", a.getKey()));
            for (int i = 0; i < places.length; i++) {
                int leaked = 0;
                String line = lines.get(i);                           // the first round's event for this place
                for (String s : secrets) {
                    if (line.contains(s) || line.contains(s.replace(" ", "")) || line.contains(s.replace("@", "\\u0040"))) {
                        leaked++;
                    }
                }
                row.append(String.format(" %s%d/5 leaked", i == 0 ? "" : "| ", leaked));
            }
            row.append(String.format("   %8.0f %10.0f", ns, nsException));
            System.out.println(row);
            if (a.getKey().equals("both")) {
                for (int i : new int[] {0, 4}) {
                    String l = lines.get(i);
                    int at = l.indexOf(i == 0 ? "\"message\"" : "\"mdc\"");
                    System.out.println("   e.g. " + l.substring(at, Math.min(l.length(), at + 190)));
                }
            }
        }
        // the masking function alone, on the lines just written (unmasked JSON from the "no masking" pass)
        List<String> corpus = Files.readAllLines(DIR.resolve("masking-raw.log"));
        double scanner = timeMask(corpus, dev.foodfleet.ch50.masking.Masker::mask);
        double regex = timeMask(corpus, RegexMasker::mask);
        long differ = corpus.stream().filter(l -> !dev.foodfleet.ch50.masking.Masker.mask(l).equals(RegexMasker.mask(l))).count();
        long timestamps = corpus.stream().filter(l -> RegexMasker.mask(l).contains("\"timestamp\":****")).count();
        System.out.printf("masking one serialized line (%d lines, avg %d chars): single-pass scanner %.1f us, six regexes %.1f us; "
                + "outputs differ on %d lines, %d of them because the regexes masked the event's epoch-millisecond timestamp "
                + "(13 digits, valid Luhn) as a card number%n", corpus.size(), (int) corpus.stream().mapToInt(String::length).average().orElse(0),
                scanner / 1000, regex / 1000, differ, timestamps);
        System.out.println();
    }

    static double timeMask(List<String> corpus, java.util.function.UnaryOperator<String> mask) {
        long sink = 0;
        for (int w = 0; w < 3; w++) {
            for (String l : corpus) {
                sink += mask.apply(l).length();
            }
        }
        long t0 = System.nanoTime();
        for (int r = 0; r < 5; r++) {
            for (String l : corpus) {
                sink += mask.apply(l).length();
            }
        }
        double ns = (System.nanoTime() - t0) / (5.0 * corpus.size());
        return sink == 42 ? -1 : ns;
    }

    static void emit(Logger log, int place, boolean source) {
        switch (place) {
            case 0 -> log.info("payment by " + EMAIL + " " + PHONE + " card " + CARD + " vpa " + VPA + " token " + JWT);
            case 1 -> {
                if (source) {
                    log.info("payment by {} {} card {} vpa {} token {}", Pii.email(EMAIL), Pii.phone(PHONE), "**** 1111",
                            Pii.vpa(VPA), "[jwt]");
                } else {
                    log.info("payment by {} {} card {} vpa {} token {}", EMAIL, PHONE, CARD, VPA, JWT);
                }
            }
            case 2 -> {
                if (source) {
                    log.info("customer {} card {} vpa {} token {}", new SafeCustomer("Priya", Pii.email(EMAIL), Pii.phone(PHONE)),
                            "**** 1111", Pii.vpa(VPA), "[jwt]");
                } else {
                    log.info("customer {} card {} vpa {} token {}", new Customer("Priya", EMAIL, PHONE), CARD, VPA, JWT);
                }
            }
            case 3 -> log.atInfo().setMessage("payment authorised")
                    .addKeyValue("email", source ? Pii.email(EMAIL) : EMAIL).addKeyValue("phone", source ? Pii.phone(PHONE) : PHONE)
                    .addKeyValue("card", source ? "**** 1111" : CARD).addKeyValue("vpa", source ? Pii.vpa(VPA) : VPA)
                    .addKeyValue("token", source ? "[jwt]" : JWT).log();
            case 4 -> {
                // MDC holds strings: a Pii value put there is masked only if the code calls toString() itself
                MDC.put("customer.email", EMAIL);
                MDC.put("customer.phone", PHONE);
                MDC.put("payment.card", CARD);
                MDC.put("payment.vpa", VPA);
                MDC.put("auth", JWT);
                log.info("payment authorised");
                MDC.clear();
            }
            default -> log.warn("payment failed", new IllegalStateException(
                    "gateway rejected " + CARD + " for " + EMAIL + " (" + PHONE + ", " + VPA + ") with " + JWT));
        }
    }
}
