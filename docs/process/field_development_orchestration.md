---
title: Field Development Design Orchestration
description: The `FieldDevelopmentDesignOrchestrator` provides a unified workflow for coordinating process simulation, mechanical design, and design validation throughout a field development project lifecycle. It ...
---

# Field Development Design Orchestration

## Overview

The `FieldDevelopmentDesignOrchestrator` provides a unified workflow for coordinating process simulation, mechanical design, and design validation throughout a field development project lifecycle. It integrates TORG requirements, design standards, and design cases into a structured workflow.

## Orchestrator Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    FieldDevelopmentDesignOrchestrator                         │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│  │ Design Phase │───▶│ Design Cases │───▶│    TORG      │───▶│  Workflow  │ │
│  │  (Lifecycle) │    │ (Scenarios)  │    │ (Standards)  │    │  Execute   │ │
│  └──────────────┘    └──────────────┘    └──────────────┘    └────────────┘ │
│         │                   │                   │                   │        │
│         ▼                   ▼                   ▼                   ▼        │
│  ┌──────────────────────────────────────────────────────────────────────────┐│
│  │                         Workflow Steps                                    ││
│  │  1. Initialize    2. Run Process   3. Apply   4. Mechanical  5. Validate ││
│  │     Environment      Simulation       TORG       Design         Results  ││
│  └──────────────────────────────────────────────────────────────────────────┘│
│         │                                                           │        │
│         ▼                                                           ▼        │
│  ┌──────────────┐                                          ┌────────────────┐│
│  │ Design Case  │                                          │  Validation    ││
│  │ Results      │                                          │  Results       ││
│  └──────────────┘                                          └────────────────┘│
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Design Phases

The `DesignPhase` enum represents project lifecycle stages with associated accuracy requirements:

| Phase | Description | Accuracy Range | Requires Full Design |
|-------|-------------|----------------|---------------------|
| `SCREENING` | Early opportunity screening | ±40-50% | No |
| `CONCEPT_SELECT` | Concept selection study | ±30% | No |
| `PRE_FEED` | Pre-FEED study | ±25% | No |
| `FEED` | Front-End Engineering Design | ±15-20% | Yes |
| `DETAIL_DESIGN` | Detailed engineering | ±10% | Yes |
| `AS_BUILT` | As-built verification | ±5% | Yes |

### Using Design Phases

```java
import neqsim.process.mechanicaldesign.designstandards.DesignPhase;

// Get phase properties
DesignPhase phase = DesignPhase.FEED;

String accuracy = phase.getAccuracyRange();           // "±15-20%"
boolean compliance = phase.requiresDetailedCompliance(); // true
boolean fullDesign = phase.requiresFullMechanicalDesign(); // true

// Phase comparisons
boolean isLate = phase.isLaterThan(DesignPhase.CONCEPT_SELECT); // true
boolean isEarly = phase.isEarlierThan(DesignPhase.DETAIL_DESIGN); // true
```

## Design Cases

The `DesignCase` enum defines operating scenarios for equipment sizing:

| Case | Load Factor | Sizing Critical | Relief Required |
|------|-------------|-----------------|-----------------|
| `NORMAL` | 1.0 | Yes | No |
| `MAXIMUM` | 1.1 | Yes | Yes |
| `MINIMUM` | 0.3 | No (turndown) | No |
| `STARTUP` | 0.1 | No | No |
| `SHUTDOWN` | 0.1 | No | No |
| `UPSET` | 1.2 | Yes | Yes |
| `EMERGENCY` | 1.0 | No | Yes |
| `WINTER` | 1.0 | Yes | No |
| `SUMMER` | 1.0 | Yes | No |
| `EARLY_LIFE` | 1.0 | Yes | No |
| `LATE_LIFE` | 0.8 | Yes | No |

### Using Design Cases

```java
import neqsim.process.mechanicaldesign.designstandards.DesignCase;

// Get case properties
DesignCase designCase = DesignCase.MAXIMUM;

double loadFactor = designCase.getTypicalLoadFactor();  // 1.1
boolean sizing = designCase.isSizingCritical();         // true
boolean turndown = designCase.isTurndownCase();         // false
boolean relief = designCase.requiresReliefSizing();     // true

// Get relevant cases for different purposes
List<DesignCase> sizingCases = DesignCase.getSizingCriticalCases();
List<DesignCase> reliefCases = DesignCase.getReliefSizingCases();
List<DesignCase> turndownCases = DesignCase.getTurndownCases();
```

## Complete Workflow Example

### Step 1: Create Orchestrator

