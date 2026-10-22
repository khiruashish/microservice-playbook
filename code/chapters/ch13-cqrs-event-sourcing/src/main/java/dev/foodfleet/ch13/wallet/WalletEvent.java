package dev.foodfleet.ch13.wallet;

import java.time.Instant;

/**
 * Everything that can happen to a rider's earnings wallet, as facts in the past tense. The
 * wallet's state is whatever these events add up to; there is no "balance" column anywhere.
 */
public sealed interface WalletEvent {

    Instant occurredAt();

    record WalletOpened(String riderId, Instant occurredAt) implements WalletEvent {
    }

    record EarningCredited(String orderId, long paise, Instant occurredAt) implements WalletEvent {
    }

    record TipCredited(String orderId, long paise, Instant occurredAt) implements WalletEvent {
    }

    /** Positive or negative: a correction, or a penalty for a cancelled delivery. */
    record AdjustmentApplied(long paise, String reason, Instant occurredAt) implements WalletEvent {
    }

    record PayoutRequested(String payoutId, long paise, Instant occurredAt) implements WalletEvent {
    }

    record PayoutCompleted(String payoutId, Instant occurredAt) implements WalletEvent {
    }

    /** The bank rejected the transfer: the money is available again. */
    record PayoutFailed(String payoutId, String reason, Instant occurredAt) implements WalletEvent {
    }
}
