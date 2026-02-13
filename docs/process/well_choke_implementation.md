---
title: "Well Choke Implementation in NeqSim"
description: "Comprehensive guide to well choke modeling in NeqSim covering architecture, multiphase flow models (Sachdeva, Gilbert, Baxendell, Ros, Achong), ThrottlingValve integration, flow regime detection, and Python usage."
---

# Well Choke Implementation in NeqSim

## Overview

NeqSim provides a complete framework for modeling production chokes (wellhead chokes / bean chokes) used to control flow from oil and gas wells. The implementation covers:

- **Mechanistic models** (Sachdeva et al.) for rigorous two-phase flow calculations
- **Empirical correlations** (Gilbert, Baxendell, Ros, Achong) for quick field estimates
- **Full integration** with the `ThrottlingValve` process equipment for use in flowsheet simulations
- **Critical and subcritical flow** regime detection
- **Safety scenarios** such as choke collapse with PSD protection

## Architecture

The choke implementation spans two layers: standalone choke flow calculators and ThrottlingValve integration.

### Package Structure

```
neqsim.process.mechanicaldesign.valve.choke/
  MultiphaseChokeFlow.java          # Abstract base class
  SachdevaChokeFlow.java            # Mechanistic model (SPE 15657)
  GilbertChokeFlow.java             # Empirical correlations (4 variants)
  MultiphaseChokeFlowFactory.java   # Factory for model creation

neqsim.process.mechanicaldesign.valve/
  ControlValveSizing_MultiphaseChoke.java  # Bridges choke models to ThrottlingValve

neqsim.process.equipment.valve/
  ThrottlingValve.java              # Process equipment (choke is a ThrottlingValve)
```

### Class Hierarchy

```
MultiphaseChokeFlow (abstract, Serializable)
├── SachdevaChokeFlow       # Mechanistic: handles critical + subcritical
└── GilbertChokeFlow        # Empirical: GILBERT, BAXENDELL, ROS, ACHONG variants

ControlValveSizingInterface
└── ControlValveSizing_MultiphaseChoke   # Adapts choke models into valve sizing API

ProcessEquipmentBaseClass
└── ThrottlingValve          # The process unit; delegates sizing to the above
```

## Available Models

### 1. Sachdeva Model (Mechanistic)

The industry-standard mechanistic model from SPE 15657. It solves both **critical** (choked) and **subcritical** flow regimes by computing mass flux through the choke throat from upstream thermodynamic conditions.

**Assumptions:**

- Homogeneous two-phase flow (no slip between phases)
- Polytropic gas expansion (default exponent = 1.2)
- Incompressible liquid phase
- Negligible friction in the choke
- Thermal equilibrium between phases

**Mass flow rate equation:**

$$
\dot{m} = C_d \, A_2 \, \sqrt{\frac{2 \, P_1 \, \rho_1}{\text{denom}}}
$$

where the denominator combines kinetic energy change, polytropic gas expansion work, and liquid work against the pressure gradient.

**Critical pressure ratio correlation:**

$$
y_c = 0.5847 - 0.0227 \, \ln(x_g)
$$

where $x_g$ is the gas mass fraction (quality). For pure gas the isentropic relation is used; for pure liquid a high ratio (~0.90) is returned.

**Default discharge coefficient:** $C_d = 0.84$

**Best for:** General two-phase flow when fluid composition is known. Handles subcritical flow which empirical correlations cannot.

### 2. Gilbert-Type Correlations (Empirical)

A family of empirical correlations developed from field data. All share the same functional form but differ in constants:

$$
q_L = \frac{P_{wh} \cdot d^a}{C \cdot \mathrm{GLR}^b}
$$

| Variable | Description | Unit |
|----------|-------------|------|
| $q_L$ | Liquid flow rate | STB/day |
| $P_{wh}$ | Upstream (wellhead) pressure | psig |
| $d$ | Choke diameter | 64ths of an inch |
| GLR | Gas-liquid ratio | scf/STB |

**Correlation constants:**

| Correlation | C | a | b | Year |
|------------|---|---|---|------|
| Gilbert | 10.0 | 1.89 | 0.546 | 1954 |
| Baxendell | 9.56 | 1.93 | 0.546 | 1958 |
| Ros | 17.4 | 2.00 | 0.500 | 1960 |
| Achong | 3.82 | 1.88 | 0.650 | 1961 |

**Best for:**

- **Gilbert / Baxendell:** General-purpose quick estimates, moderate GLR
- **Ros:** Low GLR (liquid-dominated) systems
- **Achong:** High GLR (gas-dominated) systems

**Limitation:** These assume **critical (choked) flow only** — downstream pressure does not appear in the equation.

