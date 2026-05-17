---
name: neqsim-subsea-and-wells
description: "Subsea production systems, well design, SURF cost estimation, and tieback analysis with NeqSim. USE WHEN: designing subsea fields, sizing flowlines and umbilicals, estimating well costs, performing casing design, running tieback comparisons, or configuring subsea equipment (trees, manifolds, boosters, risers)."
last_verified: "2026-07-04"
---

# NeqSim Subsea & Wells Skill

Reference for subsea production system design, well mechanical design, SURF cost
estimation, and tieback analysis using NeqSim.

---

## Subsea Development Architecture

A typical subsea development consists of:

```
Reservoir → Wells → Subsea Trees → Jumpers → Manifold → Flowlines → Riser → Host
                                                    ↑
                                            Umbilical (power, control, chemicals)
```

### Equipment Classes in NeqSim

| Equipment | NeqSim Class | Package |
|-----------|-------------|---------|
| Subsea well | `SubseaWell` | `process.equipment.subsea` |
| Christmas tree | `SubseaTree` | `process.equipment.subsea` |
| Manifold | `SubseaManifold` | `process.equipment.subsea` |
| Subsea booster | `SubseaBooster` | `process.equipment.subsea` |
| Jumper | `SubseaJumper` | `process.equipment.subsea` |
| Flowline | `SimpleFlowLine` | `process.equipment.subsea` |
| Flexible riser | `FlexiblePipe` | `process.equipment.subsea` |
| Umbilical | `Umbilical` | `process.equipment.subsea` |
| PLET | `PLET` | `process.equipment.subsea` |
| PLEM | `PLEM` | `process.equipment.subsea` |
| Floating production | `FloatingSubstructure` | `process.equipment.subsea` |
| Mooring | `MooringSystem` | `process.equipment.subsea` |

---

## Well Design

### Casing Design (API 5C3 / NORSOK D-010)

```java
SubseaWell well = new SubseaWell("Producer-1", stream);
well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
well.setCompletionType(SubseaWell.CompletionType.CASED_PERFORATED);
well.setRigType(SubseaWell.RigType.SEMI_SUBMERSIBLE);

// Geometry
well.setMeasuredDepth(3800.0);
well.setTrueVerticalDepth(3200.0);
well.setWaterDepth(350.0);
well.setMaxWellheadPressure(345.0);
well.setReservoirPressure(400.0);

// Casing program
well.setConductorOD(30.0);          well.setConductorDepth(100.0);
well.setSurfaceCasingOD(20.0);       well.setSurfaceCasingDepth(800.0);
well.setIntermediateCasingOD(13.375); well.setIntermediateCasingDepth(2500.0);
well.setProductionCasingOD(9.625);    well.setProductionCasingDepth(3800.0);
well.setTubingOD(5.5);
well.setTubingWeight(23.0);
well.setTubingGrade("L80");

// Barrier elements (NORSOK D-010 two-barrier principle)
well.setPrimaryBarrierElements(3);
well.setSecondaryBarrierElements(3);
well.setHasDHSV(true);

// Drilling schedule & costs
well.setDrillingDays(45.0);
well.setCompletionDays(25.0);
well.setRigDayRate(540000.0);

// Mechanical design
well.initMechanicalDesign();
WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
design.calcDesign();
design.calculateCostEstimate();

// Results
double burstDF = design.getProductionCasingBurstDF();     // >= 1.10
double collapseDF = design.getProductionCasingCollapseDF(); // >= 1.00
double tensionDF = design.getProductionCasingTensionDF();   // >= 1.60
boolean barrierOk = design.isBarrierVerificationPassed();
double totalCost = design.getTotalCostUSD();
String json = design.toJson();
```

### API 5CT Casing Grades

| Grade | SMYS (MPa) | Typical Use |
|-------|-----------|-------------|
| H40 | 276 | Conductor |
| K55 | 379 | Surface casing |
| N80 / L80 | 552 | Intermediate casing, tubing |
| C90 | 621 | Sour service (H2S) |
| P110 | 758 | Production casing (high pressure) |
| Q125 | 862 | Ultra-deep / HP-HT |

### NORSOK D-010 Design Factors

