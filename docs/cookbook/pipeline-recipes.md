---
title: Pipeline Recipes
description: Quick recipes for pipeline simulation in NeqSim - pressure drop, multiphase flow, slugging, and terrain effects.
---

# Pipeline Recipes

Copy-paste solutions for pipeline calculations and multiphase flow.

## Table of Contents

- [Simple Pressure Drop](#simple-pressure-drop)
- [Multiphase Pipelines](#multiphase-pipelines)
- [Heat Transfer](#heat-transfer)
- [Terrain Effects](#terrain-effects)
- [Flow Regimes](#flow-regimes)

---

## Simple Pressure Drop

### Adiabatic Pipe (Single Phase or Simple Multiphase)

```python
from neqsim import jneqsim

AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe

# Assume inlet_stream exists
pipe = AdiabaticPipe("Pipeline", inlet_stream)
pipe.setLength(10000)          # 10 km
pipe.setDiameter(0.3)          # 300 mm ID
pipe.setPipeWallRoughness(5e-5) # m (smooth steel)

process.add(pipe)
process.run()

# Results
inlet_P = inlet_stream.getPressure()
outlet_P = pipe.getOutletStream().getPressure()
dp = inlet_P - outlet_P

print(f"Inlet P: {inlet_P:.2f} bara")
print(f"Outlet P: {outlet_P:.2f} bara")
print(f"Pressure drop: {dp:.2f} bar")
print(f"ΔP/L: {dp / 10:.3f} bar/km")
```

### Set Elevation Change

```python
pipe = AdiabaticPipe("Riser", inlet_stream)
pipe.setLength(500)           # 500 m
pipe.setDiameter(0.2)
pipe.setInletElevation(0)     # Start at sea level
pipe.setOutletElevation(100)  # 100 m elevation gain
```

---

## Multiphase Pipelines

### Beggs and Brill Correlation

```python
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills

pipe = PipeBeggsAndBrills("Export Pipeline", inlet_stream)
pipe.setLength(50000)          # 50 km
pipe.setDiameter(0.4)          # 400 mm
pipe.setAngle(0)               # Horizontal (degrees from horizontal)
pipe.setPipeWallRoughness(4.5e-5)

process.add(pipe)
process.run()

# Multiphase results
print(f"Outlet pressure: {pipe.getOutletStream().getPressure():.2f} bara")
print(f"Liquid holdup: {pipe.getPressureDrop():.2f} bar")  # Total DP
```

### Two-Fluid Model (Detailed)

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

pipe = TwoFluidPipe("Flowline", inlet_stream)
pipe.setLength(20000)
pipe.setDiameter(0.25)
pipe.setNumberOfNodes(100)     # Discretization
pipe.setOuterTemperature(278.15)  # 5°C ambient

process.add(pipe)
process.run()

# Get detailed profiles
# positions = pipe.getPositions()
# pressures = pipe.getPressures()
# holdups = pipe.getLiquidHoldups()
```

---

## Heat Transfer

### Pipeline with Heat Loss

```python
pipe = PipeBeggsAndBrills("Subsea Flowline", inlet_stream)
pipe.setLength(30000)
pipe.setDiameter(0.3)

# Heat transfer
pipe.setOuterTemperature(277.15)  # 4°C seawater
pipe.setPipeHeatTransferCoefficient(10.0)  # W/m²K (overall U-value)

process.add(pipe)
process.run()

inlet_T = inlet_stream.getTemperature() - 273.15
outlet_T = pipe.getOutletStream().getTemperature() - 273.15
print(f"Temperature drop: {inlet_T - outlet_T:.1f} °C")
```

### Insulated Pipeline

```python
# Higher insulation = lower U-value
pipe.setPipeHeatTransferCoefficient(2.0)  # W/m²K (well insulated)
```

### Buried Pipeline

```python
pipe.setOuterTemperature(283.15)  # 10°C soil temperature
pipe.setPipeHeatTransferCoefficient(5.0)  # W/m²K (buried)
```

---

## Terrain Effects

### Pipeline with Elevation Profile

```python
# For more complex terrain, use TwoFluidPipe with segments
# or set overall elevation change

pipe = PipeBeggsAndBrills("Mountain Pipeline", inlet_stream)
pipe.setLength(10000)
pipe.setDiameter(0.3)

# Set inclination angle (degrees from horizontal)
# Positive = uphill, Negative = downhill
pipe.setAngle(5)  # 5° uphill

# Or set specific elevations
pipe.setInletElevation(0)
pipe.setOutletElevation(870)  # ~870 m rise for 5° over 10 km
```

### Riser (Vertical Section)

```python
riser = PipeBeggsAndBrills("Production Riser", flowline_outlet)
riser.setLength(200)       # 200 m water depth
riser.setDiameter(0.25)
riser.setAngle(90)         # Vertical

process.add(riser)
```

---

## Flow Regimes

### Detect Flow Regime

```python
# After running pipe calculation
pipe.run()

# Get flow pattern (Beggs & Brill)
flow_pattern = pipe.getFlowRegime()
print(f"Flow regime: {flow_pattern}")

# Common regimes:
# - "Segregated" (stratified)
# - "Intermittent" (slug/plug)
# - "Distributed" (bubble/mist)
# - "Transition"
```

### Slug Flow Parameters

```python
# For two-fluid model with slug tracking
pipe = TwoFluidPipe("Slugging Line", inlet_stream)
pipe.setLength(5000)
pipe.setDiameter(0.15)
pipe.setNumberOfNodes(200)

process.add(pipe)
process.run()

# Check for slugging conditions
# liquid_holdup = pipe.getAverageLiquidHoldup()
# superficial_gas_vel = pipe.getSuperficialGasVelocity()
# superficial_liq_vel = pipe.getSuperficialLiquidVelocity()
```

---

## Pipeline Network Example

```python
from neqsim import jneqsim

# Imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
Mixer = jneqsim.process.equipment.mixer.Mixer

# Create fluids for two wells
fluid1 = SystemSrkEos(273.15 + 80, 150.0)
fluid1.addComponent("methane", 0.85)
fluid1.addComponent("ethane", 0.10)
fluid1.addComponent("propane", 0.05)
fluid1.setMixingRule("classic")

fluid2 = fluid1.clone()

# Build network
process = ProcessSystem()

# Well 1 stream
well1 = Stream("Well-1", fluid1)
well1.setFlowRate(30000, "kg/hr")
process.add(well1)

# Well 1 flowline
flowline1 = PipeBeggsAndBrills("FL-1", well1)
flowline1.setLength(5000)
flowline1.setDiameter(0.2)
process.add(flowline1)

# Well 2 stream  
well2 = Stream("Well-2", fluid2)
well2.setFlowRate(20000, "kg/hr")
process.add(well2)

# Well 2 flowline
flowline2 = PipeBeggsAndBrills("FL-2", well2)
flowline2.setLength(8000)
flowline2.setDiameter(0.2)
process.add(flowline2)

# Manifold
manifold = Mixer("Manifold")
manifold.addStream(flowline1.getOutletStream())
manifold.addStream(flowline2.getOutletStream())
process.add(manifold)

# Export line
export = PipeBeggsAndBrills("Export", manifold.getOutletStream())
export.setLength(25000)
export.setDiameter(0.4)
process.add(export)

# Run
process.run()

# Results
print(f"Manifold P: {manifold.getOutletStream().getPressure():.2f} bara")
print(f"Export outlet P: {export.getOutletStream().getPressure():.2f} bara")
print(f"Total flow: {export.getOutletStream().getFlowRate('kg/hr'):.0f} kg/hr")
```

---

## Pressure Drop Correlations

| Correlation | Best For | NeqSim Class |
|-------------|----------|--------------|
| **Beggs & Brill** | General multiphase | `PipeBeggsAndBrills` |
| **Two-Fluid** | Detailed transient, slugging | `TwoFluidPipe` |
| **Adiabatic** | Single phase, quick estimates | `AdiabaticPipe` |

---

## See Also

- **[Pipeline Documentation](../process/equipment/pipelines.md)** - Detailed pipeline guide
- **[Beggs and Brill](../wiki/beggs_and_brill_correlation.md)** - Correlation details
- **[Two-Fluid Model](../wiki/two_fluid_model.md)** - Advanced model
- **[TwoFluidPipe Tutorial](../examples/TwoFluidPipe_Tutorial.md)** - Complete example
- **[JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)** - Complete reference
