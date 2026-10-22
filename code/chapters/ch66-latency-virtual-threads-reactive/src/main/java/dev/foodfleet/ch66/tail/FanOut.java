package dev.foodfleet.ch66.tail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A request that needs answers from {@code n} backends at once (a restaurant listing asking every kitchen for its
 * wait time, a search across shards) and is as slow as the slowest. Optionally hedged: if a call hasn't answered
 * after {@code hedgeAfterMillis}, send the same call again and take whichever answers first, as long as hedges stay
 * under {@code budget} of all calls (1.0: no limit).
 */
public final class FanOut {

    private final HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
    private final String url;
    private final double budget;
    public final AtomicLong calls = new AtomicLong();
    public final AtomicLong hedges = new AtomicLong();

    public FanOut(String url) {
        this(url, 1.0);
    }

    public FanOut(String url, double budget) {
        this.url = url;
        this.budget = budget;
    }

    /** Blocks until all {@code n} answers are in; returns the time taken in microseconds. */
    public long request(int n, long hedgeAfterMillis) throws Exception {
        long t = System.nanoTime();
        List<CompletableFuture<?>> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            all.add(call(i, hedgeAfterMillis));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        return (System.nanoTime() - t) / 1000;
    }

    CompletableFuture<?> call(int shard, long hedgeAfterMillis) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/kitchen/" + shard + "/wait")).timeout(Duration.ofSeconds(30)).build();
        calls.incrementAndGet();
        CompletableFuture<HttpResponse<Void>> first = http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        if (hedgeAfterMillis <= 0) {
            return first;
        }
        CompletableFuture<Object> answer = new CompletableFuture<>();
        first.whenComplete((r, e) -> answer.complete(r));
        CompletableFuture.delayedExecutor(hedgeAfterMillis, TimeUnit.MILLISECONDS).execute(() -> {
            if (!answer.isDone() && hedges.get() < budget * calls.get()) {
                hedges.incrementAndGet();
                calls.incrementAndGet();
                http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, e) -> answer.complete(r));
            }
        });
        return answer;
    }
}
