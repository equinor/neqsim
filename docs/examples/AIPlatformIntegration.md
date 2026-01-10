---
layout: default
title: "AIPlatformIntegration"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# AIPlatformIntegration

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`AIPlatformIntegration.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/AIPlatformIntegration.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/AIPlatformIntegration.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/AIPlatformIntegration.ipynb).

---

# NeqSim AI Platform Integration

This notebook demonstrates how to integrate NeqSim's thermodynamic and process simulation capabilities with AI-based production optimization platforms using the **Direct Java Access** method.

## Overview

AI-based production optimization platforms typically require:
- Real-time data streaming (millions of data points per hour)
- Hybrid AI models (physics + machine learning)
- Virtual Flow Meters (VFM) and soft sensors
- Continuous auto-calibration for digital twins
- Uncertainty quantification for risk-aware optimization

**NeqSim** provides the physics engine with packages designed for AI platform integration:
- `neqsim.process.streaming` - Real-time data publishing
- `neqsim.process.measurementdevice.vfm` - Virtual flow meters and soft sensors
- `neqsim.process.util.uncertainty` - Uncertainty propagation
- `neqsim.process.integration.ml` - ML model integration
- `neqsim.process.calibration` - Online calibration
- `neqsim.process.equipment.well.allocation` - Well production allocation
- `neqsim.process.util.event` - Event system for alerts
- `neqsim.process.util.export` - Data export for ML training

## Table of Contents

1. [Setup and Installation](#1.-Setup-and-Installation)
2. [Direct Java Access Setup](#2.-Direct-Java-Access-Setup)
3. [Creating a Production System](#3.-Creating-a-Production-System)
4. [Real-Time Data Streaming](#4.-Real-Time-Data-Streaming)
5. [Virtual Flow Meters](#5.-Virtual-Flow-Meters)
6. [Soft Sensors](#6.-Soft-Sensors)
7. [Uncertainty Quantification](#7.-Uncertainty-Quantification)
8. [Online Calibration](#8.-Online-Calibration)
9. [Well Production Allocation](#9.-Well-Production-Allocation)
10. [Event System](#10.-Event-System)
11. [Data Export for ML Training](#11.-Data-Export-for-ML-Training)
12. [Complete Integration Example](#12.-Complete-Integration-Example)

## 1. Setup and Installation

First, install the neqsim Python package. This requires Java 8 or higher to be installed.

```bash
pip install neqsim
```

The neqsim package uses JPype to bridge Python and Java, providing full access to the NeqSim Java library.

```python
# Install neqsim if not already installed
# !pip install neqsim

import neqsim
print(f"NeqSim version: {neqsim.__version__}")
```

## 2. Direct Java Access Setup

The **Direct Java Access** method provides full control over NeqSim's Java classes. This is recommended for:
- Advanced features not exposed in Python wrappers
- Custom AI/ML integration
- High-performance real-time applications

We use the `jneqsim` module which provides direct access to all Java packages.

```python
# Direct Java Access - import jneqsim for full control
from neqsim import jneqsim

# Also import standard Python libraries for data handling
import numpy as np
import pandas as pd
import time
from datetime import datetime

# Access Java utility classes
from java.util import HashMap, Arrays
from java.time import Instant

print("Direct Java access configured successfully!")
print(f"Available packages: thermo, process, thermodynamicOperations, ...")
```

## 3. Creating a Production System

Let's create a realistic oil and gas production system using direct Java access. This represents a typical offshore production scenario that an AI platform would optimize in real-time.

### Direct Java Access Pattern

```python
# Access Java classes via jneqsim module
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
```

```python
# Create a multiphase fluid using direct Java access
# Access the SRK equation of state class
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 60, 80.0)  # 60°C, 80 bara

# Add gas components
fluid.addComponent("methane", 0.70)      # 70 mol%
fluid.addComponent("ethane", 0.08)       # 8 mol%
fluid.addComponent("propane", 0.05)      # 5 mol%
fluid.addComponent("i-butane", 0.02)     # 2 mol%
fluid.addComponent("n-butane", 0.02)     # 2 mol%

# Add oil components (using n-heptane as surrogate)
fluid.addComponent("n-heptane", 0.08)    # 8 mol%
fluid.addComponent("n-octane", 0.03)     # 3 mol%

# Add water
fluid.addComponent("water", 0.02)        # 2 mol%

# Configure thermodynamic model
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

