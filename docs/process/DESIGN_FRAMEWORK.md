# NeqSim Design Framework

The Design Framework provides an integrated workflow for automated equipment sizing, process template-based design, and production optimization. This document describes the key components and usage patterns.

## Related Documentation

| Document | Description |
|----------|-------------|
| [OPTIMIZATION_IMPROVEMENT_PROPOSAL.md](OPTIMIZATION_IMPROVEMENT_PROPOSAL.md) | Implementation roadmap and status |
| [PRODUCTION_OPTIMIZATION_GUIDE.md](../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | Production optimization examples |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](CAPACITY_CONSTRAINT_FRAMEWORK.md) | Multi-constraint equipment framework |
| [process_design_guide.md](process_design_guide.md) | Complete process design workflow |
| [mechanical_design.md](mechanical_design.md) | Mechanical design integration |

## Overview

The design framework consists of several integrated components:

| Component | Purpose |
|-----------|---------|
| `AutoSizeable` | Interface for equipment that can auto-size based on flow |
| `DesignSpecification` | Builder class for equipment configuration |
| `ProcessTemplate` | Interface for reusable process templates |
| `ProcessBasis` | Design basis with feed conditions and constraints |
| `EquipmentConstraintRegistry` | Registry of default constraint templates |
| `DesignOptimizer` | Integrated design-to-optimization workflow |
| `DesignResult` | Container for optimization results |

## Quick Start

### Basic Auto-Sizing Example

```java
// Create a fluid and feed stream
SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(20000.0, "kg/hr");
feed.setTemperature(30.0, "C");
feed.setPressure(80.0, "bara");

// Create separator and auto-size it
Separator sep = new Separator("HP-Sep", feed);
sep.setDesignGasLoadFactor(0.08);  // K-factor
sep.autoSize(1.2);  // 20% safety factor

// Get sizing report
System.out.println(sep.getSizingReport());
```

### Using Design Specifications

```java
// Create design spec with fluent builder
DesignSpecification spec = DesignSpecification.forSeparator("HP-Separator")
    .setKFactor(0.08)
    .setDiameter(2.5, "m")
    .setLength(7.5, "m")
    .setMaterial("316L")
    .setStandard("ASME-VIII")
    .setSafetyFactor(1.25);

// Apply to equipment
spec.applyTo(separator);
```

### Using Process Templates

```java
// Define design basis
ProcessBasis basis = ProcessBasis.builder()
    .setFeedFluid(myOilGasFluid)
    .setFeedFlowRate(50000.0, "kg/hr")
    .setFeedPressure(85.0, "bara")
    .setFeedTemperature(50.0, "C")
    .addStagePressure(1, 80.0, "bara")  // HP
    .addStagePressure(2, 20.0, "bara")  // MP
    .addStagePressure(3, 2.0, "bara")   // LP
    .setCompanyStandard("Equinor", "TR2000")
    .build();

// Create process from template
ProcessTemplate template = new ThreeStageSeparationTemplate();
ProcessSystem process = template.create(basis);
process.run();
```

### Integrated Design and Optimization

```java
// Full workflow: template → auto-size → optimize
DesignOptimizer optimizer = DesignOptimizer.fromTemplate(template, basis)
    .autoSizeEquipment(1.2)
    .applyDefaultConstraints()
    .setObjective(DesignOptimizer.ObjectiveType.MAXIMIZE_PRODUCTION);

DesignResult result = optimizer.optimize();

if (result.isConverged()) {
    System.out.println(result.getSummary());
}
```

## Component Details

### AutoSizeable Interface

Equipment that implements `AutoSizeable` can automatically calculate their dimensions based on flow conditions.

**Implemented by:**
- `Separator` - Sizes based on gas load factor (K-factor) and liquid residence time
- `ThreePhaseSeparator` - Inherits from Separator with three-phase handling
- `GasScrubber` - Inherits from Separator, defaults to vertical orientation
- `ThrottlingValve` - Sizes based on Cv calculation using IEC 60534
- `PipeBeggsAndBrills` - Sizes based on target velocity criteria
- `Heater` - Sizes based on duty requirements with mechanical design
- `Cooler` - Inherits from Heater
- `HeatExchanger` - Sizes based on duty, UA value, and LMTD with two-stream support
- `Manifold` - Sizes based on velocity limits, FIV analysis, and erosional velocity

