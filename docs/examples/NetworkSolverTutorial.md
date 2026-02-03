---
layout: default
title: "NetworkSolverTutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# NetworkSolverTutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`NetworkSolverTutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb).

---

# Multi-Well Network Solver Tutorial

This notebook demonstrates the NetworkSolver for modeling multi-well gathering systems with flowlines and manifolds.

## Overview

The `NetworkSolver` solves pressure-flow equilibrium in gathering networks:
- Multiple wells with IPR curves
- Flowlines with pressure drop
- Common manifold pressure
- Rate allocation optimization

```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim

# Import Java classes through the jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
WellSystem = jneqsim.process.equipment.reservoir.WellSystem
SimpleReservoir = jneqsim.process.equipment.reservoir.SimpleReservoir
NetworkSolver = jneqsim.process.fielddevelopment.network.NetworkSolver
NetworkResult = jneqsim.process.fielddevelopment.network.NetworkResult

print("NeqSim Network Solver loaded successfully!")
```

## 1. Setting Up the Reservoir and Wells

```python
# Create reservoir fluid
fluid = SystemSrkEos(85.0 + 273.15, 220.0)
fluid.addComponent("methane", 75.0)
fluid.addComponent("ethane", 10.0)
fluid.addComponent("propane", 8.0)
fluid.addComponent("n-butane", 4.0)
fluid.addComponent("n-pentane", 3.0)
fluid.setMixingRule("classic")

# Create reservoir
reservoir = SimpleReservoir("Main_Reservoir")
reservoir.setReservoirFluid(fluid)

print(f"Reservoir pressure: {fluid.getPressure()} bara")
print(f"Reservoir temperature: {fluid.getTemperature() - 273.15:.1f} °C")
```

```python
# Create multiple wells with different productivities
wells = []

# Well A - high productivity
well_a = WellSystem("Well_A")
well_a.setReservoirPressure(220.0, "bara")
well_a.setReservoirTemperature(85.0, "C")
well_a.setProductivityIndex(5.0)  # High PI
wells.append((well_a, 3.0))  # 3 km flowline

# Well B - medium productivity
well_b = WellSystem("Well_B")
well_b.setReservoirPressure(215.0, "bara")  # Slightly lower pressure
well_b.setReservoirTemperature(87.0, "C")
well_b.setProductivityIndex(3.5)
wells.append((well_b, 5.5))  # Longer flowline

# Well C - low productivity, close to manifold
well_c = WellSystem("Well_C")
well_c.setReservoirPressure(210.0, "bara")
well_c.setReservoirTemperature(83.0, "C")
well_c.setProductivityIndex(2.0)
wells.append((well_c, 1.5))  # Short flowline

# Well D - medium-high productivity, far from manifold
well_d = WellSystem("Well_D")
well_d.setReservoirPressure(225.0, "bara")
well_d.setReservoirTemperature(88.0, "C")
well_d.setProductivityIndex(4.0)
wells.append((well_d, 8.0))  # Long flowline

print(f"Created {len(wells)} wells")
```

## 2. Fixed Manifold Pressure Solution

Given the manifold pressure, calculate individual well rates:

```python
# Create network solver
network = NetworkSolver("Gathering_Network")

# Add wells with flowline lengths
for well, flowline_km in wells:
    network.addWell(well, flowline_km)

# Set solution mode and manifold pressure
network.setSolutionMode(NetworkSolver.SolutionMode.FIXED_MANIFOLD_PRESSURE)
network.setManifoldPressure(80.0)  # 80 bara at manifold

# Solve the network
result = network.solve()

print(result.getSummaryTable())
```

## 3. Fixed Total Rate Solution

Find the manifold pressure that gives a target total rate:

```python
# Create new solver for fixed rate
network_rate = NetworkSolver("Rate_Constrained_Network")

for well, flowline_km in wells:
    network_rate.addWell(well, flowline_km)

# Set target total rate
network_rate.setSolutionMode(NetworkSolver.SolutionMode.FIXED_TOTAL_RATE)
network_rate.setTargetTotalRate(15.0e6)  # 15 MSm3/day target

# Solve
result_rate = network_rate.solve()

print(f"Required manifold pressure: {result_rate.manifoldPressure:.1f} bara")
print(f"Achieved total rate: {result_rate.getTotalRate('MSm3/day'):.2f} MSm3/day")
print()
print(result_rate.getSummaryTable())
```

## 4. Rate Allocation Optimization

Optimize rate allocation while respecting constraints:

```python
# Create solver for optimization
network_opt = NetworkSolver("Optimized_Network")

for well, flowline_km in wells:
    network_opt.addWell(well, flowline_km)

# Set optimization mode
network_opt.setSolutionMode(NetworkSolver.SolutionMode.OPTIMIZE_ALLOCATION)
network_opt.setMaxTotalRate(20.0e6)  # Max 20 MSm3/day processing capacity
network_opt.setManifoldPressure(70.0)  # Downstream constraint

# Solve
result_opt = network_opt.solve()

print("Optimized Allocation:")
print(result_opt.getSummaryTable())
```

