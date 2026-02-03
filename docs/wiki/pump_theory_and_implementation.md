---
title: "Pump Theory and Implementation in NeqSim"
description: "NeqSim provides comprehensive centrifugal pump simulation through the `Pump` and `PumpChart` classes. The implementation supports:"
---

# Pump Theory and Implementation in NeqSim

## Overview

NeqSim provides comprehensive centrifugal pump simulation through the `Pump` and `PumpChart` classes. The implementation supports:

- Manufacturer pump curves with affinity law scaling
- Head, efficiency, and NPSH curve interpolation
- Density correction for non-standard fluids
- Cavitation detection and operating status monitoring
- Surge and stonewall detection

Also see [pump usage guide](https://github.com/equinor/neqsim/blob/master/docs/wiki/pump_usage_guide.md).

---

## Theoretical Background

### Affinity Laws (Similarity Laws)

The affinity laws relate pump performance at different speeds:

| Parameter | Relationship |
|-----------|-------------|
| Flow | Q₂/Q₁ = N₂/N₁ |
| Head | H₂/H₁ = (N₂/N₁)² |
| Power | P₂/P₁ = (N₂/N₁)³ |
| NPSH | NPSH₂/NPSH₁ = (N₂/N₁)² |

### Hydraulic Power and Efficiency

```
P_hydraulic = ρ·g·Q·H = Q·ΔP
P_shaft = P_hydraulic / η
```

### Net Positive Suction Head (NPSH)

```
NPSHₐ = (P_suction - P_vapor) / (ρ·g) + v²/(2g)
```

Cavitation occurs when NPSHₐ < NPSHᵣ. A safety margin of 1.3× is typically required.

### Density Correction

Pump curves measured with water require correction for other fluids:

```
H_actual = H_chart × (ρ_chart / ρ_actual)
```

---

## Implementation

### Class Structure

```
Pump (PumpInterface)
├── PumpChart (PumpChartInterface)
│   ├── PumpCurve (individual speed curves)
│   └── PumpChartAlternativeMapLookupExtrapolate (alternative implementation)
└── PumpMechanicalDesign
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `Pump` | Main pump equipment model |
| `PumpChart` | Performance curve management |
| `PumpChartInterface` | Interface for pump chart implementations |

---

## Usage Guide

### Basic Pump Setup

```java
// Create fluid and stream
SystemInterface fluid = new SystemSrkEos(298.15, 2.0);
fluid.addComponent("water", 1.0);
Stream feedStream = new Stream("Feed", fluid);
feedStream.run();

// Create pump with outlet pressure
Pump pump = new Pump("MainPump", feedStream);
pump.setOutletPressure(10.0, "bara");
pump.setIsentropicEfficiency(0.75);
pump.run();

System.out.println("Power: " + pump.getPower("kW") + " kW");
System.out.println("Outlet T: " + pump.getOutletTemperature() + " K");
```

### Using Pump Curves

```java
// Define pump curves at multiple speeds
double[] speed = {1000.0, 1500.0};
double[][] flow = {
    {10, 20, 30, 40, 50},      // m³/hr at 1000 rpm
    {15, 30, 45, 60, 75}       // m³/hr at 1500 rpm
};
double[][] head = {
    {120, 115, 108, 98, 85},   // meters at 1000 rpm
    {270, 259, 243, 220, 191}  // meters at 1500 rpm
};
double[][] efficiency = {
    {65, 75, 82, 80, 72},      // % at 1000 rpm
    {67, 77, 84, 82, 74}       // % at 1500 rpm
};

// chartConditions: [refMW, refTemp, refPressure, refZ, refDensity]
double[] chartConditions = {18.0, 298.15, 1.0, 1.0, 998.0};

Pump pump = new Pump("ChartPump", feedStream);
pump.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);
pump.getPumpChart().setHeadUnit("meter");
pump.setSpeed(1200.0);
pump.run();
```

### Density Correction

When pumping fluids with different density than the chart test fluid:

```java
// Option 1: Set via chartConditions (5th element)
double[] chartConditions = {18.0, 298.15, 1.0, 1.0, 998.0}; // 998 kg/m³ reference
pump.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);

// Option 2: Set directly
pump.getPumpChart().setReferenceDensity(998.0);

