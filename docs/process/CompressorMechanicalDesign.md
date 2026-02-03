---
title: Compressor Mechanical Design
description: This document describes the mechanical design calculations for centrifugal compressors in NeqSim, implemented in the `CompressorMechanicalDesign` class.
---

# Compressor Mechanical Design

This document describes the mechanical design calculations for centrifugal compressors in NeqSim, implemented in the `CompressorMechanicalDesign` class.

## Overview

The mechanical design module provides sizing and design calculations for centrifugal compressors based on **API 617** (Axial and Centrifugal Compressors) and industry practice. The calculations enable:

- Preliminary equipment sizing for cost estimation
- Module footprint planning
- Driver selection
- Verification of operating point against mechanical limits

## Design Standards Reference

| Standard | Description |
|----------|-------------|
| API 617 | Axial and Centrifugal Compressors and Expander-compressors |
| API 672 | Packaged, Integrally Geared Centrifugal Air Compressors |
| API 692 | Dry Gas Sealing Systems |
| API 614 | Lubrication, Shaft-Sealing and Oil-Control Systems |

## Design Calculations

### 1. Number of Stages

The number of compression stages is determined by the total polytropic head and the maximum allowable head per stage:

```
numberOfStages = ceil(totalPolytropicHead / maxHeadPerStage)
```

**Design Limit:** Maximum head per stage = **30 kJ/kg** (typical for process gas centrifugal compressors)

The actual head per stage is then:
```
headPerStage = totalPolytropicHead / numberOfStages
```

### 2. Impeller Sizing

#### Tip Speed Calculation

The impeller tip speed is derived from the head requirement using the work coefficient:

```
tipSpeed = sqrt(headPerStage [J/kg] / workCoefficient)
```

Where:
- `workCoefficient` = 0.50 (typical for backward-curved impellers, range 0.4-0.6)

**Design Limit:** Maximum tip speed = **350 m/s** (material limit for steel impellers)

#### Impeller Diameter

From the tip speed and rotational speed:

```
impellerDiameter [mm] = (tipSpeed × 60) / (π × speedRPM) × 1000
```

The design verifies the flow coefficient is within acceptable range (0.01-0.15):

```
flowCoefficient = volumeFlow [m³/s] / (D² × U)
```

### 3. Shaft Diameter

Shaft diameter is calculated from torque requirements and allowable shear stress:

```
torque [Nm] = power [kW] × 1000 × 60 / (2π × speedRPM)
shaftDiameter [mm] = ((16 × torque) / (π × allowableShear))^(1/3) × 1000 × safetyFactor
```

Where:
- `allowableShear` = 50 MPa (typical for alloy steel shafts)
- `safetyFactor` = 1.5

### 4. Driver Sizing

Driver power includes margins per API 617:

| Shaft Power | Driver Margin |
|-------------|---------------|
| < 150 kW | 25% |
| 150-750 kW | 15% |
| > 750 kW | 10% |

```
driverPower = (shaftPower + mechanicalLosses) × driverMargin
```

### 5. Casing Design

#### Design Pressure and Temperature

```
designPressure = dischargePressure × 1.10  (10% margin)
designTemperature = dischargeTemperature + 30°C
```

#### Casing Type Selection

| Design Pressure | Casing Type |
|-----------------|-------------|
| > 100 bara | Barrel |
| 40-100 bara | Horizontally Split |
| < 40 bara | Vertically Split |

### 6. Rotor Dynamics

#### Critical Speeds

```
maxContinuousSpeed = operatingSpeed × 1.05
tripSpeed = maxContinuousSpeed × 1.05
```

The first lateral critical speed is estimated using simplified Rayleigh-Ritz formulation based on shaft geometry.

**API 617 Requirement:** Separation margin from critical speed ≥ 15%

#### Bearing Span

```
bearingSpan = numberOfStages × (impellerDiameter × 0.8) + impellerDiameter
```

### 7. Weight Estimation

#### Rotor Weight
```
impellerWeight = numberOfStages × 0.5 × (impellerDiameter/100)^2.5
shaftWeight = bearingSpan/1000 × 7850 × π × (shaftDiameter/2000)²
rotorWeight = impellerWeight + shaftWeight
```

#### Casing Weight
```
casingThickness = max(10mm, designPressure × impellerDiameter / (2 × 150))
casingWeight = π × casingOD × casingLength × casingThickness × 7850 × 1.2
```

For barrel-type casing, add 30% additional weight.

#### Total Skid Weight

