# Mechanical Design Framework

NeqSim provides a comprehensive mechanical design framework for sizing and specifying process equipment according to industry standards. This document describes the architecture, usage patterns, and JSON export capabilities.

> **📘 Related Documentation**
>
> | Topic | Documentation |
> |-------|---------------|
> | **Pipelines** | [Pipeline Mechanical Design](pipeline_mechanical_design.md) - Wall thickness, stress analysis, cost estimation |
> | **Mathematical Methods** | [Pipeline Design Math](pipeline_mechanical_design_math.md) - Complete formula reference |
> | **Design Standards** | [Mechanical Design Standards](mechanical_design_standards.md) - Industry standards reference |
> | **Database** | [Mechanical Design Database](mechanical_design_database.md) - Material properties, design factors |
> | **Cost Estimation** | [COST_ESTIMATION_FRAMEWORK.md](COST_ESTIMATION_FRAMEWORK.md) - CAPEX, OPEX, currency, location factors |
> | **Design Parameters** | [EQUIPMENT_DESIGN_PARAMETERS.md](EQUIPMENT_DESIGN_PARAMETERS.md) - autoSize vs manual sizing guide |

## Overview

The mechanical design system calculates:
- **Equipment sizing** - Vessel dimensions, wall thickness, nozzle sizes
- **Weight estimation** - Empty, operating, and test weights with breakdowns
- **Design conditions** - Pressure and temperature with appropriate margins
- **Module dimensions** - Plot space requirements for installation planning
- **Utility requirements** - Power consumption, heating/cooling duties
- **Cost estimation** - Material, fabrication, installation, and project costs
- **Bill of Materials** - Complete BOM with quantities and costs

## Architecture

### Class Hierarchy

```
MechanicalDesign (base class)
├── SeparatorMechanicalDesign      → ASME VIII / API 12J
├── GasScrubberMechanicalDesign    → ASME VIII / API 12J
├── CompressorMechanicalDesign     → API 617
├── PumpMechanicalDesign           → API 610
├── ValveMechanicalDesign          → IEC 60534 / ANSI/ISA-75
├── ExpanderMechanicalDesign       → API 617
├── TankMechanicalDesign           → API 650/620
├── HeatExchangerMechanicalDesign  → TEMA
├── PipelineMechanicalDesign       → ASME B31.3/B31.4/B31.8, DNV-OS-F101, API 5L
│   └── PipeMechanicalDesignCalculator (wall thickness, stress, cost)
├── AdsorberMechanicalDesign       → ASME VIII
├── AbsorberMechanicalDesign       → ASME VIII
├── EjectorMechanicalDesign        → HEI
└── SafetyValveMechanicalDesign    → API 520/521
```

### Pipeline Mechanical Design Features

The `PipelineMechanicalDesign` class provides comprehensive pipeline design including:

| Feature | Description |
|---------|-------------|
| **Wall Thickness** | ASME B31.3/B31.4/B31.8, DNV-OS-F101 calculations |
| **Stress Analysis** | Hoop, longitudinal, von Mises stress |
| **External Pressure** | Collapse and propagation buckling |
| **Weight/Buoyancy** | Steel, coating, concrete, contents |
| **Thermal Design** | Expansion loops, insulation sizing |
| **Structural Design** | Support spacing, spans, bend radius |
| **Fatigue Analysis** | S-N curves per DNV-RP-C203 |
| **Cost Estimation** | Complete project cost with BOM |

See [Pipeline Mechanical Design](pipeline_mechanical_design.md) for details.

### Response Classes for JSON Export

```
MechanicalDesignResponse (base class)
├── CompressorMechanicalDesignResponse
├── PumpMechanicalDesignResponse
├── ValveMechanicalDesignResponse
├── SeparatorMechanicalDesignResponse
└── HeatExchangerMechanicalDesignResponse
```

### System-Level Aggregation

```
SystemMechanicalDesign
└── Aggregates all equipment in a ProcessSystem
    ├── Total weights and volumes
    ├── Weight breakdown by equipment type
    ├── Weight breakdown by discipline
    ├── Utility requirements summary
    └── Equipment list with design parameters
```

## Usage Patterns

### Individual Equipment Design