# Create process system using direct Java access
process = jneqsim.process.processmodel.ProcessSystem()

# Create inlet stream (from well)
well_stream = jneqsim.process.equipment.stream.Stream("WellStream", fluid)
well_stream.setFlowRate(50000.0, "kg/hr")  # 50 tonnes/hour
well_stream.setTemperature(60.0, "C")
well_stream.setPressure(80.0, "bara")

# Add to process and run
process.add(well_stream)
process.run()

# Display stream properties
print("=" * 60)
print("WELL STREAM PROPERTIES (Direct Java Access)")
print("=" * 60)
print(f"Temperature:      {well_stream.getTemperature('C'):.2f} °C")
print(f"Pressure:         {well_stream.getPressure('bara'):.2f} bara")
print(f"Mass flow:        {well_stream.getFlowRate('kg/hr'):.2f} kg/hr")
print(f"Molar flow:       {well_stream.getFlowRate('mol/sec'):.2f} mol/s")
print(f"Number of phases: {well_stream.getFluid().getNumberOfPhases()}")

# Phase fractions
phases = well_stream.getFluid()
if phases.getNumberOfPhases() > 1:
    print(f"\nPhase distribution:")
    for i in range(phases.getNumberOfPhases()):
        phase = phases.getPhase(i)
        print(f"  {phase.getPhaseTypeName()}: {phase.getBeta()*100:.2f} mol%")
```

## 4. Real-Time Data Streaming

The `ProcessDataPublisher` class enables high-frequency data streaming from NeqSim simulations to external AI platforms. It supports:

- **Automatic tag discovery** from process equipment
- **Timestamped values** with quality indicators
- **Configurable history buffer** for trend analysis
- **State vector extraction** for ML model inputs

This is crucial for real-time optimization where data rates can exceed millions of points per hour.

```python
# Access streaming classes via direct Java access
ProcessDataPublisher = jneqsim.process.streaming.ProcessDataPublisher
TimestampedValue = jneqsim.process.streaming.TimestampedValue

# Create a ProcessDataPublisher linked to our process
publisher = ProcessDataPublisher(process)

# Publish current process state
publisher.publishFromProcessSystem()

# Get state vector (useful for ML models)
state_vector = publisher.getStateVector()
print(f"State vector dimension: {len(state_vector)}")
print(f"State vector values: {list(state_vector)[:5]}...")  # First 5 values

# Demonstrate TimestampedValue with quality indicators
measurement = TimestampedValue(
    50.5,                           # value
    "bara",                         # unit
    Instant.now(),                  # timestamp
    TimestampedValue.Quality.GOOD   # quality indicator
)

print(f"\nTimestamped Measurement:")
print(f"  Value:     {measurement.getValue()}")
print(f"  Unit:      {measurement.getUnit()}")
print(f"  Timestamp: {measurement.getTimestamp()}")
print(f"  Quality:   {measurement.getQuality()}")

# Show different quality levels
print(f"\nAvailable Quality Levels:")
for quality in TimestampedValue.Quality.values():
    print(f"  - {quality}")
```

## 5. Virtual Flow Meters (VFM)

Virtual Flow Meters use thermodynamic models to calculate multiphase flow rates when physical meters are:
- Not installed (cost savings)
- Unreliable or degraded
- Being validated against measurements

The VFM provides oil, gas, and water rates with **uncertainty bounds** - critical for production allocation and optimization under uncertainty.

### VFM Architecture

```
                    ┌─────────────────┐
    P, T, ΔP ──────►│  Thermodynamic  │
                    │     Model       │──────► Oil Rate ± σ
    Composition ───►│   (NeqSim)      │──────► Gas Rate ± σ
                    │                 │──────► Water Rate ± σ
    Calibration ───►│                 │──────► GOR, Water Cut
                    └─────────────────┘
```

```python
# Access VFM classes via direct Java access
VirtualFlowMeter = jneqsim.process.measurementdevice.vfm.VirtualFlowMeter
VFMResult = jneqsim.process.measurementdevice.vfm.VFMResult

# Create a Virtual Flow Meter for the well stream
vfm = VirtualFlowMeter("VFM-Well-A", well_stream)

# Calculate flow rates
vfm_result = vfm.calculate()

