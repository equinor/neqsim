# Pump Theory and Implementation in NeqSim

## Overview

NeqSim provides comprehensive centrifugal pump simulation through the `Pump` and `PumpChart` classes. The implementation supports:

- Manufacturer pump curves with affinity law scaling
- Head, efficiency, and NPSH curve interpolation
- Density correction for non-standard fluids
- Cavitation detection and operating status monitoring
- Surge and stonewall detection

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

## Head Unit Options

| Unit | Description | Pressure Calculation |
|------|-------------|---------------------|
| `"meter"` | Meters of fluid | ΔP = ρ·g·H |
| `"kJ/kg"` | Specific energy | ΔP = E·ρ·1000 |

---

## Test Coverage

The pump implementation includes comprehensive tests:

| Test Class | Coverage |
|------------|----------|
| `PumpTest` | Basic pump operations |
| `PumpChartTest` | Curve interpolation |
| `PumpAffinityLawTest` | Affinity law scaling |
| `PumpNPSHTest` | Cavitation detection |
| `PumpNPSHCurveTest` | NPSH curve handling |
| `PumpDensityCorrectionTest` | Density correction |

---

## References

1. Centrifugal Pumps, I.J. Karassik et al., McGraw-Hill
2. Pump Handbook, Igor J. Karassik, McGraw-Hill
3. API 610 - Centrifugal Pumps for Petroleum, Petrochemical and Natural Gas Industries
4. ISO 9906 - Rotodynamic pumps - Hydraulic performance acceptance tests
