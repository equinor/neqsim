---
title: "Heat Integration and Pinch Analysis"
description: "Guide to using PinchAnalysis and HeatStream classes for process heat integration, minimum utility targeting, composite curves, and grand composite curves using the Linnhoff method."
---

# Heat Integration and Pinch Analysis

NeqSim provides a classical pinch analysis engine (Linnhoff method) for determining minimum heating and cooling utility requirements for a set of process streams.

## Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `PinchAnalysis` | `process.equipment.heatexchanger.heatintegration` | Main analysis engine: temperature intervals, heat cascade, utility targets, composite curves |
| `HeatStream` | `process.equipment.heatexchanger.heatintegration` | Data model for a hot or cold process stream with supply/target temperatures and heat capacity flow rate |

## Quick Start

```java
import neqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis;

// Create analysis with minimum approach temperature (deltaT_min)
PinchAnalysis pinch = new PinchAnalysis(10.0); // 10 C

// Add hot streams (need cooling): name, supplyTemp_C, targetTemp_C, MCp_kW/K
pinch.addHotStream("H1", 180, 80, 30);   // 180 to 80 C, MCp = 30 kW/K
pinch.addHotStream("H2", 150, 50, 15);   // 150 to 50 C, MCp = 15 kW/K

// Add cold streams (need heating)
pinch.addColdStream("C1", 30, 140, 20);  // 30 to 140 C, MCp = 20 kW/K
pinch.addColdStream("C2", 60, 120, 25);  // 60 to 120 C, MCp = 25 kW/K

// Run the analysis
pinch.run();

// Read results
double Qh = pinch.getMinimumHeatingUtility();  // kW
double Qc = pinch.getMinimumCoolingUtility();   // kW
double Tpinch = pinch.getPinchTemperatureC();   // hot-side pinch in C
double maxRecovery = pinch.getMaximumHeatRecovery(); // kW
```

## Python (Jupyter) Usage

```python
from neqsim import jneqsim

PinchAnalysis = jneqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis

pinch = PinchAnalysis(10.0)
pinch.addHotStream("H1", 180.0, 80.0, 30.0)
pinch.addHotStream("H2", 150.0, 50.0, 15.0)
pinch.addColdStream("C1", 30.0, 140.0, 20.0)
pinch.addColdStream("C2", 60.0, 120.0, 25.0)
pinch.run()

print(f"Min hot utility:  {pinch.getMinimumHeatingUtility():.1f} kW")
print(f"Min cold utility: {pinch.getMinimumCoolingUtility():.1f} kW")
print(f"Pinch temperature: {pinch.getPinchTemperatureC():.1f} C")
print(f"Max heat recovery: {pinch.getMaximumHeatRecovery():.1f} kW")
```

## Algorithm

The implementation follows the classical Linnhoff & Flower (1978) temperature interval method:

1. **Shifted temperatures** — Hot streams are shifted down by half the minimum approach temperature; cold streams are shifted up by half
2. **Temperature intervals** — All shifted temperatures define intervals where stream populations are constant
3. **Heat balance per interval** — Net heat available = (sum MCp_hot - sum MCp_cold) * dT
4. **Heat cascade** — Cumulative heat surplus/deficit cascaded from highest to lowest temperature
5. **Feasible cascade** — Hot utility added at the top to make all cascade values non-negative
6. **Results** — The added hot utility is the minimum heating requirement; the residual at the bottom is the minimum cooling requirement; the pinch is where the cascade equals zero

## Composite Curves

After running the analysis, composite curve data is accessible for plotting:

```java
Map<String, double[]> hotCurve = pinch.getHotCompositeCurve();
Map<String, double[]> coldCurve = pinch.getColdCompositeCurve();
Map<String, double[]> grandCurve = pinch.getGrandCompositeCurve();

// Each map contains "Q_kW" (cumulative enthalpy) and "T_K" (temperature in Kelvin)
double[] hotQ = hotCurve.get("Q_kW");
double[] hotT = hotCurve.get("T_K");
```

In Python with matplotlib:

```python
import matplotlib.pyplot as plt

hot_curve = pinch.getHotCompositeCurve()
cold_curve = pinch.getColdCompositeCurve()

hot_Q = list(hot_curve.get("Q_kW"))
hot_T = [t - 273.15 for t in hot_curve.get("T_K")]
cold_Q = list(cold_curve.get("Q_kW"))
cold_T = [t - 273.15 for t in cold_curve.get("T_K")]

plt.figure(figsize=(8, 6))
plt.plot(hot_Q, hot_T, 'r-o', label='Hot Composite')
plt.plot(cold_Q, cold_T, 'b-o', label='Cold Composite')
plt.xlabel('Cumulative Enthalpy (kW)')
plt.ylabel('Temperature (C)')
plt.title('Composite Curves')
plt.legend()
plt.grid(True)
plt.show()
```

