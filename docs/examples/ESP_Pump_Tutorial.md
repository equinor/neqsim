---
layout: default
title: "ESP Pump Tutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# ESP Pump Tutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`ESP_Pump_Tutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb).

---

## 1. Introduction to ESP Pumps <a name="introduction"></a>

**Electric Submersible Pumps (ESPs)** are multistage centrifugal pumps deployed downhole in oil wells to provide artificial lift. Key characteristics:

| Feature | Description |
|---------|-------------|
| **Multi-stage design** | 10-500+ stages for high total head |
| **Submersible motor** | Electric motor at bottom of assembly |
| **Gas handling** | Critical challenge - gas reduces performance |
| **Typical depth** | 1,000 - 4,000 m (3,000 - 13,000 ft) |
| **Flow rates** | 100 - 50,000 bbl/day |

### ESP Assembly Components
```
Surface ──────────────────────────────
    │
    │  Power cable
    │
    ├─── Pump discharge head
    │
    ├─── Multistage pump (main lift)
    │
    ├─── Gas separator (optional)
    │
    ├─── Intake section
    │
    ├─── Seal/protector section
    │
    └─── Submersible motor
```

## 2. Theory: GVF and Head Degradation <a name="theory"></a>

### Gas Void Fraction (GVF)

Gas Void Fraction is the volume fraction of gas at pump inlet conditions:

$$GVF = \frac{Q_{gas}}{Q_{gas} + Q_{liquid}}$$

Where $Q$ is volumetric flow rate at pump inlet P and T.

### Head Degradation Model

Free gas in the pump reduces developed head. NeqSim uses a quadratic degradation model:

$$f = 1 - A \cdot GVF - B \cdot GVF^2$$

Where:
- $f$ = head degradation factor (0 to 1)
- $A$ = linear coefficient (default: 0.5)
- $B$ = quadratic coefficient (default: 2.0)
- Effective head = $H_{design} \times f$

### Operating Limits

| Condition | Typical Threshold | Effect |
|-----------|-------------------|--------|
| **Surging** | GVF > 15% | Unstable operation, vibration |
| **Gas Lock** | GVF > 25-35% | Complete loss of pumping, well dies |

### Gas Separator Efficiency

A rotary gas separator can remove free gas before it enters the pump stages:

$$GVF_{effective} = GVF_{inlet} \times (1 - \eta_{sep})$$

Where $\eta_{sep}$ is separator efficiency (typically 50-80%).

## 3. Basic ESP Simulation <a name="basic"></a>

Let's start with a simple single-phase (liquid only) ESP simulation.

```python
# Import NeqSim Java classes via direct Java access
from neqsim import jneqsim

# Create a simple oil fluid (single phase liquid)
oil_fluid = jneqsim.thermo.system.SystemSrkEos(353.15, 30.0)  # 80°C, 30 bara
oil_fluid.addComponent("n-heptane", 1.0)  # Pure heptane as oil proxy
oil_fluid.setMixingRule("classic")

# Create inlet stream
inlet_stream = jneqsim.process.equipment.stream.Stream("Well Inflow", oil_fluid)
inlet_stream.setFlowRate(5000.0, "kg/hr")  # ~750 bbl/day
inlet_stream.setTemperature(80.0, "C")
inlet_stream.setPressure(30.0, "bara")
inlet_stream.run()

print(f"Inlet pressure: {inlet_stream.getPressure():.1f} bara")
print(f"Inlet temperature: {inlet_stream.getTemperature() - 273.15:.1f} °C")
print(f"Liquid density: {inlet_stream.getFluid().getDensity('kg/m3'):.1f} kg/m³")
```

```python
# Create ESP pump
esp = jneqsim.process.equipment.pump.ESPPump("ESP-1", inlet_stream)

# Configure ESP parameters
esp.setNumberOfStages(100)      # 100 impeller stages
esp.setHeadPerStage(8.0)        # 8 meters head per stage
esp.setIsentropicEfficiency(0.65)  # 65% hydraulic efficiency

# Run the simulation
esp.run()

# Get results
print("=== ESP Performance (Single Phase) ===")
print(f"Number of stages: {esp.getNumberOfStages()}")
print(f"Head per stage: {esp.getHeadPerStage():.1f} m")
print(f"Total design head: {esp.getNumberOfStages() * esp.getHeadPerStage():.0f} m")
print(f"")
print(f"Inlet pressure: {esp.getInletPressure():.1f} bara")
print(f"Outlet pressure: {esp.getOutletPressure():.1f} bara")
print(f"Pressure boost: {esp.getOutletPressure() - esp.getInletPressure():.1f} bar")
print(f"")
print(f"Shaft power: {esp.getPower('kW'):.2f} kW")
print(f"GVF at inlet: {esp.getGasVoidFraction() * 100:.1f}%")
print(f"Head degradation factor: {esp.getHeadDegradationFactor():.3f}")
```

