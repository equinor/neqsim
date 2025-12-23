# Liquid Carry-Over Calculations in NeqSim Separators

This document provides technical details on liquid carry-over calculations for gas scrubbers and separators in NeqSim.

## Table of Contents

1. [Implementation Status](#implementation-status)
2. [Overview](#overview)
3. [Physical Background](#physical-background)
4. [Primary Separation Classes](#primary-separation-classes)
5. [Demisting Internal Classes](#demisting-internal-classes)
6. [References](#references)

---

## Implementation Status

| Task | Status | Notes |
|------|--------|-------|
| Define all classes needed for Pi number calculation | ✅ Done | Classes in `primaryseparation` and `internals` packages |
| Implement Pi number for primary separation carry-over calculations | 🔲 To be done | Will replace current placeholder correlations |
| Add cyclones as demisting internal with carry-over | 🔲 To be done | Extend `DemistingInternal` for cyclone demisters |
| Add risk scenario support | 🔲 To be done | Optimistic, conservative, nominal calculation modes |
| Add bottleneck handling | 🔲 To be done | Identify and report capacity constraints |

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

### Souders-Brown K-Value

The maximum allowable gas velocity in a separator is governed by the Souders-Brown equation:

$$v_{max} = K_{SB} \sqrt{\frac{\rho_L - \rho_G}{\rho_G}}$$

Where:
- $v_{max}$ = maximum gas velocity (m/s)
- $K_{SB}$ = Souders-Brown coefficient (m/s), typically 0.03-0.15
- $\rho_L$ = liquid density (kg/m³)
- $\rho_G$ = gas density (kg/m³)

The gas load factor $K$ relates actual velocity to maximum velocity:

$$K = v_{actual} \sqrt{\frac{\rho_G}{\rho_L - \rho_G}}$$

### Gas Momentum at Inlet Nozzle

The inlet nozzle momentum is a key parameter for primary separation design:

$$M = \rho_G \cdot v^2 \cdot A_{nozzle}$$

Where:
- $M$ = momentum flux (N or kg·m/s²)
- $\rho_G$ = gas density (kg/m³)
- $v$ = gas velocity through nozzle (m/s)
- $A_{nozzle}$ = nozzle cross-sectional area (m²)

High inlet momentum can cause:
- Liquid shattering into fine droplets
- Re-entrainment of separated liquid
- Damage to inlet devices

Typical design limits momentum to 1500-3000 Pa (ρv²) depending on internals type.

---

## Primary Separation Classes

Primary separation devices perform bulk liquid removal at the separator inlet.

### Current Classes

| Class | Key Parameters | Description |
|-------|---------------|-------------|
| `PrimarySeparation` | `inletNozzleDiameter` | Base class |
| `InletVane` | `geometricalExpansionRatio` | Vane-type inlet device |
| `InletVaneWithMeshpad` | `vaneToMeshpadDistance`, `freeDistanceAboveMeshpad` | Combined vane + meshpad |
| `InletCyclones` | `numberOfCyclones`, `cycloneDiameter` | Cyclone inlet device |

### InletVane

Current implementation (to be updated with Pi number correlation):

```java
// Expansion ratio efficiency: higher ratio means better separation
double expansionEfficiency = Math.min(geometricalExpansionRatio / 5.0, 1.0);

// Velocity effect: higher velocity increases carry-over
double velocityFactor = Math.min(1.0, inletVelocity / 10.0);

// Combined effect
double carryOverFactor = velocityFactor * (1.0 - 0.5 * expansionEfficiency);

return carryOverFactor * inletLiquidContent;
```

### InletVaneWithMeshpad

Current implementation (to be updated):

```java
// Combined carry-over: reduced by both meshpad coalescence and settling
double totalCarryOverFactor = vaneCarryOver 
    * (1.0 - 0.4 * meshpadCoalescenceEfficiency)
    * (1.0 - 0.3 * settlingEfficiency);

return totalCarryOverFactor * inletLiquidContent;
```

### InletCyclones

Current implementation (to be updated):

```java
// Centrifugal acceleration in cyclone
double centrifugalAcceleration = inletVelocity * inletVelocity / (cycloneDiameter / 2.0);

// Stokes number for separation
double stokesNumber = (liquidDensity - gasDensity) * Math.pow(dropletDiameter, 2) 
    * centrifugalAcceleration / (18.0 * liquidViscosity * inletVelocity);
```

---

## Demisting Internal Classes

Demisting internals provide final mist elimination before gas exits.

### Current Classes

| Class | Description |
|-------|-------------|
| `DemistingInternal` | Base demister with area and Euler number |
| `DemistingInternalWithDrainage` | Demister with drainage pipes |

### DemistingInternal

**Pressure Drop (Euler Number):**

$$\Delta p = Eu \cdot \rho_G \cdot v^2$$

```java
public double calcPressureDrop(double gasDensity, double gasVelocity) {
    return euNumber * gasDensity * gasVelocity * gasVelocity;
}
```

**Carry-Over Calculation** (to be updated):

```java
public double calcLiquidCarryOver() {
    double inletLiquidContent = separator.getInletLiquidContent();
    double calibrationConstant = 0.5;  // [PLACEHOLDER]
    double carryOverFactor = Math.exp(-calibrationConstant * area);
    return carryOverFactor * inletLiquidContent;
}
```

### DemistingInternalWithDrainage

Drainage pipes improve efficiency by preventing re-entrainment:

```java
@Override
public double calcLiquidCarryOver() {
    double baseCarryOver = super.calcLiquidCarryOver();
    return baseCarryOver * (1.0 - drainageEfficiency);
}
```

---

## References

1. GPSA Engineering Data Book, 14th Edition - Section 7: Separation Equipment
2. API 12J - Specification for Oil and Gas Separators
3. Souders, M. and Brown, G.G. (1934) - "Design of Fractionating Columns"

---

## See Also

- [Separators and Internals Overview](separators_and_internals.md)
- [Separator Internals Improvements](../SEPARATOR_INTERNALS_IMPROVEMENTS.md)
- [Gas Scrubber Test Examples](../../src/test/java/neqsim/process/equipment/separator/GasScrubberTest.java)
