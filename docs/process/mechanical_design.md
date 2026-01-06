# Mechanical Design Framework

NeqSim provides a comprehensive mechanical design framework for sizing and specifying process equipment according to industry standards. This document describes the architecture, usage patterns, and JSON export capabilities.

## Overview

The mechanical design system calculates:
- **Equipment sizing** - Vessel dimensions, wall thickness, nozzle sizes
- **Weight estimation** - Empty, operating, and test weights with breakdowns
- **Design conditions** - Pressure and temperature with appropriate margins
- **Module dimensions** - Plot space requirements for installation planning
- **Utility requirements** - Power consumption, heating/cooling duties

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
├── PipelineMechanicalDesign       → ASME B31.3
├── AdsorberMechanicalDesign       → ASME VIII
├── AbsorberMechanicalDesign       → ASME VIII
├── EjectorMechanicalDesign        → HEI
└── SafetyValveMechanicalDesign    → API 520/521
```

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

Each mechanical design class has an associated cost estimation class:

```java
// Access cost estimate
UnitCostEstimateBaseClass costEstimate = mecDesign.getCostEstimate();
double equipmentCost = costEstimate.getEquipmentCost();    // USD
double installedCost = costEstimate.getInstalledCost();    // USD
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

- [Process Equipment](../process/README.md)
- [Cost Estimation](../process/cost_estimation.md)
- [Design Standards](../standards/README.md)
