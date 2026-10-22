package dev.foodfleet.ch26.experiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many open SSE streams one server process can hold, and what they cost. Starts
 * {@link CapacityServer} in a separate JVM (thread-per-request on 200 platform threads, or on
 * virtual threads), opens N streams to it, broadcasts one event, and measures how many streams
 * receive it and how quickly, plus the server's resident memory.
 */
public final class ConnectionCapacity {

    public record Result(String executor, int clients, int streamsOpen, int received, double p50Ms, double p99Ms,
                         long rssBeforeKb, long rssAfterKb, long heapAfterKb) {
        public double kbPerStream() {
            return streamsOpen == 0 ? 0 : (rssAfterKb - rssBeforeKb) / (double) streamsOpen;
        }
    }

    public static Result run(String executor, int clients) throws Exception {
        String cp = System.getProperty("java.class.path");
        Process server = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java"),
                "-Xmx1g", "-cp", cp, CapacityServer.class.getName(), executor).redirectErrorStream(true).start();
        try {
            BufferedReader serverOut = new BufferedReader(new InputStreamReader(server.getInputStream()));
            String first;
            do {                                                          // skip JVM banners such as JAVA_TOOL_OPTIONS
                first = serverOut.readLine();
            } while (first != null && !first.trim().matches("\\d+ \\d+"));
            int port = Integer.parseInt(first.trim().split(" ")[0]);
            int adminPort = Integer.parseInt(first.trim().split(" ")[1]);
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + adminPort;
            long[] before = stats(http, base);

            List<Double> delays = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger connected = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(clients);
            List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());
            try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < clients; i++) {
                    readers.submit(() -> {
                        try {
                            Socket s = new Socket(InetAddress.getLoopbackAddress(), port);
                            sockets.add(s);
                            OutputStream out = s.getOutputStream();
                            out.write("GET /sse HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
                            out.flush();
                            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                            String line;
                            while ((line = in.readLine()) != null) {
                                if (line.startsWith(": connected")) {
                                    connected.incrementAndGet();
                                } else if (line.startsWith("data: ")) {
                                    delays.add((System.nanoTime() - Long.parseLong(line.substring(6).trim())) / 1e6);
                                    break;
                                }
                            }
                        } catch (IOException | NumberFormatException ignored) {
                            // closed
                        } finally {
                            done.countDown();
                        }
                    });
                }
                long until = System.currentTimeMillis() + 20_000;            // let connections settle
                int last = -1;
                while (System.currentTimeMillis() < until) {
                    Thread.sleep(500);
                    if (connected.get() == last) {
                        break;
                    }
                    last = connected.get();
                }
                long[] after = stats(http, base);
                http.send(HttpRequest.newBuilder(URI.create(base + "/broadcast")).POST(HttpRequest.BodyPublishers.noBody())
                        .build(), HttpResponse.BodyHandlers.ofString());
                done.await(3, TimeUnit.SECONDS);
                List<Double> sorted = new ArrayList<>(delays);
                Collections.sort(sorted);
                for (Socket s : sockets) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // closing
                    }
                }
                readers.shutdownNow();
                server.destroyForcibly();                                  // ends any stream still blocked
                return new Result(executor, clients, (int) after[0], sorted.size(),
                        sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2),
                        sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.99)), before[1], after[1], after[2]);
            }
        } finally {
            server.destroyForcibly();
            server.waitFor();
        }
    }

    private static long[] stats(HttpClient http, String base) throws Exception {
        String[] parts = http.send(HttpRequest.newBuilder(URI.create(base + "/stats")).build(),
                HttpResponse.BodyHandlers.ofString()).body().split(" ");
        return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    }

    public static void main(String[] args) throws Exception {
        int clients = args.length > 0 ? Integer.parseInt(args[0]) : 5_000;
        System.out.println(clients + " SSE clients, one broadcast event; server in its own JVM (-Xmx1g)\n");
        System.out.printf("%-10s %8s %13s %9s %8s %8s %12s %10s%n", "executor", "clients", "streams open",
                "received", "p50 ms", "p99 ms", "RSS +MB", "KB/stream");
        for (String executor : new String[] {"pool200", "virtual"}) {
            Result r = run(executor, clients);
            System.out.printf("%-10s %8d %13d %9d %8.1f %8.1f %12.1f %10.1f%n", r.executor(), r.clients(),
                    r.streamsOpen(), r.received(), r.p50Ms(), r.p99Ms(), (r.rssAfterKb() - r.rssBeforeKb()) / 1024.0,
                    r.kbPerStream());
        }
    }
}
