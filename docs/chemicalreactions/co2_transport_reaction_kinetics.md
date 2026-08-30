---
title: CO2 transport reaction kinetics
description: Safe qualification and Damköhler-timescale screening for finite-rate impurity chemistry in CO2 transport and injection calculations.
---

NeqSim provides general `KineticReaction` models, an experimental
`CO2ImpurityKineticReactor`, and transport-timescale diagnostics. These APIs support screening;
they do not supply a qualified kinetic dataset or apply reaction source terms to a pipeline.

Finite-rate results depend on temperature, absolute pressure, phase state, water availability,
concentration and rate bases, and sometimes wall or catalyst material. A numerical rate is therefore
not evidence that its parameters are suitable for engineering use.

## What the two contracts do

`KineticReactionQualification` stores a source citation, stable source identifier, validation
status, inclusive temperature and pressure ranges, and limitations. Its `requireValidatedAt`
method rejects an unvalidated parameterization or a state outside those declared ranges.

`KineticReactionDiagnostics.evaluate` screens one initialized active phase and a caller-supplied
residence time. For reactants with negative stoichiometric coefficients, it estimates the shortest
consumption time from the absolute reaction rate and local concentration. It then reports

$$
\mathrm{Da} = \frac{t_{\mathrm{transport}}}{t_{\mathrm{reaction}}}.
$$

The diagnostics object does not call the qualification object. The caller must enforce
qualification first, as the complete Java 8 helper below does.

## Safe Java helper

Provide a separately constructed reaction and qualification whose names, source, rate basis, phase,
composition range, and material limitations have been reviewed together. The thermodynamic system
must already be flashed and initialized.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.reactor.KineticReaction;
import neqsim.process.equipment.reactor.KineticReactionDiagnostics;
import neqsim.process.equipment.reactor.KineticReactionQualification;
import neqsim.thermo.system.SystemInterface;

public final class QualifiedKineticsScreen {
  private static final Logger logger = LogManager.getLogger(QualifiedKineticsScreen.class);

  private QualifiedKineticsScreen() {}

  public static KineticReactionDiagnostics evaluate(
      KineticReaction reaction,
      KineticReactionQualification qualification,
      SystemInterface fluid,
      int phaseIndex,
      double residenceTimeSeconds) {
    qualification.requireValidatedAt(fluid.getTemperature(), fluid.getPressure());

    KineticReactionDiagnostics diagnostic =
        KineticReactionDiagnostics.evaluate(
            reaction, fluid, phaseIndex, residenceTimeSeconds);

    logger.info(
        "reaction={} Da={} regime={} limitingReactant={}",
        diagnostic.getReactionName(),
        diagnostic.getDamkohlerNumber(),
        diagnostic.getRegime(),
        diagnostic.getLimitingReactant());
    return diagnostic;
  }
}
```

`getTemperature()` returns K and `getPressure()` returns bara, matching the qualification
constructor. `residenceTimeSeconds` is in s and must be finite and non-negative. `phaseIndex`
is an active phase index, not a phase-type identifier; resolve and validate the intended phase before
calling the helper.

The qualification and reaction objects are not automatically bound to one another. Matching names
do not prove that the rate parameters, stoichiometry, phase, or evidence belong together. Treat that
association as project-owned provenance and verify it before calling `evaluate`.

## Diagnostic semantics

The current diagnostic accepts only `KineticReaction.RateBasis.VOLUME`. Catalyst-mass and
catalyst-area bases are rejected because a local catalyst loading or area is required to convert
them to a volumetric consumption timescale.

| Damköhler number | Regime | Screening interpretation |
|---:|---|---|
| no finite consumption time | `INACTIVE` | no finite local conversion timescale is available |
| `Da < 0.1` | `TRANSPORT_DOMINATED` | reaction is slow relative to residence time |
| `0.1 <= Da <= 10` | `COUPLED` | reaction and transport occur on comparable timescales |
| `Da > 10` | `REACTION_DOMINATED` | reaction is fast relative to residence time |

The thresholds at exactly 0.1 and 10 are `COUPLED`. The reported rate keeps its sign, while the
timescale uses its absolute value. A missing or zero-concentration reactant, zero rate, or otherwise
non-positive limiting time produces `INACTIVE`, an infinite reaction time, and `Da = 0`.
Non-finite rates, invalid phase indexes, and unsupported rate bases fail explicitly.

These labels do not establish that equilibrium chemistry is valid. They also do not update
composition, conserve elements over a timestep, reflash the fluid, or solve charge balance.

## Intended transport integration

A future control-volume integration should:

1. solve and initialize the local pressure, temperature, and phase state;
2. identify the active phase required by each reaction;
3. verify reaction-to-evidence provenance and call `requireValidatedAt` with the local K/bara
   state;
4. evaluate the local rate and reaction/transport timescale;
5. apply a bounded, element-conserving reaction extent over the timestep;
6. reflash and update electrolyte speciation and charge balance when an aqueous phase exists;
7. retain reaction extent, source identity, range status, and conservation diagnostics.

Operator splitting requires timestep-refinement evidence. A Damköhler regime alone does not qualify
a model, choose a timestep, establish phase consistency, or approve a transport design.

## CO2 impurity chemistry boundary

The current `CO2ImpurityKineticReactor` default constants are experimental and illustrative. Do
not use them as design correlations without independent evidence and qualification. The guide does
not embed facility-specific data, kinetic constants, or a validated CO2/water, SOx, H2S, NOx/O2, or
cross-impurity reaction set.

For equilibrium speciation, mineral saturation, or precipitation, use the electrolyte framework
and verify its reaction set, standard states, charge balance, and applicability separately.

## Related documentation

- [Chemical reactions package](README.md)
- [Experimental CO2 impurity kinetics](co2_impurity_kinetics_guide.md)
- [Reactive-flash workflow](../thermo/reactive_flash.md)
