package dev.foodfleet.ch03.replication;

/** The CP choice made visible: refuse rather than risk an inconsistent answer. */
public class QuorumUnavailableException extends RuntimeException {

    public QuorumUnavailableException(String operation, int required, int reachable) {
        super("Cannot " + operation + ": need " + required + " replicas, only " + reachable + " reachable");
    }
}