```java
// Create and run process equipment
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

Stream inlet = new Stream("feed", fluid);
inlet.setFlowRate(10000.0, "kg/hr");
inlet.run();

Separator separator = new Separator("V-100", inlet);
separator.run();

// Access mechanical design
MechanicalDesign mecDesign = separator.getMechanicalDesign();

// Set design standards (optional - uses defaults if not specified)
mecDesign.setCompanySpecificDesignStandards("Equinor");

// Calculate design
mecDesign.calcDesign();

// Access results
double weight = mecDesign.getWeightTotal();           // kg
double wallThickness = mecDesign.getWallThickness();  // mm
double innerDiameter = mecDesign.getInnerDiameter();  // m
double length = mecDesign.getTantanLength();          // m
double designPressure = mecDesign.getMaxDesignPressure(); // bara

// Display results in GUI
mecDesign.displayResults();
```

### System-Wide Mechanical Design

```java
// Build a process system
ProcessSystem process = new ProcessSystem();
process.add(inlet);
process.add(separator);
process.add(gasStream);
process.add(compressor);
process.add(cooler);
process.add(outlet);
process.run();

// Create system mechanical design
SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);

// Set company standards for all equipment
sysMecDesign.setCompanySpecificDesignStandards("Equinor");

// Run design calculations for all equipment
sysMecDesign.runDesignCalculation();

// Access aggregated results
double totalWeight = sysMecDesign.getTotalWeight();           // kg
double totalVolume = sysMecDesign.getTotalVolume();           // m³
double plotSpace = sysMecDesign.getTotalPlotSpace();          // m²
double powerRequired = sysMecDesign.getTotalPowerRequired();  // kW
double heatingDuty = sysMecDesign.getTotalHeatingDuty();      // kW
double coolingDuty = sysMecDesign.getTotalCoolingDuty();      // kW

// Get breakdowns
Map<String, Double> weightByType = sysMecDesign.getWeightByEquipmentType();
Map<String, Double> weightByDiscipline = sysMecDesign.getWeightByDiscipline();
Map<String, Integer> countByType = sysMecDesign.getEquipmentCountByType();

// Print summary report
System.out.println(sysMecDesign.generateSummaryReport());
```

## JSON Export

### Exporting Individual Equipment

```java
// Calculate design
separator.getMechanicalDesign().calcDesign();

// Export to JSON
String json = separator.getMechanicalDesign().toJson();

// Example output:
/*
{
  "name": "V-100",
  "equipmentType": "Separator",
  "equipmentClass": "Separator",
  "designStandard": "ASME VIII / API 12J",
  "isSystemLevel": false,
  "totalWeight": 15420.5,
  "vesselWeight": 8500.0,
  "internalsWeight": 1200.0,
  "pipingWeight": 2100.0,
  "eiWeight": 1500.0,
  "structuralWeight": 2120.5,
  "maxDesignPressure": 55.0,
  "maxDesignTemperature": 80.0,
  "innerDiameter": 2.4,
  "tangentLength": 7.2,
  "wallThickness": 28.5,
  "moduleLength": 10.0,
  "moduleWidth": 5.0,
  "moduleHeight": 4.5,
  ...
}
*/
```

### Exporting System-Wide Design

```java
SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
sysMecDesign.runDesignCalculation();

String json = sysMecDesign.toJson();

// Example output:
/*
{
  "isSystemLevel": true,
  "processName": "Gas Processing Unit",
  "equipmentCount": 12,
  "totalWeight": 185000.0,
  "totalVolume": 450.5,
  "totalPlotSpace": 1200.0,
  "totalPowerRequired": 2500.0,
  "totalPowerRecovered": 150.0,
  "netPower": 2350.0,
  "totalHeatingDuty": 500.0,
  "totalCoolingDuty": 1800.0,
  "footprintLength": 40.0,
  "footprintWidth": 30.0,
  "maxHeight": 15.0,
  "weightByType": {
    "Separator": 45000.0,
    "Compressor": 85000.0,
    "HeatExchanger": 25000.0,
    "Valve": 5000.0,
    "Pump": 12000.0,
    "Other": 13000.0
  },
  "weightByDiscipline": {
    "Mechanical": 120000.0,
    "Piping": 35000.0,
    "E&I": 18000.0,
    "Structural": 12000.0
  },
  "equipmentList": [
    {
      "name": "V-100",
      "type": "Separator",
      "weight": 15420.5,
      "designPressure": 55.0,
      "designTemperature": 80.0,
      "power": 0.0,
      "duty": 0.0,
      "dimensions": "ID 2.4m x TT 7.2m"
    },
    ...
  ]
}
*/
```

