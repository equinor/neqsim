---
title: "Private / proprietary entrainment providers"
description: How organisations can plug a proprietary entrainment / carry-over model into NeqSim without forking the project, using the public EnhancedEntrainmentProvider SPI and the standard java.util.ServiceLoader mechanism.
---

# Private / proprietary entrainment providers

NeqSim exposes a public Service Provider Interface (SPI),
`neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider`,
so organisations can plug in proprietary entrainment / carry-over models
**without forking NeqSim** and without contributing the model upstream.

This page is intentionally generic. It documents the extension *mechanism*
only — public NeqSim ships no information about any specific private
provider, its inputs, its outputs, or the data it was calibrated against.

## How it works

1. A plug-in implements `EnhancedEntrainmentProvider` and registers itself
   through `java.util.ServiceLoader` by adding a single line to a file in
   its JAR:

   ```
   META-INF/services/neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider
   ```

   The line is the fully qualified class name of the implementation,
   e.g. `com.example.myorg.MyOrgEntrainmentProvider`.

2. A `Separator` consumes the provider at runtime by id:

   ```java
   scrubber.setEnhancedEntrainmentCalculation(true);
   scrubber.setEntrainmentProvider("my-org-model-v1");
   scrubber.run();
   EntrainmentResult r = scrubber.getEntrainmentProvider().compute(scrubber);
   ```

3. If no provider with that id is on the classpath, NeqSim throws
   `IllegalStateException` with a list of available ids. **There is no
   silent fallback** — version-dependent behaviour cannot leak in by
   accident.

The built-in `"neqsim-7stage"` provider is always available and serves as
the reference implementation.

## Build and distribute pattern

The pattern below is generic. Use whatever git hosting and Maven repository
your organisation already operates.

- The private plug-in lives in a **separate, private repository** with its
  own `pom.xml`. It declares NeqSim as a `provided` dependency so two
  copies of NeqSim never end up on the classpath:

  ```xml
  <dependency>
    <groupId>neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>${neqsim.version}</version>
    <scope>provided</scope>
  </dependency>
  ```

- Consumers either:
  - clone the private repo and run `mvn install` to put the jar in the
    local Maven cache, or
  - publish the jar to an internal Artifactory / Nexus and pull it as a
    normal Maven dependency.

- The application that calls NeqSim adds the private jar to its **runtime
  classpath**. `ServiceLoader` discovers it automatically.

- **Public NeqSim has no compile-time or runtime knowledge of any private
  plug-in.** Do not add `<dependency>` entries pointing at private
  artefacts to this repo's `pom.xml`, and do not add private repository
  URLs to this repo's build configuration.

## Authoring checklist (for plug-in authors)

- Read every input you need from public `Separator` and
  `SeparatorMechanicalDesign` getters. Do not require casts to internal
  NeqSim types or reach into package-private state.
- Return an
  `EntrainmentResult(providerId, providerVersion, oilInGasKgPerHr,
  waterInGasKgPerHr, gasInLiquidKgPerHr, confidenceBandKgPerHr)` with
  all rates in **kg/h**. Use `Double.NaN` for fields the model does not
  compute.
- Pick a stable `providerId`. Recommended pattern:
  `<org>-<model>-v<major>`, e.g. `my-org-model-v1`. **Bump the major
  version on incompatible changes** (different inputs, different output
  semantics, different validity envelope) so historians and reports can
  tell results apart.
- Implement `checkApplicability(Separator)` to fail fast outside the
  model's validity envelope rather than silently extrapolate. Diagnostics
  should be human-readable, one entry per out-of-range input.
- Document which separator geometries (vertical / horizontal, demister
  type, inlet device type) the model is valid for. This documentation
  belongs in the **private repository's README**, not in public NeqSim.
- Pin `getApiVersion()` to the SPI revision your jar was built against.
  The registry refuses providers built for a higher revision than core
  supports, so version mismatches fail at registration rather than
  silently at runtime.

## Stability contract (from the public side)

- Existing methods on `EnhancedEntrainmentProvider`,
  `EntrainmentResult` and `EntrainmentApplicability` will not change
  signature.
- New capabilities are added only as `default` methods on the SPI,
  so existing implementations keep compiling against new public NeqSim
  releases.
- The public `providerId` strings used by built-in providers (currently
  `"neqsim-7stage"`) are stable. Private plug-ins should pick a
  non-colliding namespace.

## Related documentation

- [Choosing an entrainment / carry-over model](separators.md#choosing-an-entrainment--carry-over-model)
- [Enhanced separator entrainment modeling](separator-entrainment-modeling.md)
