---
title: "Vessel Thermomechanical Safety: Blowdown, Filling, Boil-Off and Rupture"
description: "Transient thermomechanical safety models for pressurized vessels in NeqSim — non-equilibrium (two-temperature) blowdown, dynamic PSV sizing, fast filling of Type IV hydrogen cylinders, cryogenic boil-off, composite-wall conduction, and fire/blowdown wall-rupture screening. Validated against the application cases in Andreasen (2026)."
---

# Vessel Thermomechanical Safety

This page documents the vessel thermomechanical safety models in
`neqsim.process.safety.depressurization`, `neqsim.process.safety.rupture`, and
`neqsim.process.util.heattransfer`. They cover the transient pressure and
temperature behavior of a pressurized vessel during emergency depressurization,
fast filling, fire exposure, and cryogenic storage — the cases that govern relief
device sizing, low-metal-temperature material selection, and wall rupture.

These models complement the steady-state blowdown / MDMT workflow in the
`neqsim-depressurization-mdmt` skill and the relief and flare network classes.
They resolve
effects that a single-temperature lumped model misses: the gas–liquid temperature
bifurcation in a fire, the conservatism of steady-state PSV sizing, the
compression heating of a fast-filled cylinder, and the through-wall thermal lag
of a composite wall.

---

## Background and Validation

The models reproduce the headline application results of:

> Andreasen, A. (2026). *Thermomechanical safety analysis of pressurized
> equipment.* Journal of Loss Prevention in the Process Industries, 103, 106088.

The committed JUnit regression tests assert the robust, model-independent
findings of that study (trends, governing-limit flagging, conservatism
direction) rather than brittle absolute values:

| Case | Application | Validated finding |
|------|-------------|-------------------|
| §4.1 | Dynamic PSV sizing (fire case) | Steady-state API 521 sizing is conservative; the dynamically required orifice is smaller (oversizing ratio &gt; 1) |
| §4.2 | Type IV hydrogen cylinder fast fill | Compression heating raises the gas temperature; the liner upper-temperature limit governs a safe fill rate |
| §4.3 | CO₂ storage boil-off | Boil-off load falls sharply with insulation thickness; the effective heat-transfer coefficient is conduction-limited |
| §4.4 | LPG vessel in a fire | A bare vessel ruptures within minutes; passive fire protection keeps the wall stress below the derated strength |

---

## Standards

- **API STD 521** 7th ed. — pressure-relieving and depressuring systems (§4.3 fire, §4.4/§5.15 relief)
- **API STD 520** — PSV sizing (orifice area)
- **ISO 21013** / BoilFAST methodology — cryogenic relief and boil-off
- **SAE J2601 / ISO 19880-1** — hydrogen fueling and fast-fill liner limits
- **ASME UCS-66 / API 579 / EN 13445** — minimum design metal temperature (see the MDMT skill)
- **BS EN ISO 23251** — petroleum and natural gas industries, pressure-relieving systems

---

## Non-Equilibrium (Two-Temperature) Blowdown

`NonEquilibriumBlowdownModel` carries two temperature zones — vapour and liquid —
at a shared, vapour-controlled pressure. During depressurizing fire exposure the
vapour space can superheat by several hundred kelvin while the boiling liquid
stays pinned near its saturation temperature. This gas–liquid temperature
bifurcation is the governing input for low-metal-temperature and wall-rupture
assessments and is missed by single-temperature models.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.safety.depressurization.NonEquilibriumBlowdownModel;
import neqsim.process.safety.depressurization.NonEquilibriumBlowdownModel.NemResult;

SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 50.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("propane", 0.30);
fluid.addComponent("n-butane", 0.30);
fluid.setMixingRule("classic");

NonEquilibriumBlowdownModel model =
    new NonEquilibriumBlowdownModel(fluid, 10.0, 0.025, 0.72, 1.0e5);
model.setFireExposure(0.9, 1100.0, 30.0, 25.0); // emissivity, flame T [K], h [W/m2K], area [m2]
model.setWall(8000.0, 470.0);                   // wall mass [kg], cp [J/kgK]
model.setLatentHeat(350000.0);
model.setTimeStep(1.0).setMaxTime(600.0).setStopPressure(1.5e5);

NemResult res = model.run();
double bifurcationK = res.maxTemperatureBifurcationK; // peak (T_gas - T_liquid)
double finalGasT = res.finalGasTemperatureK;
double finalLiqT = res.finalLiquidTemperatureK;
```

The vapour-controlled pressure is found with an EOS-consistent constant-volume
flash each step; boil-off transfers moles from the liquid zone to the vapour
zone; and the vapour discharges through a choked / subsonic orifice. This is a
screening-grade non-equilibrium method (NEM): all flashes are guarded, so a
transient property failure degrades a single step rather than aborting the run.

The `NemResult` exposes time-series lists (`timeS`, `pressureBara`,
`gasTemperatureK`, `liquidTemperatureK`, `wallTemperatureK`, `gasMassKg`,
`liquidMassKg`) and scalar summaries (`initialPressureBara`,
`finalGasTemperatureK`, `finalLiquidTemperatureK`,
`maxTemperatureBifurcationK`, `flashFallbackCount`).

---

## Dynamic PSV Sizing (Fire Case)

`DynamicPsvSizingStudy` quantifies the conservatism of steady-state API 521 fire
PSV sizing. A spring-loaded PSV on a gas system cycles (pops at the set pressure,
reseats at the blowdown pressure), and a transient simulation that resolves this
cycling typically shows a smaller orifice keeps the peak accumulated pressure
within the allowable limit than the steady-state equation requires.

```java
import neqsim.process.safety.depressurization.DynamicPsvSizingStudy;
import neqsim.process.safety.depressurization.DynamicPsvSizingStudy.SizingComparison;

