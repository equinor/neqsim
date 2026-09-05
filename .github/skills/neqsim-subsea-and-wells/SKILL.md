---
name: neqsim-subsea-and-wells
description: "Subsea production systems, DNV-RP-F109 on-bottom stability screening, DNV-RP-F105 free-span screening, DNV-RP-F101 corroded-pipeline screening, well design, SURF cost estimation, and tieback analysis with NeqSim. USE WHEN: designing subsea fields, screening pipeline/cable/umbilical seabed stability or inspected metal loss, sizing flowlines and umbilicals, estimating well costs, performing casing design, running tieback comparisons, or configuring subsea equipment (trees, manifolds, boosters, risers)."
last_verified: "2026-08-02"
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
| Steel/rigid riser | `SimpleFlowLine` | `process.equipment.subsea` |
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
well.setWellLocationType(WellCostEstimator.WellLocationType.SUBSEA_WET_TREE);

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

### Dry vs Wet Wells

Use `WellCostEstimator.WellLocationType` to distinguish subsea wet-tree wells
from platform dry-tree wells. The same flag is carried by `SubseaWell` and fed
into `WellMechanicalDesign.calculateCostEstimate()`.

```java
SubseaWell wetWell = new SubseaWell("Subsea producer", stream);
wetWell.setWellLocationType(WellCostEstimator.WellLocationType.SUBSEA_WET_TREE);

SubseaWell dryWell = new SubseaWell("Platform producer", stream);
dryWell.setWellLocationType(WellCostEstimator.WellLocationType.PLATFORM_DRY_TREE);
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
surf.setWaterDepthM(350.0);          // m
surf.setTreePressureRatingPsi(10000);
surf.setTreeBoreSizeInches(6.0);
surf.setHorizontalTrees(true);
surf.setManifoldSlots(4);
surf.setNumberOfPLETs(2);
surf.setNumberOfPLEMs(1);
surf.setNumberOfJumpers(4);
surf.setJumperLengthM(30.0);
surf.setUmbilicalLengthKm(27.0);
surf.setUmbilicalHydraulicLines(8);
surf.setUmbilicalChemicalLines(2);
surf.setUmbilicalElectricalCables(2);
surf.setIncludeRisers(true);
surf.setFlexibleRiser(true);
surf.setRiserDiameterInches(12.0);
surf.setRiserLengthM(525.0);
surf.setInfieldFlowlineLengthKm(8.0);
surf.setExportPipelineLengthKm(25.0);
surf.setExportPipelineDiameterInches(12.0);
surf.setContingencyPct(0.35);        // FRACTION, not a percent - see the warning below

surf.calculate();
double surfCapex = surf.getTotalSURFCostUSD();
double subseaHardware = surf.getSubseaCostUSD();
double umbilicals = surf.getUmbilicalCostUSD();
double risers = surf.getRiserCostUSD();
double flowlines = surf.getFlowlineCostUSD();
List<Map<String, Object>> lineItems = surf.getLineItems();
```

### WARNING: `setContingencyPct` takes a FRACTION, not a percent

Despite the `Pct` in the name, both `SURFCostEstimator.setContingencyPct` and
`WellCostEstimator.setContingencyPct` multiply the subtotal by the value **as given**:

```java
est.setContingencyPct(30.0);   // WRONG - contingency = 30 x subtotal
est.setContingencyPct(0.30);   // RIGHT - contingency = 0.30 x subtotal
```

The symptom is a cost that is roughly 100x too large and still looks internally
consistent, e.g. a subsea gas well at 2 295 MUSD instead of 96 MUSD. Always sanity-check
the result against `getCostBreakdown()` / `getLineItems()`, where the reported
`contingencyPct` field shows the value actually applied.

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
subseaSystem.setArchitecture(SubseaProductionSystem.SubseaArchitecture.MANIFOLD_CLUSTER);
subseaSystem.setWellCount(6);
subseaSystem.setManifoldCount(2);          // 2 manifolds x 3 wells
subseaSystem.setWaterDepthM(400.0);
subseaSystem.setTiebackDistanceKm(30.0);   // km to host
subseaSystem.setUmbilicalLengthKm(32.0);   // slightly longer routing
subseaSystem.setFlowlineDiameterInches(12.0);
subseaSystem.setTubingDiameterInches(6.0);
subseaSystem.setCostRegion(SubseaCostEstimator.Region.NORWAY);
subseaSystem.setWellLocationType(WellCostEstimator.WellLocationType.SUBSEA_WET_TREE);
subseaSystem.setIncludeRisers(true);
subseaSystem.setFlexibleRiser(true);
subseaSystem.setProductionRiserCount(1);
subseaSystem.setReservoirDevelopmentCostMusd(25.0);
subseaSystem.setReservoirFluid(reservoirFluid);