## 4. Multiphase Flow with Gas Handling <a name="multiphase"></a>

Real well production contains dissolved gas that flashes as pressure drops. Let's simulate an ESP handling a gas-liquid mixture.

```python
# Create a realistic oil-gas mixture
well_fluid = jneqsim.thermo.system.SystemSrkEos(353.15, 25.0)  # 80°C, 25 bara

# Typical light oil composition with solution gas
well_fluid.addComponent("methane", 0.05)      # 5% solution gas
well_fluid.addComponent("ethane", 0.02)       # 2% ethane
well_fluid.addComponent("propane", 0.03)      # 3% propane  
well_fluid.addComponent("n-heptane", 0.50)    # 50% mid-weight oil
well_fluid.addComponent("n-nonane", 0.40)     # 40% heavier fraction

well_fluid.setMixingRule("classic")
well_fluid.setMultiPhaseCheck(True)  # Enable gas-liquid flash

# Create well inflow stream
well_stream = jneqsim.process.equipment.stream.Stream("Well Production", well_fluid)
well_stream.setFlowRate(8000.0, "kg/hr")  # ~1200 bbl/day
well_stream.setTemperature(80.0, "C")
well_stream.setPressure(25.0, "bara")  # Lower pressure = more gas breakout
well_stream.run()

# Check phase split
print("=== Well Production Stream ===")
print(f"Pressure: {well_stream.getPressure():.1f} bara")
print(f"Temperature: {well_stream.getTemperature() - 273.15:.1f} °C")
print(f"Number of phases: {well_stream.getFluid().getNumberOfPhases()}")

if well_stream.getFluid().hasPhaseType("gas"):
    gas_fraction = well_stream.getFluid().getPhase("gas").getVolume() / well_stream.getFluid().getVolume()
    print(f"Gas volume fraction: {gas_fraction * 100:.1f}%")
else:
    print("No free gas at inlet conditions")
```

```python
# Create ESP for multiphase handling
esp_multi = jneqsim.process.equipment.pump.ESPPump("ESP-Multiphase", well_stream)

# Configure ESP with gas handling parameters
esp_multi.setNumberOfStages(120)
esp_multi.setHeadPerStage(10.0)
esp_multi.setIsentropicEfficiency(0.60)

# Set operating limits
esp_multi.setMaxGVF(0.35)       # Gas lock at 35% GVF
esp_multi.setSurgingGVF(0.15)   # Surging starts at 15% GVF

# No gas separator initially
esp_multi.setHasGasSeparator(False)

esp_multi.run()

print("=== ESP with Multiphase Flow (No Separator) ===")
print(f"Inlet GVF: {esp_multi.getGasVoidFraction() * 100:.1f}%")
print(f"Head degradation factor: {esp_multi.getHeadDegradationFactor():.3f}")
print(f"")
print(f"Design head: {esp_multi.getNumberOfStages() * esp_multi.getHeadPerStage():.0f} m")
print(f"Effective head: {esp_multi.calculateTotalHead():.0f} m")
print(f"Head loss: {(1 - esp_multi.getHeadDegradationFactor()) * 100:.1f}%")
print(f"")
print(f"Pressure boost: {esp_multi.getOutletPressure() - esp_multi.getInletPressure():.1f} bar")
print(f"")
print(f"Is surging: {esp_multi.isSurging()}")
print(f"Is gas locked: {esp_multi.isGasLocked()}")
```

## 5. Gas Separator Modeling <a name="separator"></a>

Adding a rotary gas separator reduces GVF entering the pump stages.

```python
# Create a gassier fluid to show separator effect
gassy_fluid = jneqsim.thermo.system.SystemSrkEos(353.15, 20.0)  # Lower pressure
gassy_fluid.addComponent("methane", 0.12)     # 12% gas - more aggressive
gassy_fluid.addComponent("ethane", 0.03)
gassy_fluid.addComponent("n-heptane", 0.45)
gassy_fluid.addComponent("n-nonane", 0.40)
gassy_fluid.setMixingRule("classic")
gassy_fluid.setMultiPhaseCheck(True)

gassy_stream = jneqsim.process.equipment.stream.Stream("Gassy Well", gassy_fluid)
gassy_stream.setFlowRate(6000.0, "kg/hr")
gassy_stream.setTemperature(85.0, "C")
gassy_stream.setPressure(20.0, "bara")
gassy_stream.run()

# ESP WITHOUT gas separator
esp_no_sep = jneqsim.process.equipment.pump.ESPPump("ESP-NoSep", gassy_stream)
esp_no_sep.setNumberOfStages(100)
esp_no_sep.setHeadPerStage(10.0)
esp_no_sep.setMaxGVF(0.30)
esp_no_sep.setSurgingGVF(0.12)
esp_no_sep.setHasGasSeparator(False)
esp_no_sep.run()

print("=== ESP WITHOUT Gas Separator ===")
print(f"Inlet GVF: {esp_no_sep.getGasVoidFraction() * 100:.1f}%")
print(f"Head degradation: {esp_no_sep.getHeadDegradationFactor():.3f}")
print(f"Is surging: {esp_no_sep.isSurging()}")
print(f"Is gas locked: {esp_no_sep.isGasLocked()}")
print(f"Pressure boost: {esp_no_sep.getOutletPressure() - esp_no_sep.getInletPressure():.1f} bar")
```

