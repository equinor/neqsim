---
title: Multiphase Flow Correlations
description: "Multiphase pipe flow correlations in NeqSim: Beggs and Brill, Hagedorn-Brown, and Mukherjee-Brill models for pressure drop, liquid holdup, and flow pattern prediction in vertical, horizontal, and inclined pipelines."
---

# Multiphase Flow Correlations

NeqSim provides several empirical and mechanistic correlations for multiphase pipe
flow. This page covers the three main correlations, when to use each, and code examples.

## Correlation Comparison

| Correlation | Class | Inclination | Best For |
|-------------|-------|------------|----------|
| Beggs & Brill (1973) | `PipeBeggsAndBrills` | All angles | General-purpose, most-used industry default |
| Hagedorn-Brown (1965) | `PipeHagedornBrown` | Vertical / near-vertical | Oil production wells, gas lift wells |
| Mukherjee-Brill (1985) | `PipeMukherjeeAndBrill` | All angles | Improved holdup prediction, flow pattern map |

### Selection Guidelines

- **Vertical oil/gas wells:** Hagedorn-Brown — validated for production tubing, good holdup prediction
- **Horizontal or inclined pipelines:** Beggs & Brill or Mukherjee-Brill
- **All-inclination with flow patterns:** Mukherjee-Brill — provides flow pattern identification (stratified, slug, annular, bubble)
- **Quick estimates / general pipelines:** Beggs & Brill — widest adoption, well understood limitations

## Hagedorn-Brown Correlation

The Hagedorn-Brown (1965) method is an empirical holdup correlation developed for
vertical wells. It does not predict flow patterns but uses dimensionless correlations
to estimate liquid holdup as a function of flow rates, fluid properties, and pipe geometry.

### Pressure Gradient

The total pressure gradient combines hydrostatic, friction, and acceleration terms:

$$\frac{dP}{dL} = \frac{\rho_m g \sin\theta}{1 - \frac{\rho_m v_m v_{sg}}{P}} + \frac{f \rho_m v_m^2}{2 d}$$

where $\rho_m$ is the mixture density based on the Hagedorn-Brown holdup, $v_m$ is the
mixture velocity, $v_{sg}$ is the superficial gas velocity, and $f$ is the Moody friction factor.

### Usage

```java
import neqsim.process.equipment.pipeline.PipeHagedornBrown;

PipeHagedornBrown well = new PipeHagedornBrown("Production Tubing", feedStream);
well.setLength(3000.0);     // 3000 m TVD
well.setDiameter(0.1);      // 4-inch tubing
well.setElevation(-3000.0);  // Vertical downward (production)
well.setNumberOfIncrements(50);
well.setPipeWallRoughness(4.5e-5);
well.run();

// Results
double outletPressure = well.getOutletStream().getPressure();
double[] pressureProfile = well.getPressureProfile();
double[] temperatureProfile = well.getTemperatureProfile();
double liquidHoldup = well.getLiquidHoldup();

System.out.println("Outlet P: " + outletPressure + " bara");
System.out.println("Liquid holdup: " + liquidHoldup);
```

## Mukherjee-Brill Correlation

The Mukherjee-Brill (1985) correlation extends the Beggs-Brill approach with improved
holdup predictions for all pipe inclinations and an updated flow pattern map.

### Flow Patterns

The correlation identifies five flow patterns based on dimensionless parameters:

| Flow Pattern | `FlowPattern` Enum | Description |
|-------------|---------------------|-------------|
| Stratified | `STRATIFIED` | Low flow — gas on top, liquid on bottom |
| Slug | `SLUG` | Intermittent liquid slugs |
| Annular | `ANNULAR` | Gas core with liquid film on wall |
| Bubble | `BUBBLE` | Dispersed gas bubbles in liquid |
| Single phase | `SINGLE_PHASE` | Only gas or only liquid |

### Usage

