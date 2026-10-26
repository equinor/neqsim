---
name: neqsim-pvt-fluid-study-report
version: "1.0.0"
description: "Executes a professional PVT and fluid characterization study report from samples and laboratory analyses to final EOS model, ALL effective parameters and verified E300 file. USE WHEN: a complete PVT report, sample QC, recombination, heavy-end characterization, calibration/hold-out validation, full parameter/BIP documentation, E300 handover or neqsim pvt-study workflow is requested."
last_verified: "2026-10-25"
requires:
  python_packages: [python-docx, matplotlib, pyyaml, jpype1]
---

# PVT and fluid-characterization study report

Use the executable `pvt_study` workflow to connect laboratory evidence to a
reproducible fluid and a professional report. Reuse the existing NeqSim PVT
experiments, characterization, regression and E300 writer through
`neqsim.pvtsimulation.util.PVTFluidStudy`. Use `pvt_report` to render the saved
dataset. The orchestrator is
[@pvt-fluid-study-report](../../agents/pvt-fluid-study-report.agent.md).

## Entry points

- `neqsim pvt-study --init study_inputs` writes a draft study definition and
  stops. It does not create observations, results or an accepted model.
- `neqsim pvt-study study.yaml --out <task-directory> --strict` runs the complete
  workflow and reports validation findings.
- `neqsim pvt-study --demo oil --out <task-directory>` or `--demo condensate`
  executes an explicitly synthetic example.
- `--stage import|characterize|simulate|calibrate|validate|export|report` stops
  after the named stage. A stage checkpoint is not a completed final handover.
- `--resume` is guarded by complete input/code identity. Changed inputs or code
  invalidate the old result; never reuse a tuned fluid for a different slate.
- Python API: `pvt_study.load_study(path)` and
  `pvt_study.run_pipeline(study, out_dir, strict=False, resume=False,
  stop_after="report")`. Use the selected interpreter and repository tooling.

The input schema, shipped examples, supported limits and output paths are in the
[study guide](../../../docs/pvtsimulation/pvt_fluid_study_report.md).

## One normalized evidence chain

Retain sample, analysis, observation, experiment, model, parameter and export IDs
in one dataset. Every reported number and plotted point comes from this dataset
or an explicitly cited input. Preserve raw evidence immutably and keep derived
corrections and transformations reproducible.

| Stage | Required evidence and checks |
| --- | --- |
| Import / sample QC | Sample identity/type and condition, composition basis, source-linked analysis, sample acceptance and missing-data findings; raw and normalized compositions stay distinct. |
| Recombine | Actual contributing gas/liquid sample IDs and molar ratio with its source; component and mass closure; measured ratios stay distinct from calibrated ratios. |
| Characterize | TBP/plus inputs, molar mass and density/reference basis, splitting/lumping choices and source mapping; audit conserved mass and heavy-end moments. |
| Simulate | Independent starting-fluid clone per experiment; preserve sequential material removal within CVD/DLE/separator protocols; K and bara are explicit. |
| Calibrate | Supported objectives, declared calibration observations, parameter bounds and residual/weight conventions; untouched baseline and fixed/fitted parameter record. |
| Validate | Hold-out observations never fitted; inspect residuals by property/role and all explicit thermodynamic checks; no measurements means numerical verification only. |
| Export | Frozen final identity, full-precision parameter capture, complete ordered BIP matrix, exact native snapshot, supported JSON definition, populated E300 include and fresh-process replay. |
| Report | Controlled DOCX/PDF/HTML, deterministic experiment SVGs, data-derived plots/tables, open findings, compatibility statement and exact rerun instructions. |

Do not use plausible EOS output as a replacement for missing measured data.
Recombination cannot establish that a nonrepresentative sample was repaired.
Keep absent phases unavailable rather than reading phase zero as oil.

## Supported physics and deliberate limits

The first workflow supports plain hydrocarbon SRK/PR-family models with classic
mixing. Read `PVTFluidStudy` guards for the exact exportable model contract; a
class name or enum alone is not qualification. Unsupported active attraction,
temperature-dependent BIP/volume-shift or transport parameters must be rejected
or explicitly retained in a separate non-equivalent model. Do not silently
substitute an exportable EOS for the model that was fitted.

The experiment adapter uses existing CCE/CME, CVD, DLE, separator and viscosity
calculations. Call the concrete experiment entry point (`runCalc()` where
required); inherited `run()` can only reset a helper. A constant-composition
pressure sweep cannot stand in for staged gas removal. Preserve the original
laboratory pressure sequence and reference-volume basis.