**Methods:**
```java
void autoSize(double safetyFactor);  // Size with specified margin
void autoSize();                      // Size with default 20% margin
void autoSize(String company, String tr);  // Size per company standard
boolean isAutoSized();                // Check if auto-sized
String getSizingReport();             // Get text report
String getSizingReportJson();         // Get JSON report
```

### DesignSpecification

Builder pattern class for standardized equipment configuration.

**Factory Methods:**
- `forSeparator(name)` - Separator configuration
- `forValve(name)` - Valve configuration
- `forPipeline(name)` - Pipeline configuration
- `forHeater(name)` - Heater configuration
- `forCompressor(name)` - Compressor configuration

**Common Settings:**
```java
DesignSpecification spec = DesignSpecification.forSeparator("HP-Sep")
    .setMaterial("316L")           // Material grade
    .setStandard("ASME-VIII")      // Design standard
    .setTRDocument("TR2000")       // Technical requirement
    .setSafetyFactor(1.25)         // Design margin
    .setCompanyStandard("Equinor"); // Company name
```

**Equipment-Specific:**
```java
// Separator
spec.setKFactor(0.08);
spec.setDiameter(2.5, "m");
spec.setLength(7.5, "m");

// Valve  
spec.setCv(150.0);
spec.setMaxValveOpening(90.0);

// Pipeline
spec.setMaxVelocity(15.0, "m/s");
spec.setMaxPressureDrop(5.0, "bar");

// Heater
spec.setMaxDuty(5.0, "MW");

// Compressor
spec.setMaxSpeed(12000.0);
spec.setMinSurgeMargin(10.0);
```

### ProcessBasis

Contains design basis information including feed conditions, stage pressures, and company standards.

```java
ProcessBasis basis = ProcessBasis.builder()
    // Feed conditions
    .setFeedFluid(fluid)
    .setFeedFlowRate(50000.0, "kg/hr")
    .setFeedPressure(85.0, "bara")
    .setFeedTemperature(50.0, "C")
    
    // Stage pressures
    .addStagePressure(1, 80.0, "bara")
    .addStagePressure(2, 20.0, "bara")
    .addStagePressure(3, 2.0, "bara")
    
    // Company standards
    .setCompanyStandard("Equinor", "TR2000")
    .setSafetyFactor(1.15)
    
    // Ambient conditions
    .setAmbientTemperature(15.0, "C")
    
    .build();
```

### EquipmentConstraintRegistry

Singleton registry of default constraint templates by equipment type.

```java
EquipmentConstraintRegistry registry = EquipmentConstraintRegistry.getInstance();

// Get templates for equipment type
List<ConstraintTemplate> sepConstraints = registry.getConstraintTemplates("Separator");

// Available templates by type:
// Separator: gasLoadFactor, liquidResidenceTime
// Compressor: surgeLine, stonewallLine, maxSpeed, maxPower
// Valve: maxOpening, maxCv
// Pipeline: maxVelocity, maxPressureDrop, fivLOF
// Heater: maxDuty, maxOutletTemperature
```

### ProcessTemplate Interface

Interface for creating reusable process configurations.

**Available Templates:**
- `ThreeStageSeparationTemplate` - HP/MP/LP separation train

**Methods:**
```java
ProcessSystem create(ProcessBasis basis);  // Create process
boolean isApplicable(SystemInterface fluid);  // Check applicability
String[] getRequiredEquipmentTypes();  // Equipment types used
String[] getExpectedOutputs();  // Output stream descriptions
String getName();  // Template name
String getDescription();  // Template description
```

### DesignOptimizer

Integrated workflow manager for design and optimization.