### Comprehensive Mechanical Design Report (JSON)

The `MechanicalDesignReport` class provides a combined JSON output that includes all mechanical design data for a process system, similar to how `Report.generateJsonReport()` works for process simulation:

```java
// Create comprehensive mechanical design report
MechanicalDesignReport mechReport = new MechanicalDesignReport(process);
mechReport.runDesignCalculations();

// Generate combined JSON with all mechanical design data
String json = mechReport.toJson();

// Write to file
mechReport.writeJsonReport("mechanical_design_report.json");

// Example output structure:
/*
{
  "processName": "Gas Processing Unit",
  "reportType": "MechanicalDesignReport",
  "generatedAt": "2026-01-11T10:30:00Z",
  "systemSummary": {
    "totalEquipmentWeight_kg": 185000.0,
    "totalPipingWeight_kg": 35000.0,
    "totalWeight_kg": 220000.0,
    "totalVolume_m3": 450.5,
    "totalPlotSpace_m2": 1200.0,
    "equipmentCount": 12
  },
  "utilityRequirements": {
    "totalPowerRequired_kW": 2500.0,
    "totalPowerRecovered_kW": 150.0,
    "netPowerRequirement_kW": 2350.0,
    "totalHeatingDuty_kW": 500.0,
    "totalCoolingDuty_kW": 1800.0
  },
  "weightByEquipmentType": {
    "Separator": 45000.0,
    "Compressor": 85000.0,
    "HeatExchanger": 25000.0,
    "Valve": 5000.0,
    "Pump": 12000.0
  },
  "weightByDiscipline": {
    "Mechanical": 120000.0,
    "Piping": 35000.0,
    "E&I": 18000.0,
    "Structural": 12000.0
  },
  "equipment": [
    {
      "name": "V-100",
      "type": "Separator",
      "mechanicalDesign": {
        "designPressure": 55.0,
        "designTemperature": 80.0,
        "wallThickness": 28.5,
        "weight": 15420.5,
        ...
      }
    },
    ...
  ],
  "pipingDesign": {
    "totalLength_m": 450.0,
    "totalWeight_kg": 35000.0,
    "valveWeight_kg": 8500.0,
    "flangeWeight_kg": 4200.0,
    "fittingWeight_kg": 3100.0,
    "weightBySize": {
      "4 inch": 5200.0,
      "6 inch": 8400.0,
      "8 inch": 12300.0,
      ...
    },
    "pipeSegments": [
      {
        "fromEquipment": "V-100",
        "toEquipment": "K-100",
        "nominalSizeInch": 8.0,
        "outsideDiameter_mm": 219.1,
        "wallThickness_mm": 8.18,
        "schedule": "40",
        "length_m": 25.0,
        "weight_kg": 1050.0,
        "designPressure_bara": 55.0,
        "material": "A106-B",
        "isGasService": true
      },
      ...
    ]
  }
}
*/
```

#### Comparison: Process Simulation vs Mechanical Design JSON

| Use Case | Class | Method |
|----------|-------|--------|
| Process simulation results | `Report` | `generateJsonReport()` |
| System mechanical design only | `SystemMechanicalDesign` | `toJson()` |
| **Complete mechanical design with piping** | `MechanicalDesignReport` | `toJson()` |

The `MechanicalDesignReport.toJson()` method provides the most comprehensive output, combining:
- System-level aggregation from `SystemMechanicalDesign`
- Individual equipment mechanical design data
- Piping interconnection design from `ProcessInterconnectionDesign`

### Using Specialized Response Classes

For equipment-specific data, use the typed response:

```java
// Compressor-specific response
CompressorMechanicalDesignResponse response = 
    (CompressorMechanicalDesignResponse) compressor.getMechanicalDesign().getResponse();

int stages = response.getNumberOfStages();
double impellerDiameter = response.getImpellerDiameter();  // mm
double tipSpeed = response.getTipSpeed();                   // m/s
double driverPower = response.getDriverPower();             // kW
double tripSpeed = response.getTripSpeed();                 // rpm

// Valve-specific response
ValveMechanicalDesignResponse valveResponse = 
    (ValveMechanicalDesignResponse) valve.getMechanicalDesign().getResponse();

int ansiClass = valveResponse.getAnsiPressureClass();
double cvMax = valveResponse.getCvMax();
double faceToFace = valveResponse.getFaceToFace();  // mm
String valveType = valveResponse.getValveType();
```

