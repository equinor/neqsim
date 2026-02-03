---
title: Manifold Mechanical Design Guide
description: This guide documents the manifold mechanical design capabilities in NeqSim, covering topside, onshore, and subsea applications.
---

# Manifold Mechanical Design Guide

This guide documents the manifold mechanical design capabilities in NeqSim, covering topside, onshore, and subsea applications.

## Overview

Manifolds are critical process equipment used for stream distribution and collection. In NeqSim, manifolds are modeled as a combination of a mixer and splitter, with comprehensive mechanical design capabilities added for:

- **Topside manifolds** - Offshore platform applications
- **Onshore manifolds** - Process facility applications  
- **Subsea manifolds** - Seabed production and injection systems

## Design Standards

The manifold mechanical design implementation follows these industry standards:

| Standard | Application | Scope |
|----------|-------------|-------|
| **ASME B31.3** | Topside/Onshore | Wall thickness, stress analysis, reinforcement |
| **DNV-ST-F101** | Subsea | Pressure containment, collapse, safety factors |
| **API RP 14E** | All | Erosional velocity limits |
| **NORSOK L-002** | Topside/Onshore | Support spacing requirements |
| **API RP 17A** | Subsea | Subsea production system design |
| **DNV-RP-F112** | Subsea | Duplex stainless steel design |
| **ASME B16.5** | All | Flange pressure-temperature ratings |

## Quick Start Example

### Basic Topside Manifold

```java
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesign;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");
fluid.init(0);

// Create feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

// Create manifold with 2-way split
Manifold manifold = new Manifold("Production Manifold");
manifold.addStream(feed);
manifold.setSplitFactors(new double[] {0.5, 0.5});
manifold.run();

// Initialize mechanical design
manifold.initMechanicalDesign();
ManifoldMechanicalDesign design = manifold.getMechanicalDesign();

// Configure design parameters
design.setMaxOperationPressure(50.0);  // bar
design.setMaxOperationTemperature(298.0);  // K
design.setLocation(ManifoldLocation.TOPSIDE);
design.setMaterialGrade("A106-B");
design.setHeaderDiameter(0.2032);  // 8 inch
design.setBranchDiameter(0.1016);  // 4 inch
design.setNumberOfInlets(1);
design.setNumberOfOutlets(2);

// Run design calculations
design.calcDesign();

// Get JSON report
String report = design.toJson();
System.out.println(report);
```

### Subsea Manifold Design

```java
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldType;

// Create subsea manifold
Manifold subseaManifold = new Manifold("Subsea Production Manifold");
subseaManifold.addStream(wellStream1);
subseaManifold.addStream(wellStream2);
subseaManifold.addStream(wellStream3);
subseaManifold.addStream(wellStream4);
subseaManifold.setSplitFactors(new double[] {1.0});  // Single output to flowline
subseaManifold.run();

// Configure for subsea design
subseaManifold.initMechanicalDesign();
ManifoldMechanicalDesign design = subseaManifold.getMechanicalDesign();

design.setMaxOperationPressure(150.0);  // bar
design.setMaxOperationTemperature(280.0);  // K (7°C)
design.setLocation(ManifoldLocation.SUBSEA);
design.setDesignStandardCode("DNV-ST-F101");
design.setMaterialGrade("X65");  // Subsea line pipe grade
design.setWaterDepth(350.0);  // meters
design.setHeaderDiameter(0.4064);  // 16 inch
design.setBranchDiameter(0.1524);  // 6 inch
design.setNumberOfInlets(4);  // 4 well connections
design.setNumberOfOutlets(1);  // 1 flowline outlet

design.calcDesign();

// Access detailed calculations
ManifoldMechanicalDesignCalculator calc = design.getCalculator();
System.out.println("Required wall thickness: " + calc.getMinHeaderWallThickness() * 1000 + " mm");
System.out.println("Submerged weight: " + calc.getSubmergedWeight() + " kg");
```

## Manifold Locations

The `ManifoldLocation` enum defines the installation context:

| Location | Description | Design Code |
|----------|-------------|-------------|
| `TOPSIDE` | Offshore platform manifold | ASME B31.3 |
| `ONSHORE` | Land-based facility manifold | ASME B31.3 |
| `SUBSEA` | Seabed manifold | DNV-ST-F101 |

## Manifold Types

The `ManifoldType` enum categorizes manifolds by function:

