package dev.foodfleet.ch56;

import dev.foodfleet.ch56.hpa.ReplicaCalculator;
import dev.foodfleet.ch56.hpa.ReplicaCalculator.Pod;
import dev.foodfleet.ch56.hpa.ReplicaCalculator.Sample;
import dev.foodfleet.ch56.hpa.ScalingBehavior;
import dev.foodfleet.ch56.hpa.ScalingBehavior.Policy;
import dev.foodfleet.ch56.hpa.ScalingBehavior.Rules;
import dev.foodfleet.ch56.hpa.ScalingBehavior.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoscalerTest {

    static Pod busy(long usage) {
        return new Pod(Sample.USABLE, usage, 200);
    }

    @Test
    void utilizationIsRelativeToRequests() {
        // 2 pods using 300m each, request 200m: 150% against a 50% target -> 3x -> 6 pods
        assertThat(ReplicaCalculator.desired(2, 50, 0.1, List.of(busy(300), busy(300))).replicas()).isEqualTo(6);
        // the same usage with 400m requests is 75%: 1.5x -> 3 pods
        assertThat(ReplicaCalculator.desired(2, 50, 0.1, List.of(new Pod(Sample.USABLE, 300, 400), new Pod(Sample.USABLE, 300, 400))).replicas())
                .isEqualTo(3);
    }

    @Test
    void smallDeviationsAreIgnored() {
        // 54% against 50% is a ratio of 1.08: within the 10% tolerance
        assertThat(ReplicaCalculator.desired(4, 50, 0.1, List.of(busy(108), busy(108), busy(108), busy(108))).replicas()).isEqualTo(4);
        assertThat(ReplicaCalculator.desired(4, 50, 0.1, List.of(busy(124), busy(124), busy(124), busy(124))).replicas()).isEqualTo(5);
    }

    @Test
    void unreadyPodsCountAsIdleOnAScaleUp() {
        // 2 busy pods at 150%; 2 new pods not ready yet. Counting them as idle: 600 / 800 = 75% -> 1.5 x 4 = 6
        List<Pod> pods = List.of(busy(300), busy(300), new Pod(Sample.UNREADY, 400, 200), new Pod(Sample.UNREADY, 400, 200));
        assertThat(ReplicaCalculator.desired(4, 50, 0.1, pods).replicas()).isEqualTo(6);
        // had their startup CPU (400m each) counted, it would have been 1400 / 800 = 175% -> 14
        List<Pod> counted = List.of(busy(300), busy(300), busy(400), busy(400));
        assertThat(ReplicaCalculator.desired(4, 50, 0.1, counted).replicas()).isEqualTo(14);
    }

    @Test
    void missingPodsNeverMakeItMoreAggressive() {
        // scale-down: the pod without a sample is assumed busy (100% of its request) -> stays at 3 instead of going to 1
        List<Pod> pods = List.of(busy(20), busy(20), new Pod(Sample.MISSING, 0, 200));
        assertThat(ReplicaCalculator.desired(3, 50, 0.1, pods).replicas()).isEqualTo(3);
        assertThat(ReplicaCalculator.desired(3, 50, 0.1, List.of(busy(20), busy(20))).replicas()).isEqualTo(1);
    }

    @Test
    void scaleDownWaitsForTheStabilizationWindow() {
        ScalingBehavior b = new ScalingBehavior(1, 10, Rules.defaultScaleUp(), Rules.defaultScaleDown());
        int replicas = 2;
        replicas = b.next(0, replicas, 8);                         // up: 2 -> max(2+4, 2x2) = 6
        assertThat(replicas).isEqualTo(6);
        replicas = b.next(15_000, replicas, 8);
        assertThat(replicas).isEqualTo(8);
        for (long t = 30_000; t < 315_000; t += 15_000) {          // load gone: recommendations of 1 for 285 s
            replicas = b.next(t, replicas, 1);
        }
        assertThat(replicas).isEqualTo(8);                         // the 8 at 15 s is still in the 300 s window
        replicas = b.next(315_000, replicas, 1);
        assertThat(replicas).isEqualTo(1);
    }

    @Test
    void policiesLimitTheRate() {
        Rules slowDown = new Rules(0, true, List.of(new Policy(Type.PODS, 1, 60)));
        ScalingBehavior b = new ScalingBehavior(1, 10, Rules.defaultScaleUp(), slowDown);
        int replicas = 6;
        replicas = b.next(0, replicas, 1);
        assertThat(replicas).isEqualTo(5);
        replicas = b.next(15_000, replicas, 1);                    // one pod per 60 s: no more yet
        assertThat(replicas).isEqualTo(5);
        replicas = b.next(61_000, replicas, 1);
        assertThat(replicas).isEqualTo(4);
    }
}