subseaSystem.build();
subseaSystem.run();

int treeCount = subseaSystem.getTrees().size();
int jumperCount = subseaSystem.getJumpers().size();
int manifoldCount = subseaSystem.getManifolds().size();
int pletCount = subseaSystem.getPLETs().size();
int plemCount = subseaSystem.getPLEMs().size();
int umbilicalCount = subseaSystem.getUmbilicals().size();
int flexibleRiserCount = subseaSystem.getRisers().size();
int steelRiserCount = subseaSystem.getSteelRisers().size();

SubseaProductionSystem.SubseaSystemResult result = subseaSystem.getResult();
double surfCapexMusd = result.getTotalSubseaCapexMusd();
double wellsMusd = result.getWellCostMusd();
double reservoirMusd = result.getReservoirCostMusd();
double developmentCapexMusd = result.getTotalDevelopmentCapexMusd();
```

`SubseaProductionSystem.build()` creates the main process and design equipment:
`SubseaWell`, `SubseaTree`, `SubseaJumper`, `SubseaManifold`, `PLET`, `PLEM`,
`SimpleFlowLine`, `Umbilical`, and risers. With `setFlexibleRiser(true)` risers
are generated as `FlexiblePipe`; with `setFlexibleRiser(false)` steel/rigid
risers are generated as vertical `SimpleFlowLine` unit operations. All of these
classes expose mechanical design objects, and `SURFCostEstimator` includes trees,
manifolds, PLETs, PLEMs, jumpers, umbilicals, risers, flowlines, and pipelines in
the SURF CAPEX. The result separates SURF, well, reservoir, and total development
CAPEX.

---

## Flowline and Pipeline Sizing

### Size selection (API RP 14E)

Do not hand-roll the erosional sweep. `FlowlineSizeSelector` returns the smallest
standard size that passes, with every candidate and its utilisation:

```java
FlowlineSizeSelector selector = new FlowlineSizeSelector()
    .setBasisFromFluid(fluidAtArrivalConditions, 16.13)   // kg/s
    .setWallThickness(0.0159);
Map<String, Object> selected = selector.select();   // null when nothing passes
```

**Evaluate at the least-dense condition — normally the arrival, not the inlet.**
The erosional velocity is $v_e = 1.22c/\sqrt{\rho}$, so the lowest density gives
the lowest limit and the highest velocity. Sizing on inlet density under-sizes
the line.

### Use the two-fluid model for a wellstream

`PipeBeggsAndBrills` is a correlation fitted on small-diameter air-water
laboratory loops at liquid fractions around 1-2% and above. A wet-gas tie-back
runs well below that, where the two-phase friction multiplier is extrapolated
and over-predicts pressure drop substantially. **Prefer `TwoFluidPipe` for
wellstream and wet-gas lines**, and always assert both convergence conditions:

```java
pipe.run();
if (!pipe.isSteadyStateConverged() || pipe.getSteadyStateIterationsUsed() <= 1) {
  // A single-iteration exit means the pressure profile was frozen on the inlet
  // densities and never picked up the flash. Reject the result.
}
```

`MultiphaseFlowIntegrator` defaults to `HydraulicModel.TWO_FLUID`; switch to
`BEGGS_BRILL` only for a fast check on a liquid-dominated line.

### Route geometry from a survey

`RouteProfile` converts survey data once and feeds either model. Depths are
positive downwards as surveyed, elevations negative downwards as the models
expect:

```java
RouteProfile route = RouteProfile.fromDepths(kpMetres, depthMetres)
    .withRiser(216.0, 25.0)
    .resample(60);
