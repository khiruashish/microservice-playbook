package io.opentelemetry.api.trace;

import java.util.List;

/** Written to match what AutoValue 1.x generates for ArrayBasedTraceState. */
final class AutoValue_ArrayBasedTraceState extends ArrayBasedTraceState {
  private final List<String> entries;

  AutoValue_ArrayBasedTraceState(List<String> entries) {
    if (entries == null) throw new NullPointerException("Null entries");
    this.entries = entries;
  }

  @Override List<String> getEntries() { return entries; }
  @Override public String toString() { return "ArrayBasedTraceState{entries=" + entries + "}"; }
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return o instanceof ArrayBasedTraceState && entries.equals(((ArrayBasedTraceState) o).getEntries());
  }
  @Override public int hashCode() { return (1000003 ^ entries.hashCode()); }
}
