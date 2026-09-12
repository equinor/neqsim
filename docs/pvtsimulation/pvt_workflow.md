---
title: "PVT Calibration and Validation Workflow"
description: Source-backed workflow for qualifying reservoir-fluid data, calibrating NeqSim PVT models, validating hold-out experiments, and reporting results with explicit units and evidence boundaries.
---

This guide defines an auditable path from laboratory observations to a NeqSim fluid model. It
does not supply a universal fluid, tuning range, or acceptance limit. Sample identity, laboratory
conditions, uncertainty, parameter bounds, and acceptance criteria are project-specific and must
remain traceable to their owners.

For a runnable starting point, use the Java 8/Log4j2
[`PvtSeparatorQuickStart`](README.md#runnable-multi-stage-separator-example) and its enabled
[documentation regression](../../src/test/java/neqsim/pvtsimulation/PvtSimulationDocumentationTest.java).
The enabled [PVT regression tests](../../src/test/java/neqsim/pvtsimulation/regression/PVTRegressionTest.java)
exercise the current regression surface. This page intentionally contains no second code example.

## Evidence boundary

The workflow is bounded by current repository sources:

| Responsibility | Maintained evidence |
| --- | --- |
| Experiment registration, weights, parameters, and optimization | [`PVTRegression`](../../src/main/java/neqsim/pvtsimulation/regression/PVTRegression.java) |
| Available tuning parameters and library defaults | [`RegressionParameter`](../../src/main/java/neqsim/pvtsimulation/regression/RegressionParameter.java) |
| Optimized fluid, objectives, parameter values, and uncertainty accessors | [`RegressionResult`](../../src/main/java/neqsim/pvtsimulation/regression/RegressionResult.java) |
| PVT report assembly and exports | [`PVTReportGenerator`](../../src/main/java/neqsim/pvtsimulation/util/PVTReportGenerator.java) |
| Laboratory-test model setup | [PVT laboratory-test guide](pvt_lab_tests.md) |
| Characterization equations and parameter meanings | [Fluid characterization mathematics](fluid_characterization_mathematics.md) |

These sources establish API behavior, not the quality of a particular sample, laboratory campaign,
equation-of-state choice, tuned parameter set, or field-development decision.

## 1. Freeze the data basis

Before constructing a model, record:

- sample identifier, sampling point, date, and chain of custody;
- laboratory report revision and the basis for corrected or recombined data;
- composition units, component slate, plus-fraction definition, and normalization rule;
- pressure and temperature reference scales;
- reported uncertainty, detection limits, and rejected observations;
- experiment sequence, depletion path, separator stages, and stock-tank reference conditions; and
- the project-specific calibration targets, hold-out observations, and acceptance criteria.

Keep raw observations immutable. Perform unit conversion, filtering, and derived-property
calculation in a traceable preparation layer. Do not silently replace missing observations with
illustrative values.

## 2. Construct and qualify the base fluid

Build the untuned fluid from the approved composition and characterization basis. Document the
thermodynamic model, mixing rule, component mapping, plus-fraction splitting, and every externally
supplied property. The [Whitson reader](whitson_pvt_reader.md), [JSON fluid
format](json_fluid_format.md), and [Eclipse E300 import guide](eclipse_e300_fluid_import.md)
describe dedicated input routes.

Qualify the base fluid before regression. At minimum, check material normalization, component
identity, critical-property plausibility, phase behavior near the laboratory path, and whether each
laboratory experiment can run independently. Preserve the untuned result as the comparison
baseline. The `PVTRegression` constructor clones the supplied system for its base and tuned
states; subsequent project records should still identify the exact input revision.

## 3. Reproduce experiments independently

Configure only experiments represented by traceable observations. Run the corresponding NeqSim
laboratory-test models before assembling a multi-experiment objective. This separates model-setup
failures from regression behavior and exposes unit or phase-selection mistakes early.

Compare predictions and observations using the laboratory's definitions. A relative volume, liquid
dropout, solution gas-oil ratio, formation volume factor, density, and viscosity are different
observables; do not combine them merely because they share a pressure point. Record both absolute
and normalized residuals and explain the weighting basis.

## 4. Register data with explicit units

The current `PVTRegression` methods accept the following quantities:

| Method | Required quantities |
| --- | --- |
| `addCCEData` | pressure in bar, relative volume as $V/V_{sat}$, optional Y-factor, temperature in K |
| `addCVDData` | pressure in bar, liquid dropout in volume %, gas compressibility factor, temperature in K |
| `addDLEData` | pressure in bar, solution GOR in Sm³/Sm³, oil FVF in m³/Sm³, oil density in kg/m³, temperature in K |
| `addSeparatorData` | GOR in Sm³/Sm³, oil FVF, API gravity, separator pressure in bar, separator temperature in K, reservoir temperature in K |
| `addViscosityData` | pressure in bara, dynamic viscosity in Pa s, temperature in K, and phase name `gas`, `oil`, `liquid`, `aqueous`, or `water` |

The array-taking methods iterate from the pressure-array length and do not perform a visible
equal-length, non-empty, finite-value, or ordering check before indexing the companion arrays.
Validate those properties before calling the API. Also retain the original observation order or an
explicit sort record; do not infer a depletion path from an unordered table.

Pressure conventions differ across the surface: most regression experiment Javadocs say bar,
viscosity uses bara, and report metadata uses bara. Treat the laboratory pressure basis as an input
contract and convert deliberately.

## 5. Select bounded regression parameters

Choose only parameters with a defensible physical relationship to the observed residuals. Record
for every parameter:

- lower bound, upper bound, initial guess, and units where applicable;
- the source or engineering rationale for each bound;
- which experiments constrain it;
- known correlations or identifiability concerns; and
- the sensitivity and hold-out checks required for acceptance.

`RegressionParameter.getDefaultBounds()` returns library defaults, and
`addRegressionParameter(parameter)` uses them. Defaults are not project approval. Prefer explicit,
project-specific bounds when the data basis requires them. Avoid adding many weakly identifiable
parameters solely to reduce the calibration objective.

## 6. Configure and run regression

Set experiment weights only after documenting the residual normalization and the relative
importance of each data type. `setExperimentWeight`, `setMaxIterations`, `setTolerance`, and
`setVerbose` configure the current framework. `runRegression()` rejects an empty parameter set
and a run with no experimental data.

Keep the base-fluid revision, data-preparation revision, parameter configuration, optimizer
settings, software revision, and execution log together. Logging in maintained Java examples must
remain Java 8 compatible and use Log4j2; console printing is not an accepted publication pattern.

## 7. Review calibration diagnostics

`RegressionResult` exposes the tuned fluid, objective values by experiment type, total objective,
parameter configurations and optimized values, uncertainty information, confidence intervals, and
the final chi-square value. Treat these as diagnostics of the configured optimization problem.

Calibration is not independent validation. A low objective value does not establish uniqueness,
predictive accuracy outside the fitted observations, representative sampling, or fitness for a
facility or reservoir decision. Examine residual structure, parameter-bound hits, parameter
correlation, experiment balance, and sensitivity to initial guesses.

## 8. Validate before acceptance

Reserve hold-out observations or a separate experiment that was not used to tune the model. Apply
the tuned fluid without changing parameters, then compare predictions using predeclared
project-specific criteria. Where no independent data exist, state that limitation and use
cross-validation or sensitivity cases as weaker evidence rather than calling the model validated.

Challenge the model across the intended pressure, temperature, composition, and depletion envelope.
Investigate discontinuities, phase-identity changes, extrapolation, and sensitivity to laboratory
uncertainty. Any engineering use must carry the validation range and unresolved limitations
forward.

## 9. Report and hand off

`PVTReportGenerator` accepts a fluid in its constructor. Its current metadata methods include
`setProjectInfo`, `setLabInfo`, and `setReservoirConditions`; the latter takes reservoir
pressure in bara and temperature in °C. Simulation results can be attached with `addCCE`,
`addDLE`, `addCVD`, `addSeparatorTest`, and the other source-listed adders.
`generateMarkdownReport()` produces the Markdown report, while dedicated CSV methods export
supported result tables.

A handoff package should contain:

- raw and prepared data with provenance and units;
- base and tuned fluid definitions with repository revision;
- parameter bounds, initial and optimized values;
- residuals, objectives, uncertainty, and sensitivity results;
- hold-out predictions and acceptance decisions;
- validity envelope, exclusions, and accountable reviewer; and
- report output plus a machine-readable execution manifest.

Use the dedicated [black-oil PVT export guide](blackoil_pvt_export.md) or
[Eclipse E300 import guide](eclipse_e300_fluid_import.md) for supported file workflows. The
regression package does not provide an `EclipseEOSExporter` API.

## Review checklist

- [ ] Sample and laboratory revisions are traceable.
- [ ] Every observation has an explicit quantity, unit, uncertainty, and pressure basis.
- [ ] Equal-length arrays are non-empty, finite, ordered, and reviewed before API calls.
- [ ] Base experiments run independently before regression.
- [ ] Parameters and bounds have project-specific justification.
- [ ] Calibration residuals and parameter correlations are reviewed.
- [ ] Hold-out validation covers the intended operating envelope.
- [ ] Reporting uses the current source-backed API and preserves limitations.
- [ ] No result is presented as an engineering qualification without accountable project review.

## Related documentation

- [PVT simulation package](README.md)
- [PVT laboratory-test guide](pvt_lab_tests.md)
- [Fluid characterization mathematics](fluid_characterization_mathematics.md)
- [Whitson PVT reader](whitson_pvt_reader.md)
- [JSON fluid format](json_fluid_format.md)
- [Eclipse E300 fluid import](eclipse_e300_fluid_import.md)
- [Black-oil PVT export](blackoil_pvt_export.md)
