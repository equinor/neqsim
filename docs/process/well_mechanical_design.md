---
title: "Well Mechanical Design"
description: "Subsea well casing and tubing mechanical design, well barrier verification per NORSOK D-010, and drilling cost estimation using WellMechanicalDesign, WellDesignCalculator, and WellCostEstimator."
---

# Well Mechanical Design

NeqSim provides a comprehensive well mechanical design system for subsea wells,
covering casing/tubing design, well barrier verification, weight estimation, and
drilling & completion cost estimation.

## Overview

The well design system follows the same three-layer architecture used throughout
NeqSim's mechanical design framework:

```
SubseaWell (equipment)
└── WellMechanicalDesign (design orchestration)
    ├── WellDesignCalculator (casing/tubing engineering)
    └── WellCostEstimator (drilling & completion costs)
```

### Applicable Standards

| Standard | Scope |
|----------|-------|
| NORSOK D-010 | Well integrity, two-barrier principle, design factors |
| API 5CT / ISO 11960 | Casing and tubing grades and properties |
| API Bull 5C3 | Burst, collapse, and tension formulas |
| API RP 90 | Annular casing pressure management |

### Minimum Design Factors (NORSOK D-010)

| Load Case | Minimum DF |
|-----------|-----------|
| Burst | 1.10 |
| Collapse | 1.00 |
| Tension | 1.60 |
| Triaxial (VME) | 1.25 |

## Quick Start

```java
// 1. Create fluid and stream
SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 200.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

Stream stream = new Stream("reservoir", fluid);
stream.setFlowRate(50000.0, "kg/hr");
stream.setTemperature(80.0, "C");
stream.setPressure(200.0, "bara");

// 2. Create well with engineering properties
SubseaWell well = new SubseaWell("Producer-1", stream);
well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
well.setCompletionType(SubseaWell.CompletionType.CASED_PERFORATED);
well.setRigType(SubseaWell.RigType.SEMI_SUBMERSIBLE);

// Geometry
well.setMeasuredDepth(3800.0);
well.setTrueVerticalDepth(3200.0);
well.setWaterDepth(350.0);

// Design conditions
well.setMaxWellheadPressure(345.0);
well.setReservoirPressure(400.0);
well.setReservoirTemperature(100.0);

// Casing program
well.setConductorOD(30.0);      well.setConductorDepth(100.0);
well.setSurfaceCasingOD(20.0);  well.setSurfaceCasingDepth(800.0);
well.setIntermediateCasingOD(13.375); well.setIntermediateCasingDepth(2500.0);
well.setProductionCasingOD(9.625);    well.setProductionCasingDepth(3800.0);

// Tubing
well.setTubingOD(5.5);
well.setTubingWeight(23.0);
well.setTubingGrade("L80");

// Drilling schedule
well.setDrillingDays(45.0);
well.setCompletionDays(25.0);
well.setRigDayRate(540000.0);

// Well barriers (NORSOK D-010)
well.setHasDHSV(true);
well.setPrimaryBarrierElements(3);
well.setSecondaryBarrierElements(3);

// 3. Run mechanical design
well.initMechanicalDesign();
WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
design.calcDesign();

// 4. Run cost estimation
design.calculateCostEstimate();

// 5. Get results
System.out.println("Wall thickness: " + design.getProductionCasingWallThickness() + " mm");
System.out.println("Burst DF: " + design.getProductionCasingBurstDF());
System.out.println("Barriers: " + (design.isBarrierVerificationPassed() ? "PASS" : "FAIL"));
System.out.println("Total cost: $" + design.getTotalCostUSD() / 1e6 + "M");
System.out.println(design.toJson());
```

## Well Types and Enumerations

### Well Type

```java
SubseaWell.WellType.OIL_PRODUCER     // Oil production well
SubseaWell.WellType.GAS_PRODUCER     // Gas production well
SubseaWell.WellType.WATER_INJECTOR   // Water injection well
SubseaWell.WellType.GAS_INJECTOR     // Gas injection well
SubseaWell.WellType.OBSERVATION      // Observation / monitoring well
```

### Completion Type

```java
SubseaWell.CompletionType.CASED_PERFORATED  // Standard cased & perforated
SubseaWell.CompletionType.OPEN_HOLE         // Open hole (cost factor 0.70)
SubseaWell.CompletionType.GRAVEL_PACK       // Gravel pack (cost factor 1.30)
SubseaWell.CompletionType.ICD               // Inflow control device (1.25)
SubseaWell.CompletionType.AICD              // Autonomous ICD (1.45)
SubseaWell.CompletionType.MULTI_ZONE        // Multi-zone (1.80)
```

