---
title: Process Design Guide for NeqSim
description: "This guide describes the complete process design workflow using NeqSim, from initial process simulation through mechanical design and final validation."
---

# Process Design Guide for NeqSim

## Introduction

This guide describes the complete process design workflow using NeqSim, from initial process simulation through mechanical design and final validation. NeqSim provides an integrated framework for:

- **Process Simulation** - Thermodynamic calculations and equipment modeling
- **Mechanical Design** - Equipment sizing based on international standards
- **TORG Integration** - Project-specific technical requirements
- **Design Orchestration** - Coordinated workflows for field development
- **Automated Design** - Auto-sizing and optimization via [Design Framework](DESIGN_FRAMEWORK)

## Related Documentation

| Document | Description |
|----------|-------------|
| [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK) | **AutoSizeable interface, ProcessTemplates, DesignOptimizer** |
| [PRODUCTION_OPTIMIZATION_GUIDE.md](../examples/PRODUCTION_OPTIMIZATION_GUIDE) | Production optimization examples |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](CAPACITY_CONSTRAINT_FRAMEWORK) | Equipment capacity constraints |

## Process Design Workflow Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        PROCESS DESIGN WORKFLOW                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐ │
│  │   1. DEFINE  │────▶│  2. PROCESS  │────▶│ 3. MECHANICAL│────▶│ 4. VALIDATE│ │
│  │    SYSTEM    │     │  SIMULATION  │     │    DESIGN    │     │  & REPORT  │ │
│  └──────────────┘     └──────────────┘     └──────────────┘     └────────────┘ │
│        │                    │                    │                    │         │
│        ▼                    ▼                    ▼                    ▼         │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐ │
│  │ • Fluid      │     │ • Run cases  │     │ • Apply      │     │ • Check    │ │
│  │   composition│     │ • Calculate  │     │   standards  │     │   compliance│ │
│  │ • Equipment  │     │   properties │     │ • Size       │     │ • Generate │ │
│  │ • Flowsheet  │     │ • Heat/mass  │     │   equipment  │     │   reports  │ │
│  │ • TORG       │     │   balance    │     │ • Materials  │     │            │ │
│  └──────────────┘     └──────────────┘     └──────────────┘     └────────────┘ │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Define the System

### 1.1 Create the Fluid System

Define the thermodynamic system with appropriate equation of state:

```java
import neqsim.thermo.system.SystemSrkEos;

// Create fluid with SRK equation of state
SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);  // T(K), P(bar)
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-butane", 0.02);
fluid.addComponent("CO2", 0.01);
fluid.setMixingRule("classic");
fluid.createDatabase(true);
```

### 1.2 Build the Process Flowsheet

Create equipment and connect into a process system:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;

ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Well Feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.setTemperature(60.0, "C");
feed.setPressure(50.0, "bara");
process.add(feed);

// HP Separator
Separator hpSeparator = new Separator("HP Separator", feed);
process.add(hpSeparator);

// Export Compressor
Compressor exportCompressor = new Compressor("Export Compressor", hpSeparator.getGasOutStream());
exportCompressor.setOutletPressure(150.0, "bara");
process.add(exportCompressor);
```

### 1.3 Load Project TORG

Load the Technical Requirements Document governing design standards:

```java
import neqsim.process.mechanicaldesign.torg.TorgManager;
import neqsim.process.mechanicaldesign.torg.CsvTorgDataSource;

TorgManager torgManager = new TorgManager();
torgManager.addDataSource(new CsvTorgDataSource("project_torg.csv"));

Optional<TechnicalRequirementsDocument> optTorg = torgManager.load("TROLL-WEST-2025");
```

> 📖 **See:** [TORG Integration](torg_integration) for detailed TORG configuration

---

## Step 2: Process Simulation

### 2.1 Run Base Case Simulation

```java
// Run the process simulation
process.run();

// Access results
double gasRate = hpSeparator.getGasOutStream().getFlowRate("MSm3/day");
double liquidRate = hpSeparator.getLiquidOutStream().getFlowRate("m3/hr");
double compressorPower = exportCompressor.getPower("MW");

