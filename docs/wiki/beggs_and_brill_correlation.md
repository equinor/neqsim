---
title: "Beggs & Brill Correlation for Multiphase Pipe Flow"
description: "The Beggs & Brill correlation (1973) is a widely-used empirical method for predicting pressure drop and liquid holdup in multiphase pipe flow. It handles:"
---

# Beggs & Brill Correlation for Multiphase Pipe Flow

## Overview

The Beggs & Brill correlation (1973) is a widely-used empirical method for predicting pressure drop and liquid holdup in multiphase pipe flow. It handles:

- Horizontal, inclined, and vertical pipes
- All flow regimes (segregated, intermittent, distributed)
- Two-phase and three-phase flow (gas-oil-water)

## Theory

### Total Pressure Gradient

The total pressure gradient consists of three components:

$$\frac{dP}{dL} = \frac{dP}{dL}_{friction} + \frac{dP}{dL}_{hydrostatic} + \frac{dP}{dL}_{acceleration}$$

In NeqSim, the acceleration term is typically neglected (small for steady flow), so:

$$\Delta P = \Delta P_{friction} + \Delta P_{hydrostatic}$$

### Flow Regime Determination

The correlation identifies four flow regimes based on dimensionless parameters:

| Flow Regime | Description | Typical Conditions |
|-------------|-------------|-------------------|
| **Segregated** | Stratified or wavy flow | Low velocities, horizontal |
| **Intermittent** | Slug or plug flow | Moderate velocities |
| **Distributed** | Bubble or mist flow | High velocities |
| **Transition** | Between segregated and intermittent | Transitional |

Flow regime is determined by:
- Froude number: $Fr = v_m^2 / (g \cdot D)$
- Input liquid volume fraction: $\lambda_L = v_{SL} / v_m$

Where:
- $v_m = v_{SG} + v_{SL}$ (mixture superficial velocity)
- $v_{SG}$ = gas superficial velocity
- $v_{SL}$ = liquid superficial velocity

### Liquid Holdup Calculation

Liquid holdup ($H_L$ or $E_L$) is the fraction of pipe cross-section occupied by liquid:

$$H_L = H_L(0) \cdot \psi$$

Where:
- $H_L(0)$ = horizontal holdup from regime-specific correlation
- $\psi$ = inclination correction factor

**Horizontal holdup correlations:**

| Regime | Correlation |
|--------|-------------|
| Segregated | $H_L(0) = \frac{0.98 \lambda_L^{0.4846}}{Fr^{0.0868}}$ |
| Intermittent | $H_L(0) = \frac{0.845 \lambda_L^{0.5351}}{Fr^{0.0173}}$ |
| Distributed | $H_L(0) = \frac{1.065 \lambda_L^{0.5824}}{Fr^{0.0609}}$ |

### Friction Pressure Loss

$$\Delta P_{friction} = \frac{f_{tp} \cdot \rho_{ns} \cdot v_m^2 \cdot L}{2D}$$

Where:
- $f_{tp} = f_{ns} \cdot e^S$ (two-phase friction factor)
- $f_{ns}$ = no-slip friction factor (from Haaland equation)
- $S$ = slip correction factor
- $\rho_{ns}$ = no-slip mixture density

### Hydrostatic Pressure Drop

$$\Delta P_{hydrostatic} = \rho_m \cdot g \cdot \Delta h$$

Where:
- $\rho_m = \rho_L \cdot H_L + \rho_G \cdot (1 - H_L)$ (mixture density with holdup)
- $\Delta h$ = elevation change

## Usage in NeqSim

### Basic Configuration

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("flowline", inletStream);

// Geometry
pipe.setLength(1000);              // meters
pipe.setDiameter(0.1);             // meters
pipe.setElevation(100);            // meters (positive = uphill)
pipe.setAngle(5.7);                // degrees (alternative to elevation)
pipe.setPipeWallRoughness(4.6e-5); // meters (steel ≈ 0.046 mm)

// Numerical settings
pipe.setNumberOfIncrements(20);    // segments for integration

pipe.run();
```

### Accessing Results

```java
// Overall results
double pressureDrop = pipe.getInletPressure() - pipe.getOutletPressure();
double outletTemp = pipe.getOutletTemperature();

// Flow regime
PipeBeggsAndBrills.FlowRegime regime = pipe.getFlowRegime();
// Returns: SEGREGATED, INTERMITTENT, DISTRIBUTED, TRANSITION, or SINGLE_PHASE

// Profile data (for segment i)
double holdup = pipe.getSegmentLiquidHoldup(i);
double mixtureDensity = pipe.getSegmentMixtureDensity(i);
double velocity = pipe.getSegmentMixtureSuperficialVelocity(i);

// Full profiles
List<Double> pressureProfile = pipe.getPressureProfile();
List<Double> temperatureProfile = pipe.getTemperatureProfile();
```

### Heat Transfer Options

```java
// Adiabatic (default)
pipe.setRunAdiabatic(true);

// With heat transfer
pipe.setRunAdiabatic(false);
pipe.setConstantSurfaceTemperature(283.15);  // 10°C ambient
pipe.setHeatTransferCoefficient(10.0);       // W/m²K
// Or let it estimate:
pipe.setHeatTransferCoefficientMethod("Estimated");
```

## Three-Phase Flow (Gas-Oil-Water)

For three-phase systems, the liquid phase properties are calculated as volume-weighted averages:

```java
SystemInterface fluid = new SystemSrkEos(333.15, 30.0);
fluid.addComponent("methane", 3000, "kg/hr");
fluid.addComponent("nC10", 40000, "kg/hr");
fluid.addComponent("water", 20000, "kg/hr");
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);  // Enable water phase

Stream feed = new Stream("feed", fluid);
feed.run();

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", feed);
// ... configure and run
```

## Limitations

1. **Developed for oil & gas**: Correlations based on oil/gas/water systems
2. **Pipe diameter range**: Validated for 1-12 inch pipes
3. **Pressure range**: Best for moderate pressures (1-100 bara)
4. **Inclination**: Valid for -90° to +90° (horizontal to vertical)
5. **Viscosity**: May underpredict for very high viscosity fluids
6. **Flow patterns**: Simplified flow regime map; real systems may differ

## Validation

NeqSim's implementation has been validated against:

| Test Case | Reference | Deviation |
|-----------|-----------|-----------|
| Single-phase gas (turbulent) | Darcy-Weisbach | +0.5% |
| Single-phase liquid (turbulent) | Darcy-Weisbach | -1.4% |
| Single-phase liquid (laminar) | Darcy-Weisbach | 0.0% |
| Two-phase horizontal | Dukler, Homogeneous | Reasonable |
| Inclined pipes | Steady-state physics | Validated |

## References

1. Beggs, H.D. and Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes". *Journal of Petroleum Technology*, 25(5), 607-617.

2. Brill, J.P. and Mukherjee, H. (1999). *Multiphase Flow in Wells*. SPE Monograph Series.

3. Shoham, O. (2006). *Mechanistic Modeling of Gas-Liquid Two-Phase Flow in Pipes*. SPE Books.

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop.md)
- [Friction Factor Models](friction_factor_models.md)
- [Pipeline Transient Simulation](pipeline_transient_simulation.md)
