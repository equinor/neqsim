---
title: Multiphase Choke Flow Models in NeqSim
description: NeqSim provides comprehensive two-phase choke flow calculations through the `neqsim.process.mechanicaldesign.valve.choke` package. This module implements both mechanistic models (Sachdeva et al.) and ...
---

# Multiphase Choke Flow Models in NeqSim

## Overview

NeqSim provides comprehensive two-phase choke flow calculations through the `neqsim.process.mechanicaldesign.valve.choke` package. This module implements both mechanistic models (Sachdeva et al.) and empirical correlations (Gilbert-type) for calculating flow through production chokes.

## Key Features

- **Mechanistic Model**: Sachdeva et al. (1986) model for rigorous two-phase flow calculations
- **Empirical Correlations**: Gilbert (1954), Baxendell (1958), Ros (1960), and Achong (1961) correlations
- **Flow Regime Detection**: Automatic determination of critical vs. subcritical flow
- **Variable Discharge Coefficient**: Cd correlation with Reynolds number and void fraction
- **Unit Flexibility**: Support for SI and oilfield units (64ths of an inch, etc.)

## Available Models

### 1. Sachdeva Model (Mechanistic)

The industry-standard mechanistic model based on:
- Homogeneous two-phase flow assumption
- Polytropic gas expansion
- Critical pressure ratio correlation: $y_c = 0.5847 - 0.0227 \cdot \ln(x_g)$

**Best for**: Accurate predictions when fluid properties are well characterized.

```java
import neqsim.process.mechanicaldesign.valve.choke.SachdevaChokeFlow;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Create a two-phase fluid
SystemInterface fluid = new SystemSrkEos(300.0, 100.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("ethane", 0.1);
fluid.addComponent("n-heptane", 0.15);
fluid.addComponent("nC10", 0.05);
fluid.setMixingRule(2);
fluid.setMultiPhaseCheck(true);
fluid.init(0);
fluid.init(1);
fluid.initPhysicalProperties();

// Create Sachdeva model with 1/2 inch choke
SachdevaChokeFlow chokeModel = new SachdevaChokeFlow();
chokeModel.setChokeDiameter(0.5, "in");
chokeModel.setDischargeCoefficient(0.84);

// Calculate mass flow rate
double P1 = 100.0e5;  // 100 bar upstream
double P2 = 30.0e5;   // 30 bar downstream
double massFlow = chokeModel.calculateMassFlowRate(fluid, P1, P2);

// Get comprehensive sizing results
Map<String, Object> results = chokeModel.calculateSizingResults(fluid, P1, P2);
System.out.println("Mass flow: " + results.get("massFlowRate") + " kg/s");
System.out.println("Flow regime: " + results.get("flowRegime"));
System.out.println("Gas quality: " + results.get("gasQuality"));
```

### 2. Gilbert-Type Correlations (Empirical)

Empirical correlations for quick estimates, especially useful when limited fluid data is available.

**General form**: $q_L = \frac{P_1 \cdot d^a}{C \cdot GLR^b}$

Where:
- $q_L$ = liquid flow rate (STB/day)
- $P_1$ = upstream pressure (psig)
- $d$ = choke diameter (64ths of an inch)
- $GLR$ = gas-liquid ratio (scf/STB)

| Correlation | a | b | C |
|------------|---|---|---|
| Gilbert (1954) | 1.89 | 0.546 | 10.0 |
| Baxendell (1958) | 1.93 | 0.546 | 9.56 |
| Ros (1960) | 2.00 | 0.500 | 17.40 |
| Achong (1961) | 1.88 | 0.650 | 3.82 |

```java
import neqsim.process.mechanicaldesign.valve.choke.GilbertChokeFlow;

// Create Gilbert model
GilbertChokeFlow gilbertModel = new GilbertChokeFlow();
gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.GILBERT);
gilbertModel.setChokeDiameter(32, "64ths");  // 32/64" = 0.5"

// Calculate flow
double massFlow = gilbertModel.calculateMassFlowRate(fluid, P1, P2);

// Calculate required choke size for target flow
double liquidFlow = 0.001; // m3/s
double requiredDiameter = gilbertModel.calculateRequiredChokeDiameter(fluid, P1, liquidFlow);
```

### 3. Factory Pattern

Use the factory for easy model selection:

```java
import neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlowFactory;
import neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlow;

// Create model by type
MultiphaseChokeFlow model = MultiphaseChokeFlowFactory.createModel(
    MultiphaseChokeFlowFactory.ModelType.SACHDEVA);

// Create with choke diameter
MultiphaseChokeFlow model2 = MultiphaseChokeFlowFactory.createModel(
    MultiphaseChokeFlowFactory.ModelType.GILBERT, 0.5, "in");

// Get recommended model based on conditions
MultiphaseChokeFlow recommended = MultiphaseChokeFlowFactory.recommendModel(
    0.3,   // gas quality
    0.5,   // pressure ratio P2/P1
    1000   // GLR in scf/STB
);
```

