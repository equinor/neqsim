# Pipeline Modeling Documentation

## Documentation Index

This documentation covers pipeline pressure drop, flow, and heat transfer calculations in NeqSim.

### Overview & Getting Started

| Document | Description |
|----------|-------------|
| [Pipeline Pressure Drop](pipeline_pressure_drop.md) | Overview of all pipeline models, quick start examples |
| [Model Recommendations](pipeline_model_recommendations.md) | Which model to use for your application |

### Detailed Model Documentation

| Document | Description |
|----------|-------------|
| [Beggs & Brill Correlation](beggs_and_brill_correlation.md) | Multiphase flow correlation theory and usage |
| [Friction Factor Models](friction_factor_models.md) | Haaland, Colebrook-White, laminar/turbulent |
| [Heat Transfer](pipeline_heat_transfer.md) | Non-adiabatic operation, cooling, Gnielinski |
| [Transient Simulation](pipeline_transient_simulation.md) | Dynamic simulation, wave propagation |

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
│  Transient/Dynamic        →  PipeBeggsAndBrills                │
│  Quick Estimate           →  AdiabaticTwoPhasePipe             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Key Classes

| Class | Package | Description |
|-------|---------|-------------|
| `PipeBeggsAndBrills` | `neqsim.process.equipment.pipeline` | Multiphase, elevation, heat transfer, transient |
| `AdiabaticPipe` | `neqsim.process.equipment.pipeline` | Single-phase compressible gas |
| `AdiabaticTwoPhasePipe` | `neqsim.process.equipment.pipeline` | Two-phase, horizontal |

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
