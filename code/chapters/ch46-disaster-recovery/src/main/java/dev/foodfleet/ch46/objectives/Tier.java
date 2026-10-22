package dev.foodfleet.ch46.objectives;

import java.time.Duration;

/**
 * FoodFleet's recovery tiers. Each service declares one; the tier decides how its data is replicated, how it fails
 * over and how often recovery is rehearsed. RPO: how much acknowledged data may be lost. RTO: how long until the
 * service works again. Both are measured, not promised: chapter 46 measures what each replication mode delivers.
 */
public enum Tier {

    /** Orders and payments: money moves, nothing acknowledged may be lost. */
    TIER_0(Duration.ZERO, Duration.ofMinutes(5), Replication.SYNC_IN_REGION_ASYNC_CROSS_REGION, Duration.ofDays(30)),

    /** Menus, restaurant and rider state: seconds of loss are tolerable, rebuilt from events if needed. */
    TIER_1(Duration.ofSeconds(30), Duration.ofMinutes(15), Replication.ASYNC_CROSS_REGION, Duration.ofDays(90)),

    /** Recommendations, analytics: rebuilt from upstream data; restore from backup is enough. */
    TIER_2(Duration.ofHours(24), Duration.ofHours(8), Replication.BACKUP_ONLY, Duration.ofDays(180));

    public enum Replication {
        /** A synchronous standby in another zone of the same region (zero loss) plus an asynchronous one far away. */
        SYNC_IN_REGION_ASYNC_CROSS_REGION,
        /** An asynchronous standby in the other region; lag is the RPO. */
        ASYNC_CROSS_REGION,
        /** Base backups and archived WAL in another region; restore time is the RTO. */
        BACKUP_ONLY
    }

    private final Duration rpo;
    private final Duration rto;
    private final Replication replication;
    private final Duration rehearsalInterval;

    Tier(Duration rpo, Duration rto, Replication replication, Duration rehearsalInterval) {
        this.rpo = rpo;
        this.rto = rto;
        this.replication = replication;
        this.rehearsalInterval = rehearsalInterval;
    }

    public Duration rpo() {
        return rpo;
    }

    public Duration rto() {
        return rto;
    }

    public Replication replication() {
        return replication;
    }

    /** How often a real failover or restore of this tier is rehearsed. */
    public Duration rehearsalInterval() {
        return rehearsalInterval;
    }

    /** Does a measured rehearsal meet this tier's objectives? */
    public boolean met(Duration measuredDataLoss, Duration measuredDowntime) {
        return measuredDataLoss.compareTo(rpo) <= 0 && measuredDowntime.compareTo(rto) <= 0;
    }
}