System.out.println("Gas rate: " + gasRate + " MSm3/day");
System.out.println("Liquid rate: " + liquidRate + " m3/hr");
System.out.println("Compressor power: " + compressorPower + " MW");
```

### 2.2 Run Multiple Design Cases

Evaluate different operating scenarios:

```java
import neqsim.process.mechanicaldesign.designstandards.DesignCase;

// Define cases to evaluate
List<DesignCase> designCases = Arrays.asList(
    DesignCase.NORMAL,
    DesignCase.MAXIMUM,
    DesignCase.MINIMUM,
    DesignCase.UPSET
);

Map<DesignCase, Double> separatorPressures = new HashMap<>();

for (DesignCase designCase : designCases) {
    // Adjust feed based on case
    double loadFactor = designCase.getTypicalLoadFactor();
    feed.setFlowRate(50000.0 * loadFactor, "kg/hr");
    
    // Run simulation
    process.run();
    
    // Store results
    separatorPressures.put(designCase, hpSeparator.getPressure("bara"));
}
```

> 📖 **See:** [Field Development Orchestration](field_development_orchestration) for design case details

---

## Step 3: Mechanical Design

### 3.1 Apply Design Standards

Apply appropriate international standards to equipment:

```java
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;

// Apply standards to individual equipment
StandardRegistry.applyStandardToEquipment(hpSeparator, StandardType.NORSOK_P002);
StandardRegistry.applyStandardToEquipment(exportCompressor, StandardType.API_617);

// Or apply TORG to entire system (applies all project standards)
if (optTorg.isPresent()) {
    torgManager.apply(optTorg.get(), process);
}
```

> 📖 **See:** [Mechanical Design Standards](mechanical_design_standards) for available standards

### 3.2 Run Mechanical Design Calculations

```java
import neqsim.process.mechanicaldesign.MechanicalDesign;

// Get mechanical design for separator
MechanicalDesign sepDesign = hpSeparator.getMechanicalDesign();
sepDesign.calcDesign();

// Access design results
double designPressure = sepDesign.getDesignPressure();
double designTemperature = sepDesign.getDesignTemperature();
double wallThickness = sepDesign.getWallThickness();
double weight = sepDesign.getWeightTotal();
String materialGrade = sepDesign.getMaterialDesignStandard().getMaterialGrade();

System.out.println("Design Pressure: " + designPressure + " barg");
System.out.println("Design Temperature: " + designTemperature + " °C");
System.out.println("Wall Thickness: " + wallThickness + " mm");
System.out.println("Weight: " + weight + " kg");
System.out.println("Material: " + materialGrade);
```

### 3.3 Design All Equipment in System

```java
// Calculate mechanical design for all equipment
for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
    MechanicalDesign mechDesign = equipment.getMechanicalDesign();
    if (mechDesign != null) {
        mechDesign.calcDesign();
    }
}
```

> 📖 **See:** [Mechanical Design Database](mechanical_design_database) for data sources

---

## Step 4: Validate and Report

### 4.1 Validate Design Compliance

```java
import neqsim.process.mechanicaldesign.designstandards.DesignValidationResult;

DesignValidationResult validation = new DesignValidationResult();

// Check each equipment
for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
    MechanicalDesign design = equipment.getMechanicalDesign();
    if (design != null && design.hasDesignStandard()) {
        // Validate against TORG requirements
        if (optTorg.isPresent()) {
            TechnicalRequirementsDocument torg = optTorg.get();
            
            // Check corrosion allowance
            double requiredCA = torg.getSafetyFactors().getCorrosionAllowance();
            if (design.getCorrosionAllowance() < requiredCA) {
                validation.addWarning(equipment.getName() + 
                    ": Corrosion allowance below TORG requirement");
            }
        }
        validation.addInfo(equipment.getName() + " design validated");
    }
}

// Check results
if (validation.isValid()) {
    System.out.println("All equipment meets design requirements");
} else {
    System.out.println("Design issues found:");
    for (DesignValidationResult.Message msg : validation.getMessages()) {
        System.out.println("  " + msg.getSeverity() + ": " + msg.getMessage());
    }
}
```

### 4.2 Generate Design Report

```java
StringBuilder report = new StringBuilder();
report.append("Process Design Report\n");
report.append("=====================\n\n");