```java
import neqsim.process.equipment.pipeline.PipeMukherjeeAndBrill;

PipeMukherjeeAndBrill pipeline = new PipeMukherjeeAndBrill("Export Pipeline", feedStream);
pipeline.setLength(25000.0);   // 25 km
pipeline.setDiameter(0.254);   // 10-inch
pipeline.setElevation(0.0);    // Horizontal
pipeline.setNumberOfIncrements(100);
pipeline.setPipeWallRoughness(4.5e-5);
pipeline.run();

// Results
double outletP = pipeline.getOutletStream().getPressure();
String pattern = pipeline.getFlowPattern();
double liquidHoldup = pipeline.getLiquidHoldup();
List<String> patternProfile = pipeline.getFlowPatternProfile();

System.out.println("Outlet P: " + outletP + " bara");
System.out.println("Outlet flow pattern: " + pattern);
```

### Downhill Flow

Mukherjee-Brill handles downhill flow (negative inclination) better than Beggs-Brill,
making it suitable for terrain-following pipelines:

```java
pipeline.setElevation(-200.0);  // 200 m downhill over pipeline length
pipeline.run();
```

## Beggs & Brill (Existing)

The Beggs & Brill (1973) correlation is NeqSim's most established multiphase pipe flow
model. See the dedicated documentation:

- [Beggs & Brill Reference](../../process/PipeBeggsAndBrills)
- [Pipeline Recipes Cookbook](../../cookbook/pipeline-recipes)

## Comparing Correlations

To compare pressure drop predictions from different correlations on the same
conditions, create three pipe instances with the same inlet stream:

```java
// Feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

// Beggs & Brill
PipeBeggsAndBrills pBB = new PipeBeggsAndBrills("BB", feed);
pBB.setLength(5000.0);
pBB.setDiameter(0.2);
pBB.setElevation(-5000.0);  // Vertical downward
pBB.setNumberOfIncrements(50);
pBB.run();

// Hagedorn-Brown
PipeHagedornBrown pHB = new PipeHagedornBrown("HB", feed);
pHB.setLength(5000.0);
pHB.setDiameter(0.2);
pHB.setElevation(-5000.0);  // Vertical downward
pHB.setNumberOfIncrements(50);
pHB.run();

// Mukherjee-Brill
PipeMukherjeeAndBrill pMB = new PipeMukherjeeAndBrill("MB", feed);
pMB.setLength(5000.0);
pMB.setDiameter(0.2);
pMB.setElevation(-5000.0);  // Vertical downward
pMB.setNumberOfIncrements(50);
pMB.run();

System.out.printf("Beggs-Brill outlet P: %.1f bara%n", pBB.getOutletStream().getPressure());
System.out.printf("Hagedorn-Brown outlet P: %.1f bara%n", pHB.getOutletStream().getPressure());
System.out.printf("Mukherjee-Brill outlet P: %.1f bara%n", pMB.getOutletStream().getPressure());
```

## Profile Data Extraction

All three correlations return profile data as `double[]` arrays (one value per
increment) for integration with plotting tools:

| Method | Returns | Available On |
|--------|---------|-------------|
| `getPressureProfile()` | `double[]` | All three |
| `getTemperatureProfile()` | `double[]` | All three |
| `getLiquidHoldup()` | `double` | HagedornBrown, MukherjeeAndBrill |
| `getFlowPatternProfile()` | `List<String>` | MukherjeeAndBrill only |
| `getFlowPattern()` | `String` | MukherjeeAndBrill |
| `getFlowPatternEnum()` | `FlowPattern` | MukherjeeAndBrill |

## Related Documentation

- [Pipeline Models](pipelines) - Pipeline and riser models
- [Well Simulation Guide](../../simulation/well_simulation_guide) - Well simulation
- [TwoFluidPipe Model](../../process/TWOFLUIDPIPE_MODEL) - Mechanistic two-fluid model
- [Pipeline Recipes](../../cookbook/pipeline-recipes) - Cookbook recipes for pipeline simulations
- [Absorbers](absorbers) - Mass transfer columns
