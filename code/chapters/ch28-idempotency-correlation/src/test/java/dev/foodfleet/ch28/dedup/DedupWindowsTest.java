package dev.foodfleet.ch28.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DedupWindowsTest {

    @Test
    void theWindowMustCoverTheSlowestDuplicateSource() {
        var lru = DedupWindows.run(DedupWindows.Store.LOCAL_LRU_100K, 28);
        var cache = DedupWindows.run(DedupWindows.Store.SHARED_TTL_1H, 28);
        var inbox = DedupWindows.run(DedupWindows.Store.INBOX_TABLE_7D, 28);
        var version = DedupWindows.run(DedupWindows.Store.SHARED_1H_PLUS_VERSION, 28);

        assertThat(lru.applied().get(DedupWindows.Cause.PRODUCER_RETRY)).isEqualTo(0);
        assertThat(lru.applied().get(DedupWindows.Cause.REBALANCE)).isGreaterThan(2_000);  // other instance
        assertThat(cache.applied().get(DedupWindows.Cause.REPLAY)).isEqualTo(1_000);        // days later
        assertThat(inbox.appliedTotal()).isEqualTo(0);
        assertThat(version.appliedTotal()).isEqualTo(0);
        assertThat(version.idsKept()).isLessThan(inbox.idsKept() / 100);
    }
}
