# Field Development Framework Documentation

This folder contains comprehensive documentation for NeqSim's field development capabilities, enabling the creation of **digital field twins** that provide consistency from exploration through decommissioning.

---

## Overview Documents

| Document | Description |
|----------|-------------|
| [DIGITAL_FIELD_TWIN.md](DIGITAL_FIELD_TWIN.md) | **Start here!** Architecture showing how NeqSim integrates all lifecycle phases |
| [MATHEMATICAL_REFERENCE.md](MATHEMATICAL_REFERENCE.md) | Mathematical foundations for all calculations (EoS, economics, flow) |
| [API_GUIDE.md](API_GUIDE.md) | Detailed usage examples for every class and method |

---

## The Digital Field Twin Concept

NeqSim's strength is providing **calculation consistency** across the entire field lifecycle:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      DIGITAL FIELD TWIN LIFECYCLE                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  DEVELOPMENT                    OPERATIONS                  LATE-LIFE   │
│  ───────────                    ──────────                  ─────────   │
│                                                                          │
│  ┌─────────┐  ┌─────────┐  ┌───────────┐  ┌────────────┐  ┌──────────┐ │
│  │ Concept │→ │ Select  │→ │  Design   │→ │  Optimize  │→ │ Decom-   │ │
│  │Screening│  │& MCDA   │  │& Execute  │  │& Operate   │  │ mission  │ │
│  └─────────┘  └─────────┘  └───────────┘  └────────────┘  └──────────┘ │
│       │            │             │              │              │        │
│       ▼            ▼             ▼              ▼              ▼        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                SAME THERMODYNAMIC FOUNDATION                      │  │
│  │  • Same fluid (SystemInterface) throughout lifecycle             │  │
│  │  • Same EoS parameters tuned once, used everywhere               │  │
│  │  • Consistent properties from reservoir to export                │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Key Integration Points

### 1. PVT ↔ Process Integration
The same `SystemInterface` fluid flows through wells, separators, compressors, and pipelines:

```java
// Create fluid once with tuned parameters
SystemInterface reservoir = new SystemSrkCPAstatoil(95, 320);
reservoir.addComponent("methane", 0.70);
// ... configure and tune ...

// Same fluid used throughout
Stream wellStream = new Stream("well", reservoir.clone());
Separator sep = new ThreePhaseSeparator("sep", wellStream);
// Properties remain consistent
```

### 2. Reservoir ↔ Facilities Integration
VFP tables ensure the same thermodynamics apply in both domains:

```java
ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(processModel);
exporter.generateVfpProd(1, "PROD-A1");
exporter.exportToFile("vfp.inc", ExportFormat.ECLIPSE_100);
// Reservoir simulator now uses NeqSim-consistent thermodynamics
```

### 3. Economics ↔ Technical Integration
Decision support tools use process simulation results directly:

```java
ConceptEvaluator evaluator = new ConceptEvaluator();
ConceptKPIs kpis = evaluator.evaluate(concept);
// Economics (NPV, IRR) derived from technical (production, utilities)
```

---

## Package Structure

```
neqsim.process.fielddevelopment/
├── concept/           # Core data structures (FieldConcept, ReservoirInput, etc.)
├── economics/         # NPV, tax, portfolio optimization
│   ├── CashFlowEngine
│   ├── NorwegianTaxModel
│   └── PortfolioOptimizer
├── evaluation/        # Decision support
│   ├── ConceptEvaluator
│   ├── DevelopmentOptionRanker
│   └── MonteCarloRunner
├── facility/          # Process generation
│   ├── ConceptToProcessLinker
│   └── FacilityBuilder
├── network/           # Pipeline network
│   ├── MultiphaseFlowIntegrator
│   └── NetworkSolver
├── reservoir/         # Reservoir coupling
│   ├── ReservoirCouplingExporter
│   └── TransientWellModel
├── screening/         # Technical screening
│   ├── FlowAssuranceScreener
│   ├── ArtificialLiftScreener
│   └── EmissionsTracker
├── subsea/            # Subsea systems
│   └── SubseaProductionSystem
└── tieback/           # Tieback analysis
    ├── TiebackAnalyzer
    └── HostFacility
```

---

## Quick Start Examples