// Check if correction is active
if (pump.getPumpChart().hasDensityCorrection()) {
    double correctedHead = pump.getPumpChart().getCorrectedHead(flow, speed, actualDensity);
}
```

### NPSH Monitoring

```java
// Enable NPSH checking
pump.setCheckNPSH(true);
pump.setNPSHMargin(1.3);  // Safety factor

// Check cavitation risk
double npshAvailable = pump.getNPSHAvailable();
double npshRequired = pump.getNPSHRequired();
boolean cavitating = pump.isCavitating();

// Set NPSH curve from manufacturer data
double[][] npshCurve = {
    {2.0, 2.5, 3.2, 4.0, 5.2},  // NPSHr at 1000 rpm
    {4.5, 5.6, 7.2, 9.0, 11.7}  // NPSHr at 1500 rpm
};
pump.getPumpChart().setNPSHCurve(npshCurve);
```

### Operating Status Monitoring

```java
// Check operating status
String status = pump.getPumpChart().getOperatingStatus(flowRate, speed);
// Returns: "OPTIMAL", "NORMAL", "LOW_EFFICIENCY", "SURGE", or "STONEWALL"

// Check specific conditions
boolean surging = pump.getPumpChart().checkSurge2(flowRate, speed);
boolean stonewall = pump.getPumpChart().checkStoneWall(flowRate, speed);

// Get best efficiency point
double bepFlow = pump.getPumpChart().getBestEfficiencyFlowRate();
double specificSpeed = pump.getPumpChart().getSpecificSpeed();
```

---

## Complete Example: Pump with Suction System (Python)

This example demonstrates a realistic pump system with:
- Feed from an upstream separator
- Control valve at separator outlet
- Suction pipeline with elevation (static head)
- Pump with manufacturer curves and NPSH monitoring

```python
import neqsim

# Get feed stream from upstream process (e.g., oil from separator)
pump_feed = oseberg_process.get('main process').getUnit('3RD stage separator').getOilOutStream()

# === Separator Outlet Control Valve ===
# Controls flow from separator to pump suction
separatorValve = neqsim.process.equipment.valve.ThrottlingValve("SeparatorOutletValve", pump_feed)
separatorValve.setCv(200)                   # Valve Cv (flow coefficient in US gpm/psi^0.5)
separatorValve.setPercentValveOpening(80)   # 80% open - allows for control margin
separatorValve.setIsCalcOutPressure(True)   # Calculate outlet pressure from Cv
separatorValve.run()

# === Suction Pipeline ===
# Models pressure drop and elevation effects on NPSH
suctionLine = neqsim.process.equipment.pipeline.PipeBeggsAndBrills("SuctionLine", separatorValve.getOutletStream())
suctionLine.setLength(20.0)              # Pipe length in meters
suctionLine.setDiameter(0.2)             # Pipe inner diameter in meters
suctionLine.setPipeWallRoughness(1.0e-5) # Internal roughness in meters
suctionLine.setElevation(-20)            # Negative = pump is 20m below separator (adds static head)
suctionLine.run()

# === Centrifugal Pump ===
pump1 = neqsim.process.equipment.pump.Pump('oil pump', suctionLine.getOutStream())
pump1.setOutletPressure(60.0, 'bara')    # Target discharge pressure

# Enable NPSH monitoring with 1.3x safety margin
pump1.setCheckNPSH(True)
pump1.setNPSHMargin(1.3)

# Define manufacturer pump curves (single speed)
speed = [3259]                           # rpm
flow = [[1, 50, 70, 130]]               # m³/hr
head = [[250, 240, 230, 180]]           # meters
eff = [[5, 40, 50, 52]]                 # efficiency %

# NPSHr curve (meters) - must match flow array dimensions
npsh = [[2.0, 4.3, 6.0, 8.0]]

# Configure pump chart
pump1.getPumpChart().setCurves([], speed, flow, head, eff)
pump1.getPumpChart().setNPSHCurve(npsh)
pump1.getPumpChart().setHeadUnit("meter")
pump1.setSpeed(3259)

pump1.run()

