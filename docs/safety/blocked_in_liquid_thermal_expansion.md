---
title: Blocked-In Liquid Thermal Expansion Screening
description: Equation-of-state isochoric pressure-rise and local beta/kappa screening for initialized single-liquid blocked-in inventories, with explicit units, numerical boundaries, and relief-design handoff.
---

# Blocked-In Liquid Thermal Expansion Screening

A liquid-full segment with fixed mass, rigid volume, no vapour space, and no open relief path can
develop a large pressure rise when heated. This guide separates two engineering questions:

1. **What absolute pressure is required to retain the initial liquid density at a new
   temperature?**
2. **What thermal-relief flow and certified device area are required for the real heat input,
   inventory, piping, and back pressure?**

`BlockedInLiquidExpansionAnalysis` in `neqsim.process.util.fire` addresses only the first
question. It provides an equation-of-state pressure screen and a local constant-property
cross-check. It does not calculate thermal-relief flow, select a valve, or approve a design.

## When to Use This vs `TrappedLiquidFireRuptureStudy`

| Tool | Scope | Use when |
| --- | --- | --- |
| `BlockedInLiquidExpansionAnalysis` | Rigid-volume, fixed-mass pressure screening without fire heat-transfer, pipe-stress, or flange checks | Screening an initialized single-liquid inventory for pressure sensitivity to temperature |
| `TrappedLiquidFireRuptureStudy` ([guide](trapped_liquid_fire_rupture.md)) | Fire-exposure transient with wall heat-up, pipe/flange checks, failure time, and PFP demand | Screening a defined fire case and its thermomechanical response |

Use the blocked-in analysis for the thermodynamic pressure question. Move to the rupture study only
when its additional fire, geometry, material, and flange inputs represent the scenario. Neither tool
replaces project relief-system design or accountable safety review.

## Calculation Modes

### Isochoric equation-of-state march

`computeIsochoricPressureProfile(fluid, temperaturesK)` records the mixture density at the
supplied fluid's current temperature and pressure. For each requested temperature it performs
`TPflash()` calculations on internal clones and uses bracket expansion plus bisection to find the
absolute pressure that reproduces that reference density.

The current implementation:

- interprets input temperatures as absolute K and returns absolute pressures in Pa;
- converts the supplied system's canonical pressure from bara to Pa;
- seeds each point with the preceding pressure result, so an increasing temperature sequence is the
  clearest heating workflow;
- accepts a relative density error below `1.0e-6`;
- searches between `1.0e3` Pa and `1.0e9` Pa; and
- does not modify the supplied `SystemInterface`.

The algorithm does not prove that every trial or result is a single liquid phase. Confirm the
initial state and the requested range independently. A phase transition, non-monotonic density
response, unsuitable equation of state, or unreachable density may prevent bracketing and raise
`IllegalStateException`.

### Local beta/kappa relation

For a small step around the initialized reference state, the constant-property differential relation
is

$$dP=\frac{\beta}{\kappa}\,dT$$

where `estimateThermalExpansionCoefficient(fluid, dT)` returns the isobaric expansion
coefficient $\beta$ in 1/K, and
`estimateIsothermalCompressibility(fluid, dP)` returns the isothermal compressibility
$\kappa$ in 1/Pa. The finite-difference steps are in K and Pa respectively and must be positive.
`simplifiedPressureRise(beta, kappa, deltaT)` returns a pressure change in Pa.

This relation is a local diagnostic, not a validation tolerance for a long temperature interval.
The repository regression demonstrates only a subcooled-propane case over 5 K where the simplified
and EOS results differ by less than 30%.

## Executable Java Workflow

The following state and step sizes are exercised by
`BlockedInLiquidExpansionAnalysisTest`:

```java
import neqsim.process.util.fire.BlockedInLiquidExpansionAnalysis;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

double referenceTemperatureK = 293.15;
double referencePressureBara = 15.0;

SystemInterface liquid =
    new SystemSrkEos(referenceTemperatureK, referencePressureBara);
liquid.addComponent("propane", 1.0);
liquid.setMixingRule("classic");

double[] temperaturesK = {
    referenceTemperatureK,
    referenceTemperatureK + 2.0,
    referenceTemperatureK + 4.0,
    referenceTemperatureK + 6.0,
    referenceTemperatureK + 8.0,
    referenceTemperatureK + 10.0
};
double[] absolutePressuresPa =
    BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(
        liquid, temperaturesK);

double betaPerK =
    BlockedInLiquidExpansionAnalysis.estimateThermalExpansionCoefficient(
        liquid, 0.5);
double kappaPerPa =
    BlockedInLiquidExpansionAnalysis.estimateIsothermalCompressibility(
        liquid, 2.0e5);

double comparisonTemperatureRiseK = 5.0;
double simplifiedPressureRisePa =
    BlockedInLiquidExpansionAnalysis.simplifiedPressureRise(
        betaPerK, kappaPerPa, comparisonTemperatureRiseK);
```

`absolutePressuresPa` contains absolute pressures, not pressure rises. If the first requested
temperature equals the initialized temperature, its result should reproduce the initial absolute
pressure within the numerical density tolerance. Calculate a rise explicitly, for example
`absolutePressuresPa[i] - absolutePressuresPa[0]`.

Before accepting a result, require finite positive pressures, verify the expected trend, confirm that
the supplied fluid still represents the intended liquid phase, and compare against nearby step
sizes or an independent property source. Treat a bracket failure as a failed screen, not as evidence
of acceptable pressure.

## Thermal-Relief Design Handoff

This analysis supplies no heat-input model, expansion volume rate, required relieving rate,
accumulation case, back pressure, discharge coefficient, viscosity correction, inlet/outlet
hydraulics, or certified orifice selection. Those inputs cannot be inferred from a pressure rise alone.

`ReliefValveSizing.calculateLiquidReliefArea(...)` is a separate static screen. It requires an
independently established liquid volume flow at relieving conditions in m³/s, liquid density in
kg/m³, absolute set and back pressures in Pa, overpressure fraction, viscosity in Pa·s, and valve
configuration. Use the project heat-transfer and hydraulic basis to establish those inputs, then
apply the licensed project standard and vendor data.

## Standards and Evidence Boundary

API 521 identifies blocked-in liquid thermal expansion as an overpressure scenario, while API 520
provides relief-device sizing methods. Edition and section numbering must be checked against the
project's licensed copies; NeqSim does not reproduce or certify either standard.

The repository regression is numerical software evidence for one EOS case. It is not experimental
validation. It is not a universal 30% acceptance criterion, and it is not evidence that SRK is
suitable for every liquid.

## Limitations

The current pressure screen assumes fixed mass and rigid volume. It does not model pipe/vessel
elasticity, vapour space, boiling or flashing acceptance, dissolved-gas release, non-uniform or
time-dependent heating, trace heating, insulation credit, thermal relief flow, relief-line
hydraulics, valve dynamics, material limits, or structural failure.

Use a fluid model and characterization suitable for the liquid and pressure range, perform
sensitivity checks, and retain explicit units, input provenance, software version, and failed-case
diagnostics. Safety-critical conclusions require project-specific standards, independent
verification, and accountable engineering review.
