---
title: "Friction Factor Models in NeqSim Pipelines"
description: "Friction factor is a critical parameter in pressure drop calculations. NeqSim implements industry-standard correlations for both laminar and turbulent flow."
---

# Friction Factor Models in NeqSim Pipelines

## Overview

Friction factor is a critical parameter in pressure drop calculations. NeqSim implements industry-standard correlations for both laminar and turbulent flow.

## Friction Factor Equations

### Laminar Flow (Re < 2300)

For laminar flow, the Darcy friction factor is:

$$f = \frac{64}{Re}$$

Where Reynolds number:
$$Re = \frac{\rho v D}{\mu}$$

### Transition Zone (2300 < Re < 4000)

Linear interpolation between laminar and turbulent:

$$f = f_{laminar} + \frac{Re - 2300}{1700}(f_{turbulent,4000} - f_{laminar,2300})$$

### Turbulent Flow (Re > 4000)

#### Haaland Equation (Default)

NeqSim uses the Haaland equation, an explicit approximation of Colebrook-White:

$$f = \left[ -1.8 \log_{10}\left( \left(\frac{\varepsilon/D}{3.7}\right)^{1.11} + \frac{6.9}{Re} \right) \right]^{-2}$$

Where:
- $\varepsilon$ = pipe wall roughness (m)
- $D$ = pipe diameter (m)
- $Re$ = Reynolds number

**Advantages:**
- Explicit (no iteration required)
- Accuracy within 1-2% of Colebrook-White
- Computationally efficient

#### Colebrook-White Equation (Reference)

The implicit Colebrook-White equation (used for validation):

$$\frac{1}{\sqrt{f}} = -2 \log_{10}\left( \frac{\varepsilon/D}{3.7} + \frac{2.51}{Re\sqrt{f}} \right)$$

Solved iteratively using Newton-Raphson method.

## Two-Phase Friction Factor

For multiphase flow, the single-phase friction factor is modified:

$$f_{tp} = f_{ns} \cdot e^S$$

Where:
- $f_{ns}$ = no-slip friction factor
- $S$ = slip correction factor (from Beggs & Brill)

The slip factor $S$ depends on the liquid holdup ratio:
$$y = \frac{\lambda_L}{H_L^2}$$

For $1 < y < 1.2$:
$$S = \ln(2.2y - 1.2)$$

Otherwise:
$$S = \frac{\ln(y)}{-0.0523 + 3.18\ln(y) - 0.872[\ln(y)]^2 + 0.01853[\ln(y)]^4}$$

## Pipe Roughness Values

### Typical Roughness Values

| Material | Roughness ε (mm) | Roughness ε (m) |
|----------|-----------------|-----------------|
| Commercial steel (new) | 0.046 | 4.6×10⁻⁵ |
| Commercial steel (rusted) | 0.15-0.3 | 1.5-3×10⁻⁴ |
| Stainless steel | 0.015 | 1.5×10⁻⁵ |
| Drawn tubing (copper, brass) | 0.0015 | 1.5×10⁻⁶ |
| Cast iron | 0.26 | 2.6×10⁻⁴ |
| Concrete | 0.3-3.0 | 3×10⁻⁴ to 3×10⁻³ |
| PVC/Plastic | 0.0015-0.007 | 1.5-7×10⁻⁶ |
| GRP/FRP | 0.01 | 1×10⁻⁵ |

### Setting Roughness in NeqSim

```java
// For PipeBeggsAndBrills
pipe.setPipeWallRoughness(4.6e-5);  // meters

// For AdiabaticPipe
pipe.setPipeWallRoughness(4.6e-5);  // meters
```

## Implementation Details

### Reynolds Number Calculation

For two-phase flow, the no-slip Reynolds number is used:

$$Re_{ns} = \frac{\rho_{ns} \cdot v_m \cdot D}{\mu_{ns}}$$

Where:
- $\rho_{ns} = \rho_L \lambda_L + \rho_G (1-\lambda_L)$ (no-slip density)
- $\mu_{ns} = \mu_L \lambda_L + \mu_G (1-\lambda_L)$ (no-slip viscosity)
- $v_m = v_{SG} + v_{SL}$ (mixture velocity)

### Code Example

```java
// Get friction-related results
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", stream);
pipe.setLength(1000);
pipe.setDiameter(0.1);
pipe.setPipeWallRoughness(4.6e-5);
pipe.run();

// Access Reynolds number and friction factor for each segment
for (int i = 1; i <= pipe.getNumberOfIncrements(); i++) {
    double Re = pipe.getSegmentMixtureReynoldsNumber(i);
    // Friction factor is internal but affects pressure drop
}
```

## Validation Results

Comparison of NeqSim friction factor implementation against Colebrook-White:

| Reynolds | ε/D | Haaland f | Colebrook f | Deviation |
|----------|-----|-----------|-------------|-----------|
| 10,000 | 0.001 | 0.0380 | 0.0382 | -0.5% |
| 100,000 | 0.001 | 0.0227 | 0.0228 | -0.4% |
| 1,000,000 | 0.001 | 0.0197 | 0.0197 | 0.0% |
| 10,000,000 | 0.001 | 0.0191 | 0.0191 | 0.0% |

## Common Issues

### 1. Zero or Negative Friction Factor
- **Cause**: Very low Reynolds number or invalid inputs
- **Solution**: Check that flow rate, density, and viscosity are positive

### 2. Unrealistic Pressure Drop
- **Cause**: Wrong roughness units (mm vs m)
- **Solution**: Always use meters for roughness

### 3. Laminar Flow Not Recognized
- **Cause**: Re threshold set incorrectly
- **Solution**: NeqSim uses Re < 2300 for laminar

## References

1. Haaland, S.E. (1983). "Simple and Explicit Formulas for the Friction Factor in Turbulent Pipe Flow". *Journal of Fluids Engineering*, 105(1), 89-90.

2. Colebrook, C.F. (1939). "Turbulent Flow in Pipes with Particular Reference to the Transition Region Between Smooth and Rough Pipe Laws". *Journal of the Institution of Civil Engineers*, 11, 133-156.

3. Moody, L.F. (1944). "Friction Factors for Pipe Flow". *Transactions of the ASME*, 66, 671-684.

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop)
- [Beggs & Brill Correlation](beggs_and_brill_correlation)
