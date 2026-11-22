---
title: "NeqSim 3.26.0 release and migration notes"
description: "Preparation status, changes since 3.25.0, EJML and E300 migration guidance, distribution versions, and publication requirements for NeqSim 3.26.0."
---

# NeqSim 3.26.0

**Status: prepared release candidate; publication pending.** These notes were
prepared on 2026-10-20 from master
`9d6366f3af86ac79522a533b6c7da3696fc4b310`, covering **117 commits** after
[v3.25.0](https://github.com/equinor/neqsim/releases/tag/v3.25.0).
The version-preparation change does not publish artifacts or create a tag.
Open pull requests are outside this snapshot.

[Changes included in this snapshot](https://github.com/equinor/neqsim/compare/v3.25.0...9d6366f3af86ac79522a533b6c7da3696fc4b310).

## Compatibility review before upgrading

### JAMA removal and EJML interfaces

[PR #4416](https://github.com/equinor/neqsim/pull/4416) removes JAMA from the
production and test classpaths. Some former JAMA interfaces now use EJML
`SimpleMatrix`; their old Java method descriptors are not preserved.
This candidate therefore includes **source, binary, and saved-solver
incompatibilities**. It must not be described as an entirely backward-compatible
minor release. The requested candidate number remains 3.26.0; the release owner
must resolve the version and contract classification under
[repository governance](../../GOVERNANCE.md) and the
[API lifecycle policy](api-lifecycle.md) before publication. An accepted NRC
and the required major contract boundary must be recorded for breaking changes;
preparing this version does not satisfy or waive those requirements.

| Affected integration | Required migration |
| --- | --- |
| `JamaLinearAlgebra` | Use `EjmlLinearAlgebra`, preferably through the primitive-array `LinearAlgebraOperations` interface. |
| `ChemicalReactionList.calcReacRates(...)` | Adapt to its EJML return value, or prefer the existing array-returning `calcReationRates(...)`. |
| `FluidBoundaryInterface.getMassTransferCoefficientMatrix()` | Adapt to `SimpleMatrix[]`, or use `getMassTransferCoefficientArray()`. |
| Matrix overloads and subclass hooks in `StatisticsBaseClass` | Rebuild overrides against the EJML signatures; retain dimensions and ownership semantics. |
| `LevenbergMarquardtResult` matrix constructor and covariance/correlation accessors | Use EJML arguments or the named `fromArrays(...)` factory and array accessors. |
| Serialized `SysNewtonRhapsonPhaseEnvelope` working state | Recreate UID-1000 solvers from thermodynamic inputs; the current array-based solver uses UID 1001. |

Recompile Java integrations and update Python/JVM code that constructs or reads
JAMA matrices. Recreate persisted working solvers affected by changed field
types; adding the old JAMA JAR does not convert their state. Same-version
serialization tests do not establish compatibility with all older saved models.
The [complete migration guide](jama-ejml-migration.md) records the affected
interfaces, legacy TP-flash solver boundary, numerical corrections, and tests.

The EJML phase-envelope implementation also rejects volume roots that fail
the EOS pressure equation. This corrects false pure-component saturation
points rather than restoring the retired numerical backend.

### Eclipse E300 critical-volume units

[PR #4455](https://github.com/equinor/neqsim/pull/4455) writes `VCRIT` in
**m3/kmol** and `ZCRIT` as a dimensionless quantity, and converts critical
volumes to NeqSim's internal **cm3/mol** on import. Older NeqSim exports could
overstate both values by a factor of 1000. Regenerate those files before using
them in external reservoir or PVT tools. The reader's magnitude-based legacy
detection does not support mixed-unit blocks or remove the need to verify
unusually large critical volumes. See the
[E300 import and migration guide](../pvtsimulation/eclipse_e300_fluid_import.md).

### Input validation and changed results

- Unsupported ISO 6976 reference temperatures now fail explicitly instead of
  silently selecting an unrelated reference condition
  ([#4425](https://github.com/equinor/neqsim/pull/4425)).
- Named material, company, vessel-plate, packing, pipe-wall and subsea design
  lookups reject missing or unsupported data. Check custom databases and
  explicit overrides before rerunning mechanical designs
  ([#4396](https://github.com/equinor/neqsim/pull/4396),
  [#4397](https://github.com/equinor/neqsim/pull/4397),
  [#4405](https://github.com/equinor/neqsim/pull/4405),
  [#4406](https://github.com/equinor/neqsim/pull/4406),
  [#4407](https://github.com/equinor/neqsim/pull/4407),
  [#4409](https://github.com/equinor/neqsim/pull/4409),
  [#4429](https://github.com/equinor/neqsim/pull/4429)).
- Pipeline surface-temperature profiles have explicit unit handling. Review
  the selected overload and units when migrating older models
  ([#4456](https://github.com/equinor/neqsim/pull/4456)).
- The legacy Aspen IP.21/`OnlineSignal` path is deprecated by
  [#4426](https://github.com/equinor/neqsim/pull/4426), merged after the 3.25.0
  tag. Its source annotations name the development revision 3.25.0; 3.26.0 is
  the first planned published release containing that change. The API remains
  present. See the [historian migration guidance](api-lifecycle.md#legacy-aspen-ip21-historian-integration).

## Main changes since 3.25.0

### Thermodynamics, flashes, and chemistry

- Recover a missing aqueous phase in wet gas-condensate flashes; conserve
  phase masses and empty-outlet inventories; improve trace-water PS-flash
  behavior and pure-fluid saturation guards
  ([#4320](https://github.com/equinor/neqsim/pull/4320),
  [#4325](https://github.com/equinor/neqsim/pull/4325),
  [#4428](https://github.com/equinor/neqsim/pull/4428),
  [#4440](https://github.com/equinor/neqsim/pull/4440),
  [#4457](https://github.com/equinor/neqsim/pull/4457)).
- Isolate shared mixing-rule and CPA clone state, reset classic HV pair data,
  and retain wax equilibrium during endpoint cleanup
  ([#4329](https://github.com/equinor/neqsim/pull/4329),
  [#4333](https://github.com/equinor/neqsim/pull/4333),
  [#4363](https://github.com/equinor/neqsim/pull/4363),
  [#4364](https://github.com/equinor/neqsim/pull/4364),
  [#4412](https://github.com/equinor/neqsim/pull/4412),
  [#4415](https://github.com/equinor/neqsim/pull/4415)).
- Correct electrolyte derivatives, CO2/KCl response, MEG alias data, DEA
  identity and chemistry initialization; retain traceable salt-free and
  full-family Dholabhai hydrate assessments
  ([#4332](https://github.com/equinor/neqsim/pull/4332),
  [#4365](https://github.com/equinor/neqsim/pull/4365),
  [#4367](https://github.com/equinor/neqsim/pull/4367),
  [#4408](https://github.com/equinor/neqsim/pull/4408),
  [#4411](https://github.com/equinor/neqsim/pull/4411),
  [#4441](https://github.com/equinor/neqsim/pull/4441),
  [#4444](https://github.com/equinor/neqsim/pull/4444),
  [#4453](https://github.com/equinor/neqsim/pull/4453),
  [#4466](https://github.com/equinor/neqsim/pull/4466)).
  These assessments retain residuals and limitations; they do not qualify all
  electrolyte/hydrate mixtures. See
  [CO2/KCl provenance](../thermo/co2_kcl_interaction_provenance.md) and
  [barite qualification gaps](../thermo/pitzer_barite_qualification.md).

### Process equipment, networks, and optimization

- Improve compressor speed/efficiency solve reporting, pump-map speed-slice
  and BEP handling, heater input invalidation, exchanger rating invalidation,
  and distillation duties calculated from published streams
  ([#4326](https://github.com/equinor/neqsim/pull/4326),
  [#4401](https://github.com/equinor/neqsim/pull/4401),
  [#4410](https://github.com/equinor/neqsim/pull/4410),
  [#4431](https://github.com/equinor/neqsim/pull/4431),
  [#4439](https://github.com/equinor/neqsim/pull/4439),
  [#4445](https://github.com/equinor/neqsim/pull/4445),
  [#4449](https://github.com/equinor/neqsim/pull/4449),
  [#4461](https://github.com/equinor/neqsim/pull/4461)).
- Add mapped rotating-equipment power and live injection-route checks;
  retain failure states and shared-reservoir refinement evidence
  ([#4317](https://github.com/equinor/neqsim/pull/4317),
  [#4322](https://github.com/equinor/neqsim/pull/4322),
  [#4328](https://github.com/equinor/neqsim/pull/4328),
  [#4361](https://github.com/equinor/neqsim/pull/4361),
  [#4371](https://github.com/equinor/neqsim/pull/4371)).
- Persist and revalidate field and lifecycle operating-envelope evidence,
  accepted states, period changes, hard-constraint boundaries, and sampled
  operating-region transitions
  ([#4312](https://github.com/equinor/neqsim/pull/4312),
  [#4342](https://github.com/equinor/neqsim/pull/4342),
  [#4359](https://github.com/equinor/neqsim/pull/4359),
  [#4370](https://github.com/equinor/neqsim/pull/4370),
  [#4399](https://github.com/equinor/neqsim/pull/4399),
  [#4430](https://github.com/equinor/neqsim/pull/4430),
  [#4447](https://github.com/equinor/neqsim/pull/4447),
  [#4460](https://github.com/equinor/neqsim/pull/4460)).
- Add opt-in, experimental non-equilibrium MEG/water film transfer after
  pigging and conservative signed-face transitions. The film route is
  isothermal and single-stage Euler with explicit unsupported combinations;
  do not assume general transient drying qualification
  ([#4282](https://github.com/equinor/neqsim/pull/4282),
  [#4339](https://github.com/equinor/neqsim/pull/4339),
  [#4350](https://github.com/equinor/neqsim/pull/4350),
  [film-transfer guide](../process/TwoFluidPipeFilmDrying.md)).
- Add mass-based blending of complete TBP tables and a catalytic-reforming
  balance, with source-bounded refinery evidence
  ([#4448](https://github.com/equinor/neqsim/pull/4448),
  [#4454](https://github.com/equinor/neqsim/pull/4454),
  [#4465](https://github.com/equinor/neqsim/pull/4465)).

### Continuous tasks, MCP, and development tools

- Extend living tasks with ensemble calibration, a solve-loop runner,
  backtesting, drift tuning, persisted handoffs, and living/current-best/final
  reports. Keep baseline promotion and production use subject to the existing
  review gates
  ([#4420](https://github.com/equinor/neqsim/pull/4420),
  [#4422](https://github.com/equinor/neqsim/pull/4422),
  [#4468](https://github.com/equinor/neqsim/pull/4468),
  [continuous-task guide](CONTINUOUS_TASK_SOLVING.md)).
- Coordinate categorized workflow names, capability-coverage regeneration,
  retained electrolyte evidence, and numerical validation. Retain the MCP
  distribution and its own artifact version while updating its NeqSim core
  dependency to 3.26.0
  ([#4315](https://github.com/equinor/neqsim/pull/4315),
  [#4374](https://github.com/equinor/neqsim/pull/4374),
  [#4433](https://github.com/equinor/neqsim/pull/4433)).

## Planned distributions

| Distribution | Version or expected asset | Runtime |
| --- | --- | --- |
| Core Maven artifact | `com.equinor.neqsim:neqsim:3.26.0` | Java 17+ |
| Core standalone library | `neqsim-3.26.0.jar` | Java 17+ |
| Java 8 Maven artifact | `com.equinor.neqsim:neqsim:3.26.0-Java8` | Java 8+ |
| Java 8 standalone library | `neqsim-3.26.0-Java8.jar` | Java 8+ |
| MCP runner and checksum | `neqsim-mcp-server-3.26.0-runner.jar` and `.jar.sha256` | Java 21+ |
| MCP container | `ghcr.io/equinor/neqsim-mcp-server:3.26.0` | Container runtime |

The MCP module's own Maven artifact version remains `1.0.0-SNAPSHOT`;
its `revision` and `neqsim.version` resolve to the coordinated core version.
The runner's public filename and container tag use the core release version.
The Python package has its own publishing process; changing these POMs does
not publish a new Python package.

The wrapper is pinned to Maven 3.9.16, the publishing baseline qualified in
[3.25.0 preparation](https://github.com/equinor/neqsim/pull/4318). The Java 8
release profile retains central-publishing 0.11.0 while the standard profile
uses 0.12.0. Maven 3.10.0 was reintroduced after 3.25.0, but its known
artifact-level metadata issue still affects the retained 0.11.0 profile.
Qualify the bundles from both profiles before advancing the wrapper again.
Both profiles retain `waitUntil=published`.

## Release completion requirements

1. Complete the compatibility/NRC review above and record the release owner's
   version decision. Recheck these notes against the final release commit.
2. Require successful configured CI and security checks on the final candidate,
   including both supported core configurations, numerical/model specifications,
   slow-test shards, documentation, formatting, and packaged MCP protocol tests.
   A skipped numerical job or a same-version serialization test is not evidence
   of full release compatibility.
3. Build the core Java 17 and Java 8 distributions, sources/Javadoc, and the
   Java 21 MCP runner; inspect artifact versions, dependencies and checksums.
   Preserve the Java 8 dependency pins and verify the Java 8 runtime separately.
4. Merge the reviewed version PR. The existing default-branch workflow can
   create a draft release with built assets; the preparation branch does not
   authorize publication. Include these migration notes in the release body
   together with the generated change list.
5. Publish only after the release owner approves. Verify both Maven Central
   versions reach `PUBLISHED`, all GitHub assets and the MCP checksum are
   downloadable, and the MCP container build succeeds. Record the final tag,
   commit, workflow results and publication time in the release record.

Local and hosted validation evidence belongs in the preparation PR. This
document lists candidate changes and required gates; it does not claim those
gates have passed or that the planned artifacts already exist.
