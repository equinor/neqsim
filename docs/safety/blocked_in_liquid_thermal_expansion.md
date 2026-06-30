---
title: Blocked-In Liquid Thermal Expansion Screening
description: Rigorous equation-of-state isochoric pressure-rise screening for blocked-in (liquid-full, no vapor space) piping or vessel segments per API 521 7th Ed. Section 4.4.12, plus a simplified beta/kappa estimate.
---

Blocked-in liquid thermal expansion is one of the most severe overpressure scenarios in process
plants: because liquids are nearly incompressible, even a modest temperature rise can produce
extreme pressure increases when a segment is isolated with no vapor space and no relief path.
`BlockedInLiquidExpansionAnalysis` (`neqsim.process.util.fire`) provides two complementary
screening methods for this scenario, per API 521 7th Ed. Section 4.4.12.

## When to Use This vs `TrappedLiquidFireRuptureStudy`

| Tool | Scope | Use when |
|------|-------|----------|
| `BlockedInLiquidExpansionAnalysis` | Pure thermal-expansion pressure rise for a blocked-in liquid (no fire, no pipe stress/flange checks) | Quick check of whether a blocked-in liquid segment needs a thermal relief valve (TSV), or to size the magnitude of an expansion-pressure problem |
| `TrappedLiquidFireRuptureStudy` ([docs](trapped_liquid_fire_rupture.md)) | Full fire-exposure transient: wall heat-up, pipe/flange stress, rupture time, PFP demand | Fire case screening for a blocked-in segment, including material strength derating and rupture-time estimation |

Both classes provide complementary screening: use `BlockedInLiquidExpansionAnalysis`
(`neqsim.process.util.fire`) first as a fast, fire-independent screening check, then move to
`TrappedLiquidFireRuptureStudy` (`neqsim.process.safety.rupture`) when a fire scenario and
material/flange checks are required.

## Two Calculation Modes

1. **Rigorous isochoric pressure march** — `computeIsochoricPressureProfile(fluid, temperaturesK)`
   re-flashes a clone of the fluid at each requested temperature, searching (bracket + bisection)
   for the pressure that reproduces the reference density recorded at the initial blocked-in
   state. This avoids the constant-property assumption of the simplified relation and is accurate
   over large temperature spans.
2. **Simplified screening relation** — the classical $dP = (\beta / \kappa)\, dT$ relation from API
   521 §4.4.12, where $\beta$ is the isobaric thermal expansion coefficient and $\kappa$ is the
   isothermal compressibility. Use `estimateThermalExpansionCoefficient(fluid, dT)` and
   `estimateIsothermalCompressibility(fluid, dP)` to estimate $\beta$ and $\kappa$ by central finite
   differences at the reference state, then `simplifiedPressureRise(beta, kappa, deltaT)` for the
   pressure rise. This relation is only accurate for small temperature steps around the reference
   state; for larger temperature changes prefer the isochoric pressure march.

## Java Example

```java
import neqsim.process.util.fire.BlockedInLiquidExpansionAnalysis;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface oil = new SystemSrkEos(298.15, 10.0);
oil.addComponent("n-heptane", 100.0);
oil.setMixingRule("classic");

// 1. Rigorous isochoric pressure march from 25 C to 60 C
double[] temperaturesK = {298.15, 308.15, 318.15, 328.15, 333.15};
double[] pressuresPa =
    BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(oil, temperaturesK);

// 2. Simplified beta/kappa screening estimate for a quick cross-check
double beta = BlockedInLiquidExpansionAnalysis.estimateThermalExpansionCoefficient(oil, 1.0);
double kappa = BlockedInLiquidExpansionAnalysis.estimateIsothermalCompressibility(oil, 1.0e5);
double simplifiedDeltaPPa = BlockedInLiquidExpansionAnalysis.simplifiedPressureRise(beta, kappa, 35.0);
```

`computeIsochoricPressureProfile` returns pressures in Pa for each requested temperature (Kelvin);
`simplifiedPressureRise` returns the estimated pressure rise in Pa. The simplified estimate should
agree with the isochoric march to within roughly 30% for moderate temperature spans — use the
isochoric march as the reference and the simplified relation only for a fast order-of-magnitude
check.

## Standards Basis

| Topic | Standard / method |
|-------|--------------------|
| Liquid thermal expansion overpressure scenario | API 521 7th Ed., Section 4.4.12 |
| Thermal relief valve sizing once a relief requirement is confirmed | `ReliefValveSizing` ([docs](relief_valve_sizing_api.md)) — API 520 |
| Full fire-exposure rupture screening for the same blocked-in segment | `TrappedLiquidFireRuptureStudy` ([docs](trapped_liquid_fire_rupture.md)) |

## Limitations

This is a screening tool. It does not replace a thermal relief valve sizing study, and it does not
account for vapor space, trace heating, insulation credit, or non-uniform heating along a pipe run.
Use `ReliefValveSizing` to size the thermal relief device once a relief requirement is confirmed.
