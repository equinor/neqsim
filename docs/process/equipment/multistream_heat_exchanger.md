---
title: Multi-Stream Heat Exchanger
description: Comprehensive guide to multi-stream heat exchanger modeling in NeqSim, including mathematical foundations, composite curves, and practical examples for LNG, cryogenic, and refinery applications.
---

# Multi-Stream Heat Exchanger

The `MultiStreamHeatExchanger2` class models heat exchangers with multiple hot and cold streams exchanging heat simultaneously. This is essential for:

- **LNG/Cryogenic Plants**: Main cryogenic heat exchangers (MCHE)
- **Refinery Heat Integration**: Crude preheat trains
- **Gas Processing**: Turbo-expander cold boxes
- **Chemical Plants**: Multi-effect evaporators

## Table of Contents

- [Overview](#overview)
- [Mathematical Foundations](#mathematical-foundations)
  - [Energy Balance](#energy-balance)
  - [Composite Curves](#composite-curves)
  - [Pinch Analysis](#pinch-analysis)
  - [UA Calculation (LMTD Method)](#ua-calculation-lmtd-method)
- [Solver Modes](#solver-modes)
- [Usage Examples](#usage-examples)
  - [Basic Usage](#basic-usage)
  - [One Unknown Temperature](#one-unknown-temperature)
  - [Two Unknown Temperatures](#two-unknown-temperatures)
  - [Three Unknown Temperatures](#three-unknown-temperatures)
- [Python Examples](#python-examples)
- [API Reference](#api-reference)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

| Property | Value |
|----------|-------|
| **Class** | `neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2` |
| **Package** | `neqsim.process.equipment.heatexchanger` |
| **Max Streams** | Unlimited (practical limit ~10) |
| **Unknown Outlets** | 1, 2, or 3 temperatures |
| **Solver** | Newton-Raphson with numerical Jacobian |

```
         ┌─────────────────────────────────────────┐
Hot 1 ───▶│                                         │───▶ Hot 1 Out
Hot 2 ───▶│     MULTI-STREAM HEAT EXCHANGER         │───▶ Hot 2 Out
Hot 3 ───▶│                                         │───▶ Hot 3 Out
         │        Q_hot = Q_cold                   │
Cold 1 ──▶│     (Energy Balance)                   │───▶ Cold 1 Out
Cold 2 ──▶│                                         │───▶ Cold 2 Out
Cold 3 ──▶│     ΔT_min ≥ Approach Temperature      │───▶ Cold 3 Out
         └─────────────────────────────────────────┘
```

---

## Mathematical Foundations

### Energy Balance

The fundamental constraint is that total heat released by hot streams equals total heat absorbed by cold streams:

$$
\sum_{i \in \text{hot}} \dot{m}_i \cdot (h_{in,i} - h_{out,i}) = \sum_{j \in \text{cold}} \dot{m}_j \cdot (h_{out,j} - h_{in,j})
$$

Where:
- $\dot{m}_i$ = mass flow rate of stream $i$ (kg/s)
- $h_{in,i}$ = specific enthalpy at inlet (kJ/kg)
- $h_{out,i}$ = specific enthalpy at outlet (kJ/kg)

**Enthalpy Calculation**: NeqSim performs a TP flash at each temperature to calculate enthalpy, accounting for:
- Phase changes (condensation/vaporization)
- Non-ideal mixing
- Real gas effects

The energy imbalance residual function:

$$
f_E = Q_{hot} + Q_{cold} = \sum_i \dot{m}_i (h_{out,i} - h_{in,i})
$$

At convergence: $|f_E| < \epsilon$ (default $\epsilon = 10^{-3}$ kW)

### Composite Curves

Composite curves combine all hot streams into a single "hot composite" and all cold streams into a "cold composite". This enables visualization of heat transfer feasibility and pinch point identification.

**Construction Algorithm:**

1. **Collect temperature breakpoints**: All inlet and outlet temperatures on each curve
2. **Sort temperatures** in ascending order
3. **Calculate interval loads**: For each temperature interval $[T_k, T_{k+1}]$:

$$
Q_{\text{interval}} = \sum_{i \in \text{active}} \dot{m}_i \cdot C_{p,i} \cdot (T_{k+1} - T_k)
$$

Where "active" streams are those spanning the interval.

4. **Accumulate loads**: Starting from $Q = 0$ at the coldest point:

$$
Q_k = Q_{k-1} + Q_{\text{interval},k}
$$

**Result**: Temperature vs. cumulative heat duty for each composite curve.

### Pinch Analysis

The **pinch point** is the location of minimum temperature approach between hot and cold composites:

$$
\Delta T_{min} = \min_k \left( T_{hot,k} - T_{cold,k} \right)
$$

Where temperatures are evaluated at the same cumulative heat load.

**Feasibility constraint**: For thermodynamically feasible heat exchange:

$$
\Delta T_{min} \geq \Delta T_{approach}
$$

The pinch residual function:

$$
f_P = \Delta T_{min} - \Delta T_{approach}
$$

### UA Calculation (LMTD Method)

The overall heat transfer coefficient-area product (UA) is calculated by summing contributions from each interval:

$$
UA = \sum_{k=1}^{N} \frac{Q_k}{\text{LMTD}_k}
$$

Where the Log Mean Temperature Difference for interval $k$ is:

$$
\text{LMTD}_k = \frac{(T_{h,in} - T_{c,in}) - (T_{h,out} - T_{c,out})}{\ln\left(\frac{T_{h,in} - T_{c,in}}{T_{h,out} - T_{c,out}}\right)}
$$

For small temperature differences ($|\Delta T_1 - \Delta T_2| < 10^{-4}$):

$$
\text{LMTD}_k \approx \frac{\Delta T_1 + \Delta T_2}{2}
$$

**Units**: UA is returned in W/K (Watts per Kelvin).

---

## Solver Modes

The solver automatically selects the solution method based on the number of unknown outlet temperatures:

| Unknown Count | Equations | Variables | Method |
|--------------|-----------|-----------|--------|
| 0 | - | - | Direct calculation (no iteration) |
| 1 | Energy balance | 1 temperature | 1×1 Newton-Raphson |
| 2 | Energy + Pinch | 2 temperatures | 2×2 Newton-Raphson |
| 3 | Energy + Pinch + UA | 3 temperatures | 3×3 Newton-Raphson |

### Newton-Raphson Iteration

For $n$ unknowns, the system solves:

$$
\mathbf{J} \cdot \Delta \mathbf{T} = \mathbf{f}
$$

Where:
- $\mathbf{f}$ = residual vector (energy, pinch, UA)
- $\mathbf{J}$ = Jacobian matrix (numerical derivatives)
- $\Delta \mathbf{T}$ = temperature corrections

**Jacobian calculation** (numerical, with $\delta = 10^{-4}$°C):

$$
J_{ij} = \frac{f_i(T_j + \delta) - f_i(T_j)}{\delta}
$$

**Damping**: Updates are damped to improve stability:

$$
T_j^{new} = T_j^{old} - \alpha \cdot \Delta T_j
$$

Default damping factor $\alpha = 1.0$.

---

## Usage Examples

### Basic Usage

```java
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemSrkEos fluid = new SystemSrkEos(273.15 + 50.0, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-butane", 0.02);
fluid.setMixingRule("classic");

// Create hot streams
Stream hot1 = new Stream("Hot-1", fluid.clone());
hot1.setTemperature(100.0, "C");
hot1.setFlowRate(20000.0, "kg/hr");

Stream hot2 = new Stream("Hot-2", fluid.clone());
hot2.setTemperature(80.0, "C");
hot2.setFlowRate(15000.0, "kg/hr");

// Create cold streams
Stream cold1 = new Stream("Cold-1", fluid.clone());
cold1.setTemperature(10.0, "C");
cold1.setFlowRate(25000.0, "kg/hr");

Stream cold2 = new Stream("Cold-2", fluid.clone());
cold2.setTemperature(20.0, "C");
cold2.setFlowRate(10000.0, "kg/hr");

// Create multi-stream heat exchanger
MultiStreamHeatExchanger2 mshx = new MultiStreamHeatExchanger2("MCHE-100");

// Add streams: (stream, type, outletTemp)
// outletTemp = null means "solve for this"
mshx.addInStreamMSHE(hot1, "hot", 40.0);     // Known outlet: 40°C
mshx.addInStreamMSHE(hot2, "hot", 35.0);     // Known outlet: 35°C
mshx.addInStreamMSHE(cold1, "cold", null);   // Unknown - solve
mshx.addInStreamMSHE(cold2, "cold", 60.0);   // Known outlet: 60°C

// Set approach temperature
mshx.setTemperatureApproach(5.0);  // Minimum ΔT = 5°C

// Build process and run
ProcessSystem process = new ProcessSystem();
process.add(hot1);
process.add(hot2);
process.add(cold1);
process.add(cold2);
process.add(mshx);
process.run();

// Get results
System.out.println("Cold-1 outlet: " + mshx.getOutStream(2).getTemperature("C") + " °C");
System.out.println("UA value: " + mshx.getUA() + " W/K");
System.out.println("Pinch ΔT: " + mshx.getTemperatureApproach() + " °C");
```

### One Unknown Temperature

When only one outlet is unknown, the solver uses energy balance alone:

```java
MultiStreamHeatExchanger2 mshx = new MultiStreamHeatExchanger2("HX-1");

// All outlets known except one
mshx.addInStreamMSHE(hot1, "hot", 50.0);    // Known
mshx.addInStreamMSHE(hot2, "hot", 45.0);    // Known
mshx.addInStreamMSHE(cold1, "cold", null);  // Unknown - energy balance determines this
mshx.addInStreamMSHE(cold2, "cold", 70.0);  // Known

// No need to set approach or UA - only energy balance is solved
mshx.run();
```

### Two Unknown Temperatures

With two unknowns, the solver uses energy balance + pinch constraint:

```java
MultiStreamHeatExchanger2 mshx = new MultiStreamHeatExchanger2("HX-2");

mshx.addInStreamMSHE(hot1, "hot", null);    // Unknown
mshx.addInStreamMSHE(hot2, "hot", 80.0);
mshx.addInStreamMSHE(hot3, "hot", 60.0);
mshx.addInStreamMSHE(cold1, "cold", 10.0);
mshx.addInStreamMSHE(cold2, "cold", null);  // Unknown
mshx.addInStreamMSHE(cold3, "cold", 30.0);

// REQUIRED: Set approach temperature
mshx.setTemperatureApproach(5.0);

mshx.run();

// Solver finds temperatures that satisfy:
// 1. Energy balance: Q_hot = Q_cold
// 2. Pinch: ΔT_min = 5.0°C
```

### Three Unknown Temperatures

With three unknowns, the solver needs energy + pinch + UA:

```java
MultiStreamHeatExchanger2 mshx = new MultiStreamHeatExchanger2("HX-3");

mshx.addInStreamMSHE(hot1, "hot", null);    // Unknown
mshx.addInStreamMSHE(hot2, "hot", 80.0);
mshx.addInStreamMSHE(hot3, "hot", 60.0);
mshx.addInStreamMSHE(cold1, "cold", null);  // Unknown
mshx.addInStreamMSHE(cold2, "cold", null);  // Unknown
mshx.addInStreamMSHE(cold3, "cold", 30.0);

// REQUIRED for 3 unknowns: Both approach AND UA
mshx.setTemperatureApproach(5.0);
mshx.setUAvalue(70000.0);  // W/K

mshx.run();

// Solver finds temperatures satisfying:
// 1. Energy balance: Q_hot = Q_cold
// 2. Pinch: ΔT_min = 5.0°C
// 3. UA: calculated UA = 70000 W/K
```

---

## Python Examples

### Basic Multi-Stream Heat Exchanger

```python
from neqsim import jneqsim

# Import classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
MultiStreamHeatExchanger2 = jneqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create fluid
fluid = SystemSrkEos(273.15 + 50.0, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Create streams
hot1 = Stream("Hot-1", fluid.clone())
hot1.setTemperature(100.0, "C")
hot1.setFlowRate(20000.0, "kg/hr")

hot2 = Stream("Hot-2", fluid.clone())
hot2.setTemperature(90.0, "C")
hot2.setFlowRate(20000.0, "kg/hr")

cold1 = Stream("Cold-1", fluid.clone())
cold1.setTemperature(0.0, "C")
cold1.setFlowRate(20000.0, "kg/hr")

cold2 = Stream("Cold-2", fluid.clone())
cold2.setTemperature(10.0, "C")
cold2.setFlowRate(10000.0, "kg/hr")

# Create MSHE
mshx = MultiStreamHeatExchanger2("LNG-MCHE")

# Add streams (stream, type, outlet_temp)
# Use None for unknown outlet temperatures
mshx.addInStreamMSHE(hot1, "hot", None)      # Unknown
mshx.addInStreamMSHE(hot2, "hot", 80.0)      # Known: 80°C
mshx.addInStreamMSHE(cold1, "cold", None)    # Unknown
mshx.addInStreamMSHE(cold2, "cold", 85.0)    # Known: 85°C

# Set constraints
mshx.setTemperatureApproach(5.0)  # 5°C minimum approach

# Build and run process
process = ProcessSystem()
process.add(hot1)
process.add(hot2)
process.add(cold1)
process.add(cold2)
process.add(mshx)
process.run()

# Results
print(f"Hot-1 outlet: {mshx.getOutStream(0).getTemperature('C'):.1f} °C")
print(f"Cold-1 outlet: {mshx.getOutStream(2).getTemperature('C'):.1f} °C")
print(f"UA value: {mshx.getUA():.0f} W/K")
```

### Extracting Composite Curves for Plotting

```python
import matplotlib.pyplot as plt

# After running the heat exchanger...
composite_data = mshx.getCompositeCurve()

# Extract hot and cold curves
hot_curve = composite_data.get("hot")
cold_curve = composite_data.get("cold")

# Convert to lists for plotting
hot_loads = [float(p.get("load")) for p in hot_curve]
hot_temps = [float(p.get("temperature")) for p in hot_curve]
cold_loads = [float(p.get("load")) for p in cold_curve]
cold_temps = [float(p.get("temperature")) for p in cold_curve]

# Plot composite curves
plt.figure(figsize=(10, 6))
plt.plot(hot_loads, hot_temps, 'r-o', label='Hot Composite', linewidth=2)
plt.plot(cold_loads, cold_temps, 'b-o', label='Cold Composite', linewidth=2)
plt.xlabel('Heat Load (kW)')
plt.ylabel('Temperature (°C)')
plt.title('Composite Curves')
plt.legend()
plt.grid(True)
plt.show()
```

---

## API Reference

### Constructor

```java
MultiStreamHeatExchanger2(String name)
```

### Key Methods

| Method | Description |
|--------|-------------|
| `addInStreamMSHE(StreamInterface stream, String type, Double outletTemp)` | Add a stream. `type` = "hot" or "cold". `outletTemp` = null for unknown. |
| `setTemperatureApproach(double approach)` | Set minimum approach temperature (°C) |
| `setUAvalue(double ua)` | Set UA constraint (W/K) - required for 3 unknowns |
| `run()` | Execute the solver |
| `getOutStream(int index)` | Get outlet stream by index (order of addition) |
| `getUA()` | Get calculated UA value (W/K) |
| `getTemperatureApproach()` | Get approach temperature setting |
| `getCompositeCurve()` | Get composite curve data for plotting |

### Solver Configuration

| Method | Description | Default |
|--------|-------------|---------|
| `setTolerance(double tol)` | Convergence tolerance | 1e-3 |
| `setMaxIterations(int max)` | Maximum iterations | 1000 |
| `setJacobiDelta(double delta)` | Numerical derivative step | 1e-4 |
| `setDamping(double factor)` | Newton step damping | 1.0 |

---

## Best Practices

### 1. Stream Ordering

Add streams in a logical order (all hot first, then all cold) for easier result interpretation:

```java
// Recommended ordering
mshx.addInStreamMSHE(hot1, "hot", ...);   // Index 0
mshx.addInStreamMSHE(hot2, "hot", ...);   // Index 1
mshx.addInStreamMSHE(cold1, "cold", ...); // Index 2
mshx.addInStreamMSHE(cold2, "cold", ...); // Index 3
```

### 2. Reasonable Initial Guesses

The solver initializes unknown temperatures based on the approach temperature. For difficult cases, provide more known temperatures to improve convergence.

### 3. Physical Feasibility

Ensure your specification is physically feasible:
- Hot outlets should be cooler than inlets
- Cold outlets should be warmer than inlets
- Sufficient temperature driving force exists

### 4. UA Estimation

For 3-unknown cases, estimate UA from:

$$
UA \approx \frac{Q}{\Delta T_{lm}}
$$

Where $Q$ is the expected duty and $\Delta T_{lm}$ is an estimated LMTD.

### 5. Check Composite Curves

Always visualize composite curves to verify:
- No temperature crossover (hot always above cold)
- Pinch location makes physical sense
- Heat loads are balanced

---

## Troubleshooting

### "Failed to converge after maxIterations"

**Causes:**
1. Infeasible specification (temperatures physically impossible)
2. Poor initial guess
3. Stiff problem near pinch

**Solutions:**
- Reduce number of unknowns (specify more outlets)
- Check that hot inlets > cold inlets
- Increase `setMaxIterations()`
- Adjust `setDamping()` to 0.5-0.8

### "Jacobian is singular"

**Causes:**
1. Two unknowns have identical effect on residuals
2. System at or near degenerate point

**Solutions:**
- Reduce number of unknowns
- Add small UA regularization
- Check stream specifications

### Negative ΔT in Composite Curves

**Cause:** Temperature crossover (cold above hot) - thermodynamically infeasible.

**Solutions:**
- Increase cold stream flow rates
- Decrease hot stream flow rates
- Reduce specified cold outlet temperatures
- Increase specified hot outlet temperatures

### Very Large UA Values

**Cause:** Small LMTD in one or more intervals.

**Solutions:**
- Check for temperature pinch
- Increase approach temperature specification
- Review flow rates for imbalance

---

## Related Documentation

- [Heat Exchangers Overview](heat_exchangers.md) - Two-stream exchangers
- [Streams](streams.md) - Stream creation
- [Process System](../processmodel/) - Building flowsheets
- [Dynamic Simulation](../../simulation/dynamic_simulation_guide.md) - Transient heat exchangers
