package dev.foodfleet.ch14.saga;

public enum SagaStatus {
    RUNNING,
    /** All steps done. */
    COMPLETED,
    /** A step before or at the pivot failed; earlier steps were compensated. */
    COMPENSATING,
    COMPENSATED,
    /** Needs a human or a later retry: a retriable step or a compensation kept failing. */
    STUCK
}
