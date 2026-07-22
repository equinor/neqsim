---
title: Measurement Devices and Analysers
description: NeqSim provides a comprehensive set of measurement devices and process analysers for monitoring fluid properties, compositions, and process conditions.
---

# Measurement Devices and Analysers

NeqSim provides a comprehensive set of measurement devices and process analysers for monitoring fluid properties, compositions, and process conditions.

## Overview

Measurement devices in NeqSim fall into several categories:

- **Fluid Analysers** - Dew point, composition, emissions
- **Process Monitors** - Level, pressure, temperature, flow
- **Safety Detectors** - Gas and fire detection
- **Performance Monitors** - Vibration analysis, compressor monitoring
- **Quality Analysers** - Hydrocarbon dew point, water content, molar mass

## Fluid Composition Analysers

### CombustionEmissionsCalculator

Calculates CO2 emissions from fuel gas combustion based on stream composition.

```java
import neqsim.process.measurementdevice.CombustionEmissionsCalculator;

// Create fuel gas stream
Stream fuelGas = new Stream("Fuel Gas", gas);
fuelGas.setFlowRate(1000.0, "kg/hr");
fuelGas.run();

// Create emissions calculator
CombustionEmissionsCalculator emissionsCalc = 
    new CombustionEmissionsCalculator("CO2 Calculator", fuelGas);

// Get CO2 emissions rate
double co2Emissions = emissionsCalc.getMeasuredValue("kg/hr");
System.out.println("CO2 emissions: " + co2Emissions + " kg/hr");
```

**CO2 Emission Factors (kg CO2 per kg component):**

| Component | Emission Factor |
|-----------|----------------|
| Methane | 2.75 |
| Ethane | 3.75 |
| Propane | 5.50 |
| n-Butane | 6.50 |
| n-Pentane | 7.50 |
| Hexane | 8.50 |
| Nitrogen | 0.0 |
| CO2 | 0.0 |

### NMVOCAnalyser

Calculates the mass flow rate of Non-Methane Volatile Organic Compounds (nmVOCs).

```java
import neqsim.process.measurementdevice.NMVOCAnalyser;

// Create analyser
NMVOCAnalyser nmvocAnalyser = new NMVOCAnalyser("NMVOC Monitor", ventStream);

// Get nmVOC flow rate
double nmvocFlow = nmvocAnalyser.getMeasuredValue("kg/hr");
double nmvocYearly = nmvocAnalyser.getnmVOCFlowRate("tonnes/year");
System.out.println("NMVOC emissions: " + nmvocYearly + " tonnes/year");
```

**Components included in nmVOC calculation:**
- Ethane, Propane, i-Butane, n-Butane
- i-Pentane, n-Pentane, n-Hexane, n-Heptane
- Benzene, nC8, nC9, nC10, nC11

## Dew Point Analysers

### HydrocarbonDewPointAnalyser

Calculates the hydrocarbon dew point temperature at a specified pressure.

```java
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;

HydrocarbonDewPointAnalyser hcdp = 
    new HydrocarbonDewPointAnalyser("HC Dew Point", gasStream);
hcdp.setReferencePressure(50.0, "bara");

double dewPointC = hcdp.getMeasuredValue("C");
System.out.println("HC dew point: " + dewPointC + " °C");
```

### WaterDewPointAnalyser

Calculates the water dew point temperature.

```java
import neqsim.process.measurementdevice.WaterDewPointAnalyser;

WaterDewPointAnalyser wdp = 
    new WaterDewPointAnalyser("Water Dew Point", gasStream);
wdp.setReferencePressure(50.0, "bara");

double waterDewPoint = wdp.getMeasuredValue("C");
System.out.println("Water dew point: " + waterDewPoint + " °C");
```

### CricondenbarAnalyser

Calculates the cricondenbar (maximum pressure on phase envelope).

```java
import neqsim.process.measurementdevice.CricondenbarAnalyser;

CricondenbarAnalyser cricondenbar = new CricondenbarAnalyser(gasStream);
double maxPressure = cricondenbar.getMeasuredValue("bara");
System.out.println("Cricondenbar: " + maxPressure + " bara");
```

### HydrateEquilibriumTemperatureAnalyser

Calculates the hydrate equilibrium temperature at the stream pressure.

```java
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;

HydrateEquilibriumTemperatureAnalyser hydrateAnalyser = 
    new HydrateEquilibriumTemperatureAnalyser(gasStream);
double hydrateTemp = hydrateAnalyser.getMeasuredValue("C");
System.out.println("Hydrate formation temp: " + hydrateTemp + " °C");
```

