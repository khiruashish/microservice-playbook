package io.opentelemetry.api.common;

/** Written to match what AutoValue 1.x generates for KeyValueImpl. */
final class AutoValue_KeyValueImpl extends KeyValueImpl {
  private final String key;
  private final Value<?> value;

  AutoValue_KeyValueImpl(String key, Value<?> value) {
    if (key == null) throw new NullPointerException("Null key");
    this.key = key;
    if (value == null) throw new NullPointerException("Null value");
    this.value = value;
  }

  @Override public String getKey() { return key; }
  @Override public Value<?> getValue() { return value; }
  @Override public String toString() { return "KeyValueImpl{key=" + key + ", value=" + value + "}"; }
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof KeyValueImpl) {
      KeyValueImpl that = (KeyValueImpl) o;
      return key.equals(that.getKey()) && value.equals(that.getValue());
    }
    return false;
  }
  @Override public int hashCode() { return ((1000003 ^ key.hashCode()) * 1000003) ^ value.hashCode(); }
}