## 5. Sensitivity Analysis: Manifold Pressure Sweep

```python
import matplotlib.pyplot as plt

# Sweep manifold pressure from 50 to 120 bara
pressures = range(50, 121, 10)
total_rates = []
well_rates = {well[0].getName(): [] for well in wells}

for p_manifold in pressures:
    network_sweep = NetworkSolver("Sweep")
    for well, flowline_km in wells:
        network_sweep.addWell(well, flowline_km)
    
    network_sweep.setSolutionMode(NetworkSolver.SolutionMode.FIXED_MANIFOLD_PRESSURE)
    network_sweep.setManifoldPressure(float(p_manifold))
    
    res = network_sweep.solve()
    total_rates.append(res.getTotalRate("MSm3/day"))
    
    for well, _ in wells:
        name = well.getName()
        well_rates[name].append(res.getWellRate(name, "MSm3/day"))

# Plot results
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

# Total rate vs manifold pressure
ax1.plot(pressures, total_rates, 'b-o', linewidth=2, markersize=8)
ax1.set_xlabel('Manifold Pressure (bara)', fontsize=12)
ax1.set_ylabel('Total Rate (MSm3/day)', fontsize=12)
ax1.set_title('Network Deliverability Curve', fontsize=14)
ax1.grid(True, alpha=0.3)
ax1.set_xlim(50, 120)

# Individual well rates
for name, rates in well_rates.items():
    ax2.plot(pressures, rates, '-o', label=name, linewidth=2)
ax2.set_xlabel('Manifold Pressure (bara)', fontsize=12)
ax2.set_ylabel('Well Rate (MSm3/day)', fontsize=12)
ax2.set_title('Individual Well Production', fontsize=14)
ax2.legend(loc='best')
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.show()
```

## 6. Production Decline Simulation

```python
# Simulate production decline over time
years = range(1, 21)  # 20 year simulation
annual_rates = []
annual_pressures = [220.0]  # Initial reservoir pressure

# Simple material balance decline
p_res = 220.0
compressibility = 1e-4  # 1/bar
gas_in_place = 50.0e9  # Sm3

for year in years:
    # Update well reservoir pressures
    network_sim = NetworkSolver(f"Year_{year}")
    for i, (well, flowline_km) in enumerate(wells):
        # Clone well with updated pressure
        well_updated = WellSystem(well.getName())
        well_updated.setReservoirPressure(p_res - i * 2, "bara")  # Offset per well
        well_updated.setReservoirTemperature(85.0, "C")
        well_updated.setProductivityIndex(well.getProductivityIndex())
        network_sim.addWell(well_updated, flowline_km)
    
    network_sim.setSolutionMode(NetworkSolver.SolutionMode.FIXED_MANIFOLD_PRESSURE)
    network_sim.setManifoldPressure(60.0)
    
    res = network_sim.solve()
    annual_rate = res.getTotalRate("MSm3/day") * 365  # MSm3/year
    annual_rates.append(annual_rate)
    
    # Update pressure based on production
    cumulative_production = sum(annual_rates)
    p_res = 220.0 * (1 - cumulative_production * 1e6 / gas_in_place)
    annual_pressures.append(p_res)

# Plot production decline
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8))

ax1.bar(years, annual_rates, color='steelblue', alpha=0.7)
ax1.set_xlabel('Year', fontsize=12)
ax1.set_ylabel('Annual Production (MSm3/year)', fontsize=12)
ax1.set_title('Production Forecast', fontsize=14)
ax1.grid(True, alpha=0.3)

ax2.plot(range(len(annual_pressures)), annual_pressures, 'r-o', linewidth=2)
ax2.set_xlabel('Year', fontsize=12)
ax2.set_ylabel('Average Reservoir Pressure (bara)', fontsize=12)
ax2.set_title('Pressure Decline', fontsize=14)
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.show()

print(f"Total recovery: {sum(annual_rates):.1f} MSm3")
print(f"Recovery factor: {sum(annual_rates) * 1e6 / gas_in_place * 100:.1f}%")
```

## Summary

The NetworkSolver provides:

| Mode | Description | Use Case |
|------|-------------|----------|
| FIXED_MANIFOLD_PRESSURE | Calculate rates for given backpressure | Normal operation |
| FIXED_TOTAL_RATE | Find backpressure for target rate | Constrained production |
| OPTIMIZE_ALLOCATION | Optimize well allocation | Maximize production |

Key features:
- Multiple wells with different IPRs
- Flowline pressure drop modeling
- Successive substitution solver
- Easy integration with reservoir models

