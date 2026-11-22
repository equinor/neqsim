---
title: "Well Planning Physics: Drift-Flux Tubing, Gas-Condensate Inflow, APB, Chemicals, Injectors and Nodal Analysis"
description: "Screening-level well planning calculators in NeqSim: Ramey wellbore thermal model, drift-flux tubing hydraulics with flow regime and liquid-loading output, gas-condensate inflow with banking and non-Darcy flow, annular pressure build-up, tubing movement and triaxial check, hydrate-inhibitor treatment along the well, injection-well hydraulics with fracture limit, and nodal analysis."
keywords: "well planning, drift flux, Hasan Kabir, Turner loading, erosional velocity, Ramey, wellbore temperature, gas condensate, condensate banking, Forchheimer, annular pressure build-up, APB, tubing movement, buckling, MEG, methanol, hydrate inhibitor, injectivity, fracture pressure, nodal analysis"
---

The package `neqsim.process.fielddevelopment.wellplanning` collects well-planning physics that complements the
existing well and tubing classes (`WellSystem`, `WellFlow`, `TubingPerformance`, `InjectionWellModel`,
`WellIntegrityScreening`, `WellDesignCalculator`). Every class is a **screening** calculator. Validation is by
analytic limits and textbook formulas, **not** field data; nothing here replaces a qualified well-engineering tool.

| Class | Purpose | Status |
| ----- | ------- | ------ |
| `WellboreThermalModel` | Ramey / Hasan-Kabir flowing temperature for producers and injectors, layered U-value, lumped shut-in cooldown | implemented, analytic-limit validated |
| `DriftFluxWellboreHydraulics` | Vertical and deviated tubing traverse with flow regime, holdup, Turner loading ratio, API RP 14E erosional ratio | implemented, analytic-limit and Beggs-Brill compared |
| `GasCondensateInflowModel` | Radial gas-condensate inflow with banking (Fevang-Whitson style) and Forchheimer flow | implemented, dry-gas limit validated |
| `AnnularPressureBuildup` | Trapped-annulus APB with Lame compliance, steel expansion, bleed and gas cushion | implemented, closed-form validated |
| `TubingMovementCalculator` | Piston, ballooning, temperature and buckling length change, restraint force, von Mises check | implemented, closed-form validated |
| `WellChemicalTreatment` | CPA hydrate-inhibitor dose per station, partitioning, lean-solution rate, pump and line basis | implemented, Hammerschmidt-compared |
| `InjectionWellHydraulics` | Water/gas injector traverse with density change, fracture limit with thermal stress, matrix operating point | implemented, analytic-limit validated |
| `NodalAnalysis` | IPR x VLP intersections, stability, sensitivity sweeps, adapters | implemented, closed-form validated |

## Wellbore temperature and shut-in

The flowing temperature uses the relaxation distance
$A = w c_p (k_e + r_{to} U f(t)) / (2 \pi r_{to} U k_e)$. For a producer entering at depth $L$ with temperature
$T_{in}$ the profile is $T(z) = T_f(z) + gA + (T_{in} - T_f(L) - gA)\,e^{-(L-z)/A}$; for an injector
$T(z) = T_f(z) - gA + (T_{in} - T_f(0) + gA)\,e^{-z/A}$. Zero rate gives the geothermal profile and infinite rate keeps the
inlet temperature. `overallHeatTransferCoefficient(...)` builds $U$ from tubing, insulation, annulus, casing and cement
layers. `timeToReachTemperatureHours(...)` gives the lumped cooldown time to a hydrate temperature.

## Drift-flux tubing hydraulics

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 200.0);
fluid.addComponent("methane", 0.35);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("n-hexane", 0.08);
fluid.addComponent("n-decane", 0.45);
fluid.setMixingRule("classic");

WellboreThermalModel thermal = new WellboreThermalModel().setSurfaceTemperatureC(5.0)
    .setGeothermalGradient(0.03).setTotalDepth(2500.0).setMassFlowRate(8.0).setInletTemperatureC(80.0);
DriftFluxWellboreHydraulics well = new DriftFluxWellboreHydraulics(fluid).setMassFlowRate(8.0)
    .setTubing(0.1, 4.5e-5).setStraightWell(2500.0, 0.0, 25).setThermalModel(thermal);
DriftFluxWellboreHydraulics.Result r = well.calculateFromWellhead(30.0);
double bhp = r.getBottomholePressureBara();
boolean loading = r.isLiquidLoadingRisk();
double erosionRatio = r.getMaxErosionalRatio();
```

Closures follow Hasan and Kabir: bubble ($\alpha \le 0.25$), slug ($\alpha < 0.52$), churn and annular
($v_{sg} \ge 3.1(\sigma g \Delta\rho/\rho_G^2)^{0.25}$, Wallis void fraction). Evidence: static column equals
$\rho g L$; horizontal liquid matches Darcy-Weisbach; dry gas matches an independent fine-step integration; the oil
case agrees with `PipeBeggsAndBrills` within 4 percent of the pressure drop. For the synthetic gas-condensate case the
drift-flux drop is lower than Beggs and Brill (which over-predicts wet-gas holdup); the NeqSim `PipeGray` gave a still lower
drop, so the three models spread and none is field-calibrated. Not modelled: acceleration drop, emulsion viscosity,
entrainment, stratified flow below 10 degrees from horizontal (a warning is raised).

## Gas-condensate inflow

```java
SystemInterface gc = new SystemSrkEos(273.15 + 110.0, 300.0);
gc.addComponent("methane", 0.75);
gc.addComponent("ethane", 0.08);
gc.addComponent("propane", 0.05);
gc.addComponent("n-butane", 0.03);
gc.addComponent("n-hexane", 0.04);
gc.addComponent("n-octane", 0.03);
gc.addComponent("nC10", 0.02);
gc.setMixingRule("classic");