# === Results ===
print("=== Pump & Suction Line Results ===")
print(f"Flow rate (m3/hr): {pump_feed.getFlowRate('idSm3/hr'):.2f}")
print(f"Separator outlet pressure (bara): {pump_feed.getPressure('bara'):.2f}")
print(f"Pump inlet pressure (bara): {pump1.getInletPressure():.2f}")
print(f"Pump outlet pressure (bara): {pump1.getOutletPressure():.2f}")
print(f"Pump head (m): {pump1.getPumpChart().getHead(pump_feed.getFlowRate('m3/hr'), 3259):.1f}")
print(f"Pump efficiency (%): {pump1.getPumpChart().getEfficiency(pump_feed.getFlowRate('m3/hr'), 3259):.1f}")
print(f"Pump NPSHa (meter): {pump1.getNPSHAvailable():.2f}")
print(f"Pump NPSHr (meter): {pump1.getNPSHRequired():.2f}")
print(f"Pump power (kW): {pump1.getPower('kW'):.1f}")
print(f"Cavitation risk: {'YES - INCREASE SUCTION PRESSURE' if pump1.isCavitating() else 'NO'}")
```

### Key Points

1. **Suction System Design**: The suction line elevation affects NPSHₐ. Negative elevation (pump below source) adds static head, improving NPSH margin.

2. **Control Valve Sizing**: The Cv value determines pressure drop at the given flow. Use `setIsCalcOutPressure(True)` to calculate outlet pressure from Cv.

3. **NPSH Monitoring**: Enable with `setCheckNPSH(True)`. The pump calculates:
   - **NPSHₐ** from suction conditions (pressure, temperature, vapor pressure)
   - **NPSHᵣ** from the manufacturer curve (interpolated at operating flow)
   - Warns if NPSHₐ < margin × NPSHᵣ

4. **Pump Curves**: The `setCurves()` method accepts:
   - Empty array `[]` for chartConditions (or include reference density as 5th element)
   - Speed array (can be single or multiple speeds)
   - 2D arrays for flow, head, efficiency (one row per speed)

5. **NPSH Curve**: Must be set separately via `setNPSHCurve()` with same dimensions as flow array.

---

## API Reference

### Pump Class

#### Key Methods

| Method | Description |
|--------|-------------|
| `setOutletPressure(double, String)` | Set target outlet pressure |
| `setIsentropicEfficiency(double)` | Set pump efficiency (0-1) |
| `setSpeed(double)` | Set pump speed in rpm |
| `getPower()` | Get shaft power in watts |
| `getPower(String)` | Get power in specified unit |
| `getNPSHAvailable()` | Calculate available NPSH in meters |
| `getNPSHRequired()` | Get required NPSH from chart or estimate |
| `isCavitating()` | Check if pump is at cavitation risk |
| `setCheckNPSH(boolean)` | Enable/disable NPSH monitoring |
| `getPumpChart()` | Get the pump chart object |

### PumpChart Class

#### Curve Setup Methods

| Method | Description |
|--------|-------------|
| `setCurves(double[], double[], double[][], double[][], double[][])` | Set complete pump curves |
| `setHeadUnit(String)` | Set head unit: "meter" or "kJ/kg" |
| `setNPSHCurve(double[][])` | Set NPSH required curves |
| `setReferenceDensity(double)` | Set reference density for correction |

#### Performance Query Methods

| Method | Description |
|--------|-------------|
| `getHead(double, double)` | Get head at flow and speed |
| `getCorrectedHead(double, double, double)` | Get density-corrected head |
| `getEfficiency(double, double)` | Get efficiency at flow and speed |
| `getNPSHRequired(double, double)` | Get NPSH required at flow and speed |
| `getSpeed(double, double)` | Calculate speed for given flow and head |

#### Monitoring Methods

| Method | Description |
|--------|-------------|
| `getOperatingStatus(double, double)` | Get operating status string |
| `checkSurge2(double, double)` | Check if in surge condition |
| `checkStoneWall(double, double)` | Check if at stonewall |
| `getBestEfficiencyFlowRate()` | Get flow at BEP |
| `getSpecificSpeed()` | Calculate pump specific speed |
| `hasDensityCorrection()` | Check if density correction is active |
| `hasNPSHCurve()` | Check if NPSH curve is available |

---

## Chart Conditions Array

The `chartConditions` array passed to `setCurves()` contains reference conditions:

| Index | Parameter | Unit | Description |
|-------|-----------|------|-------------|
| 0 | refMW | kg/kmol | Reference molecular weight |
| 1 | refTemperature | K | Reference temperature |
| 2 | refPressure | bara | Reference pressure |
| 3 | refZ | - | Reference compressibility |
| 4 | refDensity | kg/m³ | Reference fluid density (optional) |

**Note:** Element [4] is optional for backward compatibility. If omitted, no density correction is applied.

---

## Viscosity Correction (Heavy Oil / Viscous Fluids)

Pump performance is significantly affected by fluid viscosity. Curves measured with water or light oil require correction when pumping viscous fluids like heavy crude oil.

### Hydraulic Institute (HI) Method

NeqSim implements the Hydraulic Institute viscosity correction method for centrifugal pumps. The correction uses the B parameter:

```
B = 26.6 × ν^0.5 × H^0.0625 / (Q^0.375 × N^0.25)
```

Where:
- ν = kinematic viscosity (cSt)
- H = head at BEP (meters)
- Q = flow at BEP (m³/hr)
- N = speed (rpm)

### Correction Factors

| Parameter | Factor | Description |
|-----------|--------|-------------|
| Flow | Cq | Q_viscous = Q_water × Cq |
| Head | Ch | H_viscous = H_water × Ch |
| Efficiency | Cη | η_viscous = η_water × Cη |

**Valid range:** 4 - 4000 cSt (below 4 cSt, water properties assumed)

### Usage Example (Java)

```java
// Create pump with chart
Pump pump = new Pump("ViscousPump", feedStream);
pump.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);