// gas fluid with mixing rule already set, e.g. SystemSrkEos methane
DynamicPsvSizingStudy study =
    new DynamicPsvSizingStudy(gas, 1.0, 150000.0, 11.0e5, 0.21, 1.0e5);
// vesselVolume [m3], fireHeatInput [W], setPressure [Pa], overpressureFraction (0.21 fire), backPressure [Pa]
study.setBlowdownFraction(0.1).setDischargeCoefficient(0.975);

SizingComparison cmp = study.run();
double ratio = cmp.oversizingRatio;          // steady / dynamic required area (> 1 => conservative)
double steadyArea = cmp.steadyRequiredAreaM2;
double dynamicArea = cmp.dynamicRequiredAreaM2;
double peakP = cmp.peakDynamicPressurePa;
```

The dynamically required orifice area is found by bisection as the smallest area
that keeps the peak vessel pressure at or below the allowable accumulated
pressure, then compared against the steady-state API 521 area from
`ReliefValveSizing.calculateRequiredArea`. The cycling dynamics use ideal-gas
energy bookkeeping seeded from a single NeqSim flash for molar mass, heat-capacity
ratio, and compressibility.

---

## Fast Filling (Type IV Hydrogen Cylinders)

`VesselFillingSimulator` solves the coupled mass and energy balance for a vessel
being charged with gas. Because enthalpy (not internal energy) crosses the
boundary, the flow work of the incoming stream is captured automatically, which
reproduces the gas-temperature rise during fast filling. Liner temperature limits
relevant for Type III / Type IV composite hydrogen cylinders are checked against
configurable minimum and maximum temperatures.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.process.safety.depressurization.VesselFillingSimulator;
import neqsim.process.safety.depressurization.VesselFillingSimulator.VesselFillingResult;

SystemInterface h2 = new SystemSrkEos(273.15 + 10.0, 51.0); // residual gas at 51 bara
h2.addComponent("hydrogen", 1.0);
h2.setMixingRule("classic");
new ThermodynamicOperations(h2).TPflash();
h2.initProperties();

VesselFillingSimulator sim = new VesselFillingSimulator(h2, 0.06); // 60 L cylinder
sim.setInletConditions(283.15, 360.0, 0.015); // supply T [K], supply P [bara], mass flow [kg/s]
sim.setTargetPressure(351.0);                 // stop pressure [bara]
sim.setLinerTemperatureLimits(233.15, 338.15); // -40 C / +65 C liner limits
sim.setTimeStep(1.0).setMaxTime(4000.0);

VesselFillingResult res = sim.run();
double peakGasT = res.maxFluidTemperatureK;
boolean linerOk = res.linerLimitsMet;
boolean tooHot = res.linerOverTemperature;
```

The internal gas-to-wall film coefficient may be a fixed value (via `setWall`)
or computed each step from the Woodfield mixed-convection charging correlation
(via `setWoodfieldHeatTransfer`). The `VesselFillingResult` exposes time-series
lists (`time`, `pressureBara`, `temperatureK`, `massKg`, `wallTempK`) and the
scalar flags `maxFluidTemperatureK`, `minFluidTemperatureK`, `maxPressureBara`,
`linerOverTemperature`, `linerUnderTemperature`, and `linerLimitsMet`.

---

## Cryogenic Boil-Off

`BoilOffCalculator` is a steady-state heat-ingress model for insulated cryogenic
or refrigerated vessels. The series thermal resistance of the outer film and the
insulation layer gives an effective heat-transfer coefficient

$$ h_{eff} = \left( \frac{1}{h_o} + \frac{t_{ins}}{k_{ins}} \right)^{-1} $$

from which the heat ingress $Q = h_{eff} A (T_{amb} - T_{fluid})$ and the
latent-heat boil-off rate $\dot{m} = Q / L$ follow. Sweeping the insulation
thickness reproduces the BoilFAST-style trade-off between insulation thickness
and relief load.