GasCondensateInflowModel inflow = new GasCondensateInflowModel(gc, 110.0).setReservoirPressure(450.0)
    .setFormation(20.0, 40.0).setGeometry(500.0, 0.108, 0.0).setRelativePermeability(0.2, 0.1, 1.0, 1.0, 3.0, 3.0);
inflow.useGeertsmaBeta(0.15);
GasCondensateInflowModel.Result res = inflow.calculate(100.0);
double q = res.getGasRateSm3PerDay();
double impairment = res.getBankingImpairment();
double dewPoint = res.getDewPointBara();
```

The molar rate solves $\Psi(p_e) - \Psi(p_{wf}) = a n_w + b n_w^2$ with $\Psi = \int k_{rg}\rho_g/(\mu_g V)\,dp$. Above the
dew point this is the real-gas pseudo-pressure solution (validated against an independent integral of $2p/(\mu Z)$).
Below it $k_{ro}/k_{rg}$ follows the constant-composition liquid-to-vapour ratio (region 2) and immobile oil uses the
CCE saturation (region 3). Not implemented: velocity-dependent relative permeability, CVD composition change.

## Annular pressure build-up and tubing movement

```java
AnnularPressureBuildup.Result apb = new AnnularPressureBuildup().setAnnulusLength(1500.0)
    .setGeometry(0.1397, 0.1215, 0.2204, 0.2445).setFluidProperties(3.0e-4, 4.5e-5).setTemperatureChange(40.0)
    .setInitialPressure(10.0).calculate();
double dP = apb.getDeltaPressureBar();
WellIntegrityScreening.AnnulusReading reading = apb.toAnnulusReading("A", 200.0);

TubingMovementCalculator.Result mv = new TubingMovementCalculator().setLength(3000.0)
    .setGeometry(0.1143, 0.0985, 0.12, 0.1594).setLoadChange(150.0, 20.0, 120.0, 20.0, 40.0).calculate();
double freeMovement = mv.getFreeDeltaLM();
```

The APB solves $V_0\alpha\Delta T - V_0\kappa\Delta P - V_{bled} = C\Delta P + D + V_g \Delta P/(P_0+\Delta P)$; the rigid limit is
$\alpha\Delta T/\kappa$. Fluid $\alpha,\kappa$ can be derived from a NeqSim liquid with `setFluidFromThermoSystem`. Tubing movement
uses the Lubinski piston, ballooning, temperature and helical-buckling terms; a fixed packer returns
$-EA_s\Delta L/L$. Vertical wells only; no deviated friction or bending.

## Hydrate-inhibitor treatment along the well

```java
SystemInterface fluid = new SystemSrkCPAstatoil(293.15, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-butane", 0.01);
fluid.addComponent("water", 0.05);
fluid.setMixingRule(10);

WellChemicalTreatment treatment = new WellChemicalTreatment(fluid)
    .setInhibitor(WellChemicalTreatment.HydrateInhibitor.MEG, 0.9).setSubcoolingMargin(3.0).setWaterRate(2000.0)
    .addStation("tree shut-in", 120.0, 8.0);
WellChemicalTreatment.Result r = treatment.evaluate();
double leanKgPerHour = r.getLeanSolutionKgPerHour();
```

The required mass fraction uses the rigorous CPA hydrate temperature of the whole fluid, so methanol losses to gas are
included; the CPA inhibition effect agrees with Hammerschmidt within about 35 percent in the test fluid. Scale,
corrosion and scavenger doses are sized with the existing classes in `process.chemistry` and converted to injection
rates with `chemicalInjectionRateLitresPerHour`. Pump power and capillary-line friction have closed-form helpers.
Salt-in-MEG solubility, regeneration and kinetic inhibitors are not modelled.

## Injection wells

`InjectionWellHydraulics` integrates density along the tubing with NeqSim flashes (water with CPA, gas with SRK or
GERG), applies a temperature profile, and reports the fracture margin with $\Delta\sigma = \alpha E \Delta T/(1-\nu)$
(upper bound). `findOperatingPoint(whp)` finds the matrix-injectivity rate and caps it at the fracture pressure.
Hydrate and scale compatibility are screened with the existing flow-assurance and `WaterCompatibilityScreener` classes.

## Nodal analysis

```java
NodalAnalysis.InflowCurve ipr = NodalAnalysis.linearInflow(300.0, 2.0);
NodalAnalysis.OutflowCurve vlp = NodalAnalysis.tabulatedOutflow(new double[] {1.0, 100.0, 400.0},
    new double[] {85.0, 140.0, 300.0});
NodalAnalysis.Result result = new NodalAnalysis(ipr, vlp).setRateRange(1.0, 400.0).solve();
double q = result.getOperatingPoint().getRate();
```

Intersections are classified stable or unstable (liquid loading gives a U-shaped outflow with an unstable low-rate
root). `sweepOutflow` and `sweepInflow` run tubing-size, wellhead-pressure, skin, GOR, water-cut or depletion
sensitivities through factories; `fromDriftFlux` and `fromGasCondensate` adapt the classes above.

## Not implemented

Mechanistic horizontal and stratified flow, tubing-annulus fluid conduction transient, permafrost thaw, velocity-dependent
relative permeability, fracture (Hall plot) injection, salt-in-glycol solubility, triaxial design factors from ISO 13679
load cases and sour-service material approval (the existing `SourServiceAssessment` is a partial-pressure screen only).

The code snippets above are compiled and run by `WellPlanningDocExamplesTest`.
