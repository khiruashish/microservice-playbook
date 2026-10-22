package dev.foodfleet.ch18.resilience;

import dev.foodfleet.ch18.sim.EventLoop;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** A dependency whose next answers (outcome and latency) are scripted by the test. */
final class ScriptedCall implements Call {

    record Step(Outcome outcome, long latencyMicros) {
    }

    private final EventLoop loop;
    private final Deque<Step> script = new ArrayDeque<>();
    private Step otherwise = new Step(Outcome.SUCCESS, EventLoop.ms(10));
    int calls;

    ScriptedCall(EventLoop loop) {
        this.loop = loop;
    }

    ScriptedCall then(Outcome outcome, double latencyMs) {
        script.addLast(new Step(outcome, EventLoop.ms(latencyMs)));
        return this;
    }

    ScriptedCall otherwise(Outcome outcome, double latencyMs) {
        otherwise = new Step(outcome, EventLoop.ms(latencyMs));
        return this;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        calls++;
        Step step = script.isEmpty() ? otherwise : script.pollFirst();
        loop.after(step.latencyMicros(), () -> done.accept(step.outcome()));
    }
}
