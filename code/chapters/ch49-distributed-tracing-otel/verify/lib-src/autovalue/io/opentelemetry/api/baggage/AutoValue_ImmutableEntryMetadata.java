package io.opentelemetry.api.baggage;

/** Written to match what AutoValue 1.x generates for ImmutableEntryMetadata. */
final class AutoValue_ImmutableEntryMetadata extends ImmutableEntryMetadata {
  private final String value;

  AutoValue_ImmutableEntryMetadata(String value) {
    if (value == null) throw new NullPointerException("Null value");
    this.value = value;
  }

  @Override public String getValue() { return value; }
  @Override public String toString() { return "ImmutableEntryMetadata{value=" + value + "}"; }
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return o instanceof ImmutableEntryMetadata && value.equals(((ImmutableEntryMetadata) o).getValue());
  }
  @Override public int hashCode() { return 1000003 ^ value.hashCode(); }
}
