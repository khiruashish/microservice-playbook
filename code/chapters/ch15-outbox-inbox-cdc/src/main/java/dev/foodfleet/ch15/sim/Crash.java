package dev.foodfleet.ch15.sim;

/** A simulated process crash (pod killed, OOM, deploy): whatever wasn't committed is gone. */
public final class Crash extends RuntimeException {

    public Crash(String point) {
        super("crash at " + point, null, false, false);
    }
}
