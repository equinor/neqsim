# PipeBeggsAndBrills - Multiphase Pipeline Simulation

## Overview

The `PipeBeggsAndBrills` class implements the Beggs and Brill (1973) empirical correlations for pressure drop and liquid holdup prediction in multiphase pipeline flow. It supports single-phase (gas or liquid) and multiphase (gas-liquid, gas-oil-water) flow in horizontal, inclined, and vertical pipes.

## Reference

> Beggs, H.D. and Brill, J.P., "A Study of Two-Phase Flow in Inclined Pipes", Journal of Petroleum Technology, May 1973, pp. 607-617. **SPE-4007-PA**

---

## Table of Contents

1. [Calculation Modes](#calculation-modes)
2. [Flow Regime Determination](#flow-regime-determination)
3. [Pressure Drop Calculation](#pressure-drop-calculation)
4. [Heat Transfer Models](#heat-transfer-models)
5. [Energy Equation Components](#energy-equation-components)
6. [Transient Simulation](#transient-simulation)
7. [Usage Examples](#usage-examples)
8. [Typical Parameter Values](#typical-parameter-values)
9. [API Reference](#api-reference)

---

## Calculation Modes

The pipeline supports two primary calculation modes:

| Mode | Description | Use Case |
|------|-------------|----------|
| `CALCULATE_OUTLET_PRESSURE` | Given inlet conditions and flow rate, calculate outlet pressure | Production pipelines with known flow |
| `CALCULATE_FLOW_RATE` | Given inlet and outlet pressures, calculate flow rate | Pipeline capacity analysis |

### Setting Calculation Mode

```java
// Default: Calculate outlet pressure from known flow
pipe.setCalculationMode(CalculationMode.CALCULATE_OUTLET_PRESSURE);

// Alternative: Calculate flow from known pressures
pipe.setCalculationMode(CalculationMode.CALCULATE_FLOW_RATE);
pipe.setSpecifiedOutletPressure(40.0, "bara");
pipe.setMaxFlowIterations(50);
pipe.setFlowConvergenceTolerance(1e-4);
```

---

## Flow Regime Determination

The Beggs and Brill correlation classifies two-phase flow into four regimes based on dimensionless parameters.

### Dimensionless Parameters

| Parameter | Symbol | Formula |
|-----------|--------|---------|
| Superficial liquid velocity | v_SL | Q_L / A |
| Superficial gas velocity | v_SG | Q_G / A |
| Mixture velocity | v_m | v_SL + v_SG |
| Input liquid fraction | λ_L | v_SL / v_m |
| Froude number | Fr | v_m² / (g × D) |

### Flow Regime Boundaries

The flow regime is determined by comparing the Froude number with boundary correlations L1-L4:

```
L1 = 316 × λL^0.302
L2 = 0.0009252 × λL^(-2.4684)
L3 = 0.1 × λL^(-1.4516)
L4 = 0.5 × λL^(-6.738)
```

### Flow Regime Classification

| Regime | Conditions | Description |
|--------|------------|-------------|
| **SEGREGATED** | λL < 0.01 and Fr < L1, or λL ≥ 0.01 and Fr < L2 | Stratified, wavy, or annular flow |
| **INTERMITTENT** | λL < 0.4 and L3 < Fr ≤ L1, or λL ≥ 0.4 and L3 < Fr ≤ L4 | Slug or plug flow |
| **DISTRIBUTED** | λL < 0.4 and Fr ≥ L4, or λL ≥ 0.4 and Fr > L4 | Bubble or mist flow |
| **TRANSITION** | L2 < Fr < L3 | Interpolation zone |
| **SINGLE_PHASE** | Only gas or only liquid | No two-phase effects |

### Flow Regime Map

```
                    Fr (Froude Number)
                    ↑
          1000 ─────┼─────────────────────────
                    │     DISTRIBUTED
                    │
           100 ─────┼───────────────┬─────────
                    │  INTERMITTENT │
            10 ─────┼───────────────┤
                    │   TRANSITION  │
             1 ─────┼───────────────┴─────────
                    │    SEGREGATED
           0.1 ─────┼─────────────────────────
                    └────┬────┬────┬────┬────→ λL
                       0.01  0.1  0.4  1.0
```

---

## Pressure Drop Calculation

Total pressure drop consists of three components:

```
ΔP_total = ΔP_friction + ΔP_hydrostatic + ΔP_acceleration
```

### Hydrostatic Pressure Drop

```
ΔP_hydrostatic = ρ_m × g × Δh

where:
  ρ_m = ρ_L × E_L + ρ_G × (1 - E_L)  [mixture density]
  E_L = liquid holdup (volume fraction)
  Δh = elevation change
```

### Liquid Holdup Correlations

| Flow Regime | Horizontal Holdup (E_L0) |
|-------------|--------------------------|
| Segregated | E_L0 = 0.98 × λL^0.4846 / Fr^0.0868 |
| Intermittent | E_L0 = 0.845 × λL^0.5351 / Fr^0.0173 |
| Distributed | E_L0 = 1.065 × λL^0.5824 / Fr^0.0609 |
| Transition | Weighted average of Segregated and Intermittent |

**Inclination Correction:**
```
E_L = E_L0 × B_θ

B_θ = 1 + β × [sin(1.8θ) - (1/3)sin³(1.8θ)]
```

where β depends on flow regime and inclination direction (uphill vs downhill).

### Friction Pressure Drop

```
ΔP_friction = f_tp × (L/D) × (ρ_ns × v_m²) / 2

where:
  f_tp = f × exp(S)     [two-phase friction factor]
  ρ_ns = no-slip density
  S = slip correction factor
```

**Friction Factor Correlations:**

| Flow Regime | Friction Factor |
|-------------|-----------------|
| Laminar (Re < 2300) | f = 64/Re |
| Transition (2300-4000) | Linear interpolation |
| Turbulent (Re > 4000) | Haaland: f = [1/(-1.8 log((ε/D/3.7)^1.11 + 6.9/Re))]² |

**Slip Correction Factor (S):**
```
y = λL / E_L²

For 1 < y < 1.2:  S = ln(2.2y - 1.2)
Otherwise:       S = ln(y) / [-0.0523 + 3.18ln(y) - 0.872ln²(y) + 0.01853ln⁴(y)]
```

---

## Heat Transfer Models

### Heat Transfer Modes

| Mode | Description | When to Use |
|------|-------------|-------------|
| `ADIABATIC` | No heat transfer (Q=0) | Well-insulated pipelines, short pipes |
| `ISOTHERMAL` | Constant temperature | Slow flow, thermal equilibrium |
| `SPECIFIED_U` | User-specified U-value | Known overall heat transfer coefficient |
| `ESTIMATED_INNER_H` | Calculate inner h from flow | Quick estimate, inner resistance dominant |
| `DETAILED_U` | Full thermal resistance calculation | Subsea pipelines, insulated pipes |

### NTU-Effectiveness Method

Heat transfer uses the analytical NTU (Number of Transfer Units) method:

```
NTU = U × A / (ṁ × Cp)

T_out = T_wall + (T_in - T_wall) × exp(-NTU)
```

This provides an exact solution for constant wall temperature boundary conditions.

### Inner Heat Transfer Coefficient

| Flow Regime | Nusselt Number |
|-------------|----------------|
| Laminar (Re < 2300) | Nu = 3.66 |
| Transition (2300-3000) | Linear interpolation |
| Turbulent (Re > 3000) | Gnielinski: Nu = (f/8)(Re-1000)Pr / [1 + 12.7(f/8)^0.5(Pr^(2/3)-1)] |
| Two-phase | Shah/Martinelli enhancement applied |

```
h_inner = Nu × k / D
```

### Overall U-Value (DETAILED_U Mode)

The overall heat transfer coefficient includes thermal resistances in series:

```
1/U = 1/h_inner + R_wall + R_insulation + 1/h_outer

where:
  R_wall = r_i × ln(r_o/r_i) / k_wall       [pipe wall]
  R_insulation = r_i × ln(r_ins/r_o) / k_ins [insulation layer]
```

**Example calculation for 6" pipe with 50mm insulation:**
```
Given:
  D_inner = 0.1524 m (6 inch)
  Wall thickness = 10 mm
  Insulation thickness = 50 mm
  k_steel = 45 W/(m·K)
  k_insulation = 0.04 W/(m·K)
  h_inner = 500 W/(m²·K)
  h_outer = 500 W/(m²·K) (seawater)

Calculate:
  r_i = 0.0762 m
  r_o = 0.0862 m  
  r_ins = 0.1362 m
  
  1/h_inner = 0.002 m²K/W
  R_wall = 0.0762 × ln(0.0862/0.0762) / 45 = 0.0002 m²K/W
  R_ins = 0.0762 × ln(0.1362/0.0862) / 0.04 = 0.87 m²K/W
  1/h_outer = 0.002 m²K/W
  
  1/U = 0.002 + 0.0002 + 0.87 + 0.002 = 0.874 m²K/W
  U = 1.14 W/(m²·K)
```

---

## Energy Equation Components

The energy balance can include three optional components:

### 1. Wall Heat Transfer

Heat exchange with surroundings using the NTU-effectiveness method:
```
Q_wall = ṁ × Cp × (T_out - T_in)
```

### 2. Joule-Thomson Effect

Temperature change due to gas expansion:
```
ΔT_JT = -μ_JT × ΔP

where μ_JT is the Joule-Thomson coefficient
```

**Typical Joule-Thomson Coefficients:**

| Fluid | μ_JT [K/bar] | μ_JT [K/Pa] |
|-------|--------------|-------------|
| Methane | ~0.4 | ~4×10⁻⁶ |
| Natural gas | 0.3-0.5 | 3-5×10⁻⁶ |
| CO₂ | ~1.0 | ~10⁻⁵ |
| Nitrogen | ~0.25 | ~2.5×10⁻⁶ |

### 3. Friction Heating

Viscous dissipation adds energy to the fluid:
```
Q_friction = ΔP_friction × V̇

where V̇ is the volumetric flow rate
```

**Note:** Only the friction component of pressure drop is used (not hydrostatic), as hydrostatic pressure change is reversible work.

### Enabling Energy Components

```java
// Enable Joule-Thomson effect
pipe.setIncludeJouleThomsonEffect(true);

// Enable friction heating
pipe.setIncludeFrictionHeating(true);
```

---

## Transient Simulation

The class supports time-dependent simulation using the `runTransient()` method.

### Conservation Equations

The transient solver uses explicit finite difference for:

1. **Mass conservation:**
   ```
   ∂ρ/∂t + ∂(ρv)/∂x = 0
   ```

2. **Momentum conservation:**
   ```
   ∂(ρv)/∂t + ∂(ρv²)/∂x = -∂P/∂x - τ_wall - ρg sin(θ)
   ```

3. **Energy conservation:**
   ```
   ∂(ρe)/∂t + ∂(ρvh)/∂x = Q_wall + Q_friction
   ```

### Usage

```java
// Initialize transient simulation
pipe.initTransientSimulation();

// Run time steps
double dt = 1.0;  // seconds
for (int step = 0; step < 1000; step++) {
    pipe.runTransient(dt);
    
    // Access profiles
    double outletT = pipe.getTransientTemperatureProfile().get(
        pipe.getTransientTemperatureProfile().size() - 1);
}
```

### Stability (CFL Condition)

For numerical stability, the time step must satisfy:
```
Δt ≤ Δx / (v + c)

where:
  Δx = segment length
  v = flow velocity
  c = speed of sound
```

---

## Usage Examples

### Example 1: Basic Horizontal Pipeline

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid system
SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Create inlet stream
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(50000, "kg/hr");
inlet.run();

// Create pipeline
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipeline", inlet);
pipe.setDiameter(0.2032);       // 8 inch
pipe.setLength(10000.0);        // 10 km
pipe.setElevation(0.0);         // horizontal
pipe.setNumberOfIncrements(20);
pipe.setPipeWallRoughness(4.5e-5); // Commercial steel

pipe.run();

// Results
System.out.println("Inlet pressure:  " + inlet.getPressure("bara") + " bara");
System.out.println("Outlet pressure: " + pipe.getOutletStream().getPressure("bara") + " bara");
System.out.println("Pressure drop:   " + pipe.getPressureDrop() + " bar");
System.out.println("Flow regime:     " + pipe.getFlowRegime());
System.out.println("Liquid holdup:   " + pipe.getLiquidHoldup());
```

### Example 2: Subsea Pipeline with Heat Loss

```java
// Hot production fluid in cold seawater
PipeBeggsAndBrills subseaPipe = new PipeBeggsAndBrills("subsea", hotStream);
subseaPipe.setDiameter(0.1524);                    // 6 inch
subseaPipe.setLength(15000.0);                     // 15 km
subseaPipe.setElevation(0.0);                      // horizontal

// Heat transfer settings
subseaPipe.setConstantSurfaceTemperature(4.0, "C"); // Deep sea temperature
subseaPipe.setHeatTransferCoefficient(15.0);        // W/(m²·K) with insulation

subseaPipe.run();

System.out.println("Inlet temperature:  " + hotStream.getTemperature("C") + " °C");
System.out.println("Outlet temperature: " + subseaPipe.getOutletStream().getTemperature("C") + " °C");
```

### Example 3: Detailed Heat Transfer with Insulation

```java
PipeBeggsAndBrills insulatedPipe = new PipeBeggsAndBrills("insulated", feedStream);
insulatedPipe.setDiameter(0.2032);                  // 8 inch
insulatedPipe.setThickness(0.0127);                 // 0.5 inch wall
insulatedPipe.setLength(20000.0);                   // 20 km

// Detailed thermal model
insulatedPipe.setConstantSurfaceTemperature(5.0, "C");
insulatedPipe.setOuterHeatTransferCoefficient(500.0);   // Seawater
insulatedPipe.setPipeWallThermalConductivity(45.0);     // Carbon steel
insulatedPipe.setInsulation(0.075, 0.04);               // 75mm PU foam
insulatedPipe.setHeatTransferMode(HeatTransferMode.DETAILED_U);

insulatedPipe.run();
```

### Example 4: Vertical Riser

```java
// Production riser from seabed to platform
PipeBeggsAndBrills riser = new PipeBeggsAndBrills("riser", subseaStream);
riser.setDiameter(0.1524);                          // 6 inch
riser.setLength(500.0);                             // 500 m length
riser.setElevation(500.0);                          // 500 m rise
riser.setAngle(90.0);                               // Vertical
riser.setNumberOfIncrements(50);

riser.run();

// Hydrostatic pressure difference
double hydrostaticHead = riser.getSegmentPressure(0) - 
                         riser.getSegmentPressure(riser.getNumberOfIncrements());
System.out.println("Hydrostatic head: " + hydrostaticHead + " bar");
```

### Example 5: Adiabatic Pipeline with JT Effect

```java
// High-pressure gas letdown - significant JT cooling expected
PipeBeggsAndBrills gasLine = new PipeBeggsAndBrills("gas_line", hpGasStream);
gasLine.setDiameter(0.1016);                        // 4 inch
gasLine.setLength(5000.0);                          // 5 km
gasLine.setElevation(0.0);

// Adiabatic with JT effect
gasLine.setHeatTransferMode(HeatTransferMode.ADIABATIC);
gasLine.setIncludeJouleThomsonEffect(true);

gasLine.run();

// For 20 bar pressure drop in natural gas:
// Expected JT cooling: ~8-10 K
double tempDrop = hpGasStream.getTemperature("C") - 
                  gasLine.getOutletStream().getTemperature("C");
System.out.println("Temperature drop from JT: " + tempDrop + " °C");
```

### Example 6: Flow Rate Calculation

```java
// Determine pipeline capacity for given inlet/outlet pressures
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("flowline", feedStream);
pipe.setDiameter(0.2032);
pipe.setLength(25000.0);
pipe.setElevation(-50.0);                           // Slight descent

// Calculate flow rate mode
pipe.setCalculationMode(CalculationMode.CALCULATE_FLOW_RATE);
pipe.setSpecifiedOutletPressure(35.0, "bara");
pipe.setMaxFlowIterations(100);
pipe.setFlowConvergenceTolerance(1e-5);

pipe.run();

System.out.println("Calculated flow rate: " + 
    pipe.getOutletStream().getFlowRate("kg/hr") + " kg/hr");
```

### Example 7: Multiphase Three-Phase Flow

```java
// Gas-oil-water flow
SystemInterface fluid = new SystemSrkEos(323.15, 50.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("n-heptane", 0.20);
fluid.addComponent("water", 0.10);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream threePhase = new Stream("three_phase", fluid);
threePhase.setFlowRate(100000, "kg/hr");
threePhase.run();

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("flowline", threePhase);
pipe.setDiameter(0.3048);                           // 12 inch
pipe.setLength(10000.0);
pipe.setElevation(0.0);
pipe.setNumberOfIncrements(50);

pipe.run();

// Check water accumulation
System.out.println("Average liquid holdup: " + pipe.getLiquidHoldup());
```

---

## Typical Parameter Values

### Heat Transfer Coefficients

| Environment | h [W/(m²·K)] |
|-------------|--------------|
| Still air (natural convection) | 5-25 |
| Forced air (5 m/s) | 25-50 |
| Forced air (30 m/s) | 100-250 |
| Still water | 100-500 |
| Flowing seawater (1 m/s) | 500-1000 |
| Buried in wet soil | 1-5 |
| Buried in dry soil | 0.5-2 |

### Thermal Conductivities

| Material | k [W/(m·K)] |
|----------|-------------|
| Carbon steel | 45-50 |
| Stainless steel | 15-20 |
| Copper | 380-400 |
| Mineral wool insulation | 0.03-0.05 |
| Polyurethane foam | 0.02-0.03 |
| Polypropylene | 0.1-0.2 |
| Concrete coating | 1.0-1.5 |

### Pipe Roughness

| Material | ε [mm] |
|----------|--------|
| Commercial steel | 0.045 |
| Stainless steel | 0.015 |
| Cast iron | 0.25 |
| Galvanized steel | 0.15 |
| PVC/Plastic | 0.0015 |
| Concrete | 0.3-3.0 |

---

## API Reference

### Key Methods

| Method | Description |
|--------|-------------|
| `setDiameter(double d)` | Set inner diameter [m] |
| `setLength(double L)` | Set pipe length [m] |
| `setElevation(double h)` | Set elevation change [m] |
| `setAngle(double θ)` | Set pipe inclination [degrees] |
| `setNumberOfIncrements(int n)` | Set number of calculation segments |
| `setPipeWallRoughness(double ε)` | Set wall roughness [m] |
| `setHeatTransferMode(HeatTransferMode mode)` | Set heat transfer calculation mode |
| `setHeatTransferCoefficient(double U)` | Set overall U-value [W/(m²·K)] |
| `setConstantSurfaceTemperature(double T, String unit)` | Set ambient/wall temperature |
| `setIncludeJouleThomsonEffect(boolean)` | Enable/disable JT cooling |
| `setIncludeFrictionHeating(boolean)` | Enable/disable friction heating |
| `run()` | Execute steady-state simulation |
| `runTransient(double dt)` | Execute one transient time step |

### Output Methods

| Method | Returns |
|--------|---------|
| `getPressureDrop()` | Total pressure drop [bar] |
| `getFlowRegime()` | Current flow regime |
| `getLiquidHoldup()` | Average liquid holdup [-] |
| `getOutletStream()` | Outlet stream with results |
| `getPressureProfile()` | List of pressures along pipe |
| `getTemperatureProfile()` | List of temperatures along pipe |
| `getLiquidHoldupProfile()` | List of holdups along pipe |

---

## Validation

The Beggs and Brill correlation has been validated against:
- Darcy-Weisbach equation for single-phase flow
- Analytical NTU solution for heat transfer
- Field data from inclined pipe studies

**Expected accuracy:**
- Single-phase pressure drop: ±5%
- Multiphase pressure drop: ±20-30%
- Liquid holdup: ±15%

---

## See Also

- [Pipeline](Pipeline.md) - Base pipeline class
- [TransientPipe](../twophasepipe/TransientPipe.md) - Mechanistic drift-flux model
- [PipeFlowSystem](../../fluidmechanics/PipeFlowSystem.md) - Detailed two-phase flow modeling
