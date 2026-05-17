---
title: "Erosion Prediction Calculator"
description: "Sand erosion prediction using API RP 14E erosional velocity and DNV RP O501 particle erosion models for pipelines, elbows, tees, chokes, and other geometries."
---

# Erosion Prediction Calculator

The `ErosionPredictionCalculator` provides sand erosion assessment for production piping and process equipment using two industry-standard methods.

**Class:** `neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator`

## Standards Implemented

| Standard | Method | Output |
|----------|--------|--------|
| **API RP 14E** | Erosional velocity limit | Max allowable velocity (m/s) |
| **DNV RP O501** | Sand particle erosion model | Erosion rate (mm/yr), cumulative loss |

---

## API RP 14E - Erosional Velocity

The API RP 14E erosional velocity formula limits mixture velocity to prevent erosion:

$$V_e = \frac{C}{\sqrt{\rho_m}}$$

Where:
- $V_e$ = erosional velocity (ft/s, converted to m/s internally)
- $C$ = empirical constant (default 100 for continuous service)
- $\rho_m$ = mixture density at flowing conditions (lb/ft3)

### C-Factor Guidelines

| Service | C-Factor |
|---------|----------|
| Continuous (default) | 100 |
| Intermittent | 125 |
| Solids-free, inhibited | 150 |
| With sand production | 75-100 |

---

## DNV RP O501 - Sand Erosion Model

The DNV model predicts material loss rate from sand particle impact:

$$E = K \cdot F(geometry) \cdot \dot{m}_p \cdot V_p^n \cdot f(\alpha)$$

Where:
- $K$ = material constant (depends on pipe material grade)
- $F$ = geometry factor (elbow, tee, choke, etc.)
- $\dot{m}_p$ = sand mass flow rate
- $V_p$ = particle impact velocity
- $f(\alpha)$ = impact angle function

### Supported Materials

| Material | Typical Application |
|----------|-------------------|
| `carbon_steel` | General piping |
| `duplex_steel` | Moderate corrosion environments |
| `super_duplex` | Severe corrosion + erosion |
| `13cr` | Downhole tubing |
| `inconel` | High temperature, corrosive |
| `titanium` | Seawater systems |
| `tungsten_carbide` | Choke trim |

### Supported Geometries

| Geometry | Typical Use |
|----------|------------|
| `straight_pipe` | Straight pipe sections |
| `elbow` | Standard pipe elbows |
| `tee` | Tee junctions |
| `blind_tee` | Dead-end tees (sand traps) |
| `reducer` | Pipe reducers |
| `choke` | Production chokes |
| `weld` | Girth/field welds |

---

## Usage

### Basic Erosional Velocity Check

```java
import neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator;

ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
calc.setMixtureDensity(120.0);    // kg/m3
calc.setMixtureVelocity(12.0);   // m/s (actual velocity)
calc.setApiCFactor(100.0);       // Continuous service
calc.calculate();

double erosionalVelocity = calc.getErosionalVelocity();
double velocityRatio = calc.getVelocityRatio();
boolean withinLimits = calc.isWithinApiLimits();

System.out.printf("Erosional velocity: %.1f m/s%n", erosionalVelocity);
System.out.printf("Velocity ratio: %.2f%n", velocityRatio);
System.out.printf("Within API limits: %s%n", withinLimits);
```

### Sand Erosion Assessment

```java
ErosionPredictionCalculator calc = new ErosionPredictionCalculator();

// Flow conditions
calc.setMixtureDensity(150.0);    // kg/m3
calc.setMixtureVelocity(15.0);   // m/s

// Pipe geometry
calc.setPipeDiameter(0.1524);     // 6 inch (meters)
calc.setWallThickness(10.0);     // mm

// Sand parameters
calc.setSandRate(50.0);           // kg/day
calc.setSandParticleDiameter(0.25); // mm

// Material and geometry
calc.setPipeMaterial("carbon_steel");
calc.setGeometry("elbow");

// Design parameters
calc.setDesignLife(25.0);         // years
calc.setCorrosionAllowance(3.0);  // mm

calc.calculate();

// Results
double erosionRate = calc.getErosionRate();         // mm/yr
double cumulative = calc.getCumulativeErosion();     // mm over design life
double remaining = calc.getRemainingWallThickness(); // mm
String riskLevel = calc.getRiskLevel();              // low/medium/high/critical

System.out.printf("Erosion rate: %.4f mm/yr%n", erosionRate);
System.out.printf("Cumulative erosion (25 yr): %.2f mm%n", cumulative);
System.out.printf("Remaining wall: %.2f mm%n", remaining);
System.out.printf("Risk level: %s%n", riskLevel);
```

