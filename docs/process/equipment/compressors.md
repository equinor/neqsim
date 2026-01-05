# Compressor Equipment

Documentation for compression equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Compressor Types](#compressor-types)
- [Calculation Methods](#calculation-methods)
- [Performance Curves](#performance-curves)
- [Surge and Stone Wall](#surge-and-stone-wall)
- [Usage Examples](#usage-examples)

> **📖 Detailed Curve Documentation:** For comprehensive information on compressor curves, 
> including multi-speed vs single-speed handling, surge curves, and stone wall curves, 
> see [Compressor Curves and Performance Maps](compressor_curves.md).

---

## Overview

**Location:** `neqsim.process.equipment.compressor`

**Classes:**
- `Compressor` - General compressor
- `CompressorInterface` - Compressor interface
- `CompressorChartInterface` - Performance map interface

---

## Basic Usage

```java
import neqsim.process.equipment.compressor.Compressor;

Compressor compressor = new Compressor("K-100", inletStream);
compressor.setOutletPressure(80.0, "bara");
compressor.setIsentropicEfficiency(0.75);
compressor.run();

// Results
double power = compressor.getPower("kW");
double outletT = compressor.getOutletStream().getTemperature("C");
double polytropicHead = compressor.getPolytropicHead("kJ/kg");

System.out.println("Power: " + power + " kW");
System.out.println("Outlet temperature: " + outletT + " °C");
```

---

## Calculation Methods

### Isentropic Compression

```java
compressor.setIsentropicEfficiency(0.75);  // 75%
compressor.setUsePolytropicCalc(false);
compressor.run();

double isentropicHead = compressor.getIsentropicHead("kJ/kg");
double isentropicPower = compressor.getPower("kW");
```

### Polytropic Compression

More accurate for real gas behavior.

```java
compressor.setPolytropicEfficiency(0.80);  // 80%
compressor.setUsePolytropicCalc(true);
compressor.run();

double polytropicHead = compressor.getPolytropicHead("kJ/kg");
double polytropicExponent = compressor.getPolytropicExponent();
```

### Power Specified

```java
compressor.setPower(5000.0, "kW");  // Specify power
compressor.setIsentropicEfficiency(0.75);
compressor.run();

double outletP = compressor.getOutletStream().getPressure("bara");
```

---

## Efficiency Relationships

### Isentropic Efficiency

$$\eta_{is} = \frac{H_{is}}{H_{actual}} = \frac{T_{2s} - T_1}{T_2 - T_1}$$

### Polytropic Efficiency

$$\eta_p = \frac{n-1}{n} \cdot \frac{k}{k-1}$$

Where:
- $n$ = polytropic exponent
- $k$ = isentropic exponent (Cp/Cv)

---

## Performance Curves

NeqSim supports detailed compressor performance maps with multiple speed curves. For comprehensive documentation, see [Compressor Curves and Performance Maps](compressor_curves.md).

### Setting Compressor Map

```java
// Define speed curves
double[] speeds = {8000, 9000, 10000, 11000};  // RPM

// For each speed: arrays of flow, head, efficiency
double[][] flows = { {flow1_curve1, flow2_curve1}, {flow1_curve2, flow2_curve2}, ... };
double[][] heads = { {head1_curve1, head2_curve1}, {head1_curve2, head2_curve2}, ... };
double[][] efficiencies = { {eff1_curve1, eff2_curve1}, {eff1_curve2, eff2_curve2}, ... };

CompressorChartInterface chart = compressor.getCompressorChart();
chart.setCurves(chartConditions, speeds, flows, heads, flows, efficiencies);
chart.setHeadUnit("kJ/kg");
compressor.setSpeed(10000);  // Operating speed
```

### Operating Point

```java
compressor.setSpeed(10000);  // RPM
compressor.run();

double actualFlow = compressor.getActualFlow("m3/hr");
double head = compressor.getPolytropicHead("kJ/kg");
double efficiency = compressor.getPolytropicEfficiency();
```

---

## Surge and Stone Wall

### Multi-Speed vs Single-Speed Compressors

| Compressor Type | Surge/Stone Wall | Setting Method |
|-----------------|------------------|----------------|
| Multi-speed (≥2 speeds) | Curves (interpolated) | Multiple flow/head points |
| Single-speed (1 speed) | Single points (constant) | Single flow/head point |

### Checking Operating Limits

```java
// Distance to surge (positive = above surge, safe)
double distanceToSurge = compressor.getDistanceToSurge();

// Distance to stone wall (positive = below choke, safe)
double distanceToStoneWall = compressor.getDistanceToStoneWall();

// Check if in surge
boolean isSurge = compressor.getCompressorChart().getSurgeCurve().isSurge(head, flow);

// Get surge flow rate
double surgeFlow = compressor.getSurgeFlowRate();
```

### Single-Speed Compressor Example

```java
// For single-speed compressors, surge is a single point
double[] surgeFlow = {5607.45};  // Single value
double[] surgeHead = {150.0};   // Single value
compressor.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);

// Stone wall is also a single point
double[] stoneWallFlow = {9758.49};
double[] stoneWallHead = {112.65};
compressor.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallFlow, stoneWallHead);
```

> **📖 See Also:** [Compressor Curves and Performance Maps](compressor_curves.md) for detailed
> documentation on curve setup, interpolation methods, and Python examples.

---

## Compressor Staging

### Multi-Stage with Intercooling

```java
ProcessSystem process = new ProcessSystem();

// Stage 1: 20 -> 50 bar
Compressor stage1 = new Compressor("K-100A", inletStream);
stage1.setOutletPressure(50.0, "bara");
stage1.setPolytropicEfficiency(0.78);
process.add(stage1);

// Intercooler
Cooler intercooler = new Cooler("E-100", stage1.getOutletStream());
intercooler.setOutTemperature(40.0, "C");
process.add(intercooler);

// Stage 2: 50 -> 120 bar
Compressor stage2 = new Compressor("K-100B", intercooler.getOutletStream());
stage2.setOutletPressure(120.0, "bara");
stage2.setPolytropicEfficiency(0.78);
process.add(stage2);

// Aftercooler
Cooler aftercooler = new Cooler("E-101", stage2.getOutletStream());
aftercooler.setOutTemperature(40.0, "C");
process.add(aftercooler);

process.run();

// Total power
double totalPower = stage1.getPower("kW") + stage2.getPower("kW");
System.out.println("Total compression power: " + totalPower + " kW");
```

### Optimal Pressure Ratio

For minimum work with equal stage ratios:

$$r_{stage} = \left(\frac{P_{out}}{P_{in}}\right)^{1/n}$$

```java
double overallRatio = 120.0 / 20.0;  // 6:1
int numStages = 2;
double stageRatio = Math.pow(overallRatio, 1.0 / numStages);  // √6 ≈ 2.45
```

---

## Antisurge Control

```java
// Recycle valve for antisurge
Splitter recycle = new Splitter("Antisurge Recycle", stage2.getOutletStream());
recycle.setSplitFactors(new double[]{0.9, 0.1});  // 10% recycle

// Mix with inlet
Mixer mixer = new Mixer("Inlet Mixer");
mixer.addStream(inletStream);
mixer.addStream(recycle.getSplitStream(1));
```

---

## Power Calculation Details

### Real Gas Effects

```java
// Account for compressibility
double Z1 = compressor.getInletStream().getZ();
double Z2 = compressor.getOutletStream().getZ();
double Zavg = (Z1 + Z2) / 2;
```

### Mechanical Efficiency

```java
// Include mechanical losses
compressor.setMechanicalEfficiency(0.98);
double shaftPower = compressor.getShaftPower("kW");
double gasHorsepower = compressor.getGasHorsepower("hp");
```

---

## Example: Export Gas Compression

```java
// Export gas at 100 MSm³/day
SystemInterface gas = new SystemSrkEos(288.15, 30.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.04);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("nitrogen", 0.01);
gas.setMixingRule("classic");

Stream inlet = new Stream("Inlet Gas", gas);
inlet.setFlowRate(100.0, "MSm3/day");
inlet.setTemperature(30.0, "C");
inlet.setPressure(30.0, "bara");

ProcessSystem process = new ProcessSystem();
process.add(inlet);

// 3-stage compression to 200 bar
double[] stagePressures = {55, 105, 200};

Stream currentStream = inlet;
for (int i = 0; i < 3; i++) {
    Compressor comp = new Compressor("K-10" + (i+1), currentStream);
    comp.setOutletPressure(stagePressures[i], "bara");
    comp.setPolytropicEfficiency(0.78);
    comp.setUsePolytropicCalc(true);
    process.add(comp);
    
    Cooler cooler = new Cooler("E-10" + (i+1), comp.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    process.add(cooler);
    
    currentStream = cooler.getOutletStream();
}

process.run();

// Report
System.out.println("\n=== Compression Summary ===");
double totalPower = 0;
for (int i = 1; i <= 3; i++) {
    Compressor c = (Compressor) process.getUnit("K-10" + i);
    totalPower += c.getPower("MW");
    System.out.printf("Stage %d: %.2f bar -> %.2f bar, %.2f MW%n",
        i, c.getInletStream().getPressure("bara"),
        c.getOutletStream().getPressure("bara"),
        c.getPower("MW"));
}
System.out.println("Total power: " + totalPower + " MW");
```

---

## Automatic Curve Generation

When manufacturer performance data is not available, NeqSim can automatically generate realistic compressor curves using predefined templates.

### Quick Start

```java
// 1. Create and run compressor to establish design point
Compressor comp = new Compressor("K-100", inletStream);
comp.setOutletPressure(100.0, "bara");
comp.setPolytropicEfficiency(0.78);
comp.setSpeed(10000);
comp.run();

// 2. Generate curves from template
CompressorChartGenerator generator = new CompressorChartGenerator(comp);
CompressorChartInterface chart = generator.generateFromTemplate("PIPELINE", 5);

// 3. Apply and use
comp.setCompressorChart(chart);
comp.run();
```

### Available Templates

| Category | Templates |
|----------|-----------|
| **Basic** | `CENTRIFUGAL_STANDARD`, `CENTRIFUGAL_HIGH_FLOW`, `CENTRIFUGAL_HIGH_HEAD` |
| **Application** | `PIPELINE`, `EXPORT`, `INJECTION`, `GAS_LIFT`, `REFRIGERATION`, `BOOSTER` |
| **Type** | `SINGLE_STAGE`, `MULTISTAGE_INLINE`, `INTEGRALLY_GEARED`, `OVERHUNG` |

### Template Selection

| Use Case | Recommended Template |
|----------|---------------------|
| Gas transmission | `PIPELINE` |
| Offshore export | `EXPORT` |
| Gas injection/EOR | `INJECTION` |
| Artificial lift | `GAS_LIFT` |
| LNG/refrigeration | `REFRIGERATION` |
| General purpose | `CENTRIFUGAL_STANDARD` |

> **📖 Detailed Documentation:** See [Compressor Curves - Automatic Generation](compressor_curves.md#automatic-curve-generation) 
> for complete API reference, advanced corrections, and examples.

---

## Related Documentation

- [Compressor Curves](compressor_curves.md) - Detailed curve documentation, templates, and MW correction
- [Process Package](../README.md) - Package overview
- [Expanders](expanders.md) - Expansion equipment
- [Pumps](pumps.md) - Liquid compression
