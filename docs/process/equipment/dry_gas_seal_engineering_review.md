---
title: Dry Gas Seal Systems Engineering Review
description: "Source-audited conditioning and monitoring capabilities, primary literature, standards boundaries, and validation priorities for compressor dry gas seal systems."
---

## Purpose and evidence basis

Use this review to select a calculation, investigate a seal-gas problem, or scope an
improvement. The source audit was made on NeqSim master
`4fabe2d1785104ef36d8582e1cc115702baaa2c5` on 27 October 2026. The literature
supports the proposed physics and test targets; it does not establish that those
models have been implemented or experimentally qualified in NeqSim.

Continue the existing [program roadmap #4149](https://github.com/equinor/neqsim/issues/4149),
[conditioning issue #4144](https://github.com/equinor/neqsim/issues/4144), and
[monitoring issue #4145](https://github.com/equinor/neqsim/issues/4145). Their initial
implementations are merged, but complete system dynamics and independent/OEM
qualification remain open. This review changes documentation and discovery only.

## Standards and equipment boundaries

| Reference | Verified scope and edition basis | Use in a study |
| --- | --- | --- |
| [API Standard 692](https://www.api.org/-/media/files/publications/2025-catalog/06_refining_2025.pdf) | The official API 2025 catalogue lists first edition, June 2018, for dry gas sealing systems associated with compressors and expanders. | Establish the dry-seal/support-system boundary; verify the project's controlled edition, amendments, datasheets and OEM requirements before a clause assessment. |
| [ISO 6368:2021](https://www.iso.org/standard/82220.html) | First edition, November 2021; explicitly supplements API 692 first edition (2018). It replaces ISO 10438-4:2007 and excludes clearance, restrictive-ring and oil seals. | Record the applicable ISO supplements rather than assuming API and ISO texts are identical. |
| [API Standard 614, sixth edition, 2022](https://www.apiwebstore.org/datasheets/614) | The publisher's scope covers lubrication, oil-type shaft seals and oil-control auxiliaries, explicitly excluding dry gas seal and fuel systems. | Apply it to the lube-oil boundary where relevant; do not present it as the governing dry gas seal support-system standard. |

API 617 addresses the compressor equipment context; it does not replace the dry-seal
system basis. Public catalogue/abstract metadata were checked, not the complete
licensed requirements. No clause-by-clause conformity assessment is claimed here.
Superheat, differential pressure, cleanliness, leakage limits and delays must carry
their project/OEM provenance. In particular, the existing 17 K and 20 K model defaults
are screening assumptions, not universal API 692 acceptance criteria.

Keep five flows distinct: primary seal supply, process-side labyrinth flow, primary
seal-face leakage, intermediate buffer gas, and bearing-side separation gas. A
straight-through labyrinth model does not represent a grooved dry-seal face. Primary
vent flow can contain buffer gas and other contributions; it is not automatically
equal to primary seal leakage. Trace actual branches, secondary leakage and normal
flow reference conditions before reconciling measurements.

## Current NeqSim capability audit

The following are source-backed entry points. Test names identify software checks,
not independent physical qualification.

| API and guide | Current capability | Qualification boundary |
| --- | --- | --- |
| `DryGasSealAnalyzer`; [conditioning guide](dry_gas_seal_conditioning) | Sampled seal-cavity-to-vent PH expansion, TP condensation map, hydrocarbon and aqueous liquid detection, lumped dead-leg cooling, accumulation, and GCU thermal-duty screening. `setSealGas`, `runFullAnalysis`, `getResults` and legacy `isSafeToOperate` are entry points. | The legacy Boolean covers sampled condensation evidence only. It is not a complete operating permissive, continuous-path dryness proof, hydrate/cleanliness assessment, or seal-film thermohydrodynamic solution. Dead-leg impact estimates use simplified fill/velocity assumptions, not a qualified transient pressure-wave model. `DryGasSealEvidenceTest` checks evidence handling, flow basis and retained-gas energy balance with synthetic fluids. |
| `SealGasSupplyConditioning` | `setSource`, `setSupplyPressure`, `setHeaterOutletTemperature`, `getMinimumMarginK`, `isMarginMet`, and `calculateRequiredInletTemperatureC` screen upstream regulator expansion and supply-pressure dewpoints. | Its simpler evidence handling has the source-audit concerns below. The source reference is retained by `setSealGas`; isolate a caller fluid before use. `SealGasSupplyConditioningTest` checks qualitative thermal trends, heater search and load conversions. |
| `DryGasSealMonitor`; [monitoring guide](dry_gas_seal_monitoring) | `Limits`, `Sample`, `evaluate` and `reset` provide timed, quality-aware advisory evidence for supply/process differential pressure, vents, buffer and separation gas. | Gas quality is an externally supplied Boolean. The monitor has one running/pressurized basis and a common confirmation delay; no mode-specific booster/source-switching dynamics or validated fault probabilities. `DryGasSealMonitorTest` covers fault injection, timing, recovery and serialization. |
| `LabyrinthSealFlowCalculator`; [labyrinth guide](labyrinth_seal_leakage) | Five methods for straight-through labyrinth leakage, including toothwise Neumann pressure/temperature states, real-fluid PH flashes, choking and validity diagnostics. | Use for process-side labyrinth demand within each method's range, not primary grooved-face leakage. Correlation agreement and a successful flash are distinct from independent leakage validation. |
| `CompressorThermalModel`; [thermal guide](../../compressor_thermal_model) | Lumped thermal nodes for seals, rotor/shaft, casing and bearings; steady and transient solutions with configurable links and heat capacities. | Conductances, heat allocation and capacities need machine-specific evidence. Supply temperature is not a resolved seal-film or face temperature. |
| `CompressorMechanicalLosses` | Empirical nominal leakage, buffer/separation flow and bearing-loss estimates. | Fixed coefficients and leakage clamps are screening assumptions. They do not predict film clearance, wear, reverse pressure, or abnormal leakage from first principles. |

### Supply-conditioning concerns requiring reproduction

These are findings from inspection of `SealGasSupplyConditioning.java`, not newly
executed numerical defect reproductions:

- `expand` counts liquid only if more than one phase exists, leaving a single-liquid
  outlet with zero reported liquid mass fraction.
- `liquidPresent` catches TP-flash failures and returns false. Missing dewpoint
  results can propagate through `getMinimumMarginK` as positive infinity and into
  `isMarginMet` as passing evidence.
- `dewPointC` uses a fixed -30 to 150 C interval and a monotonic bisection assumption.
  No crossing in this interval is not proof of dryness along a complete pressure path.
- `setSealGas` retains the caller reference; unknown units are silently interpreted
  as bara/C, and several setters lack finite/range checks.

First add failing regression cases for these paths under #4144. Preserve the distinction
between a supported dry result, a bounded search with no root, and unresolved evidence.
Do not weaken existing convergence or balance criteria to make a screen pass.

## Primary literature and what it would improve

| Primary source | Finding relevant to NeqSim | Evidence and limits |
| --- | --- | --- |
| Wu, Jiang, Peng, Teng and Yu (2025), [Influence of natural gas composition and operating conditions on the phase change of dry gas seals for pipeline compressors](https://doi.org/10.1016/j.ijheatfluidflow.2025.109832), *International Journal of Heat and Fluid Flow* 115, 109832. | Couples seal-face lubrication/energy behavior with real-fluid composition and phase change; a candidate basis for an optional seal-face model beyond a bulk isenthalpic expansion screen. | Numerical thermohydrodynamic study of spiral-groove seals at 8-20 MPa. Published temperature thresholds belong to the study's mixture, geometry and conditions; they are not general operating limits. Full equations and inputs must be obtained and reproduced before implementation claims. |
| Chen, Peng, Wang, Jiang and Li (2022), [Experimental and theoretical investigations on dry gas seal transient performance and its disparity with steady performance](https://doi.org/10.1177/13506501211064079), *Proceedings of the IMechE, Part J* 236(8), 1661-1673. | Measured time-varying film thickness and leakage support a transient Reynolds/face-motion model; mean leakage need not equal leakage evaluated at mean clearance. | High-speed test-rig measurements and theoretical comparison. Preserve geometry, spring loading, face deflection and measurement uncertainty before using it as a benchmark for another seal. |
| Zahorulko and Lee (2022), [Dynamic behavior and difference pressure control of difference pressure regulator for dry gas seals](https://doi.org/10.1016/j.ymssp.2021.108350), *Mechanical Systems and Signal Processing* 165, 108350. | A moving-valve force balance, chamber inventories and restrictor flows provide a basis for regulator response and instability rather than an ideal instantaneous pressure source. | Analytical, numerical and experimental work; the published tests use dry air and specific regulator geometries. A reported instability threshold is not a universal seal-system differential-pressure limit. |
| Ding and Lu (2016), [Theoretical analysis and experiment on gas film temperature in a spiral groove dry gas seal under high speed and pressure](https://doi.org/10.1016/j.ijheatmasstransfer.2016.01.045), *International Journal of Heat and Mass Transfer* 96, 438-450. | Measured face-temperature distributions and a compressible Reynolds/energy model motivate local thermal dissipation and face-temperature inputs. | Experimental and theoretical comparison for a particular seal/test medium. It supports testing a thermal model, not assigning supply-gas temperature to every seal location. |
| Bakalchuk, Dwyer, Hosanna, Chinnaswamy, Eisenmann and McCraw (2018), [Examination of Methods of Dry Gas Seal Supply, Regulation, and Monitoring](https://oaktrust.library.tamu.edu/items/60783fa5-a001-418a-bde2-78b910d9e215), Texas A&M Turbomachinery Laboratory tutorial. | Worked supply/regulation examples and failure cases connect process-side labyrinth flow, seal delivery, vent instrumentation and competing fault hypotheses. | Practitioner primary tutorial with calculated examples and field cases; not a peer-reviewed seal-film model or a substitute for the actual OEM arrangement. |
| Cich, Twist, Sihra and Taher (2020), [Introduction to Dry Gas Seals and Systems](https://oaktrust.library.tamu.edu/items/7249516e-9e44-46cd-8c53-b105b3939a2c), Texas A&M Turbomachinery Laboratory short course. | Explains supply-panel components, transient operation, contamination-induced heating, test methods and failure mechanisms from end-user, OEM and research perspectives. | Practitioner primary course and test/failure discussion; not a universal geometry-specific numerical correlation. Use the actual supplied seal's instructions. |
| Jiang, Xu, Yu, Wang, Ding and Jiang (2024), [Theoretical Expression and Screening of Real Gas Effect of Spiral Groove Dry Gas Seal](https://doi.org/10.3390/pr12071298), *Processes* 12(7), 1298. | Compares real-gas density/compressibility representations for seven gases, motivating explicit property-model selection in a seal-film calculation. | Property/EOS screening is not independent validation of a complete natural-gas seal system or of its transient failure behavior. |

The research papers motivate engineering extensions; they are not evidence that
NeqSim already solves those equations. Before importing numerical data, record the
publication version, geometry, gas composition, units, uncertainty, access conditions
and redistribution license. An abstract alone is insufficient to implement or validate
the full model. Keep calibration and independent validation cases separate.

## Calculation and troubleshooting workflow

1. Freeze the arrangement and operating mode: seal ends, tandem/double configuration,
   pressure reference points, buffer/separation branches, sources, booster, vents,
   filters/coalescers, heaters, valves, drains and dead legs. Name all material streams.
2. Record composition including water and relevant contaminants, EOS/mixing rule,
   absolute/gauge pressure basis, gas/metal/ambient temperatures, actual versus normal
   flows, geometry, instrument range, quality, sample times and OEM limits. Assess
   heavy-end characterization and water-model uncertainty before interpreting dewpoints.
3. Calculate source-to-cavity and cavity-to-vent pressure/temperature paths. Check
   hydrocarbon and aqueous liquid at every accepted state; include cold ambient,
   regulator JT cooling, heater warm-up, standby and composition changes. Treat failed
   flashes and unresolved dewpoints as missing evidence. Evaluate hydrate/solid risks
   separately with a verified applicable model.
4. Reconcile separate process labyrinth, face leakage, buffer and separation demands.
   Apply measured gas composition and instrument reference conditions to vent-flow
   conversion. A high primary-vent reading alone does not uniquely identify seal damage.
5. Compare competing explanations: pressure starvation/process ingress, wet supply,
   aerosol/particle contamination, inadequate heating, blocked drains or vents, buffer
   loss, bearing-oil ingress, regulator oscillation and actual primary-seal degradation.
   Report both supporting and contradictory measurements and unavailable evidence.
6. Replay accepted time-series samples with the advisory monitor. Compare observed
   responses with declared scenarios; do not apply live commands or claim protection
   qualification from a simulation result. Preserve failures and unresolved states.

A future support-system model should conserve component inventories at every junction
and vessel and close energy across coolers, heaters, regulators and boosters. The
normal-volume basis must remain explicit through mass/molar flow conversion. Filter
capture efficiency, sump accumulation and pressure loss need their own balances and
data; equilibrium condensation does not predict aerosol capture efficiency.

## Dependency-ordered implementation and acceptance plan

| Priority and owner | Deliverable | Measurable acceptance evidence |
| --- | --- | --- |
| 1: #4144 | Harden supply-conditioning evidence, units and fluid ownership; remove unsupported numerical standards claims. | Failing-then-passing tests for single-liquid states, failed/missing/nonfinite evidence, invalid units/inputs, caller mutation and repeat calculation. Independent HC/water dewpoint references with explicit uncertainty and applicability. |
| 2: #4144 with #4145 | Composable support-system study with named streams, source/booster selection, pressure losses, liquid removal, filtration and heating. | Component/mass/energy closure; isolated startup, cold-standby, heater-loss and low-source-pressure cases; continuous-path refinement study; supply demand distinguished from face leakage. |
| 3: #4145 | Mode-specific monitor and historian replay, alarm/trip separation, hysteresis and regulator/source-switching dynamics. | Timestamped fault-injection cases for startup, running, trip/coastdown and standstill; stale/inconsistent instrumentation; simultaneous causes, recovery and exact replay; regulator reference case with declared parameters. |
| 4: #4149 | Optional seal-face real-fluid thermohydrodynamics and transient clearance/leakage model. | Reproduce published steady/thermal and transient experiments before testing high-pressure phase-change predictions; grid/time convergence, force/energy closure and material/property validity. Keep the existing process path unchanged when unused. |
| 5: #4149 | Integrated troubleshooting qualification and discoverable workflows. | Held-out public/OEM cases with known interventions; test whether competing faults are distinguishable; uncertainty/sensitivity and false-advisory reporting. Golden agent/skill routing cases plus verified deployed operation discovery. |

Measure warmed representative study timings and baseline compressor outputs before
adding runtime integration. Expensive phase-path or seal-film analyses should be
explicitly requested studies or optional components. Do not introduce extra flashes
into ordinary compressor calculations merely to make a diagnostic discoverable.

## Agentic discovery and reporting contract

Use `@rotating-equipment` with `neqsim-rotating-equipment-design` for dry gas seals,
seal-gas conditioning and regulator/vent analysis. Hand composition/dewpoint validity
to `@thermo-fluid`, time-series evidence to `@plant-data`, competing failure hypotheses
to `@root-cause`, and controller design to `@control-system` as needed.

Source capability search can locate the audited classes through the compressor package
route. On the audited base they have no declared curated MCP operation registration.
Discover the deployed runtime and inspect its actual API before execution; a source
route or test reference is navigation, not an invocable operation or qualified model.

Report the operating mode, flow/reference bases, source revision/runtime version,
OEM limits with provenance, complete versus unresolved calculations, model assumptions,
balance residuals, calibration/validation split and competing hypotheses. Distinguish
static source findings, executed software tests, independent physical benchmarks and
project approval. Keep partially completed #4144/#4145 acceptance items open.
