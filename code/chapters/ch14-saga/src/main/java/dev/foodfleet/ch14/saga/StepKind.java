package dev.foodfleet.ch14.saga;

/**
 * Chris Richardson's three kinds of saga transaction. Their order in a saga is fixed:
 * compensatable steps, then at most one pivot, then retriable steps.
 */
public enum StepKind {
    /** Can be undone by a compensating action if a later step fails. */
    COMPENSATABLE,
    /** The point of no return: if it succeeds, the saga will complete; if it fails, compensate. */
    PIVOT,
    /** After the pivot: must eventually succeed, so it is retried, never compensated. */
    RETRIABLE
}
