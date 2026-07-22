---
title: "Connecting NeqSim to Plant Data with Tagreader"
description: "Guide to integrating NeqSim process simulations with real plant historian data (OSIsoft PI, Aspen IP.21) using tagreader-python. Covers the NeqSimLive development workflow from local model building through live digital twin deployment."
---

# Connecting NeqSim to Plant Data with Tagreader

This guide describes how to connect NeqSim process simulations to real operational data
from plant historians using the [tagreader-python](https://github.com/equinor/tagreader-python)
package. This is the foundation of the **NeqSimLive** workflow for building live digital twins.

## Overview

A live process model (digital twin) combines a physics-based NeqSim simulation with
real-time plant data to continuously track operations and predict future behavior.

```
┌──────────────────────────────────────────────────────────────────────┐
│  NeqSimLive Development Workflow                                     │
│                                                                      │
│  Step 1: Build Process Model (NeqSim Python)                        │
│      │                                                               │
│  Step 2: Read Plant Data (tagreader → PI/IP.21)                     │
│      │                                                               │
│  Step 3: Compare Model vs Plant → Tune                              │
│      │                                                               │
│  Step 4: Digital Twin Loop (continuous update)                       │
│      │                                                               │
│  Step 5: Deploy via NeqSimAPI (Cloud REST)                          │
│      │                                                               │
│  Step 6: Live Online Model (Sigma / IOC CalcEngine)                 │
│      │                                                               │
│  Step 7: What-If Scenarios & Prediction                             │
└──────────────────────────────────────────────────────────────────────┘
```

This guide covers **Steps 1-4** (local development). Steps 5-7 require cloud
infrastructure (NeqSimAPI, Sigma).

## Prerequisites

```bash
pip install neqsim tagreader
```

- **neqsim** — Thermodynamic and process simulation
- **tagreader** — Python package for reading OSIsoft PI and Aspen IP.21 data
- Access to a PI or IP.21 data source (or use mock data for testing)

## Step 1: Build the Process Model

Create a NeqSim process simulation that matches the real plant topology.

```python
from neqsim import jneqsim

# Create class aliases
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

# Define gas composition matching plant feed
fluid = SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.07)
fluid.addComponent("propane", 0.04)
fluid.addComponent("i-butane", 0.01)
fluid.addComponent("n-butane", 0.01)
fluid.addComponent("nitrogen", 0.01)
fluid.addComponent("CO2", 0.01)
fluid.setMixingRule("classic")

# Build process matching plant P&ID
feed = Stream("Feed Stream", fluid)
feed.setFlowRate(100000.0, "kg/hr")
feed.setTemperature(15.0, "C")
feed.setPressure(29.0, "bara")

sep = Separator("Inlet Sep", feed)
comp = Compressor("Export Comp", sep.getGasOutStream())
comp.setOutletPressure(90.0, "bara")

aftercooler = Cooler("Aftercooler", comp.getOutletStream())
aftercooler.setOutTemperature(273.15 + 30.0)

process = ProcessSystem()
process.add(feed)
process.add(sep)
process.add(comp)
process.add(aftercooler)
process.run()

print(f"Compressor power: {float(comp.getPower('MW')):.2f} MW")
print(f"Discharge T: {float(comp.getOutletStream().getTemperature('C')):.1f} °C")
```

## Step 2: Read Plant Data with Tagreader

### Discover Available Data Sources

```python
import tagreader

# List available PI servers
pi_sources = tagreader.list_sources("piwebapi")
print("PI sources:", pi_sources)

# List available Aspen IP.21 servers
aspen_sources = tagreader.list_sources("aspenone")
print("Aspen sources:", aspen_sources)
```

### Connect to a Historian

```python
# Connect to OSIsoft PI
c = tagreader.IMSClient("PINO", "piwebapi")
c.connect()

# — or —

# Connect to Aspen IP.21
c = tagreader.IMSClient("TRA", "aspenone")
c.connect()
```

### Search for Tags

```python
# Search by name pattern
results = c.search("*35PT36*")
for tag_name, description in results:
    print(f"  {tag_name:50s}  {description}")

# Search by description
results = c.search(desc="*compressor*suction*")
```

### Define Tag Mapping

Map logical process parameters to historian tag names. This is the key data
contract between the process model and the real plant:

```python
TAG_MAP = {
    # Inlet separator
    'inlet_pressure':       'TRA-20PT0118.PV',
    'inlet_temperature':    'TRA-20TT0124.PV',

    # Compressor A (Tog A)
    'compA_suction_P':      'TRA-35PT3601A.PV',
    'compA_discharge_P':    'TRA-35PT3601B.PV',
    'compA_suction_T':      'TRA-35TT3601A.PV',
    'compA_discharge_T':    'TRA-35TT3601B.PV',
    'compA_aftercool_T':    'TRA-35TT3646.PV',
    'compA_flow':           'TRA-35FT3601.PV',
    'compA_power':          'TRA-35JI3191F000.PV',
    'compA_speed':          'TRA-35SI3152.PV',

    # Export
    'export_pressure':      'TRA-27PT0004.PV',
    'export_flow':          'TRA-27FT0006.PV',
}
```

### Read Historical Data

```python
import pandas as pd

tags = list(TAG_MAP.values())
df_plant = c.read(
    tags,
    "01.06.2025 06:00:00",   # start
    "01.06.2025 18:00:00",   # end
    300                       # 5-minute intervals
)

print(f"Read {len(df_plant)} rows x {len(df_plant.columns)} tags")
```

### Using Mock Data (No Historian Access)

When you don't have access to a historian, generate realistic mock data to
develop and test the workflow:

```python
import numpy as np

timestamps = pd.date_range("2025-06-01 06:00", "2025-06-01 18:00", freq='5min')
np.random.seed(42)
n = len(timestamps)

df_plant = pd.DataFrame({
    TAG_MAP['compA_suction_P']:   np.random.normal(29.0, 0.2, n),
    TAG_MAP['compA_discharge_P']: np.random.normal(90.0, 1.0, n),
    TAG_MAP['compA_suction_T']:   np.random.normal(15.0, 0.5, n),
    TAG_MAP['compA_discharge_T']: np.random.normal(95.0, 2.0, n),
    TAG_MAP['compA_power']:       np.random.normal(8.5, 0.3, n),
    TAG_MAP['compA_speed']:       np.random.normal(9500, 100, n),
    TAG_MAP['export_pressure']:   np.random.normal(88.0, 1.0, n),
}, index=timestamps)
```

## Step 3: Compare Model to Plant Data

### Extract Operating Conditions

```python
import math

def get_plant_value(df, tag_map, param_name, aggregation='mean'):
    """Get a plant value by logical parameter name."""
    tag = tag_map[param_name]
    if tag not in df.columns:
        return float('nan')
    series = df[tag].dropna()
    if len(series) == 0:
        return float('nan')
    agg_funcs = {'mean': series.mean, 'last': lambda: series.iloc[-1],
                 'median': series.median, 'min': series.min, 'max': series.max}
    return float(agg_funcs.get(aggregation, series.mean)())

# Extract boundary conditions
plant_suction_P = get_plant_value(df_plant, TAG_MAP, 'compA_suction_P')
plant_suction_T = get_plant_value(df_plant, TAG_MAP, 'compA_suction_T')
plant_discharge_P = get_plant_value(df_plant, TAG_MAP, 'compA_discharge_P')
```

### Re-Run Model with Plant Boundary Conditions

```python
# Update inlet with plant data
inlet = process.getUnit("Feed Stream")
inlet.setPressure(float(plant_suction_P), "bara")
inlet.setTemperature(float(plant_suction_T), "C")

# Update compressor discharge from plant
comp_unit = process.getUnit("Export Comp")
comp_unit.setOutletPressure(float(plant_discharge_P), "bara")

process.run()
```

### Build Comparison Table

```python
comparison = []
sim_vals = {
    'Suction P (bara)': float(comp_unit.getInletStream().getPressure('bara')),
    'Discharge P (bara)': float(comp_unit.getOutletStream().getPressure('bara')),
    'Discharge T (°C)': float(comp_unit.getOutletStream().getTemperature('C')),
    'Power (MW)': float(comp_unit.getPower('MW')),
}
plant_vals = {
    'Suction P (bara)': plant_suction_P,
    'Discharge P (bara)': plant_discharge_P,
    'Discharge T (°C)': get_plant_value(df_plant, TAG_MAP, 'compA_discharge_T'),
    'Power (MW)': get_plant_value(df_plant, TAG_MAP, 'compA_power'),
}

for param in sim_vals:
    pv, sv = plant_vals[param], sim_vals[param]
    if not math.isnan(pv):
        comparison.append({
            'Parameter': param,
            'Plant': round(pv, 2),
            'Simulated': round(sv, 2),
            'Delta': round(sv - pv, 2),
            'Delta %': round((sv - pv) / max(abs(pv), 0.01) * 100, 1),
        })

df_cmp = pd.DataFrame(comparison)
print(df_cmp.to_string(index=False))
```

Large deltas indicate where the model needs tuning (e.g., adjusting compressor
polytopic efficiency, updating fluid composition, or accounting for fouling).

## Step 4: Digital Twin Loop

Run the model at every timestep to create a continuous tracking record:

```python
results = []
inlet_stream = process.getUnit("Feed Stream")
comp = process.getUnit("Export Comp")

STEP = 1  # Process every N-th row (increase for speed)

for i in range(0, len(df_plant), STEP):
    row = df_plant.iloc[i]
    ts = df_plant.index[i]

    p_in = float(row.get(TAG_MAP['compA_suction_P'], float('nan')))
    t_in = float(row.get(TAG_MAP['compA_suction_T'], float('nan')))

    # Skip bad data
    if math.isnan(p_in) or p_in <= 0 or math.isnan(t_in) or t_in < -50:
        continue

    inlet_stream.setPressure(p_in, "bara")
    inlet_stream.setTemperature(t_in, "C")

    try:
        process.run()
        results.append({
            'timestamp': ts,
            'sim_power_MW': float(comp.getPower('MW')),
            'plant_power_MW': float(row.get(TAG_MAP['compA_power'], float('nan'))),
            'sim_discharge_T': float(comp.getOutletStream().getTemperature('C')),
            'plant_discharge_T': float(row.get(TAG_MAP['compA_discharge_T'], float('nan'))),
        })
    except Exception as e:
        pass  # Skip failed timesteps

df_results = pd.DataFrame(results)
print(f"Completed {len(df_results)} timesteps")
```

### Visualize Results

```python
import matplotlib.pyplot as plt

fig, axes = plt.subplots(2, 1, figsize=(14, 8), sharex=True)

# Power comparison
ax = axes[0]
ax.plot(df_results['timestamp'], df_results['plant_power_MW'], 'b.-', label='Plant')
ax.plot(df_results['timestamp'], df_results['sim_power_MW'], 'r.-', label='NeqSim')
ax.set_ylabel('Power (MW)')
ax.set_title('Digital Twin: Compressor Power')
ax.legend()
ax.grid(True, alpha=0.3)

# Temperature comparison
ax = axes[1]
ax.plot(df_results['timestamp'], df_results['plant_discharge_T'], 'b.-', label='Plant')
ax.plot(df_results['timestamp'], df_results['sim_discharge_T'], 'r.-', label='NeqSim')
ax.set_ylabel('Discharge T (°C)')
ax.set_xlabel('Time')
ax.legend()
ax.grid(True, alpha=0.3)

plt.tight_layout()
plt.show()
```

### Model Accuracy Metrics

```python
power_error = (df_results['sim_power_MW'] - df_results['plant_power_MW']).abs()
print(f"Mean Absolute Error: {power_error.mean():.3f} MW")
print(f"Max Error:           {power_error.max():.3f} MW")
print(f"MAPE:                {power_error.mean() / df_results['plant_power_MW'].mean() * 100:.1f}%")
```

## Steps 5-7: Production Deployment

### Step 5: NeqSimAPI (Cloud REST)

Deploy the validated model as a REST API:
- [NeqSimAPI Documentation](https://neqsimapi.app.radix.equinor.com/docs)
- Model accepts JSON inputs, returns simulation results
- Runs in Equinor's Radix cloud platform

### Step 6: Live Digital Twin

Connect the API to live plant data:
- **Sigma** — automatically reads PI/Aspen tags, calls NeqSimAPI, writes results back
- **IOC CalcEngine** — alternative connector for real-time execution

```
PI/Aspen → Sigma/CalcEngine → NeqSimAPI → Omnia TimeSeries → PI/Aspen
```

### Step 7: What-If Scenarios

Use the calibrated model for prediction:
- Capacity at future reservoir pressures
- Equipment modifications (what if we add a cooler?)
- VFP table generation for reservoir simulation

## Tagreader Quick Reference

| Operation | Code |
|-----------|------|
| List PI sources | `tagreader.list_sources("piwebapi")` |
| List Aspen sources | `tagreader.list_sources("aspenone")` |
| Connect | `c = tagreader.IMSClient("NAME", "piwebapi"); c.connect()` |
| Search tags | `c.search("*pattern*")` or `c.search(desc="*keyword*")` |
| Read interpolated | `c.read(tags, start, end, interval)` |
| Read average | `c.read(tags, start, end, interval, read_type=tagreader.ReaderType.AVG)` |
| Read raw | `c.read(tags, start, end, read_type=tagreader.ReaderType.RAW)` |
| Read snapshot | `c.read([tag], read_type=tagreader.ReaderType.SNAPSHOT)` |
| With quality flags | `c.read(tags, start, end, 60, get_status=True)` |

## Related Documentation

- [Digital Twin Integration Guide](digital-twin-integration.md) — Architecture for production digital twins
- [Data Reconciliation & Parameter Estimation](../calibration/data_reconciliation_parameter_estimation.md) — Model tuning against plant data
- [tagreader-python documentation](https://equinor.github.io/tagreader-python/)
- [NeqSimAPI](https://neqsimapi.app.radix.equinor.com/docs)

## Contact Persons

| Role | Contact |
|------|---------|
| NeqSim | Sviatoslav Eroshkin / Even Solbraa |
| NeqSimAPI | Jørgen Holstad Engelsen |
| IOC/TagReader | Åsmund Våge Fannemel |
| Sigma | Gisle Otto Eikrem |
