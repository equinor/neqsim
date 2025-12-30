# Compressor Curves and Performance Maps

Detailed documentation for compressor performance curves in NeqSim, including multi-speed and single-speed compressor handling.

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
- [Anti-Surge Control](#anti-surge-control)
- [API Reference](#api-reference)
- [Python Examples](#python-examples)

---

## Overview

NeqSim supports comprehensive compressor performance modeling through compressor charts (performance maps). The key classes are:

| Class | Description |
|-------|-------------|
| `CompressorChart` | Standard compressor chart with polynomial interpolation |
| `CompressorChartKhader2015` | Advanced chart with Khader 2015 method and fan law scaling |
| `CompressorChartMWInterpolation` | Multi-map chart with MW interpolation between maps |
| `SafeSplineSurgeCurve` | Spline-based surge curve with safe extrapolation |
| `SafeSplineStoneWallCurve` | Spline-based stone wall (choke) curve |
| `CompressorCurve` | Individual speed curve (flow, head, efficiency) |

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

## API Reference

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

## Related Documentation

- [Compressor Equipment](compressors.md) - Basic compressor usage
- [Anti-Surge Control](../control/antisurge.md) - Anti-surge system details
- [Process Simulation](../../simulation/README.md) - Process simulation overview