print("=" * 60)
print("VIRTUAL FLOW METER RESULTS")
print("=" * 60)
print(f"Timestamp:   {vfm_result.getTimestamp()}")
print(f"\nFlow Rates:")
print(f"  Gas Rate:   {vfm_result.getGasFlowRate():,.2f} Sm³/day")
print(f"  Oil Rate:   {vfm_result.getOilFlowRate():,.2f} Sm³/day")
print(f"  Water Rate: {vfm_result.getWaterFlowRate():,.2f} Sm³/day")

print(f"\nDerived Properties:")
print(f"  GOR:        {vfm_result.getGOR():,.2f} Sm³/Sm³")
print(f"  Water Cut:  {vfm_result.getWaterCut()*100:.2f}%")
print(f"  Quality:    {vfm_result.getQuality()}")

# Get uncertainty bounds for gas rate
gas_uncertainty = vfm_result.getGasFlowRateUncertainty()
if gas_uncertainty:
    print(f"\nGas Rate Uncertainty (95% CI):")
    print(f"  Lower: {gas_uncertainty.getLower95():,.2f} Sm³/day")
    print(f"  Upper: {gas_uncertainty.getUpper95():,.2f} Sm³/day")
    print(f"  Std Dev: {gas_uncertainty.getStandardDeviation():,.2f} Sm³/day")
```

## 6. Soft Sensors

Soft sensors estimate properties that are difficult or expensive to measure directly. They use thermodynamic models to calculate derived properties from available measurements.

**Common Soft Sensor Applications:**
- Gas-Oil Ratio (GOR) from P, T, and flow
- Water Cut from separator levels
- Fluid density for custody transfer
- Viscosity for pipeline design
- Compressibility factor (Z) for gas metering

```python
# Access SoftSensor class via direct Java access
SoftSensor = jneqsim.process.measurementdevice.vfm.SoftSensor

print("=" * 60)
print("SOFT SENSOR DEMONSTRATIONS")
print("=" * 60)

# Available property types
print("\nAvailable Soft Sensor Property Types:")
for prop_type in SoftSensor.PropertyType.values():
    print(f"  - {prop_type}")

# Create soft sensors for key properties
soft_sensors = {}
property_types = [
    SoftSensor.PropertyType.GOR,
    SoftSensor.PropertyType.WATER_CUT,
    SoftSensor.PropertyType.DENSITY,
    SoftSensor.PropertyType.Z_FACTOR
]

print("\nSoft Sensor Results:")
print("-" * 50)

for prop_type in property_types:
    sensor = SoftSensor(f"Sensor-{prop_type}", well_stream, prop_type)
    value = sensor.getMeasuredValue()
    unit = sensor.getUnit()
    soft_sensors[str(prop_type)] = sensor
    print(f"  {prop_type}:  {value:12.4f} {unit}")

# Demonstrate sensitivity analysis for GOR sensor
gor_sensor = soft_sensors['GOR']
print(f"\nGOR Sensitivity Analysis:")
print(f"  Sensitivity to pressure:    {gor_sensor.getSensitivity('pressure'):.4f}")
print(f"  Sensitivity to temperature: {gor_sensor.getSensitivity('temperature'):.4f}")
```

## 7. Uncertainty Quantification

Production optimization under uncertainty requires propagating measurement uncertainties through thermodynamic calculations. This enables:

- **Risk-aware optimization** - optimize expected value while managing downside risk
- **Alarm threshold setting** - account for measurement uncertainty in limits
- **Model validation** - determine if deviations are significant
- **Uncertainty budgets** - identify which measurements need improvement

### Mathematical Framework

For a model $y = f(x)$ with input uncertainties $\sigma_x$, the output uncertainty is:

$$\sigma_y^2 = \left(\frac{\partial f}{\partial x}\right)^2 \sigma_x^2$$

For multiple inputs (Jacobian-based propagation):

$$\Sigma_y = J \cdot \Sigma_x \cdot J^T$$

where $J$ is the sensitivity (Jacobian) matrix.

```python
# Access uncertainty classes via direct Java access
UncertaintyAnalyzer = jneqsim.process.util.uncertainty.UncertaintyAnalyzer
SensitivityMatrix = jneqsim.process.util.uncertainty.SensitivityMatrix

# Create an uncertainty analyzer for the process
analyzer = UncertaintyAnalyzer(process)

# Define input uncertainties (typical measurement uncertainties)
input_uncertainties = {
    "pressure": 0.01,      # 1% pressure measurement uncertainty
    "temperature": 0.005,  # 0.5% temperature uncertainty
    "flowrate": 0.02       # 2% flow measurement uncertainty
}

