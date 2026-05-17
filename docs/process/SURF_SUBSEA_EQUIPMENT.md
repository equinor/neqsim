---
title: Subsea SURF Equipment - NeqSim Documentation
description: NeqSim provides comprehensive support for modeling Subsea, Umbilicals, Risers, and Flowlines (SURF) equipment used in offshore oil and gas field development. The subsea equipment package (`neqsim.proc...
---

# Subsea SURF Equipment - NeqSim Documentation

## Overview

NeqSim provides comprehensive support for modeling Subsea, Umbilicals, Risers, and Flowlines (SURF) equipment used in offshore oil and gas field development. The subsea equipment package (`neqsim.process.equipment.subsea`) includes classes for all major components of a subsea production system.

## Equipment Classes

### 1. PLET (Pipeline End Termination)

Pipeline End Terminations are structures that terminate pipelines and provide connection points for tie-ins.

```java
PLET plet = new PLET("Export PLET", pipelineStream);
plet.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
plet.setStructureType(PLET.StructureType.GRAVITY_BASE);
plet.setWaterDepth(350.0);
plet.setDesignPressure(200.0);
plet.setHubSizeInches(10.0);
plet.setMaterialGrade("X65");
plet.setHasPiggingFacility(true);
plet.run();
```

**Connection Types:**
- `VERTICAL_HUB` - Vertical connection hub
- `HORIZONTAL_HUB` - Horizontal connection hub
- `CLAMP_CONNECTOR` - Clamp connection
- `COLLET_CONNECTOR` - Collet connection
- `DIVER_FLANGE` - Diver-installable flange

**Structure Types:**
- `GRAVITY_BASE` - Gravity-based foundation
- `PILED` - Piled foundation
- `SUCTION_ANCHOR` - Suction anchor foundation
- `MUDMAT` - Mudmat foundation

### 2. PLEM (Pipeline End Manifold)

Pipeline End Manifolds are multi-slot structures for connecting multiple pipelines.

```java
PLEM plem = new PLEM("Gathering PLEM", mainStream);
plem.setConfigurationType(PLEM.ConfigurationType.COMMINGLING);
plem.setNumberOfSlots(4);
plem.setWaterDepth(400.0);
plem.setDesignPressure(180.0);
plem.setHeaderSizeInches(16.0);
plem.run();
```

**Configuration Types:**
- `THROUGH_FLOW` - Straight-through flow
- `COMMINGLING` - Multiple inputs merged
- `DISTRIBUTION` - Single input split to multiple outputs
- `CROSSOVER` - Cross-connection capability

### 3. SubseaManifold

Subsea manifolds gather production from multiple wells and route to export/test headers.

```java
SubseaManifold manifold = new SubseaManifold("Field Manifold");
manifold.setManifoldType(SubseaManifold.ManifoldType.PRODUCTION_TEST);
manifold.setNumberOfWellSlots(6);
manifold.setProductionHeaderSizeInches(12.0);
manifold.setTestHeaderSizeInches(6.0);
manifold.setWaterDepth(450.0);
manifold.setDesignPressure(250.0);

// Add well streams
manifold.addWellStream(well1Stream, 1);
manifold.addWellStream(well2Stream, 2);
manifold.addWellStream(well3Stream, 3);

// Route wells
manifold.routeWellToProduction(1);
manifold.routeWellToProduction(2);
manifold.routeWellToTest(3);  // Route well 3 to test

manifold.run();

// Get outputs
Stream prodStream = manifold.getProductionOutputStream();
Stream testStream = manifold.getTestOutputStream();
```

**Manifold Types:**
- `PRODUCTION_ONLY` - Single production header
- `PRODUCTION_TEST` - Production and test headers
- `FULL_SERVICE` - Production, test, and injection
- `INJECTION` - Injection manifold

### 4. SubseaJumper

Subsea jumpers connect subsea equipment (trees, manifolds, PLETs).

```java
SubseaJumper jumper = new SubseaJumper("Tree-Manifold Jumper", treeOutlet);
jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
jumper.setLength(50.0);
jumper.setNominalBoreInches(6.0);
jumper.setOuterDiameterInches(6.625);
jumper.setWallThicknessMm(12.7);
jumper.setDesignPressure(200.0);
jumper.setMaterialGrade("X65");
jumper.setNumberOfBends(3);
jumper.setMinimumBendRadius(1.5);
jumper.setInletHubType(SubseaJumper.HubType.VERTICAL);
jumper.setOutletHubType(SubseaJumper.HubType.HORIZONTAL);
jumper.run();
```

**Jumper Types:**
- `RIGID_M_SHAPE` - Rigid M-shaped configuration
- `RIGID_INVERTED_U` - Rigid inverted U-shape
- `FLEXIBLE_STATIC` - Static flexible jumper
- `FLEXIBLE_DYNAMIC` - Dynamic flexible jumper
- `HYBRID` - Rigid with flexible sections

### 5. Umbilical

Control umbilicals provide hydraulic power, chemical injection, and electrical/signal connectivity.

```java
Umbilical umbilical = new Umbilical("Field Umbilical");
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setLength(15000.0);
umbilical.setWaterDepth(450.0);
umbilical.setHasArmorWires(true);

// Add hydraulic lines
umbilical.addHydraulicLine(12.7, 517.0, "HP Supply");  // ID mm, pressure bar
umbilical.addHydraulicLine(12.7, 517.0, "HP Return");
umbilical.addHydraulicLine(9.525, 345.0, "LP Supply");
umbilical.addHydraulicLine(9.525, 345.0, "LP Return");

// Add chemical lines
umbilical.addChemicalLine(25.4, 207.0, "MEG Injection");
umbilical.addChemicalLine(19.05, 207.0, "Scale Inhibitor");

// Add electrical cables
umbilical.addElectricalCable(35.0, 6600.0, "Power");  // Area mm², voltage V
umbilical.addElectricalCable(4.0, 500.0, "Signal");

// Add fiber optics
umbilical.addFiberOptic(12, "Communication");  // Number of fibers

umbilical.run(null);

// Get element counts
int hydraulics = umbilical.getHydraulicLineCount();
int chemicals = umbilical.getChemicalLineCount();
int electrical = umbilical.getElectricalCableCount();
```

**Umbilical Types:**
- `STEEL_TUBE` - Steel tube umbilical
- `THERMOPLASTIC` - Thermoplastic hose umbilical
- `INTEGRATED_PRODUCTION` - Integrated production umbilical (IPU)

### 6. SubseaTree (Christmas Tree)

Subsea trees control wellhead flow and provide safety barriers.

```java
SubseaTree tree = new SubseaTree("Well-A Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setPressureRating(SubseaTree.PressureRating.PR10000);
tree.setBoreSizeInches(5.125);
tree.setWaterDepth(400.0);
tree.setDesignPressure(690.0);
tree.setDesignTemperature(121.0);
tree.setActuatorType("Hydraulic");
tree.setFailSafeClose(true);

// Control valves
tree.setPMVOpen(true);   // Production Master Valve
tree.setPWVOpen(true);   // Production Wing Valve
tree.setChokePosition(75.0);  // 75% open

tree.run();

// Emergency shutdown
tree.emergencyShutdown();
```

**Tree Types:**
- `VERTICAL` - Vertical tree (conventional)
- `HORIZONTAL` - Horizontal tree
- `DUAL_BORE` - Dual bore tree
- `MUDLINE` - Mudline suspension tree

**Pressure Ratings:**
- `PR5000` - 5,000 psi (345 bar)
- `PR10000` - 10,000 psi (690 bar)
- `PR15000` - 15,000 psi (1,034 bar)
- `PR20000` - 20,000 psi (1,379 bar)

### 7. FlexiblePipe

Flexible pipes and risers for dynamic and static applications.

```java
FlexiblePipe riser = new FlexiblePipe("Production Riser", inletStream);
riser.setPipeType(FlexiblePipe.PipeType.UNBONDED);
riser.setApplication(FlexiblePipe.Application.DYNAMIC_RISER);
riser.setServiceType(FlexiblePipe.ServiceType.OIL_SERVICE);
riser.setRiserConfiguration(FlexiblePipe.RiserConfiguration.LAZY_WAVE);
riser.setLength(1200.0);
riser.setInnerDiameterInches(6.0);
riser.setDesignPressure(200.0);
riser.setDesignTemperature(65.0);
riser.setWaterDepth(350.0);
riser.setSourService(false);

// Layer configuration
riser.setHasCarcass(true);
riser.setHasPressureArmor(true);
riser.setTensileArmorLayers(2);

// Accessories
riser.setHasBendStiffener(true);
riser.setHasBuoyancyModules(true);

riser.run();
```

**Pipe Types:**
- `UNBONDED` - Unbonded flexible pipe (API 17J)
- `BONDED` - Bonded flexible pipe (API 17K)

**Applications:**
- `FLOWLINE` - Subsea flowline
- `STATIC_RISER` - Static riser
- `DYNAMIC_RISER` - Dynamic riser
- `JUMPER` - Flexible jumper
- `EXPANSION_LOOP` - Expansion loop

**Riser Configurations:**
- `FREE_HANGING` - Free hanging catenary
- `LAZY_WAVE` - Lazy wave with buoyancy
- `STEEP_WAVE` - Steep wave configuration
- `LAZY_S` - Lazy S with mid-water arch
- `STEEP_S` - Steep S configuration
- `PLIANT_WAVE` - Pliant wave
- `CHINESE_LANTERN` - Chinese lantern

### 8. SubseaBooster

Subsea pumps and compressors for boosting production.

```java
// Multiphase pump
SubseaBooster mpPump = new SubseaBooster("MP Pump", inletStream);
mpPump.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
mpPump.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
mpPump.setDriveType(SubseaBooster.DriveType.ELECTRIC);
mpPump.setNumberOfStages(6);
mpPump.setDesignInletPressure(50.0);
mpPump.setDifferentialPressure(30.0);
mpPump.setDesignFlowRate(500.0);
mpPump.setEfficiency(0.65);
mpPump.setWaterDepth(400.0);

// Reliability settings
mpPump.setDesignLifeYears(25);
mpPump.setMtbfHours(40000);
mpPump.setRetrievable(true);

mpPump.run();

// Wet gas compressor
SubseaBooster compressor = new SubseaBooster("WG Compressor", gasStream);
compressor.setBoosterType(SubseaBooster.BoosterType.WET_GAS_COMPRESSOR);
compressor.setCompressorType(SubseaBooster.CompressorType.CENTRIFUGAL);
compressor.setPressureRatio(2.0);
compressor.run();
```

**Booster Types:**
- `MULTIPHASE_PUMP` - Multiphase pump
- `WET_GAS_COMPRESSOR` - Wet gas compressor
- `SUBSEA_SEPARATOR_PUMP` - Separation system pump
- `INJECTION_PUMP` - Water/gas injection pump

**Pump Types:**
- `HELICO_AXIAL` - Helico-axial multiphase pump
- `TWIN_SCREW` - Twin-screw positive displacement
- `ESP` - Electrical submersible pump
- `CENTRIFUGAL` - Centrifugal pump

## Mechanical Design

All subsea equipment supports mechanical design calculations:

```java
// Example with PLET
PLET plet = new PLET("Export PLET", stream);
plet.setWaterDepth(350.0);
plet.setDesignPressure(200.0);
plet.setHubSizeInches(10.0);
plet.setMaterialGrade("X65");
plet.run();

// Initialize mechanical design
plet.initMechanicalDesign();
PLETMechanicalDesign design = (PLETMechanicalDesign) plet.getMechanicalDesign();

// Set company-specific standards
design.setCompanySpecificDesignStandards("Equinor");

// Calculate design
design.readDesignSpecifications();
design.calcDesign();

// Get results
String jsonReport = design.toJson();
Map<String, Object> results = design.toMap();
double wallThickness = design.getRequiredWallThickness();
```

### Mechanical Design Classes

Each subsea equipment type has a corresponding mechanical design class:

| Equipment | Mechanical Design Class | Key Calculations |
|-----------|------------------------|------------------|
| PLET | `PLETMechanicalDesign` | Hub wall thickness, foundation sizing, mudmat area, pile depth |
| PLEM | `PLEMMechanicalDesign` | Header wall thickness, multi-slot structure, foundation |
| SubseaTree | `SubseaTreeMechanicalDesign` | Bore wall thickness, connector capacity, gate valve sizing |
| SubseaManifold | `SubseaManifoldMechanicalDesign` | Header sizing, valve skid, foundation requirements |
| SubseaJumper | `SubseaJumperMechanicalDesign` | Wall thickness, bend radius, spool piece length |
| Umbilical | `UmbilicalMechanicalDesign` | Cross-section design, armor wire sizing, tensile capacity |
| FlexiblePipe | `FlexiblePipeMechanicalDesign` | Layer design, collapse resistance, fatigue life |
| SubseaBooster | `SubseaBoosterMechanicalDesign` | Motor sizing, seal design, foundation requirements |

### Design Standards Supported

| Standard | Description | Equipment |
|----------|-------------|-----------|
| DNV-ST-F101 | Submarine Pipeline Systems | Pipelines, Jumpers, PLETs |
| DNV-ST-F201 | Dynamic Risers | Flexible Risers |
| DNV-RP-F109 | On-Bottom Stability | Flowlines |
| API Spec 17D | Subsea Wellhead and Tree Equipment | Trees |
| API RP 17A | Design of Subsea Production Systems | General |
| API RP 17B | Flexible Pipe | Flexible Pipes |
| API Spec 17J | Unbonded Flexible Pipe | Unbonded Flexible |
| API Spec 17K | Bonded Flexible Pipe | Bonded Flexible |
| API RP 17E | Umbilicals | Umbilicals |
| API RP 17G | Subsea Production Systems | Manifolds |
| API RP 17Q | Subsea Equipment Qualification | All |
| API RP 17V | Subsea Boosting | Boosters |
| ISO 13628 | Subsea Production Systems | All |
| NORSOK U-001 | Subsea Production Systems | All |

### Detailed Mechanical Design Example

```java
// PLET Mechanical Design
PLET plet = new PLET("Production PLET");
plet.setHubSizeInches(12.0);
plet.setWaterDepth(350.0);
plet.setDesignPressure(250.0);
plet.setDryWeight(25.0);
plet.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
plet.setStructureType(PLET.StructureType.GRAVITY_BASE);
plet.setHasIsolationValve(true);
plet.setHasPiggingFacilities(true);
plet.initMechanicalDesign();

PLETMechanicalDesign design = (PLETMechanicalDesign) plet.getMechanicalDesign();
design.setMaxOperationPressure(250.0);
design.setMaxOperationTemperature(80.0 + 273.15);
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-ST-F101");
design.setCompanySpecificDesignStandards("Equinor");

// Calculate design
design.readDesignSpecifications();
design.calcDesign();

// Get design results
double hubWallThickness = design.getHubWallThickness();
double requiredMudmatArea = design.getRequiredMudmatArea();
double maxBearingPressure = design.getMaxBearingPressure();
double connectorCapacity = design.getConnectorLoadCapacity();

System.out.println("Hub Wall Thickness: " + hubWallThickness + " mm");
System.out.println("Required Mudmat Area: " + requiredMudmatArea + " m²");
System.out.println("Connector Capacity: " + connectorCapacity + " kN");

// Full JSON report
String jsonReport = design.toJson();
```

### Foundation Design

The mechanical design classes calculate foundation requirements based on soil conditions and loading:

```java
// Gravity base foundation
PLETMechanicalDesign design = (PLETMechanicalDesign) plet.getMechanicalDesign();
design.calcDesign();

double mudmatArea = design.getRequiredMudmatArea();       // m²
double foundationWeight = design.getRequiredFoundationWeight(); // tonnes
double bearingPressure = design.getMaxBearingPressure();  // kPa

// For piled structures
if (plet.getStructureType() == PLET.StructureType.PILED) {
    double pileDepth = design.getPileDepth();             // m
    int numberOfPiles = design.getNumberOfPiles();
}

// For suction anchor structures
if (plet.getStructureType() == PLET.StructureType.SUCTION_ANCHOR) {
    double anchorDiameter = design.getSuctionAnchorDiameter();  // m
    double anchorLength = design.getSuctionAnchorLength();      // m
}
```

---

## Cost Estimation

NeqSim provides comprehensive cost estimation for all subsea SURF equipment through the `SubseaCostEstimator` class and integrated cost methods in each mechanical design class.

### Cost Estimator Overview

The `SubseaCostEstimator` calculates:

- **Equipment/Procurement Costs** - Base equipment and material costs
- **Fabrication Costs** - Shop fabrication and assembly
- **Installation Costs** - Vessel, ROV, and labor costs
- **Engineering Costs** - Design engineering and project management
- **Contingency** - Risk-based contingency allowances

### Regional Cost Factors

Costs are adjusted based on installation region:

| Region | Factor | Description |
|--------|--------|-------------|
| NORWAY | 1.35 | Norwegian Continental Shelf |
| UK | 1.25 | UK North Sea |
| GOM | 1.00 | Gulf of Mexico (baseline) |
| BRAZIL | 0.85 | Brazilian pre-salt basins |
| WEST_AFRICA | 1.10 | West African margin |

### Currency Support

Cost estimates can be output in multiple currencies:

| Currency | Code | Conversion (from USD) |
|----------|------|----------------------|
| US Dollar | USD | 1.00 |
| Euro | EUR | 0.92 |
| British Pound | GBP | 0.79 |
| Norwegian Krone | NOK | 10.50 |

### Basic Cost Estimation

```java
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;

// Create estimator with region
SubseaCostEstimator estimator = new SubseaCostEstimator(
    SubseaCostEstimator.Region.NORWAY);

// PLET cost estimation
// Parameters: dryWeightTonnes, hubSizeInches, waterDepthM, hasIsolationValve, hasPiggingFacility
estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);

// Get results
double totalCost = estimator.getTotalCost();
double equipmentCost = estimator.getEquipmentCost();
double installationCost = estimator.getInstallationCost();
double vesselDays = estimator.getVesselDays();
double totalManhours = estimator.getTotalManhours();

System.out.println("Total Cost: $" + String.format("%,.0f", totalCost));
System.out.println("Equipment: $" + String.format("%,.0f", equipmentCost));
System.out.println("Installation: $" + String.format("%,.0f", installationCost));
System.out.println("Vessel Days: " + vesselDays);
System.out.println("Total Manhours: " + totalManhours);
```

### Cost Estimation Methods

The `SubseaCostEstimator` provides methods for each equipment type:

```java
// PLET/PLEM cost
estimator.calculatePLETCost(dryWeightTonnes, hubSizeInches, waterDepthM, 
    hasIsolationValve, hasPiggingFacility);

// Subsea Tree cost
estimator.calculateTreeCost(pressureRatingPsi, boreSizeInches, waterDepthM, 
    isHorizontal, isDualBore);

// Manifold cost
estimator.calculateManifoldCost(numberOfSlots, dryWeightTonnes, waterDepthM, 
    hasTestHeader);

// Jumper cost
estimator.calculateJumperCost(lengthM, diameterInches, isRigid, waterDepthM);

// Umbilical cost
estimator.calculateUmbilicalCost(lengthKm, numberOfHydraulicLines, 
    numberOfChemicalLines, numberOfElectricalCables, waterDepthM, isDynamic);

// Flexible pipe cost
estimator.calculateFlexiblePipeCost(lengthM, innerDiameterInches, waterDepthM, 
    isDynamic, hasBuoyancy);

// Subsea booster cost
estimator.calculateBoosterCost(powerMW, isCompressor, waterDepthM, hasRedundancy);
```

### Integrated Cost Estimation

Each mechanical design class integrates cost estimation:

```java
// PLET with cost estimation
PLET plet = new PLET("Production PLET");
plet.setHubSizeInches(12.0);
plet.setWaterDepth(350.0);
plet.setDryWeight(25.0);
plet.setHasIsolationValve(true);
plet.initMechanicalDesign();

PLETMechanicalDesign design = (PLETMechanicalDesign) plet.getMechanicalDesign();
design.setMaxOperationPressure(250.0);
design.setRegion(SubseaCostEstimator.Region.NORWAY);

// Calculate design and costs
design.calcDesign();

// Get costs directly from design
double totalCost = design.getTotalCostUSD();
double equipmentCost = design.getEquipmentCostUSD();
double installationCost = design.getInstallationCostUSD();
double vesselDays = design.getVesselDays();

// Get full cost breakdown
Map<String, Object> costBreakdown = design.getCostBreakdown();

// Generate bill of materials
List<Map<String, Object>> bom = design.generateBillOfMaterials();
```

### Cost Breakdown Structure

The `getCostBreakdown()` method returns a comprehensive Map with:

```java
Map<String, Object> costs = design.getCostBreakdown();

// Direct Costs
Map<String, Object> direct = (Map<String, Object>) costs.get("directCosts");
double equipmentCost = (Double) direct.get("equipmentCostUSD");
double fabricationCost = (Double) direct.get("fabricationCostUSD");
double installationCost = (Double) direct.get("installationCostUSD");

// Indirect Costs
Map<String, Object> indirect = (Map<String, Object>) costs.get("indirectCosts");
double engineeringCost = (Double) indirect.get("engineeringCostUSD");
double pmCost = (Double) indirect.get("projectManagementCostUSD");

// Installation Breakdown
Map<String, Object> install = (Map<String, Object>) costs.get("installationBreakdown");
double vesselCost = (Double) install.get("vesselCostUSD");
double vesselDays = (Double) install.get("vesselDays");
double vesselDayRate = (Double) install.get("vesselDayRateUSD");
double rovHours = (Double) install.get("rovHours");

// Labor Estimate
Map<String, Object> labor = (Map<String, Object>) costs.get("laborEstimate");
double engManhours = (Double) labor.get("engineeringManhours");
double fabManhours = (Double) labor.get("fabricationManhours");
double installManhours = (Double) labor.get("installationManhours");
double totalManhours = (Double) labor.get("totalManhours");

// Summary
double contingency = (Double) costs.get("contingencyUSD");
double totalCost = (Double) costs.get("totalCostUSD");
```

### Bill of Materials Generation

Generate detailed BOM for procurement:

```java
List<Map<String, Object>> bom = design.generateBillOfMaterials();

for (Map<String, Object> item : bom) {
    System.out.println(item.get("item") + ": " + 
        item.get("quantity") + " " + item.get("unit") +
        " @ $" + item.get("unitCost") + " = $" + item.get("totalCost"));
}
```

Example BOM output:

| Item | Material | Quantity | Unit | Unit Cost | Total Cost |
|------|----------|----------|------|-----------|------------|
| Steel Structure | S355/X65 | 15.0 | tonnes | $5,000 | $75,000 |
| Piping Components | Duplex SS/CRA | 3.75 | tonnes | $15,000 | $56,250 |
| Valves and Actuators | Various | 2 | ea | $150,000 | $300,000 |
| Subsea Connectors | Forged Steel | 2 | ea | $200,000 | $400,000 |
| Foundation/Mudmat | S355 Steel Plate | 6.25 | tonnes | $4,000 | $25,000 |
| Marine Coating System | Epoxy/Polyurethane | 150 | m² | $150 | $22,500 |
| Sacrificial Anodes | Aluminum Alloy | 12 | ea | $500 | $6,000 |

### Complete Cost Example - Subsea Tree

```java
// Create and configure tree
SubseaTree tree = new SubseaTree("Well-A Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setPressureRating(SubseaTree.PressureRating.PR15000);
tree.setBoreSizeInches(7.0);
tree.setWaterDepth(500.0);
tree.setDesignPressure(1034.0);
tree.initMechanicalDesign();

SubseaTreeMechanicalDesign design = 
    (SubseaTreeMechanicalDesign) tree.getMechanicalDesign();
design.setMaxOperationPressure(1034.0);
design.setRegion(SubseaCostEstimator.Region.NORWAY);
design.calcDesign();

// Display costs
System.out.println("=== Subsea Tree Cost Estimate ===");
System.out.println("Total Cost: $" + String.format("%,.0f", design.getTotalCostUSD()));
System.out.println("Equipment: $" + String.format("%,.0f", design.getEquipmentCostUSD()));
System.out.println("Installation: $" + String.format("%,.0f", design.getInstallationCostUSD()));
System.out.println("Vessel Days: " + design.getVesselDays());

// Full JSON report includes design AND costs
String json = design.toJson();
```

### Regional Cost Comparison

```java
// Compare costs across regions
double[] regionCosts = new double[5];
SubseaCostEstimator.Region[] regions = SubseaCostEstimator.Region.values();

for (int i = 0; i < regions.length; i++) {
    SubseaCostEstimator estimator = new SubseaCostEstimator(regions[i]);
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    regionCosts[i] = estimator.getTotalCost();
    
    System.out.println(regions[i].name() + ": $" + 
        String.format("%,.0f", regionCosts[i]));
}
```

Example output:
```
NORWAY: $3,450,000
UK: $3,210,000
GOM: $2,570,000
BRAZIL: $2,180,000
WEST_AFRICA: $2,820,000
```

### Cost Data Sources

Cost estimation uses data from CSV tables in `src/main/resources/designdata/`:

| File | Description |
|------|-------------|
| `SubseaCostEstimation.csv` | Base equipment costs, material costs per tonne |
| `SubseaLaborRates.csv` | Labor categories with hourly rates by region |
| `SubseaVesselRates.csv` | Vessel day rates, mob/demob costs |

### JSON Cost Output

The `toJson()` method includes comprehensive cost data:

```json
{
  "equipmentName": "Production PLET",
  "designStandard": "DNV-ST-F101",
  "materialGrade": "X65",
  "hubWallThickness_mm": 15.2,
  "requiredMudmatArea_m2": 25.0,
  "maxBearingPressure_kPa": 50.0,
  "costEstimation": {
    "region": "NORWAY",
    "currency": "USD",
    "directCosts": {
      "equipmentCostUSD": 1250000,
      "fabricationCostUSD": 375000,
      "installationCostUSD": 980000
    },
    "indirectCosts": {
      "engineeringCostUSD": 125000,
      "projectManagementCostUSD": 62500
    },
    "installationBreakdown": {
      "vesselCostUSD": 750000,
      "vesselDays": 2.5,
      "vesselDayRateUSD": 300000,
      "rovHours": 30
    },
    "contingencyUSD": 419625,
    "totalCostUSD": 3212125
  },
  "laborEstimate": {
    "engineeringManhours": 1200,
    "fabricationManhours": 2500,
    "installationManhours": 800,
    "totalManhours": 4500
  }
}
```

---

## Complete SURF System Example

```java
// Create fluid system
SystemInterface fluid = new SystemSrkEos(323.15, 150.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.05);
fluid.setMixingRule("classic");

// Well stream
Stream wellStream = new Stream("Well-1", fluid);
wellStream.setFlowRate(100000, "kg/hr");
wellStream.run();

// Subsea tree
SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setPressureRating(SubseaTree.PressureRating.PR10000);
tree.setChokePosition(80.0);
tree.run();

// Jumper to manifold
SubseaJumper jumper = new SubseaJumper("Tree-Manifold Jumper", tree.getOutletStream());
jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
jumper.setLength(50.0);
jumper.run();

// Manifold
SubseaManifold manifold = new SubseaManifold("Field Manifold");
manifold.setManifoldType(SubseaManifold.ManifoldType.PRODUCTION_TEST);
manifold.setNumberOfWellSlots(4);
manifold.addWellStream(jumper.getOutletStream(), 1);
manifold.routeWellToProduction(1);
manifold.run();

// Export PLET
PLET exportPLET = new PLET("Export PLET", manifold.getProductionOutputStream());
exportPLET.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
exportPLET.run();

// Flexible riser
FlexiblePipe riser = new FlexiblePipe("Production Riser", exportPLET.getOutletStream());
riser.setPipeType(FlexiblePipe.PipeType.UNBONDED);
riser.setApplication(FlexiblePipe.Application.DYNAMIC_RISER);
riser.setRiserConfiguration(FlexiblePipe.RiserConfiguration.LAZY_WAVE);
riser.setLength(1200.0);
riser.run();

// Control umbilical
Umbilical umbilical = new Umbilical("Field Umbilical");
umbilical.setLength(15000.0);
umbilical.addHydraulicLine(12.7, 517.0, "HP Supply");
umbilical.addHydraulicLine(12.7, 517.0, "HP Return");
umbilical.addChemicalLine(25.4, 207.0, "MEG");
umbilical.run(null);

// Add all to process system
ProcessSystem process = new ProcessSystem();
process.add(wellStream);
process.add(tree);
process.add(jumper);
process.add(manifold);
process.add(exportPLET);
process.add(riser);

process.run();
```

## JSON Output

All equipment provides JSON output for integration with other systems:

```java
SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setPressureRating(SubseaTree.PressureRating.PR10000);
tree.run();

// Equipment JSON
String equipmentJson = tree.toJson();

// Mechanical design JSON
tree.initMechanicalDesign();
tree.getMechanicalDesign().calcDesign();
String designJson = tree.getMechanicalDesign().toJson();
```

## See Also

- [Pipeline Mechanical Design](pipeline_design)
- [Process Equipment Overview](equipment_overview)
- [Field Development Module](../fielddevelopment/API_GUIDE)