### Evaluate a Field Concept
```java
import neqsim.process.fielddevelopment.concept.*;
import neqsim.process.fielddevelopment.evaluation.*;

FieldConcept concept = FieldConcept.oilDevelopment("My Field", 100.0, 8, 5000.0);
ConceptEvaluator evaluator = new ConceptEvaluator();
evaluator.setOilPrice(75.0);
ConceptKPIs kpis = evaluator.evaluate(concept);

System.out.println("NPV: " + kpis.getNpv() + " MUSD");
System.out.println("IRR: " + kpis.getIrr() * 100 + "%");
System.out.println("CO2 Intensity: " + kpis.getCo2Intensity() + " kg/boe");
```

### Compare Development Options
```java
import neqsim.process.fielddevelopment.evaluation.*;

DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();

DevelopmentOption fpso = ranker.addOption("FPSO");
fpso.setScore(Criterion.NPV, 1200.0);
fpso.setScore(Criterion.CO2_INTENSITY, 12.0);

DevelopmentOption tieback = ranker.addOption("Tieback");
tieback.setScore(Criterion.NPV, 650.0);
tieback.setScore(Criterion.CO2_INTENSITY, 7.0);

ranker.setWeightProfile("balanced");
RankingResult result = ranker.rank();
System.out.println("Recommended: " + result.getRankedOptions().get(0).getName());
```

### Generate Process Model from Concept
```java
import neqsim.process.fielddevelopment.facility.*;

ConceptToProcessLinker linker = new ConceptToProcessLinker();
ProcessSystem process = linker.generateProcessSystem(concept, FidelityLevel.PRE_FEED);
process.run();

double powerMW = linker.getTotalPowerMW(process);
System.out.println("Total Power Required: " + powerMW + " MW");
```

### Estimate SURF Costs
```java
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;

// Create estimator with regional factors (Norway, UK, GOM, Brazil, West Africa)
SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);

// Calculate SURF equipment costs
estimator.calculateTreeCost(10000.0, 7.0, 380.0, true, false);
System.out.println("Subsea Tree: $" + String.format("%,.0f", estimator.getTotalCost()));

estimator.calculateManifoldCost(6, 80.0, 380.0, true);
System.out.println("Manifold: $" + String.format("%,.0f", estimator.getTotalCost()));

estimator.calculateUmbilicalCost(48.0, 4, 3, 2, 380.0, false);
System.out.println("Umbilical: $" + String.format("%,.0f", estimator.getTotalCost()));

estimator.calculateFlexiblePipeCost(1200.0, 8.0, 380.0, true, true);
System.out.println("Dynamic Riser: $" + String.format("%,.0f", estimator.getTotalCost()));
```

---

## SURF Equipment Classes

NeqSim provides comprehensive SURF (Subsea, Umbilical, Riser, Flowline) modeling in `neqsim.process.equipment.subsea`:

| Class | Description |
|-------|-------------|
| `SubseaTree` | Christmas tree for well control (horizontal/vertical) |
| `SubseaManifold` | Production/test/injection routing with well slots |
| `PLET` | Pipeline End Termination structures |
| `PLEM` | Pipeline End Manifold with multiple connections |
| `SubseaJumper` | Rigid or flexible inter-equipment connections |
| `Umbilical` | Control, power, and chemical injection lines |
| `FlexiblePipe` | Dynamic risers and static flowlines |
| `SubseaBooster` | Multiphase pumps and wet gas compressors |

Each equipment type has a dedicated mechanical design class with:
- Wall thickness and structural calculations
- Design standard compliance (DNV, API, NORSOK)
- Regional cost estimation
- Bill of materials generation
- JSON export for reporting

See [SURF Subsea Equipment Guide](../process/SURF_SUBSEA_EQUIPMENT.md) for detailed documentation.

---

## Related Documentation

| Topic | Document |
|-------|----------|
| SURF Subsea Equipment | [SURF_SUBSEA_EQUIPMENT.md](../process/SURF_SUBSEA_EQUIPMENT.md) |
| Late-Life Operations | [LATE_LIFE_OPERATIONS.md](LATE_LIFE_OPERATIONS.md) |
| Field Development Strategy | [FIELD_DEVELOPMENT_STRATEGY.md](FIELD_DEVELOPMENT_STRATEGY.md) |
| Integrated Framework | [INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md) |

---

## See Also

- [Process Simulation Guide](../wiki/process_simulation.md)
- [Thermodynamic Models](../thermo/thermodynamic_models.md)
- [Economics Module](../process/economics/README.md)
- [Reference Manual Index](../REFERENCE_MANUAL_INDEX.md)
