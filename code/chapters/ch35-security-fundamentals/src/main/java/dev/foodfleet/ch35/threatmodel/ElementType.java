package dev.foodfleet.ch35.threatmodel;

import java.util.EnumSet;
import java.util.Set;

/**
 * Data-flow diagram element types and the STRIDE categories that apply to each ("STRIDE per element"): an
 * external entity can be impersonated or deny what it did, but its internals are out of scope; a data flow can be
 * read, altered or blocked in transit; a process is exposed to all six.
 */
public enum ElementType {
    EXTERNAL_ENTITY(EnumSet.of(Stride.SPOOFING, Stride.REPUDIATION)),
    PROCESS(EnumSet.allOf(Stride.class)),
    DATA_STORE(EnumSet.of(Stride.TAMPERING, Stride.INFORMATION_DISCLOSURE, Stride.DENIAL_OF_SERVICE)),
    /** A data store that is an audit trail: repudiation applies (can its entries be denied or erased?). */
    AUDIT_LOG(EnumSet.of(Stride.TAMPERING, Stride.REPUDIATION, Stride.INFORMATION_DISCLOSURE,
            Stride.DENIAL_OF_SERVICE)),
    DATA_FLOW(EnumSet.of(Stride.TAMPERING, Stride.INFORMATION_DISCLOSURE, Stride.DENIAL_OF_SERVICE));

    private final Set<Stride> applicable;

    ElementType(Set<Stride> applicable) {
        this.applicable = applicable;
    }

    public Set<Stride> applicable() {
        return applicable;
    }
}