```java
// Create from existing process
DesignOptimizer optimizer = DesignOptimizer.forProcess(myProcess);

// Or create from template
DesignOptimizer optimizer = DesignOptimizer.fromTemplate(template, basis);

// Configure workflow
optimizer
    .autoSizeEquipment(1.2)       // Auto-size all AutoSizeable equipment
    .applyDefaultConstraints()     // Apply registry constraints
    .setObjective(ObjectiveType.MAXIMIZE_PRODUCTION);

// Run
DesignResult result = optimizer.validate();  // Just validate
DesignResult result = optimizer.optimize();  // Full optimization
```

**Objective Types:**
- `MAXIMIZE_PRODUCTION` - Maximize total hydrocarbon production
- `MAXIMIZE_OIL` - Maximize oil production
- `MAXIMIZE_GAS` - Maximize gas production
- `MINIMIZE_ENERGY` - Minimize energy consumption
- `CUSTOM` - Custom objective function

### DesignResult

Container for design and optimization results.

```java
DesignResult result = optimizer.optimize();

// Check convergence
if (result.isConverged()) {
    // Get metrics
    int iterations = result.getIterations();
    double objective = result.getObjectiveValue();
    
    // Get optimized values
    double gasFlow = result.getOptimizedFlowRate("Export Gas");
    
    // Get equipment sizes
    Map<String, Double> sizes = result.getEquipmentSizes("HP-Separator");
    double diameter = sizes.get("diameter");
    
    // Check constraints
    boolean violated = result.hasViolations();
    List<String> warnings = result.getWarnings();
    
    // Get summary report
    String summary = result.getSummary();
}
```

## Best Practices

### 1. Always Set Design Basis First

```java
// Create comprehensive design basis
ProcessBasis basis = ProcessBasis.builder()
    .setFeedFluid(fluid)
    .setFeedFlowRate(rate, "kg/hr")
    .setFeedPressure(pressure, "bara")
    .setFeedTemperature(temp, "C")
    .setSafetyFactor(1.2)
    .build();
```

### 2. Use Templates for Standard Configurations

```java
// Use pre-built templates for common configurations
ProcessTemplate template = new ThreeStageSeparationTemplate();
if (template.isApplicable(myFluid)) {
    ProcessSystem process = template.create(basis);
}
```

### 3. Apply Company Standards

```java
// Company-specific standards are used for sizing
separator.autoSize("Equinor", "TR2000");
```

### 4. Always Validate Before Optimization

```java
// Validate first to catch configuration issues
DesignResult validation = optimizer.validate();
if (!validation.hasViolations()) {
    DesignResult result = optimizer.optimize();
}
```

### 5. Review Sizing Reports

```java
// Check auto-sizing results
System.out.println(separator.getSizingReport());
System.out.println(valve.getSizingReportJson());
```

## Integration with Mechanical Design System

The AutoSizeable interface connects to NeqSim's comprehensive mechanical design system, which includes design standards, material databases, and company-specific technical requirements.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    AutoSizeable Interface                       │
│  autoSize(company, trDocument) ─────────────────────────────────┤
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MechanicalDesign                             │
│  - setCompanySpecificDesignStandards(company)                   │
│  - readDesignSpecifications() ← loads from database             │
│  - calcDesign() ← applies standards                             │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Design Standards (designstandards/)                │
│  ┌────────────────────┐  ┌────────────────────┐                │
│  │ SeparatorDesign    │  │ PipelineDesign     │                │
│  │ Standard           │  │ Standard           │                │
│  │ - getGasLoadFactor │  │ - getDesignFactor  │                │
│  │ - getFg            │  │ - getUsageFactor   │                │
│  └────────────────────┘  └────────────────────┘                │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│          Database Tables (src/main/resources/designdata/)       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ TechnicalRequirements_Process.csv                        │  │
│  │ - Equipment-specific parameters by Company               │  │
│  │ - NORSOK, ASME, DNV, API standard references             │  │
│  │ - TR document mappings (TR1414, TR2000, etc.)            │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ MaterialPipeProperties.csv, MaterialPlateProperties.csv  │  │
│  │ - Material grades (SA-516, X65, 316L, etc.)              │  │
│  │ - SMYS, SMTS, density values                             │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Using Company-Specific Standards