```python
# ESP WITH gas separator (re-run the stream)
gassy_stream.run()

esp_with_sep = jneqsim.process.equipment.pump.ESPPump("ESP-WithSep", gassy_stream)
esp_with_sep.setNumberOfStages(100)
esp_with_sep.setHeadPerStage(10.0)
esp_with_sep.setMaxGVF(0.30)
esp_with_sep.setSurgingGVF(0.12)

# Enable gas separator with 70% efficiency
esp_with_sep.setHasGasSeparator(True)
esp_with_sep.setGasSeparatorEfficiency(0.70)  # 70% of free gas removed

esp_with_sep.run()

print("=== ESP WITH Gas Separator (70% efficiency) ===")
print(f"Inlet GVF (raw): {esp_no_sep.getGasVoidFraction() * 100:.1f}%")  # Same as before separator
print(f"Effective GVF (after separator): ~{esp_no_sep.getGasVoidFraction() * 0.30 * 100:.1f}%")
print(f"Head degradation: {esp_with_sep.getHeadDegradationFactor():.3f}")
print(f"Is surging: {esp_with_sep.isSurging()}")
print(f"Is gas locked: {esp_with_sep.isGasLocked()}")
print(f"Pressure boost: {esp_with_sep.getOutletPressure() - esp_with_sep.getInletPressure():.1f} bar")
print()
print(f"=== Improvement from Gas Separator ===")
boost_no_sep = esp_no_sep.getOutletPressure() - esp_no_sep.getInletPressure()
boost_with_sep = esp_with_sep.getOutletPressure() - esp_with_sep.getInletPressure()
print(f"Pressure boost increase: {boost_with_sep - boost_no_sep:.1f} bar ({(boost_with_sep/boost_no_sep - 1)*100:.0f}% improvement)")
```

## 6. Operating Limits and Diagnostics <a name="diagnostics"></a>

Let's explore ESP behavior across different GVF conditions.

```python
import matplotlib.pyplot as plt
import numpy as np

# Generate head degradation curve
gvf_range = np.linspace(0, 0.40, 50)

# Default coefficients: A=0.5, B=2.0
A, B = 0.5, 2.0
degradation = 1 - A * gvf_range - B * gvf_range**2
degradation = np.maximum(degradation, 0)  # Can't go negative

# Create plot
fig, ax = plt.subplots(figsize=(10, 6))

ax.plot(gvf_range * 100, degradation * 100, 'b-', linewidth=2, label='Head Degradation Factor')

# Mark operating zones
ax.axvline(x=15, color='orange', linestyle='--', linewidth=1.5, label='Surging Onset (15%)')
ax.axvline(x=30, color='red', linestyle='--', linewidth=1.5, label='Gas Lock Threshold (30%)')

# Fill zones
ax.axvspan(0, 15, alpha=0.2, color='green', label='Normal Operation')
ax.axvspan(15, 30, alpha=0.2, color='orange', label='Surging Zone')
ax.axvspan(30, 40, alpha=0.2, color='red', label='Gas Lock Zone')

ax.set_xlabel('Gas Void Fraction (%)', fontsize=12)
ax.set_ylabel('Head Degradation Factor (%)', fontsize=12)
ax.set_title('ESP Head Degradation vs Gas Void Fraction', fontsize=14)
ax.set_xlim(0, 40)
ax.set_ylim(0, 105)
ax.grid(True, alpha=0.3)
ax.legend(loc='lower left')

plt.tight_layout()
plt.show()
```

