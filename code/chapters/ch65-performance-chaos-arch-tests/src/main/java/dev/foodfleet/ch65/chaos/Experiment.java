package dev.foodfleet.ch65.chaos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * A chaos experiment in the form chaos engineering prescribes: a steady-state hypothesis measured in windows, a fault
 * injected after a quiet period, and an abort condition that ends the fault when the system leaves steady state badly
 * enough, so a failed experiment hurts for seconds, not for its whole length.
 */
public final class Experiment {

    /** Steady state: at least {@code minSuccess} of orders succeed and p99 is under {@code maxP99Millis}, per window. */
    public record Hypothesis(double minSuccess, double maxP99Millis) {
    }

    public record Window(double startS, int requests, int ok, double p99Millis, boolean faultActive) {
        double success() {
            return requests == 0 ? 1 : (double) ok / requests;
        }
    }

    public record Report(List<Window> windows, Double abortedAtS, int faultRequests, int faultOk, double faultP99) {
    }

    private Experiment() {
    }

    /**
     * Sends {@code rate} orders a second for {@code beforeS + faultS + afterS} seconds; users give up after 5 s.
     * The fault runs from {@code beforeS} for {@code faultS}, unless a window breaks {@code abortBelow} success first.
     */
    public static Report run(String orders, FaultProxy proxy, FaultProxy.Fault fault, Hypothesis h, double abortBelow, double rate,
                             double beforeS, double faultS, double afterS, double windowS) throws InterruptedException {
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        double total = beforeS + faultS + afterS;
        int windows = (int) Math.ceil(total / windowS);
        List<List<Long>> lat = new ArrayList<>();
        AtomicInteger[] ok = new AtomicInteger[windows];
        AtomicInteger[] sent = new AtomicInteger[windows];
        for (int w = 0; w < windows; w++) {
            lat.add(Collections.synchronizedList(new ArrayList<>()));
            ok[w] = new AtomicInteger();
            sent[w] = new AtomicInteger();
        }
        boolean[] faultIn = new boolean[windows];
        long t0 = System.nanoTime();
        long n = (long) (rate * total);
        Double abortedAt = null;
        boolean injected = false;
        boolean ended = false;
        int checkedWindow = -1;
        List<Thread> threads = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            long due = t0 + (long) (i * 1e9 / rate);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            double now = (System.nanoTime() - t0) / 1e9;
            if (!injected && now >= beforeS) {
                proxy.inject(fault);
                injected = true;
            }
            if (injected && !ended && now >= beforeS + faultS) {
                proxy.inject(new FaultProxy.None());
                ended = true;
            }
            int w = (int) (now / windowS);
            faultIn[w] |= injected && !ended;
            // the abort check looks at the last complete window, once its 5 s user timeout has passed
            int finished = (int) ((now - 5) / windowS) - 1;
            if (injected && !ended && finished > checkedWindow && finished >= 0) {
                checkedWindow = finished;
                if (faultIn[finished] && sent[finished].get() > 0 && (double) ok[finished].get() / sent[finished].get() < abortBelow) {
                    proxy.inject(new FaultProxy.None());
                    ended = true;
                    abortedAt = now - beforeS;
                }
            }
            int window = w;
            sent[window].incrementAndGet();
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(orders + "/orders/new")).timeout(Duration.ofSeconds(5)).build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (r.statusCode() == 201) {
                        ok[window].incrementAndGet();
                    }
                } catch (Exception e) {
                    // the user gave up, or the order failed
                }
                lat.get(window).add((System.nanoTime() - due) / 1_000_000);
            }));
        }
        if (!ended) {
            proxy.inject(new FaultProxy.None());
        }
        for (Thread t : threads) {
            t.join();
        }
        List<Window> out = new ArrayList<>();
        int fr = 0;
        int fok = 0;
        List<Long> faultLat = new ArrayList<>();
        for (int w = 0; w < windows; w++) {
            List<Long> l = new ArrayList<>(lat.get(w));
            Collections.sort(l);
            double p99 = l.isEmpty() ? 0 : l.get(Math.max(0, (int) Math.ceil(0.99 * l.size()) - 1));
            out.add(new Window(w * windowS, sent[w].get(), ok[w].get(), p99, faultIn[w]));
            if (faultIn[w]) {
                fr += sent[w].get();
                fok += ok[w].get();
                faultLat.addAll(l);
            }
        }
        Collections.sort(faultLat);
        double fp99 = faultLat.isEmpty() ? 0 : faultLat.get(Math.max(0, (int) Math.ceil(0.99 * faultLat.size()) - 1));
        return new Report(out, abortedAt, fr, fok, fp99);
    }

    /** Windows that broke the hypothesis. */
    public static long breaches(Report r, Hypothesis h) {
        return r.windows().stream().filter(w -> w.success() < h.minSuccess() || w.p99Millis() > h.maxP99Millis()).count();
    }
}