### Multiple Geometry Comparison

```java
String[] geometries = {"straight_pipe", "elbow", "blind_tee", "tee", "choke"};

for (String geometry : geometries) {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(150.0);
    calc.setMixtureVelocity(15.0);
    calc.setPipeDiameter(0.1524);
    calc.setSandRate(50.0);
    calc.setPipeMaterial("carbon_steel");
    calc.setGeometry(geometry);
    calc.calculate();

    System.out.printf("%-15s  Erosion: %.4f mm/yr  Risk: %s%n",
        geometry, calc.getErosionRate(), calc.getRiskLevel());
}
```

### JSON Output

```java
String json = calc.toJson();
System.out.println(json);
```

---

## Sand Load Defaults by Completion Type

The calculator provides default sand production parameters based on well completion type, enabling quick screening assessments without detailed sand monitoring data.

### Available Completion Types

| Completion | Sand Rate (g/m3) | Particle Diameter (μm) | Description |
|------------|-------------------|-------------------------|-------------|
| `openhole_gravel_pack` | 5.0 | 200 | Open hole with gravel pack |
| `cased_perforated` | 15.0 | 250 | Cased and perforated |
| `standalone_screen` | 3.0 | 150 | Standalone screen in open hole |
| `frac_pack` | 8.0 | 180 | Hydraulic fracture with gravel pack |
| `expandable_screen` | 4.0 | 170 | Expandable sand screen |

### Usage

```java
ErosionPredictionCalculator calc = new ErosionPredictionCalculator();

// Apply defaults for a completion type
calc.applySandLoadDefaults("cased_perforated");
// Sets sandRate = 15.0 g/m3, sandParticleDiameter = 250 μm

// Get defaults without applying
ErosionPredictionCalculator.SandLoadDefaults defaults =
    calc.getSandLoadDefaults("frac_pack");
System.out.println(defaults.getSandRate());           // 8.0
System.out.println(defaults.getSandParticleDiameter()); // 180.0
System.out.println(defaults.getDescription());        // "Hydraulic fracture with gravel pack"

// List all available completion types
List<String> types = calc.getAvailableCompletionTypes();
```

## Maximum Velocity for Corrosion Protection

In addition to erosion limits, the calculator provides a velocity limit to protect internal corrosion barriers (coatings, inhibitor films):

```java
// Calculate maximum velocity that preserves corrosion protection
double maxVel = calc.calcMaxVelocityForCorrosionProtection(
    150.0,    // mixture density kg/m3
    0.0002    // sand concentration kg/kg (200 ppm)
);
// Typical range: 5-20 m/s depending on conditions
```

---

## Related Documentation

- [Flow Assurance Screening Tools](flow_assurance_screening_tools) - Pipeline cooldown, corrosion, scale
- [Emulsion Viscosity](emulsion_viscosity_calculator) - Emulsion viscosity models

```java
calc.calculate();
String json = calc.toJson();
System.out.println(json);
```

Output includes:
- API RP 14E results (erosional velocity, velocity ratio, within limits)
- DNV RP O501 results (erosion rate, cumulative, remaining wall)
- Risk assessment (level: low/medium/high/critical)
- All input parameters for traceability

---

## Risk Assessment

The risk level is determined by the velocity ratio (actual / erosional):

| Velocity Ratio | Risk Level |
|----------------|------------|
| < 0.5 | Low |
| 0.5 - 0.8 | Medium |
| 0.8 - 1.0 | High |
| > 1.0 | Critical |

---

## Python Usage

```python
from neqsim import jneqsim

ErosionPredictionCalculator = jneqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator

calc = ErosionPredictionCalculator()
calc.setMixtureDensity(150.0)
calc.setMixtureVelocity(15.0)
calc.setPipeDiameter(0.1524)
calc.setSandRate(50.0)
calc.setPipeMaterial("carbon_steel")
calc.setGeometry("elbow")
calc.setDesignLife(25.0)
calc.setCorrosionAllowance(3.0)
calc.calculate()

print(f"Erosion rate: {calc.getErosionRate():.4f} mm/yr")
print(f"Risk: {calc.getRiskLevel()}")
print(calc.toJson())
```

---

## Related Documentation

- [Flow Assurance Screening Tools](../pvtsimulation/flowassurance/flow_assurance_screening_tools)
- [Flow Assurance Overview](../pvtsimulation/flow_assurance_overview)
- [Emulsion Viscosity Calculator](emulsion_viscosity_calculator)