### Model Selection Guide

| Condition | Recommended Model | Reason |
|-----------|-------------------|--------|
| Known fluid composition | Sachdeva | Mechanistic, covers all regimes |
| Subcritical flow possible | Sachdeva | Only model that handles subcritical |
| Quick field estimate | Gilbert | Simple, widely used |
| High GLR ($>$ 5000 Sm$^3$/Sm$^3$) | Achong | Tuned for gas-dominated flow |
| Low GLR ($<$ 100 Sm$^3$/Sm$^3$) | Gilbert or Ros | Good for liquid-rich conditions |
| Limited fluid data | Gilbert family | Needs only GLR and pressure |

## Flow Regime Detection

All models automatically distinguish between **critical** and **subcritical** flow:

- **Critical (choked):** When $P_2/P_1 \le y_c$. The mass flow rate is independent of downstream pressure — further reduction in $P_2$ does not increase flow.
- **Subcritical:** When $P_2/P_1 > y_c$. Both pressures affect flow rate.

Typical critical pressure ratios for two-phase flow:

| Gas Quality ($x_g$) | Critical Ratio ($y_c$) |
|---------------------|----------------------|
| 0.1 | ~0.64 |
| 0.3 | ~0.61 |
| 0.5 | ~0.60 |
| 0.7 | ~0.59 |
| 0.9 | ~0.58 |

## Discharge Coefficient

The default values are:

- **Sachdeva:** $C_d = 0.84$ (adjustable)
- **Gilbert family:** $C_d$ is implicit in the correlation constant

The Sachdeva model supports a **variable discharge coefficient** that accounts for Reynolds number and void fraction:

```java
double Cd = chokeModel.calculateVariableDischargeCoefficient(
    50000,  // Reynolds number
    0.5     // void fraction
);
chokeModel.setDischargeCoefficient(Cd);
```

## Choke Diameter Units

The choke diameter can be specified in multiple unit systems:

```java
chokeModel.setChokeDiameter(0.0127);          // meters (default)
chokeModel.setChokeDiameter(12.7, "mm");       // millimeters
chokeModel.setChokeDiameter(0.5, "in");        // inches
chokeModel.setChokeDiameter(32, "64ths");      // 64ths of an inch (oilfield standard: 32/64" = 0.5")
```

## Java Usage

### Standalone Choke Flow Calculation

```java
import neqsim.process.mechanicaldesign.valve.choke.SachdevaChokeFlow;
import neqsim.process.mechanicaldesign.valve.choke.GilbertChokeFlow;
import neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlowFactory;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create a two-phase fluid
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

// 2. Create Sachdeva model with 1/2 inch choke
SachdevaChokeFlow chokeModel = new SachdevaChokeFlow();
chokeModel.setChokeDiameter(0.5, "in");
chokeModel.setDischargeCoefficient(0.84);

// 3. Calculate mass flow rate
double P1 = 100.0e5;  // 100 bar upstream (Pa)
double P2 = 30.0e5;   // 30 bar downstream (Pa)
double massFlow = chokeModel.calculateMassFlowRate(fluid, P1, P2);
System.out.println("Mass flow: " + massFlow + " kg/s");

// 4. Determine flow regime
MultiphaseChokeFlow.FlowRegime regime =
    chokeModel.determineFlowRegime(fluid, P1, P2);
System.out.println("Flow regime: " + regime);

// 5. Get all sizing results as a Map
Map<String, Object> results = chokeModel.calculateSizingResults(fluid, P1, P2);
```

### Using the Factory

```java
// Create by enum
MultiphaseChokeFlow model = MultiphaseChokeFlowFactory.createModel(
    MultiphaseChokeFlowFactory.ModelType.SACHDEVA);

// Create by name string
MultiphaseChokeFlow model2 = MultiphaseChokeFlowFactory.createModel("gilbert");

// Get recommended model for conditions
MultiphaseChokeFlowFactory.ModelType recommended =
    MultiphaseChokeFlowFactory.recommendModel(
        300.0,   // GLR Sm3/Sm3
        true     // is critical flow expected
    );
```

### Using Gilbert Correlation Variants

```java
GilbertChokeFlow gilbertModel = new GilbertChokeFlow();
gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ACHONG);
gilbertModel.setChokeDiameter(32, "64ths");  // 32/64" = 0.5"

double massFlow = gilbertModel.calculateMassFlowRate(fluid, P1, P2);

// Calculate required choke size for a target liquid flow
double requiredDiameter = gilbertModel.calculateRequiredChokeDiameter(
    fluid, P1, 0.001);  // 0.001 m3/s target liquid flow
```

### Calculating Downstream Pressure