```python
# Parametric study: vary inlet pressure to change GVF
pressures = [35, 30, 25, 20, 15, 12, 10]  # bara

results = []
for p in pressures:
    # Create fluid at each pressure
    test_fluid = jneqsim.thermo.system.SystemSrkEos(353.15, float(p))
    test_fluid.addComponent("methane", 0.10)
    test_fluid.addComponent("ethane", 0.03)
    test_fluid.addComponent("n-heptane", 0.47)
    test_fluid.addComponent("n-nonane", 0.40)
    test_fluid.setMixingRule("classic")
    test_fluid.setMultiPhaseCheck(True)
    
    test_stream = jneqsim.process.equipment.stream.Stream("test", test_fluid)
    test_stream.setFlowRate(5000.0, "kg/hr")
    test_stream.setTemperature(80.0, "C")
    test_stream.setPressure(float(p), "bara")
    test_stream.run()
    
    # Run ESP
    esp_test = jneqsim.process.equipment.pump.ESPPump("test", test_stream)
    esp_test.setNumberOfStages(100)
    esp_test.setHeadPerStage(10.0)
    esp_test.setMaxGVF(0.35)
    esp_test.setSurgingGVF(0.15)
    esp_test.setHasGasSeparator(False)
    esp_test.run()
    
    results.append({
        'pressure': p,
        'gvf': esp_test.getGasVoidFraction() * 100,
        'degradation': esp_test.getHeadDegradationFactor() * 100,
        'surging': esp_test.isSurging(),
        'gas_locked': esp_test.isGasLocked(),
        'dp': esp_test.getOutletPressure() - esp_test.getInletPressure()
    })

# Display results
print("=== ESP Performance vs Inlet Pressure ===")
print(f"{'P_in':>6} {'GVF':>8} {'Degrad':>8} {'ΔP':>8} {'Status':>15}")
print(f"{'(bara)':>6} {'(%)':>8} {'(%)':>8} {'(bar)':>8} {'':>15}")
print("-" * 50)

for r in results:
    status = "GAS LOCK" if r['gas_locked'] else ("SURGING" if r['surging'] else "Normal")
    print(f"{r['pressure']:>6.0f} {r['gvf']:>8.1f} {r['degradation']:>8.1f} {r['dp']:>8.1f} {status:>15}")
```

```python
# Plot pressure boost vs inlet pressure
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

pressures_plot = [r['pressure'] for r in results]
gvf_plot = [r['gvf'] for r in results]
dp_plot = [r['dp'] for r in results]
degradation_plot = [r['degradation'] for r in results]

# Left plot: GVF vs pressure
ax1.plot(pressures_plot, gvf_plot, 'bo-', linewidth=2, markersize=8)
ax1.axhline(y=15, color='orange', linestyle='--', label='Surging threshold')
ax1.axhline(y=30, color='red', linestyle='--', label='Gas lock threshold')
ax1.set_xlabel('Inlet Pressure (bara)', fontsize=12)
ax1.set_ylabel('Gas Void Fraction (%)', fontsize=12)
ax1.set_title('GVF Increases as Pressure Drops', fontsize=14)
ax1.grid(True, alpha=0.3)
ax1.legend()
ax1.invert_xaxis()  # Higher pressure on left

# Right plot: Pressure boost vs inlet pressure
colors = ['green' if not r['surging'] and not r['gas_locked'] 
          else 'orange' if r['surging'] and not r['gas_locked']
          else 'red' for r in results]
ax2.bar(pressures_plot, dp_plot, color=colors, alpha=0.7, edgecolor='black')
ax2.set_xlabel('Inlet Pressure (bara)', fontsize=12)
ax2.set_ylabel('Pressure Boost (bar)', fontsize=12)
ax2.set_title('ESP Pressure Boost Degrades with Gas', fontsize=14)
ax2.grid(True, alpha=0.3, axis='y')

# Add legend for colors
from matplotlib.patches import Patch
legend_elements = [Patch(facecolor='green', alpha=0.7, label='Normal'),
                   Patch(facecolor='orange', alpha=0.7, label='Surging'),
                   Patch(facecolor='red', alpha=0.7, label='Gas Lock')]
ax2.legend(handles=legend_elements)

plt.tight_layout()
plt.show()
```

## Summary

This notebook demonstrated ESP pump modeling in NeqSim:

| Capability | Description |
|------------|-------------|
| **Multi-stage design** | Configurable stages and head per stage |
| **GVF calculation** | Automatic from multiphase thermodynamics |
| **Head degradation** | Quadratic model based on GVF |
| **Gas separator** | Optional with configurable efficiency |
| **Operating diagnostics** | Surging and gas lock detection |

### Key API Methods

```python
# Create ESP
esp = jneqsim.process.equipment.pump.ESPPump(name, inlet_stream)

# Configure
esp.setNumberOfStages(100)
esp.setHeadPerStage(10.0)  # meters
esp.setMaxGVF(0.30)        # gas lock threshold
esp.setSurgingGVF(0.15)    # surging onset
esp.setHasGasSeparator(True)
esp.setGasSeparatorEfficiency(0.70)

# Run and query
esp.run()
gvf = esp.getGasVoidFraction()
degradation = esp.getHeadDegradationFactor()
is_surging = esp.isSurging()
is_locked = esp.isGasLocked()
```