print("=" * 60)
print("UNCERTAINTY ANALYSIS")
print("=" * 60)

print("\nInput Uncertainties (relative):")
for name, unc in input_uncertainties.items():
    print(f"  {name}: ±{unc*100:.1f}%")

# Perform analytical uncertainty propagation
result = analyzer.analyzeAnalytical()

print(f"\nAnalysis Complete:")
print(f"  Number of outputs analyzed: {len(result.getOutputNames())}")

# Get sensitivity matrix
sens_matrix = result.getSensitivityMatrix()
print(f"\nSensitivity Matrix Dimensions:")
print(f"  Inputs:  {len(sens_matrix.getInputNames())}")
print(f"  Outputs: {len(sens_matrix.getOutputNames())}")

# Display output uncertainties
print(f"\nOutput Uncertainties:")
output_uncertainties = result.getOutputUncertainties()
for name in result.getOutputNames():
    unc = output_uncertainties.get(name)
    if unc:
        print(f"  {name}: ±{unc*100:.2f}%")
```

## 8. Online Calibration

Digital twins require continuous calibration to maintain accuracy as:
- Equipment degrades over time
- Operating conditions change
- Fluid composition varies

The `OnlineCalibrator` provides:
- **Deviation monitoring** - detect when model predictions diverge from measurements
- **Incremental updates** - fast, real-time parameter adjustments
- **Full recalibration** - thorough optimization using historical data
- **Quality metrics** - track calibration freshness and accuracy

```python
# Access calibration classes via direct Java access
OnlineCalibrator = jneqsim.process.calibration.OnlineCalibrator
CalibrationResult = jneqsim.process.calibration.CalibrationResult
CalibrationQuality = jneqsim.process.calibration.CalibrationQuality

# Create an online calibrator for the process
calibrator = OnlineCalibrator(process)

# Configure tunable parameters
tunable_params = Arrays.asList("efficiency", "k_factor", "heat_transfer_coefficient")
calibrator.setTunableParameters(tunable_params)

# Set deviation threshold (10%)
calibrator.setDeviationThreshold(0.10)

print("=" * 60)
print("ONLINE CALIBRATION DEMONSTRATION")
print("=" * 60)

# Simulate recording measurement/prediction pairs over time
print("\nSimulating 20 measurement/prediction pairs...")
for i in range(20):
    # Simulated measurements (with some noise)
    measurements = HashMap()
    measurements.put("outlet_pressure", 45.0 + np.random.normal(0, 0.5))
    measurements.put("outlet_temperature", 35.0 + np.random.normal(0, 0.2))
    measurements.put("gas_flowrate", 1000.0 + np.random.normal(0, 20))
    
    # Simulated predictions (with systematic bias)
    predictions = HashMap()
    predictions.put("outlet_pressure", 44.5 + np.random.normal(0, 0.3))
    predictions.put("outlet_temperature", 35.5 + np.random.normal(0, 0.15))
    predictions.put("gas_flowrate", 980.0 + np.random.normal(0, 15))
    
    # Record data point
    exceeds = calibrator.recordDataPoint(measurements, predictions)

print(f"Data points recorded: {calibrator.getHistorySize()}")

# Perform full recalibration
print("\nPerforming full recalibration...")
cal_result = calibrator.fullRecalibration()

print(f"\nCalibration Result:")
print(f"  Successful:   {cal_result.isSuccessful()}")
print(f"  Message:      {cal_result.getMessage()}")
print(f"  Iterations:   {cal_result.getIterations()}")
print(f"  Objective:    {cal_result.getObjectiveValue():.6f}")

# Get calibration quality metrics
quality = calibrator.getQualityMetrics()
if quality:
    print(f"\nCalibration Quality:")
    print(f"  RMSE:         {quality.getRootMeanSquareError():.4f}")
    print(f"  R² Score:     {quality.getR2Score():.4f}")
    print(f"  Sample Count: {quality.getSampleCount()}")
    print(f"  Overall Score: {quality.getOverallScore():.1f}/100")
    print(f"  Rating:       {quality.getRating()}")
    print(f"  Needs Recal.: {quality.needsRecalibration()}")
