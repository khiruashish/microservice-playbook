package dev.foodfleet.ch35.threatmodel;

/** Microsoft's STRIDE: each category is the violation of one security property. */
public enum Stride {
    SPOOFING("authenticity"),
    TAMPERING("integrity"),
    REPUDIATION("non-repudiation"),
    INFORMATION_DISCLOSURE("confidentiality"),
    DENIAL_OF_SERVICE("availability"),
    ELEVATION_OF_PRIVILEGE("authorization");

    private final String violates;

    Stride(String violates) {
        this.violates = violates;
    }

    public String violates() {
        return violates;
    }
}
