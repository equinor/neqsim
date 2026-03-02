---
title: "Vessel Depressurization and Filling"
description: "Dynamic modeling of pressure vessel filling, depressurization, and blowdown using VesselDepressurization. Covers thermodynamic modes, heat transfer models, fire cases, composite vessels, flow assurance, CNG tank scenarios, and Python/Java API reference."
---

# Vessel Depressurization and Filling

Dynamic modeling of pressure vessel filling, depressurization, and blowdown using the `VesselDepressurization` class.

## Table of Contents

- [Overview](#overview)
- [Enumerations](#enumerations)
- [Constructors](#constructors)
- [Vessel Geometry and Materials](#vessel-geometry-and-materials)
- [Operating Conditions](#operating-conditions)
- [Heat Transfer Models](#heat-transfer-models)
- [Fire Case Modeling](#fire-case-modeling)
- [Composite Vessels (Type III/IV)](#composite-vessels-type-iiiiv)
- [Transient Simulation](#transient-simulation)
- [Results and History](#results-and-history)
- [Flow Assurance Checks](#flow-assurance-checks)
- [Convenience Methods](#convenience-methods)
- [Bug Fixes (2025)](#bug-fixes-2025)
- [Java Examples](#java-examples)
- [Python Examples](#python-examples)
- [Related Documentation](#related-documentation)

---

## Overview

The `VesselDepressurization` class models dynamic filling and depressurization of pressure vessels. It supports:

1. **Multiple thermodynamic processes**: isothermal, isenthalpic, isentropic, isenergetic, and full energy balance
2. **Heat transfer models**: adiabatic, fixed U-value, fixed Q, calculated natural/mixed convection, and transient 1-D wall conduction
3. **Multi-component mixtures**: full thermodynamic calculations via NeqSim equation of state (SRK, PR, CPA, etc.)
4. **Orifice flow or fixed flow rates**: choked/subsonic discharge, or user-specified mass/volumetric rates
5. **Filling (pressurization)**: inlet enthalpy calculated at supply temperature and vessel pressure
6. **Fire case (API 521)**: external heat flux with wetted/unwetted surface areas
7. **Composite vessels**: liner + shell for Type III/IV hydrogen/CNG tanks
8. **Two-phase heat transfer**: separate gas-wetted and liquid-wetted wall zones
9. **Flow assurance**: hydrate formation, CO2 freezing, MDMT, and liquid rainout checks

**Package:** `neqsim.process.equipment.tank`

**Reference:** Andreasen, A. (2021). "HydDown: A Python package for calculation of hydrogen (or other gas) pressure vessel filling and discharge." *Journal of Open Source Software*, 6(66), 3695.

### Physical Model

```
                    ┌─────────────────────────────────┐
                    │          AMBIENT (T_amb)         │
                    │   ╔═══════════════════════╗     │
                    │   ║  Optional Liner        ║     │
     h_ext ─────────┤   ║  ┌───────────────┐    ║     ├────── h_ext
     (auto or       │   ║  │ VESSEL FLUID  │    ║     │     (Churchill-Chu
      manual)       │   ║  │ P, T, m, U    │    ║     │      or manual)
                    │   ║  │ Gas / Liquid   │    ║     │
                    │   ║  └───────────────┘    ║     │
                    │   ║  Steel / Composite     ║     │
                    │   ╚═══════════════════════╝     │
                    │          h_int (calculated)      │
                    └──────────────┬──────────────────┘
                                   │
                      Orifice (d) or Fixed Flow Rate
                                   │
                    ┌──────────────┴──────────────────┐
                    │  FILLING (inlet @ T_inlet)       │
                    │  DISCHARGE (back pressure P_bp)  │
                    └─────────────────────────────────┘
```

---

## Enumerations

### CalculationType

| Value | Description | Conservation Law | Use Case |
|-------|-------------|------------------|----------|
| `ISOTHERMAL` | Constant temperature | T = const | Quick estimates |
| `ISENTHALPIC` | Constant enthalpy | H = const | Adiabatic, no PV work (Joule-Thomson) |
| `ISENTROPIC` | Constant entropy | S = const | Adiabatic with PV work |
| `ISENERGETIC` | Constant internal energy | U = const | Closed adiabatic vessel |
| `ENERGY_BALANCE` | Full energy balance with heat transfer | dU = Q - m_dot * h | Most accurate; use for fire, wall effects |

### HeatTransferType

| Value | Description |
|-------|-------------|
| `ADIABATIC` | No heat exchange (Q = 0) |
| `FIXED_U` | Fixed overall heat transfer coefficient U [W/(m2*K)] |
| `FIXED_Q` | Fixed heat rate Q [W] |
| `CALCULATED` | Natural/mixed convection correlations with quasi-steady wall |
| `TRANSIENT_WALL` | 1-D transient conduction through wall (11-node finite difference) |

### VesselOrientation

| Value | Description |
|-------|-------------|
| `VERTICAL` | Characteristic length = vessel height; liquid level from bottom |
| `HORIZONTAL` | Characteristic length = diameter; liquid level by segment geometry |

### FlowDirection

| Value | Description |
|-------|-------------|
| `DISCHARGE` | Depressurization / blowdown |
| `FILLING` | Pressurization (inlet enthalpy at supply temperature) |

### VesselMaterial (Preset Wall Materials)

| Value | Density [kg/m3] | Cp [J/(kg*K)] | k [W/(m*K)] |
|-------|-----------------|----------------|-------------|
| `CARBON_STEEL` | 7850 | 490 | 45.0 |
| `STAINLESS_304` | 8000 | 500 | 16.2 |
| `STAINLESS_316` | 8000 | 500 | 16.3 |
| `DUPLEX_22CR` | 7800 | 500 | 19.0 |
| `ALUMINUM_6061` | 2700 | 896 | 167.0 |
| `TITANIUM_GR2` | 4510 | 520 | 16.4 |
| `CFRP` | 1600 | 1000 | 1.0 |
| `FIBERGLASS` | 1900 | 900 | 0.3 |

### LinerMaterial (for Type III/IV Vessels)

| Value | Density [kg/m3] | Cp [J/(kg*K)] | k [W/(m*K)] |
|-------|-----------------|----------------|-------------|
| `HDPE` | 945 | 1584 | 0.385 |
| `NYLON` | 1130 | 1700 | 0.25 |
| `ALUMINUM` | 2700 | 896 | 167.0 |

---

## Constructors

```java
// Name only (configure fluid later via setInletStream)
VesselDepressurization vessel = new VesselDepressurization("name");

// Name + initial fluid stream (recommended)
VesselDepressurization vessel = new VesselDepressurization("name", feedStream);
```

---

## Vessel Geometry and Materials

### Geometry

```java
// Option 1: Set volume directly
vessel.setVolume(0.5);  // m3

// Option 2: Set geometry (auto-calculates volume = cylinder + 2 hemispheres)
vessel.setVesselGeometry(
    2.0,                              // length [m]
    0.5,                              // inner diameter [m]
    VesselOrientation.VERTICAL        // orientation
);
// Volume = pi/4 * D^2 * L + pi/6 * D^3
```

### Wall Properties

```java
// Option 1: Manual wall properties
vessel.setVesselProperties(
    0.025,    // wall thickness [m]
    7850.0,   // density [kg/m3]
    490.0,    // heat capacity [J/(kg*K)]
    45.0      // thermal conductivity [W/(m*K)]
);

// Option 2: Preset material
vessel.setVesselMaterial(0.025, VesselMaterial.CARBON_STEEL);
```

### Default Wall Properties

| Property | Default Value | Unit |
|----------|---------------|------|
| Wall thickness | 0.015 | m |
| Wall density | 7800 | kg/m3 |
| Wall heat capacity | 500 | J/(kg*K) |
| Wall thermal conductivity | 45.0 | W/(m*K) |

---

## Operating Conditions

### Flow Rate

```java
// Option 1: Orifice-based flow (choked/subsonic)
vessel.setOrificeDiameter(0.010);       // m
vessel.setDischargeCoefficient(0.61);   // Cd (sharp-edge default)

// Option 2: Fixed volumetric flow rate
vessel.setFixedVolumetricFlowRate(1783.4, "Sm3/day");
// Also accepts "Sm3/hr" and "Sm3/sec"

// Option 3: Fixed mass flow rate
vessel.setFixedMassFlowRate(0.015);     // kg/s (positive)

// Revert to orifice-based
vessel.clearFixedFlowRate();
```

### Flow Direction

```java
vessel.setFlowDirection(FlowDirection.DISCHARGE);  // blowdown
vessel.setFlowDirection(FlowDirection.FILLING);     // pressurization
```

### Pressures and Temperatures

```java
vessel.setBackPressure(1.0);                // downstream pressure [bara]
vessel.setTargetPressure(20.0);             // stop condition [bar]
vessel.setAmbientTemperature(288.15);       // ambient [K]
vessel.setInletTemperature(288.15);         // filling gas supply T [K]
vessel.setInletTemperature(15.0, "C");      // or with unit string
vessel.setValveOpeningTime(5.0);            // ESD ramp time [s] (0 = instant)
```

### Initial Liquid Level

```java
vessel.setInitialLiquidLevel(0.5);  // 50% liquid by height
```

---

## Heat Transfer Models

### Adiabatic

No heat exchange. Gas temperature changes purely from expansion/compression work.

```java
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setHeatTransferType(HeatTransferType.ADIABATIC);
```

### Fixed U-Value

Overall heat transfer coefficient applied between fluid and ambient.

```java
vessel.setFixedU(10.0);  // W/(m2*K) — also sets HeatTransferType.FIXED_U
```

### Calculated (Quasi-Steady)

Natural convection correlations (Churchill-Chu) compute internal HTC from gas properties. External HTC can be auto-calculated or manually set.

```java
vessel.setHeatTransferType(HeatTransferType.CALCULATED);
vessel.setAmbientTemperature(288.15);
vessel.setCalculateExternalHTC(true);     // auto-calc via Churchill-Chu for air
// OR
vessel.setExternalHeatTransferCoefficient(5.0);  // manual [W/(m2*K)]
```

### Transient Wall (Most Accurate)

Full 1-D transient conduction through the wall using an 11-node explicit finite difference scheme (`TransientWallHeatTransfer` class). Robin boundary conditions on inner and outer surfaces.

```java
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL);
vessel.setCalculateExternalHTC(true);
vessel.setAmbientTemperature(288.15);
```

**Key behavior:**
- Wall thermal inertia acts as buffer: absorbs heat during filling, releases heat during emptying
- Wall thermal time constant: tau = (m_wall * Cp_wall) / (h * A)
- For heavy steel vessels with slow filling/emptying, this model is essential for realistic temperature predictions

### Two-Phase Heat Transfer

When liquid is present, separate wall zones track gas-wetted and liquid-wetted areas independently.

```java
vessel.setTwoPhaseHeatTransfer(true);
vessel.setInitialLiquidLevel(0.5);
```

### Internal HTC Correlations

| Flow Direction | Correlation | Description |
|----------------|-------------|-------------|
| DISCHARGE | Churchill-Chu | Natural convection on vertical/horizontal surface |
| FILLING | Mixed convection | Gnielinski (forced) + Churchill-Chu (natural), combined asymptotically: h = (h_forced^3 + h_natural^3)^(1/3) |

The `VesselHeatTransferCalculator` class (in `neqsim.process.util.fire`) implements these correlations.

### External HTC

Auto-calculated external HTC uses Churchill-Chu for natural convection in still air with:
- Air properties from ideal gas law and Sutherland's viscosity correlation
- Clamped to range 2-50 W/(m2*K)

---

## Fire Case Modeling

API 521 fire exposure with configurable heat flux and wetted surface fraction.

```java
vessel.setFireCase(true);
vessel.setFireHeatFlux(75000.0);           // W/m2 (75 kW/m2 pool fire)
vessel.setFireHeatFlux(75.0, "kW/m2");     // or with unit
vessel.setFireHeatFlux(23730, "BTU/hr/ft2"); // or in imperial
vessel.setWettedSurfaceFraction(0.5);      // 50% wetted

// Query fire state
boolean isFireCase = vessel.isFireCase();
double fireHeat = vessel.getFireHeatInput("kW");  // total heat rate
```

Fire heat is added to the energy balance regardless of the HeatTransferType setting.

---

## Composite Vessels (Type III/IV)

For hydrogen or CNG tanks with inner liner:

```java
// Option 1: Manual liner properties
vessel.setLinerProperties(
    0.005,    // thickness [m]
    945.0,    // density [kg/m3] (HDPE)
    1584.0,   // heat capacity [J/(kg*K)]
    0.385     // thermal conductivity [W/(m*K)]
);

// Option 2: Preset liner material
vessel.setLinerMaterial(0.005, LinerMaterial.HDPE);
```

When a liner is set, the transient wall model uses a two-layer conduction scheme (liner + wall).

---

## Transient Simulation

### Step-by-Step

```java
import java.util.UUID;

vessel.run();  // initialize

UUID simId = UUID.randomUUID();
double dt = 10.0;  // time step [seconds]

while (!vessel.isTargetPressureReached()) {
    vessel.runTransient(dt, simId);
}
```

### What Each Time Step Does

1. **Compute mass flow** from orifice equation or fixed rate
2. **Update component moles** proportionally (well-mixed assumption)
3. **Re-initialize** thermodynamic system at new composition
4. **Flash calculation** per `CalculationType`:
   - `ISOTHERMAL` uses TV-flash
   - `ISENTHALPIC` uses VH-flash (constant specific enthalpy)
   - `ISENTROPIC` uses VS-flash (constant specific entropy)
   - `ISENERGETIC` uses VU-flash (constant internal energy)
   - `ENERGY_BALANCE` uses VU-flash with U_new = U_old - m_dot * h_out * dt + Q_wall * dt
5. **Temperature sanity check** (clamp to 50-2000 K with recovery flash)
6. **Update wall temperature** via transient 1-D conduction or lumped model
7. **Record state** to history arrays
8. **Update outlet stream** (isenthalpic expansion to back pressure)

### Convenience Method

```java
// Run complete simulation, returns SimulationResult object
SimulationResult result = vessel.runSimulation(3600.0, 1.0);  // endTime=3600s, dt=1s
SimulationResult result = vessel.runSimulation(3600.0, 1.0, 10);  // record every 10 steps
```

### Time Step Selection

- Small time steps (dt = 0.5-10 s) required for numerical stability
- Larger steps can cause VU-flash convergence failures at low pressures
- For CNG filling/emptying at ~1800 Sm3/day, dt = 10 s is stable across 20-250 bar
- For rapid blowdown, dt = 0.5-1.0 s is recommended

---

## Results and History

### Current State Getters

| Method | Returns | Unit |
|--------|---------|------|
| `getPressure()` | Vessel pressure | Pa |
| `getPressure("bar")` | Pressure with unit | "Pa", "bar", "bara", "barg", "psi" |
| `getTemperature()` | Fluid temperature | K |
| `getTemperature("C")` | Temperature with unit | "K", "C", "F" |
| `getWallTemperature()` | Inner wall surface T | K |
| `getGasWallTemperature()` | Gas-wetted wall T (two-phase) | K |
| `getLiquidWallTemperature()` | Liquid-wetted wall T (two-phase) | K |
| `getMass()` | Mass in vessel | kg |
| `getDensity()` | Current density | kg/m3 |
| `getVolume()` | Vessel volume | m3 |
| `getVentTemperature()` | T after orifice expansion | K |
| `getVaporFraction()` | Vapor mole fraction | 0-1 |
| `getInternalEnergy()` | Internal energy | J |
| `getEnthalpy()` | Enthalpy | J |
| `getEntropy()` | Entropy | J/K |
| `getLiquidLevel()` | Liquid level fraction | 0-1 |
| `getThermoSystem()` | Thermodynamic system | SystemInterface |
| `getOutletStream()` | Outlet stream | StreamInterface |

### Discharge Analysis

| Method | Returns | Description |
|--------|---------|-------------|
| `getDischargeRate("kg/s")` | Mass flow rate | "kg/s", "kg/hr", "kg/min" |
| `getPeakDischargeRate("kg/s")` | Peak flow from history | For flare header sizing |
| `getTotalMassDischarged("kg")` | Total mass lost | "kg", "tonnes" |
| `getTimeToReachPressure(50.0)` | Time [s] or -1 | Seconds to reach specified pressure [bar] |
| `getMinimumTemperatureReached("C")` | Min fluid T | For MDMT assessment |
| `getMinimumWallTemperatureReached("C")` | Min wall T | For MDMT assessment |
| `isTargetPressureReached()` | boolean | Target pressure reached? |

### Flare Header Analysis

| Method | Returns | Description |
|--------|---------|-------------|
| `getFlareHeaderVelocity(0.3, "m/s")` | Gas velocity | In header of given diameter |
| `getFlareHeaderMach(0.3)` | Mach number | In header of given diameter |
| `hasLiquidRainout()` | boolean | Liquid in outlet? |
| `getOutletLiquidFraction()` | double | Liquid mass fraction in outlet |
| `getPeakOutletLiquidFraction()` | double | Worst-case liquid loading |

### History Arrays

| Method | Returns |
|--------|---------|
| `getTimeHistory()` | `List<Double>` [s] |
| `getPressureHistory()` | `List<Double>` [Pa] |
| `getTemperatureHistory()` | `List<Double>` [K] |
| `getMassHistory()` | `List<Double>` [kg] |
| `getGasWallTemperatureHistory()` | `List<Double>` [K] |
| `getLiquidWallTemperatureHistory()` | `List<Double>` [K] |
| `getLiquidLevelHistory()` | `List<Double>` [0-1] |
| `clearHistory()` | void |

### Export

```java
String csv = vessel.exportToCSV();
String json = vessel.exportToJSON();
```

### SimulationResult Object

Returned by `runSimulation()`. Contains parallel arrays accessed via:

`getTime()`, `getPressure()` [bar], `getTemperature()` [K], `getMass()` [kg],
`getWallTemperature()` [K], `getMassFlowRate()` [kg/s], `getGasWallTemperature()`,
`getLiquidWallTemperature()`, `getLiquidLevel()`, `getEndTime()`, `getTimeStep()`,
`size()`, `getInitialPressure()`, `getFinalPressure()`, `getInitialTemperature()`,
`getFinalTemperature()`, `getMassDischarged()` [kg], `getMassDischargedFraction()`,
`getMinTemperature()` [K], `getMinWallTemperature()` [K].

---

## Flow Assurance Checks

### Hydrate Risk

```java
double T_hydrate = vessel.getHydrateFormationTemperature();      // K
double T_hydrate_C = vessel.getHydrateFormationTemperature("C"); // C
boolean risk = vessel.hasHydrateRisk();                          // T < T_hydrate?
double subcooling = vessel.getHydrateSubcooling("K");            // T_hydrate - T
double maxSubcooling = vessel.getMaxHydrateSubcoolingDuringBlowdown(); // worst-case
```

### CO2 Freezing Risk

```java
double T_freeze = vessel.getCO2FreezingTemperature();     // K (-1 if no CO2)
double T_freeze_C = vessel.getCO2FreezingTemperature("C");
boolean risk = vessel.hasCO2FreezingRisk();               // T < T_freeze?
double subcooling = vessel.getCO2FreezingSubcooling();    // T_freeze - T [K]
```

### Comprehensive Risk Assessment

```java
Map<String, String> risks = vessel.assessFlowAssuranceRisks();
// Returns: HYDRATE, CO2_FREEZING, MDMT, LIQUID_RAINOUT status
```

---

## Convenience Methods

### Validation

```java
vessel.validate();                               // throws on errors
List<String> warnings = vessel.validateWithWarnings();  // non-throwing
```

### Orifice Sizing (API 521)

```java
// Find orifice diameter for target blowdown time
double d = vessel.calculateRequiredOrificeDiameter(
    900.0,   // target time [s]
    0.5      // pressure reduction fraction (50%)
);
```

### Orifice Sensitivity

```java
double[] sizes = {0.005, 0.010, 0.015, 0.020, 0.025};
vessel.runOrificeSensitivity(sizes, 2.0);  // time to reach 2 bar for each
```

### Static Factory Methods

```java
// Create two-phase fluid at saturation
SystemInterface fluid = VesselDepressurization.createTwoPhaseFluid(
    "propane", 300.0, 0.5);  // T=300K, 50% vapor

SystemInterface fluid = VesselDepressurization.createTwoPhaseFluidAtPressure(
    "propane", 10.0, 0.5);  // P=10 bar, 50% vapor
```

---

## Bug Fixes (2025)

Two critical bugs were discovered and fixed during the CNG tank modeling project:

### Bug 1: Cp Unit Conversion Error

**Problem:** Three internal HTC methods (`calculateInternalHeatTransferCoeff`,
`calculateGasHeatTransferCoeff`, `calculateLiquidHeatTransferCoeff`) had a spurious
x1000 multiplier on the heat capacity conversion.

Since `Phase.getCp()` returns J/K (total) and `Phase.getMolarMass()` returns kg/mol,
the conversion `Cp = getCp() / nMoles / getMolarMass()` already yields J/(kg*K).
The extra x1000 inflated Cp by 1000x, cascading through Pr, Ra, Nu, and h to produce
grossly inflated heat transfer coefficients and unstable wall temperatures.

**Fix:** Removed the spurious x1000 multiplier. The correct conversion is:

```java
Cp = thermoSystem.getPhase(0).getCp()
    / thermoSystem.getPhase(0).getNumberOfMolesInPhase()
    / thermoSystem.getPhase(0).getMolarMass();  // J/(kg*K)
```

### Bug 2: VU-flash Static Variables

**Problem:** The `OptimizedVUflash` solver used static tracking variables
(`lastPressure`, `lastTemperature`) that contaminated results between successive
flash calls. In transient simulations, the previous time step's converged state
would incorrectly warm-start the next flash, causing convergence to wrong solutions.

**Fix:** Converted to instance-level variables:

```java
// Before (broken): shared across all instances
private static double lastPressure = Double.NaN;
private static double lastTemperature = Double.NaN;

// After (fixed): per-instance
private double lastPressure = Double.NaN;
private double lastTemperature = Double.NaN;
```

Additionally, temperature and pressure bounds were widened and convergence criteria
were strengthened to check actual V/U specification errors (`volErr < 1e-6` and
`hErr < 1e-5`) rather than just iteration variable changes.

---

## Java Examples

### Blowdown Simulation

```java
import neqsim.process.equipment.tank.VesselDepressurization;
import neqsim.process.equipment.tank.VesselDepressurization.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

// Natural gas at 100 bara
SystemSrkEos gas = new SystemSrkEos(300.0, 100.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(100.0, "kg/hr");
feed.run();

VesselDepressurization vessel = new VesselDepressurization("Scrubber", feed);
vessel.setVesselGeometry(2.5, 1.0, VesselOrientation.VERTICAL);
vessel.setVesselProperties(0.025, 7850.0, 490.0, 45.0);
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL);
vessel.setOrificeDiameter(0.025);
vessel.setDischargeCoefficient(0.85);
vessel.setBackPressure(1.0);
vessel.setAmbientTemperature(288.15);
vessel.setCalculateExternalHTC(true);
vessel.run();

UUID id = UUID.randomUUID();
double dt = 1.0;
while (vessel.getPressure("bar") > 2.0) {
    vessel.runTransient(dt, id);
}

System.out.println("Min T: " + vessel.getMinimumTemperatureReached("C") + " C");
System.out.println("Min wall T: " + vessel.getMinimumWallTemperatureReached("C") + " C");
```

### CNG Tank Filling

```java
// CNG filling: 20 -> 250 bar
SystemSrkEos gas = new SystemSrkEos(288.15, 20.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.046);
gas.addComponent("propane", 0.018);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.054);
gas.setMixingRule("classic");
gas.setMultiPhaseCheck(true);
gas.init(0);
gas.init(1);

Stream feed = new Stream("feed", gas);
feed.setTemperature(288.15, "K");
feed.setPressure(20.0, "bara");
feed.setFlowRate(100.0, "kg/hr");
feed.run();

VesselDepressurization vessel = new VesselDepressurization("CNG Tank", feed);
vessel.setVesselGeometry(19.0, 0.999, VesselOrientation.VERTICAL);
vessel.setVesselProperties(0.0335, 7850.0, 480.0, 50.0);
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL);
vessel.setFlowDirection(FlowDirection.FILLING);
vessel.setTargetPressure(250.0);
vessel.setInletTemperature(288.15);
vessel.setAmbientTemperature(288.15);
vessel.setFixedVolumetricFlowRate(1783.4, "Sm3/day");
vessel.setCalculateExternalHTC(true);
vessel.run();

UUID id = UUID.randomUUID();
double dt = 10.0;
while (!vessel.isTargetPressureReached()) {
    vessel.runTransient(dt, id);
}

System.out.println("Fill time: " + vessel.getTimeHistory().get(
    vessel.getTimeHistory().size() - 1) / 3600.0 + " hours");
System.out.println("Max gas T: " + (vessel.getTemperature("C")) + " C");
System.out.println("Wall T: " + (vessel.getWallTemperature() - 273.15) + " C");
```

### Fire Case (API 521)

```java
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL);
vessel.setFireCase(true);
vessel.setFireHeatFlux(75.0, "kW/m2");     // pool fire
vessel.setWettedSurfaceFraction(0.5);
vessel.run();

UUID id = UUID.randomUUID();
double dt = 0.5;
double t = 0;
while (t < 1800) {
    vessel.runTransient(dt, id);
    t += dt;
    if (vessel.getPressure("bar") >= reliefSetPressure) {
        System.out.println("Relief opens at t = " + t + " s");
        break;
    }
}
```

---

## Python Examples

### CNG Tank Filling (Python with jneqsim)

```python
from neqsim import jneqsim
import jpype

# Import classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
VesselDepressurization = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization')

# Import inner enums via $ notation
VesselOrientation = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$VesselOrientation')
CalculationType = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$CalculationType')
HeatTransferType = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$HeatTransferType')
FlowDirection = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$FlowDirection')
UUID = jpype.JClass('java.util.UUID')

# Create gas at 15 C, 20 bar
gas = SystemSrkEos(273.15 + 15.0, 20.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.046)
gas.addComponent("propane", 0.018)
gas.addComponent("nitrogen", 0.02)
gas.addComponent("CO2", 0.054)
gas.setMixingRule("classic")
gas.setMultiPhaseCheck(True)
gas.init(0)
gas.init(1)

# Feed stream
feed = Stream("feed", gas)
feed.setTemperature(273.15 + 15.0, "K")
feed.setPressure(20.0, "bar")
feed.setFlowRate(100.0, "kg/hr")
feed.run()

# Configure vessel
vessel = VesselDepressurization("CNG Tank", feed)
vessel.setVesselGeometry(19.0, 0.999, VesselOrientation.VERTICAL)
vessel.setVesselProperties(0.0335, 7850.0, 480.0, 50.0)
vessel.setCalculationType(CalculationType.ENERGY_BALANCE)
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL)
vessel.setFlowDirection(FlowDirection.FILLING)
vessel.setTargetPressure(250.0)
vessel.setInletTemperature(273.15 + 15.0)
vessel.setAmbientTemperature(273.15 + 15.0)
vessel.setFixedVolumetricFlowRate(1783.4, "Sm3/day")
vessel.setCalculateExternalHTC(True)
vessel.run()

# Run transient
sim_id = UUID.randomUUID()
dt = 10.0
times, pressures, temps_gas, temps_wall = [], [], [], []
t = 0.0

while float(vessel.getPressure('bar')) < 250.0:
    P = float(vessel.getPressure('bar'))
    T_gas = float(vessel.getTemperature()) - 273.15
    T_wall = float(vessel.getWallTemperature()) - 273.15
    times.append(t / 3600.0)
    pressures.append(P)
    temps_gas.append(T_gas)
    temps_wall.append(T_wall)

    try:
        vessel.runTransient(dt, sim_id)
    except Exception as e:
        print(f"Warning at t={t/3600:.1f}hr: {e}")
        break
    t += dt

print(f"Fill time: {times[-1]:.1f} hours")
print(f"Max gas T: {max(temps_gas):.1f} C")
print(f"Max wall T: {max(temps_wall):.1f} C")
```

### Blowdown (Python with jneqsim)

```python
from neqsim import jneqsim
import jpype

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
VesselDepressurization = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization')
VesselOrientation = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$VesselOrientation')
CalculationType = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$CalculationType')
HeatTransferType = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$HeatTransferType')
FlowDirection = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$FlowDirection')
UUID = jpype.JClass('java.util.UUID')

# High-pressure gas
gas = SystemSrkEos(300.0, 150.0)
gas.addComponent("hydrogen", 1.0)
gas.setMixingRule("classic")

feed = Stream("feed", gas)
feed.setFlowRate(100.0, "kg/hr")
feed.run()

vessel = VesselDepressurization("H2 Tank", feed)
vessel.setVesselGeometry(1.5, 0.4, VesselOrientation.HORIZONTAL)
vessel.setVesselMaterial(0.020, jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$VesselMaterial').CARBON_STEEL)
vessel.setCalculationType(CalculationType.ENERGY_BALANCE)
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL)
vessel.setOrificeDiameter(0.010)
vessel.setBackPressure(1.0)
vessel.setAmbientTemperature(288.15)
vessel.setCalculateExternalHTC(True)
vessel.run()

sim_id = UUID.randomUUID()
dt = 0.5
t = 0.0
while float(vessel.getPressure('bar')) > 2.0 and t < 600:
    vessel.runTransient(dt, sim_id)
    t += dt

print(f"Blowdown time: {t:.1f} s")
print(f"Min gas T: {float(vessel.getMinimumTemperatureReached('C')):.1f} C")
```

### Important Python Notes

1. **Inner enums** must be accessed via `jpype.JClass('...Class$EnumName')` syntax, not dot notation
2. **Temperature** is in Kelvin by default; use `getTemperature("C")` or subtract 273.15
3. **Pressure** is in Pa by default; use `getPressure("bar")` for bar
4. **Always call** `gas.init(0)` and `gas.init(1)` after creating the gas system
5. **Always set** `feed.setTemperature()` and `feed.setPressure()` explicitly on the stream
6. **Always call** `vessel.setCalculateExternalHTC(True)` when using CALCULATED or TRANSIENT_WALL

---

## Related Documentation

- [CNG Tank Filling Notebook](../../../examples/CNGtankmodelling/CNG_FillingSimulation.ipynb) - Filling simulation example
- [CNG Tank Emptying Notebook](../../../examples/CNGtankmodelling/CNG_EmptyingSimulation.ipynb) - Depressurization example
- [CNG Gas Properties Notebook](../../../examples/CNGtankmodelling/CNG_GasProperties_HTC.ipynb) - Gas properties and HTC calculations
- [QRA Integration Guide](../../integration/QRA_INTEGRATION_GUIDE.md) - Safety analysis integration
- [Tank Equipment](tanks.md) - General tank modeling

---

*Package location: `neqsim.process.equipment.tank`*
*Heat transfer utilities: `neqsim.process.util.fire`*
