---
title: "Well Mechanical Design"
description: "Subsea well casing and tubing mechanical design, standards-based barrier verification per NORSOK D-010, MAASP calculation per API RP 90, and drilling cost estimation using WellMechanicalDesign, WellDesignCalculator, WellBarrierSchematic, and WellCostEstimator."
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
    ├── WellMechanicalDesignDataSource (CSV standards loader)
    ├── WellBarrierSchematic (NORSOK D-010 two-barrier verification)
    │   ├── BarrierEnvelope (primary / secondary)
    │   │   └── BarrierElement (individual barrier components)
    │   └── Validation logic (element counts, DHSV/ISV, annulus monitoring)
    └── WellCostEstimator (drilling & completion costs)
```

Design factors, barrier requirements, and MAASP parameters are loaded from
CSV standards databases at runtime — no magic numbers in code.

### Applicable Standards

| Standard | Scope | CSV Data File |
|----------|-------|---------------|
| NORSOK D-010 Rev 5 | Well integrity, two-barrier principle, design factors | `norsok_standards.csv` |
| API 5CT / ISO 11960 | Casing and tubing grades and properties | `CasingProperties.csv` |
| API Bull 5C3 / TR 5C3 | Burst, collapse, and tension formulas | `api_standards.csv` |
| API RP 90 | Annular casing pressure management, MAASP | `api_standards.csv` |
| ISO 16530-1 | Well integrity lifecycle governance | `dnv_iso_en_standards.csv` |

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

NeqSim implements well barrier verification per NORSOK D-010 using a structured
three-class model: `BarrierElement` → `BarrierEnvelope` → `WellBarrierSchematic`.

### Barrier Elements (NORSOK D-010 Section 4)

A `BarrierElement` is an independent physical component that prevents uncontrolled
flow. Each element has a type, functional status, and verification status.

```java
BarrierElement dhsv = new BarrierElement(BarrierElement.ElementType.DHSV, "DHSV");
dhsv.setStatus(BarrierElement.Status.INTACT);
dhsv.setVerified(true);
dhsv.setInstallationDepth(500.0);

System.out.println(dhsv.isFunctional());  // true (INTACT or DEGRADED)
```

**Supported element types** (18 types per NORSOK D-010 Figure 3, Tables 20/36/37):

| Type | Description | Typical Envelope |
|------|-------------|-----------------|
| `CASING` | Production casing | Secondary |
| `TUBING` | Tubing string | Primary |
| `PACKER` | Production packer | Primary |
| `DHSV` | Downhole safety valve (SSSV) | Primary (producer) |
| `ISV` | Injection safety valve | Primary (injector) |
| `WELLHEAD` | Wellhead and tubing hanger | Both |
| `XMAS_TREE` | Christmas tree | Primary |
| `CEMENT` | Cement (primary or secondary) | Secondary |
| `CASING_CEMENT` | Cement behind production casing | Secondary |
| `FORMATION` | Competent caprock | Secondary |
| `PLUG` | Bridge/cement plug | Either |
| `WING_VALVE` | Wing valve (WV/PWV) | Primary |
| `SWAB_VALVE` | Swab valve | Primary |
| `ASV` | Annular safety valve | Secondary |
| `KILL_VALVE` | Kill valve | Secondary |
| `GAUGE` | Downhole gauge | — |
| `ANNULUS_ACCESS_VALVE` | Annulus access valve | Secondary |
| `CHEMICAL_INJECTION_VALVE` | Chemical injection valve | — |

### Barrier Envelopes (NORSOK D-010 Section 5)

A `BarrierEnvelope` groups elements into a complete pressure-containing boundary:

```java
BarrierEnvelope primary = new BarrierEnvelope("Primary");
primary.addElement(new BarrierElement(BarrierElement.ElementType.TUBING, "Tubing"));
primary.addElement(new BarrierElement(BarrierElement.ElementType.DHSV, "DHSV"));
primary.addElement(new BarrierElement(BarrierElement.ElementType.XMAS_TREE, "Xmas Tree"));