route.applyTo(twoFluidPipe);            // sets sections, lengths, elevations
route.getLowPointKp();                  // terrain-slug traps
```

`getElevationProfile()` has one more entry than `getSectionLengths()` — that
off-by-one is what `TwoFluidPipe.setElevationProfile` requires.

### Solving for the required inlet pressure

The host imposes an arrival pressure; the question is what inlet the reservoir
must supply. Both pipe models march forward from a fixed inlet, so use the
solver rather than bisecting by hand:

```java
pipe.setCalculationMode(PipeBeggsAndBrills.CalculationMode.CALCULATE_INLET_PRESSURE);
pipe.setOutletPressure(70.0, "bara");
pipe.run();
double requiredInlet = pipe.getSolvedInletPressure();
```

### Shut-in wellhead pressure sets the design pressure

Start the containment design here, not at the flowing pressure:

```java
double shutIn = well.calculateShutInWellheadPressure(reservoirFluid, 40.0, 60);
```

The static column is re-flashed at each step so density follows pressure and
temperature; a single-density estimate over-predicts the drop badly for gas. The
result decides whether the line is rated for full shut-in or protected by HIPPS
— and that choice feeds straight into the cooldown design below.

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

### Free-span screening (DNV-RP-F105)

For an explicit current `DNV-RP-F105 2025-12` basis, use
`DnvRpF105FreeSpanScreeningKernel`. Supply surveyed span/pipe geometry, a separate hydrodynamic
diameter, accepted effective modal mass and axial force, normal current/wave inputs, and verified
project response triggers. The kernel reports a simply supported first-mode frequency and common
dimensionless groups only.

Do not use `PipeMechanicalDesignCalculator.calculateAllowableSpanLength(...)` as F105 evidence. It
is a legacy fixed-assumption estimate with fallback/cap behavior. Do not turn the typed kernel's
caller-controlled response triggers into PASS/FAIL against DNV: soil and span-shoulder stiffness,
multi-span interaction, detailed VIV/direct-wave response, ULS/FLS, fatigue, monitoring, and
intervention remain a controlled external assessment.

### Corroded-pipeline screening (DNV-RP-F101)

For an explicit current `DNV-RP-F101 2019-09+AMD:2025-09` basis, use
`DnvRpF101CorrodedPipelineScreeningKernel` only for one verified isolated longitudinal metal-loss
defect under internal pressure. Supply assessment wall thickness, measured maximum depth and axial
length, caller-controlled depth allowance, characteristic ultimate tensile strength,
internal/external absolute pressures, and a verified project-controlled pressure factor.

Keep interacting/complex defects, combined longitudinal compression, probabilistic and inspection-
accuracy models, corrosion growth, crack/dent/gouge/blister or weld damage, repair, inspection
interval, and fitness-for-service approval external. M-506 rate prediction is not inspected defect
sizing. RP-F101 does not replace DNV-ST-F101 pressure containment, collapse, propagation/local
buckling, interaction, fatigue, pressure cases, de-rating, safety class, ovality, fabrication, or
installation-strain checks.

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

### DNV-RP-F109 On-Bottom Stability Screening

Use `DnvRpF109OnBottomStabilityKernel` for typed, fail-closed vertical and lateral
screening of a pipeline, cable, or umbilical. The exact supported edition is
`2021-05+AMD 2025-09`. Supply every project coefficient, factor, soil resistance,
environmental load case, and submerged weight explicitly; there are no numerical
project defaults.

Build `DnvRpF109OnBottomStabilityInput` with the exact edition, matching asset and
equipment types, geometry, engineering-basis reference, and one or more explicit
`LoadCase` values. Call `DnvRpF109OnBottomStabilityKernel.calculate(input,
context)` and retain its readiness findings and full input provenance. See
`docs/process/dnv_rp_f109_on_bottom_stability.md` for the complete Java pattern.

The absolute-static route calculates normal Morison drag/inertia and lift, then
checks vertical equilibrium and horizontal demand against friction plus explicit
passive soil resistance. External-response routes check supplied displacement at
0.5D, 10D, or a project limit, and require affirmative response-model validity plus
a traceable basis. NeqSim does not reproduce generalized design tables, generate
dynamic response, qualify pipe-soil inputs, or claim DNV conformity. Treat every
result, including a pass, as `CALCULATED_REVIEW_REQUIRED`.

---

## Flowline Cooldown and No-Touch Time

### CRITICAL: the fluid must carry water

A fluid with no water cannot form hydrates, so `SurfCooldownAnalyzer` reports
`NO_HYDRATE_RISK` with an **unbounded** no-touch time. Correct for a deliberately
dry gas — and identical to what a **wet** line gives when its fluid file has no
water component. The verdict cannot separate the two, so check explicitly:

```java
analyzer.isWaterPresent();        // false for a dry gas AND for the wrong file
analyzer.setRequireWater(true);   // design workflows: throw instead
```

`TiebackThermalDesign` sets `setRequireWater(true)` by default.

Add water through NeqSim's own path, which sets the water kij and enables the
VLLE flash — **not** a bare `addComponent`:

```java
SystemInterface wet = EclipseFluidReadWrite.read(file, true);     // kij 0.5
EclipseFluidReadWrite.addWaterToFluid(existingFluid, 0.5);        // in place
```

And never call `setMixingRule` after `EclipseFluidReadWrite.read`: `read`
installs the mixing rule and the file's BIC block, and `setMixingRule` reloads
database kij, silently discarding the characterisation's regressed values.

### Wall thickness and insulation are coupled

The steel wall is part of the cooldown thermal mass. Thinning the wall — typically
by crediting HIPPS so the line need not be rated for full shut-in — removes
stored heat and **lengthens** the insulation needed for the same no-touch time.

A worked case: full shut-in required a 22.2 mm wall, and 75 mm of insulation
comfortably exceeded an 8 h target. HIPPS dropped the wall to 14.3 mm, and the
same 75 mm then gave only 7.9 h. Use `TiebackThermalDesign` to sweep both
together rather than fixing one and then the other:

```java
TiebackThermalDesign design = new TiebackThermalDesign(wetFluid)
    .setWallThicknesses(new double[] {0.0143, 0.0222})
    .setInsulationThicknesses(new double[] {0.050, 0.075, 0.100})
    .setRequiredNoTouchTime(8.0);
