package dev.foodfleet.ch44.web;

import dev.foodfleet.ch44.budget.TimeoutBudget;
import dev.foodfleet.ch44.degrade.Brownout;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The menu page: the menu is required, recommendations are optional. Recommendations get what is left of the page's
 * budget (never more than 150 ms) and are asked for only while the brownout dimmer allows; the page renders without
 * them rather than waiting or failing.
 */
public class MenuPageService {

    public record Page(List<String> menu, List<String> recommendations, boolean degraded) {
    }

    private final Supplier<List<String>> menu;
    private final Supplier<List<String>> recommendations;
    private final Brownout brownout;
    private final TimeoutBudget recommendationsBudget = new TimeoutBudget(Duration.ofMillis(150), Duration.ofMillis(20), Duration.ofMillis(20));
    private final Executor executor;

    public MenuPageService(Supplier<List<String>> menu, Supplier<List<String>> recommendations, Brownout brownout, Executor executor) {
        this.menu = menu;
        this.recommendations = recommendations;
        this.brownout = brownout;
        this.executor = executor;
    }

    public Page render(long deadlineNanos) {
        List<String> items = menu.get();
        Optional<Duration> timeout = recommendationsBudget.next(Duration.ofNanos(deadlineNanos - System.nanoTime()));
        if (timeout.isEmpty() || !brownout.shouldTry()) {
            return new Page(items, List.of(), true);
        }
        CompletableFuture<List<String>> recs = CompletableFuture.supplyAsync(recommendations, executor);
        try {
            List<String> r = recs.get(timeout.get().toMillis(), TimeUnit.MILLISECONDS);
            brownout.succeeded();
            return new Page(items, r, false);
        } catch (TimeoutException | ExecutionException e) {
            brownout.failedOrSlow();
            return new Page(items, List.of(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Page(items, List.of(), true);
        }
    }
}