// Enable viscosity correction
pump.getPumpChart().setReferenceViscosity(1.0);       // Chart measured with water (1 cSt)
pump.getPumpChart().setUseViscosityCorrection(true);  // Enable correction

// Set pump parameters
pump.getPumpChart().setReferenceFlow(100.0);          // BEP flow (m³/hr)
pump.getPumpChart().setReferenceHead(100.0);          // BEP head (meters)
pump.getPumpChart().setReferenceSpeed(1500.0);        // Reference speed (rpm)

pump.run();

// Check applied corrections
System.out.println("Flow correction factor Cq: " + pump.getPumpChart().getFlowCorrectionFactor());
System.out.println("Head correction factor Ch: " + pump.getPumpChart().getHeadCorrectionFactor());
System.out.println("Efficiency correction Cη: " + pump.getPumpChart().getEfficiencyCorrectionFactor());
```

### Usage Example (Python)

```python
import neqsim
from neqsim.process import stream, pump

# Create stream with viscous oil
oil = neqsim.thermo.system.SystemSrkEos(323.15, 5.0)
oil.addComponent("nC20", 1.0)  # Heavy hydrocarbon
oil.setMixingRule("classic")

feed = stream.stream("ViscousOilFeed", oil)
feed.setFlowRate(100.0, "kg/hr")
feed.run()

# Create pump with viscosity correction
viscous_pump = pump.pump("OilBooster", feed)
viscous_pump.getPumpChart().setReferenceViscosity(1.0)
viscous_pump.getPumpChart().setUseViscosityCorrection(True)
viscous_pump.setOutletPressure(10.0, "bara")
viscous_pump.run()

print(f"Actual viscosity: {feed.getFluid().getKinematicViscosity('cSt'):.1f} cSt")
print(f"Head correction: {viscous_pump.getPumpChart().getHeadCorrectionFactor():.3f}")
print(f"Efficiency correction: {viscous_pump.getPumpChart().getEfficiencyCorrectionFactor():.3f}")
```

---

## ESP Pump (Electric Submersible Pump)

The `ESPPump` class extends `Pump` for handling multiphase gas-liquid flows commonly encountered in oil well production.

### Key Features

- Multi-stage impeller design
- Gas Void Fraction (GVF) calculation at pump inlet
- Head degradation model for gassy conditions
- Gas separator modeling
- Surge and gas lock detection

### GVF Degradation Model

Head degradation follows a quadratic relationship:

```
f = 1 - A × GVF - B × GVF²
```

Where default coefficients are: A = 0.5, B = 2.0

### Operating Limits

| Condition | Default Threshold | Description |
|-----------|------------------|-------------|
| Surging | GVF > 15% | Unstable operation begins |
| Gas Lock | GVF > 30% | Pump loses prime, flow stops |

### Usage Example (Java)

```java
// Create multiphase stream (gas + liquid)
SystemInterface fluid = new SystemSrkEos(323.15, 30.0);
fluid.addComponent("methane", 0.05);     // 5% gas
fluid.addComponent("n-heptane", 0.95);   // 95% liquid
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream wellStream = new Stream("WellProduction", fluid);
wellStream.setFlowRate(1000.0, "kg/hr");
wellStream.run();