### Rig Type

```java
SubseaWell.RigType.SEMI_SUBMERSIBLE  // Semi-submersible (factor 1.0)
SubseaWell.RigType.DRILLSHIP         // Drillship (factor 1.20)
SubseaWell.RigType.JACK_UP           // Jack-up (factor 0.70)
SubseaWell.RigType.PLATFORM_RIG      // Platform rig (factor 0.60)
```

## Casing Design

The `WellDesignCalculator` performs casing design for each string:

### Load Cases

**Burst** (internal pressure exceeds external):
- Worst case: full reservoir pressure at surface (gas-filled well)
- Barlow formula: $P_{burst} = \frac{2 \cdot SMYS \cdot t}{OD}$
- Required: $P_{rated} \geq DF_{burst} \times P_{design}$

**Collapse** (external pressure exceeds internal):
- Worst case: empty string opposite full mud/cement column
- API 5C3 yield-strength collapse: $P_{collapse} = 2 \cdot SMYS \cdot \frac{(D/t - 1)}{(D/t)^2}$

**Tension** (axial load from casing weight):
- Weight in air plus running/landing loads
- $DF_{tension} = \frac{SMYS \times A_{cross}}{W_{casing} \times g}$

### Supported Casing Grades (API 5CT)

| Grade | SMYS (MPa) | SMTS (MPa) | Typical Use |
|-------|-----------|-----------|-------------|
| H40 | 276 | 414 | Conductor |
| J55 / K55 | 379 | 517-655 | Surface casing |
| N80 / L80 | 552 | 655-689 | Intermediate (L80 for sour service) |
| C90 / C95 | 621-655 | 689-724 | Sour service |
| P110 | 758 | 862 | Production casing |
| Q125 | 862 | 931 | Ultra-high-strength |
| 13Cr-L80 | 552 | 655 | CO2 service (CRA) |
| 25Cr | 758 | 862 | Severe corrosion (super duplex) |

### Example: Direct Calculator Usage

```java
WellDesignCalculator calc = new WellDesignCalculator();
calc.setMeasuredDepth(3800.0);
calc.setWaterDepth(350.0);
calc.setMaxWellheadPressure(345.0);
calc.setReservoirPressure(400.0);

calc.setConductorCasing(30.0, 100.0);
calc.setSurfaceCasing(20.0, 800.0);
calc.setIntermediateCasing(13.375, 2500.0);
calc.setProductionCasing(9.625, 3800.0);
calc.setTubing(5.5, 23.0, "L80");

calc.calculateCasingDesign();
calc.calculateTubingDesign();
calc.calculateWeights();
calc.calculateCementVolumes();

Map<String, Object> results = calc.toMap();
```

## Well Barrier Verification

Per NORSOK D-010, subsea wells must maintain two independent well barriers.
`WellMechanicalDesign.calcDesign()` automatically verifies:

1. **Primary barrier** requires minimum 2 elements
2. **Secondary barrier** requires minimum 2 elements
3. **DHSV (SSSV)** required for subsea production wells
4. **Annular pressure monitoring** noted as required

```java
design.calcDesign();

if (design.isBarrierVerificationPassed()) {
    System.out.println("Two-barrier principle satisfied");
} else {
    for (String note : design.getBarrierNotes()) {
        System.out.println(note);
    }
}
```

## Cost Estimation

### WellCostEstimator

The cost estimator provides detailed well cost breakdowns with regional factors.

```java
WellCostEstimator estimator = new WellCostEstimator(SubseaCostEstimator.Region.NORWAY);
estimator.calculateWellCost(
    "OIL_PRODUCER",       // well type
    "SEMI_SUBMERSIBLE",   // rig type
    "CASED_PERFORATED",   // completion type
    3800.0,               // measured depth (m)
    350.0,                // water depth (m)
    45.0,                 // drilling days
    25.0,                 // completion days
    0.0,                  // rig day rate override (0 = use default)
    true,                 // has DHSV
    4                     // number of casing strings
);

System.out.println("Total: $" + estimator.getTotalCost() / 1e6 + "M");
System.out.println(estimator.toJson());
```

### Cost Breakdown Structure

| Category | Components |
|----------|-----------|
| **Drilling** | Rig time, casing material, cement, mud, bits & tools |
| **Completion** | Tubing, packers, screens, safety valves, rig time |
| **Wellhead** | Subsea wellhead and Xmas tree assembly |
| **Evaluation** | Logging, surveys, well testing |
| **Contingency** | Default 15% on subtotal |

