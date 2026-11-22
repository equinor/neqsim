---
title: "NeqSim 3.27.0 release and migration notes"
description: "Changes since 3.26.0, numerical and saved-model migration checks, experimental limits, coordinated distributions, and publication requirements for NeqSim 3.27.0."
---

# NeqSim 3.27.0

**Status: prepared release candidate; publication pending.** Prepared on
2026-10-31 from master `3c1c5dda4a58cbab78330612889e377b57f7793c`,
covering **232 commits** after
[v3.26.0](https://github.com/equinor/neqsim/releases/tag/v3.26.0).
Only changes merged into that snapshot are included. The version-preparation
PR does not publish artifacts or create a tag.

[Complete change list for this snapshot](https://github.com/equinor/neqsim/compare/v3.26.0...3c1c5dda4a58cbab78330612889e377b57f7793c).

## Review before upgrading

### Numerical changes and model qualification

Rebaseline saved engineering cases against their measured data and balance
checks. Results can change for wet-gas and trace-water flashes, CPA caloric
properties, Huron–Vidal derivatives, compressor chart calculations, transport
properties, reservoir depletion and two-fluid transient energy balances.
Passing software tests does not qualify every fluid or operating range.

- Water-dew-point calculations now enforce gas/aqueous fugacity equilibrium;
  GERG-water temperature derivatives and CPA boundary/hydrate-amount paths
  have additional consistency checks
  ([#4558](https://github.com/equinor/neqsim/pull/4558),
  [#4645](https://github.com/equinor/neqsim/pull/4645),
  [#4728](https://github.com/equinor/neqsim/pull/4728),
  [#4731](https://github.com/equinor/neqsim/pull/4731)).
  Water, ice and hydrate boundaries remain distinct calculations. Select the
  intended EOS, mixing rule and phase options; one boundary test is not a
  general inhibitor/oil/multiphase qualification.
- Compressor chart mode honors the selected polytropic method and resolves
  small wet-gas shaft duties more accurately. Compare pressure rise, head,
  efficiency and power when rerunning calibrated maps
  ([#4721](https://github.com/equinor/neqsim/pull/4721),
  [#4735](https://github.com/equinor/neqsim/pull/4735),
  [#4739](https://github.com/equinor/neqsim/pull/4739)).
- `EmissionsTracker` uses methane GWP **28**, consistent with the other
  IPCC AR5 emission calculators. Recorded CO2-equivalent totals may change
  ([#4751](https://github.com/equinor/neqsim/pull/4751)).
- Unsupported PVT-regression targets and invalid heavy-TBP estimates fail
  explicitly. Revisit previously accepted input rather than suppressing the
  validation
  ([#4540](https://github.com/equinor/neqsim/pull/4540),
  [#4570](https://github.com/equinor/neqsim/pull/4570)).

### Saved models and public contracts

[#4630](https://github.com/equinor/neqsim/pull/4630) repairs loading of older
`.neqsim` files, and thermodynamic deserialization reconciles inconsistent
total-mole inventories. Validate representative prior-release files, equipment
state, units, component inventories and rerun results before migrating an
application. Same-version serialization round trips do not establish
compatibility with every older object graph. A complete API and cross-version
persistence assessment remains a release gate; this candidate does not claim
universal backward compatibility.

When upgrading from before 3.26.0, also follow the
[3.26.0 migration notes](release-notes-3.26.0.md), particularly the JAMA-to-EJML
interfaces, saved numerical-solver state and corrected E300 critical-volume
units. Those changes already shipped in 3.26.0. Any newly identified breaking
contract must follow [governance](../../GOVERNANCE.md) and the
[API lifecycle policy](api-lifecycle.md), including the required NRC and major
contract review; preparing the requested number does not waive those gates.

### Runtime and packaging

The standard core artifact requires **Java 17+**, the separate Java 8 artifact
requires **Java 8+**, and the MCP runner requires **Java 21+**. Java source in
the repository remains Java 8 compatible. The Python development/report
toolkit now requires **Python 3.11+**, following the Pillow security update;
that requirement is separate from the core Java artifacts
([#4542](https://github.com/equinor/neqsim/pull/4542),
[#4713](https://github.com/equinor/neqsim/pull/4713)).

## Main changes since 3.26.0

### Thermodynamics, physical properties and PVT

- Conserve multiphase roots and phase amounts, safely recycle removed phase
  storage, equilibrate late aqueous seeds, refresh translated UMR-CPA state,
  and stop stalled PH iterations
  ([#4502](https://github.com/equinor/neqsim/pull/4502),
  [#4526](https://github.com/equinor/neqsim/pull/4526),
  [#4533](https://github.com/equinor/neqsim/pull/4533),
  [#4629](https://github.com/equinor/neqsim/pull/4629),
  [#4649](https://github.com/equinor/neqsim/pull/4649),
  [#4658](https://github.com/equinor/neqsim/pull/4658)).
- Modernize PR-CPA and caloric consistency; expose higher and mixed site
  derivatives and an opt-in four-site polarity topology. Qualify SRK/PR
  Huron–Vidal derivatives and clone isolation, and reduce temporary derivative
  allocation. Measured MEG, nitrogen-water and CO2 assessments retain their
  documented limits; they do not qualify every parameter transfer
  ([#4516](https://github.com/equinor/neqsim/pull/4516),
  [#4521](https://github.com/equinor/neqsim/pull/4521),
  [#4577](https://github.com/equinor/neqsim/pull/4577),
  [#4637](https://github.com/equinor/neqsim/pull/4637),
  [#4648](https://github.com/equinor/neqsim/pull/4648),
  [#4660](https://github.com/equinor/neqsim/pull/4660),
  [#4664](https://github.com/equinor/neqsim/pull/4664),
  [#4706](https://github.com/equinor/neqsim/pull/4706),
  [#4744](https://github.com/equinor/neqsim/pull/4744),
  [#4748](https://github.com/equinor/neqsim/pull/4748)).
- Validate interfacial-tension models, isolate transport clone state, correct
  diffusion volumes and liquid pair invariants, align Chung/CO2 models, and
  add bounded Letsou–Stiel viscosity and Watson latent-heat estimates
  ([#4499](https://github.com/equinor/neqsim/pull/4499),
  [#4507](https://github.com/equinor/neqsim/pull/4507),
  [#4562](https://github.com/equinor/neqsim/pull/4562),
  [#4572](https://github.com/equinor/neqsim/pull/4572),
  [#4581](https://github.com/equinor/neqsim/pull/4581),
  [#4606](https://github.com/equinor/neqsim/pull/4606),
  [#4640](https://github.com/equinor/neqsim/pull/4640),
  [#4686](https://github.com/equinor/neqsim/pull/4686),
  [#4725](https://github.com/equinor/neqsim/pull/4725)).
- Preserve Pedersen plus-fraction mass; correct isothermal swelling,
  near-critical black-oil/PVTO conversion, and CVD/DLE regression path history.
  Add [PVT characterization studies and reports](../pvtsimulation/pvt_fluid_study_report.md),
  verified E300 handoff and calibrated EOS checkpoints
  ([#4559](https://github.com/equinor/neqsim/pull/4559),
  [#4569](https://github.com/equinor/neqsim/pull/4569),
  [#4594](https://github.com/equinor/neqsim/pull/4594),
  [#4602](https://github.com/equinor/neqsim/pull/4602),
  [#4609](https://github.com/equinor/neqsim/pull/4609),
  [#4621](https://github.com/equinor/neqsim/pull/4621),
  [#4747](https://github.com/equinor/neqsim/pull/4747)).

### Reservoir history and well-to-host systems

- Add generalized Rs/Rv material-balance history estimates, measured component
  history replay and fitting, depletion-path PVT, physical finite-aquifer
  coupling and joint Bayesian history scenarios
  ([#4741](https://github.com/equinor/neqsim/pull/4741),
  [#4753](https://github.com/equinor/neqsim/pull/4753),
  [#4759](https://github.com/equinor/neqsim/pull/4759)). See the
  [material-balance](../pvtsimulation/reservoir_material_balance.md) and
  [aquifer uncertainty](../pvtsimulation/reservoir_aquifer_uncertainty.md)
  guides for the supported assumptions and uncertainty interpretation.
- Preserve layered well definitions through deep copies; make water-injection
  mass/PVT bases explicit; solve active multi-zone allocation and radial-Darcy
  coupling; add deviated tubing surveys, VFP lift curves and VFPPROD hydraulics
  ([#4475](https://github.com/equinor/neqsim/pull/4475),
  [#4490](https://github.com/equinor/neqsim/pull/4490),
  [#4506](https://github.com/equinor/neqsim/pull/4506),
  [#4512](https://github.com/equinor/neqsim/pull/4512),
  [#4520](https://github.com/equinor/neqsim/pull/4520),
  [#4531](https://github.com/equinor/neqsim/pull/4531),
  [#4557](https://github.com/equinor/neqsim/pull/4557),
  [#4676](https://github.com/equinor/neqsim/pull/4676)).

### Process equipment, control and optimization

- Add common compressor shafts, driver/site power limits, fixed-speed map
  operating-point classification, turboexpander nozzle/IGV/JT-bypass closure,
  fixed-geometry ejector off-design screening, analyser sample-system checks,
  and optional emulsion-separation studies
  ([#4661](https://github.com/equinor/neqsim/pull/4661),
  [#4667](https://github.com/equinor/neqsim/pull/4667),
  [#4691](https://github.com/equinor/neqsim/pull/4691),
  [#4694](https://github.com/equinor/neqsim/pull/4694),
  [#4695](https://github.com/equinor/neqsim/pull/4695),
  [#4700](https://github.com/equinor/neqsim/pull/4700),
  [#4715](https://github.com/equinor/neqsim/pull/4715)).
- Add bounded transactional adaptive steps, sampled pressure/level transmitter
  chains, transactional valve actuators, embedded LCV/PCV tests, exact safety
  event timing, final-element fault response, and an opt-in run watchdog
  ([#4650](https://github.com/equinor/neqsim/pull/4650),
  [#4665](https://github.com/equinor/neqsim/pull/4665),
  [#4672](https://github.com/equinor/neqsim/pull/4672),
  [#4685](https://github.com/equinor/neqsim/pull/4685),
  [#4693](https://github.com/equinor/neqsim/pull/4693),
  [#4702](https://github.com/equinor/neqsim/pull/4702),
  [#4738](https://github.com/equinor/neqsim/pull/4738),
  [#4752](https://github.com/equinor/neqsim/pull/4752)).
- Extend lifecycle constraint coverage, boundary refinement, non-additive
  relief portfolios, sampled two-dimensional envelopes, route-conditioned
  separator pressure and common-shaft speed actions
  ([#4474](https://github.com/equinor/neqsim/pull/4474),
  [#4624](https://github.com/equinor/neqsim/pull/4624),
  [#4677](https://github.com/equinor/neqsim/pull/4677),
  [#4704](https://github.com/equinor/neqsim/pull/4704),
  [#4729](https://github.com/equinor/neqsim/pull/4729),
  [#4742](https://github.com/equinor/neqsim/pull/4742),
  [#4756](https://github.com/equinor/neqsim/pull/4756)).
- Add bounded refinery conversion and hydrogen-supply balances and correct
  electrolyzer water conservation/Faradaic modes and the alkaline efficiency
  reference
  ([#4486](https://github.com/equinor/neqsim/pull/4486),
  [#4488](https://github.com/equinor/neqsim/pull/4488),
  [#4493](https://github.com/equinor/neqsim/pull/4493),
  [#4696](https://github.com/equinor/neqsim/pull/4696),
  [#4699](https://github.com/equinor/neqsim/pull/4699)).

### Transient pipelines and safety: retain experimental limits

Two-fluid updates conserve phase-appearance sources, boundary/component energy,
node thermodynamics and mechanical pressure at closed walls, with improved
thermal recovery and stiff friction treatment
([#4608](https://github.com/equinor/neqsim/pull/4608),
[#4611](https://github.com/equinor/neqsim/pull/4611),
[#4673](https://github.com/equinor/neqsim/pull/4673),
[#4679](https://github.com/equinor/neqsim/pull/4679),
[#4689](https://github.com/equinor/neqsim/pull/4689),
[#4732](https://github.com/equinor/neqsim/pull/4732),
[#4758](https://github.com/equinor/neqsim/pull/4758)). Review the canonical
[two-fluid evidence matrix](../process/twofluidpipe-evidence-matrix.md).
These individual repairs do not establish full rich-gas blowdown, terrain
pooling and repressurization/evaporation qualification.

The experimental Safety Shutdown acoustic path adds exact valve-travel
timing, conservative cell inventory, bounded forward pump curves and
lift-check boundaries
([#4708](https://github.com/equinor/neqsim/pull/4708),
[#4714](https://github.com/equinor/neqsim/pull/4714),
[#4720](https://github.com/equinor/neqsim/pull/4720),
[#4724](https://github.com/equinor/neqsim/pull/4724),
[#4734](https://github.com/equinor/neqsim/pull/4734),
[#4746](https://github.com/equinor/neqsim/pull/4746)). Respect its documented
fluid, wave-speed, pump-flow and boundary assumptions. This is not a general
multiphase hydraulic-hammer or safety-system certification.

Live release-inventory studies now gate outlet withdrawal using current phase
geometry ([#4690](https://github.com/equinor/neqsim/pull/4690)); see the
[coupled inventory guide](../process/safety/coupled-release-inventory.md).

### Structure-H hydrate data: preliminary qualification only

[#4754](https://github.com/equinor/neqsim/pull/4754) adds 24 digitized published
experimental markers, provenance, reproducible system-correlation fits and
checks. These coefficients describe complete methane/water/promoter systems;
they are **not component Langmuir/Kihara parameters**, do not update COMP with
qualified sH parameters, and do not enable structure H in hydrate flashes.
Executable hydrate models still support sI and sII. See the
[structure-H qualification record](../thermo/structure_h_parameter_qualification.md).

### Engineering reports, agents and development tools

Add process, PVT, flow-assurance, SURF, utilities and onshore/offshore layout
study/report workflows, with executable documentation, equipment tables,
drawings, capability discovery and PFD layout review
([#4503](https://github.com/equinor/neqsim/pull/4503),
[#4578](https://github.com/equinor/neqsim/pull/4578),
[#4592](https://github.com/equinor/neqsim/pull/4592),
[#4604](https://github.com/equinor/neqsim/pull/4604),
[#4612](https://github.com/equinor/neqsim/pull/4612),
[#4613](https://github.com/equinor/neqsim/pull/4613),
[#4749](https://github.com/equinor/neqsim/pull/4749),
[#4757](https://github.com/equinor/neqsim/pull/4757)). Plugin-only installs gain
required report modules and explicit engine-version checks
([#4643](https://github.com/equinor/neqsim/pull/4643)). Engineering-coverage CI
retains validation while avoiding writes to protected master
([#4763](https://github.com/equinor/neqsim/pull/4763)).

## Planned distributions

| Distribution | Version or expected asset | Runtime |
| --- | --- | --- |
| Core Maven artifact | `com.equinor.neqsim:neqsim:3.27.0` | Java 17+ |
| Core standalone library | `neqsim-3.27.0.jar` | Java 17+ |
| Java 8 Maven artifact | `com.equinor.neqsim:neqsim:3.27.0-Java8` | Java 8+ |
| Java 8 standalone library | `neqsim-3.27.0-Java8.jar` | Java 8+ |
| MCP runner and checksum | `neqsim-mcp-server-3.27.0-runner.jar` and `.jar.sha256` | Java 21+ |
| MCP container | `ghcr.io/equinor/neqsim-mcp-server:3.27.0` | Container runtime |

The MCP module's own Maven identity remains `1.0.0-SNAPSHOT`; its core
`revision`/`neqsim.version` resolve to 3.27.0. The runner filename and container
tag use the core release version. The Python package and agent-plugin
marketplace have separate publication processes.

The preparation restores Maven **3.9.16**, the publishing baseline used by
3.25.0 and 3.26.0. Dependency update
[#4615](https://github.com/equinor/neqsim/pull/4615) had reintroduced 3.10.0
while the Java 8 release profile still uses central-publishing **0.11.0**.
[#4318](https://github.com/equinor/neqsim/pull/4318) documents the unwanted local
metadata in that combination's publication bundle. Keep the standard profile
at 0.12.0 and both profiles at `waitUntil=published`; qualify both bundles
before advancing the wrapper. This preparation changes no library dependency
or simulation source.

## Release completion requirements

1. Review these notes against the final candidate and confirm API and saved-model
   compatibility, migration instructions and any required contract decisions.
2. Require configured CI and security gates on the final candidate: full core
   tests on supported configurations, slow-test shards, model specifications,
   formatting, documentation/Javadoc, coverage and packaged MCP protocol tests.
   Parent-commit results do not establish final-candidate success.
3. Build and inspect the Java 17/Java 8 libraries, sources/Javadoc, and Java 21
   MCP runner. Verify embedded versions, class-file targets, Java 8 dependency
   pins and runtime behavior, and MCP checksums. Local POM validation alone is
   not package or protocol qualification.
4. Merge the reviewed version PR. The default-branch workflow can build assets
   and create a draft release. Include these notes and the generated change list
   in the release body. Preparation alone does not authorize publication.
5. After release-owner approval, publish and verify both Maven Central versions
   reach `PUBLISHED`, GitHub assets/checksums are downloadable, and the MCP
   container is available. Record the final tag, commit and workflow evidence.

Local and hosted validation evidence is recorded in the preparation PR. These
notes describe candidate changes and required gates; the expected artifacts
above are not asserted to be published or available.