## JSON Output

The `toJson()` method returns a comprehensive JSON report:

```java
String json = pinch.toJson();
```

The JSON includes minimum utilities, pinch temperatures, maximum heat recovery, stream data (names, types, temperatures, MCp, duties), and the minimum approach temperature.

## HeatStream

The `HeatStream` class represents a single process stream for pinch analysis:

```java
import neqsim.process.equipment.heatexchanger.heatintegration.HeatStream;

// Constructor: name, supplyTemp_C, targetTemp_C, MCp_kW/K
HeatStream h1 = new HeatStream("H1", 200, 100, 15);

// Auto-classified based on temperature direction
h1.getType();               // StreamType.HOT (supply > target)
h1.getEnthalpyChange();     // 1500.0 kW (MCp * |dT|)
h1.getSupplyTemperatureC(); // 200.0 C
h1.getTargetTemperatureC(); // 100.0 C
h1.getHeatCapacityFlowRate(); // 15.0 kW/K
```

Streams can be added directly to the analysis:

```java
pinch.addStream(h1); // auto-routes to hot or cold list based on type
```

## Related Documentation

- [Heat Exchangers](heat_exchangers.md)
- [Multi-Stream Heat Exchanger](multistream_heat_exchanger.md)
- [Thermal-Hydraulic Design](../mechanical_design/thermal_hydraulic_design.md)

## Integration with NeqSim Process Equipment

PinchAnalysis can extract stream data directly from NeqSim process equipment, eliminating the need to manually specify temperatures and MCp values.

### From a ProcessSystem (recommended)

The `fromProcessSystem` factory method scans all unit operations in a `ProcessSystem` and automatically extracts heat streams from Heaters, Coolers, HeatExchangers, and MultiStreamHeatExchanger2 (including LNGHeatExchanger):

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment, run process ...
process.run();

PinchAnalysis pinch = PinchAnalysis.fromProcessSystem(process, 10.0);
pinch.run();

double Qh = pinch.getMinimumHeatingUtility();
double Qc = pinch.getMinimumCoolingUtility();
```

In Python:

```python
PinchAnalysis = jneqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis

pinch = PinchAnalysis.fromProcessSystem(process, 10.0)
pinch.run()
print(f"Min hot utility: {pinch.getMinimumHeatingUtility():.1f} kW")
```

### From a two-stream HeatExchanger

```java
HeatExchanger hx = new HeatExchanger("HX-100", hotStream, coldStream);
// ... run the HX ...

PinchAnalysis pinch = new PinchAnalysis(10.0);
pinch.addStreamsFromHeatExchanger(hx);
// Adds "HX-100 hot-side" and "HX-100 cold-side" automatically
```

### From a MultiStreamHeatExchanger2 or LNGHeatExchanger

```java
MultiStreamHeatExchanger2 mshe = new MultiStreamHeatExchanger2("LNG-HX");
// ... configure and run ...

PinchAnalysis pinch = new PinchAnalysis(10.0);
pinch.addStreamsFromHeatExchanger(mshe);
// Iterates through all internal streams
```

### From individual process streams

For fine-grained control, add individual NeqSim streams with a specified outlet temperature:

```java
PinchAnalysis pinch = new PinchAnalysis(10.0);
pinch.addProcessStream("Feed cooler", feedStream, 40.0);
// Extracts inlet temperature, computes MCp from enthalpy difference
```

### Equipment type mapping

| Equipment | Method used | Streams extracted |
|-----------|-------------|-------------------|
| `Cooler` | Heater/Cooler path | 1 hot stream (process-side cooling) |
| `Heater` | Heater/Cooler path | 1 cold stream (process-side heating) |
| `HeatExchanger` | `addStreamsFromHeatExchanger(HeatExchanger)` | 1 hot + 1 cold (both sides) |
| `MultiStreamHeatExchanger2` | `addStreamsFromHeatExchanger(MSHE2)` | N streams (all internal streams) |
| `LNGHeatExchanger` | Same as MSHE2 (extends it) | N streams (all internal streams) |