```

## 9. Well Production Allocation

When multiple wells produce into a common pipeline (commingled production), the total measured rates must be allocated back to individual wells for:
- **Reservoir management** - track well performance
- **Production accounting** - revenue distribution
- **Optimization** - identify underperforming wells

### Allocation Methods

| Method | Description | Typical Uncertainty |
|--------|-------------|---------------------|
| Well Test | Based on periodic test data | ±10% |
| VFM-based | Real-time virtual flow meter | ±5% |
| Choke Model | Based on choke performance | ±15% |
| Combined | Weighted average of methods | ±7% |

```python
# Access well allocation classes via direct Java access
WellProductionAllocator = jneqsim.process.equipment.well.allocation.WellProductionAllocator
AllocationResult = jneqsim.process.equipment.well.allocation.AllocationResult

# Create well production allocator for a 3-well field
allocator = WellProductionAllocator("Field-Alpha-Allocation")

print("=" * 60)
print("WELL PRODUCTION ALLOCATION")
print("=" * 60)

# Well A - High GOR gas well
wellA = allocator.addWell("Well-A")
wellA.setTestRates(150.0, 45000.0, 50.0)    # oil, gas, water (Sm³/day)
wellA.setVFMRates(145.0, 44000.0, 48.0)
wellA.setChokePosition(0.75)
wellA.setProductivityIndex(12.0)
wellA.setReservoirPressure(280.0)

# Well B - Oil-dominated well
wellB = allocator.addWell("Well-B")
wellB.setTestRates(350.0, 25000.0, 120.0)
wellB.setVFMRates(340.0, 24500.0, 115.0)
wellB.setChokePosition(0.85)
wellB.setProductivityIndex(15.0)
wellB.setReservoirPressure(275.0)

# Well C - High water cut well
wellC = allocator.addWell("Well-C")
wellC.setTestRates(200.0, 18000.0, 280.0)
wellC.setVFMRates(195.0, 17500.0, 275.0)
wellC.setChokePosition(0.65)
wellC.setProductivityIndex(8.0)
wellC.setReservoirPressure(265.0)

print(f"Number of wells: {allocator.getWellCount()}")
print(f"Wells: {list(allocator.getWellNames())}")

# Set allocation method
allocator.setAllocationMethod(WellProductionAllocator.AllocationMethod.VFM_BASED)

# Total measured production at separator
total_oil = 680.0    # Sm³/day
total_gas = 86000.0  # Sm³/day  
total_water = 440.0  # Sm³/day

print(f"\nTotal Measured Production:")
print(f"  Oil:   {total_oil:,.1f} Sm³/day")
print(f"  Gas:   {total_gas:,.1f} Sm³/day")
print(f"  Water: {total_water:,.1f} Sm³/day")

# Perform allocation
result = allocator.allocate(total_oil, total_gas, total_water)

print(f"\n{'Well':<10} {'Oil':<12} {'Gas':<14} {'Water':<12} {'GOR':<10} {'WC%':<8} {'Unc.':<8}")
print("-" * 74)

for well_name in result.getWellNames():
    oil = result.getOilRate(well_name)
    gas = result.getGasRate(well_name)
    water = result.getWaterRate(well_name)
    gor = result.getGOR(well_name)
    wc = result.getWaterCut(well_name) * 100
    unc = result.getUncertainty(well_name) * 100
    
    print(f"{well_name:<10} {oil:>10,.1f} {gas:>12,.1f} {water:>10,.1f} {gor:>8,.1f} {wc:>6.1f}% {unc:>6.1f}%")

print("-" * 74)
print(f"{'TOTAL':<10} {result.getTotalOilRate():>10,.1f} {result.getTotalGasRate():>12,.1f} {result.getTotalWaterRate():>10,.1f}")

print(f"\nAllocation Status:")
print(f"  Balanced:         {result.isBalanced()}")
print(f"  Allocation Error: {result.getAllocationError()*100:.4f}%")
```

## 10. Event System

The event system enables reactive programming patterns for:
- **Alarm notifications** - threshold violations
- **Model deviation alerts** - digital twin drift detection
- **Calibration triggers** - automatic recalibration
- **Integration with external systems** - SCADA, PI, OPC-UA

### Event Types

| Event Type | Description | Use Case |
|------------|-------------|----------|
| THRESHOLD_CROSSED | Value exceeded limit | Safety alarms |
| MODEL_DEVIATION | Prediction differs from measurement | Digital twin health |
| ALARM | General alarm condition | Operations alerts |
| CALIBRATION | Calibration event occurred | Model maintenance |
| STATE_CHANGE | Equipment state changed | Process monitoring |

```python
# Access event system classes via direct Java access
ProcessEventBus = jneqsim.process.util.event.ProcessEventBus
ProcessEvent = jneqsim.process.util.event.ProcessEvent

