---
title: "NeqSim 3.28.0 release and migration notes"
description: "Changes since 3.27.0, DEXPI 2.0.1 P&ID review, numerical and saved-model migration checks, experimental limits, distributions and publication requirements."
---

# NeqSim 3.28.0

**Status: prepared release candidate; publication pending.** Prepared on
2026-11-03 from master `047cb7e4c59101404bcc3aa8f01761630ca1d87f`,
covering **75 commits** after
[v3.27.0](https://github.com/equinor/neqsim/releases/tag/v3.27.0).
Only changes in that merged snapshot are included. The preparation PR does not
create a release tag or publish artifacts.

[Complete change list for this snapshot](https://github.com/equinor/neqsim/compare/v3.27.0...047cb7e4c59101404bcc3aa8f01761630ca1d87f).

## Review before upgrading

### Numerical behavior and saved models

Rebaseline representative engineering cases against measurements, material and
energy balances. Saturation/PS roots, equilibrium sensitivities, CVD cell-volume
restoration, wet laboratory depletion, compressor chart power, TEG absorption
and transient pipeline energy/phase transport have changed. Passing a software
test does not qualify all fluids or operating ranges.

- Pressure-flash roots now require fugacity closure; PS recovery and bracketed
  saturation paths have corrected root handling and reduced repeated work
  ([#4784](https://github.com/equinor/neqsim/pull/4784),
  [#4831](https://github.com/equinor/neqsim/pull/4831)). Compare phase identity,
  equilibrium residuals, enthalpy/entropy and calculated boundary conditions.
- Equilibrium sensitivities and batched property gradients have corrections
  ([#4786](https://github.com/equinor/neqsim/pull/4786)). Recheck downstream
  optimization and uncertainty calculations that consume derivatives.
- Compressor chart power is local to each run
  ([#4775](https://github.com/equinor/neqsim/pull/4775)); rerun reused equipment
  objects and compare power, head and pressure after input changes.
- Horner buildup permeability, skin and extrapolated pressure are corrected
  ([#4829](https://github.com/equinor/neqsim/pull/4829),
  [#4835](https://github.com/equinor/neqsim/pull/4835)). Reassess existing fitted
  well-test results rather than carrying old derived values forward.
- `SimpleTEGAbsorber` handles hydrocarbon liquid, with additional CPA TEG/MEG
  coverage ([#4840](https://github.com/equinor/neqsim/pull/4840)). Qualify the
  actual feed phases, carryover and dehydration duty for each application.

Load representative prior-release `.neqsim` files and verify retained equipment
state, controllers, component inventories, units and rerun results. Added fields
and same-version round trips alone do not prove cross-version persistence.
Public API and saved-model compatibility review remains a release gate; this
preparation does not claim universal backward compatibility. Any incompatible
contract must follow [governance](../../GOVERNANCE.md) and the
[API lifecycle policy](api-lifecycle.md) before publication.

Retain the [3.27.0 migration notes](release-notes-3.27.0.md) and, when upgrading
from an earlier release, the [3.26.0 migration notes](release-notes-3.26.0.md),
including the JAMA-to-EJML interface migration and E300 critical-volume units.

### Runtime and publishing toolchain

The standard core artifact requires **Java 17+**, the separate Java 8 artifact
requires **Java 8+**, and the MCP runner requires **Java 21+**. Core and test
source remains Java 8 compatible. Repository tooling, reports and notebooks
require **Python 3.11+**, separately from the Java runtime requirements.

Restore the Maven wrapper to **3.9.16**, the publishing baseline used for
3.27.0. [#4811](https://github.com/equinor/neqsim/pull/4811) reintroduced Maven
3.10.0 while the Java 8 release profile still uses central-publishing **0.11.0**.
[#4318](https://github.com/equinor/neqsim/pull/4318) records the local-metadata
publication-bundle issue with that combination. Retain standard-profile
central-publishing **0.12.0** and both profiles' `waitUntil=published` settings;
qualify both bundles before advancing the wrapper. Other merged dependency
updates, including Gson, Enforcer and JaCoCo, remain in this candidate.

## Main changes since 3.27.0

### DEXPI 2.0.1, PFDs and instrumented P&IDs

Native DEXPI 2.0.1 packages now connect tagged process models, instrumentation
and safeguarding proposals, physical projections, drawing registers and
steady-state/dynamic simulation bindings
([#4836](https://github.com/equinor/neqsim/pull/4836),
[#4839](https://github.com/equinor/neqsim/pull/4839),
[#4845](https://github.com/equinor/neqsim/pull/4845)). Native PFD and instrumented
P&ID review drawings have reconciled topology, auxiliary line identities,
physical sensing connections and valve symbols
([#4848](https://github.com/equinor/neqsim/pull/4848)).

The [P&ID generation guide](../pid-design-synthesis.md) and
[quality review workflow](../engineering/pid-quality-review.md) describe the
required evidence. The reviewer checks actual package hashes, native references,
registers, drawing tag coverage and layout, then retains separate visual and
controlled-standards review records. Regeneration invalidates earlier receipts.
Missing design inputs or reviews remain HOLD; inconsistencies remain FAIL.

`PASS_FOR_ENGINEERING_REVIEW` is a bounded review disposition. It does not assign
ISO/IEC/ISA conformance, CAE receiving-tool qualification, construction fitness,
SIL or operating approval. Use project-controlled standard editions and actual
visual/physical review. A generated drawing or DEXPI export alone is insufficient.

The professional process-overview workflow also adds A3/slide output and repairs
small-flow preservation and slide validation
([#4777](https://github.com/equinor/neqsim/pull/4777),
[#4796](https://github.com/equinor/neqsim/pull/4796)). Physical isolation-inventory
evidence is retained in layout studies
([#4790](https://github.com/equinor/neqsim/pull/4790)).

### Laboratory PVT and reservoir handoff

Native depletion paths preserve actual phases and inventories through phase
appearance, closed-mass CCE and wet DLE. CVD upper-dew reference and post-liquid
disappearance volume restoration have corrections. Explicit fixed-cell
depletion adds transactional execution and refined equilibrium/volume precision
([#4781](https://github.com/equinor/neqsim/pull/4781),
[#4792](https://github.com/equinor/neqsim/pull/4792),
[#4797](https://github.com/equinor/neqsim/pull/4797),
[#4806](https://github.com/equinor/neqsim/pull/4806),
[#4819](https://github.com/equinor/neqsim/pull/4819),
[#4824](https://github.com/equinor/neqsim/pull/4824),
[#4832](https://github.com/equinor/neqsim/pull/4832),
[#4843](https://github.com/equinor/neqsim/pull/4843)).

Follow the [native PVT handoff guide](../pvtsimulation/depletion_pvt_generation.md)
for path-specific assumptions. Closed-mass CCE, gas-withdrawal CVD, changing-volume
DLE and explicit initial-cell volume are distinct laboratory protocols. Absent
phases do not acquire invented formation factors. Synthetic analytical checks
are not measured laboratory holdouts or a qualified laboratory-to-field chain.

Reservoir work adds phase-selective production and separator outputs, and
source-identified bounds on laboratory gas-density handoff
([#4771](https://github.com/equinor/neqsim/pull/4771),
[#4776](https://github.com/equinor/neqsim/pull/4776)).

### Pipelines and Safety Shutdown: experimental limits remain

Two-fluid updates expose phase-resolved boundary ledgers, improve interior
hydrostatic pressure and signed work, couple stratified pressure moments and
cross-sectional gravity energy, conserve total energy in the optional Rusanov
flux, retain bidirectional phase-face transport, add a gas-only pressure outlet
and qualify bounded conservative hydrocarbon pool transfer
([#4769](https://github.com/equinor/neqsim/pull/4769),
[#4768](https://github.com/equinor/neqsim/pull/4768),
[#4778](https://github.com/equinor/neqsim/pull/4778),
[#4782](https://github.com/equinor/neqsim/pull/4782),
[#4791](https://github.com/equinor/neqsim/pull/4791),
[#4798](https://github.com/equinor/neqsim/pull/4798),
[#4809](https://github.com/equinor/neqsim/pull/4809),
[#4818](https://github.com/equinor/neqsim/pull/4818),
[#4821](https://github.com/equinor/neqsim/pull/4821),
[#4838](https://github.com/equinor/neqsim/pull/4838)).

Review the [two-fluid evidence matrix](../process/twofluidpipe-evidence-matrix.md).
These bounded repairs do not establish complete rich-gas blowdown, terrain
pooling and same-gas repressurization/evaporation qualification. Safety Shutdown
acoustics retain their documented fluid, wave-speed, pump and boundary limits;
no general multiphase hydraulic-hammer qualification is claimed.

Release-source studies add external heat coupling and finite-rate retained phase
inventories ([#4807](https://github.com/equinor/neqsim/pull/4807),
[#4820](https://github.com/equinor/neqsim/pull/4820)); consult the
[coupled release-inventory guide](../process/safety/coupled-release-inventory.md).

### Controls, optimization and reusable engineering evidence

Sampled temperature and physical flow transmitter scans gain dynamic
qualification, alongside TEG restart/sample-system dynamics, oscillation analysis,
compressor load limiting and time-series process replay
([#4800](https://github.com/equinor/neqsim/pull/4800),
[#4825](https://github.com/equinor/neqsim/pull/4825),
[#4834](https://github.com/equinor/neqsim/pull/4834),
[#4840](https://github.com/equinor/neqsim/pull/4840),
[#4841](https://github.com/equinor/neqsim/pull/4841)).

Engineering evidence gains revision-controlled scenarios, map-to-driver migration
records/timelines linked to lifecycle replay, optimizer termination reporting,
discipline-report manifests and a runnable field-acceptance workflow
([#4766](https://github.com/equinor/neqsim/pull/4766),
[#4780](https://github.com/equinor/neqsim/pull/4780),
[#4789](https://github.com/equinor/neqsim/pull/4789),
[#4799](https://github.com/equinor/neqsim/pull/4799),
[#4803](https://github.com/equinor/neqsim/pull/4803),
[#4804](https://github.com/equinor/neqsim/pull/4804),
[#4817](https://github.com/equinor/neqsim/pull/4817),
[#4827](https://github.com/equinor/neqsim/pull/4827),
[#4837](https://github.com/equinor/neqsim/pull/4837)). Evidence remains specific
to the executed model, revision, inputs and acceptance criteria.

Huron–Vidal source-root checks, selected-pair cost evidence and retained CO2-MEG
and nitrogen-water primary-source receipts advance qualification without turning
quarantined or held evaluator proposals into accepted model parameters
([#4772](https://github.com/equinor/neqsim/pull/4772),
[#4783](https://github.com/equinor/neqsim/pull/4783),
[#4805](https://github.com/equinor/neqsim/pull/4805),
[#4823](https://github.com/equinor/neqsim/pull/4823),
[#4842](https://github.com/equinor/neqsim/pull/4842)). Structure-H hydrate data
retain the [3.27.0 limits](release-notes-3.27.0.md); no new executable sH support
is claimed. Documentation gains executable examples and corrected API navigation,
including literal physical-property examples
([#4774](https://github.com/equinor/neqsim/pull/4774),
[#4844](https://github.com/equinor/neqsim/pull/4844),
[#4846](https://github.com/equinor/neqsim/pull/4846)).

## Planned distributions

| Distribution | Version or expected asset | Runtime |
| --- | --- | --- |
| Core Maven artifact | `com.equinor.neqsim:neqsim:3.28.0` | Java 17+ |
| Core standalone library | `neqsim-3.28.0.jar` | Java 17+ |
| Java 8 Maven artifact | `com.equinor.neqsim:neqsim:3.28.0-Java8` | Java 8+ |
| Java 8 standalone library | `neqsim-3.28.0-Java8.jar` | Java 8+ |
| MCP runner and checksum | `neqsim-mcp-server-3.28.0-runner.jar` and `.jar.sha256` | Java 21+ |
| MCP container | `ghcr.io/equinor/neqsim-mcp-server:3.28.0` | Container runtime |

The MCP module retains its own `1.0.0-SNAPSHOT` Maven identity; its core
`revision`/`neqsim.version`, runner filename and container tag use 3.28.0.
Python and plugin-marketplace packages have separate release processes.

## Release completion requirements

1. Review the final candidate, API and saved-model compatibility, migration notes
   and any contract decisions required by governance.
2. Require the configured CI and security gates on the final head: core and slow
   tests, model specifications, formatting, documentation/Javadoc and packaged
   Java 21 MCP protocol qualification. Parent results do not qualify this head.
3. Build and inspect Java 17/Java 8 libraries, sources/Javadoc and the MCP runner.
   Verify embedded versions, class-file targets, pinned Java 8 dependencies,
   actual supported JVM behavior, checksums and both publication bundles.
4. Merge the reviewed version PR to let the existing default-branch workflow
   build a draft release. Include these notes with the generated change list.
5. After release-owner approval, publish and verify both Maven Central versions
   reach `PUBLISHED`, the GitHub assets/checksums are downloadable and the MCP
   container exists. Record the final tag, commit and workflow evidence.

The preparation PR records actual local/hosted validation and pending gates.
The expected distributions above are not asserted to be published or available.
