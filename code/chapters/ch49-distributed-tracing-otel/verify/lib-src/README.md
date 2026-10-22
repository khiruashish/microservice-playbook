# lib-src

Sources that `build-libs.sh` compiles alongside the libraries it builds from their release tags:

- `autovalue/`: the five value classes the OpenTelemetry API's build generates with AutoValue
  (`AutoValue_ImmutableSpanContext`, `AutoValue_ArrayBasedTraceState`, `AutoValue_KeyValueImpl`,
  `AutoValue_ImmutableEntry`, `AutoValue_ImmutableEntryMetadata`), written by hand to the same shape (fields, null
  checks, getters, `equals`, `hashCode`, `toString`), because the AutoValue annotation processor isn't available here.
- `stubs/`: compile-only annotations (`@AutoValue`, `@MustBeClosed`) and three Bouncy Castle class signatures that jnats
  uses only for NKey (Ed25519) authentication, which the lab doesn't use. None of these are on the run-time class path
  except the Bouncy Castle stand-ins, which throw if called.

With Maven Central available, use the released `opentelemetry-api` and `jnats` jars instead.