# Get the event bus singleton
event_bus = ProcessEventBus.getInstance()

print("=" * 60)
print("EVENT SYSTEM DEMONSTRATION")
print("=" * 60)

# Show available event types
print("\nAvailable Event Types:")
for event_type in ProcessEvent.EventType.values():
    print(f"  - {event_type}")

print("\nAvailable Severity Levels:")
for severity in ProcessEvent.Severity.values():
    print(f"  - {severity}")

# Publish some example events
print("\nPublishing example events...")

# Info event
event_bus.publish(ProcessEvent.info("Compressor-1", "Startup sequence completed successfully"))

# Warning event
event_bus.publish(ProcessEvent.warning("Separator-V100", "Level approaching high alarm limit (85%)"))

# Alarm event
event_bus.publish(ProcessEvent.alarm("ESD-System", "Emergency shutdown initiated by operator"))

# Threshold crossing event
event_bus.publish(ProcessEvent.thresholdCrossed(
    "PT-101",           # source
    "pressure",         # variable
    52.5,              # current value
    50.0,              # threshold
    True               # above threshold
))

# Model deviation event
event_bus.publish(ProcessEvent.modelDeviation(
    "VFM-Well-A",       # source
    "gas_rate",         # variable
    48500.0,           # measured
    50000.0            # predicted
))

# Get recent events
print(f"\nRecent Events (last 10):")
print("-" * 80)
recent = event_bus.getRecentEvents(10)
for event in recent:
    print(f"[{event.getSeverity()}] {event.getType()}: {event.getDescription()}")

# Get alarms specifically
print(f"\nAlarm Events:")
print("-" * 80)
alarms = event_bus.getEventsByType(ProcessEvent.EventType.ALARM, 5)
for alarm in alarms:
    print(f"  {alarm.getSource()}: {alarm.getDescription()}")

print(f"\nTotal events in history: {event_bus.getHistorySize()}")
```

## 11. Data Export for ML Training

The **TimeSeriesExporter** enables efficient data collection and export for training machine learning models:

| Export Format | Use Case |
|--------------|----------|
| JSON | Structured API consumption, web services |
| CSV | ML training datasets, pandas/scikit-learn workflows |
| Snapshots | Point-in-time state capture |
| Deltas | Efficient streaming of changes only |

### Snapshot Contents

Each snapshot includes:
- **Timestamp** (ISO 8601 format)
- **Equipment pressures and temperatures**
- **Flow rates** (mass, molar, volumetric)
- **Phase compositions**
- **Custom annotations**

```python
# Access export classes via direct Java access
TimeSeriesExporter = jneqsim.process.util.export.TimeSeriesExporter
ProcessSnapshot = jneqsim.process.util.export.ProcessSnapshot

# Create a TimeSeriesExporter
exporter = TimeSeriesExporter("production_training_data")

print("=" * 60)
print("DATA EXPORT FOR ML TRAINING")
print("=" * 60)

# Simulate collecting data over time
print("\nCollecting time series data...")

for i in range(5):
    # In real usage, process conditions would change between snapshots
    # Here we just capture the current state
    exporter.collectSnapshot(process)
    print(f"  Snapshot {i+1}/5 collected")
    time.sleep(0.1)  # Small delay to get different timestamps

# Export to JSON (AI platform format)
print("\n--- JSON Export (AI Platform format) ---")
json_output = exporter.exportToJson()
# Print first 1000 chars as preview
print(json_output[:1000] + "..." if len(json_output) > 1000 else json_output)

# Export to CSV (ML training format)
print("\n--- CSV Export (ML training format) ---")
csv_output = exporter.exportToCsv()
# Print first 800 chars as preview
print(csv_output[:800] + "..." if len(csv_output) > 800 else csv_output)

# Show snapshot count
print(f"\n✓ Total snapshots collected: {exporter.getSnapshotCount()}")

# Clear for next batch
exporter.clear()
print("✓ Exporter cleared for next batch")
```

## 12. Complete Integration Example

This section demonstrates a complete **AI platform integration workflow** combining all the components:

```
┌─────────────────────────────────────────────────────────────────┐
│                   COMPLETE INTEGRATION FLOW                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐ │
│  │  Wells   │───►│  VFMs    │───►│ Calibra- │───►│  Event   │ │
│  │          │    │          │    │  tion    │    │  System  │ │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘ │
│       │              │                │               │        │
│       ▼              ▼                ▼               ▼        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              TimeSeriesExporter                         │   │
│  │           (JSON/CSV for AI Platform)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            │                                   │
│                            ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              ML Model Training                           │   │
│  │          (HybridModelAdapter)                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

