# ADR-014: External service clients: Quarkus REST Client and Jackson

**Status**: accepted, 2026-07-16.

## Context

Orpheus has to query two external services for the tiered identification described in SPEC.md
section 4.4: MusicBrainz (text search in Tier 0/1, and lookup of the releases of a recording found by
fingerprint in Tier 2) and AcoustID (computing and comparing the audio fingerprint in Tier 2). Both
impose limits on request frequency: MusicBrainz asks for no more than 1 request per second per client,
AcoustID applies a rate limit on the personal API key. One of the real bugs that motivated ADR-006 was
precisely a shared key hardcoded and rate-limited unpredictably. The concurrency model chosen in
ADR-011 (one virtual thread per track) means that several threads may want to call these services at
the same moment.

## Decision

- **Declarative HTTP client**: Quarkus REST Client (MicroProfile Rest Client), not OpenFeign. The same
  declarative-interface approach as OpenFeign, but native to the Quarkus ecosystem chosen in ADR-007:
  it integrates with Arc (DI) and with the framework configuration, instead of bringing in a foreign
  body designed for Spring Cloud.
- **JSON serialization**: Jackson, through the `quarkus-rest-client-jackson` extension.
- **Targeted retry and timeout**: SmallRye Fault Tolerance annotations (`@Retry`, `@Timeout`) on the
  client methods, for transient network failures.
- **Continuous rate limiting** (1 request per second towards MusicBrainz, a similar limit towards
  AcoustID): not delegated to the fault-tolerance annotations, which handle retries on failure and not
  a constant throttling. An explicit client-side limiter, a thread-safe semaphore or mutex shared among
  the virtual threads calling the same service, blocks the calling thread until a call slot is
  available again. Blocking a virtual thread is cheap, consistent with the concurrency choice of
  ADR-011.

## Consequences

The limiter must be implemented as a shared infrastructure component (an adapter, under ADR-009), not
duplicated for each call: a single instance per external service, injected wherever that call is
made. The personal AcoustID key stays a configuration requirement, not a code one, handled with the
configuration mechanism Quarkus already provides (MicroProfile Config, ADR-007), never hardcoded: that
is exactly the ADR-006 bug this choice must not repeat.