## Flow Regime Determination

The models automatically detect whether flow is **critical** (choked) or **subcritical**:

```java
MultiphaseChokeFlow.FlowRegime regime = chokeModel.determineFlowRegime(fluid, P1, P2);

if (regime == MultiphaseChokeFlow.FlowRegime.CRITICAL) {
    // Flow is choked - mass flow independent of downstream pressure
    System.out.println("Critical flow: mass flow = " + massFlow + " kg/s");
} else {
    // Subcritical flow - mass flow depends on pressure difference
    System.out.println("Subcritical flow: mass flow = " + massFlow + " kg/s");
}
```

## Critical Pressure Ratio

For two-phase flow, the critical pressure ratio varies with gas quality:

| Gas Quality (x_g) | Critical Ratio (y_c) |
|-------------------|---------------------|
| 0.1 | ~0.64 |
| 0.3 | ~0.61 |
| 0.5 | ~0.60 |
| 0.7 | ~0.59 |
| 0.9 | ~0.58 |

## Discharge Coefficient

Default values:
- **Sachdeva**: Cd = 0.84
- **Gilbert**: Cd = 0.85 (implicit in correlation)

The Sachdeva model supports variable discharge coefficient:

```java
// Calculate Cd based on Reynolds number and void fraction
double Cd = chokeModel.calculateVariableDischargeCoefficient(
    50000,  // Reynolds number
    0.5     // void fraction
);
chokeModel.setDischargeCoefficient(Cd);
```

## Unit Conversions

Choke diameter can be set in multiple units:

```java
// SI units
chokeModel.setChokeDiameter(0.0127);  // meters (default)

// Inches
chokeModel.setChokeDiameter(0.5, "in");

// 64ths of an inch (oilfield standard)
chokeModel.setChokeDiameter(32, "64ths");  // 32/64" = 0.5"
```

## Python Usage

```python
from neqsim.process.mechanicaldesign.valve.choke import SachdevaChokeFlow, GilbertChokeFlow
from neqsim.thermo.system import SystemSrkEos

# Create fluid
fluid = SystemSrkEos(300.0, 100.0)
fluid.addComponent("methane", 0.7)
fluid.addComponent("ethane", 0.1)
fluid.addComponent("n-heptane", 0.15)
fluid.addComponent("nC10", 0.05)
fluid.setMixingRule(2)
fluid.setMultiPhaseCheck(True)
fluid.init(0)
fluid.init(1)
fluid.initPhysicalProperties()

# Calculate with Sachdeva model
choke = SachdevaChokeFlow()
choke.setChokeDiameter(0.5, "in")

P1 = 100.0e5  # Pa
P2 = 30.0e5   # Pa
mass_flow = choke.calculateMassFlowRate(fluid, P1, P2)
print(f"Mass flow: {mass_flow:.2f} kg/s")

# Get all results
results = choke.calculateSizingResults(fluid, P1, P2)
for key, value in results.items():
    print(f"{key}: {value}")
```

## References

1. Sachdeva, R., Schmidt, Z., Brill, J.P., and Blais, R.M. (1986). "Two-Phase Flow Through Chokes." SPE 15657.
2. Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance." API Drilling and Production Practice.
3. Baxendell, P.B. (1958). "Bean Performance - Lake Wells." Shell Internal Report.
4. Ros, N.C.J. (1960). "An Analysis of Critical Simultaneous Gas/Liquid Flow Through a Restriction and Its Application to Flow Metering." Applied Scientific Research.
5. Achong, I. (1961). "Revised Bean Performance Formula for Lake Maracaibo Wells." Shell Internal Report.

---

## Integration with ThrottlingValve

The multiphase choke models are fully integrated with the `ThrottlingValve` unit operation, allowing you to use production chokes in process simulations just like control valves.

### Setting Up a Production Choke

```java
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

// Create production choke
ThrottlingValve choke = new ThrottlingValve("Production Choke", wellStream);
choke.setOutletPressure(30.0, "bara");
choke.setPercentValveOpening(50.0);

// Configure multiphase choke model
ValveMechanicalDesign design = choke.getMechanicalDesign();
design.setValveSizingStandard("Sachdeva");  // or "Gilbert", "Baxendell", "Ros", "Achong"
design.setChokeDiameter(0.5, "in");
design.setChokeDischargeCoefficient(0.84);

// Run simulation
choke.run();
```