| Component | Estimation Method |
|-----------|-------------------|
| Casing | As calculated above |
| Bundle (rotor + internals) | rotorWeight + stage internals |
| Seal system | 100 × (shaftDiameter/100) kg |
| Lube oil system | 200 + driverPower × 0.1 kg |
| Baseplate | casingWeight × 0.3 |
| Piping | emptyVesselWeight × 0.2 |
| Electrical | driverPower × 0.5 kg |
| Structural steel | emptyVesselWeight × 0.15 |

### 8. Module Dimensions

```
moduleLength = compressorLength + driverLength + couplingSpace + auxiliarySpace
moduleWidth = casingOD + 3.0m (access each side)
moduleHeight = casingOD + 2.0m (piping and lifting)
```

Minimum dimensions: 4m × 3m × 3m

## Integration with CompressorMechanicalLosses

The mechanical design integrates with `CompressorMechanicalLosses` for:

- **Seal gas consumption** - Primary/secondary leakage, buffer gas, separation gas
- **Bearing losses** - Radial and thrust bearing power dissipation
- **Lube oil system sizing** - Based on heat removal requirements

When `setDesign()` is called, the mechanical losses model is automatically initialized with the calculated shaft diameter.

## Usage Example

```java
// Create and run compressor
SystemInterface gas = new SystemSrkEos(300.0, 10.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule(2);

Stream inlet = new Stream("inlet", gas);
inlet.setFlowRate(10000.0, "kg/hr");

Compressor comp = new Compressor("export compressor", inlet);
comp.setOutletPressure(40.0);
comp.setPolytropicEfficiency(0.76);
comp.setSpeed(8000);

ProcessSystem ps = new ProcessSystem();
ps.add(inlet);
ps.add(comp);
ps.run();

// Calculate mechanical design
comp.getMechanicalDesign().calcDesign();

// Access design results
int stages = comp.getMechanicalDesign().getNumberOfStages();
double impellerD = comp.getMechanicalDesign().getImpellerDiameter(); // mm
double driverPower = comp.getMechanicalDesign().getDriverPower(); // kW
double totalWeight = comp.getMechanicalDesign().getWeightTotal(); // kg

// Apply design (initializes mechanical losses)
comp.getMechanicalDesign().setDesign();

// Get seal gas consumption
double sealGas = comp.getSealGasConsumption(); // Nm³/hr
```

## Design Output Parameters

| Parameter | Method | Unit |
|-----------|--------|------|
| Number of stages | `getNumberOfStages()` | - |
| Head per stage | `getHeadPerStage()` | kJ/kg |
| Impeller diameter | `getImpellerDiameter()` | mm |
| Tip speed | `getTipSpeed()` | m/s |
| Shaft diameter | `getShaftDiameter()` | mm |
| Bearing span | `getBearingSpan()` | mm |
| Design pressure | `getDesignPressure()` | bara |
| Design temperature | `getDesignTemperature()` | °C |
| Casing type | `getCasingType()` | enum |
| Driver power | `getDriverPower()` | kW |
| Max continuous speed | `getMaxContinuousSpeed()` | rpm |
| Trip speed | `getTripSpeed()` | rpm |
| First critical speed | `getFirstCriticalSpeed()` | rpm |
| Casing weight | `getCasingWeight()` | kg |
| Bundle weight | `getBundleWeight()` | kg |
| Total skid weight | `getWeightTotal()` | kg |
| Module dimensions | `getModuleLength/Width/Height()` | m |

## Limitations and Assumptions

1. **Single-shaft configuration** - Does not handle integrally geared or multi-body compressors
2. **Empirical correlations** - Weight and dimension estimates are approximate; vendor data should be used for detailed design
3. **Steel impellers** - Tip speed limit assumes conventional steel; titanium or composites allow higher speeds
4. **Backward-curved impellers** - Work coefficient assumes standard backward-curved blade geometry
5. **No intercooling** - Multi-stage calculations assume adiabatic compression; intercooled designs require separate handling

## Related Classes

- `Compressor` - Main compressor process equipment class
- `CompressorMechanicalLosses` - Seal gas and bearing loss calculations
- `CompressorChart` - Performance curve handling
- `CompressorCostEstimate` - Cost estimation based on mechanical design

## References

1. API 617, 8th Edition - Axial and Centrifugal Compressors
2. Bloch, H.P. - "A Practical Guide to Compressor Technology"
3. Japikse, D. - "Centrifugal Compressor Design and Performance"
4. Lüdtke, K.H. - "Process Centrifugal Compressors"