design.calculate();
double insulation = design.getRequiredInsulationForWall(14.3);
```

After a planned or unplanned shutdown, an insulated subsea flowline or riser
cools toward the seabed temperature. The **no-touch time** is how long operators
can wait before the fluid reaches the hydrate formation temperature (plus a
safety margin) and remedial action (depressurization, MEG/methanol injection) is
required. NeqSim couples a live fluid to a lumped cooldown engine with
`SurfCooldownAnalyzer`.

```java
// Live fluid carries composition; analyzer auto-extracts density, Cp, hydrate Teq
SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(fluid);  // SystemInterface
analyzer.setInternalDiameter(0.254);        // m
analyzer.setWallThickness(0.0159);          // m
analyzer.setInsulationThickness(0.060);     // m
analyzer.setInsulationConductivity(0.17);   // W/m·K (wet insulation, PP foam)
analyzer.setSeabedTemperature(4.0);         // °C
analyzer.setHydrateMargin(3.0);             // K above hydrate Teq
analyzer.setRequiredNoTouchTimeHours(8.0);  // operational target (optional)

// Either give an overall U-value directly, or let the layer model compute it:
analyzer.setOverallUValue(2.5);             // W/m²·K  (skip for layer calc)

analyzer.calculate();
double noTouch = analyzer.getNoTouchTimeHours();
String verdict = analyzer.getVerdict();     // OK / MARGINAL / CRITICAL / NO_HYDRATE_RISK
double hydrateTeqC = analyzer.getHydrateEquilibriumTemperatureK() - 273.15;
double tau = analyzer.getTimeConstantHours();
String json = analyzer.toJson();
```

**How it works:**
- Clones the fluid, runs `TPflash` + `initProperties`, and reads `getDensity("kg/m3")`
  and `getCp("J/kgK")` for the lumped thermal mass.
- Computes the hydrate equilibrium temperature via `hydrateFormationTemperature()`.
  If the fluid has no free water (no hydrate risk), the verdict is `NO_HYDRATE_RISK`.
- Delegates the transient to `PipelineCooldownCalculator` (exponential lumped
  cooldown, layer or direct U-value). No-touch time is the time to reach
  `hydrateTeq + margin`.
- Verdict bands: with a required no-touch time, `OK` ≥ required, `MARGINAL` ≥ 0.75×,
  else `CRITICAL`. Without one, `OK` ≥ 12 h, `MARGINAL` ≥ 6 h, else `CRITICAL`.

**Basis:** project thermal-management requirements and API RP 17A subsea-system
context. DNV-RP-F109 is an on-bottom stability document and is not a cooldown or
no-touch-time basis. This is a screening-level lumped model — use a distributed
transient thermal-hydraulic tool for detailed design.

`package`: `neqsim.pvtsimulation.flowassurance` —
`SurfCooldownAnalyzer`, `PipelineCooldownCalculator`.

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

> For a *choke-back / open-up* decision across a well fleet under **multiple** shared
> facility ceilings (gas handling + produced-water/PWRI + lift-gas budget) with discrete
> on/off locks, use `ChokeAndGasLiftAllocationOptimizer` + `StrupeOkeReport`
> (`neqsim.process.fielddevelopment.integrated`). Build each well's response with
> `GasLiftPerformanceCurve.fromWellSystem(...)`. See the `neqsim-production-optimization`
> skill for the full pattern.

---

## Design Standards Reference

| Domain | Standard | Used For |
|--------|---------|----------|
| Casing design | API 5CT / ISO 11960 | Casing/tubing grades, SMYS |
| Casing formulas | API Bull 5C3 / TR 5C3 | Burst, collapse, tension |
| Well barriers | NORSOK D-010 | Design factors, two-barrier principle |
| Submarine pipelines | DNV-ST-F101 | Pressure containment and structural limit states |
| On-bottom stability | DNV-RP-F109 | Vertical stability, absolute lateral stability, displacement acceptance |
| Process piping | ASME B31.3 | Onshore/topsides piping |
| Pressure vessels | ASME VIII Div.1/2 | Separator, vessel sizing |
| Subsea production | API 17A-17Q | Subsea equipment specs |
| Risers | API 2RD / DNV-OS-F201 | Riser design |
| Flowlines | DNV-RP-F105 2025-12 | Use `DnvRpF105FreeSpanScreeningKernel` for first-mode/dimensionless escalation screening; retain detailed response and acceptance externally |
| Corroded flowlines/risers | DNV-RP-F101 2019-09+AMD:2025-09 | Use `DnvRpF101CorrodedPipelineScreeningKernel` only for verified isolated longitudinal metal loss under internal pressure; retain full integrity assessment and ST-F101 design checks externally |
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
| Cooldown run on a dry E300 fluid | `NO_HYDRATE_RISK` and unbounded no-touch time — indistinguishable from a genuinely dry gas | `EclipseFluidReadWrite.read(file, true)`; check `isWaterPresent()`; `setRequireWater(true)` in design |
| `setMixingRule` after `EclipseFluidReadWrite.read` | Silently wipes the file BIC block and the regressed kij | Never call it after `read`; `read` installs the mixing rule already |
| Beggs & Brill on a wet-gas tie-back | Pressure drop over-predicted; concept looks infeasible | Use `TwoFluidPipe`; assert `getSteadyStateIterationsUsed() > 1` |
| Sizing the flowline on inlet density | Under-sized line — velocity peaks where the mixture is least dense | Evaluate API RP 14E at the arrival condition |
| Designing wall and insulation independently | Thin HIPPS wall silently fails the no-touch target | `TiebackThermalDesign` sweeps both together |
| Assuming the shut-in wellhead pressure | Wrong flowline design pressure, wrong wall | `SubseaWell.calculateShutInWellheadPressure(fluid)` |
