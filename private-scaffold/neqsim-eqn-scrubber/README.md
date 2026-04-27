# neqsim-eqn-scrubber

**Equinor-internal — DO NOT publish to Maven Central or any public registry.**

Private NeqSim plug-in implementing **approach (4) — Pi-number with EQN
scrubber testing database** as documented in
[separators.md](https://github.com/equinor/neqsim/blob/master/docs/process/equipment/separators.md#choosing-an-entrainment--carry-over-model)
and
[separator-entrainment-modeling.md#private-extensions](https://github.com/equinor/neqsim/blob/master/docs/process/equipment/separator-entrainment-modeling.md#private-extensions).

## What it provides

A single `ServiceLoader` implementation of the public NeqSim SPI
`neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider`,
registered as id **`eqn-pi-v1`**. The plug-in correlates carry-over against
dimensionless π-groups regressed from the EQN full-scale scrubber test rig.

Inputs:

- mechanical design — nozzle ID, vessel ID, vertical distances (mesh-pad
  to top tan-tan, inlet to liquid level)
- operating conditions — gas density, liquid density, gas mass flow,
  surface tension

Output: `EntrainmentResult` with carry-over (kg/h) and a one-sigma
confidence band derived from the regression residuals.

**Scope.** The π-number regression is valid for **inlet-vane + mesh-pad**
primary-separation scrubbers only. Other geometries (cyclones, vane packs)
must use NeqSim's public 7-stage chain (`neqsim-7stage`) or a different
private plug-in.

## How users consume it

Distribution is via `git clone` + `mvn install` into the consumer's local
Maven cache. There is no Artifactory or Maven Central publication.

1. Clone and install once (requires Equinor GitHub access):

   ```bash
   git clone https://github.com/equinor/neqsim-eqn-scrubber.git
   cd neqsim-eqn-scrubber
   mvn install
   ```

2. Add a dependency to their project's `pom.xml`:

   ```xml
   <dependency>
     <groupId>com.equinor.neqsim</groupId>
     <artifactId>neqsim-eqn-scrubber</artifactId>
     <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

3. Use it from Java / Python:

   ```java
   scrubber.setEnhancedEntrainmentCalculation(true);
   scrubber.setEntrainmentProvider("eqn-pi-v1");
   scrubber.run();
   EntrainmentResult r = scrubber.getEntrainmentProvider().compute(scrubber);
   ```

   If the JAR is not on the classpath, `setEntrainmentProvider("eqn-pi-v1")`
   throws `IllegalStateException` with a clear message — there is no silent
   fallback.

## Test database

The π-number **regression coefficients** ship as a JSON resource inside the
JAR (see `src/main/resources/eqn/coefficients/v1.json`). They are numbers
only, no raw test rows.

The **raw EQN test database** (vendor-tagged operating points and
performance values) is **not** distributed with the JAR. The plug-in does
not need it at runtime — only the regression is required.

If a future feature wants to expose the raw rows (e.g. for re-fitting), it
must load them from a separate location controlled by the
`EQN_SCRUBBER_DB` environment variable. The default plug-in build does
**not** read this variable.

## Releasing

Releases are cut by tagging this repo (`v1.0.0`, etc.). Consumers pick up
new versions by pulling and re-running `mvn install`. CI is configured in
`.azure-pipelines.yml` (build + test only — no publish step).

## Maintainers

See `CODEOWNERS`.
