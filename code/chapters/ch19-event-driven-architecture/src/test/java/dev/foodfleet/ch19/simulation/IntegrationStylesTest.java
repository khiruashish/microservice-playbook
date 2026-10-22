package dev.foodfleet.ch19.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch19.simulation.IntegrationStyles.Result;
import dev.foodfleet.ch19.simulation.IntegrationStyles.Style;
import org.junit.jupiter.api.Test;

class IntegrationStylesTest {

    @Test
    void synchronousCallsFailEveryOrderWhileTheOwnerIsDown() {
        Result sync = IntegrationStyles.run(Style.SYNCHRONOUS, 1);

        assertThat(sync.failedDuringOutage()).isGreaterThan(99.0);
        assertThat(sync.soldOutAccepted()).isLessThan(0.01);
    }

    @Test
    void notificationsKeepOrdersFlowingButOnStaleDataDuringTheOutage() {
        Result notification = IntegrationStyles.run(Style.NOTIFICATION, 1);

        assertThat(notification.failedDuringOutage()).isLessThan(0.01);
        assertThat(notification.soldOutAcceptedDuringOutage()).isGreaterThan(5.0);
    }

    @Test
    void stateTransferNeedsNoCallsAndStaysFreshThroughTheOutage() {
        Result ecst = IntegrationStyles.run(Style.STATE_TRANSFER, 1);

        assertThat(ecst.catalogCalls()).isEqualTo(0L);
        assertThat(ecst.soldOutAcceptedDuringOutage()).isLessThan(0.1);
        assertThat(ecst.stalenessP99Ms()).isLessThan(200.0);
    }

    @Test
    void coalescingCutsCallbackLoad() {
        assertThat(IntegrationStyles.run(Style.NOTIFICATION, 1).catalogCalls())
                .isLessThan(IntegrationStyles.run(Style.NOTIFICATION_NAIVE, 1).catalogCalls() / 3);
    }

    @Test
    void replayingTheFullLogCostsMoreThanACompactedTopic() {
        IntegrationStyles.Bootstrap b = IntegrationStyles.bootstrap(1);

        assertThat(b.fullLogMessages()).isGreaterThan(5 * b.compactedMessages());
    }
}