// Create ESP pump
ESPPump esp = new ESPPump("ESP-1", wellStream);
esp.setNumberOfStages(100);           // 100-stage pump
esp.setHeadPerStage(10.0);            // 10 m head per stage

// Configure GVF handling
esp.setMaxGVF(0.30);                  // 30% max GVF before gas lock
esp.setSurgingGVF(0.15);              // 15% - surging onset
esp.setHasGasSeparator(true);         // Include rotary gas separator
esp.setGasSeparatorEfficiency(0.60);  // 60% gas separation

esp.run();

// Check operating status
System.out.println("GVF at inlet: " + (esp.getGasVoidFraction() * 100) + "%");
System.out.println("Head degradation: " + (1 - esp.getHeadDegradationFactor()) * 100 + "% loss");
System.out.println("Surging: " + esp.isSurging());
System.out.println("Gas locked: " + esp.isGasLocked());
System.out.println("Pressure boost: " + (esp.getOutletPressure() - esp.getInletPressure()) + " bara");
```

### Usage Example (Python)

```python
import neqsim
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.equipment.pump import ESPPump

# Create multiphase well fluid
well_fluid = SystemSrkEos(353.15, 25.0)
well_fluid.addComponent("methane", 0.08)
well_fluid.addComponent("n-heptane", 0.92)
well_fluid.setMixingRule("classic")
well_fluid.setMultiPhaseCheck(True)

well_stream = neqsim.process.stream.stream("WellStream", well_fluid)
well_stream.setFlowRate(2000.0, "kg/hr")
well_stream.run()

# Create and configure ESP
esp = ESPPump("ESP-1", well_stream)
esp.setNumberOfStages(80)
esp.setHeadPerStage(12.0)
esp.setMaxGVF(0.25)
esp.setHasGasSeparator(True)
esp.setGasSeparatorEfficiency(0.70)
esp.run()

# Monitor performance
print(f"Inlet GVF: {esp.getGasVoidFraction()*100:.1f}%")
print(f"Head degradation factor: {esp.getHeadDegradationFactor():.3f}")
print(f"Effective head: {esp.calculateTotalHead():.1f} m")
print(f"Is surging: {esp.isSurging()}")
```

### ESPPump API Reference

| Method | Description |
|--------|-------------|
| `setNumberOfStages(int)` | Set number of impeller stages |
| `setHeadPerStage(double)` | Set head per stage (meters) |
| `setMaxGVF(double)` | Set gas lock threshold (0-1) |
| `setSurgingGVF(double)` | Set surging onset threshold (0-1) |
| `setHasGasSeparator(boolean)` | Enable rotary gas separator |
| `setGasSeparatorEfficiency(double)` | Set separator efficiency (0-1) |
| `getGasVoidFraction()` | Get calculated inlet GVF |
| `getHeadDegradationFactor()` | Get head degradation (0-1) |
| `isSurging()` | Check if pump is surging |
| `isGasLocked()` | Check if pump has lost prime |
| `calculateTotalHead()` | Get total developed head |

---

## Head Unit Options

| Unit | Description | Pressure Calculation |
|------|-------------|---------------------|
| `"meter"` | Meters of fluid | ΔP = ρ·g·H |
| `"kJ/kg"` | Specific energy | ΔP = E·ρ·1000 |

---

## Test Coverage

The pump implementation includes comprehensive tests:

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `PumpTest` | 3 | Basic pump operations |
| `PumpChartTest` | 3 | Curve interpolation |
| `PumpAffinityLawTest` | 6 | Affinity law scaling |
| `PumpNPSHTest` | 8 | Cavitation detection |
| `PumpNPSHCurveTest` | 12 | NPSH curve handling |
| `PumpDensityCorrectionTest` | 7 | Density correction |
| `PumpViscosityCorrectionTest` | 12 | HI viscosity correction method |
| `ESPPumpTest` | 12 | ESP multiphase handling |

**Total: 63 tests**

---

## References

1. Centrifugal Pumps, I.J. Karassik et al., McGraw-Hill
2. Pump Handbook, Igor J. Karassik, McGraw-Hill
3. API 610 - Centrifugal Pumps for Petroleum, Petrochemical and Natural Gas Industries
4. ISO 9906 - Rotodynamic pumps - Hydraulic performance acceptance tests
