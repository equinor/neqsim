---
title: Process Recipes
description: Quick recipes for process simulation in NeqSim - separators, compressors, heat exchangers, flowsheets, and more.
---

# Process Recipes

Copy-paste solutions for common process simulation tasks.

## Table of Contents

- [Streams](#streams)
- [Separators](#separators)
- [Compressors and Expanders](#compressors-and-expanders)
- [Heat Exchangers](#heat-exchangers)
- [Valves](#valves)
- [Flowsheet Building](#flowsheet-building)
- [Recycles and Adjusters](#recycles-and-adjusters)

---

## Streams

### Create a Feed Stream

```python
from neqsim import jneqsim

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 30, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Create stream
stream = jneqsim.process.equipment.stream.Stream("Feed", fluid)
stream.setFlowRate(10000, "kg/hr")  # Mass flow
# Or: stream.setFlowRate(500, "MSm3/day")  # Standard volume flow

stream.run()
print(f"Molar flow: {stream.getFlowRate('mol/hr'):.0f} mol/hr")
```

### Set Stream Conditions

```python
stream.setTemperature(30.0, "C")    # Celsius
stream.setPressure(50.0, "bara")   # bara
stream.setFlowRate(5000, "kg/hr")  # Mass flow

# Alternative units
stream.setTemperature(86.0, "F")   # Fahrenheit
stream.setPressure(725, "psia")    # psia
stream.setFlowRate(10, "MSm3/day") # Standard m³/day
```

---

## Separators

### Two-Phase Separator

```python
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator

# Setup (assume feed stream exists)
process = ProcessSystem()
process.add(feed)

# Add separator
separator = Separator("HP Separator", feed)
separator.setInternalDiameter(2.0)  # meters (optional)
process.add(separator)

process.run()

# Get outlet streams
gas_out = separator.getGasOutStream()
liquid_out = separator.getLiquidOutStream()

print(f"Gas: {gas_out.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid: {liquid_out.getFlowRate('kg/hr'):.0f} kg/hr")
```

### Three-Phase Separator

```python
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator

separator = ThreePhaseSeparator("3-Phase Sep", feed)
process.add(separator)
process.run()

gas_out = separator.getGasOutStream()
oil_out = separator.getOilOutStream()
water_out = separator.getWaterOutStream()
```

---

## Compressors and Expanders

### Compressor with Efficiency

```python
Compressor = jneqsim.process.equipment.compressor.Compressor

compressor = Compressor("K-100", gas_stream)
compressor.setOutletPressure(100.0)  # bara
compressor.setIsentropicEfficiency(0.75)  # 75%
compressor.setPolytropicEfficiency(0.80)  # Alternative: polytropic
process.add(compressor)

process.run()

print(f"Power: {compressor.getPower('kW'):.1f} kW")
print(f"Outlet T: {compressor.getOutletStream().getTemperature() - 273.15:.1f} °C")
print(f"Head: {compressor.getPolytropicHead('kJ/kg'):.1f} kJ/kg")
```

### Multi-Stage Compression with Intercooling

```python
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

# Stage 1
comp1 = Compressor("K-100A", gas_stream)
comp1.setOutletPressure(30.0)
comp1.setIsentropicEfficiency(0.75)
process.add(comp1)

# Intercooler
cooler1 = Cooler("E-100", comp1.getOutletStream())
cooler1.setOutTemperature(273.15 + 40)  # 40°C
process.add(cooler1)

# Stage 2
comp2 = Compressor("K-100B", cooler1.getOutletStream())
comp2.setOutletPressure(100.0)
comp2.setIsentropicEfficiency(0.75)
process.add(comp2)

process.run()

total_power = comp1.getPower("kW") + comp2.getPower("kW")
print(f"Total power: {total_power:.1f} kW")
```

### Expander

```python
Expander = jneqsim.process.equipment.expander.Expander

expander = Expander("Turbo-Expander", high_pressure_stream)
expander.setOutletPressure(20.0)  # bara
expander.setIsentropicEfficiency(0.85)
process.add(expander)

process.run()

print(f"Power generated: {-expander.getPower('kW'):.1f} kW")
print(f"Outlet T: {expander.getOutletStream().getTemperature() - 273.15:.1f} °C")
```

---

## Heat Exchangers

### Heater (Specified Outlet Temperature)

```python
Heater = jneqsim.process.equipment.heatexchanger.Heater

heater = Heater("E-100", inlet_stream)
heater.setOutTemperature(273.15 + 80)  # 80°C
process.add(heater)

process.run()

print(f"Duty: {heater.getDuty() / 1000:.1f} kW")
```

### Cooler (Specified Outlet Temperature)

```python
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

cooler = Cooler("E-101", inlet_stream)
cooler.setOutTemperature(273.15 + 30)  # 30°C
process.add(cooler)

process.run()

print(f"Cooling duty: {cooler.getDuty() / 1000:.1f} kW")
```

### Shell-and-Tube Heat Exchanger

```python
HeatExchanger = jneqsim.process.equipment.heatexchanger.HeatExchanger

hx = HeatExchanger("E-102", hot_stream, cold_stream)
hx.setUAvalue(5000)  # W/K
process.add(hx)

process.run()

print(f"Duty: {hx.getDuty() / 1000:.1f} kW")
print(f"Hot out T: {hx.getOutStream(0).getTemperature() - 273.15:.1f} °C")
print(f"Cold out T: {hx.getOutStream(1).getTemperature() - 273.15:.1f} °C")
```

---

## Valves

### Throttling Valve (JT Valve)

```python
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

valve = ThrottlingValve("VLV-100", inlet_stream)
valve.setOutletPressure(20.0)  # bara
process.add(valve)

process.run()

# JT cooling
delta_T = valve.getOutletStream().getTemperature() - inlet_stream.getTemperature()
print(f"Temperature change: {delta_T:.1f} K")
```

### Control Valve with Cv

```python
valve = ThrottlingValve("CV-100", inlet_stream)
valve.setOutletPressure(30.0)
valve.setCv(100.0)  # Valve Cv
valve.setPercentValveOpening(50.0)  # % open
process.add(valve)
```

---

## Flowsheet Building

### Complete Simple Process

```python
from neqsim import jneqsim

# Imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

# 1. Create fluid
fluid = SystemSrkEos(273.15 + 50, 30.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.10)
fluid.addComponent("n-butane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")

# 2. Build process
process = ProcessSystem()

# Feed
feed = Stream("Feed", fluid)
feed.setFlowRate(50000, "kg/hr")
process.add(feed)

# HP Separator
hp_sep = Separator("HP Sep", feed)
process.add(hp_sep)

# Gas compression
comp = Compressor("Compressor", hp_sep.getGasOutStream())
comp.setOutletPressure(80.0)
comp.setIsentropicEfficiency(0.75)
process.add(comp)

# Aftercooler
cooler = Cooler("Aftercooler", comp.getOutletStream())
cooler.setOutTemperature(273.15 + 40)
process.add(cooler)

# 3. Run
process.run()

# 4. Results
print("=== PROCESS RESULTS ===")
print(f"Feed: {feed.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Gas: {hp_sep.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid: {hp_sep.getLiquidOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {comp.getPower('kW'):.1f} kW")
print(f"Cooler duty: {cooler.getDuty()/1000:.1f} kW")
```

---

## Recycles and Adjusters

### Recycle Stream

```python
Recycle = jneqsim.process.equipment.util.Recycle

# After building main process...
# Create recycle (connects outlet back to inlet)
recycle = Recycle("Recycle")
recycle.addStream(recycle_outlet_stream)
recycle.setOutletStream(mixer_inlet)  # Where it goes back
recycle.setTolerance(1e-6)
process.add(recycle)

# Run with recycle convergence
process.run()
```

### Adjuster (Spec Controller)

```python
Adjuster = jneqsim.process.equipment.util.Adjuster

# Adjust compressor outlet pressure to achieve target flow
adjuster = Adjuster("Adjust-1")
adjuster.setAdjustedVariable(compressor, "outlet pressure")
adjuster.setTargetVariable(outlet_stream, "flow rate", "kg/hr")
adjuster.setTargetValue(5000)  # Target 5000 kg/hr
adjuster.setTolerance(1e-4)
process.add(adjuster)

process.run()
print(f"Adjusted pressure: {compressor.getOutletPressure():.2f} bara")
```

---

## See Also

- **[Process Equipment Documentation](../process/equipment/)** - All equipment types
- **[Optimization Guide](../process/optimization/)** - Process optimization
- **[JavaDoc: ProcessSystem](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/process/processmodel/ProcessSystem.html)** - Complete API