When you call `autoSize(company, trDocument)`, the system:

1. **Sets company on MechanicalDesign** - Triggers database lookup
2. **Loads design parameters** - K-factors, design factors, safety margins from TR documents
3. **Applies standards** - Uses NORSOK, ASME, DNV values per company specification

```java
// Size separator using Equinor's NORSOK-based standards
Separator sep = new Separator("HP-Sep", feed);
sep.autoSize("Equinor", "NORSOK-P-001");

// The system automatically:
// 1. Queries TechnicalRequirements_Process for "Separator" + "Equinor"
// 2. Loads GasLoadFactor = 0.12-0.15 per NORSOK P-001
// 3. Applies to sizing calculation
```

### Database Tables for Design Standards

**TechnicalRequirements_Process.csv** contains equipment-specific parameters:

| EQUIPMENTTYPE | SPECIFICATION | VALUE | Company | DOCUMENTID |
|--------------|---------------|-------|---------|------------|
| Separator | GasLoadFactor | 0.12-0.15 | Equinor | NORSOK-P-001 |
| Pipeline | designFactor | 0.72 | Equinor | NORSOK-L-001 |
| Gas scrubber | GasLoadFactor | 0.11 | StatoilTR | TR1414 |
| Compressor | SurgeMargin | 10% | Equinor | NORSOK-P-002 |
| Pump | DriverPowerMargin | 1.15 | Equinor | API-610 |
| Pump | NPSHMargin | 0.6 | Equinor | API-610 |
| Manifold | HeaderVelocityLimit | 15.0 | Equinor | API-RP-14E |
| Manifold | LOFThreshold | 0.5 | Equinor | EI-GL-017 |

**Standards Tables** (in `designdata/standards/` subdirectory):

| File | Standards Covered |
|------|-------------------|
| `api_standards.csv` | API-610 (pumps), API-674/675 (reciprocating/metering), API-682 (seals), API-RP-17A (subsea), API-RP-14E (erosional velocity) |
| `asme_standards.csv` | ASME B73 (pumps), ASME B31.3 (piping/manifolds), ASME B16.5 (flanges), ASME-PTC-8.2 (pump tests) |
| `dnv_iso_en_standards.csv` | ISO-13709 (pumps), ISO-21049 (seals), ISO-13628 (subsea manifolds), DNV-RP-A203 (subsea pumps) |
| `norsok_standards.csv` | NORSOK-L-002 (piping), NORSOK-P-001/P-002 (process/pumps), NORSOK-U-001 (subsea) |

**Example query flow:**
```java
// When Separator.autoSize("Equinor", "NORSOK-P-001") is called:
SELECT SPECIFICATION, MAXVALUE, MINVALUE 
FROM TechnicalRequirements_Process 
WHERE EQUIPMENTTYPE='Separator' AND Company='Equinor'

// Returns: GasLoadFactor = 0.12-0.15, LiquidRetentionTime = 2-5 min, etc.
```

### Extending the Design Database

To add new company standards or equipment types:

1. **Add rows to TechnicalRequirements_Process.csv**:
```csv
"ID","EQUIPMENTTYPE","SPECIFICATION","MINVALUE","MAXVALUE","UNIT","Company","DOCUMENTID","DESCRIPTION"
100,"Separator","GasLoadFactor",0.10,0.12,"m/s","Shell","DEP-31.22.05.11","Shell K-factor"
```

2. **Create or update DesignStandard subclass** if custom logic is needed:
```java
public class ShellSeparatorDesignStandard extends SeparatorDesignStandard {
    // Shell-specific sizing rules
}
```

### Material Properties Database

**MaterialPipeProperties.csv** and **MaterialPlateProperties.csv** contain:
- Material grades (API 5L X65, SA-516-70, etc.)
- Mechanical properties (SMYS, SMTS, density)
- Temperature derating factors

Used for wall thickness calculations:
```java
// Pipeline wall thickness per ASME B31.8
double t = (P * D) / (2 * S * F * E * T)
// where S = SMYS from MaterialPipeProperties
//       F = design factor from TechnicalRequirements_Process
```

