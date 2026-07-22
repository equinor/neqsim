---
title: Iron-sulfide wall inventory and elemental-sulfur source
description: "Stateful screening model for FeS formation on carbon-steel walls, oxidation during oxygen ingress, and coupling of generated S8 to condensate transport and compressor deposition."
---

# Iron-sulfide wall inventory and elemental-sulfur source

`IronSulfideWallInventory` and `IronSulfideOxidationSource` represent a source that a fluid-only
equilibrium calculation cannot retain: corrosion product accumulated during an earlier wet or sour
period. The source can be connected directly to NeqSim's existing S8 solid flash, sulfur filter, and
compressor deposit models.

The intended root-cause chain is:

```text
water/seawater + carbon steel + H2S -> wall FeS inventory
wall FeS + later O2 ingress          -> S0 source represented as S8
S8 + entrained condensate            -> transport to compressor
warm local shaft/seal surface         -> condensate evaporation and solid-S8 deposition
```

This is a screening and inventory model, not a universal corrosion-rate correlation. Mineralogy,
scale ageing, porosity, water coverage, oxygen mass transfer and competing sulfate/thiosulfate
formation can change the rate and elemental-sulfur selectivity by orders of magnitude. Consequently,
all kinetic defaults are zero and an uncalibrated calculation reports low/base/high S0 rates.

## Model basis

The implemented bookkeeping reactions are:

\[
\begin{aligned}
\mathrm{Fe + H_2S} &\rightarrow \mathrm{FeS + H_2} \\
\mathrm{FeCO_3 + H_2S} &\rightarrow \mathrm{FeS + CO_2 + H_2O} \\
\mathrm{Fe_2O_3 + 3H_2S} &\rightarrow \mathrm{2FeS + S^0 + 3H_2O} \\
\mathrm{4FeS + 3O_2} &\rightarrow \mathrm{2Fe_2O_3 + 4S^0}
\end{aligned}
\]

The last equation is the default Fe2O3-equivalent screening branch. The oxygen stoichiometry and
fraction ending as S0 are configurable because partially oxidised sulfur and iron products are
expected in real scales. Iron oxide is stored on an Fe2O3-equivalent iron-inventory basis; it does not
assert that hematite is the only wall phase.

The scientific basis is the established low-temperature Fe-S reaction network and its metastable
phases: Rickard and Luther's review of iron-sulfide chemistry
([doi:10.1021/cr0503658](https://doi.org/10.1021/cr0503658)), Benning, Wilkin and Barnes' experimental
reaction pathways below 100 °C ([doi:10.1016/S0009-2541(99)00198-9](https://doi.org/10.1016/S0009-2541(99)00198-9)),
and the structural transformation measurements of Csákberényi-Malasics et al.
([doi:10.1016/j.chemgeo.2011.12.009](https://doi.org/10.1016/j.chemgeo.2011.12.009)). These sources
support retaining a polymorph/reactivity category and explicit uncertainty; they do not justify one
plant-independent oxidation rate.

## Java example

```java
IronSulfideWallInventory wall = new IronSulfideWallInventory(250.0, 80.0, 20.0);
wall.setPipeSurfaceAreaM2(1200.0);
wall.setPorosityFraction(0.35);
wall.setWettedFraction(0.20);
wall.setSurfaceAreaMultiplier(2.0);
wall.setFeSPhase(IronSulfideWallInventory.FeSPhase.MIXED);
// Alternative to direct masses: total thickness + skeletal density + lab mass fractions.
// wall.setMassesFromMeasuredScaleThickness(0.001, 4300.0, 0.70, 0.20, 0.10);

IronSulfideOxidationSource source =
    new IronSulfideOxidationSource("pipeline wall source", oxygenContainingGas, wall);
source.setFeCO3SulfidationFractionPerHour(1.0e-4);
source.setFeSOxidationFractionPerHour(2.0e-3);
source.setOxygenTransferFraction(0.10);
source.setElementalSulfurYieldFraction(0.50);
source.setElementalSulfurYieldBounds(0.10, 1.00);
source.setKineticUncertaintyFactor(3.0);
source.run();                         // rate calculation; inventory is unchanged

double s0 = source.getElementalSulfurRateKgPerHour();
String report = source.toJson();

// Advance a one-hour oxygen-containing nitrogen purge and retain its history.
IronSulfideWallInventory.ExposureEvent purge =
    new IronSulfideWallInventory.ExposureEvent(
        IronSulfideWallInventory.ExposureType.NITROGEN_PURGE,
        1.0, 30.0, 0.0, "Nitrogen purge with measured residual oxygen");
source.runExposure(purge, UUID.randomUUID());
```

`run()` deliberately does not mutate the historical wall inventory. Use `runTransient(dt, id)` or
`runExposure(event, id)` to consume/form material over time.

## Condensate and warm-shaft coupling

The outlet contains generated elemental sulfur as the existing `S8` component. It can therefore feed
`SolidFlashDepositSource`. Include separator carry-over or another estimate of entrained condensate in
the connected stream. A local temperature can be supplied explicitly or read from a solved compressor
thermal node:

```java
SolidFlashDepositSource deposit = new SolidFlashDepositSource(
    source.getOutletStream(), "S8", DepositMechanism.SULFUR_S8, 0.5);
deposit.setThermalNode(compressor, CompressorThermalModel.INLET_SHAFT);

double precipitated = deposit.getPrecipitationRate("kg/hr");
double deposited = deposit.getDepositRate("kg/hr");
double evaporatedCondensateFraction = deposit.getLastLiquidEvaporatedFraction();
```

The evaporation metric compares equilibrium liquid flow at the bulk inlet with liquid remaining at the
local P-T condition. It indicates whether a warm shaft can remove the liquid carrier at that location;
the capture fraction still needs plant evidence or a sensitivity range.

## Recommended RCA inputs

- scale mass or thickness and sampled mineralogy (XRD/SEM-EDS/XPS where available);
- pipe internal area, wet fraction, porosity, roughness/reactive-area multiplier;
- water, seawater, hydrotest, shutdown, purge and restart history;
- measured O2 concentration and duration, not only the nitrogen specification;
- entrained-liquid rate and condensate composition at compressor suction;
- local shaft/seal/bearing temperatures or compressor thermal-model estimates;
- deposit composition and sulfur oxidation state;
- uncertainty cases for kinetics, S0 yield, oxygen transfer and capture efficiency.

Avoid interpreting the maximum sulfur contained in FeS as an hourly rate. It is an inventory ceiling;
the released rate is the minimum of reaction kinetics, available FeS and oxygen transferred to the wall.

## Example notebook

[`examples/notebooks/IronSulfideWallSulfurDeposition.ipynb`](../../../examples/notebooks/IronSulfideWallSulfurDeposition.ipynb)
uses the reported 10 kg/h nitrogen stream with 2% oxygen as a screening example and connects the wall
source to a local warm-shaft solid flash.