### Regional Cost Factors

Regional factors affect all cost components (rig rates, materials, services):

| Region | Factor | Typical Well Cost (Oil Producer) |
|--------|--------|----------------------------------|
| Norway (NCS) | 1.35 | Highest |
| UK North Sea | 1.20 | High |
| West Africa | 1.10 | Above average |
| US Gulf of Mexico | 1.00 | Baseline |
| Brazil | 0.90 | Below average |

### Bill of Materials

```java
List<Map<String, Object>> bom = design.generateBillOfMaterials();
for (Map<String, Object> item : bom) {
    System.out.printf("%s: %.0f %s%n",
        item.get("name"), item.get("quantity"), item.get("unit"));
}
```

BOM includes: conductor, surface, intermediate, and production casing (by depth),
production tubing, wellhead assembly, DHSV, and cement.

## JSON Export

`toJson()` produces a comprehensive report:

```json
{
  "equipmentType": "SubseaWell",
  "designStandardCode": "NORSOK-D-010",
  "configuration": {
    "wellType": "OIL_PRODUCER",
    "completionType": "CASED_PERFORATED",
    "rigType": "SEMI_SUBMERSIBLE",
    "hasDHSV": true,
    "numberOfCasingStrings": 4
  },
  "geometry": {
    "measuredDepthM": 3800.0,
    "trueVerticalDepthM": 3200.0,
    "waterDepthM": 350.0
  },
  "casingProgram": {
    "conductorOD_in": 30.0,
    "surfaceCasingOD_in": 20.0,
    "intermediateCasingOD_in": 13.375,
    "productionCasingOD_in": 9.625
  },
  "designResults": {
    "productionCasingWallThicknessMm": 8.9,
    "productionCasingBurstDF": 1.52,
    "productionCasingCollapseDF": 2.31,
    "productionCasingTensionDF": 4.87
  },
  "weights": {
    "totalCasingWeightTonnes": 185.3,
    "totalTubingWeightTonnes": 42.5,
    "totalCementVolumeM3": 78.2
  },
  "barrierVerification": {
    "verificationPassed": true,
    "notes": ["PASS: Two-barrier principle satisfied per NORSOK D-010"]
  },
  "costEstimation": {
    "totalCostUSD": 45200000,
    "drillingCostUSD": 24300000,
    "completionCostUSD": 12100000,
    "wellheadCostUSD": 3375000
  },
  "schedule": {
    "drillingDays": 45,
    "completionDays": 25,
    "totalDays": 70
  }
}
```

## Integration with Field Development

`FieldDevelopmentCostEstimator` uses `WellCostEstimator` internally when well
parameters are configured:

```java
FieldDevelopmentCostEstimator costEst = new FieldDevelopmentCostEstimator(facility);
costEst.setSubseaParameters(15.0, 350.0);     // 15 km tieback, 350m water depth
costEst.setWellParameters(6, 3, 3800.0);      // 6 producers, 3 injectors, avg 3800m MD

FieldDevelopmentCostReport report = costEst.estimateDevelopmentCosts();
```

## Class Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `SubseaWell` | `neqsim.process.equipment.subsea` | Equipment class with well engineering properties |
| `WellMechanicalDesign` | `neqsim.process.mechanicaldesign.subsea` | Design orchestration, barrier verification |
| `WellDesignCalculator` | `neqsim.process.mechanicaldesign.subsea` | Casing/tubing burst, collapse, tension calculations |
| `WellCostEstimator` | `neqsim.process.mechanicaldesign.subsea` | Drilling & completion cost estimation |

## Data Files

| File | Path | Content |
|------|------|---------|
| `WellCostData.csv` | `src/main/resources/designdata/` | Well cost parameters by type and rig |
| `CasingProperties.csv` | `src/main/resources/designdata/` | API 5CT casing grade properties (SMYS, SMTS) |

## Related Documentation

- [SURF Subsea Equipment](SURF_SUBSEA_EQUIPMENT) - PLET, PLEM, manifolds, trees, jumpers, umbilicals
- [Mechanical Design Framework](mechanical_design) - Architecture and JSON export
- [Mechanical Design Standards](mechanical_design_standards) - Industry standards reference
- [Mechanical Design Database](mechanical_design_database) - Material properties, CSV data files
- [Pipeline Mechanical Design](pipeline_mechanical_design) - Pipeline wall thickness and cost
- [Riser Mechanical Design](riser_mechanical_design) - Riser catenary and VIV