## Integration with Existing Code

The design framework integrates with existing NeqSim capabilities:

### With ProductionOptimizer

```java
// DesignOptimizer can work with ProductionOptimizer
DesignOptimizer designOpt = DesignOptimizer.forProcess(process)
    .autoSizeEquipment()
    .applyDefaultConstraints()
    .setObjective(ObjectiveType.MAXIMIZE_PRODUCTION);

// The underlying ProductionOptimizer handles the mathematical optimization
DesignResult result = designOpt.optimize();
```

### With CapacityConstrainedEquipment

```java
// Auto-sized equipment maintains capacity constraints
separator.autoSize(1.2);
separator.addCapacityConstraint(new CapacityConstraint.Builder()
    .name("K-factor")
    .type("gasLoadFactor")
    .maxValue(0.08)
    .build());
```

### With Mechanical Design

```java
// Auto-sizing uses mechanical design calculations
valve.autoSize(1.2);  // Uses IEC 60534 via MechanicalDesign
double cv = valve.getMechanicalDesign().getValveCvMax();

// Company-specific sizing
separator.autoSize("Equinor", "NORSOK-P-001");
// → Loads K-factor from TechnicalRequirements_Process
// → Applies NORSOK design rules via SeparatorDesignStandard
```

### Full Example with Standards

```java
// Create separator with company standards
Separator sep = new Separator("HP-Sep", feed);

// Method 1: Direct auto-size with company standard
sep.autoSize("Equinor", "NORSOK-P-001");

// Method 2: Manual configuration then auto-size
sep.getMechanicalDesign().setCompanySpecificDesignStandards("Equinor");
sep.getMechanicalDesign().readDesignSpecifications();
sep.autoSize(1.15);  // Use 15% margin per company policy

// Get full mechanical design report
sep.getMechanicalDesign().displayResults();
```

## Future Enhancements

Planned improvements include:
- Additional process templates (compression trains, fractionation)
- More equipment types implementing AutoSizeable
- Enhanced company-specific standards from database
- Export to design tools (PFD generation, data sheets)
- Machine learning-based sizing recommendations

---

## AutoSizing, Mechanical Design, and Optimization Integration

This section explains how AutoSizing, Mechanical Design, and Production Optimization work together to create a complete equipment sizing and process optimization workflow.

### The Complete Workflow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           1. EQUIPMENT CREATION                              │
│  Equipment is created with basic process specifications                      │
│  (flow rate, inlet/outlet conditions)                                       │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           2. AUTO-SIZING (autoSize)                          │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ • Calculates physical dimensions (diameter, length, impeller size)    │   │
│  │ • Generates compressor curves (for Compressor)                        │   │
│  │ • Sets operational modes (solveSpeed=true for compressors)            │   │
│  │ • Uses company standards from database (TR documents)                 │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                     │                                        │
│                                     ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │             MECHANICAL DESIGN (calcDesign)                            │   │
│  │ • Wall thickness, material selection, weight estimation               │   │
│  │ • Driver power sizing, stage calculations                             │   │
│  │ • Cost estimation, bill of materials                                  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    3. CAPACITY CONSTRAINTS ARE SET                           │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ Each equipment has constraints based on mechanical design:            │   │
│  │                                                                       │   │
│  │ Separator:    gasLoadFactor, liquidResidenceTime                     │   │
│  │ Compressor:   speed, power, surgeMargin, stonewallMargin             │   │
│  │ Pump:         npshMargin, power, flowRate                            │   │
│  │ Valve:        valveOpening, cvUtilization                            │   │
│  │ Pipeline:     velocity, pressureDrop, FIV_LOF, FIV_FRMS              │   │
│  │ Heater:       duty, outletTemperature                                │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    4. PRODUCTION OPTIMIZATION                                │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ Optimizer increases feed rate until an ACTIVE CONSTRAINT is hit       │   │
│  │                                                                       │   │
│  │ • Checks ALL CapacityConstrainedEquipment in the process             │   │
│  │ • The equipment with highest utilization is the BOTTLENECK           │   │
│  │ • The specific constraint limiting that equipment is ACTIVE          │   │
│  │ • Reports optimal flow and which constraint limits production         │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Equipment That Can Be Bottlenecks