## Vibration Analysis

### FlowInducedVibrationAnalyser

Calculates Flow-Induced Vibration (FIV) risk indicators for pipelines.

```java
import neqsim.process.measurementdevice.FlowInducedVibrationAnalyser;

// Create pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Export", feed);
pipeline.setLength(5000.0);
pipeline.setDiameter(0.3048);  // 12 inch
pipeline.setThickness(0.0127); // 0.5 inch
pipeline.run();

// Create FIV analyser
FlowInducedVibrationAnalyser fivAnalyser = 
    new FlowInducedVibrationAnalyser("FIV Monitor", pipeline);
fivAnalyser.setSupportArrangement("Stiff");
fivAnalyser.setSupportDistance(3.0);  // meters

// Get FIV metrics
fivAnalyser.setMethod("LOF");  // Likelihood of Failure
double lof = fivAnalyser.getMeasuredValue("");
System.out.println("Likelihood of Failure: " + lof);

fivAnalyser.setMethod("FRMS");  // Fatigue Root Mean Square
double frms = fivAnalyser.getMeasuredValue("");
System.out.println("F-RMS: " + frms);
```

**Support Arrangements:**
- `"Stiff"` - Well-supported piping
- `"Medium stiff"` - Moderate support
- `"Medium"` - Typical support
- `"Flexible"` - Minimal support

**Analysis Methods:**
- `"LOF"` - Likelihood of Failure (API RP 14E based)
- `"FRMS"` - Fatigue Root Mean Square

## Process Monitors

### PressureTransmitter

Monitors pressure at a measurement point.

```java
import neqsim.process.measurementdevice.PressureTransmitter;

PressureTransmitter pt = new PressureTransmitter(separator);
pt.setUnit("bara");
double pressure = pt.getMeasuredValue();
```

### TemperatureTransmitter

Monitors temperature at a measurement point.

```java
import neqsim.process.measurementdevice.TemperatureTransmitter;

TemperatureTransmitter tt = new TemperatureTransmitter(heatExchanger);
tt.setUnit("C");
double temperature = tt.getMeasuredValue();
```

### LevelTransmitter

Monitors liquid level in vessels.

```java
import neqsim.process.measurementdevice.LevelTransmitter;

LevelTransmitter lt = new LevelTransmitter(separator);
lt.setUnit("%");
double level = lt.getMeasuredValue();
```

### VolumeFlowTransmitter

Monitors volumetric flow rate.

```java
import neqsim.process.measurementdevice.VolumeFlowTransmitter;

VolumeFlowTransmitter vft = new VolumeFlowTransmitter(stream);
vft.setUnit("m3/hr");
double volumeFlow = vft.getMeasuredValue();
```

## Safety Devices

### GasDetector

Simulates gas detection for safety systems.

```java
import neqsim.process.measurementdevice.GasDetector;

GasDetector gasDetector = new GasDetector("Gas Detector 1", stream);
gasDetector.setDetectionLimit(20.0);  // % LEL
boolean gasDetected = gasDetector.isTriggered();
```

### FireDetector

Simulates fire detection for safety systems.

```java
import neqsim.process.measurementdevice.FireDetector;

FireDetector fireDetector = new FireDetector("Fire Detector 1");
fireDetector.setTemperatureThreshold(65.0);  // °C
boolean fireDetected = fireDetector.isTriggered();
```

## Quality Analysers

### MolarMassAnalyser

Calculates the molar mass of a stream.

```java
import neqsim.process.measurementdevice.MolarMassAnalyser;

MolarMassAnalyser mma = new MolarMassAnalyser(gasStream);
double molarMass = mma.getMeasuredValue("kg/mol");
System.out.println("Molar mass: " + molarMass * 1000 + " g/mol");
```

### WaterContentAnalyser

Measures water content in gas streams.

```java
import neqsim.process.measurementdevice.WaterContentAnalyser;

WaterContentAnalyser wca = new WaterContentAnalyser(gasStream);
double waterContent = wca.getMeasuredValue("ppm");
System.out.println("Water content: " + waterContent + " ppm");
```

### pHProbe

Measures pH of aqueous streams.

```java
import neqsim.process.measurementdevice.pHProbe;

pHProbe ph = new pHProbe(aqueousStream);
double phValue = ph.getMeasuredValue("");
System.out.println("pH: " + phValue);
```

## Multi-Phase Measurement

