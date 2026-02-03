---
title: "Pipeline Modeling Documentation"
description: "This documentation covers pipeline pressure drop, flow, and heat transfer calculations in NeqSim."
---

# Pipeline Modeling Documentation

## Documentation Index

This documentation covers pipeline pressure drop, flow, and heat transfer calculations in NeqSim.

### Overview & Getting Started

| Document | Description |
|----------|-------------|
| [Pipeline Pressure Drop](pipeline_pressure_drop) | Overview of all pipeline models, quick start examples |
| [Model Recommendations](pipeline_model_recommendations) | Which model to use for your application |

### Detailed Model Documentation

| Document | Description |
|----------|-------------|
| [Beggs & Brill Correlation](beggs_and_brill_correlation) | Multiphase flow correlation theory and usage |
| [Friction Factor Models](friction_factor_models) | Haaland, Colebrook-White, laminar/turbulent |
| [Heat Transfer](pipeline_heat_transfer) | Non-adiabatic operation, cooling, Gnielinski |
| [Transient Simulation](pipeline_transient_simulation) | Dynamic simulation, slow wave propagation |
| [Water Hammer](water_hammer_implementation) | Fast transients, pressure surges, MOC solver |

## Quick Model Selection

```
┌─────────────────────────────────────────────────────────────────┐
│                        PIPELINE MODELS                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Single-Phase Gas         →  AdiabaticPipe                     │
│  Single-Phase Liquid      →  PipeBeggsAndBrills                │
│  Two-Phase (Gas-Liquid)   →  PipeBeggsAndBrills                │
│  Three-Phase (G-O-W)      →  PipeBeggsAndBrills                │
│  With Elevation           →  PipeBeggsAndBrills                │
│  With Heat Transfer       →  PipeBeggsAndBrills                │
│  Slow Transient/Dynamic   →  PipeBeggsAndBrills                │
│  Water Hammer/Fast Trans. →  WaterHammerPipe                   │
│  Quick Estimate           →  AdiabaticTwoPhasePipe             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Key Classes

| Class | Package | Description |
|-------|---------|-------------|
| `PipeBeggsAndBrills` | `neqsim.process.equipment.pipeline` | Multiphase, elevation, heat transfer, slow transient |
| `WaterHammerPipe` | `neqsim.process.equipment.pipeline` | Water hammer, fast pressure transients (MOC) |
| `AdiabaticPipe` | `neqsim.process.equipment.pipeline` | Single-phase compressible gas |
| `AdiabaticTwoPhasePipe` | `neqsim.process.equipment.pipeline` | Two-phase, horizontal |
| `TwoFluidPipe` | `neqsim.process.equipment.pipeline` | Two-fluid model with drift-flux |
| `TransientPipe` | `neqsim.process.equipment.pipeline` | Transient drift-flux with AUSM+ scheme |
| `TwoPhasePipeFlowSystem` | `neqsim.fluidmechanics.flowsystem` | Low-level non-equilibrium mass/heat transfer |

### Low-Level Fluid Mechanics

For detailed non-equilibrium mass and heat transfer calculations, the `TwoPhasePipeFlowSystem` in the `fluidmechanics` package provides:

- **Multicomponent mass transfer** using Krishna-Standart film model
- **Flow pattern detection** (Taitel-Dukler, Baker, Barnea, Beggs-Brill)
- **Interfacial area calculations** for all flow patterns
- **Wall heat transfer** with multiple boundary conditions
- **Bidirectional mass transfer** (evaporation and dissolution)

See [Fluid Mechanics README](../fluidmechanics/) and [Two-Phase Pipe Flow Model](../fluidmechanics/TwoPhasePipeFlowModel) for details.

## Common Parameters

### Geometry
- `setLength(double meters)` - Pipe length
- `setDiameter(double meters)` - Inside diameter
- `setElevation(double meters)` - Elevation change (+ = uphill)
- `setPipeWallRoughness(double meters)` - Surface roughness

### Numerical
- `setNumberOfIncrements(int n)` - Number of calculation segments

### Calculation Mode
- `setOutletPressure(double bara)` - Specify outlet pressure, calculate flow rate
- Default mode: Specify flow rate, calculate outlet pressure

### Heat Transfer
- `setRunAdiabatic(boolean)` - Enable/disable heat exchange
- `setConstantSurfaceTemperature(double K)` - Ambient temperature
- `setHeatTransferCoefficient(double W_m2K)` - Overall U-value

### Transient
- `setCalculateSteadyState(boolean)` - Switch steady/transient mode
- `runTransient(double dt, UUID id)` - Run one time step

## Typical Roughness Values

| Pipe Material | Roughness (mm) | Roughness (m) |
|--------------|----------------|---------------|
| New steel | 0.046 | 4.6×10⁻⁵ |
| Corroded steel | 0.15-0.3 | 1.5-3×10⁻⁴ |
| Stainless | 0.015 | 1.5×10⁻⁵ |
| Plastic/GRP | 0.005 | 5×10⁻⁶ |

## Validation Summary

| Test Case | Model | Deviation |
|-----------|-------|-----------|
| Gas (Darcy-Weisbach) | All models | <1% |
| Liquid turbulent | Beggs-Brill | -1.4% |
| Liquid laminar | Beggs-Brill | 0% |
| Uphill two-phase | Beggs-Brill | Validated |
| Transient convergence | Beggs-Brill | <15% |

## Version History

- **December 2025**: Fixed Java 8 compatibility in TwoPhasePipeFlowSystem tests
- **December 2025**: Added bidirectional mass transfer mode
- **December 2025**: Improved solver stability for edge cases
- **2025**: Added calculate flow from outlet pressure mode
- **2025**: Fixed AdiabaticPipe and AdiabaticTwoPhasePipe calcFlow() methods
- **2024**: Added transient with friction and hydrostatic
- **2024**: Fixed Haaland exponent (^1.11)
- **2024**: Fixed single-phase liquid handling
- **2024**: Added Gnielinski heat transfer
- **2024**: Added flow regime detection

## Support

For questions or issues:
- GitHub Issues: https://github.com/equinor/neqsim/issues
- Documentation: https://equinor.github.io/neqsim/