| Type | Description |
|------|-------------|
| `PRODUCTION` | Gathering produced fluids from wells |
| `INJECTION` | Distributing injection fluids (water, gas) |
| `TEST` | Test separator feed manifold |
| `PIGGING` | Pig launcher/receiver manifold |
| `DISTRIBUTION` | General distribution manifold |

## Design Calculations

### Wall Thickness

#### ASME B31.3 (Topside/Onshore)

Wall thickness is calculated per ASME B31.3 Equation 3a:

```
t_m = P × D / (2 × (S × E + P × Y))
t_min = t_m / (1 - tolerance) + corrosion_allowance
```

Where:
- P = Design pressure (MPa)
- D = Outside diameter (m)
- S = Allowable stress (MPa) from ASME B31.3 Table A-1
- E = Joint efficiency factor
- Y = Coefficient (0.4 for ferritic steel < 482°C)

#### DNV-ST-F101 (Subsea)

Wall thickness for subsea includes safety class factors:

```
t_1 = P × D / (2 × f_y × α_U / (γ_M × γ_SC))
t_min = t_1 / (1 - tolerance) + corrosion_allowance
t_min = max(t_min, 6.35mm)  // Minimum handling thickness
```

Where:
- γ_M = Material resistance factor (1.15)
- γ_SC = Safety class factor (1.046 low, 1.138 medium, 1.308 high)
- f_y = SMYS (Specified Minimum Yield Strength)

### Velocity Limits

Erosional velocity is calculated per API RP 14E:

```
V_e = C / √ρ_m
```

Where:
- C = Erosional constant (100-150)
- ρ_m = Mixture density (kg/m³)

Recommended velocity limits:

| Service | Maximum Velocity |
|---------|------------------|
| Gas | 20-30 m/s |
| Liquid | 3-5 m/s |
| Multiphase | 15-20 m/s |
| Erosional limit | 0.8 × V_e |

### Branch Reinforcement

Branch connections are analyzed per ASME B31.3 area replacement method:

1. Calculate area removed by branch opening
2. Calculate excess area in header wall
3. Calculate excess area in branch wall
4. If total excess < area removed, reinforcement pad required

```java
// Check reinforcement requirement
design.calcDesign();
ManifoldMechanicalDesignCalculator calc = design.getCalculator();

if (calc.isReinforcementRequired()) {
    double padThickness = calc.getReinforcementPadThickness();
    System.out.println("Reinforcement pad required: " + padThickness * 1000 + " mm");
}
```

### Support Spacing

Support spacing follows NORSOK L-002 guidelines:

| Pipe Size | Support Spacing |
|-----------|-----------------|
| ≤ NPS 4 | 2.7 m |
| NPS 4-8 | 3.7 m |
| NPS 8-12 | 4.3 m |
| > NPS 12 | 5.0 m |

For subsea manifolds, the structure itself provides support.

## Material Properties

### Carbon Steel (Topside/Onshore)

| Grade | Allowable Stress at 20°C (MPa) |
|-------|-------------------------------|
| A106-B | 138 |
| A312-TP316 | 138 |
| A312-TP316L | 115 |
| A790-S31803 (Duplex) | 207 |
| A790-S32750 (Super Duplex) | 241 |

### Subsea Line Pipe Grades

| Grade | SMYS (MPa) | SMTS (MPa) |
|-------|------------|------------|
| X52 | 359 | 455 |
| X60 | 414 | 517 |
| X65 | 448 | 531 |
| X70 | 483 | 565 |
| 22Cr Duplex | 450 | 620 |
| 25Cr Super Duplex | 550 | 750 |
| 6Mo | 300 | 650 |
| Inconel 625 | 414 | 827 |

## Weight Calculations

### Dry Weight

Dry weight includes:
- Header pipe weight
- Branch pipe weights
- Valve weights (estimated from size)
- Structure weight (10% topside, 20% subsea)
- Reinforcement pads (if required)

```java
double dryWeight = calc.calculateDryWeight();
System.out.println("Total dry weight: " + dryWeight + " kg");
```

### Submerged Weight (Subsea)

For subsea manifolds, submerged weight accounts for buoyancy:

```java
if (design.getLocation() == ManifoldLocation.SUBSEA) {
    double submergedWeight = calc.calculateSubmergedWeight();
    System.out.println("Submerged weight: " + submergedWeight + " kg");
}
```

## Design Verification

The design verification checks:

