---
title: CO2 transport reaction kinetics
description: Qualification and transport-timescale workflow for finite-rate impurity chemistry in CO2 pipelines and injection systems.
---

# CO2 transport reaction kinetics

NeqSim contains general `KineticReaction` models and an experimental
`CO2ImpurityKineticReactor`. Finite-rate chemistry in a CCS transport system must not be treated as
validated simply because a reaction can be evaluated numerically. Kinetic parameters are strongly
dependent on temperature, pressure, phase state, water availability, concentration basis, and in
some cases wall or catalyst material.

This workflow adds two reusable building blocks for Northern-Lights-type and other dense-phase CO2
transport studies without embedding facility-specific data or unqualified kinetic constants in the
library.

## Scientific qualification

`KineticReactionQualification` records the evidence boundary independently of the numerical
`KineticReaction` object:

```java
KineticReactionQualification qualification = new KineticReactionQualification(
    "reaction name",
    "primary literature citation",
    "DOI or stable public identifier",
    ChemicalReactionValidationStatus.VALIDATED,
    minimumTemperatureK,
    maximumTemperatureK,
    minimumPressureBara,
    maximumPressureBara,
    "model, phase, composition, and material limitations");

qualification.requireValidatedAt(localTemperatureK, localPressureBara);
```

`requireValidatedAt` fails closed when a parameterization is unvalidated or when the local state is
outside its declared temperature/pressure range. The qualification object does not claim that a
complete pipeline, electrolyte, corrosion, or precipitation model is validated.

## Reaction time versus transport time

For a volume-basis `KineticReaction`, `KineticReactionDiagnostics` estimates the local time required
to consume the limiting reactant at the current rate. It compares this reaction time with a caller
supplied residence time:

$$
Da = \frac{t_{transport}}{t_{reaction}}
$$

```java
KineticReactionDiagnostics diagnostic =
    KineticReactionDiagnostics.evaluate(reaction, fluid, phaseIndex, residenceTimeSeconds);

logger.info("reaction={} Da={} regime={} limitingReactant={}",
    diagnostic.getReactionName(),
    diagnostic.getDamkohlerNumber(),
    diagnostic.getRegime(),
    diagnostic.getLimitingReactant());
```

The regime labels are intentionally coarse screening diagnostics:

| Damkohler number | Regime | Interpretation |
|---:|---|---|
| no finite rate | `INACTIVE` | no local finite-rate conversion predicted |
| `Da < 0.1` | `TRANSPORT_DOMINATED` | chemistry is slow relative to residence time |
| `0.1 <= Da <= 10` | `COUPLED` | kinetics and transport occur on comparable timescales |
| `Da > 10` | `REACTION_DOMINATED` | chemistry is fast relative to residence time |

These thresholds do not prove that an equilibrium assumption is valid. A coupled pipeline model
must still demonstrate timestep refinement, phase consistency, element conservation, and agreement
with independent kinetic evidence.

## Intended CCS workflow

A representative future pipeline integration should evaluate each control volume in the following
order:

1. solve the local pressure, temperature, and phase state;
2. determine whether the phase required by each reaction is present;
3. require the selected kinetic parameterization to be scientifically qualified at the local state;
4. evaluate reaction rate and reaction/transport timescale diagnostics;
5. apply conservative reaction source terms over the local timestep or residence interval;
6. reflash and, when an aqueous phase exists, update electrolyte speciation and charge balance;
7. retain reaction extent, source provenance, range status, and conservation diagnostics in the
   pipeline result.

Operator splitting is acceptable as a first coupling strategy only when timestep refinement shows
that splitting error is controlled. Reactions with catalyst-mass or catalyst-area rate bases require
explicit local catalyst loading or area before a transport timescale can be computed; the generic
transport diagnostic therefore rejects those bases rather than silently converting units.

## CO2 impurity chemistry scope

The campaign in issue #3318 targets, in evidence order, CO2/water chemistry followed by qualified
SOx, H2S, NOx/O2, and cross-impurity reactions. Aqueous acid/base speciation, mineral saturation,
and precipitation should use the electrolyte framework coordinated under issue #3144. Generic
phase-stability defects remain owned by issue #2937, while full transient plant/pipeline state
management is coordinated with issue #2911 and the pipeline roadmaps.

The current `CO2ImpurityKineticReactor` includes illustrative default kinetic constants. Those
constants remain experimental and must not be used as design correlations without independent
qualification.
