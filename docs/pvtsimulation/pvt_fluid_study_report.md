---
title: "PVT and fluid-characterization study report"
description: "Execute a traceable sample-to-model PVT study with characterization, experiments, calibration and hold-out evidence, complete parameter capture, fresh-process E300 replay and professional reporting."
keywords: "PVT, fluid characterization, sample QC, recombination, EOS calibration, holdout, E300, full parameters, professional report, pvt-study"
---

`neqsim pvt-study` connects sample and laboratory evidence to a reproducible
fluid model, an export audit and a controlled report. It uses existing NeqSim
characterization, PVT experiments, regression and fluid writers; reporting reads
the saved canonical dataset and does not rerun the thermodynamic calculations.

Use [@pvt-fluid-study-report](../../.github/agents/pvt-fluid-study-report.agent.md)
for a natural-language request such as: “Document these samples from laboratory
QC through a final characterized model, every parameter and a verified E300
file.” The agent uses the
[neqsim-pvt-fluid-study-report skill](../../.github/skills/neqsim-pvt-fluid-study-report/SKILL.md)
and delegates experiment or regression detail to `@pvt-simulation`.

## Run the workflow

The toolkit is installed through the existing `devtools` project. Use the
selected interpreter and the same NeqSim source/JAR for every study stage.

Write a draft definition first. This creates inputs only; it does not fabricate
sample records, observations, calculations or an accepted model.

<!-- run -->
```powershell
neqsim pvt-study --init study_inputs
```

Complete the study definition from the actual evidence, then execute the entire
chain. `--strict` reports failed validation as a nonzero exit status; inspect
the diagnostic findings rather than treating an existing report as acceptance.

<!-- parse -->
```powershell
neqsim pvt-study study.yaml --out pvt_study_output --strict
```

The shipped examples exercise the software with visibly synthetic oil and
gas-condensate definitions. They have no independent laboratory observations;
their result is numerical workflow/replay evidence, not experimental accuracy.

<!-- parse -->
```powershell
neqsim pvt-study --demo oil --out pvt_oil_demo
neqsim pvt-study --demo condensate --out pvt_condensate_demo
```

Each stage can be stopped for inspection. The named stage runs all its
dependencies; a partial checkpoint is not a completed fluid handover.

<!-- parse -->
```powershell
neqsim pvt-study study.yaml --out pvt_study_output --stage import
neqsim pvt-study study.yaml --out pvt_study_output --stage characterize
neqsim pvt-study study.yaml --out pvt_study_output --stage simulate
neqsim pvt-study study.yaml --out pvt_study_output --stage calibrate
neqsim pvt-study study.yaml --out pvt_study_output --stage validate
neqsim pvt-study study.yaml --out pvt_study_output --stage export
neqsim pvt-study study.yaml --out pvt_study_output --stage report --strict
neqsim pvt-study study.yaml --out pvt_study_output --resume --strict
```

Resume is guarded by the complete input and code identity. A changed sample,
composition, experiment, fitting choice, acceptance criterion or implementation
invalidates the cached result. Never copy tuning from an old component slate
and label it the new final model.

## Input and evidence contract

Use the generated definition and `devtools/pvt_examples/oil.yaml` or
`devtools/pvt_examples/condensate.yaml` as executable schema examples. Preserve
the source laboratory documents outside the public code repository.

| Record | Required meaning |
| --- | --- |
| Study | Unique identity, title, intended use, synthetic status, explicit units, provenance, assumptions, data gaps and acceptance criteria. |
| Sample | Unique sample ID, sample type, source/condition information and acceptance decision. A generated ID is not a chain-of-custody record. |
| Analysis | Source-linked composition and declared mole/mass basis; keep original and prepared records distinct and record normalization. |
| Recombination | Contributing gas/liquid sample identities and sourced molar ratio, with component/mass closure. Adjusted ratios remain calibration decisions. |
| Characterization | Named components, TBP/plus inputs, molar mass/density basis, splitting/lumping settings and the relation to source cuts. |
| Experiment | Unique ID, sample/fluid identity, protocol, K temperature, bara pressure schedule, observables, data role, units and criteria. |
| Observation | Calibration or hold-out role, experiment/property identity, value, unit, uncertainty and source. Predictions are never measurements. |
| Calibration | Supported objectives, explicit parameter bounds and the observation subset to fit. Missing calibration data do not produce a fictitious fit. |
| Standards | Designation, adopted edition, actual method/scope, source, verification date, text availability and assessment status. |

Input validation rejects ambiguous units, nonfinite values, broken IDs,
unsupported experiment/fitting routes and missing sample/analysis basis.
The study retains its raw input and transformation findings. An accepted fluid
still needs a stated validity envelope; successful parsing does not establish
reservoir-wide representativeness.