The following code demonstrates a realistic integration scenario.

```python
def run_complete_integration_example():
    """
    Complete AI Platform Integration Example
    
    This demonstrates a realistic workflow combining:
    - Multi-well production system (direct Java access)
    - Virtual Flow Meters with uncertainty
    - Online calibration
    - Event monitoring
    - Data export for ML training
    """
    
    print("=" * 70)
    print("       COMPLETE AI PLATFORM INTEGRATION EXAMPLE")
    print("=" * 70)
    
    # =========================================================================
    # STEP 1: Setup Production System (Direct Java Access)
    # =========================================================================
    print("\n[STEP 1] Creating production system with 3 wells...")
    
    # Create realistic multiphase fluid using direct Java access
    fluid = jneqsim.thermo.system.SystemSrkEos(280.0, 85.0)
    fluid.addComponent("nitrogen", 0.02)
    fluid.addComponent("CO2", 0.03)
    fluid.addComponent("methane", 0.75)
    fluid.addComponent("ethane", 0.08)
    fluid.addComponent("propane", 0.05)
    fluid.addComponent("i-butane", 0.02)
    fluid.addComponent("n-butane", 0.02)
    fluid.addComponent("n-pentane", 0.015)
    fluid.addComponent("n-hexane", 0.015)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    
    # Create process system using direct Java access
    system = jneqsim.process.processmodel.ProcessSystem()
    
    # Well streams
    well_names = ["Well-A", "Well-B", "Well-C"]
    well_rates = [35000.0, 28000.0, 42000.0]  # Sm3/day
    
    for name, rate in zip(well_names, well_rates):
        well_stream = jneqsim.process.equipment.stream.Stream(name, fluid.clone())
        well_stream.setFlowRate(rate, "Sm3/day")
        well_stream.setTemperature(75.0, "C")
        well_stream.setPressure(85.0, "bara")
        system.add(well_stream)
    
    # Run the system
    system.run()
    print(f"  ✓ Created {len(well_names)} well streams")
    print(f"  ✓ Total production: {sum(well_rates):,.0f} Sm3/day")
    
    # =========================================================================
    # STEP 2: Initialize Virtual Flow Meters
    # =========================================================================
    print("\n[STEP 2] Initializing Virtual Flow Meters...")
    
    VirtualFlowMeter = jneqsim.process.measurementdevice.vfm.VirtualFlowMeter
    
    vfms = {}
    for i, name in enumerate(well_names):
        well_stream = system.getUnit(name)
        if well_stream is not None:
            vfm = VirtualFlowMeter(f"VFM-{name}", well_stream)
            vfm.setTemperatureUncertainty(0.5)
            vfm.setPressureUncertainty(0.1)
            vfm.setCalculationMethod("ORIFICE")
            vfms[name] = vfm
            print(f"  ✓ VFM configured for {name}")
    
    # =========================================================================
    # STEP 3: Setup Online Calibrators
    # =========================================================================
    print("\n[STEP 3] Setting up online calibrators...")
    
    OnlineCalibrator = jneqsim.process.calibration.OnlineCalibrator
    
    calibrators = {}
    for name in well_names:
        vfm = vfms.get(name)
        if vfm is not None:
            calibrator = OnlineCalibrator(f"CAL-{name}", vfm)
            calibrator.setMeasuredFlowRate(well_rates[well_names.index(name)] * 1.02)
            calibrators[name] = calibrator
    print(f"  ✓ {len(calibrators)} calibrators initialized")
    
    # =========================================================================
    # STEP 4: Configure Event Monitoring
    # =========================================================================
    print("\n[STEP 4] Configuring event monitoring...")
    
    ProcessEventBus = jneqsim.process.util.event.ProcessEventBus
    ProcessEvent = jneqsim.process.util.event.ProcessEvent
    
    event_bus = ProcessEventBus.getInstance()
    event_bus.clear()
    
    event_bus.publish(ProcessEvent.info("SYSTEM", "AI platform integration started"))
    print("  ✓ Event bus configured")
    
    # =========================================================================
    # STEP 5: Run Real-time Simulation Loop
    # =========================================================================
    print("\n[STEP 5] Running real-time simulation (5 iterations)...")
    print("-" * 70)
    
    TimeSeriesExporter = jneqsim.process.util.export.TimeSeriesExporter
    data_exporter = TimeSeriesExporter("production_data")
    
    for iteration in range(1, 6):
        print(f"\n  --- Iteration {iteration}/5 ---")
        
        # Calculate VFM results
        for name, vfm in vfms.items():
            result = vfm.calculate()
            if result is not None:
                gas_rate = result.getGasFlowRate()
                oil_rate = result.getOilFlowRate()
                gor = result.getGOR()
                
                print(f"    {name}: Gas={gas_rate:.0f} Sm3/d, Oil={oil_rate:.1f} m3/d, GOR={gor:.0f}")
        
        # Collect snapshot for ML training
        data_exporter.collectSnapshot(system)
        
        time.sleep(0.1)
    
    # =========================================================================
    # STEP 6: Generate Reports
    # =========================================================================
    print("\n" + "-" * 70)
    print("[STEP 6] Generating reports...")
    
    # Event summary
    all_events = list(event_bus.getRecentEvents(100))
    
    print(f"\n  Event Summary:")
    print(f"    - Total events: {len(all_events)}")
    
    # Export data
    print(f"\n  Data Export:")
    print(f"    - Snapshots collected: {data_exporter.getSnapshotCount()}")
    
    json_data = data_exporter.exportToJson()
    print(f"    - JSON export size: {len(json_data)} bytes")
    
    csv_data = data_exporter.exportToCsv()
    print(f"    - CSV export size: {len(csv_data)} bytes")
    
    print("\n" + "=" * 70)
    print("       INTEGRATION EXAMPLE COMPLETE")
    print("=" * 70)
    print("\nThe exported data can now be sent to AI platforms for:")
    print("  • Training hybrid physics-ML models")
    print("  • Real-time production optimization")
    print("  • Anomaly detection and predictive maintenance")
    print("  • Production forecasting")
    
    return {
        "system": system,
        "vfms": vfms,
        "calibrators": calibrators,
        "exporter": data_exporter,
        "event_count": event_bus.getHistorySize()
    }

# Run the complete example
results = run_complete_integration_example()
```

