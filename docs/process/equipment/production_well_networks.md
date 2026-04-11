---
title: "Production Well Networks"
description: "Modeling production well networks with IPR, chokes, tubing VLP, and multiphase pipe flow using LoopedPipeNetwork. Covers Newton-Raphson Global Gradient Algorithm with generalized resistance elements for oil and gas gathering systems."
---

# Production Well Networks

Model complete production well networks — from reservoir sandface through tubing,
chokes, flowlines, and manifolds to delivery — using the `LoopedPipeNetwork`
Newton-Raphson Global Gradient Algorithm (NR-GGA) solver.

## Table of Contents

- [Overview](#overview)
- [Network Element Types](#network-element-types)
- [IPR Models](#ipr-models)
- [Choke Model](#choke-model)
- [Tubing VLP Model](#tubing-vlp-model)
- [Multiphase Pipe Flow](#multiphase-pipe-flow)
- [Building a Production Network](#building-a-production-network)
- [Solver Details](#solver-details)
- [Usage Examples](#usage-examples)
- [Results and Inspection](#results-and-inspection)
- [Artificial Lift](#artificial-lift)
- [Water Handling](#water-handling)
- [Sand and Solids Tracking](#sand-and-solids-tracking)
- [Corrosion and Integrity](#corrosion-and-integrity)
- [GHG Emissions Tracking](#ghg-emissions-tracking)
- [Large-Scale Networks](#large-scale-networks)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.network`

The `LoopedPipeNetwork` class extends the basic pipeline network solver with
production-specific resistance elements. Instead of modeling every element as a
pipe segment, you can now represent each component of the production system with
its own physics:

| Element | Physics | Typical Use |
|---------|---------|-------------|
| **PIPE** | Darcy-Weisbach friction | Flowlines, export lines, risers |
| **WELL_IPR** | Inflow Performance Relationship | Reservoir-to-wellbore inflow |
| **CHOKE** | Valve flow equation (IEC 60534-style) | Wellhead/surface chokes |
| **TUBING** | Gravity + friction (VLP) | Vertical tubing strings |
| **MULTIPHASE_PIPE** | Segmented multiphase correlations | Subsea flowlines |

All element types participate in the same Newton-Raphson global gradient
solver. The Schur complement reduction handles any mix of elements in a single
network.

---

## Network Element Types

### Enum: `NetworkElementType`

```java
public enum NetworkElementType {
    PIPE,             // Standard Darcy-Weisbach pipe
    WELL_IPR,         // Inflow Performance Relationship
    CHOKE,            // Valve / choke with Kv rating
    TUBING,           // Vertical tubing with gravity + friction
    MULTIPHASE_PIPE   // Segmented multiphase pipe flow
}
```

Each element type has its own head-loss and derivative calculation, allowing
the NR-GGA solver to converge efficiently even when mixing very different
resistance characteristics (e.g., a low-resistance IPR next to a high-resistance
choke).

---

## IPR Models

The Inflow Performance Relationship describes how much fluid flows from the
reservoir into the wellbore as a function of drawdown
$\Delta P = P_{res} - P_{wf}$.

### Enum: `IPRType`

```java
public enum IPRType {
    PRODUCTIVITY_INDEX,  // Linear PI (oil or gas)
    VOGEL,               // Vogel's equation for solution-gas drive
    FETKOVICH            // Fetkovich empirical model
}
```

### Productivity Index (PI)

For oil (incompressible), the flow rate is:

$$Q = PI \times (P_{res} - P_{wf})$$

For gas (compressible), the flow is proportional to the pressure-squared
difference:

$$Q = PI_{gas} \times (P_{res}^2 - P_{wf}^2)$$

**Parameters:**

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Reservoir pressure | `setReservoirPressure(double)` | bara | Static reservoir pressure |
| Productivity index (oil) | `setProductivityIndex(double)` | kg/s/Pa | Oil PI |
| Gas IPR flag | `setGasIPR(boolean)` | — | Use pressure-squared formulation |
| Productivity index (gas) | `setProductivityIndex(double)` | kg/s/Pa² | Gas PI |

**Convenience method:**

```java
network.addWellIPR("ipr1", "reservoir", "wellhead",
    350.0,           // reservoir pressure [bara]
    5e-7,            // PI [kg/s/Pa] for oil, or [kg/s/Pa²] for gas
    false);          // gasIPR = false → oil PI
```

### Vogel's Equation

For solution-gas-drive reservoirs below the bubble point:

$$\frac{Q}{Q_{max}} = 1 - 0.2 \left(\frac{P_{wf}}{P_{res}}\right) - 0.8 \left(\frac{P_{wf}}{P_{res}}\right)^2$$

The absolute open-flow potential $Q_{max}$ is specified instead of PI.

**Convenience method:**

```java
network.addWellIPRVogel("vogel1", "reservoir", "wellhead",
    350.0,           // reservoir pressure [bara]
    50.0);           // Qmax [kg/s]
```

### Fetkovich Empirical Model

A generalized IPR with empirical exponent $n$ (0.5 to 1.0):

$$Q = C \times (P_{res}^2 - P_{wf}^2)^n$$

**Convenience method:**

```java
network.addWellIPRFetkovich("fetk1", "reservoir", "wellhead",
    350.0,           // reservoir pressure [bara]
    1e-12,           // C coefficient
    0.8);            // n exponent (0.5–1.0)
```

---

## Choke Model

The choke model follows IEC 60534-style valve flow equations:

$$Q = K_v \times \theta \times \sqrt{\Delta P \times \rho}$$

Where:
- $K_v$ = valve flow coefficient [m³/hr/√bar]
- $\theta$ = choke opening fraction (0–1)
- $\Delta P$ = pressure drop across choke [Pa]
- $\rho$ = fluid density [kg/m³]

### Critical Flow

When the pressure ratio drops below the critical pressure ratio $x_T$
(typically 0.7), the flow is choked and the pressure drop is capped:

$$\Delta P_{max} = P_{upstream} \times x_T$$

The solver automatically detects the transition between subcritical and
critical flow and adjusts the Jacobian derivatives accordingly.

**Parameters:**

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Kv | `setChokeKv(double)` | m³/hr/√bar | Valve flow coefficient |
| Opening | `setChokeOpening(double)` | 0–1 | Fraction open |
| Critical ratio | `setChokeCriticalPressureRatio(double)` | 0–1 | Default 0.7 |

**Convenience method:**

```java
network.addChoke("choke1", "wellhead", "downstream",
    150.0,           // Kv [m³/hr/√bar]
    0.8,             // opening fraction
    0.7);            // critical pressure ratio
```

### Choosing Kv Values

The Kv value should be realistic for the expected flow and pressure drop.
A useful check:

$$K_v \approx \frac{Q_{expected}}{\theta \times \sqrt{\Delta P_{expected} \times \rho}}$$

**Typical ranges:**

| Application | Kv Range (m³/hr/√bar) |
|-------------|----------------------|
| Gas well choke | 100–300 |
| Oil well choke | 50–200 |
| Control valve (small) | 5–50 |
| Control valve (large) | 50–500 |

Using too small a Kv for the expected flow rate can cause convergence
difficulties because the choke becomes the dominant resistance and may
drive the solver into the critical-flow regime.

---

## Tubing VLP Model

The tubing element models vertical lift performance (VLP) — the pressure
drop in the production tubing due to gravity and friction:

$$\Delta P = \rho g L \sin\alpha + f \frac{L}{D} \frac{\rho v^2}{2}$$

Where:
- $\alpha$ = inclination from horizontal (90° = vertical)
- $L$ = measured depth of tubing
- $f$ = Darcy friction factor
- $D$ = tubing internal diameter

For deep wells, the tubing is divided into segments, each evaluated at
local conditions (pressure-averaged density).

**Parameters:**

| Parameter | Setter on `NetworkPipe` | Unit | Description |
|-----------|------------------------|------|-------------|
| Inclination | `setTubingInclination(double)` | degrees | From horizontal |
| Segments | `setTubingSegments(int)` | — | Number of integration segments |

**Convenience method:**

```java
network.addTubing("tubing1", "bottomhole", "wellhead",
    3000.0,          // measured depth [m]
    0.1,             // tubing ID [m]
    90.0,            // inclination [degrees from horizontal]
    10);             // number of segments
```

---

## Multiphase Pipe Flow

Multiphase pipe elements use a segmented approach similar to tubing but
oriented horizontally (or at any angle). Each segment evaluates local
friction and gravity based on bulk fluid properties.

**Convenience method:**

```java
network.addMultiphasePipe("mp1", "manifold", "platform",
    15000.0,         // length [m]
    0.3,             // ID [m]
    0.0,             // inclination [degrees]
    20);             // number of segments
```

---

## Building a Production Network

### Step-by-Step Pattern

```java
// 1. Create network and set fluid template
LoopedPipeNetwork net = new LoopedPipeNetwork("Gathering System");
SystemSrkEos gas = new SystemSrkEos(288.15, 100.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");
net.setFluidTemplate(gas);

// 2. Add reservoir nodes (SOURCE, fixed pressure)
net.addSourceNode("res1", 350.0, 0.0);  // P=350 bara, demand=0

// 3. Add delivery node (SINK, fixed pressure)
net.addFixedPressureSinkNode("separator", 50.0);

// 4. Add intermediate junction nodes
net.addJunctionNode("wellhead");
net.addJunctionNode("downstream_choke");

// 5. Add production elements
net.addWellIPR("ipr", "res1", "wellhead", 350.0, 5e-7, false);
net.addChoke("choke", "wellhead", "downstream_choke", 150.0, 0.8, 0.7);
net.addPipe("downstream_choke", "separator", "flowline", 5000.0, 0.2);

// 6. Configure solver
net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setTolerance(500.0);       // Pa
net.setMaxIterations(500);

// 7. Solve
net.run();

// 8. Inspect results
System.out.println("Converged: " + net.isConverged());
System.out.println("Well rate: " + net.getPipeFlowRate("ipr") + " kg/hr");
System.out.println("WHP: " + net.getNodePressure("wellhead") + " bara");
```

### Multi-Well Gathering Network

```
     [Res 1]            [Res 2]
        │                   │
    (IPR 1)             (IPR 2)
        │                   │
     [WH 1]             [WH 2]
        │                   │
    (Choke 1)           (Choke 2)
        │                   │
     [DS 1]             [DS 2]
        │                   │
    (Pipe 1)            (Pipe 2)
        │                   │
        └───────┬───────────┘
                │
           [Manifold]
                │
           (Export Pipe)
                │
          [Separator]
```

```java
LoopedPipeNetwork net = new LoopedPipeNetwork("Multi-Well");
net.setFluidTemplate(gas);

// Well 1: high-pressure, high-PI
net.addSourceNode("res1", 350.0, 0.0);
net.addJunctionNode("wh1");
net.addJunctionNode("ds1");
net.addWellIPR("ipr1", "res1", "wh1", 350.0, 8e-13, true);   // gas IPR
net.addChoke("choke1", "wh1", "ds1", 150.0, 0.8, 0.7);
net.addPipe("ds1", "manifold", "pipe1", 5000.0, 0.2);

// Well 2: lower-pressure, lower-PI
net.addSourceNode("res2", 280.0, 0.0);
net.addJunctionNode("wh2");
net.addJunctionNode("ds2");
net.addWellIPR("ipr2", "res2", "wh2", 280.0, 5e-13, true);
net.addChoke("choke2", "wh2", "ds2", 150.0, 0.6, 0.7);
net.addPipe("ds2", "manifold", "pipe2", 8000.0, 0.2);

// Gathering
net.addJunctionNode("manifold");
net.addFixedPressureSinkNode("separator", 50.0);
net.addPipe("manifold", "separator", "export", 10000.0, 0.3);

// Solve
net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setTolerance(500.0);
net.setMaxIterations(500);
net.run();
```

---

## Solver Details

### Newton-Raphson Global Gradient Algorithm

The production network solver uses the NR-GGA (Todini & Pilati, 1988)
extended for generalized resistance elements. The system is formulated as:

$$\begin{bmatrix} D & -A^T \\ A & 0 \end{bmatrix} \begin{bmatrix} \delta Q \\ \delta H \end{bmatrix} = \begin{bmatrix} -f_1 \\ -f_2 \end{bmatrix}$$

Where:
- $D$ = diagonal matrix of $dh/dQ$ derivatives
- $A$ = node-pipe incidence matrix
- $f_1$ = head-loss residuals per element
- $f_2$ = mass-balance residuals at nodes

For **pipe** elements, $f_1$ uses the Darcy-Weisbach resistance inline:

$$f_1 = D_i Q_i + (H_{from} - H_{to})$$

For **production** elements (IPR, choke, tubing), $f_1$ compares the
element-specific head loss to the available pressure difference:

$$f_1 = h_{element}(Q_i) - (P_{from} - P_{to})$$

The Schur complement reduces the $(np + nn) \times (np + nn)$ system to
an $nn \times nn$ system for node heads, then back-substitutes for flows.

### Adaptive Relaxation

The solver uses adaptive under-relaxation for stability:

| Iteration Range | Residual Condition | Relaxation Factor |
|----------------|-------------------|-------------------|
| 1–5 | $> 10^6$ Pa | 0.3 |
| 6–15 | $> 10^4$ Pa | 0.6 |
| After 15 | Any | 1.0 (full Newton) |

### Flow Initialization

Production elements get physics-based initial flow estimates:

- **IPR**: $Q_0 = PI \times (P_{res} - P_{sink})/2$
- **Choke**: $Q_0 = K_v \times \theta \times \sqrt{\Delta P_{est} \times \rho}$
- **Pipe**: $Q_0$ from Darcy-Weisbach with estimated velocity

Pressures at free nodes are initialized by BFS propagation from
fixed-pressure nodes (sources and sinks).

---

## Usage Examples

### Example 1: Single Well with PI

```java
LoopedPipeNetwork net = new LoopedPipeNetwork("Single Well PI");
net.setFluidTemplate(gas);

net.addSourceNode("reservoir", 350.0, 0.0);
net.addJunctionNode("wellhead");
net.addFixedPressureSinkNode("separator", 40.0);

net.addWellIPR("ipr", "reservoir", "wellhead", 350.0, 5e-7, false);
net.addPipe("wellhead", "separator", "flowline", 5000.0, 0.2);

net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setTolerance(100.0);
net.setMaxIterations(200);
net.run();

double wellRate = net.getPipeFlowRate("ipr");     // kg/hr
double whp = net.getNodePressure("wellhead");      // bara
```

### Example 2: Vogel IPR

```java
net.addWellIPRVogel("vogel", "reservoir", "wellhead", 350.0, 50.0);
```

### Example 3: Complete Well System

IPR + tubing + choke + flowline in series:

```java
LoopedPipeNetwork net = new LoopedPipeNetwork("Complete Well");
net.setFluidTemplate(gas);

net.addSourceNode("reservoir", 400.0, 0.0);
net.addJunctionNode("bottomhole");
net.addJunctionNode("wellhead");
net.addJunctionNode("ds_choke");
net.addFixedPressureSinkNode("separator", 40.0);

net.addWellIPR("ipr", "reservoir", "bottomhole", 400.0, 5e-7, false);
net.addTubing("tubing", "bottomhole", "wellhead", 3000.0, 0.1, 90.0, 10);
net.addChoke("choke", "wellhead", "ds_choke", 150.0, 0.7, 0.7);
net.addPipe("ds_choke", "separator", "flowline", 5000.0, 0.2);

net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setTolerance(500.0);
net.setMaxIterations(500);
net.run();
```

### Example 4: Choke Sensitivity Study

Sweep choke opening from 10% to 100% and observe how production rate
and wellhead pressure respond:

```java
double[] openings = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
for (double opening : openings) {
    net.getNetworkPipe("choke").setChokeOpening(opening);
    net.run();
    System.out.printf("Opening=%.0f%%  Q=%.0f kg/hr  WHP=%.1f bara%n",
        opening * 100,
        net.getPipeFlowRate("ipr"),
        net.getNodePressure("wellhead"));
}
```

---

## Results and Inspection

### Solution Summary

```java
// JSON-like summary of the network solution
String summary = net.getSolutionSummary();
```

Returns a map with keys:
- `converged` (boolean)
- `iterations` (int)
- `maxResidual_Pa` (double)
- `tolerance_Pa` (double)
- `nodePressures_bara` (map of node name to pressure)
- `pipeFlowRates_kghr` (map of pipe name to flow rate)
- `massBalanceError_kgs` (double)

### Element Inspection

```java
// Inspect each element's type and calculated values
for (String pipeName : net.getPipeNames()) {
    NetworkPipe pipe = net.getNetworkPipe(pipeName);
    System.out.printf("%-12s type=%-18s flow=%10.0f kg/hr%n",
        pipeName,
        pipe.getElementType(),
        net.getPipeFlowRate(pipeName));
}
```

### Hydraulic Report

```java
// Detailed pipe-by-pipe hydraulic report
String report = net.getHydraulicReport();
```

### Mass Balance

```java
// Verify mass balance at every node
String massBalance = net.getMassBalanceReport();
```

---

## Artificial Lift

The `LoopedPipeNetwork` supports four artificial lift methods that inject
additional energy into wells to increase production. Each method applies a
pressure boost in the NR-GGA solver's `calculateHeadLoss()` step.

### Lift Types

| Type | Enum Value | Method | Typical Application |
|------|-----------|--------|---------------------|
| Gas lift | `GAS_LIFT` | `setGasLift()` | High GOR wells, offshore |
| ESP | `ESP` | `setESP()` | High-rate wells, deepwater |
| Jet pump | `JET_PUMP` | `setJetPump()` | Low-rate, deviated wells |
| Rod pump | `ROD_PUMP` | `setRodPump()` | Onshore, low-rate stripper wells |

### API

```java
// Gas lift: inject gas to reduce hydrostatic gradient
net.setGasLift("W3", 500.0);              // 500 kg/hr gas lift rate

// ESP: electrical submersible pump
net.setESP("W4", 80.0, 0.55);             // 80 kW power, 55% efficiency

// Jet pump: Venturi-based downhole pump
net.setJetPump("W5", 600.0, 0.40);        // 600 kW nozzle power, 40% efficiency

// Rod pump: sucker-rod beam pump
net.setRodPump("W6", 30.0, 0.35);         // 30 kW motor, 35% efficiency

// Query totals
double totalGL = net.getTotalGasLiftRate();   // kg/hr
double totalESP = net.getTotalESPPower();     // kW
```

### Effect on Solver

Each artificial lift type converts its input energy (gas injection rate or
electrical power) to a pressure boost $\Delta P_{lift}$ that is subtracted from
the well element's head loss. For example, gas lift reduces the effective
hydrostatic gradient, while ESP/jet/rod pump add a direct pressure rise to the
well's IPR resistance.

---

## Water Handling

Track produced water, water injection, and water breakthrough per well.

### API

```java
// Set water cut on a well (fraction, 0-1)
net.setWaterCut("W1", 0.15);       // 15% water cut
net.setWaterCut("W2", 0.40);       // 40% water cut

// Add water injection support
// addWaterInjection(sourceNode, reservoirNode, elementName, rateKgHr)
net.addWaterInjection("WI_supply", "R2", "WI-W2", 2000.0);

// Water breakthrough tracking
// setWaterBreakthrough(elementName, breakthroughWC, finalWC, currentWC)
net.setWaterBreakthrough("W1", 0.10, 0.65, 0.10);

// Calculate water balance (after run)
Map<String, double[]> wb = net.calculateWaterBalance();
// Per node: [0] = water production (kg/hr), [1] = injection (kg/hr), [2] = net

double totalProd = net.getTotalWaterProduction();   // kg/hr
double totalInj  = net.getTotalWaterInjection();    // kg/hr
```

---

## Sand and Solids Tracking

Per-element sand production, erosion rate (DNV RP O501), and deposition
tracking with configurable limits and violation reporting.

### API

```java
net.setSandRate("W1", 3.0);             // 3 kg/hr sand production
net.setSandRate("W2", 12.0);            // 12 kg/hr

net.setMaxAllowableSandRate(10.0);      // kg/hr limit
net.setMaxAllowableErosionRate(5.0);    // mm/yr limit

net.run();
Map<String, double[]> sand = net.calculateSandTransport();
// Per element: [0] = sand rate (kg/hr), [1] = concentration (kg/m3),
//              [2] = erosion rate (mm/yr), [3] = deposition rate (mm/yr)

List<String> violations = net.getSandViolations();
```

### Erosion Model (DNV RP O501)

$$
E = K \cdot C_{sand} \cdot v^{2.6} \cdot d_p^{0.2} \cdot 3600 \cdot 8760
$$

where $E$ is erosion rate (mm/yr), $K$ is the material factor, $C_{sand}$ is
sand concentration (kg/m3), $v$ is flow velocity (m/s), and $d_p$ is particle
diameter (m). Deposition is flagged when velocity falls below 1 m/s.

---

## Corrosion and Integrity

Inline CO2/H2S corrosion rate estimation with two industry-standard models
and remaining wall life calculation.

### API

```java
// Set corrosive gas composition on a pipe or well
net.setCorrosiveGas("trunk", 0.035, 0.002);  // 3.5 mol% CO2, 0.2 mol% H2S
net.setCorrosionModel("trunk", "NORSOK");     // "DEWAARD" (default) or "NORSOK"

net.setMinAllowableWallLife(20.0);  // years

net.run();
Map<String, double[]> corr = net.calculateCorrosion();
// Per element: [0] = corrosion rate (mm/yr), [1] = pCO2 (bar),
//              [2] = remaining wall life (yr)

List<String> violations = net.getCorrosionViolations();
```

### de Waard-Milliams Model

$$
\log_{10}(V_{corr}) = 5.8 - \frac{1710}{T} + 0.67 \cdot \log_{10}(p_{CO_2})
$$

where $T$ is temperature (K) and $p_{CO_2}$ is CO2 partial pressure (bar).
An H2S multiplier $(1 + 2 \cdot y_{H_2S}/y_{CO_2})$ is applied when H2S is present.

### NORSOK M-506 Model

$$
V_{corr} = K_t \cdot f_{CO_2}^{0.62} \cdot \left(\frac{S}{19}\right)^{0.146}
$$

where $K_t$ is a temperature-dependent factor, $f_{CO_2}$ is CO2 fugacity,
and $S$ is the wall shear stress (Pa). The NORSOK model generally predicts
lower rates than de Waard-Milliams and accounts for protective scale formation.

---

## GHG Emissions Tracking

Calculate CO2 and methane emissions per compressor station with field-level
totals, annual figures, and emissions intensity.

### API

```java
net.setCO2EmissionFactor(2.75);     // kg CO2 per kg fuel gas (default)
net.setMethaneSlipFactor(0.02);     // 2% methane slip (default)

net.run();

// Set compressor power (if not computed by the solver)
net.getPipe("booster").setCompressorPower(1500.0);  // kW

Map<String, double[]> em = net.calculateEmissions();
// Per compressor: [0] = CO2 (kg/hr), [1] = CH4 slip (kg/hr),
//                 [2] = CO2-eq (kg/hr), [3] = power (kW), [4] = fuel gas (kg/hr)

double totalCO2eq = net.getTotalCO2Emissions();        // kg/hr
double annual     = net.getAnnualCO2EmissionsTonnes();  // tonnes/yr
double intensity  = net.getEmissionsIntensity();        // kgCO2-eq/tonne product
```

### Emission Factors

| Parameter | Default | Units | Source |
|-----------|---------|-------|--------|
| CO2 emission factor | 2.75 | kgCO2/kg fuel | Natural gas combustion |
| Methane slip | 2% | fraction | Gas turbine typical |
| Methane GWP | 28 | - | IPCC AR5 (100-yr) |
| Fuel gas heat rate | 10,000 | kJ/kWh | Gas turbine typical |
| LHV natural gas | 48,000 | kJ/kg | - |

### Calculations

$$
\dot{m}_{fuel} = \frac{P \cdot HR}{LHV \cdot 3600}
$$

$$
CO_{2,eq} = \dot{m}_{fuel} \cdot EF_{CO_2} + \dot{m}_{fuel} \cdot f_{slip} \cdot GWP_{CH_4}
$$

where $P$ is compressor power (kW), $HR$ is heat rate (kJ/kWh),
$LHV$ is LHV (kJ/kg), $EF_{CO_2}$ is the CO2 emission factor,
$f_{slip}$ is methane slip fraction, and $GWP_{CH_4}$ is the methane global
warming potential.

---

## Large-Scale Networks

The NR-GGA solver handles networks with 100+ wells efficiently. The Schur
complement reduction keeps the system matrix size proportional to the number
of independent loops, not the total number of elements.

### Scaling Example

```java
LoopedPipeNetwork net = new LoopedPipeNetwork("large");
net.setFluidTemplate(gas);
net.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
net.setMaxIterations(500);
net.setTolerance(500.0);

// 6 manifolds, 20 wells each = 120 wells
for (int m = 0; m < 6; m++) {
    net.addJunctionNode("MF" + (m + 1));
    for (int w = 0; w < 20; w++) {
        double resP = 230.0 + random(-30, 30);
        double pi = 5e-13 * random(0.5, 2.0);
        net.addSourceNode("R" + m + "_" + w, resP, 0.0);
        net.addWellIPR("R" + m + "_" + w, "MF" + (m + 1),
                        "W" + m + "_" + w, pi, true);
    }
}

// Star topology: manifolds to hub to export
net.addJunctionNode("hub");
for (int m = 0; m < 6; m++) {
    net.addPipe("MF" + (m + 1), "hub", "trunk" + (m + 1),
                5000.0 + m * 3000.0, 0.3);
}
net.addFixedPressureSinkNode("export", 70.0);
net.addPipe("hub", "export", "export_line", 30000.0, 0.4);

net.run();
// Typically converges in 15-20 iterations, < 0.1 s
```

### Performance Benchmarks

| Network Size | Wells | Elements | Iterations | Time |
|-------------|-------|----------|-----------|------|
| Small | 4 | 10 | 8-12 | < 0.01 s |
| Medium | 20 | 50 | 12-15 | < 0.03 s |
| Large | 120 | 300+ | 15-20 | < 0.1 s |

---

## Troubleshooting

### Common Issues

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Non-convergence with choke | Kv too small for the flow | Increase Kv or reduce flow rate |
| Oscillation near critical flow | Jacobian mismatch at subcritical/critical boundary | Increase tolerance to 500 Pa |
| Zero flow from IPR | Separator pressure > reservoir pressure | Check node pressures |
| Negative flow in tubing | Wrong node ordering | Ensure from-node is bottomhole |
| Slow convergence | Large pressure span in network | Increase maxIterations to 500 |

### Choosing Tolerances

For **pipe-only** networks (water distribution, gas transmission):
- Tolerance: 0.001–1.0 Pa
- MaxIterations: 50–200

For **production networks** with chokes and IPR elements:
- Tolerance: 100–500 Pa
- MaxIterations: 200–500

The larger tolerance is needed because choke elements have steep
resistance curves that amplify small flow changes into large
pressure residuals.

---

## Related Documentation

- [Looped Pipeline Networks](looped_networks) — Hardy Cross and NR-GGA solver fundamentals
- [Pipeline Networks (Basic)](networks) — PipeFlowNetwork and WellFlowlineNetwork
- [Pipelines](pipelines) — Individual pipeline modeling with Beggs-Brill, OLGA
- [Production Network Notebook](../../../examples/notebooks/production_well_network.ipynb) — 8 worked examples with plots
- [Looped Network Notebook](../../../examples/notebooks/looped_pipe_network.ipynb) — 6 examples of pipe-only networks

---

## API Reference

### Convenience Methods on LoopedPipeNetwork

```java
// Well IPR (oil or gas PI model)
void addWellIPR(String name, String fromNode, String toNode,
    double reservoirPressure_bara, double pi, boolean gasIPR)

// Vogel IPR (solution-gas drive)
void addWellIPRVogel(String name, String fromNode, String toNode,
    double reservoirPressure_bara, double qmax_kgs)

// Fetkovich empirical IPR
void addWellIPRFetkovich(String name, String fromNode, String toNode,
    double reservoirPressure_bara, double C, double n)

// Choke / valve
void addChoke(String name, String fromNode, String toNode,
    double kv, double opening, double criticalPressureRatio)

// Tubing (vertical lift performance)
void addTubing(String name, String fromNode, String toNode,
    double length_m, double diameter_m,
    double inclination_deg, int segments)

// Multiphase pipe (segmented horizontal/inclined)
void addMultiphasePipe(String name, String fromNode, String toNode,
    double length_m, double diameter_m,
    double inclination_deg, int segments)

// Fixed-pressure sink (separator, platform)
void addFixedPressureSinkNode(String name, double pressure_bara)
```

### NetworkPipe Production Fields

```java
// Element type
NetworkElementType getElementType()
void setElementType(NetworkElementType type)

// IPR fields
IPRType getIprType()
void setIprType(IPRType type)
double getReservoirPressure()
void setReservoirPressure(double p)
double getProductivityIndex()
void setProductivityIndex(double pi)
boolean isGasIPR()
void setGasIPR(boolean gas)
double getVogelQmax()
void setVogelQmax(double qmax)
double getFetkovichC()
void setFetkovichC(double c)
double getFetkovichN()
void setFetkovichN(double n)

// Choke fields
double getChokeKv()
void setChokeKv(double kv)
double getChokeOpening()
void setChokeOpening(double opening)
double getChokeCriticalPressureRatio()
void setChokeCriticalPressureRatio(double ratio)

// Tubing / multiphase fields
double getTubingInclination()
void setTubingInclination(double degrees)
int getTubingSegments()
void setTubingSegments(int n)
int getMultiphaseSegments()
void setMultiphaseSegments(int n)
```

### NetworkElementType Enum

```java
public enum NetworkElementType {
    PIPE,
    WELL_IPR,
    CHOKE,
    TUBING,
    MULTIPHASE_PIPE
}
```

### IPRType Enum

```java
public enum IPRType {
    PRODUCTIVITY_INDEX,
    VOGEL,
    FETKOVICH
}
```

---

## References

1. Todini, E. & Pilati, S. (1988). "A gradient algorithm for the analysis of pipe networks." *Computer Applications in Water Supply*, Vol. 1, pp. 1–20.
2. Vogel, J.V. (1968). "Inflow Performance Relationships for Solution-Gas Drive Wells." *JPT*, 83–92.
3. Fetkovich, M.J. (1973). "The Isochronal Testing of Oil Wells." *SPE 4529*.
4. IEC 60534-2-1. "Industrial-process control valves — Flow capacity."