## Execution and scientific boundaries

The Java adapter is
`neqsim.pvtsimulation.util.PVTFluidStudy`. It exposes reusable parameter capture,
PVT simulation, explicit consistency checks, native snapshots and E300 auditing
to Java callers and to the Python workflow through JPype. Its exact supported
model guards are part of the export contract.

| Capability | Contract and limit |
| --- | --- |
| Hydrocarbon model | Plain SRK/PR-family models with classic mixing; unsupported active model/parameter features are rejected rather than silently converted. |
| TBP/plus characterization | Existing characterization APIs with recorded input choices and mass/molar-mass audit; inspect the current heavy-fraction guards and applicable property correlations. |
| CCE/CME | Closed-cell, constant-composition expansion through the existing `ConstantMassExpansion` calculation. |
| CVD | Existing fixed-cell depletion protocol with equilibrium gas removal; a constant-composition TP scan is not equivalent. |
| DLE | Existing staged gas-removal calculation; pressure order and retained fluid matter. |
| Separator / viscosity | Existing experiment implementations; phase identity and absent-phase availability remain explicit. |
| Calibration | CCE `relative_volume` through `PVTRegression`, with bounded constant symmetric BIP parameters only. Other fitting objectives are rejected. Hold-out measurements do not enter the fit. |
| Saturation / swelling / MMP | Simulation APIs have separate qualification boundaries. Reserved saturation/swelling regression identifiers are unsupported targets; no MMP claim follows from swelling. |
| Validation | Separate calibration and hold-out evidence, six explicit `ThermodynamicModelTest` outcomes and export replay. These do not independently validate a field fluid. |

Each independent experiment starts from a clone. Sequential material removal
within an actual depletion protocol keeps its state. Pressure is absolute bara;
temperature is kelvin unless a particular source API explicitly documents a
different argument convention. The adapter translates that boundary rather
than changing the study basis.

Viscosity predictions distinguish a phase that is absent from a computed
property value. The canonical dataset retains
`predictions.phaseAvailability.gas`, `.oil` and `.aqueous` as Boolean arrays
aligned with `pressuresBara` and the corresponding `values` arrays. The masks
come from the actual flashed state at each point. An absent phase has a JSON
`null` viscosity; an available finite value is retained, including zero. No
generic zero-value filter determines phase availability.

The report labels absent-phase viscosity as unavailable and does not draw a
physical zero curve. If a phase appears or disappears along the pressure grid,
the figure retains a gap at unavailable points. An observation requiring an
absent phase still fails explicitly; existing finite-value and positive-property
comparison gates remain active. The underlying `ViscositySim` numerical getters
retain their legacy zero-for-absence convention for existing callers; its
phase-availability getters and the study adapter provide the separate presence
information.

CVD/DLE simulation support is separate from calibration support. The existing
one-pressure fitting objective resets the fluid at each point; it cannot stand
in for a full depletion protocol with material removal. This workflow therefore
rejects those calibration targets until a protocol-faithful objective is added
and qualified. Advanced transport, association/activity models, broad
uncertainty propagation and independently qualified field-fluid examples remain
separate work; the first workflow does not claim the whole original research
program is accepted.

The condensate demonstration uses a restricted synthetic BIP interval
`0.024` to `0.026` and initial guess `0.0248` against the independently frozen
reference value `0.025`. Broader intervals and some starting guesses reject
nonconservative near-critical flash endpoints. The conservation checks remain
active; the example does not qualify the optimizer across arbitrary fluids,
bounds or initial guesses. Its stopping reason is unavailable from the current
regression result, so acceptance uses the declared post-fit residual criteria
and does not assert global convergence.

No requested fit means the final parameter values are unchanged and calibration
is reported as not requested. With no independent measurements, the report must
say numerically verified or calibrated as appropriate; it must not say
experimentally validated. Retain convergence failures, absent phases, outliers,
bound hits and model/export limitations in the report.

Read the [PVT workflow](pvt_workflow.md),
[laboratory-test guide](pvt_lab_tests.md) and
[method-qualification matrix](pedersen_method_qualification.md) for the
experiment and model evidence behind this workflow.

## Frozen parameters and export fidelity

The handover includes distinct model identities for the baseline,
characterized and final representations, a full-precision readable parameter
capture, the complete ordered BIP matrix and an exact native snapshot for
compatible-version replay. Document all effective captured coefficients,
including unchanged/default values, rather than listing only fitted values.
The native object preserves a different contract from a portable JSON fluid
definition; retain both and record the NeqSim revision.