// TORG Information
if (optTorg.isPresent()) {
    TechnicalRequirementsDocument torg = optTorg.get();
    report.append("Project: ").append(torg.getProjectName()).append("\n");
    report.append("TORG Revision: ").append(torg.getRevision()).append("\n");
    report.append("Design Life: ").append(torg.getDesignLifeYears()).append(" years\n\n");
}

// Equipment Summary
report.append("Equipment Summary\n");
report.append("-----------------\n");
for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
    MechanicalDesign design = equipment.getMechanicalDesign();
    if (design != null) {
        report.append("\n").append(equipment.getName()).append(":\n");
        report.append("  Design Pressure: ").append(design.getDesignPressure()).append(" barg\n");
        report.append("  Design Temperature: ").append(design.getDesignTemperature()).append(" °C\n");
        report.append("  Weight: ").append(design.getWeightTotal()).append(" kg\n");
    }
}

System.out.println(report);
```

---

## Using the Field Development Orchestrator

For complex projects, use the `FieldDevelopmentDesignOrchestrator` to coordinate the entire workflow:

```java
import neqsim.process.mechanicaldesign.designstandards.FieldDevelopmentDesignOrchestrator;
import neqsim.process.mechanicaldesign.designstandards.DesignPhase;
import neqsim.process.mechanicaldesign.designstandards.DesignCase;

// Create orchestrator
FieldDevelopmentDesignOrchestrator orchestrator = 
    new FieldDevelopmentDesignOrchestrator(process);

// Configure design phase
orchestrator.setDesignPhase(DesignPhase.FEED);  // ±15-20% accuracy

// Add design cases
orchestrator.addDesignCase(DesignCase.NORMAL);
orchestrator.addDesignCase(DesignCase.MAXIMUM);
orchestrator.addDesignCase(DesignCase.MINIMUM);
orchestrator.addDesignCase(DesignCase.UPSET);
orchestrator.addDesignCase(DesignCase.EARLY_LIFE);
orchestrator.addDesignCase(DesignCase.LATE_LIFE);

// Load TORG
orchestrator.loadTorg(torgManager, "TROLL-WEST-2025");

// Run complete workflow
orchestrator.runCompleteDesignWorkflow();

// Get results
DesignValidationResult validation = orchestrator.validateDesign();
String report = orchestrator.generateDesignReport();

System.out.println(report);
```

> 📖 **See:** [Field Development Orchestration](field_development_orchestration) for complete workflow details

---

## Design Phases and Accuracy

Choose the appropriate design phase based on project stage:

| Phase | Use Case | Accuracy | Full Mechanical Design |
|-------|----------|----------|------------------------|
| **SCREENING** | Early opportunity evaluation | ±40-50% | No |
| **CONCEPT_SELECT** | Concept comparison | ±30% | No |
| **PRE_FEED** | Preliminary engineering | ±25% | No |
| **FEED** | Front-end engineering | ±15-20% | Yes |
| **DETAIL_DESIGN** | Detailed engineering | ±10% | Yes |
| **AS_BUILT** | Verification | ±5% | Yes |

```java
DesignPhase phase = DesignPhase.FEED;

// Check phase requirements
if (phase.requiresFullMechanicalDesign()) {
    // Run detailed calculations
    runFullMechanicalDesign(process);
} else {
    // Use simplified estimates
    runQuickEstimates(process);
}
```

---

## Supported Design Standards

NeqSim supports 30+ international standards:

| Category | Standards |
|----------|-----------|
| **Pressure Vessels** | ASME VIII Div 1/2, PD 5500, EN 13445 |
| **Process Design** | NORSOK P-001, NORSOK P-002 |
| **Piping** | ASME B31.3, NORSOK L-001 |
| **Pipelines** | DNV-OS-F101, API 5L |
| **Compressors** | API 617, API 618 |
| **Heat Exchangers** | TEMA, API 660 |
| **Materials** | ASTM, NACE MR0175 |
| **Safety** | API 521, ISO 23251 |

> 📖 **See:** [Mechanical Design Standards](mechanical_design_standards) for complete list

---

## Data Sources

Design parameters can be loaded from:

1. **CSV Files** - Simple configuration files
2. **Database** - NeqSim thermodynamic database
3. **Custom Sources** - Implement `MechanicalDesignDataSource`

```java
// CSV data source
StandardBasedCsvDataSource csvSource = 
    new StandardBasedCsvDataSource(StandardType.NORSOK_P002, "norsok_p002.csv");