```java
// Given upstream pressure and desired mass flow, find P2
double P2_calculated = chokeModel.calculateDownstreamPressure(
    fluid, 100.0e5, 2.5);  // P1=100 bar, target 2.5 kg/s
```

## ThrottlingValve Integration

Production chokes are modeled as `ThrottlingValve` instances in process flowsheets. The multiphase choke models plug in through `ValveMechanicalDesign`.

### Setting Up a Production Choke in a Process

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create two-phase well stream
SystemInterface wellFluid = new SystemSrkEos(350.0, 100.0);
wellFluid.addComponent("methane", 0.70);
wellFluid.addComponent("ethane", 0.10);
wellFluid.addComponent("propane", 0.05);
wellFluid.addComponent("n-heptane", 0.10);
wellFluid.addComponent("nC10", 0.05);
wellFluid.setMixingRule(2);
wellFluid.setMultiPhaseCheck(true);

Stream wellStream = new Stream("Well Stream", wellFluid);
wellStream.setFlowRate(10000.0, "kg/hr");

// Create production choke
ThrottlingValve choke = new ThrottlingValve("Production Choke", wellStream);
choke.setOutletPressure(30.0, "bara");
choke.setPercentValveOpening(50.0);

// Configure multiphase choke model via mechanical design
ValveMechanicalDesign design = choke.getMechanicalDesign();
design.setValveSizingStandard("Sachdeva");  // or "Gilbert", "Baxendell", "Ros", "Achong"
design.setChokeDiameter(0.5, "in");
design.setChokeDischargeCoefficient(0.84);

