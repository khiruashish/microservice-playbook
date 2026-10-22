package dev.foodfleet.ch73.fitness;

/**
 * An automated check of one architectural characteristic, traced to the decision it protects.
 * Static functions read the release's classes (atomic: one characteristic, no running system); dynamic ones run it.
 */
public record FitnessFunction(String id, String adr, Kind kind, String what, Check check) {

    public enum Kind { STATIC, DYNAMIC }

    /** The release under test: its compiled classes, the previous release's (or null), and a probe of the running release. */
    public record Release(String name, java.nio.file.Path classes, java.nio.file.Path previous, Bytecode bytecode, Bytecode previousBytecode) {
    }

    public record Result(boolean pass, String detail) {
    }

    @FunctionalInterface
    public interface Check {
        Result run(Release release, Probe probe) throws Exception;
    }
}