| Check | Minimum DF | Formula |
|-------|-----------|---------|
| Burst | 1.10 | DF = Burst_rating / (P_internal - P_external) |
| Collapse | 1.00 | DF = Collapse_rating / (P_external - P_internal) |
| Tension | 1.60 | DF = Yield_strength / Axial_load |
| Triaxial (VME) | 1.25 | Von Mises equivalent stress check |

---

## SURF Cost Estimation

### SURFCostEstimator

```java
SURFCostEstimator surf = new SURFCostEstimator();
surf.setRegion(SubseaCostEstimator.Region.NORWAY);
surf.setNumberOfWells(4);
surf.setFlowlineLength(25.0);       // km
surf.setUmbilicalLength(27.0);      // km
surf.setWaterDepth(350.0);          // m
surf.setTreeType("vertical");
surf.setHasManifold(true);
surf.setManifoldWells(4);
surf.setHasRiser(true);
surf.setRiserType("flexible");

double surfCapex = surf.estimate();  // USD
String breakdown = surf.toJson();    // Detailed cost breakdown
```

### Regional Cost Factors

| Region | Factor | Basis |
|--------|--------|-------|
| Norway (NCS) | 1.0 | Reference |
| UK (UKCS) | 0.85-0.95 | Lower labor cost |
| Gulf of Mexico | 0.80-0.90 | Established supply chain |
| Brazil (pre-salt) | 1.10-1.30 | Deep water, local content |
| West Africa | 1.05-1.20 | Logistics premium |

---

## Tieback Analysis

### Workflow

1. Define the satellite field (reservoir, fluid, wells)
2. Define candidate host facilities with available capacity
3. Configure tieback options (distance, diameter, insulation, boosting)
4. Screen flow assurance (hydrate margin, arrival temperature, pressure drop)
5. Estimate SURF CAPEX for each option
6. Rank by NPV or unit technical cost

### Usage

```java
TiebackAnalyzer analyzer = new TiebackAnalyzer();

// Define host
HostFacility host = new HostFacility("Platform Alpha");
host.setAvailableCapacity(30000.0);   // boe/d spare capacity
host.setProcessingPressure(70.0);     // bara
host.setLocation(61.5, 2.5);         // lat, lon

// Define options
TiebackOption opt1 = new TiebackOption("Direct Tieback");
opt1.setFlowlineLength(15.0);
opt1.setFlowlineDiameter(10.0);
opt1.setWaterDepth(350.0);
opt1.setInsulationType("wet_insulation");

TiebackOption opt2 = new TiebackOption("Boosted Tieback");
opt2.setFlowlineLength(30.0);
opt2.setFlowlineDiameter(12.0);
opt2.setWaterDepth(450.0);
opt2.setHasBooster(true);
opt2.setBoosterType("multiphase_pump");

analyzer.setHost(host);
analyzer.addOption(opt1);
analyzer.addOption(opt2);
analyzer.setFluid(reservoirFluid);

TiebackReport report = analyzer.analyze();
// report contains: pressure drop, arrival temperature, hydrate margin,
// SURF cost, NPV ranking, flow assurance verdict per option
```

---

## Subsea System Configuration

### Complete Subsea Layout

```java
SubseaProductionSystem subseaSystem = new SubseaProductionSystem("Field Layout");
subseaSystem.setNumberOfWells(6);
subseaSystem.setWaterDepth(400.0);
subseaSystem.setFlowlineLength(30.0);     // km to host
subseaSystem.setUmbilicalLength(32.0);    // slightly longer routing
subseaSystem.setRiserType("flexible");
subseaSystem.setTreeType("horizontal");
subseaSystem.setHasManifold(true);
subseaSystem.setManifoldWells(3);          // 2 manifolds × 3 wells
subseaSystem.setHasPigLoop(true);
subseaSystem.setHasInLineTee(false);
subseaSystem.setHasSubseaBooster(true);
subseaSystem.setBoosterType("compressor"); // or "pump"
```

---

## Flowline and Pipeline Sizing

### Steady-State Pipe Flow

