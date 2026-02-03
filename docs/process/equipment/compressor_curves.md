---
title: Compressor Curves and Performance Maps
description: Detailed documentation for compressor performance curves in NeqSim, including multi-speed and single-speed compressor handling, automatic curve generation, and predefined templates.
---

# Compressor Curves and Performance Maps

Detailed documentation for compressor performance curves in NeqSim, including multi-speed and single-speed compressor handling, automatic curve generation, and predefined templates.

## Table of Contents
- [Overview](#overview)
- [Varying Molecular Weight / Gas Composition](#varying-molecular-weight--gas-composition)
- [Multi-Map MW Interpolation](#multi-map-mw-interpolation)
- [Multi-Speed Compressors](#multi-speed-compressors)
- [Single-Speed Compressors](#single-speed-compressors)
- [Surge Curves](#surge-curves)
- [Stone Wall (Choke) Curves](#stone-wall-choke-curves)
- [Setting Curves Manually](#setting-curves-manually)
- [Distance to Operating Limits](#distance-to-operating-limits)
- [**Speed Calculation from Operating Point**](#speed-calculation-from-operating-point) ⭐ NEW
- [Anti-Surge Control](#anti-surge-control)
- [**Loading Compressor Curves from Files**](#loading-compressor-curves-from-files) ⭐ NEW
  - [Loading from JSON Files](#loading-from-json-files)
  - [Loading from CSV Files](#loading-from-csv-files)
  - [CompressorChartJsonReader Class](#compressorchartjsonreader-class)
  - [Best Practices for File-Based Curves](#best-practices-for-file-based-curves)
- [API Reference](#api-reference)
- [Python Examples](#python-examples)
- [**Automatic Curve Generation**](#automatic-curve-generation) ⭐ NEW
- [**Compressor Curve Templates**](#compressor-curve-templates) ⭐ NEW
  - [Basic Centrifugal Templates](#basic-centrifugal-templates)
  - [Application-Based Templates](#application-based-templates)
  - [Compressor Type Templates](#compressor-type-templates)
- [Template Selection Guide](#template-selection-guide)
- [Using the CompressorChartGenerator](#using-the-compressorchart-generator)
- [Template API Reference](#template-api-reference)
- [**Dynamic Simulation Features**](#dynamic-simulation-features) ⭐ NEW
  - [Compressor States](#compressor-states)
  - [Event Listeners](#event-listeners)
  - [Driver Modeling](#driver-modeling)
  - [Startup/Shutdown Profiles](#startupshutdown-profiles)
  - [Anti-Surge Control Strategies](#anti-surge-control-strategies)
  - [Operating History Analysis](#operating-history-analysis)
  - [Performance Degradation](#performance-degradation)

---

## Overview

NeqSim supports comprehensive compressor performance modeling through compressor charts (performance maps). The key classes are:

| Class | Description |
|-------|-------------|
| `CompressorChart` | Standard compressor chart with polynomial interpolation |
| `CompressorChartKhader2015` | Advanced chart with Khader 2015 method and fan law scaling |
| `CompressorChartMWInterpolation` | Multi-map chart with MW interpolation between maps |
| `CompressorChartGenerator` | **Automatic curve generation** from templates |
| `CompressorCurveTemplate` | **Predefined curve templates** (12 available) |
| `SafeSplineSurgeCurve` | Spline-based surge curve with safe extrapolation |
| `SafeSplineStoneWallCurve` | Spline-based stone wall (choke) curve |
| `CompressorCurve` | Individual speed curve (flow, head, efficiency) |
| `CompressorMechanicalLosses` | **Seal gas and bearing loss calculations** (API 692/617) |

### Compressor Operating Envelope

```
                    Head
                     ↑
                     │        ╭──────────╮
                     │       ╱   Stone    ╲
                     │      ╱    Wall      ╲
              Surge │     ╱   (Choke)      ╲
              Curve │    ╱                  ╲
                    │   ╱                    ╲
                    │  ╱   Operating          ╲
                    │ ╱     Envelope           ╲
                    │╱                          ╲
                    └─────────────────────────────→ Flow
                         ↑                  ↑
                    Minimum Flow      Maximum Flow
                   (Surge Point)    (Stone Wall Point)
```

---

## Varying Molecular Weight / Gas Composition

Compressor curves are typically measured at specific reference conditions (temperature, pressure, gas composition). When the actual operating fluid has a different molecular weight or composition, the curves must be corrected.

### The Problem

Compressor performance maps are generated at specific **reference conditions**:
- Reference temperature and pressure
- Reference gas composition (molecular weight)
- Reference density and compressibility

When the **actual operating fluid** differs from the reference:
- Flow capacity changes
- Head capacity changes  
- Surge and stone wall limits shift

### Solution: CompressorChartKhader2015

NeqSim implements the **Khader 2015 method** in `CompressorChartKhader2015` which automatically corrects curves for varying gas composition using **dimensionless similarity parameters**.

#### Mathematical Background

The method normalizes compressor map data using sound speed-based similarity:

| Parameter | Dimensionless Form | Description |
|-----------|-------------------|-------------|
| Corrected Head | $H_{corr} = H / c_s^2$ | Head normalized by sound speed squared |
| Corrected Flow | $Q_{corr} = Q / (c_s \cdot D^2)$ | Flow normalized by sound speed and diameter |
| Machine Mach Number | $Ma = N \cdot D / c_s$ | Speed normalized by sound speed |

Where:
- $H$ = polytropic head (kJ/kg)
- $Q$ = volumetric flow (m³/s)
- $N$ = rotational speed (rev/s)
- $D$ = impeller outer diameter (m)
- $c_s$ = sound speed of the gas (m/s)

#### How It Works

1. **Reference curves** are converted to dimensionless form using the reference fluid's sound speed
2. **At runtime**, the actual fluid's sound speed is calculated
3. **Curves are scaled** back to physical units using the actual sound speed
4. **Surge and stone wall curves** are regenerated for the actual fluid

This approach ensures that when molecular weight changes, the operating envelope automatically adjusts.

### Using CompressorChartKhader2015

```java
import neqsim.process.equipment.compressor.CompressorChartKhader2015;

// Create fluid (this is the ACTUAL operating fluid)
SystemInterface operatingFluid = new SystemSrkEos(298.15, 50.0);
operatingFluid.addComponent("methane", 0.85);
operatingFluid.addComponent("ethane", 0.10);
operatingFluid.addComponent("propane", 0.05);
operatingFluid.setMixingRule("classic");

// Create stream
Stream stream = new Stream("inlet", operatingFluid);
stream.setFlowRate(5000.0, "Am3/hr");
stream.run();

// Create compressor with Khader 2015 chart
Compressor compressor = new Compressor("K-100", stream);
double impellerDiameter = 0.3;  // meters

// Create the Khader2015 chart - it takes the stream to get the actual fluid
CompressorChartKhader2015 chart = new CompressorChartKhader2015(stream, impellerDiameter);

// Chart conditions: [temperature (°C), pressure (bara), density (kg/m³), MW (g/mol)]
// The MW (4th element) is used to create a reference fluid for the curves
double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

// Set curves at reference conditions
double[] speed = new double[] {10000, 11000, 12000};
double[][] flow = new double[][] {
    {3500, 4000, 4500, 5000, 5500},
    {3800, 4300, 4800, 5300, 5800},
    {4000, 4500, 5000, 5500, 6000}
};
double[][] head = new double[][] {
    {110, 105, 98, 88, 75},
    {128, 122, 114, 103, 90},
    {148, 141, 132, 120, 105}
};
double[][] polyEff = new double[][] {
    {77, 80, 81, 79, 74},
    {76, 79, 80, 78, 73},
    {75, 78, 79, 77, 72}
};

chart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
chart.setHeadUnit("kJ/kg");

// Apply chart to compressor
compressor.setCompressorChart(chart);
compressor.setSpeed(11000);
compressor.run();

// The chart automatically corrects for the actual fluid's molecular weight!
System.out.println("Polytropic head: " + compressor.getPolytropicHead("kJ/kg"));
System.out.println("Polytropic efficiency: " + compressor.getPolytropicEfficiency());
```

### Chart Conditions Array

The `chartConditions` array specifies the reference conditions:

| Index | Value | Unit | Description |
|-------|-------|------|-------------|
| 0 | Temperature | °C | Reference temperature |
| 1 | Pressure | bara | Reference pressure |
| 2 | Density | kg/m³ | Reference density (optional) |
| 3 | Molecular Weight | g/mol | Reference molecular weight |

When the 4th element (molecular weight) is provided, the chart creates a reference fluid that matches this MW by blending methane, ethane, and propane in appropriate proportions.

### Specifying a Custom Reference Fluid

For more control, you can provide your own reference fluid:

```java
// Create reference fluid (the fluid the curves were measured with)
SystemInterface referenceFluid = new SystemSrkEos(298.15, 50.0);
referenceFluid.addComponent("methane", 0.90);
referenceFluid.addComponent("ethane", 0.07);
referenceFluid.addComponent("propane", 0.03);
referenceFluid.setMixingRule("classic");

// Create chart with both operating and reference fluids
CompressorChartKhader2015 chart = new CompressorChartKhader2015(
    operatingFluid,    // Actual fluid
    referenceFluid,    // Reference fluid (curves measured with this)
    impellerDiameter
);

chart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
```

### When the Fluid Changes at Runtime

If the fluid composition changes during simulation, regenerate the real curves:

```java
// Fluid composition has changed...
operatingFluid.addComponent("CO2", 0.02);  // Added CO2
operatingFluid.init(0);

// Regenerate curves for new fluid
chart.generateRealCurvesForFluid();

// Run compressor with updated curves
compressor.run();
```

### Comparison: Standard vs Khader2015 Chart

| Feature | CompressorChart | CompressorChartKhader2015 |
|---------|-----------------|---------------------------|
| MW Correction | Manual | Automatic |
| Method | Polynomial interpolation | Sound speed scaling |
| Impeller diameter | Not required | Required |
| Fluid dependency | None | Uses stream fluid |
| Best for | Fixed composition | Variable composition |

### Limitations

- The Khader 2015 method assumes **similar gas behavior** (ideal gas-like scaling)
- Very different fluids (e.g., switching from natural gas to pure CO2) may require new base curves
- **Single-phase gas** is assumed; liquids or two-phase flows are not handled
- Accuracy depends on the validity of the similarity assumptions for your specific application

---

## Multi-Map MW Interpolation

When you have compressor performance maps measured at **multiple discrete molecular weights**, use `CompressorChartMWInterpolation` to interpolate between maps based on the actual operating MW.

### Quick Start

The simplest way to use multi-MW charts is to let the compressor automatically detect the gas MW:

```java
// 1. Create chart and add maps at different MW values
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");
chart.setAutoGenerateSurgeCurves(true);
chart.setAutoGenerateStoneWallCurves(true);

chart.addMapAtMW(18.0, chartConditions, speed, flow18, head18, polyEff18);
chart.addMapAtMW(22.0, chartConditions, speed, flow22, head22, polyEff22);

// 2. Set chart on compressor
Compressor comp = new Compressor("K-100", inletStream);
comp.setCompressorChart(chart);
comp.setOutletPressure(60.0);
comp.setSpeed(11000);

// 3. Run - MW is automatically detected from inlet stream
comp.run();

// The chart now uses the actual gas MW from the inlet stream
System.out.println("Operating MW: " + chart.getOperatingMW() + " g/mol");
```

**Key features:**
- **Automatic MW detection**: The compressor sets the inlet stream on the chart during `run()`
- **No manual MW calls needed**: `setOperatingMW()` is called automatically
- **Composition changes tracked**: If gas composition changes, just run again

### When to Use Multi-Map Interpolation

Use this approach when:
- You have performance data measured at several gas compositions (e.g., MW = 18, 20, 22 g/mol)
- The gas composition varies significantly during operation
- You want direct interpolation between measured maps rather than theoretical scaling

### How It Works

1. **Add multiple maps**: Each map is defined at a specific molecular weight
2. **Compressor run()**: Automatically sets inlet stream on the chart
3. **Chart uses actual MW**: Gets MW from `inletStream.getFluid().getMolarMass()`
4. **Automatic interpolation**: Head, efficiency, surge, and stone wall are linearly interpolated between the two nearest MW maps

### Java Example

```java
import neqsim.process.equipment.compressor.CompressorChartMWInterpolation;

// Create MW interpolation chart
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");

double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

// Map at MW = 18 g/mol (lighter gas - higher head capacity)
double[] speed18 = {10000, 11000, 12000};
double[][] flow18 = {
    {3000, 3500, 4000, 4500, 5000},
    {3300, 3800, 4300, 4800, 5300},
    {3600, 4100, 4600, 5100, 5600}
};
double[][] head18 = {
    {120, 115, 108, 98, 85},
    {138, 132, 124, 113, 98},
    {158, 151, 142, 130, 113}
};
double[][] polyEff18 = {
    {75, 78, 80, 78, 73},
    {74, 77, 79, 77, 72},
    {73, 76, 78, 76, 71}
};
chart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);

// Map at MW = 22 g/mol (heavier gas - lower head capacity)
double[] speed22 = {10000, 11000, 12000};
double[][] flow22 = {
    {2800, 3300, 3800, 4300, 4800},
    {3100, 3600, 4100, 4600, 5100},
    {3400, 3900, 4400, 4900, 5400}
};
double[][] head22 = {
    {100, 96, 90, 82, 71},
    {115, 110, 103, 94, 82},
    {132, 126, 118, 108, 94}
};
double[][] polyEff22 = {
    {73, 76, 78, 76, 71},
    {72, 75, 77, 75, 70},
    {71, 74, 76, 74, 69}
};
chart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);

// Set current operating MW (e.g., from actual fluid composition)
double actualMW = 20.0;  // Midpoint - will interpolate 50/50 between maps
chart.setOperatingMW(actualMW);

// Get interpolated values
double flow = 3500.0;  // m³/hr
double speed = 10000;  // RPM
double polytropicHead = chart.getPolytropicHead(flow, speed);
double efficiency = chart.getPolytropicEfficiency(flow, speed);

System.out.println("Operating MW: " + actualMW + " g/mol");
System.out.println("Interpolated polytropic head: " + polytropicHead + " kJ/kg");
System.out.println("Interpolated efficiency: " + efficiency + " %");

// Apply to compressor
Compressor comp = new Compressor("K-100", stream);
comp.setCompressorChart(chart);
comp.setSpeed(10000);
comp.run();
```

### Adding More Maps

You can add any number of MW maps. The chart will interpolate between the two nearest:

```java
// Add a third map at MW = 20 g/mol for better accuracy
chart.addMapAtMW(20.0, chartConditions, speed20, flow20, head20, polyEff20);

// Maps are automatically sorted by MW
// Interpolation at MW=19 will use maps at MW=18 and MW=20
// Interpolation at MW=21 will use maps at MW=20 and MW=22
```

### Automatic MW Detection from Inlet Stream

By default, the compressor automatically updates the chart with its inlet stream during each `run()` call. The chart then uses the actual molecular weight from the inlet stream's fluid. This means you don't need to manually call `setOperatingMW()` - it's automatically updated each time the compressor runs:

```java
// Create MW interpolation chart
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");

// Add MW maps
chart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);
chart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);

// Set the chart on the compressor
Compressor comp = new Compressor("K-100", inletStream);
comp.setCompressorChart(chart);
comp.setOutletPressure(60.0);

// When the compressor runs, it automatically:
// 1. Sets the inlet stream on the chart
// 2. The chart uses the stream's MW for interpolation
comp.run();

// Check the operating MW that was used
System.out.println("Operating MW: " + chart.getOperatingMW() + " g/mol");

// If the gas composition changes, just run again
// The MW will be automatically updated
process.run();  // Chart uses new MW automatically
```

#### Manual Inlet Stream Setup (Optional)

You can also manually set the inlet stream on the chart if needed:

```java
chart.setInletStream(compressorInletStream);

// MW is automatically updated when calling chart methods
double head = chart.getPolytropicHead(flow, speed);  // Uses stream's current MW
```

#### Disabling Auto MW Detection

If you want to manually control the operating MW:

```java
// Disable auto MW detection
chart.setUseActualMW(false);

// Now you must set MW manually
chart.setOperatingMW(20.0);
```

### Extrapolation Behavior

By default, when the operating MW is outside the range of available maps, the chart uses the boundary map:

| Operating MW | Default Behavior |
|--------------|----------|
| Below lowest map MW | Uses lowest MW map (no extrapolation) |
| Between maps | Linear interpolation |
| Above highest map MW | Uses highest MW map (no extrapolation) |

#### Enabling Extrapolation

Enable extrapolation to linearly extend beyond the MW range:

```java
// Enable extrapolation outside MW range
chart.setAllowExtrapolation(true);

// Now values are extrapolated when MW is outside the map range
chart.setOperatingMW(16.0);  // Below lowest map (18.0)
double head = chart.getPolytropicHead(flow, speed);  // Extrapolated value

chart.setOperatingMW(26.0);  // Above highest map (22.0)
double eff = chart.getPolytropicEfficiency(flow, speed);  // Extrapolated value
```

**Caution**: Extrapolation can produce unrealistic values if the MW is too far outside the measured range. Use with appropriate limits.

### Surge and Stone Wall Curves

Generate surge and stone wall curves for all maps:

```java
// Generate curves for all MW maps
chart.generateAllSurgeCurves();
chart.generateAllStoneWallCurves();

// Or set curves manually for each MW
chart.setSurgeCurveAtMW(18.0, chartConditions, surgeFlow18, surgeHead18);
chart.setSurgeCurveAtMW(22.0, chartConditions, surgeFlow22, surgeHead22);

chart.setStoneWallCurveAtMW(18.0, chartConditions, stoneWallFlow18, stoneWallHead18);
chart.setStoneWallCurveAtMW(22.0, chartConditions, stoneWallFlow22, stoneWallHead22);
```

#### Auto-Generate Curves When Adding Maps

Enable auto-generation before adding maps:

```java
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");

// Enable auto-generation of surge and stone wall curves
chart.setAutoGenerateSurgeCurves(true);
chart.setAutoGenerateStoneWallCurves(true);

// Surge and stone wall are now automatically generated when maps are added
chart.addMapAtMW(18.0, chartConditions, speed18, flow18, head18, polyEff18);
chart.addMapAtMW(22.0, chartConditions, speed22, flow22, head22, polyEff22);
```

#### Interpolated Surge and Stone Wall Methods

Check operating limits using interpolated curves:

```java
chart.setOperatingMW(20.0);
double head = 100.0;  // kJ/kg
double flow = 3500.0; // m³/hr

// Get interpolated surge flow at a given head
double surgeFlow = chart.getSurgeFlow(head);
double stoneWallFlow = chart.getStoneWallFlow(head);

// Check if operating point is in surge or choked
boolean inSurge = chart.isSurge(head, flow);
boolean isChoked = chart.isStoneWall(head, flow);

// Get distance to operating limits
double distanceToSurge = chart.getDistanceToSurge(head, flow);     // Positive = above surge
double distanceToStoneWall = chart.getDistanceToStoneWall(head, flow); // Positive = below stone wall

System.out.println("Surge flow: " + surgeFlow + " m³/hr");
System.out.println("Stone wall flow: " + stoneWallFlow + " m³/hr");
System.out.println("In surge: " + inSurge);
System.out.println("Is choked: " + isChoked);
System.out.println("Distance to surge: " + (distanceToSurge * 100) + "%");
System.out.println("Distance to stone wall: " + (distanceToStoneWall * 100) + "%");
```

### Simplified Method Signatures

For convenience, you can omit `chartConditions` - default conditions will be generated automatically based on the MW:

#### Multi-Speed Compressor (without chartConditions)

```java
// Simplest form: (MW, speed[], flow[][], head[][], efficiency[][])
chart.addMapAtMW(18.0, speeds, flow18, head18, polyEff18);
chart.addMapAtMW(22.0, speeds, flow22, head22, polyEff22);

// With separate flow arrays: (MW, speed[], flow[][], head[][], flowEff[][], efficiency[][])
chart.addMapAtMW(20.0, speeds, flowHead, heads, flowEff, effs);
```

**Complete multi-speed example:**

{% raw %}
```java
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");
chart.setAutoGenerateSurgeCurves(true);
chart.setAutoGenerateStoneWallCurves(true);

double[] speeds = {10000, 11000, 12000};  // Multiple speeds (RPM)

// Map at MW = 18 g/mol
double[][] flow18 = {{3000, 3500, 4000, 4500, 5000}, 
                     {3300, 3800, 4300, 4800, 5300},
                     {3600, 4100, 4600, 5100, 5600}};
double[][] head18 = {{120, 115, 108, 98, 85}, 
                     {138, 132, 124, 113, 98},
                     {158, 151, 142, 130, 113}};
double[][] eff18 = {{75, 78, 80, 78, 73}, 
                    {74, 77, 79, 77, 72},
                    {73, 76, 78, 76, 71}};
chart.addMapAtMW(18.0, speeds, flow18, head18, eff18);  // No chartConditions needed!

// Map at MW = 22 g/mol
double[][] flow22 = {{2800, 3300, 3800, 4300, 4800}, 
                     {3100, 3600, 4100, 4600, 5100},
                     {3400, 3900, 4400, 4900, 5400}};
double[][] head22 = {{100, 96, 90, 82, 71}, 
                     {115, 110, 103, 94, 82},
                     {132, 126, 118, 108, 94}};
double[][] eff22 = {{73, 76, 78, 76, 71}, 
                    {72, 75, 77, 75, 70},
                    {71, 74, 76, 74, 69}};
chart.addMapAtMW(22.0, speeds, flow22, head22, eff22);

// Use with compressor
Compressor comp = new Compressor("K-100", inletStream);
comp.setCompressorChart(chart);
comp.setSpeed(11000);
comp.run();
```
{% endraw %}

#### Single-Speed Compressor (without chartConditions)

For single-speed compressors, you can use simplified method signatures with 1D arrays:

```java
// Simplest form: (MW, speed, flow[], head[], efficiency[])
chart.addMapAtMW(18.0, 10000, flow, head, polyEff);
chart.addMapAtMW(22.0, 10000, flow22, head22, polyEff22);

// With separate flow arrays for efficiency: (MW, speed, flow[], head[], flowEff[], efficiency[])
chart.addMapAtMW(20.0, 10000, flowHead, head, flowEff, polyEff);
```

**Complete single-speed example:**

```java
CompressorChartMWInterpolation chart = new CompressorChartMWInterpolation();
chart.setHeadUnit("kJ/kg");
chart.setAutoGenerateSurgeCurves(true);
chart.setAutoGenerateStoneWallCurves(true);

double speed = 10000;  // Single speed (RPM)

// Map at MW = 18 g/mol
double[] flow18 = {3000, 3500, 4000, 4500, 5000};
double[] head18 = {120, 115, 108, 98, 85};
double[] eff18 = {75, 78, 80, 78, 73};
chart.addMapAtMW(18.0, speed, flow18, head18, eff18);

// Map at MW = 22 g/mol
double[] flow22 = {2800, 3300, 3800, 4300, 4800};
double[] head22 = {100, 96, 90, 82, 71};
double[] eff22 = {73, 76, 78, 76, 71};
chart.addMapAtMW(22.0, speed, flow22, head22, eff22);

// Use with compressor
Compressor comp = new Compressor("K-100", inletStream);
comp.setCompressorChart(chart);
comp.setSpeed(speed);
comp.run();
```

### Separate Flow Arrays for Head and Efficiency

When efficiency is measured at different flow points than head:

{% raw %}
```java
double[] speeds = {10000, 11000};

// Flow and head arrays
double[][] flowHead = {{3000, 3500, 4000, 4500}, {3300, 3800, 4300, 4800}};
double[][] heads = {{120, 115, 108, 98}, {138, 132, 124, 113}};

// Efficiency measured at different flow points
double[][] flowEff = {{3100, 3600, 4100}, {3400, 3900, 4400}};
double[][] effs = {{76, 79, 77}, {75, 78, 76}};

// Use the 6-argument version of addMapAtMW
chart.addMapAtMW(20.0, chartConditions, speeds, flowHead, heads, flowEff, effs);
```
{% endraw %}

### Python Example

```python
from jpype import JClass

CompressorChartMWInterpolation = JClass(
    'neqsim.process.equipment.compressor.CompressorChartMWInterpolation'
)

# Create multi-map chart
chart = CompressorChartMWInterpolation()
chart.setHeadUnit("kJ/kg")

chart_conditions = [25.0, 50.0, 50.0, 20.0]

# Add map at MW = 18 g/mol
speed_18 = [10000, 11000, 12000]
flow_18 = [[3000, 3500, 4000], [3300, 3800, 4300], [3600, 4100, 4600]]
head_18 = [[120, 115, 108], [138, 132, 124], [158, 151, 142]]
eff_18 = [[75, 78, 80], [74, 77, 79], [73, 76, 78]]
chart.addMapAtMW(18.0, chart_conditions, speed_18, flow_18, head_18, eff_18)

# Add map at MW = 22 g/mol
speed_22 = [10000, 11000, 12000]
flow_22 = [[2800, 3300, 3800], [3100, 3600, 4100], [3400, 3900, 4400]]
head_22 = [[100, 96, 90], [115, 110, 103], [132, 126, 118]]
eff_22 = [[73, 76, 78], [72, 75, 77], [71, 74, 76]]
chart.addMapAtMW(22.0, chart_conditions, speed_22, flow_22, head_22, eff_22)

# Set operating MW and get interpolated values
chart.setOperatingMW(20.0)
head = chart.getPolytropicHead(3500.0, 10000)
eff = chart.getPolytropicEfficiency(3500.0, 10000)

print(f"Interpolated head at MW=20: {head:.2f} kJ/kg")
print(f"Interpolated efficiency at MW=20: {eff:.1f} %")
```

### Complete Python Example with Automatic MW Detection

This example shows a full workflow where the compressor automatically uses the actual gas MW:

```python
from jpype import JClass
from neqsim.thermo import fluid
from neqsim.process import stream, compressor, runProcess

# Import MW interpolation chart
CompressorChartMWInterpolation = JClass(
    'neqsim.process.equipment.compressor.CompressorChartMWInterpolation'
)

# Create gas with specific composition
gas = fluid("srk")
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.setTemperature(25.0, "C")
gas.setPressure(30.0, "bara")
gas.setTotalFlowRate(5000.0, "Am3/hr")
gas.setMixingRule("classic")

# Create inlet stream
inlet_stream = stream(gas)
inlet_stream.run()

print(f"Inlet gas MW: {inlet_stream.getFluid().getMolarMass('kg/mol') * 1000:.2f} g/mol")

# Create MW interpolation chart with maps at MW = 18 and 22 g/mol
chart = CompressorChartMWInterpolation()
chart.setHeadUnit("kJ/kg")

chart_conditions = [25.0, 50.0, 50.0, 20.0]

# Map at MW = 18 g/mol (lighter gas)
speed = [10000, 11000, 12000]
flow_18 = [[3000, 3500, 4000, 4500, 5000],
           [3300, 3800, 4300, 4800, 5300],
           [3600, 4100, 4600, 5100, 5600]]
head_18 = [[120, 115, 108, 98, 85],
           [138, 132, 124, 113, 98],
           [158, 151, 142, 130, 113]]
eff_18 = [[75, 78, 80, 78, 73],
          [74, 77, 79, 77, 72],
          [73, 76, 78, 76, 71]]

# Map at MW = 22 g/mol (heavier gas)
flow_22 = [[2800, 3300, 3800, 4300, 4800],
           [3100, 3600, 4100, 4600, 5100],
           [3400, 3900, 4400, 4900, 5400]]
head_22 = [[100, 96, 90, 82, 71],
           [115, 110, 103, 94, 82],
           [132, 126, 118, 108, 94]]
eff_22 = [[73, 76, 78, 76, 71],
          [72, 75, 77, 75, 70],
          [71, 74, 76, 74, 69]]

# Add maps and enable auto-generation of surge/stone wall curves
chart.setAutoGenerateSurgeCurves(True)
chart.setAutoGenerateStoneWallCurves(True)
chart.addMapAtMW(18.0, chart_conditions, speed, flow_18, head_18, eff_18)
chart.addMapAtMW(22.0, chart_conditions, speed, flow_22, head_22, eff_22)

# Create compressor and set the chart
comp = compressor(inlet_stream)
comp.setOutletPressure(60.0, "bara")
comp.setSpeed(11000)
comp.setCompressorChart(chart)

# Run - MW is automatically detected from inlet stream
comp.run()

print(f"\nAfter compressor run:")
print(f"Chart operating MW: {chart.getOperatingMW():.2f} g/mol")
print(f"Polytropic head: {comp.getPolytropicHead('kJ/kg'):.2f} kJ/kg")
print(f"Polytropic efficiency: {comp.getPolytropicEfficiency() * 100:.1f}%")
print(f"Outlet pressure: {comp.getOutletStream().getPressure('bara'):.1f} bara")
print(f"Power: {comp.getPower('kW'):.1f} kW")

# Check surge/stone wall margins
print(f"\nOperating margins:")
print(f"Distance to surge: {comp.getDistanceToSurge() * 100:.1f}%")
print(f"Distance to stone wall: {comp.getDistanceToStoneWall() * 100:.1f}%")

# Change gas composition and run again
gas.addComponent("CO2", 0.03)
gas.init(0)
inlet_stream.run()
comp.run()

print(f"\nAfter adding CO2:")
print(f"New gas MW: {inlet_stream.getFluid().getMolarMass('kg/mol') * 1000:.2f} g/mol")
print(f"Chart operating MW: {chart.getOperatingMW():.2f} g/mol")
print(f"Polytropic head: {comp.getPolytropicHead('kJ/kg'):.2f} kJ/kg")
```

### Enabling Extrapolation in Python

```python
# Enable extrapolation outside the MW range
chart.setAllowExtrapolation(True)

# Now values can be extrapolated for MW outside 18-22 range
# For example, with a very light gas (MW = 16 g/mol)
# or a heavy gas (MW = 26 g/mol)
```

### Disabling Auto MW Detection in Python

```python
# Disable automatic MW detection from stream
chart.setUseActualMW(False)

# Now you must manually set the MW
chart.setOperatingMW(20.0)
```

### Comparison: Multi-Map vs Khader2015

| Feature | CompressorChartMWInterpolation | CompressorChartKhader2015 |
|---------|-------------------------------|---------------------------|
| Input data | Multiple measured maps | Single reference map |
| Correction method | Linear interpolation | Sound speed scaling |
| Accuracy | Higher (measured data) | Theoretical approximation |
| Data requirements | Maps at multiple MW | One map + impeller diameter |
| Best for | Validated multi-MW data | When only one map available |

---

## Multi-Speed Compressors

Multi-speed (variable speed) compressors have performance curves at multiple rotational speeds. NeqSim interpolates between these curves to determine performance at any operating speed.

### Setting Up Multi-Speed Curves

```java
// Chart conditions: [temperature (°C), pressure (bara), density (kg/m³), MW (g/mol)]
double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

// Multiple speeds (RPM)
double[] speed = new double[] {8000, 9000, 10000, 11000, 12000};

// Flow arrays for each speed (m³/hr at chart conditions)
double[][] flow = new double[][] {
    {3000, 3500, 4000, 4500, 5000},  // Speed 8000 RPM
    {3200, 3700, 4200, 4700, 5200},  // Speed 9000 RPM
    {3500, 4000, 4500, 5000, 5500},  // Speed 10000 RPM
    {3800, 4300, 4800, 5300, 5800},  // Speed 11000 RPM
    {4000, 4500, 5000, 5500, 6000}   // Speed 12000 RPM
};

// Head arrays for each speed (kJ/kg)
double[][] head = new double[][] {
    {80, 78, 74, 68, 60},  // Speed 8000 RPM
    {95, 92, 88, 82, 72},  // Speed 9000 RPM
    {110, 106, 101, 94, 85}, // Speed 10000 RPM
    {128, 123, 117, 109, 99}, // Speed 11000 RPM
    {145, 140, 133, 124, 112} // Speed 12000 RPM
};

// Polytropic efficiency arrays for each speed (%)
double[][] polyEff = new double[][] {
    {75, 78, 80, 79, 75},
    {76, 79, 81, 80, 76},
    {77, 80, 82, 81, 77},
    {76, 79, 81, 80, 76},
    {75, 78, 80, 79, 75}
};

// Set curves on compressor
compressor.getCompressorChart().setCurves(chartConditions, speed, flow, head, flow, polyEff);
compressor.getCompressorChart().setHeadUnit("kJ/kg");
compressor.setSpeed(10000);  // Operating speed
```

### Automatic Surge and Stone Wall Generation

For multi-speed compressors with **2 or more speed curves**, NeqSim automatically generates surge and stone wall curves:

```java
// Automatic generation from chart data
compressor.getCompressorChart().generateSurgeCurve();
compressor.getCompressorChart().generateStoneWallCurve();
```

The surge curve is created by connecting the **minimum flow points** from each speed curve.
The stone wall curve is created by connecting the **maximum flow points** from each speed curve.

---

## Single-Speed Compressors

Single-speed (fixed speed) compressors operate at a constant rotational speed. For these compressors:

- **Surge** is a single point (minimum stable flow at the fixed speed)
- **Stone wall** is a single point (maximum flow at the fixed speed)
- Speed cannot be adjusted to move away from operating limits

### Setting Up Single-Speed Curves

```java
// Chart conditions
double[] chartConditions = new double[] {25.0, 50.0, 50.0, 20.0};

// Single speed
double[] speed = new double[] {10250};

// Single speed curve
double[][] flow = new double[][] {
    {5607, 6008, 6480, 7112, 7800, 8180, 8509, 8750, 9007, 9758}
};
double[][] head = new double[][] {
    {150.0, 149.5, 148.8, 148.0, 146.1, 144.8, 143.0, 140.7, 137.3, 112.6}
};
double[][] polyEff = new double[][] {
    {78, 79, 80, 80.5, 80, 79.5, 79, 78, 77, 70}
};

compressor.setSpeed(10250);
compressor.getCompressorChart().setCurves(chartConditions, speed, flow, head, flow, polyEff);
compressor.getCompressorChart().setHeadUnit("kJ/kg");
```

### Single-Point Surge and Stone Wall

For single-speed compressors, surge and stone wall are **single points**, not curves. NeqSim supports setting these directly:

```java
// Single-point surge (minimum flow point on the curve)
double[] surgeFlow = new double[] {5607.45};  // Single value
double[] surgeHead = new double[] {150.0};    // Corresponding head
compressor.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);

// Single-point stone wall (maximum flow point on the curve)
double[] stoneWallFlow = new double[] {9758.49};  // Single value
double[] stoneWallHead = new double[] {112.65};   // Corresponding head
compressor.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallFlow, stoneWallHead);
```

**Key Differences from Multi-Speed:**

| Aspect | Multi-Speed | Single-Speed |
|--------|-------------|--------------|
| Surge/Stone Wall | Curves (interpolated) | Single points (constant) |
| Speed Adjustment | Can vary speed to avoid limits | Fixed speed |
| Control Strategy | Speed + recycle control | Recycle control only |
| Curve Points Required | ≥2 points per curve | 1 point (single point) |

---

## Surge Curves

The surge curve defines the minimum stable flow at each head value. Operating below this flow causes unstable, potentially damaging oscillations.

### Surge Curve Implementation

NeqSim uses `SafeSplineSurgeCurve` which provides:
- Spline interpolation for smooth curve fitting
- Safe linear extrapolation beyond curve limits
- Support for single-point surge (single-speed compressors)

### Setting Surge Curve Manually

```java
// Multi-speed: Multiple flow/head points
double[] surgeFlow = {4512.7, 4862.5, 5237.8, 5642.9, 6221.8, 6888.9, 7109.8, 7598.9};
double[] surgeHead = {61.9, 71.4, 81.6, 92.5, 103.5, 114.9, 118.6, 126.7};
compressor.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);

// Single-speed: Single point
double[] singleSurgeFlow = {5607.45};
double[] singleSurgeHead = {150.0};
compressor.getCompressorChart().getSurgeCurve().setCurve(chartConditions, singleSurgeFlow, singleSurgeHead);
```

### Checking Surge Status

```java
// Check if operating point is in surge
boolean inSurge = compressor.getCompressorChart().getSurgeCurve().isSurge(head, flow);

// Get surge flow at current head
double surgeFlow = compressor.getSurgeFlowRate();

// Get margin above surge
double surgeMargin = compressor.getSurgeFlowRateMargin();  // m³/hr above surge
```

---

## Stone Wall (Choke) Curves

The stone wall (choke) curve defines the maximum flow at each head value. At this limit, the gas velocity approaches sonic conditions and flow cannot increase further.

### Stone Wall Curve Implementation

NeqSim uses `SafeSplineStoneWallCurve` which provides:
- Spline interpolation for smooth curve fitting
- Safe linear extrapolation beyond curve limits
- Support for single-point stone wall (single-speed compressors)

### Setting Stone Wall Curve Manually

```java
// Multi-speed: Multiple flow/head points
double[] stoneWallFlow = {6500, 7200, 8000, 8800, 9600};
double[] stoneWallHead = {55, 70, 88, 108, 130};
compressor.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallFlow, stoneWallHead);

// Single-speed: Single point
double[] singleStoneWallFlow = {9758.49};
double[] singleStoneWallHead = {112.65};
compressor.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, singleStoneWallFlow, singleStoneWallHead);
```

### Checking Stone Wall Status

```java
// Check if operating point is at stone wall
boolean atStoneWall = compressor.isStoneWall();

// Check with explicit flow/head
boolean atStoneWall = compressor.getCompressorChart().getStoneWallCurve().isStoneWall(head, flow);
```

---

## Distance to Operating Limits

NeqSim provides methods to calculate the margin from operating limits:

### Distance to Surge

```java
// Returns ratio: (current flow / surge flow) - 1
// Positive = above surge, Negative = in surge
double distanceToSurge = compressor.getDistanceToSurge();

// Example: 0.25 means operating 25% above surge flow
System.out.println("Operating " + (distanceToSurge * 100) + "% above surge");
```

### Distance to Stone Wall

```java
// Returns ratio: (stone wall flow / current flow) - 1
// Positive = below stone wall, Zero/Negative = at/beyond choke
double distanceToStoneWall = compressor.getDistanceToStoneWall();

// Example: 0.40 means stone wall is 40% above current flow
System.out.println("Stone wall is " + (distanceToStoneWall * 100) + "% above current flow");
```

### How Distance Calculations Work

For **multi-speed compressors** (curve is active):
```
Distance to Surge = (Operating Flow / Surge Flow at Current Head) - 1
Distance to Stone Wall = (Stone Wall Flow at Current Head / Operating Flow) - 1
```

For **single-speed compressors** (single-point curve):
```
Distance to Surge = (Operating Flow / Single Surge Flow Point) - 1
Distance to Stone Wall = (Single Stone Wall Flow Point / Operating Flow) - 1
```

---

## Speed Calculation from Operating Point

When you need to determine the compressor speed required to achieve a specific operating point (flow and head), NeqSim provides a robust algorithm that works both within the defined curve range and with extrapolation beyond it.

### The `getSpeed()` and `getSpeedValue()` Methods

```java
// Get speed as integer (legacy method)
int speed = chart.getSpeed(flow, head);

// Get speed as double for full precision
double preciseSpeed = chart.getSpeedValue(flow, head);
```

### Algorithm Details

The speed calculation uses a hybrid approach for robustness:

1. **Fan-law Initial Guess**: Uses the relationship $H \propto N^2$ to estimate:
   $$N_{guess} = N_{ref} \times \sqrt{\frac{H_{target}}{H_{ref}}}$$

2. **Damped Newton-Raphson Iteration**: Fast convergence with safeguards:
   - 70% damping factor to prevent oscillation
   - Maximum 30% step change per iteration
   - Automatic gradient estimation using finite differences
   - Fallback to fan-law derivative when gradient is near zero

3. **Bounds Protection**: Prevents divergence:
   - Lower bound: 50% of minimum curve speed
   - Upper bound: 150% of maximum curve speed

4. **Bisection Fallback**: Guaranteed convergence if Newton-Raphson fails:
   - Automatic bounds extension if needed
   - Binary search to find the root

### Speed Limit Checking

The compressor chart and compressor classes provide methods to check if the calculated speed is within the defined curve range:

```java
// Using the compressor chart directly
CompressorChartInterface chart = compressor.getCompressorChart();
double speed = chart.getSpeedValue(flow, head);

// Check speed limits on chart
boolean tooHigh = chart.isHigherThanMaxSpeed(speed);
boolean tooLow = chart.isLowerThanMinSpeed(speed);
boolean inRange = chart.isSpeedWithinRange(speed);

// Get ratios (useful for warnings)
double ratioToMax = chart.getRatioToMaxSpeed(speed);  // >1.0 means above max
double ratioToMin = chart.getRatioToMinSpeed(speed);  // <1.0 means below min

// Using the compressor (uses current operating speed)
compressor.run();
boolean currentSpeedTooHigh = compressor.isHigherThanMaxSpeed();
boolean currentSpeedTooLow = compressor.isLowerThanMinSpeed();
double currentRatioToMax = compressor.getRatioToMaxSpeed();

// Or check a specific speed
boolean testSpeedTooHigh = compressor.isHigherThanMaxSpeed(12000);
```

### When Speed is Outside Curve Range

When the calculated speed falls outside the defined performance curves, the algorithm uses fan-law extrapolation:

- **Head scaling**: $H \propto N^2$ (head scales with speed squared)
- **Flow scaling**: $Q \propto N$ (flow scales linearly with speed)

This allows reasonable estimates for speeds up to 50% beyond the curve boundaries.

### Example: Speed Calculation with Limit Checking

```java
// Set up compressor
Compressor comp = new Compressor("K-100", inlet);
comp.setOutletPressure(120.0, "bara");
comp.setCompressorChart(chart);

// Run and check speed limits
comp.run();

// Get the calculated speed
double speed = comp.getSpeed();

// Check if operating within design envelope
if (comp.isHigherThanMaxSpeed()) {
    double ratio = comp.getRatioToMaxSpeed();
    System.out.println("WARNING: Speed " + ratio * 100 + "% of max curve speed");
    System.out.println("Compressor may be undersized for this duty");
} else if (comp.isLowerThanMinSpeed()) {
    double ratio = comp.getRatioToMinSpeed();
    System.out.println("WARNING: Speed " + ratio * 100 + "% of min curve speed");
    System.out.println("Compressor may be oversized - turndown issues");
} else {
    System.out.println("Operating within design envelope");
}
```

### Python Example

```python
# Get calculated speed
speed = chart.getSpeedValue(flow, head)

# Check speed limits
if chart.isHigherThanMaxSpeed(speed):
    ratio = chart.getRatioToMaxSpeed(speed)
    print(f"Speed {speed:.0f} RPM is {ratio:.1%} of max curve speed")
elif chart.isLowerThanMinSpeed(speed):
    ratio = chart.getRatioToMinSpeed(speed)
    print(f"Speed {speed:.0f} RPM is {ratio:.1%} of min curve speed")
else:
    print(f"Speed {speed:.0f} RPM is within curve range")

# Using compressor methods with current operating speed
comp.run()
if comp.isHigherThanMaxSpeed():
    print(f"Current speed exceeds max by {(comp.getRatioToMaxSpeed() - 1) * 100:.1f}%")
```

---

## Anti-Surge Control

### Surge Control Factor

The anti-surge system adds a safety margin to the surge limit:

```java
// Default surge control factor is 1.05 (5% safety margin)
compressor.getAntiSurge().setSurgeControlFactor(1.10);  // 10% margin

// Safe minimum flow = Surge Flow × Surge Control Factor
double safeMinFlow = compressor.getSurgeFlowRate() * compressor.getAntiSurge().getSurgeControlFactor();
```

### Anti-Surge Configuration

```java
AntiSurge antiSurge = compressor.getAntiSurge();
antiSurge.setActive(true);
antiSurge.setSurgeControlFactor(1.10);  // 10% margin above surge

// Check current status
boolean isSurging = antiSurge.isSurge();
double controlFactor = antiSurge.getSurgeControlFactor();
```

---

## Loading Compressor Curves from Files

NeqSim supports loading compressor performance curves from external JSON and CSV files. This is useful when:
- Working with vendor-provided compressor data
- Sharing compressor curves between simulations
- Storing compressor curves in version control
- Integrating with external data management systems

### Loading from JSON Files

JSON is the recommended format for compressor curves due to its readability and support for metadata.

#### JSON File Format

```json
{
  "compressorName": "Example Compressor",
  "headUnit": "kJ/kg",
  "maxDesignPower_kW": 16619.42,
  "speedCurves": [
    {
      "speed_rpm": 7382.55,
      "flow_m3h": [19852.05, 21679.87, 23507.69, 25335.50, 27163.32],
      "head_kJkg": [256.69, 253.67, 249.29, 243.58, 236.91],
      "polytropicEfficiency_pct": [81.74, 82.99, 83.95, 84.64, 85.12]
    },
    {
      "speed_rpm": 7031.0,
      "flow_m3h": [17735.92, 19543.79, 21351.65, 23159.52, 24967.38],
      "head_kJkg": [233.14, 230.33, 226.34, 220.79, 214.38],
      "polytropicEfficiency_pct": [81.26, 82.67, 83.79, 84.53, 85.05]
    },
    {
      "speed_rpm": 6327.9,
      "flow_m3h": [15510.56, 17055.53, 18600.50, 20145.47, 21690.43],
      "head_kJkg": [187.13, 184.12, 180.13, 175.31, 169.41],
      "polytropicEfficiency_pct": [81.66, 82.93, 83.87, 84.54, 84.80]
    }
  ]
}
```

#### JSON Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `compressorName` | string | No | Name/identifier for the compressor |
| `headUnit` | string | No | Unit for head values (default: "kJ/kg") |
| `maxDesignPower_kW` | number | No | Maximum design power in kW |
| `speedCurves` | array | **Yes** | Array of speed curve objects |

**Speed Curve Object:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `speed_rpm` | number | **Yes** | Rotational speed in RPM |
| `flow_m3h` | array | **Yes** | Flow values in m³/h (actual conditions) |
| `head_kJkg` or `head` | array | **Yes** | Polytropic head in kJ/kg |
| `polytropicEfficiency_pct` | array | **Yes** | Polytropic efficiency in % (0-100) |

**Important Notes:**
- All arrays in a speed curve must have the same length
- Flow, head, and efficiency values must be ordered consistently (typically from surge to stonewall)
- The surge point is automatically detected as the minimum flow point
- The stonewall/choke point is automatically detected as the maximum flow point
- Multiple speed curves should be provided for variable-speed compressors

#### Java Usage

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create a simple gas stream
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(20000.0, "m3/hr");
inlet.setTemperature(35.0, "C");
inlet.setPressure(37.0, "bara");
inlet.run();

// Create compressor and load curves from JSON file
Compressor compressor = new Compressor("K-100", inlet);
compressor.setOutletPressure(110.0, "bara");
compressor.setUsePolytropicCalc(true);

// Load compressor curves from JSON file
compressor.loadCompressorChartFromJson("path/to/compressor_curve.json");

// Set speed and run
compressor.setSpeed(6327.9);  // RPM matching one of the curves
compressor.run();

// Results now use the loaded performance curves
System.out.println("Power: " + compressor.getPower("kW") + " kW");
System.out.println("Polytropic Efficiency: " + (compressor.getPolytropicEfficiency() * 100) + "%");
System.out.println("Polytropic Head: " + compressor.getPolytropicHead("kJ/kg") + " kJ/kg");
```

#### Loading from JSON String

You can also load curves directly from a JSON string:

```java
String jsonString = "{\n" +
  "  \"compressorName\": \"Test Compressor\",\n" +
  "  \"headUnit\": \"kJ/kg\",\n" +
  "  \"speedCurves\": [\n" +
  "    {\n" +
  "      \"speed_rpm\": 10000,\n" +
  "      \"flow_m3h\": [3000, 4000, 5000, 6000],\n" +
  "      \"head_kJkg\": [120, 110, 95, 75],\n" +
  "      \"polytropicEfficiency_pct\": [75, 80, 82, 78]\n" +
  "    }\n" +
  "  ]\n" +
  "}";

compressor.loadCompressorChartFromJsonString(jsonString);
```

#### Python Usage (neqsim-python)

```python
from neqsim.thermo import fluid
from neqsim.process import stream, compressor

# Create inlet stream
gas = fluid('srk')
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.setMixingRule("classic")

inlet = stream(gas)
inlet.setFlowRate(20000.0, "m3/hr")
inlet.setTemperature(35.0, "C")
inlet.setPressure(37.0, "bara")
inlet.run()

# Create compressor
comp = compressor(inlet)
comp.setOutletPressure(110.0, "bara")
comp.setUsePolytropicCalc(True)

# Load curves from JSON file
comp.loadCompressorChartFromJson("compressor_curves/example_compressor_curve.json")

# Set speed and run
comp.setSpeed(6327.9)
comp.run()

print(f"Power: {comp.getPower('kW'):.2f} kW")
print(f"Efficiency: {comp.getPolytropicEfficiency()*100:.2f}%")
```

#### Saving Curves to JSON

You can export a compressor's current curves to JSON:

```java
// Save current compressor chart to JSON file
compressor.saveCompressorChartToJson("output/my_compressor_curve.json");

// Or get as JSON string
String jsonOutput = compressor.getCompressorChartAsJson();
System.out.println(jsonOutput);
```

---

### Loading from CSV Files

CSV format is useful for spreadsheet-based workflows or when importing data from other simulation tools.

#### CSV File Format

The CSV file must use **semicolon (`;`)** as the delimiter and include a header row.

**Required columns:**
- `speed` - Rotational speed in RPM
- `flow` - Volumetric flow in m³/h (actual conditions)
- `head` - Polytropic head in kJ/kg
- `polyEff` - Polytropic efficiency in % (0-100)

**Example CSV file (`compressor_curve.csv`):**

```csv
speed;flow;head;polyEff
7382.55;19852.05;256.69;81.74
7382.55;21679.87;253.67;82.99
7382.55;23507.69;249.29;83.95
7382.55;25335.50;243.58;84.64
7382.55;27163.32;236.91;85.12
7031.00;17735.92;233.14;81.26
7031.00;19543.79;230.33;82.67
7031.00;21351.65;226.34;83.79
7031.00;23159.52;220.79;84.53
7031.00;24967.38;214.38;85.05
6327.90;15510.56;187.13;81.66
6327.90;17055.53;184.12;82.93
6327.90;18600.50;180.13;83.87
6327.90;20145.47;175.31;84.54
6327.90;21690.43;169.41;84.80
```

**Key Points:**
- Each row represents one operating point
- Multiple rows with the same speed define a single speed curve
- Rows are automatically grouped by speed value
- Order of columns does not matter (column names are used)
- Surge and stonewall points are automatically detected from min/max flow per speed

#### Java Usage

```java
// Load compressor curves from CSV file
compressor.loadCompressorChartFromCsv("path/to/compressor_curve.csv");

// The chart is automatically activated
System.out.println("Chart active: " + compressor.getCompressorChart().isUseCompressorChart());
```

#### Creating CSV from Spreadsheet

If you have compressor data in Excel or another spreadsheet:

1. Organize data with columns: `speed`, `flow`, `head`, `polyEff`
2. Ensure each speed curve has multiple flow/head/efficiency points
3. Save as CSV with semicolon delimiter (`;`)
4. Verify the header row matches exactly: `speed;flow;head;polyEff`

**Excel Formula Example (to create CSV):**

| A (speed) | B (flow) | C (head) | D (polyEff) |
|-----------|----------|----------|-------------|
| 10000 | 3000 | 120 | 75 |
| 10000 | 3500 | 115 | 78 |
| 10000 | 4000 | 108 | 80 |
| 11000 | 3300 | 138 | 76 |
| 11000 | 3800 | 132 | 79 |

---

### CompressorChartJsonReader Class

For advanced use cases, you can use the reader class directly:

```java
import neqsim.process.equipment.compressor.CompressorChartJsonReader;

// Create reader from file
CompressorChartJsonReader reader = new CompressorChartJsonReader("compressor_curve.json");

// Get metadata
String name = reader.getCompressorName();
String headUnit = reader.getHeadUnit();
double maxPower = reader.getMaxDesignPower();

// Get curve data
double[] speeds = reader.getSpeeds();
double[][] flows = reader.getFlowLines();
double[][] heads = reader.getHeadLines();
double[][] efficiencies = reader.getPolyEffLines();

// Get automatically detected surge/choke points
double[] surgeFlows = reader.getSurgeFlow();
double[] surgeHeads = reader.getSurgeHead();
double[] chokeFlows = reader.getChokeFlow();
double[] chokeHeads = reader.getChokeHead();

// Apply to compressor
reader.setCurvesToCompressor(compressor);
```

### CompressorChartReader Class (CSV)

```java
import neqsim.process.equipment.compressor.CompressorChartReader;

// Create reader from CSV file
CompressorChartReader reader = new CompressorChartReader("compressor_curve.csv");

// Get curve data
double[] speeds = reader.getSpeeds();
double[][] flows = reader.getFlowLines();
double[][] heads = reader.getHeadLines();
double[][] efficiencies = reader.getPolyEffLines();

// Apply to compressor
reader.setCurvesToCompressor(compressor);
```

---

### Best Practices for File-Based Curves

1. **Consistent Units**: Always use the same units throughout:
   - Flow: m³/h at actual (inlet) conditions
   - Head: kJ/kg (polytropic)
   - Efficiency: % (0-100 scale, polytropic)
   - Speed: RPM

2. **Data Quality**:
   - Include at least 5-10 points per speed curve for accurate interpolation
   - Cover the full operating range from surge to stonewall
   - Ensure efficiency values are physically reasonable (typically 70-90%)

3. **Multiple Speeds**:
   - Include at least 3 speed curves for variable-speed compressors
   - Speed curves should cover the expected operating range

4. **Version Control**:
   - Store JSON/CSV files in version control
   - Include metadata (compressor name, date, source) in JSON files
   - Document the reference conditions (temperature, pressure) used

5. **Validation**:
   - After loading, verify the curves are active:
     ```java
     assert compressor.getCompressorChart().isUseCompressorChart();
     ```
   - Check that min/max speed bounds are set correctly:
     ```java
     System.out.println("Min speed: " + compressor.getMinimumSpeed());
     System.out.println("Max speed: " + compressor.getMaximumSpeed());
     ```

---

## API Reference

### CompressorChartInterface Methods

#### Speed Calculation and Limits

| Method | Description |
|--------|-------------|
| `getSpeed(flow, head)` | Calculate speed for given flow and head (returns int) |
| `getSpeedValue(flow, head)` | Calculate speed for given flow and head (returns double for precision) |
| `getMinSpeedCurve()` | Get minimum speed from defined curves (RPM) |
| `getMaxSpeedCurve()` | Get maximum speed from defined curves (RPM) |
| `isHigherThanMaxSpeed(speed)` | Check if speed exceeds maximum curve speed |
| `isLowerThanMinSpeed(speed)` | Check if speed is below minimum curve speed |
| `isSpeedWithinRange(speed)` | Check if speed is within [min, max] range |
| `getRatioToMaxSpeed(speed)` | Get ratio speed/maxSpeed (>1.0 means above max) |
| `getRatioToMinSpeed(speed)` | Get ratio speed/minSpeed (<1.0 means below min) |

#### Performance Lookup

| Method | Description |
|--------|-------------|
| `getPolytropicHead(flow, speed)` | Get head at given flow and speed |
| `getPolytropicEfficiency(flow, speed)` | Get efficiency at given flow and speed |
| `getFlow(head, speed, guessFlow)` | Get flow for given head and speed |

#### Surge and Stone Wall

| Method | Description |
|--------|-------------|
| `getSurgeCurve()` | Get the surge curve object |
| `getStoneWallCurve()` | Get the stone wall curve object |
| `generateSurgeCurve()` | Auto-generate surge curve from performance curves |
| `generateStoneWallCurve()` | Auto-generate stone wall curve from performance curves |
| `getSurgeFlowAtSpeed(speed)` | Get surge flow at given speed |
| `getSurgeHeadAtSpeed(speed)` | Get surge head at given speed |
| `getStoneWallFlowAtSpeed(speed)` | Get stone wall flow at given speed |
| `getStoneWallHeadAtSpeed(speed)` | Get stone wall head at given speed |

### SafeSplineSurgeCurve

| Method | Description |
|--------|-------------|
| `setCurve(chartConditions, flow, head)` | Set surge curve points (1 or more points) |
| `getSurgeFlow(head)` | Get surge flow at given head |
| `getSurgeHead(flow)` | Get surge head at given flow |
| `isSurge(head, flow)` | Check if point is in surge |
| `isActive()` | Check if curve is active |
| `isSinglePointSurge()` | Check if single-point surge (single-speed) |
| `getSingleSurgeFlow()` | Get single-point surge flow value |
| `getSingleSurgeHead()` | Get single-point surge head value |

### SafeSplineStoneWallCurve

| Method | Description |
|--------|-------------|
| `setCurve(chartConditions, flow, head)` | Set stone wall curve points (1 or more points) |
| `getStoneWallFlow(head)` | Get stone wall flow at given head |
| `getStoneWallHead(flow)` | Get stone wall head at given flow |
| `isStoneWall(head, flow)` | Check if point is at stone wall |
| `isActive()` | Check if curve is active |
| `isSinglePointStoneWall()` | Check if single-point (single-speed) |
| `getSingleStoneWallFlow()` | Get single-point stone wall flow value |
| `getSingleStoneWallHead()` | Get single-point stone wall head value |

### CompressorChartMWInterpolation

| Method | Description |
|--------|-------------|
| `addMapAtMW(mw, chartConditions, speed[], flow[][], head[][], polyEff[][])` | Add multi-speed map with chart conditions |
| `addMapAtMW(mw, chartConditions, speed[], flow[][], head[][], flowPolyEff[][], polyEff[][])` | Add multi-speed map with chart conditions and separate flow arrays |
| `addMapAtMW(mw, speed[], flow[][], head[][], polyEff[][])` | Add multi-speed map (default conditions) |
| `addMapAtMW(mw, speed[], flow[][], head[][], flowPolyEff[][], polyEff[][])` | Add multi-speed map with separate flow arrays (default conditions) |
| `addMapAtMW(mw, speed, flow[], head[], polyEff[])` | Add single-speed map (default conditions) |
| `addMapAtMW(mw, speed, flow[], head[], flowPolyEff[], polyEff[])` | Add single-speed map with separate flow arrays |
| `setOperatingMW(mw)` | Set current operating molecular weight |
| `getOperatingMW()` | Get current operating molecular weight |
| `setInletStream(stream)` | Set inlet stream for auto MW detection |
| `getInletStream()` | Get the inlet stream |
| `setUseActualMW(enabled)` | Enable/disable auto MW from inlet stream (default: true) |
| `isUseActualMW()` | Check if auto MW is enabled |
| `setAllowExtrapolation(enabled)` | Enable/disable extrapolation outside MW range |
| `isAllowExtrapolation()` | Check if extrapolation is enabled |
| `getNumberOfMaps()` | Get number of MW maps defined |
| `getMapMolecularWeights()` | Get list of MW values |
| `getChartAtMW(mw)` | Get chart at specific MW |
| `setAutoGenerateSurgeCurves(enabled)` | Enable auto-generation of surge curves |
| `setAutoGenerateStoneWallCurves(enabled)` | Enable auto-generation of stone wall curves |
| `generateAllSurgeCurves()` | Generate surge curves for all maps |
| `generateAllStoneWallCurves()` | Generate stone wall curves for all maps |
| `setSurgeCurveAtMW(mw, chartConditions, flow, head)` | Set surge curve for specific MW |
| `setStoneWallCurveAtMW(mw, chartConditions, flow, head)` | Set stone wall curve for specific MW |
| `getSurgeFlow(head)` | Get interpolated surge flow at head |
| `getStoneWallFlow(head)` | Get interpolated stone wall flow at head |
| `getSurgeFlowAtSpeed(speed)` | Get interpolated surge flow at speed |
| `getStoneWallFlowAtSpeed(speed)` | Get interpolated stone wall flow at speed |
| `getSurgeHeadAtSpeed(speed)` | Get interpolated surge head at speed |
| `getStoneWallHeadAtSpeed(speed)` | Get interpolated stone wall head at speed |
| `isSurge(head, flow)` | Check if point is in surge (interpolated) |
| `isStoneWall(head, flow)` | Check if point is at stone wall (interpolated) |
| `getDistanceToSurge(head, flow)` | Get distance to surge (interpolated) |
| `getDistanceToStoneWall(head, flow)` | Get distance to stone wall (interpolated) |
| `setInterpolationEnabled(enabled)` | Enable/disable MW interpolation |

### Compressor Methods

| Method | Description |
|--------|-------------|
| `getDistanceToSurge()` | Ratio margin above surge |
| `getDistanceToStoneWall()` | Ratio margin below stone wall |
| `getSurgeFlowRate()` | Surge flow at current head (m³/hr) |
| `getSurgeFlowRateMargin()` | Flow margin above surge (m³/hr) |
| `isSurge(flow, head)` | Check if in surge |
| `isStoneWall()` | Check if at stone wall |
| `isHigherThanMaxSpeed()` | Check if current speed exceeds max curve speed |
| `isHigherThanMaxSpeed(speed)` | Check if given speed exceeds max curve speed |
| `isLowerThanMinSpeed()` | Check if current speed is below min curve speed |
| `isLowerThanMinSpeed(speed)` | Check if given speed is below min curve speed |
| `isSpeedWithinRange()` | Check if current speed is within curve range |
| `isSpeedWithinRange(speed)` | Check if given speed is within curve range |
| `getRatioToMaxSpeed()` | Get ratio of current speed to max speed |
| `getRatioToMaxSpeed(speed)` | Get ratio of given speed to max speed |
| `getRatioToMinSpeed()` | Get ratio of current speed to min speed |
| `getRatioToMinSpeed(speed)` | Get ratio of given speed to min speed |

---

## Python Examples

### Multi-Speed Compressor with Curves

```python
import jpype
import jpype.imports
jpype.startJVM(classpath=['path/to/neqsim.jar'])

from neqsim.thermo.system import SystemSrkEos
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.compressor import Compressor

# Create fluid
fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")
fluid.setTotalFlowRate(5000.0, "Am3/hr")

# Create stream and compressor
stream = Stream("inlet", fluid)
stream.run()

compressor = Compressor("K-100", stream)
compressor.setUsePolytropicCalc(True)
compressor.setOutletPressure(100.0)

# Set multi-speed curves
chartConditions = [25.0, 50.0, 50.0, 20.0]
speed = [8000, 10000, 12000]
flow = [[3000, 4000, 5000], [3500, 4500, 5500], [4000, 5000, 6000]]
head = [[80, 70, 55], [100, 88, 70], [120, 105, 85]]
polyEff = [[78, 80, 76], [79, 81, 77], [78, 80, 76]]

compressor.getCompressorChart().setCurves(chartConditions, speed, flow, head, flow, polyEff)
compressor.getCompressorChart().setHeadUnit("kJ/kg")
compressor.setSpeed(10000)

# Generate surge and stone wall curves automatically
compressor.getCompressorChart().generateSurgeCurve()
compressor.getCompressorChart().generateStoneWallCurve()

compressor.run()

# Check operating margins
print(f"Distance to surge: {compressor.getDistanceToSurge() * 100:.1f}%")
print(f"Distance to stone wall: {compressor.getDistanceToStoneWall() * 100:.1f}%")
```

### Single-Speed Compressor with Single-Point Curves

```python
# Create compressor with single speed
compressor = Compressor("K-100", stream)
compressor.setUsePolytropicCalc(True)
compressor.setOutletPressure(100.0)

# Set single-speed curve
chartConditions = [25.0, 50.0, 50.0, 20.0]
speed = [10250]
flow = [[5607, 6480, 7800, 8750, 9758]]
head = [[150.0, 148.8, 146.1, 140.7, 112.6]]
polyEff = [[78, 80, 80, 78, 70]]

compressor.getCompressorChart().setCurves(chartConditions, speed, flow, head, flow, polyEff)
compressor.getCompressorChart().setHeadUnit("kJ/kg")
compressor.setSpeed(10250)

# Set single-point surge and stone wall
compressor.getCompressorChart().getSurgeCurve().setCurve(
    chartConditions, 
    [5607.45],   # Single surge flow point
    [150.0]      # Single surge head point
)

compressor.getCompressorChart().getStoneWallCurve().setCurve(
    chartConditions,
    [9758.49],   # Single stone wall flow point  
    [112.65]     # Single stone wall head point
)

compressor.run()

# Verify single-point curves are active
surge_curve = compressor.getCompressorChart().getSurgeCurve()
print(f"Surge curve active: {surge_curve.isActive()}")
print(f"Is single-point surge: {surge_curve.isSinglePointSurge()}")
print(f"Surge flow: {surge_curve.getSingleSurgeFlow()}")

# Check operating margins
print(f"Distance to surge: {compressor.getDistanceToSurge() * 100:.1f}%")
print(f"Distance to stone wall: {compressor.getDistanceToStoneWall() * 100:.1f}%")
```

### Molecular Weight Correction with CompressorChartKhader2015

```python
from neqsim.thermo import SystemSrkEos
from neqsim.process import Stream, Compressor

# Import the Khader2015 chart class
from jpype import JClass
CompressorChartKhader2015 = JClass('neqsim.process.equipment.compressor.CompressorChartKhader2015')

# Create the actual operating fluid (composition different from reference)
operating_fluid = SystemSrkEos(298.15, 50.0)
operating_fluid.addComponent("methane", 0.75)  # Different composition
operating_fluid.addComponent("ethane", 0.15)
operating_fluid.addComponent("propane", 0.08)
operating_fluid.addComponent("n-butane", 0.02)
operating_fluid.setMixingRule("classic")
operating_fluid.setTotalFlowRate(5000.0, "Am3/hr")

# Create stream
stream = Stream("inlet", operating_fluid)
stream.run()

# Create compressor
compressor = Compressor("K-100", stream)
compressor.setUsePolytropicCalc(True)
compressor.setOutletPressure(100.0)

# Create Khader2015 chart with impeller diameter
impeller_diameter = 0.3  # meters
chart = CompressorChartKhader2015(stream, impeller_diameter)

# Chart conditions: [temp °C, pres bara, density kg/m³, MW g/mol]
# MW = 20.0 g/mol is the reference molecular weight
chart_conditions = [25.0, 50.0, 50.0, 20.0]

# Reference curves (measured at MW = 20 g/mol)
speed = [10000, 11000, 12000]
flow = [[3500, 4000, 4500, 5000, 5500],
        [3800, 4300, 4800, 5300, 5800],
        [4000, 4500, 5000, 5500, 6000]]
head = [[110, 105, 98, 88, 75],
        [128, 122, 114, 103, 90],
        [148, 141, 132, 120, 105]]
poly_eff = [[77, 80, 81, 79, 74],
            [76, 79, 80, 78, 73],
            [75, 78, 79, 77, 72]]

chart.setCurves(chart_conditions, speed, flow, head, flow, poly_eff)
chart.setHeadUnit("kJ/kg")

# Apply chart - curves are automatically corrected for the operating fluid's MW
compressor.setCompressorChart(chart)
compressor.setSpeed(11000)
compressor.run()

print(f"Operating fluid MW: {operating_fluid.getMolarMass('kg/mol') * 1000:.1f} g/mol")
print(f"Reference fluid MW: {chart_conditions[3]:.1f} g/mol")
print(f"Polytropic head: {compressor.getPolytropicHead('kJ/kg'):.2f} kJ/kg")
print(f"Polytropic efficiency: {compressor.getPolytropicEfficiency() * 100:.1f}%")

# If fluid composition changes, regenerate curves
operating_fluid.addComponent("CO2", 0.05)
operating_fluid.init(0)
chart.generateRealCurvesForFluid()
compressor.run()

print(f"\nAfter adding CO2:")
print(f"New fluid MW: {operating_fluid.getMolarMass('kg/mol') * 1000:.1f} g/mol")
print(f"Polytropic head: {compressor.getPolytropicHead('kJ/kg'):.2f} kJ/kg")
```

---

## Automatic Curve Generation

When you don't have manufacturer performance data, NeqSim can **automatically generate realistic compressor curves** using the `CompressorChartGenerator` class and predefined curve templates.

### Quick Start

```java
// Create and run compressor to establish design point
Compressor compressor = new Compressor("K-100", inletStream);
compressor.setOutletPressure(100.0, "bara");
compressor.setPolytropicEfficiency(0.78);
compressor.setSpeed(10000);
compressor.run();

// Generate curves automatically
CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
CompressorChartInterface chart = generator.generateFromTemplate("PIPELINE", 5);

// Apply and use
compressor.setCompressorChart(chart);
compressor.run();
```

### Why Use Automatic Generation?

| Use Case | Benefit |
|----------|---------|
| **Early design studies** | Estimate performance before vendor data available |
| **Sensitivity analysis** | Quickly evaluate different compressor configurations |
| **Education/training** | Realistic curves without proprietary data |
| **Default behavior** | Reasonable performance when no map is provided |

---

## Compressor Curve Templates

NeqSim provides **12 predefined templates** organized into three categories, each representing typical compressor characteristics for different applications.

### Template Categories Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    COMPRESSOR CURVE TEMPLATES                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────┐ │
│  │   BASIC (3)         │  │  APPLICATION (6)    │  │  TYPE (4)   │ │
│  │                     │  │                     │  │             │ │
│  │  • STANDARD         │  │  • PIPELINE         │  │ • SINGLE    │ │
│  │  • HIGH_FLOW        │  │  • EXPORT           │  │   _STAGE    │ │
│  │  • HIGH_HEAD        │  │  • INJECTION        │  │ • MULTI     │ │
│  │                     │  │  • GAS_LIFT         │  │   STAGE     │ │
│  │                     │  │  • REFRIGERATION    │  │ • INTEGRAL  │ │
│  │                     │  │  • BOOSTER          │  │   _GEARED   │ │
│  │                     │  │                     │  │ • OVERHUNG  │ │
│  └─────────────────────┘  └─────────────────────┘  └─────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Basic Centrifugal Templates

Generic centrifugal compressor characteristics for general use.

| Template | Peak η | Flow Range | Head | Best For |
|----------|--------|------------|------|----------|
| `CENTRIFUGAL_STANDARD` | ~78% | Medium | Medium | General purpose, default choice |
| `CENTRIFUGAL_HIGH_FLOW` | ~78% | Wide | Lower | High throughput, low pressure ratio |
| `CENTRIFUGAL_HIGH_HEAD` | ~78% | Narrow | High | High pressure ratio, multiple stages |

### Application-Based Templates

Optimized for specific oil & gas applications.

| Template | Peak η | Typical Use | Key Characteristics |
|----------|--------|-------------|---------------------|
| `PIPELINE` | **82-85%** | Gas transmission | High capacity, flat curves, wide turndown (~40%) |
| `EXPORT` | ~80% | Offshore gas export | High pressure, stable operation, 6-8 stages |
| `INJECTION` | ~77% | Gas injection/EOR | Very high pressure ratio, lower capacity |
| `GAS_LIFT` | ~75% | Artificial lift | Wide surge margin (~35%), liquid tolerant |
| `REFRIGERATION` | ~78% | LNG/process cooling | Wide operating range, part-load efficiency |
| `BOOSTER` | ~76% | Process plant | Moderate pressure ratio (2-4), balanced design |

### Compressor Type Templates

Based on mechanical design characteristics.

| Template | Peak η | Pressure Ratio | Design Features |
|----------|--------|----------------|-----------------|
| `SINGLE_STAGE` | ~75% | 1.5-2.5 | Simple, wide flow range, cost-effective |
| `MULTISTAGE_INLINE` | ~78% | 5-15 | Barrel type, 4-8 stages, O&G standard |
| `INTEGRALLY_GEARED` | **82%** | Flexible | Multiple pinions, air separation, optimized |
| `OVERHUNG` | ~74% | Low-medium | Cantilever, simple maintenance |

---

## Template Selection Guide

### Decision Flowchart

```
                          ┌─────────────────────────┐
                          │ What is the application?│
                          └───────────┬─────────────┘
                                      │
           ┌──────────────┬───────────┼───────────┬──────────────┐
           ▼              ▼           ▼           ▼              ▼
    ┌────────────┐ ┌────────────┐ ┌─────────┐ ┌─────────┐ ┌────────────┐
    │Gas Pipeline│ │ Offshore   │ │  EOR /  │ │Gas Lift │ │Refriger.   │
    │Transmission│ │   Export   │ │Injection│ │         │ │ / LNG      │
    └─────┬──────┘ └─────┬──────┘ └────┬────┘ └────┬────┘ └─────┬──────┘
          │              │             │           │             │
          ▼              ▼             ▼           ▼             ▼
      PIPELINE        EXPORT      INJECTION    GAS_LIFT    REFRIGERATION


                          ┌─────────────────────────┐
                          │  What type of machine?  │
                          └───────────┬─────────────┘
                                      │
           ┌──────────────┬───────────┴───────────┬──────────────┐
           ▼              ▼                       ▼              ▼
    ┌────────────┐ ┌────────────┐         ┌────────────┐ ┌────────────┐
    │   Simple   │ │   Barrel   │         │ Integrally │ │  Overhung  │
    │Single Stage│ │ Multistage │         │   Geared   │ │ Cantilever │
    └─────┬──────┘ └─────┬──────┘         └─────┬──────┘ └─────┬──────┘
          │              │                      │              │
          ▼              ▼                      ▼              ▼
    SINGLE_STAGE   MULTISTAGE_INLINE    INTEGRALLY_GEARED   OVERHUNG
```

### Quick Selection Matrix

| Your Requirement | Recommended Template |
|------------------|---------------------|
| Default / don't know | `CENTRIFUGAL_STANDARD` |
| Large capacity, moderate PR | `PIPELINE` |
| High discharge pressure | `EXPORT` or `INJECTION` |
| Variable inlet conditions | `GAS_LIFT` |
| Process cooling/LNG | `REFRIGERATION` |
| Simple, low PR | `SINGLE_STAGE` |
| High PR, compact | `MULTISTAGE_INLINE` |
| Highest efficiency | `INTEGRALLY_GEARED` |
| Small duty, easy maintenance | `OVERHUNG` |

---

## Using the CompressorChartGenerator

### Basic Usage

```java
// Create generator from compressor
CompressorChartGenerator generator = new CompressorChartGenerator(compressor);

// Option 1: Generate from template (recommended)
CompressorChartInterface chart = generator.generateFromTemplate("PIPELINE", 5);

// Option 2: Generate "normal curves" from operating point
CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

// Option 3: Single speed curve
CompressorChartInterface chart = generator.generateCompressorChart("normal curves");
```

### Setting Chart Type

The generator supports three output chart types:

```java
generator.setChartType("interpolate and extrapolate");  // Default, most flexible
generator.setChartType("interpolate");                  // No extrapolation
generator.setChartType("simple");                       // Basic fan law scaling
```

| Chart Type | Class | Use Case |
|------------|-------|----------|
| `interpolate and extrapolate` | `CompressorChartAlternativeMapLookupExtrapolate` | Production, wide operating range |
| `interpolate` | `CompressorChartAlternativeMapLookup` | Stay within measured envelope |
| `simple` | `CompressorChart` | Basic calculations, teaching |

### Specifying Custom Speeds

```java
// Using number of speeds (auto-distributed)
CompressorChartInterface chart = generator.generateFromTemplate("EXPORT", 5);
// → Generates 5 curves from 70% to 100% of design speed

// Using specific speed values
double[] speeds = {7000, 8000, 9000, 10000, 10500};
CompressorChartInterface chart = generator.generateCompressorChart("normal curves", speeds);
```

### Advanced Corrections

Enable industry-standard corrections for more accurate off-design performance:

```java
CompressorChartGenerator generator = new CompressorChartGenerator(compressor);

// Individual corrections
generator.setUseReynoldsCorrection(true);      // Efficiency correction for Re
generator.setUseMachCorrection(true);          // Choke flow limitation
generator.setUseMultistageSurgeCorrection(true); // Surge line shift at low speeds
generator.setNumberOfStages(6);
generator.setImpellerDiameter(0.35);

// Or enable all at once
generator.enableAdvancedCorrections(6);  // 6 stages

CompressorChartInterface chart = generator.generateFromTemplate("MULTISTAGE_INLINE", 5);
```

#### Correction Effects

| Correction | Effect | When Important |
|------------|--------|----------------|
| **Reynolds** | Adjusts η at low Re (high viscosity, low speed) | Heavy gases, low-speed operation |
| **Mach** | Limits stonewall flow based on sonic velocity | Light gases (H₂), high speed |
| **Multistage Surge** | Shifts surge to higher flow at reduced speed | Variable speed, >3 stages |

---

## Template API Reference

### Listing Available Templates

```java
// Get all templates
String[] all = CompressorCurveTemplate.getAvailableTemplates();
// → ["CENTRIFUGAL_STANDARD", "CENTRIFUGAL_HIGH_FLOW", ..., "OVERHUNG"]

// Get by category
String[] basic = CompressorCurveTemplate.getTemplatesByCategory("basic");
// → ["CENTRIFUGAL_STANDARD", "CENTRIFUGAL_HIGH_FLOW", "CENTRIFUGAL_HIGH_HEAD"]

String[] application = CompressorCurveTemplate.getTemplatesByCategory("application");
// → ["PIPELINE", "EXPORT", "INJECTION", "GAS_LIFT", "REFRIGERATION", "BOOSTER"]

String[] type = CompressorCurveTemplate.getTemplatesByCategory("type");
// → ["SINGLE_STAGE", "MULTISTAGE_INLINE", "INTEGRALLY_GEARED", "OVERHUNG"]
```

### Template Name Matching

The `getTemplate()` method is flexible with naming:

```java
// All of these return the same template:
CompressorCurveTemplate.getTemplate("GAS_LIFT");
CompressorCurveTemplate.getTemplate("gas-lift");
CompressorCurveTemplate.getTemplate("gas lift");
CompressorCurveTemplate.getTemplate("gaslift");

// Abbreviations work too:
CompressorCurveTemplate.getTemplate("igc");     // → INTEGRALLY_GEARED
CompressorCurveTemplate.getTemplate("barrel");  // → MULTISTAGE_INLINE
CompressorCurveTemplate.getTemplate("LNG");     // → REFRIGERATION
```

### Working Directly with Templates

```java
// Get template object
CompressorCurveTemplate template = CompressorCurveTemplate.PIPELINE;

// Get unscaled original chart
CompressorChartInterface originalChart = template.getOriginalChart();

// Scale to specific speed
CompressorChartInterface scaledChart = template.scaleToSpeed(8000);

// Scale to design point
CompressorChartInterface chart = template.scaleToDesignPoint(
    10000,   // designSpeed (RPM)
    5000,    // designFlow (m³/hr)
    85.0,    // designHead (kJ/kg)
    5        // numberOfSpeeds
);

// Get template metadata
String name = template.getName();
double refSpeed = template.getReferenceSpeed();
double[] speedRatios = template.getSpeedRatios();
```

---

## Complete Java Example

```java
import neqsim.process.equipment.compressor.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorCurveGenerationExample {
    public static void main(String[] args) {
        // 1. Create fluid and inlet stream
        SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
        gas.addComponent("methane", 0.85);
        gas.addComponent("ethane", 0.10);
        gas.addComponent("propane", 0.05);
        gas.setMixingRule("classic");
        
        Stream inlet = new Stream("inlet", gas);
        inlet.setFlowRate(15000.0, "kg/hr");
        inlet.setTemperature(25.0, "C");
        inlet.setPressure(40.0, "bara");
        inlet.run();
        
        // 2. Create compressor at design point
        Compressor comp = new Compressor("K-100", inlet);
        comp.setOutletPressure(120.0, "bara");
        comp.setUsePolytropicCalc(true);
        comp.setPolytropicEfficiency(0.78);
        comp.setSpeed(9500);
        comp.run();
        
        System.out.println("=== Design Point ===");
        System.out.println("Flow: " + String.format("%.1f", inlet.getFlowRate("m3/hr")) + " m³/hr");
        System.out.println("Head: " + String.format("%.1f", comp.getPolytropicFluidHead()) + " kJ/kg");
        System.out.println("Power: " + String.format("%.1f", comp.getPower("kW")) + " kW");
        
        // 3. Generate curves using EXPORT template (offshore gas export)
        CompressorChartGenerator generator = new CompressorChartGenerator(comp);
        generator.setChartType("interpolate and extrapolate");
        generator.enableAdvancedCorrections(6);  // 6-stage compressor
        
        CompressorChartInterface chart = generator.generateFromTemplate("EXPORT", 5);
        
        // 4. Apply chart and verify
        comp.setCompressorChart(chart);
        comp.run();
        
        System.out.println("\n=== With Generated Chart ===");
        System.out.println("Speeds available: " + chart.getSpeeds().length);
        System.out.println("Efficiency from chart: " + 
            String.format("%.1f", comp.getPolytropicEfficiency() * 100) + "%");
        System.out.println("Distance to surge: " + 
            String.format("%.1f", comp.getDistanceToSurge() * 100) + "%");
        
        // 5. Test at different operating point
        inlet.setFlowRate(12000.0, "kg/hr");
        inlet.run();
        comp.run();
        
        System.out.println("\n=== Turndown Operation ===");
        System.out.println("Flow: " + String.format("%.1f", inlet.getFlowRate("m3/hr")) + " m³/hr");
        System.out.println("Efficiency: " + 
            String.format("%.1f", comp.getPolytropicEfficiency() * 100) + "%");
        System.out.println("Distance to surge: " + 
            String.format("%.1f", comp.getDistanceToSurge() * 100) + "%");
    }
}
```

---

## Python Example

```python
from neqsim import jNeqSim
from neqsim.process import stream, compressor

# Import Java classes
SystemSrkEos = jNeqSim.thermo.system.SystemSrkEos
Stream = jNeqSim.process.equipment.stream.Stream
Compressor = jNeqSim.process.equipment.compressor.Compressor
CompressorChartGenerator = jNeqSim.process.equipment.compressor.CompressorChartGenerator
CompressorCurveTemplate = jNeqSim.process.equipment.compressor.CompressorCurveTemplate

# Create fluid
gas = SystemSrkEos(298.15, 50.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.07)
gas.addComponent("propane", 0.03)
gas.setMixingRule("classic")

# Create stream
inlet = Stream("inlet", gas)
inlet.setFlowRate(20000.0, "kg/hr")
inlet.setTemperature(30.0, "C")
inlet.setPressure(45.0, "bara")
inlet.run()

# Create compressor
comp = Compressor("K-100", inlet)
comp.setOutletPressure(150.0, "bara")
comp.setUsePolytropicCalc(True)
comp.setPolytropicEfficiency(0.77)
comp.setSpeed(10000)
comp.run()

# List available templates
print("Available templates:")
for cat in ["basic", "application", "type"]:
    templates = CompressorCurveTemplate.getTemplatesByCategory(cat)
    print(f"  {cat}: {list(templates)}")

# Generate chart using INJECTION template (high pressure application)
generator = CompressorChartGenerator(comp)
generator.setChartType("interpolate and extrapolate")
generator.enableAdvancedCorrections(8)  # 8-stage injection compressor

chart = generator.generateFromTemplate("INJECTION", 5)
comp.setCompressorChart(chart)
comp.run()

print(f"\nDesign efficiency: {comp.getPolytropicEfficiency() * 100:.1f}%")
print(f"Design head: {comp.getPolytropicFluidHead():.1f} kJ/kg")
print(f"Power: {comp.getPower('MW'):.2f} MW")
print(f"Surge margin: {comp.getDistanceToSurge() * 100:.1f}%")
```

---

## Template Technical Specifications

### PIPELINE Template

```
Application: Natural gas transmission (30-50 MW class)
Reference Speed: 5500 RPM (large direct-drive or gear-driven)
Peak Efficiency: 85%
Turndown: ~40%

Curve Characteristics:
        Head
         ↑
    79 ──┼────╮
         │     ╲   Flat curve
    65 ──┼──────╲──  for pipeline
         │       ╲   stability
    53 ──┼────────╲─
         │         ╲
         └──────────┴────→ Flow
           30k    70k m³/hr
```

### INJECTION Template

```
Application: Gas injection/EOR (very high pressure)
Reference Speed: 11000 RPM
Peak Efficiency: 77%
Pressure Ratio: 50-200 (overall, with intercooling)

Curve Characteristics:
        Head
         ↑
   245 ──┼──╮
         │   ╲   Steep curve
   165 ──┼────╲──  (high head
         │     ╲    per stage)
   130 ──┼──────╲
         │       ╲
         └────────┴────→ Flow
           2.5k   6k m³/hr
           (lower capacity)
```

### INTEGRALLY_GEARED Template

```
Application: Air separation, process air
Reference Speed: 20000 RPM (pinion speed)
Peak Efficiency: 82% (highest of all templates)

Design Features:
  - Bull gear drives multiple pinions
  - Each pinion has optimized impeller
  - Intercooling between stages

┌─────────────────────────┐
│     BULL GEAR           │
│    ╭───────╮            │
│   ╱  ●   ●  ╲           │  ● = Pinion with impeller
│  │  ●     ●  │          │
│   ╲  ●   ●  ╱           │
│    ╰───────╯            │
└─────────────────────────┘
```

---

## Dynamic Simulation Features ⭐ NEW

NeqSim now provides comprehensive dynamic simulation capabilities for compressors, including state machines, event-driven control, driver modeling, and startup/shutdown sequences.

### Overview

The dynamic simulation features enable realistic transient simulations including:

- **Compressor State Machine**: Track operating states (STOPPED, STARTING, RUNNING, SURGE_PROTECTION, etc.)
- **Event-Driven Control**: Listeners for surge approach, speed limits, power limits, state changes
- **Driver Modeling**: Electric motors, gas turbines, steam turbines with power limits and efficiency curves
- **Inertia Modeling**: Realistic acceleration/deceleration with rate limits
- **Startup/Shutdown Profiles**: Sequenced startup and shutdown procedures
- **Operating History**: Track and export operating points for post-simulation analysis
- **Performance Degradation**: Model fouling and performance reduction over time

### Compressor States

The `CompressorState` enum defines the possible operating states:

| State | Description | Can Start? | Is Operational? |
|-------|-------------|------------|-----------------|
| `STOPPED` | Compressor is not running | Yes | No |
| `STARTING` | Startup sequence in progress | No | No |
| `RUNNING` | Normal operation | No | Yes |
| `SURGE_PROTECTION` | Near or in surge, recycle active | No | Yes |
| `SPEED_LIMITED` | At max/min speed limit | No | Yes |
| `SHUTDOWN` | Shutdown sequence in progress | No | No |
| `DEPRESSURIZING` | Pressure settling after shutdown | No | No |
| `TRIPPED` | Emergency shutdown, requires acknowledgment | No | No |
| `STANDBY` | Ready to start after trip acknowledgment | Yes | No |

### Basic Dynamic Simulation Setup

```java
// Create compressor with dynamic features
Compressor comp = new Compressor("K-100", inletStream);
comp.setOutletPressure(100.0, "bara");
comp.setSpeed(10000);
comp.run();

// Enable dynamic simulation features
comp.enableOperatingHistory();
comp.setRotationalInertia(15.0);  // kg⋅m² combined rotor inertia
comp.setMaxAccelerationRate(100.0);  // RPM/s
comp.setMaxDecelerationRate(200.0);  // RPM/s

// Set surge margin thresholds
comp.setSurgeWarningThreshold(0.15);   // 15% margin triggers warning
comp.setSurgeCriticalThreshold(0.05);  // 5% margin triggers critical alarm

// Configure anti-surge controller
AntiSurge antiSurge = comp.getAntiSurge();
antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PID);
antiSurge.setPIDParameters(2.0, 0.5, 0.1);
antiSurge.setValveResponseTime(2.0);  // seconds

// Start compressor with startup profile
comp.startCompressor(10000);  // Target speed
```

### Event Listeners

Listen for compressor events during dynamic simulation:

```java
// Create event listener
CompressorEventListener listener = new CompressorEventListener() {
    @Override
    public void onSurgeApproach(Compressor compressor, double surgeMargin, boolean isCritical) {
        if (isCritical) {
            System.out.println("CRITICAL: Surge margin only " + surgeMargin * 100 + "%");
        } else {
            System.out.println("WARNING: Approaching surge, margin = " + surgeMargin * 100 + "%");
        }
    }

    @Override
    public void onSurgeOccurred(Compressor compressor, double surgeMargin) {
        System.out.println("ALARM: Compressor in surge!");
    }

    @Override
    public void onSpeedLimitExceeded(Compressor compressor, double currentSpeed, double ratio) {
        System.out.println("Speed limit exceeded: " + currentSpeed + " RPM (" + ratio * 100 + "% of max)");
    }

    @Override
    public void onSpeedBelowMinimum(Compressor compressor, double currentSpeed, double ratio) {
        System.out.println("Speed below minimum: " + currentSpeed + " RPM");
    }

    @Override
    public void onPowerLimitExceeded(Compressor compressor, double currentPower, double maxPower) {
        System.out.println("Power limit exceeded: " + currentPower + " kW > " + maxPower + " kW");
    }

    @Override
    public void onStateChange(Compressor compressor, CompressorState oldState, CompressorState newState) {
        System.out.println("State change: " + oldState + " -> " + newState);
    }

    @Override
    public void onStoneWallApproach(Compressor compressor, double stoneWallMargin) {
        System.out.println("Approaching stone wall, margin = " + stoneWallMargin * 100 + "%");
    }

    @Override
    public void onStartupComplete(Compressor compressor) {
        System.out.println("Startup complete!");
    }

    @Override
    public void onShutdownComplete(Compressor compressor) {
        System.out.println("Shutdown complete!");
    }
};

// Register listener
comp.addEventListener(listener);
```

### Driver Modeling

Model driver (motor, turbine) characteristics:

```java
// Create driver model
CompressorDriver driver = new CompressorDriver(DriverType.GAS_TURBINE, 5000);  // 5000 kW gas turbine
driver.setMaxPower(5500);  // 110% overload capacity
driver.setInertia(25.0);   // kg⋅m² combined inertia
driver.setMaxAcceleration(80.0);   // RPM/s
driver.setMaxDeceleration(150.0);  // RPM/s

// For gas turbines: ambient temperature derating
driver.setAmbientTemperature(308.15);  // 35°C
driver.setTemperatureDerateFactor(0.005);  // 0.5% power reduction per °C above ISO

// For VFD motors: efficiency vs speed curve
CompressorDriver vfdDriver = new CompressorDriver(DriverType.VFD_MOTOR, 3000);
vfdDriver.setVfdEfficiencyCoefficients(0.90, 0.05, -0.02);  // η = a + b*(N/Nrated) + c*(N/Nrated)²

// Apply driver to compressor
comp.setDriver(driver);
```

### Driver Types

| Driver Type | Typical Efficiency | Response Time | Characteristics |
|-------------|-------------------|---------------|-----------------|
| `ELECTRIC_MOTOR` | 95% | 1 s | Fixed speed, constant torque |
| `VFD_MOTOR` | 93% | 5 s | Variable speed, high efficiency |
| `GAS_TURBINE` | 35% | 30 s | Variable speed, ambient temp dependent |
| `STEAM_TURBINE` | 40% | 20 s | Variable speed, steam supply dependent |
| `RECIPROCATING_ENGINE` | 42% | 15 s | High efficiency, limited speed range |
| `EXPANDER_DRIVE` | 85% | 5 s | Direct drive from process expander |

### Startup/Shutdown Profiles

Define sequenced startup and shutdown procedures:

```java
// Create startup profile
StartupProfile startup = new StartupProfile();
startup.setMinimumIdleSpeed(3000);      // RPM
startup.setIdleHoldTime(60.0);           // seconds at idle
startup.setWarmupRampRate(50.0);         // RPM/s during warmup
startup.setNormalRampRate(100.0);        // RPM/s during normal ramp
startup.setRequireAntisurgeOpen(true);
startup.setAntisurgeOpeningDuration(10.0);  // seconds before start

// Or use predefined profiles
StartupProfile fastStartup = StartupProfile.createFastProfile(10000);  // Emergency restart
StartupProfile slowStartup = StartupProfile.createSlowProfile(10000, 3000);  // Cold start

comp.setStartupProfile(startup);

// Create shutdown profile
ShutdownProfile shutdown = new ShutdownProfile(ShutdownProfile.ShutdownType.NORMAL, 10000);
shutdown.setNormalRampRate(100.0);       // RPM/s
shutdown.setIdleRundownTime(30.0);       // seconds at idle before stop
shutdown.setOpenAntisurgeOnShutdown(true);

comp.setShutdownProfile(shutdown);

// Start/stop compressor
comp.startCompressor(10000);  // Start with target speed
comp.stopCompressor();        // Normal shutdown
comp.emergencyShutdown();     // Emergency shutdown (trips compressor)
```

### Anti-Surge Control Strategies

The enhanced `AntiSurge` class supports multiple control strategies:

| Strategy | Description | Best For |
|----------|-------------|----------|
| `ON_OFF` | Simple on/off based on surge line | Simple systems, teaching |
| `PROPORTIONAL` | Linear valve opening based on margin | Most applications |
| `PID` | Full PID control with anti-windup | Precise control, variable load |
| `PREDICTIVE` | Uses rate-of-change prediction | Rapid load changes |
| `DUAL_LOOP` | Separate surge and capacity loops | Complex systems |

```java
AntiSurge antiSurge = comp.getAntiSurge();

// Select control strategy
antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PID);

// Configure PID
antiSurge.setPIDParameters(2.0, 0.5, 0.1);  // Kp, Ki, Kd
antiSurge.setPIDSetpoint(0.10);              // 10% surge margin setpoint

// Configure predictive control
antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PREDICTIVE);
antiSurge.setPredictiveHorizon(5.0);         // 5 second look-ahead

// Valve dynamics
antiSurge.setValveResponseTime(2.0);         // First-order time constant
antiSurge.setValveRateLimit(0.5);            // Max 50% per second
antiSurge.setMinimumRecycleFlow(500.0);      // Minimum flow m³/hr
antiSurge.setMaximumRecycleFlow(3000.0);     // Maximum flow m³/hr

// Surge cycle trip protection
antiSurge.setMaxSurgeCyclesBeforeTrip(3);    // Trip after 3 surge cycles
```

### Dynamic Simulation Loop

Update compressor state during transient simulation:

```java
double dt = 0.1;  // 100 ms time step
double simTime = 0.0;

// Startup compressor
comp.startCompressor(10000);

while (simTime < 600.0) {  // 10 minute simulation
    // Update inlet conditions (from upstream process)
    inletStream.run();
    
    // Run compressor
    comp.run();
    
    // Update dynamic state (handles startup/shutdown, checks limits)
    comp.updateDynamicState(dt);
    
    // Update anti-surge controller
    double surgeMargin = comp.getDistanceToSurge();
    comp.getAntiSurge().updateController(surgeMargin, dt);
    
    // Record to history
    comp.recordOperatingPoint(simTime);
    
    simTime += dt;
}

// Export operating history
comp.getOperatingHistory().exportToCSV("compressor_history.csv");
System.out.println(comp.getOperatingHistory().generateSummary());
```

### Operating History Analysis

Track and analyze operating history:

```java
// Enable history tracking
comp.enableOperatingHistory();

// ... run simulation ...

// Get history summary
CompressorOperatingHistory history = comp.getOperatingHistory();
System.out.println("Total points recorded: " + history.getPointCount());
System.out.println("Surge events: " + history.getSurgeEventCount());
System.out.println("Time in surge: " + history.getTimeInSurge() + " s");
System.out.println("Minimum surge margin: " + history.getMinimumSurgeMargin() * 100 + "%");
System.out.println("Average efficiency: " + history.getAverageEfficiency() * 100 + "%");

// Get peak values
CompressorOperatingHistory.OperatingPoint peakPower = history.getPeakPower();
System.out.println("Peak power: " + peakPower.getPower() + " kW at t=" + peakPower.getTime() + " s");

// Export to CSV for plotting
history.exportToCSV("compressor_history.csv");

// Full summary report
System.out.println(history.generateSummary());
```

### Performance Degradation

Model compressor performance degradation over time:

```java
// Set degradation factor (1.0 = new, <1.0 = degraded)
comp.setDegradationFactor(0.95);  // 5% degradation

// Set fouling factor (head reduction)
comp.setFoulingFactor(0.03);  // 3% head reduction due to fouling

// Track operating hours
comp.setOperatingHours(25000);  // Initial operating hours
comp.addOperatingHours(100);    // Add 100 hours

// Get effective performance
double effectiveHead = comp.getEffectivePolytropicHead();  // Accounts for degradation
double effectiveEff = comp.getEffectivePolytropicEfficiency();  // Accounts for degradation
```

### Auto-Speed Mode

Automatically calculate speed from operating point:

```java
// Enable auto-speed mode
comp.setAutoSpeedMode(true);

// During simulation, speed is calculated from flow and head
comp.run();  // Speed automatically adjusted based on chart
```

### Python Example - Dynamic Simulation

```python
from neqsim import jNeqSim
from jpype import JClass

# Import classes
Compressor = jNeqSim.process.equipment.compressor.Compressor
CompressorState = JClass('neqsim.process.equipment.compressor.CompressorState')
CompressorDriver = JClass('neqsim.process.equipment.compressor.CompressorDriver')
DriverType = JClass('neqsim.process.equipment.compressor.DriverType')
AntiSurge = jNeqSim.process.equipment.compressor.AntiSurge
StartupProfile = JClass('neqsim.process.equipment.compressor.StartupProfile')
ShutdownProfile = JClass('neqsim.process.equipment.compressor.ShutdownProfile')

# Create compressor (assuming inlet stream exists)
comp = Compressor("K-100", inlet_stream)
comp.setOutletPressure(100.0, "bara")
comp.setSpeed(10000)
comp.run()

# Configure dynamic features
comp.enableOperatingHistory()
comp.setRotationalInertia(15.0)
comp.setSurgeWarningThreshold(0.15)
comp.setSurgeCriticalThreshold(0.05)

# Set up gas turbine driver
driver = CompressorDriver(DriverType.GAS_TURBINE, 5000)
driver.setAmbientTemperature(308.15)  # 35°C
comp.setDriver(driver)

# Configure anti-surge
anti_surge = comp.getAntiSurge()
anti_surge.setControlStrategy(AntiSurge.ControlStrategy.PID)
anti_surge.setPIDParameters(2.0, 0.5, 0.1)

# Run dynamic simulation
dt = 0.1  # 100 ms
sim_time = 0.0

comp.startCompressor(10000)

while sim_time < 300.0:
    inlet_stream.run()
    comp.run()
    comp.updateDynamicState(dt)
    
    # Print state periodically
    if int(sim_time) % 10 == 0 and sim_time == int(sim_time):
        print(f"t={sim_time:.0f}s: State={comp.getOperatingState()}, "
              f"Speed={comp.getSpeed():.0f} RPM, "
              f"Surge margin={comp.getDistanceToSurge()*100:.1f}%")
    
    sim_time += dt

# Export results
comp.getOperatingHistory().exportToCSV("compressor_dynamic.csv")
print(str(comp.getOperatingHistory().generateSummary()))
```

---

## Related Documentation

- [Compressor Equipment](compressors.md) - Basic compressor usage
- [Process Simulation](../../simulation/README.md) - Process simulation overview
