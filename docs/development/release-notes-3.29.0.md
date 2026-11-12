---
title: "NeqSim 3.29.0 release and migration notes"
description: "Changes since 3.28.0, LBC viscosity, ISO 6976, E300 and flash corrections, distillation convergence reporting, process performance, experimental limits, distributions and publication requirements."
---

# NeqSim 3.29.0

**Status: prepared release candidate; publication pending.** Prepared on
2026-11-12 from master `b870ffbb80ff329b57e618ef1f228e48f3754da2`,
covering **87 commits** after
[v3.28.0](https://github.com/equinor/neqsim/releases/tag/v3.28.0).
Only changes in that merged snapshot are included. The preparation PR does not
create a release tag or publish artifacts.

[Complete change list for this snapshot](https://github.com/equinor/neqsim/compare/v3.28.0...b870ffbb80ff329b57e618ef1f228e48f3754da2).

## Review before upgrading

### Numerical behavior and saved models

Rebaseline representative engineering cases against measurements, material and
energy balances. Viscosity, sales-gas properties, imported E300 fluids, free-water
detection, CPA volume roots, column convergence status and scrubber design
volumes have changed. Passing a software test does not qualify all fluids or
operating ranges.

- LBC viscosity now uses pseudo-critical (Kay) mixing for the mixture
  viscosity-reducing parameter, as in Lohrenz–Bray–Clark and Eclipse E300. E300
  files with `LBCCOEF` but without `VCRITVIS` now use the file's `VCRIT` values
  as LBC critical volumes
  ([#4953](https://github.com/equinor/neqsim/pull/4953)). Compare gas and oil
  viscosity against measurements or the source simulator over the operating grid;
  see the [viscosity models](../physical_properties/viscosity_models.md) and
  [E300 import guide](../pvtsimulation/eclipse_e300_fluid_import.md).
- The ISO 6976:2016 n-decane molar mass is corrected from 42.28 to 142.28 g/mol
  ([#4920](https://github.com/equinor/neqsim/pull/4920)). Gases containing nC10
  previously had low relative density and high Wobbe index (about −0.55 % and
  +0.28 % at 0.1 mol% nC10). Recheck sales-gas specifications and reported
  calorific values. The 1995 table was not affected.
- E300 import applies `OMEGAA` to every repeated named component block and enables
  the multiphase check when the fluid contains water
  ([#4874](https://github.com/equinor/neqsim/pull/4874)); the E300 writer now
  writes `BIC`/`BICS` with round-trip precision
  ([#4958](https://github.com/equinor/neqsim/pull/4958)). Re-import and
  re-export representative decks and compare phase counts and EOS parameters.
- Multiphase TP flashes can now recover a free-water phase below the former
  five-mole-percent overall-water screen when oil water fugacity indicates
  supersaturation ([#4873](https://github.com/equinor/neqsim/pull/4873)).
  Low-water-cut cases may gain an aqueous phase; compare phase identity and
  water balances.
- Initialized zero-flow streams keep their intensive phase state
  ([#4890](https://github.com/equinor/neqsim/pull/4890)), and transient closed
  boundaries preserve exact-zero flow and enthalpy
  ([#4862](https://github.com/equinor/neqsim/pull/4862)).
- `DistillationColumn.solved()` now returns false, with status `FAILED` and an
  explanatory reason, when internal liquid and vapour flows keep accumulating
  ([#4962](https://github.com/equinor/neqsim/pull/4962)). Columns with fixed
  reboiler and condenser temperatures that previously reported convergence with
  an arbitrary reflux will now fail visibly; revisit their specifications.
- `GasScrubberMechanicalDesign` reports `volumeTotal` from the final sized scrubber
  geometry ([#4966](https://github.com/equinor/neqsim/pull/4966)). Weight, cost
  and inventory estimates derived from it may change.
- The SRK-CPA association solver refreshes association strength with g(V) before
  solving site fractions, uses the exact third-order site term and stops the
  volume iteration at numerical resolution
  ([#4956](https://github.com/equinor/neqsim/pull/4956)). Expect small differences
  in CPA densities and derivatives; compare representative glycol, methanol and
  water systems.
- `ProcessSystem` now solves units downstream of all recycle loops once after the
  loops converge (`setDeferPostRecycleUnits`, default on) for plain
  stream-connected flowsheets
  ([#4952](https://github.com/equinor/neqsim/pull/4952)). The reference flowsheet
  matched to eight digits; set it to `false` to restore the previous order if a
  flowsheet depends on intermediate downstream results.
- Fixed-cell PVT, finite and radial aquifer and Carter–Tracy calculations have
  corrections ([#4855](https://github.com/equinor/neqsim/pull/4855),
  [#4866](https://github.com/equinor/neqsim/pull/4866),
  [#4868](https://github.com/equinor/neqsim/pull/4868),
  [#4878](https://github.com/equinor/neqsim/pull/4878),
  [#4895](https://github.com/equinor/neqsim/pull/4895),
  [#4924](https://github.com/equinor/neqsim/pull/4924),
  [#4930](https://github.com/equinor/neqsim/pull/4930)). Rerun depletion and
  aquifer studies instead of carrying old derived values forward.

Saved `.neqsim` files now record the actual NeqSim version that wrote them, via
the new `neqsim.util.NeqSimVersion` helper; files from earlier releases report
`unknown` ([#4960](https://github.com/equinor/neqsim/pull/4960)). An archive keeps
the component data of its writer. Load representative prior-release files and
verify retained equipment state, controllers, inventories, units and rerun
results. Same-version round trips alone do not prove cross-version persistence.
Public API and saved-model compatibility review remains a release gate; this
preparation does not claim universal backward compatibility. Any incompatible
contract must follow [governance](../../GOVERNANCE.md) and the
[API lifecycle policy](api-lifecycle.md) before publication.

Retain the [3.28.0 migration notes](release-notes-3.28.0.md) and, when upgrading
from an earlier release, the [3.27.0](release-notes-3.27.0.md) and
[3.26.0](release-notes-3.26.0.md) migration notes.

### Runtime and publishing toolchain

The standard core artifact requires **Java 17+**, the separate Java 8 artifact
requires **Java 8+**, and the MCP runner requires **Java 21+**. Core and test
source remains Java 8 compatible. Repository tooling, reports and notebooks
require **Python 3.11+**, separately from the Java runtime requirements.

Restore the Maven wrapper to **3.9.16**, the publishing baseline used for 3.28.0.
[#4937](https://github.com/equinor/neqsim/pull/4937) reintroduced Maven 3.10.0
while the Java 8 release profile still uses central-publishing **0.11.0**.
[#4318](https://github.com/equinor/neqsim/pull/4318) records the local-metadata
publication-bundle issue with that combination. Retain standard-profile
central-publishing **0.12.0** and both profiles' `waitUntil=published` settings;
qualify both bundles before advancing the wrapper. Other merged dependency
updates (Spotless and Maven Resources plugins,
[#4938](https://github.com/equinor/neqsim/pull/4938),
[#4939](https://github.com/equinor/neqsim/pull/4939)) remain in this candidate.

## Main changes since 3.28.0

### Thermodynamics, physical properties and fluid handoff

Besides the corrections above, OLGA keyword PVT tables gain configurable axes and
envelope refinement, produced water with salinity corrections, IAPWS reference
water properties, monotonic absent-phase fill and quality/table reports
([#4954](https://github.com/equinor/neqsim/pull/4954)); see
[OLGA PVT table generation](../integration/olga_pvt_table_generation.md).
Cooling-water utilities use reference water and seawater properties
([#4908](https://github.com/equinor/neqsim/pull/4908)).

Pitzer brines gain audited automatic reactive carbonate, calcium sulfate and
sulfide parameter selection and an exact-source reactive carbonate mode
([#4896](https://github.com/equinor/neqsim/pull/4896),
[#4903](https://github.com/equinor/neqsim/pull/4903)). A new solid-gas reaction
equilibrium (FeS + H2O + CO2 = FeCO3 + H2S) and an H2S equilibration temperature
in `ReservoirH2SEstimator` support reservoir souring screening
([#4944](https://github.com/equinor/neqsim/pull/4944),
[#4947](https://github.com/equinor/neqsim/pull/4947)). Electrolyte and
Huron–Vidal diagnostics retain CO2-brine, KCl and MEG–methane evidence without
turning held proposals into accepted parameters
([#4915](https://github.com/equinor/neqsim/pull/4915),
[#4925](https://github.com/equinor/neqsim/pull/4925),
[#4929](https://github.com/equinor/neqsim/pull/4929),
[#4931](https://github.com/equinor/neqsim/pull/4931)).

### Performance

Fluid clones no longer copy 200-component work arrays, the TP-flash stability
test clones thermodynamics only, and post-recycle units are deferred; a 43-unit
offshore separation/recompression flowsheet ran in 360 ms instead of 810 ms
([#4952](https://github.com/equinor/neqsim/pull/4952)). CPA mixing-rule
derivatives and the molar-volume iteration are cheaper
([#4956](https://github.com/equinor/neqsim/pull/4956)), and cubic phase
classification and liquid-volume seeding are lazy
([#4875](https://github.com/equinor/neqsim/pull/4875)). Timing depends on the
flowsheet and hardware.

### Process equipment, power and control

Compressor power trains integrate with gas-turbine and electric supplies, and gas
turbines gain inlet/exhaust installation pressure-loss corrections (defaults
unchanged; not OEM-qualified)
([#4910](https://github.com/equinor/neqsim/pull/4910),
[#4936](https://github.com/equinor/neqsim/pull/4936)). Compressor automation,
dynamic tests, heat-exchanger P&ID/DEXPI generation and PID tuning are connected
([#4906](https://github.com/equinor/neqsim/pull/4906),
[#4923](https://github.com/equinor/neqsim/pull/4923)). Separators gain conservative
transient energy integration, cumulative energy-closure audits and a bound
control study ([#4863](https://github.com/equinor/neqsim/pull/4863),
[#4879](https://github.com/equinor/neqsim/pull/4879),
[#4892](https://github.com/equinor/neqsim/pull/4892)).

### Safety, P&ID and DEXPI

DEXPI import now reads equipment and piping components from standard Proteus XML,
including NeqSim's own exports and the DEXPI C01V04 reference example
([#4964](https://github.com/equinor/neqsim/pull/4964)). Native ESD/HIPPS and PSV
response, System Control Diagrams and ISO 15519 review gates link to P&IDs and
DEXPI; a low-low level liquid final element stays unresolved instead of defaulting
to inlet isolation ([#4862](https://github.com/equinor/neqsim/pull/4862),
[#4882](https://github.com/equinor/neqsim/pull/4882),
[#4921](https://github.com/equinor/neqsim/pull/4921)). Safety scenarios run across
process models, including quantified blocked-outlet and control-air-loss cases
([#4916](https://github.com/equinor/neqsim/pull/4916),
[#4932](https://github.com/equinor/neqsim/pull/4932)). Proposed compressor
automation and native P&ID feedback contracts are documented for review
([#4905](https://github.com/equinor/neqsim/pull/4905)).

`PASS_FOR_ENGINEERING_REVIEW` remains a bounded review disposition. It does not
assign standards conformance, SIL or operating approval; generated drawings and
scenario results require project-controlled review.

### Reservoir and laboratory PVT

A PVT forward-proxy surface-process generator, an independent gas-only
lab-to-tank chain, finite-scenario in-place parameter quantiles and independently
verified coupled-aquifer benchmarks are added
([#4885](https://github.com/equinor/neqsim/pull/4885),
[#4897](https://github.com/equinor/neqsim/pull/4897),
[#4911](https://github.com/equinor/neqsim/pull/4911),
[#4914](https://github.com/equinor/neqsim/pull/4914),
[#4935](https://github.com/equinor/neqsim/pull/4935)). Synthetic checks are not
measured laboratory holdouts or a qualified field chain.

### Pipelines, networks and pigging: experimental limits remain

Two-fluid pipes gain signed boundary donor and momentum ledgers, terrain low-point
pool reports, restart transfer-area preservation, atomic network intervals,
coupled detached pressure trials and atomic subcycling of branches. Networks solve
one or several coupled algebraic junctions with per-junction and whole-network
closure. Pigs couple to ideal-gas inventories with reversal-safe position events
([#4865](https://github.com/equinor/neqsim/pull/4865),
[#4871](https://github.com/equinor/neqsim/pull/4871),
[#4881](https://github.com/equinor/neqsim/pull/4881),
[#4888](https://github.com/equinor/neqsim/pull/4888),
[#4889](https://github.com/equinor/neqsim/pull/4889),
[#4894](https://github.com/equinor/neqsim/pull/4894),
[#4912](https://github.com/equinor/neqsim/pull/4912),
[#4919](https://github.com/equinor/neqsim/pull/4919),
[#4926](https://github.com/equinor/neqsim/pull/4926),
[#4934](https://github.com/equinor/neqsim/pull/4934)).

Review the [two-fluid evidence matrix](../process/twofluidpipe-evidence-matrix.md).
These bounded additions do not establish general rich-gas blowdown, terrain
pooling or multiphase network qualification.

### Tooling, reports and documentation

Code-health gates and an agent maintenance workflow are established
([#4904](https://github.com/equinor/neqsim/pull/4904)). Process-study reports gain
diagram layout options, a generic handoff basis and stream table, and
reader-facing wording ([#4898](https://github.com/equinor/neqsim/pull/4898),
[#4917](https://github.com/equinor/neqsim/pull/4917),
[#4955](https://github.com/equinor/neqsim/pull/4955),
[#4958](https://github.com/equinor/neqsim/pull/4958)). Agent skills add SURF
jurisdiction, seabed protection screening, an incident-history prior for root-cause
analysis and quiet child processes; plugin manifests are portable
([#4891](https://github.com/equinor/neqsim/pull/4891),
[#4927](https://github.com/equinor/neqsim/pull/4927),
[#4943](https://github.com/equinor/neqsim/pull/4943),
[#4942](https://github.com/equinor/neqsim/pull/4942),
[#4946](https://github.com/equinor/neqsim/pull/4946),
[#4959](https://github.com/equinor/neqsim/pull/4959)). CI runs fast tests in
parallel forks with a longer timeout
([#4949](https://github.com/equinor/neqsim/pull/4949),
[#4950](https://github.com/equinor/neqsim/pull/4950)). Further quick starts and
tutorials are now executed as literal examples, including DEXPI export, PVT
laboratory, E300 import, hydrate, flare, TEG, sales-contract and dew-point guides
([#4854](https://github.com/equinor/neqsim/pull/4854),
[#4864](https://github.com/equinor/neqsim/pull/4864),
[#4867](https://github.com/equinor/neqsim/pull/4867),
[#4870](https://github.com/equinor/neqsim/pull/4870),
[#4880](https://github.com/equinor/neqsim/pull/4880),
[#4884](https://github.com/equinor/neqsim/pull/4884),
[#4887](https://github.com/equinor/neqsim/pull/4887),
[#4893](https://github.com/equinor/neqsim/pull/4893),
[#4907](https://github.com/equinor/neqsim/pull/4907),
[#4918](https://github.com/equinor/neqsim/pull/4918),
[#4922](https://github.com/equinor/neqsim/pull/4922),
[#4928](https://github.com/equinor/neqsim/pull/4928),
[#4933](https://github.com/equinor/neqsim/pull/4933)).

## Planned distributions

| Distribution | Version or expected asset | Runtime |
| --- | --- | --- |
| Core Maven artifact | `com.equinor.neqsim:neqsim:3.29.0` | Java 17+ |
| Core standalone library | `neqsim-3.29.0.jar` | Java 17+ |
| Java 8 Maven artifact | `com.equinor.neqsim:neqsim:3.29.0-Java8` | Java 8+ |
| Java 8 standalone library | `neqsim-3.29.0-Java8.jar` | Java 8+ |
| MCP runner and checksum | `neqsim-mcp-server-3.29.0-runner.jar` and `.jar.sha256` | Java 21+ |
| MCP container | `ghcr.io/equinor/neqsim-mcp-server:3.29.0` | Container runtime |

The MCP module retains its own `1.0.0-SNAPSHOT` Maven identity; its core
`revision`/`neqsim.version`, runner filename and container tag use 3.29.0.
Python and plugin-marketplace packages have separate release processes.

## Release completion requirements

1. Review the final candidate, API and saved-model compatibility, migration notes
   and any contract decisions required by governance.
2. Require the configured CI and security gates on the final head: core and slow
   tests, model specifications, formatting, documentation/Javadoc and packaged
   Java 21 MCP protocol qualification. Parent results do not qualify this head.
3. Build and inspect Java 17/Java 8 libraries, sources/Javadoc and the MCP runner.
   Verify embedded versions (including the `.neqsim` writer version), class-file
   targets, pinned Java 8 dependencies, actual supported JVM behavior, checksums
   and both publication bundles.
4. Merge the reviewed version PR to let the existing default-branch workflow
   build a draft release. Include these notes with the generated change list.
5. After release-owner approval, publish and verify both Maven Central versions
   reach `PUBLISHED`, the GitHub assets/checksums are downloadable and the MCP
   container exists. Record the final tag, commit and workflow evidence.

The preparation PR records actual local/hosted validation and pending gates.
The expected distributions above are not asserted to be published or available.
