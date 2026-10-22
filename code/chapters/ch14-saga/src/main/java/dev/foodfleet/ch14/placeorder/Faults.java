package dev.foodfleet.ch14.placeorder;

/**
 * Failure injection for participants. {@code LOST_REPLY} is the nasty one: the participant did the
 * work, but the reply was lost (timeout), so the caller sees a transient failure and calls again.
 */
public interface Faults {

    enum Fault { NONE, REJECT, TRANSIENT, LOST_REPLY }

    Fault next(String participant, String operation, String sagaId);

    Faults NONE = (p, o, s) -> Fault.NONE;
}
