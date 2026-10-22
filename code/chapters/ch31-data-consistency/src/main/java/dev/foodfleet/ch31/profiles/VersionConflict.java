package dev.foodfleet.ch31.profiles;

/** The client edited a version that is no longer current: someone else changed the profile in between. */
public class VersionConflict extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int current;

    public VersionConflict(int current) {
        super("the profile is at version " + current);
        this.current = current;
    }

    public int current() {
        return current;
    }
}