For viscosity, retain the adapter's `predictions.phaseAvailability` Boolean
arrays (`gas`, `oil`, `aqueous`) alongside nullable Pa s values. Each mask
describes the actual flashed state at the corresponding pressure, including
phase appearance and disappearance. `ViscositySim` retains legacy numerical
zeros for absent phases, so use its phase-availability getters rather than
inferring presence from a viscosity value. Show absent values as unavailable
and preserve gaps in report curves. Do not replace every finite zero with
missing data, or omit an absent-phase observation to make a comparison pass;
existing numerical and physical acceptance gates remain mandatory.

The initial calibrated route uses `PVTRegression` for CCE `relative_volume`
with bounded constant symmetric BIP parameters only. CVD/DLE/separator/viscosity
simulation support does not imply that this study workflow supports fitting
those protocols. Unsupported fitting targets are rejected. In particular, a
one-pressure objective that resets depletion cannot match the full laboratory
material-removal sequence. Extend and qualify that objective before enabling it.

Other `PVTRegression` routes retain their own source/API contracts. `SATURATION_PRESSURE` and
`SWELLING` are reserved unsupported fitting targets even when their simulations
exist. Swelling does not establish MMP; investigate the current method
qualification and delegate independent extensions instead of claiming coverage.

Read [PVT workflow](../../../docs/pvtsimulation/pvt_workflow.md),
[method qualification](../../../docs/pvtsimulation/pedersen_method_qualification.md)
and the `neqsim-eos-regression` skill before interpreting a calibration.
Retain all failed points and convergence findings. Do not loosen a global
tolerance to conceal a singular fit or phase-boundary issue.

## Thermodynamic and export checks

Record individual Boolean outcomes from `ThermodynamicModelTest`:
`checkFugacityCoefficients`, `checkFugacityCoefficientsDn`,
`checkFugacityCoefficientsDn2`, `checkFugacityCoefficientsDP`,
`checkFugacityCoefficientsDT` and `checkNumerically`. Use the documented
initialization and cloned state; `runTest()` logging alone is insufficient.
Keep phase-internal derivatives separate from a flash across phase appearance.

The native snapshot preserves the exact object for compatible-version replay;
the readable parameter capture documents effective component/model coefficients
and complete BIPs. A supported `JsonFluidReadWrite` definition has a narrower
contract than arbitrary object serialization. Neither it nor a diagnostic
`fluid.toJson()` proves that every advanced model setting is portable.

Use `EclipseFluidReadWrite` for the actual E300 include and the explicit
OmegaB-preserving reader when required. Audit component order, fractions,
critical properties, units, alpha/EOS convention, volume shifts, BIPs and active
coefficient coverage. Fresh-process replay must recompute relevant properties
without restoring missing values from the native source. Show export-induced
differences separately from laboratory residuals. Shared writer/reader behavior
does not independently establish target-simulator compatibility.

## Reporting and visual acceptance

Generate the sample/analysis/QC register, transformations, model comparisons,
experiment results, calibration and hold-out evidence, consistency outcomes,
complete parameter/BIP appendix, export audit and open findings. Keep the raw
and prepared records, untuned/final identities and all effective parameters in
the handover, not only fitted values.

Use editable deterministic SVG sketches of the applicable lineage/recombination,
CCE closed cell, CVD equilibrium gas removal, staged DLE and separator/stock-tank
protocols. Mark conceptual apparatus explicitly. Never invent instrument
dimensions, certificates or chromatograms. Plot actual experiment schedules,
named phases, units and normalization; distinguish measured markers from model
curves and calibration from validation data.

Render and inspect every report page, full matrix and figure. Fix clipping,
overlap, small labels, page breaks and missing captions. Report generation must
read cached canonical results and must not trigger additional flashes.

## Standards and acceptance

Create a project applicability register for actual sampling/analysis/property
methods and uncertainty practice. Candidate families include ISO/IEC 17025,
ISO 6974, ISO 10715, ISO 3170, ASTM D1945/D4052/D445 and JCGM guidance. These
names are starting points, not claims of current editions or compliance.
Verify publisher metadata and the adopted controlled text, retain the source,
verification date and exact applicable requirement, and use
`neqsim-standards-lookup` for clause-level assessment. Gas-only sampling or
atmospheric property methods do not automatically apply to live reservoir PVT.
No single standard certifies the complete fluid characterization or EOS fit.

Acceptance criteria must follow the study decision and evidence uncertainty.
Synthetic examples demonstrate execution, identities and numerical replay;
they are never independent physical validation. A finite optimizer objective,
an exported file or a pretty report does not establish final acceptance.

## Public, community and enterprise integration

Use current agent/skill discovery before creating overlapping specialists.
Core code owns the public APIs and workflow; community packages may coordinate
broader studies; enterprise overlays own private data, licensed access and
company requirements. Do not copy those overlays into the public repository.
Keep proprietary task output outside the source checkout.

Related: `neqsim-eos-regression`, `neqsim-phase-envelope`,
`neqsim-thermodynamic-initialization`, `neqsim-professional-reporting`,
`neqsim-standards-lookup` and `process-study-report`.