boolean intact = primary.isIntact();              // true if all elements functional
boolean meets = primary.meetsMinimum(2);          // true if >= 2 functional elements
int functional = primary.getFunctionalElementCount();
boolean hasDhsv = primary.hasElementType(BarrierElement.ElementType.DHSV);
```

### Barrier Schematic (Two-Barrier Principle)

The `WellBarrierSchematic` validates the complete barrier arrangement:

```java
WellBarrierSchematic schematic = new WellBarrierSchematic();
schematic.setWellType("OIL_PRODUCER");

schematic.setPrimaryEnvelope(primary);
schematic.setSecondaryEnvelope(secondary);

schematic.validate();

if (schematic.isPassed()) {
    System.out.println("Two-barrier principle satisfied");
} else {
    for (String issue : schematic.getIssues()) {
        System.out.println(issue);
    }
}
```

**Automatic validation checks:**

| Check | Requirement | Standard |
|-------|-------------|----------|
| Primary element count | ≥ 2 functional elements | NORSOK D-010 Sec 5 |
| Secondary element count | ≥ 2 functional elements | NORSOK D-010 Sec 5 |
| DHSV present (producers) | Required in primary envelope | NORSOK D-010 Table 20 |
| ISV present (injectors) | Required in primary envelope | NORSOK D-010 Table 36 |
| Annulus monitoring | Required | API RP 90 |
| Envelope integrity | All elements must be functional | NORSOK D-010 |

### Automatic Barrier Construction

When `WellMechanicalDesign.calcDesign()` runs, it automatically builds barrier
envelopes from the `SubseaWell` configuration:

- **Primary envelope:** tubing, DHSV (producer) or ISV (injector), wellhead, tree
- **Secondary envelope:** casing, cement, wellhead
- Extra elements are added based on `primaryBarrierElements` / `secondaryBarrierElements` counts

```java
// Runs automatically during calcDesign()
design.calcDesign();

WellBarrierSchematic schematic = design.getBarrierSchematic();
Map<String, Object> barrierMap = schematic.toMap();
// Contains element-level detail: type, status, verified, installation depth
```

### Legacy Barrier Verification

For backward compatibility, the count-based barrier verification still works
when no schematic is configured:

```java
well.setPrimaryBarrierElements(3);
well.setSecondaryBarrierElements(3);
well.setHasDHSV(true);

design.calcDesign();
boolean passed = design.isBarrierVerificationPassed();
```

## Standards-Based Design Data

Design factors and requirements are loaded from CSV databases at runtime via
`WellMechanicalDesignDataSource`. This eliminates hardcoded magic numbers and
allows company/project-specific overrides.

### How It Works

```java
// WellMechanicalDesign.readDesignSpecifications() does this automatically:
WellMechanicalDesignDataSource dataSource = new WellMechanicalDesignDataSource();

// 1. Load NORSOK D-010 design factors from CSV
dataSource.loadNorskD010DesignFactors(calculator, isInjectionWell);
// Sets: burstDF=1.10, collapseDF=1.00 (or 1.10 for injectors), tensionDF=1.60, vmeDF=1.25

// 2. Load barrier requirements from CSV
Map<String, Double> barriers = dataSource.loadBarrierRequirements();
// Returns: minPrimaryElements, minSecondaryElements, dhsvRequired, isvRequiredInjector, ...

// 3. Load API RP 90 MAASP parameters
Map<String, Double> maasp = dataSource.loadApiRp90Parameters();
// Returns: safetyFactor, collapseFactor, pressureTolerance, decayTestDuration, maxDecayRate

