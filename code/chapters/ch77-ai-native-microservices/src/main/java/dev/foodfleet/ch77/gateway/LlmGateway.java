package dev.foodfleet.ch77.gateway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FoodFleet's LLM gateway: every model call from every service goes through it. It streams responses, enforces a
 * total deadline, a first-token deadline and an idle deadline between chunks, retries overloads, rate limits and stalls
 * with backoff, and limits concurrency globally and per feature, so a batch job can't take the provider's whole limit.
 */
public final class LlmGateway {

    /** Deadlines (null for none) and retries. */
    public record Policy(Duration total, Duration firstToken, Duration idle, int retries) {
    }

    /**
     * @param wastedTokens tokens the provider streamed in attempts that were then abandoned (still billed)
     */
    public record Outcome(boolean ok, double millis, double firstTokenMillis, int tokens, int attempts, String error, int wastedTokens) {
    }

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;
    private final Policy policy;
    private final Semaphore global;
    private final Map<String, Semaphore> perFeature = new ConcurrentHashMap<>();

    /**
     * @param globalLimit  concurrent calls across all features (0: no limit)
     * @param featureLimits concurrent calls per feature, for the features that have one
     */
    public LlmGateway(URI endpoint, Policy policy, int globalLimit, Map<String, Integer> featureLimits) {
        this.endpoint = endpoint;
        this.policy = policy;
        this.global = globalLimit > 0 ? new Semaphore(globalLimit, true) : null;
        featureLimits.forEach((f, n) -> perFeature.put(f, new Semaphore(n, true)));
    }

    public Outcome call(String feature, String model, int maxTokens) throws InterruptedException {
        Semaphore mine = perFeature.get(feature);
        long start = System.nanoTime();
        if (mine != null) {
            mine.acquire();
        }
        if (global != null) {
            global.acquire();
        }
        try {
            int wasted = 0;
            String error = null;
            for (int attempt = 1; attempt <= policy.retries() + 1; attempt++) {
                Attempt a = attempt(model, maxTokens, start);
                if (a.error == null) {
                    return new Outcome(true, ms(start), a.firstToken, a.tokens, attempt, null, wasted);
                }
                wasted += a.tokens;
                error = a.error;
                if (policy.total() != null && System.nanoTime() - start > policy.total().toNanos()) {
                    break;
                }
                long backoff = a.retryAfterMillis > 0 ? a.retryAfterMillis : (long) (200 * Math.pow(2, attempt - 1) * (0.5 + ThreadLocalRandom.current().nextDouble()));
                Thread.sleep(backoff);
            }
            return new Outcome(false, ms(start), -1, 0, policy.retries() + 1, error, wasted);
        } finally {
            if (global != null) {
                global.release();
            }
            if (mine != null) {
                mine.release();
            }
        }
    }

    private static final class Attempt {
        String error;
        int tokens;
        double firstToken = -1;
        long retryAfterMillis;
    }

    private Attempt attempt(String model, int maxTokens, long callStart) {
        Attempt a = new Attempt();
        long t0 = System.nanoTime();
        HttpRequest req = HttpRequest.newBuilder(endpoint).header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\",\"max_tokens\":" + maxTokens + ",\"stream\":true}")).build();
        HttpResponse<InputStream> resp;
        try {
            resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            a.error = "connect";
            return a;
        }
        if (resp.statusCode() != 200) {
            a.error = "http " + resp.statusCode();
            a.retryAfterMillis = resp.headers().firstValue("retry-after").map(s -> Long.parseLong(s) * 1_000).orElse(0L);
            try {
                resp.body().close();
            } catch (IOException ignored) {
                // nothing to do
            }
            return a;
        }
        InputStream body = resp.body();
        long[] lastChunk = {System.nanoTime()};
        boolean[] first = {false};
        Thread watchdog = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();
                String why = null;
                if (policy.total() != null && now - callStart > policy.total().toNanos()) {
                    why = "total deadline";
                } else if (!first[0] && policy.firstToken() != null && now - t0 > policy.firstToken().toNanos()) {
                    why = "first-token deadline";
                } else if (first[0] && policy.idle() != null && now - lastChunk[0] > policy.idle().toNanos()) {
                    why = "idle deadline";
                }
                if (why != null) {
                    synchronized (a) {
                        if (a.error == null) {
                            a.error = why;
                        }
                    }
                    try {
                        body.close();
                    } catch (IOException ignored) {
                        // closing aborts the read below
                    }
                    return;
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        try (BufferedReader r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("data: {\"type\":\"content_block_delta\"")) {
                    if (!first[0]) {
                        a.firstToken = ms(callStart);
                        first[0] = true;
                    }
                    lastChunk[0] = System.nanoTime();
                    a.tokens += Integer.parseInt(line.replaceAll(".*\"tokens\":([0-9]+).*", "$1"));
                } else if (line.startsWith("data: {\"type\":\"message_stop\"")) {
                    break;
                }
            }
        } catch (IOException e) {
            synchronized (a) {
                if (a.error == null) {
                    a.error = "stream broken";
                }
            }
        } finally {
            watchdog.interrupt();
        }
        synchronized (a) {
            return a;
        }
    }

    static double ms(long since) {
        return (System.nanoTime() - since) / 1e6;
    }
}
