package dev.foodfleet.ch13.wallet;

import dev.foodfleet.ch13.wallet.WalletEvent.AdjustmentApplied;
import dev.foodfleet.ch13.wallet.WalletEvent.EarningCredited;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutCompleted;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutFailed;
import dev.foodfleet.ch13.wallet.WalletEvent.PayoutRequested;
import dev.foodfleet.ch13.wallet.WalletEvent.TipCredited;
import dev.foodfleet.ch13.wallet.WalletEvent.WalletOpened;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An event-sourced aggregate in the decide / evolve style:
 * <ul>
 *   <li><b>decide</b> (command methods) checks the rules against current state and returns new
 *       events; it changes nothing;</li>
 *   <li><b>evolve</b> ({@link #apply}) folds one event into state; it never fails, because the
 *       event already happened.</li>
 * </ul>
 * State is rebuilt by applying every event (or a snapshot plus the events after it).
 */
public final class RiderWallet {

    public static final long MIN_PAYOUT_PAISE = 100_00;

    private String riderId;
    private long balancePaise;               // credited − adjusted − paid out (includes the pending payout)
    private long lifetimeEarnedPaise;
    private String pendingPayoutId;
    private long pendingPayoutPaise;
    private final Set<String> creditedOrders = new HashSet<>();
    private long version;

    public static RiderWallet empty() {
        return new RiderWallet();
    }

    // ---------- decide ----------

    public List<WalletEvent> open(String riderId, Instant now) {
        if (this.riderId != null) {
            throw new WalletRuleViolation("Wallet for " + riderId + " already exists");
        }
        return List.of(new WalletOpened(riderId, now));
    }

    public List<WalletEvent> creditEarning(String orderId, long paise, Instant now) {
        requireOpen();
        requirePositive(paise);
        if (creditedOrders.contains(orderId)) {
            return List.of();                    // idempotent: the same delivery is never paid twice
        }
        return List.of(new EarningCredited(orderId, paise, now));
    }

    public List<WalletEvent> creditTip(String orderId, long paise, Instant now) {
        requireOpen();
        requirePositive(paise);
        return List.of(new TipCredited(orderId, paise, now));
    }

    public List<WalletEvent> adjust(long paise, String reason, Instant now) {
        requireOpen();
        if (paise == 0) {
            throw new WalletRuleViolation("An adjustment must change the balance");
        }
        if (paise < 0 && availablePaise() + paise < 0) {
            throw new WalletRuleViolation("Adjustment of " + paise + " paise would make the available balance negative");
        }
        return List.of(new AdjustmentApplied(paise, reason, now));
    }

    public List<WalletEvent> requestPayout(String payoutId, long paise, Instant now) {
        requireOpen();
        if (pendingPayoutId != null) {
            throw new WalletRuleViolation("Payout " + pendingPayoutId + " is still in progress");
        }
        if (paise < MIN_PAYOUT_PAISE) {
            throw new WalletRuleViolation("Minimum payout is ₹100");
        }
        if (paise > availablePaise()) {
            throw new WalletRuleViolation("Requested " + paise + " paise but only " + availablePaise() + " are available");
        }
        return List.of(new PayoutRequested(payoutId, paise, now));
    }

    public List<WalletEvent> completePayout(String payoutId, Instant now) {
        requirePending(payoutId);
        return List.of(new PayoutCompleted(payoutId, now));
    }

    public List<WalletEvent> failPayout(String payoutId, String reason, Instant now) {
        requirePending(payoutId);
        return List.of(new PayoutFailed(payoutId, reason, now));
    }

    // ---------- evolve ----------

    public RiderWallet apply(WalletEvent event) {
        switch (event) {
            case WalletOpened e -> riderId = e.riderId();
            case EarningCredited e -> {
                balancePaise += e.paise();
                lifetimeEarnedPaise += e.paise();
                creditedOrders.add(e.orderId());
            }
            case TipCredited e -> {
                balancePaise += e.paise();
                lifetimeEarnedPaise += e.paise();
            }
            case AdjustmentApplied e -> balancePaise += e.paise();
            case PayoutRequested e -> {
                pendingPayoutId = e.payoutId();
                pendingPayoutPaise = e.paise();
            }
            case PayoutCompleted e -> {
                balancePaise -= pendingPayoutPaise;
                pendingPayoutId = null;
                pendingPayoutPaise = 0;
            }
            case PayoutFailed e -> {
                pendingPayoutId = null;
                pendingPayoutPaise = 0;
            }
        }
        version++;
        return this;
    }

    // ---------- snapshots ----------

    public Snapshot snapshot() {
        return new Snapshot(riderId, balancePaise, lifetimeEarnedPaise, pendingPayoutId, pendingPayoutPaise,
                Set.copyOf(creditedOrders), version);
    }

    public static RiderWallet fromSnapshot(Snapshot s) {
        RiderWallet w = new RiderWallet();
        w.riderId = s.riderId();
        w.balancePaise = s.balancePaise();
        w.lifetimeEarnedPaise = s.lifetimeEarnedPaise();
        w.pendingPayoutId = s.pendingPayoutId();
        w.pendingPayoutPaise = s.pendingPayoutPaise();
        w.creditedOrders.addAll(s.creditedOrders());
        w.version = s.version();
        return w;
    }

    public record Snapshot(String riderId, long balancePaise, long lifetimeEarnedPaise, String pendingPayoutId,
                           long pendingPayoutPaise, Set<String> creditedOrders, long version) {
    }

    // ---------- queries on the aggregate itself ----------

    public long availablePaise() {
        return balancePaise - pendingPayoutPaise;
    }

    public long balancePaise() {
        return balancePaise;
    }

    public long lifetimeEarnedPaise() {
        return lifetimeEarnedPaise;
    }

    public String pendingPayoutId() {
        return pendingPayoutId;
    }

    public long version() {
        return version;
    }

    public boolean isOpen() {
        return riderId != null;
    }

    private void requireOpen() {
        if (riderId == null) {
            throw new WalletRuleViolation("Wallet does not exist");
        }
    }

    private void requirePending(String payoutId) {
        requireOpen();
        if (!payoutId.equals(pendingPayoutId)) {
            throw new WalletRuleViolation("No payout " + payoutId + " in progress");
        }
    }

    private static void requirePositive(long paise) {
        if (paise <= 0) {
            throw new WalletRuleViolation("Amount must be positive");
        }
    }
}