```java
import neqsim.process.util.heattransfer.BoilOffCalculator;

BoilOffCalculator calc = new BoilOffCalculator()
    .setSurfaceArea(150.0)             // m2
    .setOuterFilmCoefficient(10.0)     // W/(m2.K)
    .setInsulationConductivity(0.025)  // W/(m.K)
    .setAmbientTemperatureK(288.15)
    .setFluidTemperatureK(253.15)      // refrigerated CO2
    .setLatentHeat(320000.0);          // J/kg

double thinBoilOff = calc.boilOffRateKgPerH(0.05); // 5 cm insulation
double thickBoilOff = calc.boilOffRateKgPerH(0.30); // 30 cm insulation
double hEff = calc.effectiveHeatTransferCoefficient(0.30);
double qIngress = calc.heatIngressW(0.30);
```

For a conduction-limited wall the effective coefficient approaches
$k_{ins}/t_{ins}$, so thicker insulation strongly reduces both the heat ingress
and the boil-off relief load.

---

## Composite-Wall Conduction

`CompositeWallConduction` is a one-dimensional transient heat-conduction solver
for a multi-layer vessel wall (for example an inner polymer liner, a composite
overwrap, and an outer steel shell). The non-uniform grid is integrated with the
unconditionally stable Crank-Nicolson scheme and a Thomas-algorithm tridiagonal
solve. The outer face receives a prescribed absorbed fire flux (Neumann boundary)
and the inner face exchanges heat with the contained fluid through a convective
film coefficient (Robin boundary).

Because the solver resolves the temperature gradient across the wall thickness,
it captures the through-wall thermal lag that the lumped-capacitance model misses
once the Biot number is no longer small. The static
`CompositeWallConduction.biotNumber(...)` helper supports that screening: the
lumped model is adequate for $Bi < 0.1$, and this distributed solver should be
preferred above it. Wetted and unwetted zones of a partially filled vessel are
represented by two independent instances driven with the same outer fire flux but
different inner film coefficients and fluid temperatures.

---

## Vessel Rupture in Fire / Blowdown

`VesselRuptureAnalyzer` tracks the von Mises wall stress against the
temperature-derated allowable tensile strength of the material throughout a fire
or blowdown transient. Rupture is predicted at the first crossover where the
stress reaches the allowable strength; the crossover time is found by linear
interpolation. The temperature derating is supplied by `MaterialStrengthCurve`.

```java
import neqsim.process.safety.rupture.VesselRuptureAnalyzer;
import neqsim.process.safety.rupture.VesselRuptureAnalyzer.VesselRuptureResult;
import neqsim.process.safety.rupture.MaterialStrengthCurve;

MaterialStrengthCurve steel = MaterialStrengthCurve.carbonSteel("CS", 245.0e6, 415.0e6);
VesselRuptureAnalyzer analyzer = new VesselRuptureAnalyzer(0.5, 0.012, steel); // radius [m], thickness [m]

// transient history (e.g. from a fire model) — time [s], pressure [Pa], metal temperature [K]
double[] timeS = {0.0, 60.0, 120.0, 180.0, 240.0, 300.0};
double[] pressurePa = {18e5, 20e5, 22e5, 24e5, 25e5, 26e5};
double[] metalTempK = {300.0, 420.0, 540.0, 660.0, 780.0, 900.0};

VesselRuptureResult res = analyzer.analyze(timeS, pressurePa, metalTempK);
boolean ruptured = res.ruptured;
double ruptureTime = res.ruptureTimeS;       // NaN if no rupture
double minMargin = res.minMarginPa;          // closest approach to rupture
```

A bare vessel exposed to a full fire ramps quickly to wall temperatures where the
derated strength falls below the hoop stress and ruptures within minutes. With
passive fire protection (a much slower wall heat-up and lower peak pressure) the
margin stays positive throughout and rupture is prevented — the §4.4 LPG
validation case.

---

## Choosing the Right Model

| Question | Model |
|----------|-------|
| Does the gas superheat while the liquid stays cold during a fire blowdown? | `NonEquilibriumBlowdownModel` |
| Is the steady-state fire PSV oversized? | `DynamicPsvSizingStudy` |
| How hot does a fast-filled H₂ cylinder get, and is the liner limit exceeded? | `VesselFillingSimulator` |
| What relief / vent rate must a cryogenic vessel handle, and how much insulation? | `BoilOffCalculator` |
| Does the through-wall thermal lag matter for a composite wall? | `CompositeWallConduction` (check `biotNumber`) |
| Will the vessel wall rupture in a fire, and is PFP sufficient? | `VesselRuptureAnalyzer` + `MaterialStrengthCurve` |
| What is the blocked-outlet overpressure with a relief device? | `BlockedOutletOverpressureAnalyzer` |

For steady-state blowdown sizing and the MDMT material check, use the
`neqsim-depressurization-mdmt` skill.

---

## Related Documentation

- [Safety Systems Documentation](README.md)
- [Fire Blowdown Capabilities](fire_blowdown_capabilities.md)
- [Fire Heat Transfer Enhancements](fire_heat_transfer_enhancements.md)
- [PSV Dynamic Sizing Example](psv_dynamic_sizing_example.md)
- [Trapped Liquid Fire Rupture](trapped_liquid_fire_rupture.md)
- [Trapped Inventory Calculator](trapped_inventory_calculator.md)
