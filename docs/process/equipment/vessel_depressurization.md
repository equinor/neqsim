---
title: "Vessel Depressurization and Filling"
description: "Dynamic modeling of pressure vessel filling, depressurization, and blowdown using VesselDepressurization. Covers thermodynamic modes, heat transfer models (real-gas beta, momentum-based mixed convection, Biot correction, Rohsenow boiling), fire cases, composite vessels, flow assurance, CNG tank scenarios, and Python/Java API reference."
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
- [Heat Transfer Model Improvements (2025-2026)](#heat-transfer-model-improvements-20252026)
- [Bug Fixes (2025)](#bug-fixes-2025)
- [Java Examples](#java-examples)
- [Python Examples](#python-examples)
- [Related Documentation](#related-documentation)

---

## Overview

The `VesselDepressurization` class models dynamic filling and depressurization of pressure vessels. It supports:

1. **Multiple thermodynamic processes**: isothermal, isenthalpic, isentropic, isenergetic, and full energy balance
2. **Heat transfer models**: adiabatic, fixed U-value, fixed Q, calculated natural/mixed convection (momentum-based with real-gas beta and Biot correction), and transient 1-D wall conduction
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
| DISCHARGE | Momentum-based mixed convection | Circulation velocity from orifice momentum (area-ratio scaling), Gnielinski Nu with vessel diameter as length scale, blended asymptotically with Churchill-Chu natural convection |
| FILLING | Momentum-based mixed convection (1.5x enhancement) | Same as discharge but with 1.5x filling enhancement factor on circulation velocity to account for the stronger mixing from incoming jet |
| Natural convection | Churchill-Chu | Vertical surface or horizontal cylinder correlation, with real-gas thermal expansion coefficient |
| Liquid (wetted wall) | Rohsenow + natural convection | Nucleate boiling via Rohsenow correlation when wall superheat exists, otherwise natural convection |

The `VesselHeatTransferCalculator` class (in `neqsim.process.util.fire`) implements these correlations.

#### Momentum-Based Forced Convection Model

Both filling and discharge use the same physical approach to estimate forced convection:

1. Compute orifice exit velocity: $v_{orifice} = \dot{m} / (\rho \cdot A_{orifice})$
2. Estimate bulk circulation velocity from area ratio: $v_{circ} = v_{orifice} \cdot A_{orifice} / A_{vessel}$
3. For filling, apply 1.5x enhancement: $v_{circ,fill} = 1.5 \cdot v_{circ}$
4. Compute Re based on vessel diameter: $Re = \rho \cdot v_{circ} \cdot D_{vessel} / \mu$
5. Evaluate Gnielinski correlation for Nu
6. Convert: $h_{forced} = Nu \cdot k / D_{vessel}$
7. Blend with natural convection: $h_{mixed} = (h_{forced}^3 + h_{natural}^3)^{1/3}$

Using the vessel diameter as the length scale (rather than the orifice diameter) prevents non-physical HTC spikes at low pressure when orifice velocities are large.

#### Real-Gas Thermal Expansion Coefficient

Natural convection uses a real-gas thermal expansion coefficient computed from the equation of state:

$$\beta = -\frac{1}{\rho} \left(\frac{\partial \rho}{\partial T}\right)_P$$

This can differ significantly from the ideal-gas approximation $\beta = 1/T$ at high pressures. Falls back to $1/T$ if the EoS derivative is unavailable.

#### Biot-Number Wall Correction

The lumped wall model applies a first-order Biot correction to account for the temperature gradient through the wall:

$$h_{eff} = \frac{1}{\frac{1}{h_{int}} + \frac{t_{wall}}{2 k_{wall}}}$$

For composite vessels with liner:

$$h_{eff} = \frac{1}{\frac{1}{h_{int}} + \frac{t_{liner}}{k_{liner}} + \frac{t_{wall}}{2 k_{wall}}}$$

This ensures that walls with low thermal conductivity (e.g., CFRP composites) correctly limit the heat transfer rate.

#### Nucleate Boiling (Wetted Wall)

When the wall temperature exceeds the saturation temperature in two-phase systems, the Rohsenow correlation estimates nucleate boiling heat flux:

$$q = \mu_l h_{fg} \sqrt{\frac{g (\rho_l - \rho_v)}{\sigma}} \left[\frac{C_{p,l} \Delta T_{sat}}{C_{sf} h_{fg} Pr^n}\right]^3$$

Default surface–fluid constants: $C_{sf} = 0.013$, $n = 1.7$ (water-steel). Falls back to simplified estimate $h = 1000 \Delta T^{0.3}$ if Rohsenow gives unreasonably low values. Capped at 5000 W/(m²·K).

#### Impinging Jet Correlation (Available)

The Martin correlation for axisymmetric impinging jets (VDI Heat Atlas) is available in `VesselHeatTransferCalculator.calculateNusseltImpingingJet()` for specialized analysis:

$$Nu = F(Re) \cdot K(H/D, D/r) \cdot Pr^{0.42}$$

Valid for $2000 < Re < 400{,}000$, $2 < H/D < 12$, $2.5 < r/D < 7.5$. Not used in the default simulation path (the momentum-based model is preferred for stability).

#### HTC Safety Cap

Gas-phase HTC is capped at 500 W/(m²·K) to prevent numerical instability from extreme flow conditions (e.g., very low pressure with high orifice velocity).

### External HTC

Auto-calculated external HTC uses Churchill-Chu for natural convection in still air with:
- Air properties from ideal gas law and Sutherland's viscosity correlation
- Clamped to range 2-50 W/(m2*K)

---

## Fire Case Modeling

### Legacy Constant Flux (API 521)

Constant fire heat flux applied directly to the gas energy balance. Simple but may overpredict
gas temperature because heat bypasses the vessel wall.

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

### Stefan-Boltzmann Fire Model

Physically correct fire model that applies fire heat to the **outer wall surface**
rather than directly to the gas. The wall then conducts heat inward to the gas
through the normal heat transfer path (1-D conduction or lumped model).

The net fire heat flux at the outer wall surface is computed as:

$$
q_f = \alpha_s \cdot \varepsilon_f \cdot \sigma \cdot T_f^4
    + h_f \cdot (T_f - T_s)
    - \varepsilon_s \cdot \sigma \cdot T_s^4
$$

where $\alpha_s$ is surface absorptivity, $\varepsilon_f$ is flame emissivity,
$\varepsilon_s$ is surface emissivity, $h_f$ is fire convection coefficient,
$T_f$ is flame temperature, and $T_s$ is the outer wall temperature.

As the wall heats up, the re-radiation term ($\varepsilon_s \sigma T_s^4$) increases
and the net heat input naturally decreases — this is physically correct and matches
HydDown/Unisim behavior.

#### Preset Fire Types

Use a preset fire type for standard Scandpower or API fire scenarios:

```java
// Scandpower jet fire (100 kW/m2 incident)
vessel.setFireType(FireType.SCANDPOWER_JET);

// Scandpower pool fire (100 kW/m2 incident, lower convection)
vessel.setFireType(FireType.SCANDPOWER_POOL);

// API 521 jet fire
vessel.setFireType(FireType.API_JET);

// API 521 pool fire (43.2 kW/m2 incident)
vessel.setFireType(FireType.API_POOL);

vessel.setWettedSurfaceFraction(1.0);  // fraction of surface exposed to fire
```

Preset parameters:

| Fire Type | $\alpha_s$ | $\varepsilon_f$ | $\varepsilon_s$ | $h_f$ [W/(m2K)] | Incident flux [kW/m2] |
|-----------|:---:|:---:|:---:|:---:|:---:|
| SCANDPOWER_JET  | 0.85 | 1.0  | 0.85 | 100 | 100 |
| SCANDPOWER_POOL | 0.85 | 1.0  | 0.85 | 30  | 100 |
| API_JET         | 0.75 | 0.33 | 0.75 | 40  | 100 |
| API_POOL        | 0.75 | 0.3  | 0.75 | 15  | 43.2 |

#### Custom Parameters

```java
// Set model type explicitly
vessel.setFireModelType(FireModelType.STEFAN_BOLTZMANN);

// Custom S-B parameters: absorptivity, flame emissivity, surface emissivity, h_conv
vessel.setSBFireParameters(0.85, 1.0, 0.85, 100.0);

// Set incident heat flux (flame temperature is back-calculated)
vessel.setIncidentHeatFlux(100.0, "kW/m2");

// Or set flame temperature directly
vessel.setFlameTemperature(933.0);  // K

vessel.setWettedSurfaceFraction(1.0);
```

#### Python Example (S-B Fire Blowdown)

```python
from neqsim import jneqsim
import jpype

VesselDepressurization = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization')
FireType = jpype.JClass(
    'neqsim.process.equipment.tank.VesselDepressurization$FireType')
# ... other imports ...

vessel = VesselDepressurization("Fire case", feed)
vessel.setVesselGeometry(9.0, 3.0, VesselOrientation.VERTICAL)
vessel.setVesselProperties(0.136, 7700.0, 500.0, 50.0)
vessel.setCalculationType(CalculationType.ENERGY_BALANCE)
vessel.setHeatTransferType(HeatTransferType.TRANSIENT_WALL)
vessel.setFlowDirection(FlowDirection.DISCHARGE)

# Use Scandpower jet fire preset
vessel.setFireType(FireType.SCANDPOWER_JET)
vessel.setWettedSurfaceFraction(1.0)
vessel.run()

# Run blowdown
sim_id = UUID.randomUUID()
for i in range(900):
    vessel.runTransient(1.0, sim_id)
    T_gas = float(vessel.getTemperature()) - 273.15
    T_wall = float(vessel.getWallTemperature()) - 273.15
```

#### Comparison: Constant Flux vs Stefan-Boltzmann

For a 9 m x 3 m steel vessel (136 mm wall), methane at 115 bar, 25 degC,
100 kW/m2 jet fire, 15-minute blowdown:

| Metric | Constant flux (to gas) | S-B Scandpower jet (to wall) |
|--------|:---:|:---:|
| Final pressure [bar] | 115 | 61 |
| Max gas temp [degC] | 451 (unrealistic) | 47 |
| Max wall temp [degC] | 149 | 110 |

The constant flux model overpredicts gas temperature because fire heat bypasses
the wall thermal resistance. The S-B model produces physically realistic results
consistent with HydDown and Unisim.

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

## Heat Transfer Model Improvements (2025–2026)

Five improvements were made to the internal heat transfer model based on validation against published experimental data (CNG filling, H₂ filling, air charging/discharging):

### 1. Real-Gas Thermal Expansion Coefficient

**Before:** Ideal-gas approximation $\beta = 1/T$ used for all Grashof number calculations.

**After:** Uses EoS derivative $\beta = -(1/\rho)(\partial\rho/\partial T)_P$ via `phase.getdrhodT()`. Falls back to $1/T$ only when the derivative is unavailable or non-physical.

**Impact:** Improves natural convection accuracy at high pressures where $\beta$ can differ significantly from $1/T$.

### 2. Momentum-Based Forced Convection

**Before:** Filling used inlet orifice diameter as the length scale for both Re and the Nu-to-h conversion. At low pressure with small orifices, this produced extreme HTC values (>10,000 W/m²K) causing numerical instability.

**After:** Both filling and discharge estimate a bulk circulation velocity from orifice momentum (area-ratio scaling) and use the vessel diameter as the length scale. Filling applies a 1.5x enhancement factor. This approach is physically consistent — the entire gas volume circulates, not just the jet core.

**Impact:** Eliminates oscillations in CNG filling simulation. Case 1 gas temperature RMS error improved from 40.4°C to 16.0°C.

### 3. Biot-Number Wall Correction

**Before:** Lumped wall model equated inner surface temperature with mean wall temperature for the internal heat flux calculation.

**After:** Applies Biot correction: $h_{eff} = 1/(1/h_{int} + t_{wall}/(2k_{wall}))$. For composite vessels, includes liner resistance.

**Impact:** More accurate wall temperature predictions for thick walls and low-conductivity composites (CFRP, fiberglass).

### 4. Rohsenow Nucleate Boiling

**Before:** Wetted wall boiling used ad-hoc formula $h = 1000 \cdot \Delta T^{0.3}$.

**After:** Uses Rohsenow correlation for nucleate pool boiling with fallback to simplified estimate when Rohsenow gives unreasonably low values. Cap raised to 5000 W/(m²·K).

**Impact:** Physics-based boiling HTC for two-phase scenarios.

### 5. HTC Safety Cap

Gas-phase HTC is capped at 500 W/(m²·K) to prevent energy balance overcorrection from extreme transient conditions.

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
- [HTC Literature Comparison](../../../examples/CNGtankmodelling/CNG_HTC_Literature_Comparison.ipynb) - Validation against published experimental data
- [QRA Integration Guide](../../integration/QRA_INTEGRATION_GUIDE.md) - Safety analysis integration
- [Fire Heat Transfer](../../safety/fire_heat_transfer_enhancements.md) - Fire exposure and blowdown enhancements
- [Tank Equipment](tanks.md) - General tank modeling

---

*Package location: `neqsim.process.equipment.tank`*
*Heat transfer utilities: `neqsim.process.util.fire`*