```java
import neqsim.process.mechanicaldesign.designstandards.FieldDevelopmentDesignOrchestrator;
import neqsim.process.mechanicaldesign.designstandards.DesignPhase;
import neqsim.process.mechanicaldesign.designstandards.DesignCase;
import neqsim.process.processmodel.ProcessSystem;

// Build process system
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
process.add(feed);

Separator separator = new Separator("HP Separator", feed);
process.add(separator);

Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(80.0, "bara");
process.add(compressor);

// Create orchestrator
FieldDevelopmentDesignOrchestrator orchestrator = 
    new FieldDevelopmentDesignOrchestrator(process);
```

### Step 2: Configure Design Phase and Cases

```java
// Set design phase
orchestrator.setDesignPhase(DesignPhase.FEED);

// Add design cases to evaluate
orchestrator.addDesignCase(DesignCase.NORMAL);
orchestrator.addDesignCase(DesignCase.MAXIMUM);
orchestrator.addDesignCase(DesignCase.MINIMUM);
orchestrator.addDesignCase(DesignCase.UPSET);
```

### Step 3: Load and Apply TORG

```java
import neqsim.process.mechanicaldesign.torg.TorgManager;
import neqsim.process.mechanicaldesign.torg.CsvTorgDataSource;

// Configure TORG source
TorgManager torgManager = new TorgManager();
torgManager.addDataSource(new CsvTorgDataSource("project_torg.csv"));

// Load TORG for project
boolean loaded = orchestrator.loadTorg(torgManager, "TROLL-WEST-2025");
if (!loaded) {
    throw new IllegalStateException("Failed to load TORG");
}
```

### Step 4: Run Complete Workflow

```java
// Run complete design workflow
orchestrator.runCompleteDesignWorkflow();
```

This executes the following steps:
1. **Initialize** - Set up environment and validate configuration
2. **Run Process Simulation** - Execute process calculations for all design cases
3. **Apply TORG** - Apply standards and requirements from TORG
4. **Run Mechanical Design** - Calculate equipment sizing and material selection
5. **Validate** - Check compliance with standards and requirements

### Step 5: Get Results

```java
// Get validation results
DesignValidationResult results = orchestrator.validateDesign();

if (results.isValid()) {
    System.out.println("Design validation passed!");
} else {
    System.out.println("Design validation failed:");
    for (DesignValidationResult.ValidationMessage msg : results.getMessages()) {
        System.out.println("  " + msg.getSeverity() + ": " + msg.getMessage());
    }
}

// Get results for each design case
Map<DesignCase, DesignCaseResult> caseResults = orchestrator.getDesignCaseResults();
for (Map.Entry<DesignCase, DesignCaseResult> entry : caseResults.entrySet()) {
    DesignCase dc = entry.getKey();
    DesignCaseResult result = entry.getValue();
    System.out.println(dc.name() + ": " + (result.isConverged() ? "Converged" : "Failed"));
}
```

## Design Validation Results

The `DesignValidationResult` class provides structured validation feedback:

### Severity Levels

| Level | Description | Blocks Design |
|-------|-------------|---------------|
| `INFO` | Informational messages | No |
| `WARNING` | Potential issues, review recommended | No |
| `ERROR` | Design problems, must be addressed | Yes |
| `CRITICAL` | Severe issues, safety implications | Yes |

### Using Validation Results

```java
import neqsim.process.mechanicaldesign.designstandards.DesignValidationResult;

DesignValidationResult result = orchestrator.validateDesign();

// Check overall status
boolean isValid = result.isValid();          // true if no ERROR/CRITICAL
boolean hasWarnings = result.hasWarnings();  // true if any WARNING
boolean hasCritical = result.hasCriticalIssues(); // true if any CRITICAL

// Get counts by severity
int errorCount = result.getErrorCount();
int warningCount = result.getWarningCount();

// Get all messages
List<ValidationMessage> allMessages = result.getMessages();

// Filter by severity
List<ValidationMessage> errors = result.getMessagesBySeverity(Severity.ERROR);
List<ValidationMessage> warnings = result.getMessagesBySeverity(Severity.WARNING);

// Print formatted summary
System.out.println(result.getSummary());
```

Example output:
```
Design Validation Summary
=========================
Status: PASSED WITH WARNINGS

Messages:
  [INFO] HP Separator design completed successfully
  [INFO] Export Compressor design completed successfully
  [WARNING] HP Separator corrosion allowance (2.0 mm) is below TORG requirement (3.0 mm)
  [WARNING] Minimum case shows separator efficiency at 85% (target 90%)

Statistics:
  - Info: 2
  - Warnings: 2
  - Errors: 0
  - Critical: 0
```

## Design Report Generation

Generate comprehensive design reports:

```java
// Generate design report
String report = orchestrator.generateDesignReport();
System.out.println(report);

// Save to file
Files.write(Paths.get("design_report.txt"), report.getBytes());
```

Example report:
```
Field Development Design Report
================================
Project: TROLL-WEST-2025
Phase: FEED (±15-20% accuracy)
Generated: 2025-01-06 14:30:00

TORG Information
----------------
Revision: Rev 2
Company: EQUINOR
Design Life: 25 years

Design Cases Evaluated
----------------------
1. NORMAL (Load Factor: 1.0)
   Status: Converged
   Iterations: 5
   
2. MAXIMUM (Load Factor: 1.1)
   Status: Converged
   Iterations: 7
   
3. MINIMUM (Load Factor: 0.3)
   Status: Converged
   Iterations: 4
   
4. UPSET (Load Factor: 1.2)
   Status: Converged
   Iterations: 9

Equipment Summary
-----------------
HP Separator:
  - Design Pressure: 55.0 barg
  - Design Temperature: 150°C
  - Material: SA-516-70
  - Wall Thickness: 25.4 mm
  - Weight: 12,500 kg
  - Standards: ASME VIII Div 1, NORSOK P-002

Export Compressor:
  - Stages: 2
  - Power: 2.5 MW
  - Discharge Pressure: 80 bara
  - Material: API 617 compliant
  - Standards: API 617

Validation Summary
------------------
Overall Status: PASSED WITH WARNINGS
- 0 Critical issues
- 0 Errors
- 2 Warnings
- 4 Info messages

See detailed validation report for warning details.
```

## Workflow Customization

### Custom Workflow Steps

```java
// Add custom pre-processing step
orchestrator.addPreProcessStep("Custom Pre-Check", () -> {
    // Custom validation logic
    if (!checkCustomRequirements()) {
        throw new IllegalStateException("Custom requirements not met");
    }
});

// Add custom post-processing step
orchestrator.addPostProcessStep("Export Results", () -> {
    // Export to external system
    exportToExternalDatabase(orchestrator.getResults());
});
```

### Selective Case Execution

```java
// Run only sizing-critical cases
orchestrator.clearDesignCases();
for (DesignCase dc : DesignCase.getSizingCriticalCases()) {
    orchestrator.addDesignCase(dc);
}
orchestrator.runCompleteDesignWorkflow();
```

### Phase-Specific Behavior

```java
DesignPhase phase = orchestrator.getDesignPhase();

if (phase.requiresFullMechanicalDesign()) {
    // Full mechanical design with detailed calculations
    orchestrator.setDetailedCalculations(true);
} else {
    // Simplified calculations for early phases
    orchestrator.setDetailedCalculations(false);
}
```

## Integration with Process Simulation

### Updating Process Conditions

```java
// For each design case, update process conditions
for (DesignCase designCase : orchestrator.getDesignCases()) {
    // Adjust feed rate based on case
    double loadFactor = designCase.getTypicalLoadFactor();
    feed.setFlowRate(baseFlowRate * loadFactor, "kg/hr");
    
    // Adjust temperature for seasonal cases
    if (designCase == DesignCase.WINTER) {
        feed.setTemperature(-20.0, "C");
    } else if (designCase == DesignCase.SUMMER) {
        feed.setTemperature(35.0, "C");
    }
    
    // Run simulation
    process.run();
    
    // Store results
    orchestrator.storeDesignCaseResult(designCase, process);
}
```

### Equipment Sizing Envelope

```java
// Get sizing envelope across all cases
SizingEnvelope envelope = orchestrator.getSizingEnvelope();

double maxPressure = envelope.getMaxDesignPressure();
double maxTemperature = envelope.getMaxDesignTemperature();
double maxFlow = envelope.getMaxFlowRate();

System.out.println("Sizing Envelope:");
System.out.println("  Max Pressure: " + maxPressure + " barg");
System.out.println("  Max Temperature: " + maxTemperature + " °C");
System.out.println("  Max Flow: " + maxFlow + " kg/hr");
```

## Error Handling

```java
try {
    orchestrator.runCompleteDesignWorkflow();
} catch (TorgNotFoundException e) {
    System.err.println("TORG not found: " + e.getMessage());
    // Fall back to default standards
    orchestrator.applyDefaultStandards();
    orchestrator.runCompleteDesignWorkflow();
} catch (DesignConvergenceException e) {
    System.err.println("Design did not converge: " + e.getMessage());
    // Get partial results
    DesignValidationResult partial = e.getPartialResults();
    System.out.println(partial.getSummary());
} catch (StandardNotSupportedException e) {
    System.err.println("Standard not supported: " + e.getMessage());
    String remediation = e.getRemediation();
    System.out.println("Suggested action: " + remediation);
}
```