// Build process and run
ProcessSystem process = new ProcessSystem();
process.add(wellStream);
process.add(choke);
process.run();
```

### Steady-State vs Transient Mode

| Mode | Behavior | Use Case |
|------|----------|----------|
| Steady-state (default) | Outlet flow = inlet flow; pressure drop applied | Process flowsheet simulations |
| Transient | Choke model calculates flow from pressures and opening | Dynamic simulations, rate prediction |

```java
// Transient mode: choke model calculates flow
choke.setCalculateSteadyState(false);
choke.runTransient(0.1);  // timestep in seconds
double calculatedFlow = choke.getOutletStream().getFlowRate("kg/hr");
```

### Valve Opening and Effective Diameter

The effective choke diameter scales with valve opening:

$$
d_{\text{eff}} = d_{\text{nom}} \cdot \sqrt{\frac{\text{opening\%}}{100}}
$$

This means flow scales roughly linearly with opening percentage (since flow $\propto A \propto d^2$):

| Opening (%) | Relative Flow |
|-------------|---------------|
| 25 | ~25% |
| 50 | ~50% |
| 75 | ~75% |
| 100 | 100% |

### Comprehensive Sizing Report

```java
ControlValveSizing_MultiphaseChoke chokeMethod =
    (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();

// Get sizing results at current opening
Map<String, Object> results = chokeMethod.calcValveSize(50.0);
// Keys: massFlowRate, flowRegime, gasQuality, GLR, criticalPressureRatio,
//       isChoked, Kv, modelType, nominalChokeDiameter, effectiveChokeDiameter

// Or get a formatted text report
String report = chokeMethod.getChokeReport(
    choke.getInletStream(), 30.0);  // outlet pressure in bara
System.out.println(report);
```

### Finding Required Valve Opening

```java
// Target volumetric flow rate (m3/s)
double targetQ = 0.5;

double requiredOpening = chokeMethod.calculateValveOpeningFromFlowRate(
    targetQ, 0.0, choke.getInletStream(), choke.getOutletStream());
System.out.println("Required opening: " + requiredOpening + "%");
```

## Python Usage

```python
from neqsim import jneqsim

# Import Java classes through jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
SachdevaChokeFlow = jneqsim.process.mechanicaldesign.valve.choke.SachdevaChokeFlow

# Create two-phase well fluid (T in Kelvin, P in bara)
fluid = SystemSrkEos(273.15 + 77.0, 100.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-heptane", 0.10)
fluid.addComponent("nC10", 0.05)
fluid.setMixingRule(2)
fluid.setMultiPhaseCheck(True)
fluid.init(0)
fluid.init(1)
fluid.initPhysicalProperties()

# --- Standalone choke calculation ---
choke_model = SachdevaChokeFlow()
choke_model.setChokeDiameter(0.5, "in")
choke_model.setDischargeCoefficient(0.84)

P1 = 100.0e5  # Pa
P2 = 30.0e5   # Pa
mass_flow = choke_model.calculateMassFlowRate(fluid, P1, P2)
print(f"Mass flow: {mass_flow:.3f} kg/s")

results = choke_model.calculateSizingResults(fluid, P1, P2)
print(f"Flow regime: {results.get('flowRegime')}")
print(f"Gas quality: {results.get('gasQuality'):.3f}")

# --- Process simulation with choke ---
well_stream = Stream("Well Stream", fluid)
well_stream.setFlowRate(10000.0, "kg/hr")

choke = ThrottlingValve("Production Choke", well_stream)
choke.setOutletPressure(30.0, "bara")
choke.setPercentValveOpening(50.0)

process = ProcessSystem()
process.add(well_stream)
process.add(choke)
process.run()

print(f"Outlet T: {choke.getOutletStream().getTemperature() - 273.15:.1f} C")
print(f"Outlet P: {choke.getOutletStream().getPressure():.1f} bara")
```

## Safety Scenarios: Choke Collapse

A critical safety scenario is **choke collapse** — the choke valve fails open to 100%, causing rapid pressure rise in downstream equipment. NeqSim supports modeling this scenario with transient simulation, pressure transmitters, alarm systems, and PSD (Process Shutdown) valves.

```java
// Normal operation: choke at 30% controlling to ~50 bara
ThrottlingValve chokeValve = new ThrottlingValve("Inlet Choke", feedStream);
chokeValve.setPercentValveOpening(30.0);
chokeValve.setOutletPressure(50.0);

// PSD valve for protection
PSDValve psdValve = new PSDValve("PSD Protection", chokeValve.getOutletStream());
psdValve.setClosureTime(2.0);  // 2 second closure

// Pressure transmitter with HIHI alarm
PressureTransmitter pt = new PressureTransmitter("Sep Inlet PT", chokeOutlet);
AlarmConfig alarms = AlarmConfig.builder()
    .highHighLimit(55.0)    // Trip setpoint
    .highLimit(52.0)        // Early warning
    .deadband(0.5)
    .delay(1.0)
    .unit("bara")
    .build();
pt.setAlarmConfig(alarms);
psdValve.linkToPressureTransmitter(pt);

// Simulate failure: choke fails open
chokeValve.setPercentValveOpening(100.0);
// Run transient to observe PSD response ...
```

For the complete choke collapse scenario with test results and recovery procedures, see [Choke Collapse PSD Protection](../wiki/choke_collapse_psd_protection).

## Validation

The choke models have been validated against published data:

| Model | Validation Source | Average Error |
|-------|-------------------|---------------|
| Sachdeva critical ratio | SPE 15657 (13 data points) | 3.3% |
| Gilbert correlation | Lake Maracaibo field data (20 points) | 0.0% |
| Flow regime detection | Fortunati data (15 points) | 100% accuracy |

Test classes:

- `SachdevaChokeFlowTest` — Unit tests for Sachdeva model
- `SachdevaChokeFlowValidationTest` — Validation against SPE paper data
- `GilbertChokeFlowTest` — Unit tests for Gilbert family
- `MultiphaseChokeFlowFactoryTest` — Factory pattern tests
- `MultiphaseChokeFlowValidationTest` — Cross-model validation
- `ThrottlingValveMultiphaseChokeTest` — Integration with ThrottlingValve
- `ChokeCollapsePSDProtectionTest` — Safety scenario test

Run all choke tests:

```bash
mvn test -Dtest="SachdevaChokeFlowTest,GilbertChokeFlowTest,MultiphaseChokeFlowFactoryTest,MultiphaseChokeFlowValidationTest,ThrottlingValveMultiphaseChokeTest"
```

## References

1. Sachdeva, R., Schmidt, Z., Brill, J.P., and Blais, R.M. (1986). "Two-Phase Flow Through Chokes." SPE 15657.
2. Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance." API Drilling and Production Practice.
3. Baxendell, P.B. (1958). "Bean Performance — Lake Wells." Shell Internal Report.
4. Ros, N.C.J. (1960). "An Analysis of Critical Simultaneous Gas/Liquid Flow Through a Restriction and Its Application to Flow Metering." Applied Scientific Research.
5. Achong, I. (1961). "Revised Bean Performance Formula for Lake Maracaibo Wells." Shell Internal Report.
6. Perkins, T.K. (1990). "Critical and Subcritical Flow of Multiphase Mixtures Through Chokes." SPE 20633.

## Related Documentation

- [Multiphase Choke Flow Models](MultiphaseChokeFlow) — Detailed API reference for the choke calculation classes
- [Well and Choke Simulation](../simulation/well_and_choke_simulation) — Network-level well and choke simulation
- [Choke Collapse PSD Protection](../wiki/choke_collapse_psd_protection) — Safety scenario with PSD valve trip
- [Valve Mechanical Design](ValveMechanicalDesign) — IEC 60534 single-phase valve sizing
