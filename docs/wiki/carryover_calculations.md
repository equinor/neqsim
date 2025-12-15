# Liquid Carry-Over Calculations in NeqSim Separators

This document provides technical details on liquid carry-over calculations for gas scrubbers and separators in NeqSim. It covers the theory, implemented correlations, and guidance for enhancing carry-over models.

## Table of Contents

1. [Overview](#overview)
2. [Physical Background](#physical-background)
3. [Primary Separation Carry-Over](#primary-separation-carry-over)
4. [Demisting Internal Carry-Over](#demisting-internal-carry-over)
5. [Separator Section Carry-Over](#separator-section-carry-over)
6. [Current Implementation Status](#current-implementation-status)
7. [Enhancement Opportunities](#enhancement-opportunities)

---

## Overview

Liquid carry-over is the undesired entrainment of liquid droplets in the gas phase exiting a separator. In gas scrubbers, minimizing carry-over is critical for:

- Protecting downstream equipment (compressors, heat exchangers)
- Meeting product gas specifications
- Preventing hydrate formation in pipelines
- Ensuring process safety

NeqSim models carry-over through three main mechanisms:

| Component | Location | Responsibility |
|-----------|----------|----------------|
| **Primary Separation** | `primaryseparation` package | Initial bulk liquid removal at inlet |
| **Demisting Internals** | `internals` package | Fine mist removal before gas outlet |
| **Separator Sections** | `sectiontype` package | Individual section efficiency tracking |

---

## Physical Background

### Droplet Settling Velocity

The terminal settling velocity of a liquid droplet in gas follows Stokes' law for small droplets:

$$v_t = \frac{d_p^2 (\rho_L - \rho_G) g}{18 \mu_G}$$

Where:
- $v_t$ = terminal settling velocity (m/s)
- $d_p$ = droplet diameter (m)
- $\rho_L$ = liquid density (kg/m³)
- $\rho_G$ = gas density (kg/m³)
- $g$ = gravitational acceleration (9.81 m/s²)
- $\mu_G$ = gas dynamic viscosity (Pa·s)

### Souders-Brown Equation

The maximum allowable gas velocity in a separator is governed by the Souders-Brown equation:

$$v_{max} = K_{SB} \sqrt{\frac{\rho_L - \rho_G}{\rho_G}}$$

Where:
- $v_{max}$ = maximum gas velocity (m/s)
- $K_{SB}$ = Souders-Brown coefficient (m/s), typically 0.03-0.15

The gas load factor $K$ relates actual velocity to maximum velocity:

$$K = v_{actual} \sqrt{\frac{\rho_G}{\rho_L - \rho_G}}$$

### Carry-Over Mechanisms

1. **Momentum-driven entrainment**: High gas velocity creates shear at gas-liquid interface
2. **Re-entrainment**: Liquid collected on internals is re-entrained by gas flow
3. **Droplet breakthrough**: Small droplets pass through mesh/vane without capture

---

## Primary Separation Carry-Over

Primary separation devices (inlet vanes, cyclones, deflectors) perform bulk liquid removal at the separator inlet. Carry-over from primary separation depends on:

- Inlet nozzle velocity and momentum
- Device geometry (expansion ratio, deflection angle)
- Liquid loading at inlet

### Current Classes

| Class | Key Parameters | Carry-Over Model |
|-------|---------------|------------------|
| `PrimarySeparation` | `inletNozzleDiameter` | Base model with velocity factor |
| `InletVane` | `geometricalExpansionRatio` | Expansion-based efficiency |
| `InletVaneWithMeshpad` | `vaneToMeshpadDistance`, `freeDistanceAboveMeshpad` | Combined settling + coalescence |
| `InletCyclones` | `numberOfCyclones`, `cycloneDiameter` | Centrifugal separation |

### InletVane Carry-Over Calculation

Current implementation in `InletVane.calcLiquidCarryOver()`:

```java
// Expansion ratio efficiency: higher ratio means better separation
double expansionEfficiency = Math.min(geometricalExpansionRatio / 5.0, 1.0);

// Velocity effect: higher velocity increases carry-over
double velocityFactor = Math.min(1.0, inletVelocity / 10.0);

// Combined effect
double carryOverFactor = velocityFactor * (1.0 - 0.5 * expansionEfficiency);

return carryOverFactor * inletLiquidContent;
```

**Placeholder Status**: This is a simplified correlation. Industry correlations (e.g., from Koch-Glitsch, Sulzer) should be validated.

### InletVaneWithMeshpad Carry-Over Calculation

Current implementation accounts for:

1. **Vane deflection efficiency** (95% assumed for 90° deflection)
2. **Velocity factor** (linear up to 10 m/s reference)
3. **Meshpad coalescence** (based on free distance above meshpad)
4. **Settling efficiency** (based on vane-to-meshpad distance)

```java
// Combined carry-over: reduced by both meshpad coalescence and settling
double totalCarryOverFactor = vaneCarryOver 
    * (1.0 - 0.4 * meshpadCoalescenceEfficiency)
    * (1.0 - 0.3 * settlingEfficiency);

return totalCarryOverFactor * inletLiquidContent;
```

### InletCyclones Separation Efficiency

Cyclone efficiency based on centrifugal force correlation:

```java
// Centrifugal acceleration in cyclone
double centrifugalAcceleration = inletVelocity * inletVelocity / (cycloneDiameter / 2.0);

// Stokes number for separation
double stokesNumber = (liquidDensity - gasDensity) * Math.pow(dropletDiameter, 2) 
    * centrifugalAcceleration / (18.0 * liquidViscosity * inletVelocity);
```

---

## Demisting Internal Carry-Over

Demisting internals (mesh pads, vane packs, cyclones) provide final mist elimination before gas exits.

### Current Classes

| Class | Description | Carry-Over Model |
|-------|-------------|------------------|
| `DemistingInternal` | Base demister | Area-based exponential model |
| `DemistingInternalWithDrainage` | With drain pipes | Improved efficiency via drainage |

### DemistingInternal Calculations

**Carry-Over Calculation:**

```java
public double calcLiquidCarryOver() {
    double inletLiquidContent = separator.getInletLiquidContent();
    
    // Exponential reduction with area
    double calibrationConstant = 0.5;  // [PLACEHOLDER]
    double carryOverFactor = Math.exp(-calibrationConstant * area);
    
    return carryOverFactor * inletLiquidContent;
}
```

**Separation Efficiency:**

```java
public double calcEfficiency() {
    double calibrationConstant = 0.5;  // [PLACEHOLDER]
    return 1.0 - Math.exp(-calibrationConstant * area);
}
```

**Pressure Drop (Euler Number):**

$$\Delta p = Eu \cdot \rho_G \cdot v^2$$

```java
public double calcPressureDrop(double gasDensity, double gasVelocity) {
    return euNumber * gasDensity * gasVelocity * gasVelocity;
}
```

### DemistingInternalWithDrainage

Drainage pipes improve efficiency by preventing re-entrainment:

```java
@Override
public double calcLiquidCarryOver() {
    double baseCarryOver = super.calcLiquidCarryOver();
    // Drainage reduces carry-over
    return baseCarryOver * (1.0 - drainageEfficiency);
}
```

---

## Separator Section Carry-Over

`GasScrubberSimple` calculates overall carry-over from separator sections:

```java
public double calcLiquidCarryoverFraction() {
    double carryover = 1.0;
    for (SeparatorSection section : separatorSections) {
        if (section.isCalcEfficiency()) {
            double sectionEfficiency = section.getEfficiency();
            carryover *= (1.0 - sectionEfficiency);
        }
    }
    return carryover;
}
```

Overall carry-over fraction = $\prod_i (1 - \eta_i)$ where $\eta_i$ is each section's efficiency.

---

## Current Implementation Status

### What's Implemented

| Feature | Status | Notes |
|---------|--------|-------|
| Primary separation classes | ✅ Complete | `InletVane`, `InletVaneWithMeshpad`, `InletCyclones` |
| Demisting internal classes | ✅ Complete | `DemistingInternal`, `DemistingInternalWithDrainage` |
| `calcLiquidCarryOver()` methods | ⚠️ Placeholder | Simplified correlations need validation |
| `calcEfficiency()` methods | ⚠️ Placeholder | Generic exponential model |
| Pressure drop calculations | ✅ Complete | Based on Euler number |
| Input validation | ✅ Complete | All setters validate inputs |

### Placeholder Correlations

The following use hardcoded constants marked as `[PLACEHOLDER]`:

1. **InletVane**: Reference velocity (10 m/s), expansion ratio reference (5.0)
2. **InletVaneWithMeshpad**: Vane efficiency (0.95), distance references (0.3m, 0.5m)
3. **DemistingInternal**: Calibration constant (0.5)
4. **InletCyclones**: Droplet diameter assumption, efficiency correlation

---

## Enhancement Opportunities

### 1. Configurable Calibration Constants

**Current:**
```java
double calibrationConstant = 0.5; // Hardcoded
```

**Enhanced:**
```java
private double calibrationConstant = 0.5;

public void setCalibrationConstant(double k) {
    if (k <= 0) throw new IllegalArgumentException("Calibration constant must be positive");
    this.calibrationConstant = k;
}
```

### 2. Industry-Standard Correlations

#### Mesh Pad K-Factor Correlation

From GPSA Engineering Data Book:

$$K = K_0 \cdot F_P \cdot F_\mu \cdot F_L$$

Where:
- $K_0$ = base K-factor (typically 0.107 m/s for wire mesh)
- $F_P$ = pressure correction factor
- $F_\mu$ = viscosity correction factor  
- $F_L$ = liquid load correction factor

#### Vane Pack Efficiency

Based on Stokes number:

$$\eta = 1 - \exp\left(-\frac{St}{St_{50}}\right)^n$$

Where:
- $St$ = Stokes number
- $St_{50}$ = Stokes number at 50% efficiency
- $n$ = shape factor

### 3. Droplet Size Distribution

Real inlet streams have a distribution of droplet sizes:

```java
public class DropletSizeDistribution {
    private double d50;        // Median diameter (μm)
    private double spread;     // Distribution spread parameter
    
    // Rosin-Rammler distribution
    public double cumulativeFraction(double diameter) {
        return 1.0 - Math.exp(-Math.pow(diameter / d50, spread));
    }
    
    // Grade efficiency integration
    public double calcOverallEfficiency(SeparationDevice device) {
        // Integrate device efficiency over droplet distribution
    }
}
```

### 4. Re-Entrainment Models

At high gas velocities, collected liquid can be re-entrained:

```java
public double calcReEntrainmentFraction(double gasVelocity, double liquidLoading) {
    double criticalVelocity = calcCriticalReEntrainmentVelocity();
    if (gasVelocity < criticalVelocity) {
        return 0.0;
    }
    // Re-entrainment increases above critical velocity
    double excessVelocity = (gasVelocity - criticalVelocity) / criticalVelocity;
    return Math.min(1.0, reEntrainmentFactor * excessVelocity * liquidLoading);
}
```

### 5. Operating Envelope Validation

Add warnings when operating outside valid correlation ranges:

```java
public double calcLiquidCarryOver() {
    double gasVelocity = separator.getInletGasVelocity();
    
    // Validate operating range
    if (gasVelocity > maxValidVelocity) {
        logger.warn("Operating above validated velocity range ({} > {} m/s)", 
            gasVelocity, maxValidVelocity);
    }
    
    // ... carry-over calculation
}
```

---

## References

1. GPSA Engineering Data Book, 14th Edition - Section 7: Separation Equipment
2. API 12J - Specification for Oil and Gas Separators
3. Souders, M. and Brown, G.G. (1934) - "Design of Fractionating Columns"
4. Koch-Glitsch Mist Elimination Design Manual
5. Sulzer Chemtech - Separation Technology Handbook

---

## See Also

- [Separators and Internals Overview](separators_and_internals.md)
- [Separator Internals Improvements](../SEPARATOR_INTERNALS_IMPROVEMENTS.md)
- [Gas Scrubber Test Examples](../../src/test/java/neqsim/process/equipment/separator/GasScrubberTest.java)
