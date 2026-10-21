---
title: "DOE Big Hill atmospheric-to-vacuum workflow"
description: "Composition-preserving DOE assay handoff with material, energy, utility, and emissions closure."
---

# DOE Big Hill atmospheric-to-vacuum workflow

This reference workflow carries one modeled DOE Big Hill Sweet assay through crude
heating, atmospheric fractionation, the calculated atmospheric-bottoms boundary,
and a caller-configured vacuum column. It is the executable integration layer
between the existing public-assay factory and the existing rigorous column APIs.

## Public provenance

The material identity is the 24 September 2021 U.S. Department of Energy
Strategic Petroleum Reserve Big Hill Sweet assay encoded by
<code>DoeBigHillSweetAssay</code>:

- [comprehensive assay workbook](https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx)
- [PIANO workbook](https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx)

The source reports assay composition and properties. It does **not** report a
matching atmospheric/vacuum tower configuration, pressure profile, heat duties,
reflux, side-draw policy, or product-yield benchmark. All such values in the
workflow are explicit engineering inputs or documented numerical qualification
settings.

## Composition-preserving handoff

<code>DoeBigHillVacuumFractionationCase.create(...)</code> retains the earlier
normalized three-cut 650 degF+ screening feed. The new
<code>createFromAtmosphericBottoms(...)</code> overload serves a different
purpose: it clones the calculated atmospheric liquid-bottoms stream, verifies
that the three named DOE heavy pseudo-components remain present, applies the
caller's explicit vacuum-feed temperature and pressure, and configures the
existing MESH-residual vacuum column.

No component is re-created, discarded, or replaced by a normalized assay slice.
The source stream is not mutated. This preserves one assay identity across the
software boundary while keeping the two feed contracts distinguishable.

~~~java
DoeBigHillVacuumFractionationCase.OperatingInputs vacuumInputs =
    new DoeBigHillVacuumFractionationCase.OperatingInputs(
        12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);

DoeBigHillVacuumFractionationCase vacuum =
    DoeBigHillVacuumFractionationCase.createFromAtmosphericBottoms(
        "DOE Big Hill connected vacuum",
        atmosphericColumn.getLiquidOutStream(),
        vacuumInputs);

vacuum.getColumn().run(UUID.randomUUID());
DoeBigHillVacuumFractionationResult result =
    DoeBigHillVacuumFractionationResult.evaluate(vacuum);
~~~

The same public method is accessible through NeqSim's normal JPype bridge. The
caller remains responsible for running and accepting the upstream atmospheric
solve before constructing the handoff.

## Connected operating sensitivity

The reboiler-temperature sensitivity also accepts the solved atmospheric-bottoms
stream directly. Each point independently clones the complete handoff composition,
changes only the caller-owned vacuum reboiler temperature, runs the qualified
MESH column, and evaluates the existing immutable result contract.

~~~java
double[] reboilerTemperaturesKelvin = {698.0, 702.0};
DoeBigHillVacuumReboilerTemperatureSensitivity sensitivity =
    DoeBigHillVacuumReboilerTemperatureSensitivity.run(
        "DOE Big Hill connected vacuum screen",
        atmosphericColumn.getLiquidOutStream(),
        vacuumInputs,
        reboilerTemperaturesKelvin);
~~~

The connected overload is JPype-callable with the same Java array and stream
objects. The repository regression uses the exact public overload and verifies
that the source stream is unchanged, both independently constructed points pass
mass, component, energy, residual, and boiling-order gates, and the returned
point array is defensive.

The two temperatures above are a deliberately bounded numerical perturbation
around the explicit 700 K baseline. They are neither DOE observations nor a
recommended plant envelope. A separately executed and rendered Python/JPype
notebook remains the publication gate for Python workflow evidence; this Java API
does not claim that unexecuted artifact.

## Regression-protected engineering contract

<code>DoeBigHillAtmosphericVacuumIntegrationTest</code> exercises the complete
connected boundary:

1. <code>DoeBigHillSweetAssay</code> creates the complete twelve-component
   modeled crude.
2. <code>FiredHeater</code> heats that same stream using explicit efficiency,
   fuel-LHV, CO2-factor, NOx-factor, and stack-temperature assumptions.
3. The qualified eight-tray atmospheric MESH case produces overhead, one liquid
   side draw, and calculated bottoms.
4. The cloned atmospheric bottoms feed the existing twelve-tray vacuum MESH case.
5. Final atmospheric and vacuum products close total mass and per-component
   molar flow within the columns' 5% numerical acceptance tolerance.
6. Both columns must report accepted mass and energy closure, and the vacuum
   result exposes discrete normal-boiling-point distributions and 10%/90%
   quantiles.
7. Fired duty, absorbed duty, fuel use, and direct CO2 bookkeeping must satisfy
   their independent identities.

The 5% column tolerance is a numerical integration acceptance inherited from the
existing broad-boiling qualification. It is not measurement uncertainty or a
plant-performance tolerance.

## Engineering limits

This workflow establishes composability, identity preservation, conservation,
energy closure, product ordering, and utility/emission bookkeeping. It does not
claim:

- measured DOE atmospheric or vacuum product yields;
- a public vacuum-plant operating benchmark or calibration;
- ASTM D86, D1160, or D2887 laboratory equivalence;
- pressure-corrected boiling curves or continuous simulated distillation;
- tray efficiency, pumparound, steam, side-stripper, fouling, exchanger-network,
  or fired-equipment design qualification;
- fuel-selection, combustion-chemistry, emissions-compliance, safe-operability,
  or economic approval.

Do not mix Sarir operating or plant-yield evidence into this DOE case. Use
<code>SarirAtmosphericFractionationCase</code> only for its separately documented
public reference boundary.