**ANY equipment implementing `CapacityConstrainedEquipment` can be a bottleneck**, not just compressors:

| Equipment Class | Constraints | When It Limits |
|----------------|-------------|----------------|
| **Separator** | `gasLoadFactor`, `liquidResidenceTime` | High gas/liquid rates |
| **Compressor** | `speed`, `power`, `surgeMargin`, `stonewallMargin` | High gas rates, high compression ratios |
| **Pump** | `npshMargin`, `power`, `flowRate` | High liquid rates, low suction pressure |
| **ThrottlingValve** | `valveOpening`, `cvUtilization` | High flow through restriction |
| **Pipeline** | `velocity`, `pressureDrop`, `FIV_LOF`, `FIV_FRMS` | High velocities, long pipelines |
| **Heater** | `duty`, `outletTemperature` | High heating demand |

### What is an "Active Constraint"?

An **active constraint** is the specific limit on a piece of equipment that currently restricts the process from operating at a higher rate.

```java
// Example: Finding the active constraint
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Find the bottleneck equipment
ProcessEquipmentInterface bottleneck = process.getBottleneck();
System.out.println("Bottleneck equipment: " + bottleneck.getName());

// Find the specific active constraint on that equipment
if (bottleneck instanceof CapacityConstrainedEquipment) {
    CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) bottleneck;
    CapacityConstraint activeConstraint = constrained.getBottleneckConstraint();
    
    System.out.println("Active constraint: " + activeConstraint.getName());
    System.out.println("Current value: " + activeConstraint.getCurrentValue());
    System.out.println("Design limit: " + activeConstraint.getDesignValue());
    System.out.println("Utilization: " + activeConstraint.getUtilizationPercent() + "%");
}
```

**Example outputs:**
- "Bottleneck: HP Separator, Active constraint: gasLoadFactor at 95%"
- "Bottleneck: Export Compressor, Active constraint: power at 92%"
- "Bottleneck: Feed Valve, Active constraint: valveOpening at 88%"
- "Bottleneck: Export Pipeline, Active constraint: velocity at 97%"

### How Constraints Are Set

Constraints are initialized automatically when equipment is auto-sized or when `initMechanicalDesign()` is called:

```java
// Method 1: Auto-sizing sets constraints automatically
Separator sep = new Separator("HP-Sep", feed);
sep.autoSize(1.2);  // Sets gasLoadFactor constraint based on design K-factor

// Method 2: Manual constraint setup
Compressor comp = new Compressor("Export Comp", gasStream);
comp.setMaximumSpeed(11000.0);   // Sets HARD speed constraint
comp.setMaximumPower(2000.0);    // Sets HARD power constraint
comp.setSurgeMargin(10.0);       // Sets SOFT surge margin constraint

// Method 3: Programmatic constraint addition
Pipeline pipe = new Pipeline("Export Line", compOutput);
pipe.addCapacityConstraint(new CapacityConstraint("velocity", "m/s", ConstraintType.DESIGN)
    .setDesignValue(15.0)
    .setMaxValue(20.0)
    .setWarningThreshold(0.8)
    .setValueSupplier(() -> pipe.getVelocity()));
```

### Constraint Types and Optimization Behavior

| Constraint Type | During Optimization | Example |
|-----------------|---------------------|---------|
| **HARD** | Cannot be exceeded - optimization stops | Compressor trip speed, vessel MAWP |
| **SOFT** | Can be exceeded with penalty/warning | Efficiency degradation zone |
| **DESIGN** | Target limit for normal operation | Design K-factor, rated flow |