// 4. Load ISO 16530 lifecycle requirements
Map<String, Double> lifecycle = dataSource.loadIso16530Requirements();
// Returns: integrityTestInterval, barrierVerificationInterval, cementEvaluationRequired
```

### CSV Standards Files

The well design data comes from these standards CSV files in `src/main/resources/designdata/standards/`:

| File | Standards | Example Specifications |
|------|----------|----------------------|
| `norsok_standards.csv` | NORSOK D-010 | BurstDesignFactor, CollapseDesignFactor, MinPrimaryBarrierElements, DHSVRequired |
| `api_standards.csv` | API RP 90, API TR 5C3 | MAASPSafetyFactor, BarlowBurstFormula, MaxPressureDecayRate |
| `dnv_iso_en_standards.csv` | ISO 16530-1 | WellIntegrityTestInterval, BarrierVerificationInterval |

### Production vs. Injection Design Factors

The data source automatically selects the right design factors based on well type:

| Factor | Production Well | Injection Well | Source |
|--------|----------------|----------------|--------|
| Burst DF | 1.10 | 1.10 | NORSOK D-010 Table 18 |
| Collapse DF | 1.00 | **1.10** | NORSOK D-010 Table 18 |
| Tension DF | 1.60 | 1.60 | NORSOK D-010 Table 18 |
| Triaxial (VME) DF | 1.25 | 1.25 | NORSOK D-010 |

Note: injection wells use a higher collapse design factor (1.10 vs 1.00)
because reversed pressure loads during shut-in can create collapse scenarios.

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
| `WellMechanicalDesign` | `neqsim.process.mechanicaldesign.subsea` | Design orchestration, barrier verification, JSON reporting |
| `WellDesignCalculator` | `neqsim.process.mechanicaldesign.subsea` | Casing/tubing burst, collapse, tension calculations |
| `WellMechanicalDesignDataSource` | `neqsim.process.mechanicaldesign.subsea` | Loads design factors from CSV standards databases |
| `BarrierElement` | `neqsim.process.mechanicaldesign.subsea` | Single barrier element (type, status, depth) |
| `BarrierEnvelope` | `neqsim.process.mechanicaldesign.subsea` | Ordered collection of elements forming one envelope |
| `WellBarrierSchematic` | `neqsim.process.mechanicaldesign.subsea` | Primary + secondary envelopes with NORSOK D-010 validation |
| `WellCostEstimator` | `neqsim.process.mechanicaldesign.subsea` | Drilling & completion cost estimation |
| `AnnularLeakagePath` | `neqsim.process.equipment.reservoir` | Annular leakage + MAASP calculation per API RP 90 |

## Data Files

| File | Path | Content |
|------|------|---------|
| `WellCostData.csv` | `src/main/resources/designdata/` | Well cost parameters by type and rig |
| `CasingProperties.csv` | `src/main/resources/designdata/` | API 5CT casing grade properties (SMYS, SMTS) |
| `norsok_standards.csv` | `src/main/resources/designdata/standards/` | NORSOK D-010 design factors, barrier requirements |
| `api_standards.csv` | `src/main/resources/designdata/standards/` | API RP 90 MAASP parameters, API TR 5C3 burst formula |
| `dnv_iso_en_standards.csv` | `src/main/resources/designdata/standards/` | ISO 16530-1 lifecycle requirements |

## Related Documentation

- [Out-of-Zone Injection](out_of_zone_injection.md) - Multi-zone injection, leakage, MAASP
- [SURF Subsea Equipment](SURF_SUBSEA_EQUIPMENT) - PLET, PLEM, manifolds, trees, jumpers, umbilicals
- [Mechanical Design Framework](mechanical_design) - Architecture and JSON export
- [Mechanical Design Standards](mechanical_design_standards) - Industry standards reference
- [Mechanical Design Database](mechanical_design_database) - Material properties, CSV data files
- [Pipeline Mechanical Design](pipeline_mechanical_design) - Pipeline wall thickness and cost
- [Riser Mechanical Design](riser_mechanical_design) - Riser catenary and VIV