// Register with registry
StandardRegistry.registerDataSource(StandardType.NORSOK_P002, csvSource);
```

> 📖 **See:** [Mechanical Design Database](mechanical_design_database) for data configuration

---

## Complete Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.mechanicaldesign.designstandards.*;
import neqsim.process.mechanicaldesign.torg.*;

public class ProcessDesignExample {
    
    public static void main(String[] args) {
        // ===== STEP 1: Define System =====
        
        // Create fluid
        SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.04);
        fluid.addComponent("n-butane", 0.03);
        fluid.setMixingRule("classic");
        
        // Build flowsheet
        ProcessSystem process = new ProcessSystem();
        
        Stream feed = new Stream("Well Feed", fluid);
        feed.setFlowRate(50000.0, "kg/hr");
        process.add(feed);
        
        Separator hpSep = new Separator("HP Separator", feed);
        process.add(hpSep);
        
        Compressor compressor = new Compressor("Export Compressor", hpSep.getGasOutStream());
        compressor.setOutletPressure(150.0, "bara");
        process.add(compressor);
        
        // ===== STEP 2: Configure Orchestrator =====
        
        FieldDevelopmentDesignOrchestrator orchestrator = 
            new FieldDevelopmentDesignOrchestrator(process);
        
        orchestrator.setDesignPhase(DesignPhase.FEED);
        orchestrator.addDesignCase(DesignCase.NORMAL);
        orchestrator.addDesignCase(DesignCase.MAXIMUM);
        orchestrator.addDesignCase(DesignCase.UPSET);
        
        // Load TORG
        TorgManager torgManager = new TorgManager();
        torgManager.addDataSource(new CsvTorgDataSource("project_torg.csv"));
        orchestrator.loadTorg(torgManager, "PROJECT-001");
        
        // ===== STEP 3: Run Workflow =====
        
        orchestrator.runCompleteDesignWorkflow();
        
        // ===== STEP 4: Validate and Report =====
        
        DesignValidationResult validation = orchestrator.validateDesign();
        
        if (validation.isValid()) {
            System.out.println("Design PASSED");
            System.out.println(orchestrator.generateDesignReport());
        } else {
            System.out.println("Design FAILED");
            for (DesignValidationResult.Message msg : validation.getMessagesBySeverity(
                    DesignValidationResult.Severity.ERROR)) {
                System.err.println("ERROR: " + msg.getMessage());
            }
        }
    }
}
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Mechanical Design Standards](mechanical_design_standards) | StandardType enum, StandardRegistry, applying standards |
| [Mechanical Design Database](mechanical_design_database) | Data sources, schemas, CSV configuration |
| [TORG Integration](torg_integration) | Technical requirements documents |
| [Field Development Orchestration](field_development_orchestration) | Design phases, cases, orchestrator |

---

## Quick Reference

### Key Classes

| Class | Purpose |
|-------|---------|
| `ProcessSystem` | Container for process flowsheet |
| `MechanicalDesign` | Base class for equipment mechanical design |
| `StandardType` | Enum of supported design standards |
| `StandardRegistry` | Factory for creating and applying standards |
| `TechnicalRequirementsDocument` | TORG representation |
| `TorgManager` | Loads and applies TORG |
| `FieldDevelopmentDesignOrchestrator` | Workflow coordinator |
| `DesignPhase` | Project lifecycle phases |
| `DesignCase` | Operating scenarios |
| `DesignValidationResult` | Validation messages and results |

### Key Packages

```
neqsim.process.processmodel          - ProcessSystem
neqsim.process.equipment             - All equipment types
neqsim.process.mechanicaldesign      - MechanicalDesign base
neqsim.process.mechanicaldesign.designstandards - Standards framework
neqsim.process.mechanicaldesign.torg - TORG framework
neqsim.process.mechanicaldesign.data - Data sources
```