`JsonFluidReadWrite` provides the supported reloadable NeqSim JSON definition.
The diagnostic `fluid.toJson()` method is not a substitute. The export guard
must reject a model that the chosen portable format cannot represent faithfully.
CPA, advanced activity/mixing rules, temperature-dependent coefficients and
custom transport parameters cannot be silently replaced by a plain cubic EOS.

The populated E300 fluid include is written through
`EclipseFluidReadWrite`, then read in a fresh process with explicit reader
settings. PR OmegaB overrides require the preserving reader route. Source
parameters must not be reapplied after reading the E300 file to make the audit
pass. Parameter/property differences from serialization are reported separately
from experimental residuals and checked against the declared fidelity criteria.

`PVTFluidStudy.configureViscosity` establishes the declared LBC or PFCT model
in the current and allocated gas/oil transport slots before experiments or
regression, including after characterization rebuilds the component slate.
Explicit E300 and JSON transport imports preserve the viscosity model,
coefficients and effective LBC critical volumes through phase-type changes.
The workflow does not repair a tuned or reloaded model by silently reapplying
missing source parameters after the fit or import.

Inspect component order, composition, critical properties, molar-mass and
critical-volume units, EOS/alpha conventions, volume shifts, complete BIPs and
the coverage of every active coefficient. A writer/reader round trip alone
cannot independently establish a target simulator's keyword interpretation.
The report therefore leaves target-simulator ingestion unverified unless that
target was actually executed. An E300 include is not a complete reservoir deck.

## Output package

| Path under the output directory | Purpose |
| --- | --- |
| `inputs/<input_sha256>.raw.json` | Preserved raw input and identity evidence. |
| `dataset/pvt_study.json` | Canonical sample/analysis/model/experiment/report dataset. |
| `dataset/summary.json` | Stage and handover summary. |
| `models/base.ser`, `characterized.ser`, `tuned.ser`, `final.ser` | Exact native model snapshots for compatible-version replay. An unrequested fit leaves values unchanged. |
| `models/*.parameters.json` | Full-precision effective cubic-model/transport parameter inventory and complete BIPs. Native serialization retains the broader object state. |
| `models/final_fluid.json` | Supported portable NeqSim fluid definition. |
| `models/final_fluid.e300` | Populated E300 fluid include. |
| `validation/e300_fresh_replay.json` | Fresh-process parameter/property export audit. |
| `validation/validation.json` | Checks, failures and validity findings. |

The report manifest records the actual DOCX/HTML/PDF paths, renderer status,
figure register and dataset hash. Its default files use the safe study ID under
the report output directory; figures retain editable SVG and preview paths. A
partial-stage run writes only the available checkpoint outputs.

## Professional report and review

The report covers scope and intended use; sample history and QC; original and
prepared analyses; recombination; PVT program; EOS/characterization; untuned
predictions; calibration; hold-out validation and uncertainty; consistency;
the final model and all captured parameters; export replay; conclusions and
open findings; and complete appendix tables with rerun instructions.

DOCX/HTML generation and deterministic PDF support are separate rendering paths;
inspect the returned paths and findings for the formats actually produced.
The existing toolkit `pdf` extra supplies ReportLab for this PDF renderer;
install that extra before requesting a complete DOCX/PDF handover.
Editable SVG figures describe sample/recombination lineage, the CCE closed
cell, CVD gas removal, staged DLE and the separator/stock-tank protocol.
They are conceptual apparatus sketches, not certified laboratory drawings.
Plots distinguish model predictions from measurements and calibration from
hold-out records, with phase, units and normalization labelled.

Render and inspect every report page, parameter matrix and figure. Missing or
failed observations remain visible; do not smooth curves across nonconverged
states or phase transitions. A visually polished report does not override a
failed physical, provenance or export check.

## Standards applicability

Maintain a verified project register for the actual sampling and analysis
methods. ISO/IEC 17025, ISO 6974, ISO 10715, ISO 3170, ASTM D1945/D4052/D445 and
JCGM guidance are candidate reference families, not blanket qualifications for
live-fluid PVT. Verify the adopted editions and controlled text through the
standards-lookup workflow. Public metadata can establish scope; clause-level
compliance requires the actual text and evidence. No single standard approves
the entire reservoir-fluid characterization or EOS fit.

## Related workflows

- [E300 import and export](eclipse_e300_fluid_import.md)
- [Reloadable JSON fluid format](json_fluid_format.md)
- [Fluid-characterization mathematics](fluid_characterization_mathematics.md)
- [Process-study report architecture](../development/PROCESS_STUDY_REPORT.md)
- [Agent capability discovery](../development/AGENT_CAPABILITY_DISCOVERY.md)