### Available Sizing Standards

| Standard | Model Type | Best For |
|----------|-----------|----------|
| `Sachdeva` | Mechanistic | When fluid composition is known |
| `Gilbert` | Empirical | Quick estimates, field data matching |
| `Baxendell` | Empirical | Higher flow rates than Gilbert |
| `Ros` | Empirical | Low GLR systems |
| `Achong` | Empirical | High GLR systems |
| `IEC_60534` | Single-phase | Control valves (gas or liquid) |

### Flow Calculation Modes

The valve supports two operation modes:

#### 1. Steady-State Mode (Default)
Outlet flow equals inlet flow. Use for process simulations where upstream equipment sets the flow.

```java
choke.run();  // Flow passes through unchanged
double outletFlow = choke.getOutletStream().getFlowRate("kg/hr");
// outletFlow == inletFlow
```

#### 2. Transient Mode (Flow Calculation)
The choke model calculates flow based on pressure drop and valve opening.

```java
choke.setCalculateSteadyState(false);  // Enable transient mode
choke.runTransient(0.1);  // Run with small timestep

// Flow is calculated from choke model
double calculatedFlow = choke.getOutletStream().getFlowRate("kg/hr");
```

### Calculate Required Valve Opening

Given a target flow rate and pressure conditions, find the required valve opening:

```java
// Get the sizing method
ControlValveSizing_MultiphaseChoke chokeMethod = 
    (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();

// Calculate required opening for target flow
double targetFlow_m3s = 0.5;  // m³/s volumetric flow
double requiredOpening = chokeMethod.calculateValveOpeningFromFlowRate(
    targetFlow_m3s, 0.0, inletStream, outletStream);

System.out.println("Required opening: " + requiredOpening + "%");
```

### Get Comprehensive Sizing Results

```java
import java.util.Map;

// Get all sizing results at 100% opening
Map<String, Object> results = design.getValveSizingMethod().calcValveSize(100.0);

System.out.println("Mass flow rate: " + results.get("massFlowRate") + " kg/s");
System.out.println("Flow regime: " + results.get("flowRegime"));
System.out.println("Gas quality: " + results.get("gasQuality"));
System.out.println("GLR: " + results.get("GLR") + " Sm³/Sm³");
System.out.println("Critical pressure ratio: " + results.get("criticalPressureRatio"));
System.out.println("Is choked: " + results.get("isChoked"));
System.out.println("Kv equivalent: " + results.get("Kv"));
```

### Choke Diameter Effect

Flow rate scales approximately with the square of choke diameter (proportional to area):

| Diameter (in) | Relative Flow |
|---------------|---------------|
| 0.25 | 1.0x |
| 0.50 | 4.0x |
| 0.75 | 9.0x |
| 1.00 | 16.0x |

### Python Usage with ThrottlingValve

```python
from neqsim.process.equipment.valve import ThrottlingValve
from neqsim.process.equipment.stream import Stream
from neqsim.thermo.system import SystemSrkEos

# Create two-phase well stream
fluid = SystemSrkEos(350.0, 100.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-heptane", 0.10)
fluid.addComponent("nC10", 0.05)
fluid.setMixingRule(2)
fluid.setMultiPhaseCheck(True)

well_stream = Stream("Well Stream", fluid)
well_stream.setFlowRate(10000.0, "kg/hr")
well_stream.run()

# Create production choke
choke = ThrottlingValve("Production Choke", well_stream)
choke.setOutletPressure(30.0, "bara")
choke.setPercentValveOpening(50.0)

# Configure Sachdeva model
design = choke.getMechanicalDesign()
design.setValveSizingStandard("Sachdeva")
design.setChokeDiameter(0.5, "in")

# Run in transient mode to calculate flow
choke.setCalculateSteadyState(False)
choke.runTransient(0.1)

# Get results
outlet_flow = choke.getOutletStream().getFlowRate("kg/hr")
print(f"Calculated flow: {outlet_flow:.1f} kg/hr")
```

---

## Model Validation

The models have been validated against literature data:

| Model | Validation Source | Error |
|-------|-------------------|-------|
| Sachdeva Critical Ratio | SPE 15657 (13 points) | 3.3% avg |
| Gilbert Correlation | Lake Maracaibo (20 points) | 0.0% avg |
| Flow Regime Detection | Fortunati (15 points) | 100% accuracy |

---

## See Also

- [Valve Sizing (IEC 60534)](./equipment/valves) - Single-phase control valve sizing
- [Valve Mechanical Design](./ValveMechanicalDesign) - Valve body and actuator sizing
- [Well & Choke Simulation](../simulation/well_and_choke_simulation) - Complete well simulation
