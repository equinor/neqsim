---
title: Mechanical Design Standards in NeqSim
description: NeqSim provides comprehensive support for international design standards used in process equipment mechanical design. The framework enables engineers to apply company-specific and international standa...
---

# Mechanical Design Standards in NeqSim

## Overview

NeqSim provides comprehensive support for international design standards used in process equipment mechanical design. The framework enables engineers to apply company-specific and international standards consistently across all equipment in a process simulation.

## Supported Design Standards

### StandardType Enumeration

The `StandardType` enum catalogs 30+ international design standards organized by category:

| Category | Standards |
|----------|-----------|
| **Pressure Vessel Codes** | ASME Section VIII Div.1/2, EN 13445, PD 5500, DNV-OS-F101 |
| **Piping Codes** | ASME B31.3, ASME B31.4, ASME B31.8, EN 13480, NORSOK L-002 |
| **Process Design** | NORSOK P-001, NORSOK P-002, API RP 14E, API RP 521 |
| **Material Standards** | ASTM A516, ASTM A106, EN 10028, NORSOK M-001 |
| **Safety Standards** | API RP 520, API RP 521, ISO 23251 |

### Using StandardType

```java
import neqsim.process.mechanicaldesign.designstandards.StandardType;

// Get standard by code
StandardType standard = StandardType.fromCode("ASME-VIII-1");

// Get standard properties
String code = standard.getCode();           // "ASME-VIII-1"
String name = standard.getName();           // "ASME Section VIII Division 1"
String version = standard.getDefaultVersion(); // "2023"
String category = standard.getDesignStandardCategory(); // "pressure vessel design code"

// Check equipment applicability
boolean applies = standard.appliesTo("separator"); // true
boolean applies2 = standard.appliesTo("pump");     // false

// Get all standards for an equipment type
List<StandardType> applicable = StandardType.getApplicableStandards("compressor");
```

## Standard Categories

NeqSim uses category-based standard assignment to ensure appropriate standards are applied to each equipment type:

| Category Key | Description | Example Standards |
|--------------|-------------|-------------------|
| `pressure vessel design code` | Pressure containment design | ASME VIII, EN 13445 |
| `separator process design` | Separator sizing rules | NORSOK P-002, API 12J |
| `compressor design` | Compressor design requirements | API 617, API 618 |
| `pipeline design codes` | Pipeline design | DNV-OS-F101, ASME B31.4 |
| `valve design` | Valve sizing and selection | API 6D, EN ISO 10497 |
| `material plate design` | Plate material selection | ASTM A516, EN 10028 |
| `material pipe design` | Pipe material selection | ASTM A106, API 5L |

## StandardRegistry

The `StandardRegistry` class provides factory methods for creating `DesignStandard` instances:

```java
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;

// Create a design standard from StandardType
DesignStandard standard = StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1);

// Create with specific version
DesignStandard standard2 = StandardRegistry.createStandard(StandardType.NORSOK_P002, "Rev 3");

// Get recommended standards for equipment
List<StandardType> recommended = StandardRegistry.getRecommendedStandards("separator", "Equinor");
```

## Applying Standards to Equipment

### Single Equipment

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

// Create equipment
Separator separator = new Separator("HP Separator", feedStream);

// Get mechanical design
MechanicalDesign mechDesign = separator.getMechanicalDesign();

// Apply single standard
mechDesign.setDesignStandard(StandardType.ASME_VIII_DIV1);

// Apply standard with version
mechDesign.setDesignStandard(StandardType.NORSOK_P002, "Rev 3");

// Apply multiple standards
List<StandardType> standards = Arrays.asList(
    StandardType.ASME_VIII_DIV1,
    StandardType.NORSOK_P002,
    StandardType.ASTM_A516
);
mechDesign.setDesignStandards(standards);
```

### System-Wide Standards

```java
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;

// Create process system
ProcessSystem process = new ProcessSystem();
process.add(separator);
process.add(compressor);
process.add(heatExchanger);

// Apply company standards to all equipment
SystemMechanicalDesign sysMechDesign = new SystemMechanicalDesign(process);
sysMechDesign.setCompanySpecificDesignStandards("Equinor");

// Run design calculations
sysMechDesign.runDesignCalculation();
```

## Design Standard Hierarchy

Standards are applied hierarchically based on specificity:

```
1. Equipment-specific standard (highest priority)
   ↓
2. TORG project standards
   ↓
3. Company default standards
   ↓
4. NeqSim default standards (lowest priority)
```

## Available Design Standard Classes

NeqSim includes specialized design standard implementations:

| Class | Purpose |
|-------|---------|
| `PressureVesselDesignStandard` | ASME/EN pressure vessel calculations |
| `SeparatorDesignStandard` | Separator sizing per NORSOK/API |
| `CompressorDesignStandard` | Compressor design per API 617/618 |
| `PipelineDesignStandard` | Pipeline wall thickness per DNV/ASME |
| `MaterialPlateDesignStandard` | Plate material properties |
| `MaterialPipeDesignStandard` | Pipe material properties |
| `JointEfficiencyPlateStandard` | Weld joint efficiency factors |
| `GasScrubberDesignStandard` | Gas scrubber sizing rules |
| `AdsorptionDehydrationDesignStandard` | Dehydration unit design |

## Example: Complete Standard Application

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Create feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000, "kg/hr");
feed.setTemperature(25, "C");
feed.setPressure(50, "bara");

// Create equipment with standards
Separator separator = new Separator("HP Separator", feed);
separator.getMechanicalDesign().setDesignStandard(StandardType.ASME_VIII_DIV1);
separator.getMechanicalDesign().setDesignStandard(StandardType.NORSOK_P002);

Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(150, "bara");
compressor.getMechanicalDesign().setDesignStandard(StandardType.API_617);

// Build process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.run();

// Run mechanical design
SystemMechanicalDesign sysMechDesign = new SystemMechanicalDesign(process);
sysMechDesign.runDesignCalculation();

// Get results
System.out.println("Total Weight: " + sysMechDesign.getTotalWeight() + " kg");
System.out.println("Total Volume: " + sysMechDesign.getTotalVolume() + " m³");
```

## See Also

- [Mechanical Design Database](mechanical_design_database) - Data sources for design parameters
- [TORG Document Integration](torg_integration) - Technical Requirements Documents
- [Field Development Orchestration](field_development_orchestration) - Complete design workflows
