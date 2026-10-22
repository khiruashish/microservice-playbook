package io.opentelemetry.api.baggage;

/** Written to match what AutoValue 1.x generates for ImmutableEntry. */
final class AutoValue_ImmutableEntry extends ImmutableEntry {
  private final String value;
  private final BaggageEntryMetadata metadata;

  AutoValue_ImmutableEntry(String value, BaggageEntryMetadata metadata) {
    if (value == null) throw new NullPointerException("Null value");
    this.value = value;
    if (metadata == null) throw new NullPointerException("Null metadata");
    this.metadata = metadata;
  }

  @Override public String getValue() { return value; }
  @Override public BaggageEntryMetadata getMetadata() { return metadata; }
  @Override public String toString() { return "ImmutableEntry{value=" + value + ", metadata=" + metadata + "}"; }
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof ImmutableEntry) {
      ImmutableEntry that = (ImmutableEntry) o;
      return value.equals(that.getValue()) && metadata.equals(that.getMetadata());
    }
    return false;
  }
  @Override public int hashCode() { return ((1000003 ^ value.hashCode()) * 1000003) ^ metadata.hashCode(); }
}
