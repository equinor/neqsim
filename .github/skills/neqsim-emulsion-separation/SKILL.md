---
name: neqsim-emulsion-separation
description: "Run optional NeqSim oil-water emulsion separation studies and separator troubleshooting: rag-layer or densely packed zone (DPZ) growth, coalescence-limited capacity, water-in-oil and oil-in-water symptoms, droplet-size and temperature sensitivity, changing water cut, and comparison with measured separator trends. Start with explicitly estimated inputs when droplet or coalescence measurements are absent; preserve existing process calculations and mark uncalibrated screening results."
last_verified: "2026-10-27"
---

# Oil-water emulsion separation studies

Use `@emulsion-separation` and the model in
`neqsim.process.equipment.separator.emulsion`. Read the
[study guide](../../../docs/process/equipment/emulsion-separation-study.md)
for exact equations, units, APIs, numerical checks and applicability before
running a calculation. Read the current Javadocs for overloads and setup order.

## Build from the existing separator

1. Start from a solved `ThreePhaseSeparator` with oil and aqueous phases.
   Record EOS, mixing rule, fluid characterization, absolute pressure and
   temperature. Reuse named process objects rather than reconstructing an
   unrelated fluid from guessed component fractions.
2. Create `EmulsionSeparationStudy` with the separator and an explicit effective
   separation area in m2. The study copies state; never initialize properties
   or alter phase inventories on the live separator to run the study.
3. Pass dispersed-water flow in m3/s to `run(...)`. If the input is a flux in
   m/s, multiply it by the declared effective area first. Do not equate total
   produced-water flow to emulsified-water flow without declaring that assumption.
   The four-argument `run(...)` takes initial dispersed-water volume divided by
   area in m; it does not take total emulsion-layer thickness. Derive that
   conserved state from measured water inventory or an explicit inversion of
   the model's height relation. Never pass measured layer thickness directly.
4. State whether the effective area is measured, drawing-derived or estimated.
   For a horizontal vessel, use horizontal interface width times effective
   settling length when that approximation is appropriate. The vessel's
   circular cross section is a different area. Retain the constant-area
   assumption and the physical bounds of the available liquid space.

`DenseEmulsionLayerModel` provides the dense-layer closure. This first study
tracks a separate diagnostic control volume; it does not modify the live
separator's stored mass, outlet streams, level controllers or calculated
entrainment. Ordinary process runs do not execute the study. Do not describe
study results as coupled outlet-quality predictions.

## Handle missing measurements without blocking screening

Start an explicitly estimated case when droplet and coalescence measurements
are absent. Use a droplet radius of **1.35e-3 m** and a coalescence time of
**60 s** as the documented starting assumptions, retaining
`ESTIMATED_UNCALIBRATED`. The radius comes from the paper's model-fluid
experiment; neither value is a universal crude-oil property.

Use `guessKinetics(radiusM, contactTimeSeconds)` for explicit screening guesses
or `calibratedKinetics(radiusM, coalescenceRatePerM2Second)` for supplied omega.
Both return new immutable studies; retain the returned object. Give a supplied
calibrated coefficient precedence over the estimated time. The explicit-property
constructor accepts measured interfacial tension together with oil and water
densities. The separator constructor instead uses its copied fluid's property
models. Record the chosen basis and limitations. State radius versus diameter
explicitly and let the current model API perform the coefficient conversion.

Vary radius and coalescence time by a factor of three around the starting
assumptions and show which scenarios change the predicted operating regime.
Call the result a **sensitivity envelope**, not a statistical confidence
interval or P10/P90 range. Do not tune a coefficient merely to obtain the
desired separator capacity.

## Troubleshoot with distinct hypotheses

Compare a verified baseline with independently initialized scenarios for
incoming dispersed-water flux, droplet size and coalescence kinetics. Preserve
the distinction between measured operating changes and assumed mechanisms.
Report layer growth or drainage, critical flux, steady-state existence,
available-height limits and the model control-volume residual.

Use time-aligned rate, pressure, temperature, level and outlet-quality data
when available. Identify whether the observations distinguish greater load,
finer droplets and slower coalescence. A single water-in-oil measurement
usually cannot identify a unique mechanism. Hand other equipment hypotheses
to [root-cause analysis](../neqsim-root-cause-analysis/SKILL.md), and chemical
selection, dose-response evidence and compatibility to
[production chemistry](../neqsim-production-chemistry/SKILL.md).

For oil-in-water symptoms, use the existing separator performance model and
check the phase roles. The present dense-layer closure is for water-in-oil;
do not promise a validated inverse-emulsion model. Likewise, changes in
temperature or brine chemistry require justified property/kinetic inputs,
not a generic rule that they improve separation.

## Evidence and limitations

The closure is based on Zlobin et al., *AIChE Journal* (2026),
[DOI 10.1002/aic.70429](https://doi.org/10.1002/aic.70429). Their experiments use
millimeter-sized, nearly monodisperse water-in-oil model emulsions. Raw data
are identified at [DOI 10.5281/zenodo.17463071](https://doi.org/10.5281/zenodo.17463071).
Document which equations and data were actually reproduced. Field crude
validation, polydisperse population dynamics and product-specific chemistry
remain separate qualification tasks.

Do not claim this implementation resolves interfacial aging, adsorption,
asphaltene film rheology, droplet breakup or electric-field effects. These
can motivate later closures but are not implied by the coalescence parameter.
Do not infer a demulsifier dose without experimental dose-response evidence.

Keep returned warnings, rejected cases and missing inputs visible. Record
parameter provenance and the tested source version. Verify conservation
within the study, timestep sensitivity and source-separator immutability.
Report engineering validation separately from regression-test execution.
Package routing is navigation only; inspect the deployed MCP registry before
claiming any operation can be invoked remotely.

## Related workflows

- [Process modeling](../neqsim-process-modeling/SKILL.md): solved, reusable
  flowsheets and named equipment.
- [Production chemistry](../neqsim-production-chemistry/SKILL.md): chemical
  compatibility and measured dose-response models.
- [Root-cause analysis](../neqsim-root-cause-analysis/SKILL.md): competing
  equipment hypotheses and historian evidence.
- [Emulsion viscosity](../../../docs/pvtsimulation/flowassurance/emulsion_viscosity_calculator.md):
  existing viscosity and phase-inversion correlations, distinct from a dynamic
  emulsion-layer inventory.