1. **Wall thickness** - Actual ≥ required
2. **Velocity limits** - Header and branch velocities within limits
3. **Reinforcement** - Branch connections properly reinforced
4. **Support** - Adequate support spacing

```java
// Perform complete design verification
design.calcDesign();
ManifoldMechanicalDesignCalculator calc = design.getCalculator();

boolean allPassed = calc.performDesignVerification();

System.out.println("Wall thickness check: " + 
    (calc.isWallThicknessCheckPassed() ? "PASSED" : "FAILED"));
System.out.println("Velocity check: " + 
    (calc.isVelocityCheckPassed() ? "PASSED" : "FAILED"));
System.out.println("Reinforcement check: " + 
    (calc.isReinforcementCheckPassed() ? "PASSED" : "FAILED"));
```

## JSON Output

The design produces comprehensive JSON output:

```json
{
  "designStandardCode": "ASME-B31.3",
  "materialGrade": "A106-B",
  "manifoldLocation": "TOPSIDE",
  "manifoldType": "PRODUCTION",
  "numberOfInlets": 1,
  "numberOfOutlets": 2,
  "headerDiameter_m": 0.2032,
  "branchDiameter_m": 0.1016,
  "designCalculations": {
    "configuration": {
      "location": "TOPSIDE",
      "manifoldType": "PRODUCTION",
      "numberOfInlets": 1,
      "numberOfOutlets": 2,
      "numberOfValves": 4
    },
    "geometry": {
      "headerOuterDiameter_m": 0.2032,
      "headerWallThickness_m": 0.0087,
      "branchOuterDiameter_m": 0.1016,
      "branchWallThickness_m": 0.00602
    },
    "wallThicknessAnalysis": {
      "minHeaderWallThickness_m": 0.0075,
      "wallThicknessCheckPassed": true
    },
    "velocityAnalysis": {
      "headerVelocity_m_s": 8.5,
      "branchVelocity_m_s": 12.3,
      "erosionalVelocity_m_s": 14.14,
      "velocityCheckPassed": true
    },
    "reinforcementAnalysis": {
      "reinforcementRequired": false,
      "reinforcementCheckPassed": true
    },
    "weightAnalysis": {
      "totalDryWeight_kg": 850.5
    },
    "appliedStandards": [
      "ASME B31.3 - Wall Thickness",
      "API RP 14E - Erosional Velocity",
      "ASME B31.3 - Branch Reinforcement",
      "NORSOK L-002 - Support Spacing"
    ]
  }
}
```

## Database Integration

Design parameters are loaded from database tables:

### TechnicalRequirements_Process

Company-specific parameters for manifolds:

| Parameter | Default | Equinor | Unit |
|-----------|---------|---------|------|
| designFactor | 0.72 | 0.67-0.72 | - |
| jointEfficiency | 0.85-1.0 | - | - |
| corrosionAllowance | 1.5-3.0 | 3.0 | mm |
| erosionalCFactor | 100-150 | 100-125 | - |
| safetyClassFactor | 1.046-1.138 | - | - |

### asme_standards

ASME B31.3 and B16.5 parameters for manifolds.

### dnv_iso_en_standards

DNV-ST-F101 and API RP 17A parameters for subsea manifolds.

## Best Practices

### Sizing Recommendations

1. **Header sizing**: Size for total combined flow with velocity < 15 m/s
2. **Branch sizing**: Size for individual well/outlet flow with velocity < 20 m/s
3. **Corrosion allowance**: Use 3mm for sour service, 1.5mm for sweet service

### Subsea Design Considerations

1. Always specify water depth for external pressure calculation
2. Use appropriate safety class factor based on consequence of failure
3. Consider minimum 6.35mm wall thickness for handling
4. Use corrosion resistant alloys for long-term subsea service

### Material Selection

| Application | Recommended Materials |
|-------------|----------------------|
| Topside sweet service | A106-B, A333 Gr 6 |
| Topside sour service | NACE compliant A106-B |
| Subsea standard | X65 with FBE coating |
| Subsea corrosive | 22Cr Duplex, 25Cr Super Duplex |
| High temperature | A335 Gr P11, P22 |

## See Also

- [Pipeline Mechanical Design](../pipeline_mechanical_design)
- [Riser Mechanical Design](../riser_mechanical_design)
- [Subsea Systems](subsea_systems)

## API Reference

See the [JavaDoc API Documentation](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html) for class details.