```java
// Beggs & Brill multiphase correlation
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Export Line", feedStream);
pipeline.setLength(50000.0);           // m
pipeline.setDiameter(0.508);           // m (20 inch)
pipeline.setPipeWallRoughness(5e-5);   // m
pipeline.setAngle(0.0);               // horizontal
pipeline.setNumberOfIncrements(50);

// With formation temperature gradient (subsea/buried)
pipeline.setFormationTemperatureGradient(4.0, -0.03, "C");
// 4°C at seabed, -0.03 °C/m depth gradient

pipeline.run();
double pressureDrop = feedStream.getPressure() - pipeline.getOutletPressure();
double arrivalTemp = pipeline.getOutletTemperature() - 273.15;  // °C
```

### Pipeline Mechanical Design

```java
AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", feedStream);
pipe.setLength(50000.0);
pipe.setDiameter(0.508);

PipelineMechanicalDesign mechDesign =
    (PipelineMechanicalDesign) pipe.getMechanicalDesign();
mechDesign.setMaxOperationPressure(150.0);
mechDesign.setMaterialGrade("X65");
mechDesign.setDesignStandardCode("DNV-OS-F101");
mechDesign.calcDesign();

double wallThickness = mechDesign.getWallThickness();  // mm
String report = mechDesign.toJson();
```

---

## Artificial Lift Screening

```java
ArtificialLiftScreener alScreener = new ArtificialLiftScreener();
alScreener.setReservoirPressure(250.0);     // bara
alScreener.setWaterDepth(350.0);
alScreener.setGOR(200.0);                   // Sm3/Sm3
alScreener.setWaterCut(0.30);
alScreener.setDepth(3000.0);                // m TVD
alScreener.setProductionRate(5000.0);       // boe/d

// Screen all methods
Map<String, String> recommendations = alScreener.screen();
// Returns: {"ESP": "RECOMMENDED", "Gas Lift": "FEASIBLE",
//           "Rod Pump": "NOT_RECOMMENDED", ...}
```

---

## Gas Lift Design

```java
GasLiftCalculator gasLift = new GasLiftCalculator();
gasLift.setWellDepth(3000.0);
gasLift.setReservoirPressure(250.0);
gasLift.setProductionRate(5000.0);
gasLift.setGLR(500.0);                    // Sm3/Sm3
gasLift.setInjectionPressure(150.0);      // bara

double optimalGLR = gasLift.calculateOptimalGLR();
double injectionRate = gasLift.calculateInjectionRate();

// Multi-well gas lift optimization
GasLiftOptimizer optimizer = new GasLiftOptimizer();
optimizer.addWell(well1, gasLift1);
optimizer.addWell(well2, gasLift2);
optimizer.setTotalGasAvailable(500000.0);  // Sm3/d
Map<String, Double> allocation = optimizer.optimize();
```

---

## Design Standards Reference

| Domain | Standard | Used For |
|--------|---------|----------|
| Casing design | API 5CT / ISO 11960 | Casing/tubing grades, SMYS |
| Casing formulas | API Bull 5C3 / TR 5C3 | Burst, collapse, tension |
| Well barriers | NORSOK D-010 | Design factors, two-barrier principle |
| Submarine pipelines | DNV-ST-F101 | Wall thickness, on-bottom stability |
| Process piping | ASME B31.3 | Onshore/topsides piping |
| Pressure vessels | ASME VIII Div.1/2 | Separator, vessel sizing |
| Subsea production | API 17A-17Q | Subsea equipment specs |
| Risers | API 2RD / DNV-OS-F201 | Riser design |
| Flowlines | DNV-RP-F105 | Free-spanning pipelines |
| Fatigue | DNV-RP-C203 | S-N curves, fatigue life |

---

## Common Subsea Design Pitfalls

| Pitfall | Impact | Prevention |
|---------|--------|------------|
| Ignoring hydrate sub-cooling margin | Hydrate blockage | Design for 3-6°C subcooling below hydrate T |
| Undersized flowline (low rate sensitivity) | Cannot achieve design rate | Size for peak + 20% surge capacity |
| Missing umbilical in cost estimate | 10-15% CAPEX underestimate | Always include umbilical with routing factor 1.1× |
| Wrong water depth for cost curve | Non-physical costs | Verify depth matches field data |
| Ignoring slugging in riser | Separator flooding, trips | Include slug catcher sizing, check riser stability |
| No pipeline end expansion | Structural failure | Account for thermal expansion, expansion loops |