## Best Practices

### 1. Progressive Refinement

Start with coarse phases and refine:

```java
// Screening phase - quick estimates
orchestrator.setDesignPhase(DesignPhase.SCREENING);
orchestrator.addDesignCase(DesignCase.NORMAL);
orchestrator.addDesignCase(DesignCase.MAXIMUM);
orchestrator.runCompleteDesignWorkflow();

// If viable, move to FEED
if (orchestrator.validateDesign().isValid()) {
    orchestrator.setDesignPhase(DesignPhase.FEED);
    // Add more cases for detailed analysis
    orchestrator.addDesignCase(DesignCase.MINIMUM);
    orchestrator.addDesignCase(DesignCase.UPSET);
    orchestrator.addDesignCase(DesignCase.WINTER);
    orchestrator.addDesignCase(DesignCase.SUMMER);
    orchestrator.runCompleteDesignWorkflow();
}
```

### 2. Document All Assumptions

```java
// Add assumptions to report
orchestrator.addAssumption("Feed composition based on 2024 well test data");
orchestrator.addAssumption("Ambient temperature range from met-ocean study");
orchestrator.addAssumption("Design life 25 years per TORG Rev 2");
```

### 3. Version Control Integration

```java
// Tag design run with version info
orchestrator.setRunMetadata("git_commit", getGitCommitHash());
orchestrator.setRunMetadata("torg_revision", torg.getRevision());
orchestrator.setRunMetadata("analyst", System.getProperty("user.name"));
```

### 4. Reproducibility

```java
// Save complete configuration for reproducibility
orchestrator.saveConfiguration("design_config_2025-01-06.json");

// Later, reload and re-run
FieldDevelopmentDesignOrchestrator restored = 
    FieldDevelopmentDesignOrchestrator.loadConfiguration("design_config_2025-01-06.json");
restored.runCompleteDesignWorkflow();
```

## Complete Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.designstandards.*;
import neqsim.process.mechanicaldesign.torg.*;
import neqsim.thermo.system.SystemSrkEos;

public class FieldDevelopmentDesignExample {
    
    public static void main(String[] args) {
        // 1. Create fluid and process
        SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.04);
        fluid.addComponent("n-butane", 0.03);
        fluid.setMixingRule("classic");
        
        ProcessSystem process = new ProcessSystem();
        
        Stream feed = new Stream("Well Feed", fluid);
        feed.setFlowRate(50000.0, "kg/hr");
        process.add(feed);
        
        Separator hpSep = new Separator("HP Separator", feed);
        process.add(hpSep);
        
        Compressor exportComp = new Compressor("Export Compressor", hpSep.getGasOutStream());
        exportComp.setOutletPressure(150.0, "bara");
        process.add(exportComp);
        
        // 2. Create orchestrator
        FieldDevelopmentDesignOrchestrator orchestrator = 
            new FieldDevelopmentDesignOrchestrator(process);
        
        // 3. Configure for FEED phase
        orchestrator.setDesignPhase(DesignPhase.FEED);
        
        // 4. Add design cases
        orchestrator.addDesignCase(DesignCase.NORMAL);
        orchestrator.addDesignCase(DesignCase.MAXIMUM);
        orchestrator.addDesignCase(DesignCase.MINIMUM);
        orchestrator.addDesignCase(DesignCase.UPSET);
        orchestrator.addDesignCase(DesignCase.EARLY_LIFE);
        orchestrator.addDesignCase(DesignCase.LATE_LIFE);
        
        // 5. Load TORG
        TorgManager torgManager = new TorgManager();
        torgManager.addDataSource(new CsvTorgDataSource("project_torg.csv"));
        orchestrator.loadTorg(torgManager, "TROLL-WEST-2025");
        
        // 6. Run complete workflow
        orchestrator.runCompleteDesignWorkflow();
        
        // 7. Validate and report
        DesignValidationResult validation = orchestrator.validateDesign();
        System.out.println(validation.getSummary());
        
        if (validation.isValid()) {
            String report = orchestrator.generateDesignReport();
            System.out.println(report);
        } else {
            System.err.println("Design validation failed!");
            for (ValidationMessage msg : validation.getMessagesBySeverity(Severity.ERROR)) {
                System.err.println("  ERROR: " + msg.getMessage());
            }
        }
    }
}
```

## See Also

- [Mechanical Design Standards](mechanical_design_standards) - Available standards and categories
- [Mechanical Design Database](mechanical_design_database) - Data source configuration
- [TORG Integration](torg_integration) - Technical requirements documents