### MultiPhaseMeter

Simulates multi-phase flow meter measurements.

```java
import neqsim.process.measurementdevice.MultiPhaseMeter;

MultiPhaseMeter mpm = new MultiPhaseMeter("MPFM-1", multiphaseStream);

double gasFlow = mpm.getGasFlowRate("Sm3/hr");
double oilFlow = mpm.getOilFlowRate("m3/hr");
double waterFlow = mpm.getWaterFlowRate("m3/hr");
double waterCut = mpm.getWaterCut();
double gor = mpm.getGOR("Sm3/Sm3");
```

## Compressor Monitoring

### CompressorMonitor

Monitors compressor performance parameters.

```java
import neqsim.process.measurementdevice.CompressorMonitor;

CompressorMonitor cm = new CompressorMonitor(compressor);

double polyEff = cm.getPolytropicEfficiency();
double isenEff = cm.getIsentropicEfficiency();
double head = cm.getPolytropicHead("kJ/kg");
double power = cm.getPower("kW");
double surgeMargin = cm.getSurgeMargin();
```

## Well Allocation

### WellAllocator

Allocates production to individual wells based on test data.

```java
import neqsim.process.measurementdevice.WellAllocator;

WellAllocator allocator = new WellAllocator("Allocation System");
allocator.addWellTest("Well-A", oilRate, gasRate, waterRate);
allocator.addWellTest("Well-B", oilRate2, gasRate2, waterRate2);
allocator.allocateProduction(totalOil, totalGas, totalWater);

double wellAOil = allocator.getAllocatedOil("Well-A");
```

## Python Usage

```python
from jpype import JClass

# Import measurement devices
CombustionEmissionsCalculator = JClass('neqsim.process.measurementdevice.CombustionEmissionsCalculator')
FlowInducedVibrationAnalyser = JClass('neqsim.process.measurementdevice.FlowInducedVibrationAnalyser')
NMVOCAnalyser = JClass('neqsim.process.measurementdevice.NMVOCAnalyser')

# Emissions calculation
emissions_calc = CombustionEmissionsCalculator("CO2", fuel_stream)
co2_rate = emissions_calc.getMeasuredValue("kg/hr")
print(f"CO2 emissions: {co2_rate} kg/hr")

# nmVOC analysis
nmvoc = NMVOCAnalyser("NMVOC", vent_stream)
nmvoc_rate = nmvoc.getMeasuredValue("tonnes/year")
print(f"NMVOC: {nmvoc_rate} tonnes/year")

# FIV analysis
fiv = FlowInducedVibrationAnalyser("FIV", pipeline)
fiv.setMethod("LOF")
lof = fiv.getMeasuredValue("")
print(f"LOF: {lof}")
```

## API Reference

### MeasurementDeviceBaseClass

Base class for all measurement devices.

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue()` | `double` | Get measurement in default unit |
| `getMeasuredValue(unit)` | `double` | Get measurement in specified unit |
| `setUnit(unit)` | `void` | Set default measurement unit |
| `getUnit()` | `String` | Get current measurement unit |
| `displayResult()` | `void` | Display measurement result |

### StreamMeasurementDeviceBaseClass

Base class for stream-based measurement devices.

| Method | Returns | Description |
|--------|---------|-------------|
| `setStream(stream)` | `void` | Set the stream to measure |
| `getStream()` | `StreamInterface` | Get the measured stream |

### CombustionEmissionsCalculator

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue(unit)` | `double` | Get CO2 emissions rate |
| `setComponents()` | `void` | Update component list from stream |

### FlowInducedVibrationAnalyser

| Method | Parameters | Description |
|--------|------------|-------------|
| `setMethod(method)` | `"LOF"` or `"FRMS"` | Set analysis method |
| `setSupportArrangement(type)` | `"Stiff"`, `"Medium stiff"`, `"Medium"`, `"Flexible"` | Set pipe support type |
| `setSupportDistance(distance)` | meters | Set support spacing |
| `setSegment(segment)` | segment number | Analyse specific pipe segment |

### NMVOCAnalyser

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue(unit)` | `double` | Get nmVOC flow rate |
| `getnmVOCFlowRate(unit)` | `double` | Get nmVOC flow rate |

## See Also

- [Process Simulation](../../wiki/process_simulation)
- [Safety Systems](../safety/)
- [Pipeline Simulation](../../fluidmechanics/)
- [Capacity Constraints](../CAPACITY_CONSTRAINT_FRAMEWORK) - FIV/AIV limits