## 13. Summary and Next Steps

### What We Covered

This notebook demonstrated **NeqSim integration with AI-based production optimization platforms** using the Direct Java Access method:

| Component | Purpose | Java Package |
|-----------|---------|--------------|
| **Direct Java Access** | Full control via jneqsim | `from neqsim import jneqsim` |
| **Streaming** | Real-time data publishing | `jneqsim.process.streaming` |
| **VFM** | Virtual flow metering | `jneqsim.process.measurementdevice.vfm` |
| **Soft Sensors** | Derived properties | `jneqsim.process.measurementdevice.vfm` |
| **Uncertainty** | Confidence intervals | `jneqsim.process.util.uncertainty` |
| **Calibration** | Online model tuning | `jneqsim.process.calibration` |
| **Allocation** | Well back-allocation | `jneqsim.process.equipment.well.allocation` |
| **Events** | Monitoring and alerts | `jneqsim.process.util.event` |
| **Export** | ML training data | `jneqsim.process.util.export` |

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              AI Production Optimization Platform                │
│          (Digital Twin, ML Models, Optimization)                │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                    JSON/CSV Data Stream
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NeqSim Integration Layer                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐    │
│  │ VFM      │  │ Soft     │  │ Online   │  │ Time Series  │    │
│  │ Engines  │  │ Sensors  │  │ Calibr.  │  │ Exporter     │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NeqSim Core Engine                           │
│        Thermodynamics • Phase Equilibria • Properties          │
└─────────────────────────────────────────────────────────────────┘
```

### Next Steps

1. **Deploy to Production**: Configure real sensor inputs
2. **Train ML Models**: Use exported data with your AI platform
3. **Tune Parameters**: Adjust VFM and calibration settings
4. **Monitor Performance**: Track events and calibration quality
5. **Iterate**: Continuously improve hybrid physics-ML models

### Resources

- **NeqSim Documentation**: [neqsim.github.io](https://neqsim.github.io/)
- **NeqSim Python**: [github.com/equinor/neqsim-python](https://github.com/equinor/neqsim-python)
- **API Reference**: See `docs/ai_platform_integration.md`