```java
// Setting constraint types
CapacityConstraint speedLimit = new CapacityConstraint("speed", ConstraintType.HARD)
    .setDesignValue(10000.0)   // Normal operating speed
    .setMaxValue(11000.0);      // Trip point - HARD limit

CapacityConstraint surgeMargin = new CapacityConstraint("surgeMargin", ConstraintType.SOFT)
    .setDesignValue(10.0)      // 10% margin from surge
    .setMinValue(5.0);         // Absolute minimum - warning

CapacityConstraint kFactor = new CapacityConstraint("gasLoadFactor", ConstraintType.DESIGN)
    .setDesignValue(0.08)      // Design basis
    .setWarningThreshold(0.9); // Warn at 90% utilization
```

### Optimization with Multiple Equipment Types

The optimizer checks **all** constrained equipment, not just compressors:

```java
// Create process with multiple equipment types
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");

Separator sep = new Separator("Inlet Sep", feed);
sep.autoSize(1.2);  // Sets gasLoadFactor constraint

ThrottlingValve valve = new ThrottlingValve("HP Valve", sep.getGasOutStream());
valve.setOutletPressure(30.0, "bara");
valve.autoSize(1.2);  // Sets valveOpening constraint

Compressor comp = new Compressor("Export Comp", valve.getOutletStream());
comp.setOutletPressure(100.0);
comp.autoSize(1.2);  // Sets speed, power, surge constraints

Pipeline pipe = new PipeBeggsAndBrills("Export Pipeline", comp.getOutletStream());
pipe.setLength(50000.0);
pipe.setDiameter(0.4);
// Pipeline has velocity, pressureDrop, FIV constraints

process.add(feed);
process.add(sep);
process.add(valve);
process.add(comp);
process.add(pipe);

// Run optimization - checks ALL equipment constraints
OptimizationConfig config = new OptimizationConfig(1000.0, 50000.0)
    .defaultUtilizationLimit(0.95);

OptimizationResult result = ProductionOptimizer.optimize(process, feed, config);

// The bottleneck could be ANY of: separator, valve, compressor, or pipeline
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Active constraint: " + result.getActiveConstraintName());
System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
```

### Viewing All Constraints in a Process

```java
// Get all constrained equipment
for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    System.out.println("\n" + equip.getName() + ":");
    
    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        String status = c.isViolated() ? "⚠️ EXCEEDED" : 
                       c.isNearLimit() ? "⚡ NEAR LIMIT" : "✓ OK";
        System.out.printf("  %-20s: %6.1f / %6.1f %s (%5.1f%%) %s%n",
            c.getName(),
            c.getCurrentValue(),
            c.getDesignValue(),
            c.getUnit(),
            c.getUtilizationPercent(),
            status);
    }
}
```

Example output:
```
Inlet Sep:
  gasLoadFactor       :   0.07 /   0.08 m/s  (87.5%) ✓ OK
  liquidResidenceTime :   3.20 /   3.00 min  (106.7%) ⚠️ EXCEEDED

HP Valve:
  valveOpening        :  72.00 /  90.00 %    (80.0%) ✓ OK
  cvUtilization       :  45.00 /  50.00 -    (90.0%) ⚡ NEAR LIMIT

Export Comp:
  speed               : 9500.0 /10000.0 RPM  (95.0%) ⚡ NEAR LIMIT
  power               : 1650.0 / 2000.0 kW   (82.5%) ✓ OK
  surgeMargin         :  12.00 /  10.00 %    (83.3%) ✓ OK

Export Pipeline:
  velocity            :  14.50 /  15.00 m/s  (96.7%) ⚡ NEAR LIMIT
  pressureDrop        :   4.20 /   5.00 bara (84.0%) ✓ OK
  FIV_LOF             :   0.35 /   1.00 -    (35.0%) ✓ OK
```

### Summary: AutoSizing → Mechanical Design → Constraints → Optimization

1. **AutoSizing** (`autoSize()`) calculates equipment dimensions from process conditions
2. **Mechanical Design** (`calcDesign()`) determines detailed specifications (materials, wall thickness, etc.)
3. **Constraints** are set based on the design limits (K-factor, max speed, rated power, etc.)
4. **Optimization** finds the maximum flow rate that respects ALL constraints across ALL equipment
5. The **Active Constraint** is the specific limit currently preventing higher production
6. The **Bottleneck** is the equipment where that active constraint exists


