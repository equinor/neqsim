# Flow Pattern Detection

Automatic detection and classification of two-phase flow regimes in pipe flow using mechanistic models.

**Related Documentation:**
- [TwoPhasePipeFlowModel.md](TwoPhasePipeFlowModel.md) - Two-phase flow governing equations
- [MassTransferAPI.md](MassTransferAPI.md) - Flow pattern-specific mass transfer correlations
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer.md) - Interfacial area by flow pattern

## Table of Contents

- [Overview](#overview)
- [Flow Patterns](#flow-patterns)
- [Detection Models](#detection-models)
  - [Taitel-Dukler Model](#taitel-dukler-model)
  - [Baker Chart](#baker-chart)
  - [Barnea Model](#barnea-model)
  - [Beggs-Brill Correlation](#beggs-brill-correlation)
- [FlowPatternDetector Class](#flowpatterndetector-class)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Integration with Pipeline Models](#integration-with-pipeline-models)
- [Python Examples](#python-examples)

---

## Overview

Flow pattern detection is essential for accurate two-phase flow simulation because:

1. **Pressure Drop**: Different flow patterns have different friction factors and gravitational pressure drop
2. **Heat Transfer**: Interfacial area and convection depend on flow regime
3. **Mass Transfer**: Gas-liquid contact varies significantly between patterns
4. **Equipment Sizing**: Separators and slug catchers require flow regime information

**Location:** `neqsim.fluidmechanics.flownode`

### Flow Pattern Map

```
                         Superficial Gas Velocity (m/s)
                    0.1    1      10     100
                    │      │       │       │
          1000  ────┼──────┼───────┼───────┼───── Dispersed Bubble
                    │      │       │       │
Superficial   10 ────┼──────┼───────┼───────┼───── Annular / Mist
Liquid              │      │       │       │
Velocity     1  ────┼──────┼───────┼───────┼───── Slug / Intermittent
(m/s)               │      │       │       │
           0.1  ────┼──────┼───────┼───────┼───── Stratified (Smooth/Wavy)
                    │      │       │       │
          0.01  ────┴──────┴───────┴───────┴─────
```

---

## Flow Patterns

NeqSim recognizes the following flow patterns:

| Flow Pattern | Description | Typical Conditions |
|--------------|-------------|-------------------|
| `STRATIFIED` | Liquid at bottom, smooth interface | Low gas & liquid velocity |
| `STRATIFIED_WAVY` | Stratified with wavy interface | Moderate gas velocity |
| `SLUG` | Large liquid slugs filling pipe | Moderate velocities |
| `PLUG` | Elongated gas bubbles | Low gas, moderate liquid |
| `ANNULAR` | Liquid film on wall, gas core | High gas velocity |
| `DISPERSED_BUBBLE` | Gas bubbles in liquid | High liquid velocity |
| `BUBBLE` | Small bubbles rising | Vertical, low gas velocity |
| `CHURN` | Oscillatory motion | Vertical, transition regime |

### FlowPattern Enum

```java
public enum FlowPattern {
    STRATIFIED,
    STRATIFIED_WAVY,
    SLUG,
    PLUG,
    ANNULAR,
    DISPERSED_BUBBLE,
    BUBBLE,
    CHURN,
    UNKNOWN
}
```

---

## Detection Models

### Taitel-Dukler Model

The Taitel-Dukler (1976) mechanistic model is the most widely used for horizontal and near-horizontal pipes.

**Reference:** Taitel, Y., & Dukler, A.E. (1976). "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.

**Transition Criteria:**

| Transition | Mechanism | Criterion |
|------------|-----------|-----------|
| Stratified → Slug | Kelvin-Helmholtz instability | Wave growth on interface |
| Slug → Annular | Liquid film stability | Minimum film thickness |
| Stratified → Annular | Film suspension | Minimum gas velocity |
| Bubble → Slug | Void fraction limit | α > 0.25 |

**Dimensionless Parameters:**

- **Froude Number (F):** $F = \sqrt{\frac{\rho_G}{\rho_L - \rho_G}} \frac{u_{SG}}{\sqrt{gD\cos\theta}}$

- **Lockhart-Martinelli (X):** $X = \sqrt{\frac{\rho_L}{\rho_G} \cdot \frac{\mu_L}{\mu_G}} \cdot \frac{u_{SL}}{u_{SG}}$

- **Kelvin-Helmholtz (K):** $K = F \cdot \sqrt{Re_{SL}}$

```java
// Use Taitel-Dukler model
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.TAITEL_DUKLER,
    usg,        // Superficial gas velocity (m/s)
    usl,        // Superficial liquid velocity (m/s)
    rhoG,       // Gas density (kg/m³)
    rhoL,       // Liquid density (kg/m³)
    muG,        // Gas viscosity (Pa·s)
    muL,        // Liquid viscosity (Pa·s)
    sigma,      // Surface tension (N/m)
    diameter,   // Pipe diameter (m)
    inclination // Pipe inclination (radians, + = upward)
);
```

### Baker Chart

The Baker (1954) chart is an empirical flow pattern map using dimensionless parameters.

**Baker Parameters:**

- $\lambda = \sqrt{\frac{\rho_G}{\rho_{air}} \cdot \frac{\rho_L}{\rho_{water}}}$

- $\psi = \frac{\sigma_{water}}{\sigma} \cdot \left(\frac{\mu_L}{\mu_{water}} \cdot \left(\frac{\rho_{water}}{\rho_L}\right)^2\right)^{1/3}$

```java
// Use Baker chart
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.BAKER_CHART,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination
);
```

### Barnea Model

The Barnea (1987) model extends Taitel-Dukler for all pipe inclinations, including vertical.

**Key Features:**
- Valid for all inclination angles (-90° to +90°)
- Includes churn flow for vertical pipes
- More accurate near-vertical transitions

```java
// Use Barnea model for vertical/inclined pipes
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.BARNEA,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, 
    Math.toRadians(45.0)  // 45° upward inclination
);
```

### Beggs-Brill Correlation

Empirical correlation optimized for oil & gas applications.

```java
// Use Beggs-Brill
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.BEGGS_BRILL,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination
);
```

---

## FlowPatternDetector Class

The `FlowPatternDetector` is a utility class providing static methods for flow pattern detection.

### Basic Usage

```java
import neqsim.fluidmechanics.flownode.FlowPatternDetector;
import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.fluidmechanics.flownode.FlowPatternModel;

// Fluid and flow properties
double usg = 5.0;           // Superficial gas velocity (m/s)
double usl = 0.5;           // Superficial liquid velocity (m/s)
double rhoG = 50.0;         // Gas density (kg/m³)
double rhoL = 800.0;        // Liquid density (kg/m³)
double muG = 1.5e-5;        // Gas viscosity (Pa·s)
double muL = 1.0e-3;        // Liquid viscosity (Pa·s)
double sigma = 0.025;       // Surface tension (N/m)
double diameter = 0.2;      // Pipe diameter (m)
double inclination = 0.0;   // Horizontal (radians)

// Detect flow pattern
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.TAITEL_DUKLER,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination
);

System.out.println("Flow Pattern: " + pattern);
// Output: Flow Pattern: SLUG
```

### Using NeqSim Thermodynamics

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("nC10", 0.2);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

// Extract properties
double rhoG = fluid.getPhase("gas").getDensity("kg/m3");
double rhoL = fluid.getPhase("oil").getDensity("kg/m3");
double muG = fluid.getPhase("gas").getViscosity("kg/msec");
double muL = fluid.getPhase("oil").getViscosity("kg/msec");
double sigma = fluid.getInterphaseProperties().getSurfaceTension(
    fluid.getPhaseIndex("gas"), fluid.getPhaseIndex("oil"));

// Calculate superficial velocities from flow rates
double totalArea = Math.PI * Math.pow(diameter / 2, 2);
double gasVolumetricRate = 1.0;   // m³/s
double liquidVolumetricRate = 0.1; // m³/s
double usg = gasVolumetricRate / totalArea;
double usl = liquidVolumetricRate / totalArea;

// Detect flow pattern
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.TAITEL_DUKLER,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, 0.0
);
```

---

## Usage Examples

### Flow Pattern Along Pipeline

Calculate flow pattern changes along a pipeline:

```java
import neqsim.fluidmechanics.flownode.*;

// Pipeline parameters
double length = 10000.0;  // m
double diameter = 0.3;    // m
int nSegments = 50;
double segmentLength = length / nSegments;

// Track flow pattern transitions
List<String> transitions = new ArrayList<>();
FlowPattern previousPattern = null;

for (int i = 0; i < nSegments; i++) {
    double distance = i * segmentLength;
    
    // Get local properties (from pipeline simulation)
    double localRhoG = getGasDensity(distance);
    double localRhoL = getLiquidDensity(distance);
    double localUsg = getSuperficialGasVelocity(distance);
    double localUsl = getSuperficialLiquidVelocity(distance);
    double localSigma = getSurfaceTension(distance);
    double localMuG = getGasViscosity(distance);
    double localMuL = getLiquidViscosity(distance);
    double localInclination = getPipeInclination(distance);
    
    FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
        FlowPatternModel.TAITEL_DUKLER,
        localUsg, localUsl, localRhoG, localRhoL, 
        localMuG, localMuL, localSigma, diameter, localInclination
    );
    
    if (pattern != previousPattern) {
        transitions.add(String.format(
            "%.0fm: %s → %s", distance, previousPattern, pattern
        ));
        previousPattern = pattern;
    }
}

// Print transitions
transitions.forEach(System.out::println);
```

### Detecting Slug Flow for Slug Catcher Design

```java
// Check for slug flow that requires slug catcher
FlowPattern pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.TAITEL_DUKLER,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination
);

if (pattern == FlowPattern.SLUG) {
    System.out.println("WARNING: Slug flow detected - slug catcher required");
    
    // Estimate slug characteristics (simplified)
    double slugVelocity = 1.2 * (usg + usl);
    double slugFrequency = 0.1;  // Hz (estimated)
    double slugLength = slugVelocity / slugFrequency;
    
    System.out.println("Estimated slug velocity: " + slugVelocity + " m/s");
    System.out.println("Estimated slug length: " + slugLength + " m");
}
```

---

## API Reference

### FlowPatternDetector

| Method | Description |
|--------|-------------|
| `detectFlowPattern(model, usg, usl, rhoG, rhoL, muG, muL, sigma, D, theta)` | Main detection method |
| `detectTaitelDukler(...)` | Taitel-Dukler specific |
| `detectBakerChart(...)` | Baker chart specific |
| `detectBarnea(...)` | Barnea model specific |
| `detectBeggsBrill(...)` | Beggs-Brill specific |

### FlowPatternModel Enum

| Value | Description | Best For |
|-------|-------------|----------|
| `TAITEL_DUKLER` | Mechanistic model | Horizontal/near-horizontal |
| `BAKER_CHART` | Empirical chart | Quick estimates |
| `BARNEA` | Extended mechanistic | All inclinations |
| `BEGGS_BRILL` | Empirical correlation | Oil & gas applications |
| `MANUAL` | User override | Special cases |

### FlowPattern Enum

| Value | Description |
|-------|-------------|
| `STRATIFIED` | Smooth stratified |
| `STRATIFIED_WAVY` | Wavy stratified |
| `SLUG` | Slug/intermittent |
| `PLUG` | Plug flow |
| `ANNULAR` | Annular/mist |
| `DISPERSED_BUBBLE` | Dispersed bubble |
| `BUBBLE` | Bubble flow |
| `CHURN` | Churn flow |
| `UNKNOWN` | Could not determine |

---

## Integration with Pipeline Models

The flow pattern detector integrates with NeqSim pipeline models:

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

// Create pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Pipeline", feedStream);
pipeline.setLength(5000.0);
pipeline.setDiameter(0.25);
pipeline.setInclination(0.0);

// Enable automatic flow pattern detection
pipeline.setFlowPatternModel(FlowPatternModel.TAITEL_DUKLER);

// Run simulation
pipeline.run();

// Get detected flow pattern
FlowPattern pattern = pipeline.getFlowPattern();
System.out.println("Detected flow pattern: " + pattern);
```

---

## Python Examples

### Basic Flow Pattern Detection

```python
from jpype import JClass

# Import classes
FlowPatternDetector = JClass('neqsim.fluidmechanics.flownode.FlowPatternDetector')
FlowPatternModel = JClass('neqsim.fluidmechanics.flownode.FlowPatternModel')

# Flow conditions
usg = 3.0       # m/s
usl = 0.3       # m/s
rhoG = 40.0     # kg/m³
rhoL = 750.0    # kg/m³
muG = 1.5e-5    # Pa·s
muL = 2.0e-3    # Pa·s
sigma = 0.022   # N/m
diameter = 0.2  # m
inclination = 0.0  # radians

# Detect pattern
pattern = FlowPatternDetector.detectFlowPattern(
    FlowPatternModel.TAITEL_DUKLER,
    usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, inclination
)

print(f"Flow Pattern: {pattern}")
```

### Flow Pattern Map Generation

```python
import numpy as np
import matplotlib.pyplot as plt

# Generate flow pattern map
usg_range = np.logspace(-1, 2, 50)  # 0.1 to 100 m/s
usl_range = np.logspace(-2, 1, 50)  # 0.01 to 10 m/s

patterns = np.zeros((len(usl_range), len(usg_range)))

for i, usl in enumerate(usl_range):
    for j, usg in enumerate(usg_range):
        pattern = FlowPatternDetector.detectFlowPattern(
            FlowPatternModel.TAITEL_DUKLER,
            usg, usl, rhoG, rhoL, muG, muL, sigma, diameter, 0.0
        )
        patterns[i, j] = pattern.ordinal()

# Plot
plt.figure(figsize=(10, 8))
plt.contourf(usg_range, usl_range, patterns, levels=8, cmap='viridis')
plt.xscale('log')
plt.yscale('log')
plt.xlabel('Superficial Gas Velocity (m/s)')
plt.ylabel('Superficial Liquid Velocity (m/s)')
plt.title('Flow Pattern Map (Taitel-Dukler)')
plt.colorbar(label='Flow Pattern')
plt.savefig('flow_pattern_map.png', dpi=150)
```

---

## References

1. Taitel, Y., & Dukler, A.E. (1976). "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.

2. Baker, O. (1954). "Simultaneous flow of oil and gas." Oil and Gas Journal, 53, 185-195.

3. Barnea, D. (1987). "A unified model for predicting flow-pattern transitions for the whole range of pipe inclinations." International Journal of Multiphase Flow, 13(1), 1-12.

4. Beggs, H.D., & Brill, J.P. (1973). "A study of two-phase flow in inclined pipes." Journal of Petroleum Technology, 25(05), 607-617.

---

## Related Documentation

- [Pipeline Simulation](../process/equipment/pipeline_simulation.md) - Pipeline flow models
- [Two-Phase Pipe Flow](TwoPhasePipeFlowSystem_Development_Plan.md) - Advanced two-phase modeling
- [Heat Transfer](heat_transfer.md) - Flow pattern effects on heat transfer

---

*Package Location: `neqsim.fluidmechanics.flownode`*