### Round-Trip Parsing

```java
// Export to JSON
String json = sysMecDesign.toJson();

// Parse back to object
MechanicalDesignResponse parsed = MechanicalDesignResponse.fromJson(json);

// Access parsed data
double weight = parsed.getTotalWeight();
boolean isSystem = parsed.isSystemLevel();
```

### Merging with Process Data

```java
// Get mechanical design response
MechanicalDesignResponse mecResponse = sysMecDesign.getResponse();

// Get process simulation JSON
String processJson = process.toJson();

// Merge into combined document
String combined = mecResponse.mergeWithEquipmentJson(processJson);

// Result has both "processData" and "mechanicalDesign" sections
```

## Equipment-Specific Design Standards

### Separators (API 12J / ASME VIII)

```java
SeparatorMechanicalDesign sepDesign = 
    (SeparatorMechanicalDesign) separator.getMechanicalDesign();

// Key parameters
double gasLoadFactor = sepDesign.getGasLoadFactor();      // K-factor
double retentionTime = sepDesign.getRetentionTime();      // seconds
double liquidLevelFraction = sepDesign.getFg();           // Fg factor
```

Design calculations include:
- Gas capacity based on Souders-Brown equation
- Liquid retention time requirements
- Vessel L/D optimization
- Demister sizing
- Nozzle sizing per API RP 14E

### Compressors (API 617)

```java
CompressorMechanicalDesign compDesign = 
    (CompressorMechanicalDesign) compressor.getMechanicalDesign();

// Key parameters
int stages = compDesign.getNumberOfStages();
double headPerStage = compDesign.getHeadPerStage();       // kJ/kg
double impellerDia = compDesign.getImpellerDiameter();    // mm
double tipSpeed = compDesign.getTipSpeed();                // m/s
double driverPower = compDesign.getDriverPower();          // kW
```

Design calculations include:
- Number of stages based on max head per stage (30 kJ/kg typical)
- Impeller sizing based on flow coefficient
- Driver margin per API 617 (10-25% depending on power)
- Casing type selection (barrel vs split)
- Rotordynamic estimates (critical speeds)

### Pumps (API 610)

```java
PumpMechanicalDesign pumpDesign = 
    (PumpMechanicalDesign) pump.getMechanicalDesign();

// Key parameters
double specificSpeed = pumpDesign.getSpecificSpeed();
double npshRequired = pumpDesign.getNpshRequired();        // m
double impellerDia = pumpDesign.getImpellerDiameter();     // mm
double driverPower = pumpDesign.getDriverPower();          // kW
```

Design calculations include:
- Pump type selection (OH, BB, VS) based on application
- Impeller sizing from affinity laws
- NPSH margin verification
- Driver margin per API 610 (10-25%)
- Seal type selection

### Valves (IEC 60534)

```java
ValveMechanicalDesign valveDesign = 
    (ValveMechanicalDesign) valve.getMechanicalDesign();

// Key parameters
double cvMax = valveDesign.getValveCvMax();
int ansiClass = valveDesign.getAnsiPressureClass();
double faceToFace = valveDesign.getFaceToFace();           // mm
double actuatorThrust = valveDesign.getRequiredActuatorThrust(); // N
```

Design calculations include:
- Cv/Kv sizing per IEC 60534
- ANSI pressure class selection
- Body sizing and wall thickness
- Actuator sizing
- Face-to-face dimensions per ANSI/ISA

### Heat Exchangers (TEMA)

Design calculations include:
- Heat transfer area calculation
- Tube count and layout
- Shell diameter sizing
- Baffle spacing optimization
- Pressure drop verification

### Tanks (API 650/620)

Design calculations include:
- Shell course thickness
- Bottom plate sizing
- Roof type selection
- Wind/seismic loads
- Foundation requirements

## Weight Breakdown Categories

### By Equipment Type
- Separator
- Compressor
- Pump
- Valve
- HeatExchanger
- Tank
- Expander
- Pipeline
- Other

### By Discipline
- **Mechanical** - Vessel shells, rotating equipment, internals
- **Piping** - Process piping, valves, fittings
- **E&I** - Electrical, instrumentation, control systems
- **Structural** - Steel supports, platforms, ladders

## Design Margins

The framework applies industry-standard margins:

| Parameter | Margin | Standard |
|-----------|--------|----------|
| Design Pressure | +10% above max operating | ASME VIII |
| Design Temperature | +30°C above max operating | ASME VIII |
| Driver Power (small) | +25% for < 22 kW | API 610/617 |
| Driver Power (medium) | +15% for 22-75 kW | API 610/617 |
| Driver Power (large) | +10% for > 75 kW | API 610/617 |
| Wall Thickness | +CA (corrosion allowance) | ASME VIII |

## Integration with Cost Estimation

Each mechanical design class has an associated cost estimation class in `neqsim.process.costestimation`:

```java
// Access cost estimate from mechanical design
UnitCostEstimateBaseClass costEstimate = mecDesign.getCostEstimate();
double equipmentCost = costEstimate.getEquipmentCost();    // USD
double installedCost = costEstimate.getInstalledCost();    // USD
```

### Comprehensive Cost Estimation Framework

For detailed cost estimation including OPEX, financial metrics, currency conversion, and location factors, see the dedicated cost estimation documentation:

| Document | Description |
|----------|-------------|
| [COST_ESTIMATION_FRAMEWORK.md](COST_ESTIMATION_FRAMEWORK.md) | **Comprehensive guide to capital and operating cost estimation** |
| [COST_ESTIMATION_API_REFERENCE.md](COST_ESTIMATION_API_REFERENCE.md) | **Detailed API reference for all cost estimation classes** |

**Key Features:**
- Equipment costs using Turton et al., Peters & Timmerhaus, GPSA correlations
- 14+ equipment types (separators, compressors, heat exchangers, tanks, expanders, ejectors, absorbers, etc.)
- Multi-currency support (USD, EUR, NOK, GBP, CNY, JPY)
- Location factors for 11 global regions
- Operating cost (OPEX) calculation with utility costs
- Financial metrics (payback period, ROI, NPV)
- Process-level cost aggregation with `ProcessCostEstimate`

```java
// Example: Process-level cost estimation
ProcessCostEstimate processCost = new ProcessCostEstimate(process);

// Set location and currency
processCost.setLocationByRegion("North Sea");
processCost.setCurrency("NOK");

// Calculate costs
processCost.calculateCosts();

// Get results in selected currency
double totalCAPEX = processCost.getTotalCapitalCost();  // NOK
double totalOPEX = processCost.calculateOperatingCost(8760);  // NOK/year

// Export comprehensive JSON report
String json = processCost.toJson();
```

## Best Practices

1. **Always run equipment before calculating design** - The mechanical design uses process conditions from the simulation.

2. **Set design standards early** - Call `setCompanySpecificDesignStandards()` before `calcDesign()`.

3. **Use system-level design for complete estimates** - `SystemMechanicalDesign` handles all equipment consistently.

4. **Export JSON for documentation** - The `toJson()` method provides comprehensive, structured output.

5. **Verify critical parameters** - Check that design pressure/temperature exceed operating conditions.

## Example: Complete Workflow

```java
// 1. Create fluid system
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// 2. Build process
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");

Separator separator = new Separator("V-100", feed);

Stream gas = new Stream("gas", separator.getGasOutStream());

Compressor compressor = new Compressor("K-100", gas);
compressor.setOutletPressure(80.0, "bara");

Cooler cooler = new Cooler("E-100", compressor.getOutletStream());
cooler.setOutTemperature(40.0, "C");

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(gas);
process.add(compressor);
process.add(cooler);
process.run();

// 3. Calculate mechanical design
SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
sysMecDesign.setCompanySpecificDesignStandards("Equinor");
sysMecDesign.runDesignCalculation();

// 4. Generate reports
System.out.println(sysMecDesign.generateSummaryReport());

// 5. Export JSON for documentation/integration
String json = sysMecDesign.toJson();
Files.write(Paths.get("mechanical_design.json"), json.getBytes());

// 6. Access specific equipment details
CompressorMechanicalDesignResponse compResponse = 
    (CompressorMechanicalDesignResponse) compressor.getMechanicalDesign().getResponse();
System.out.println("Compressor stages: " + compResponse.getNumberOfStages());
System.out.println("Driver power: " + compResponse.getDriverPower() + " kW");
```

## See Also

- [Process Equipment](README.md)
- [Pipeline Mechanical Design](pipeline_mechanical_design.md)
- [Design Standards](../standards/README.md)
