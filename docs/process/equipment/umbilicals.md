---
title: Umbilicals
description: Documentation for subsea umbilical equipment in NeqSim.
---

# Umbilicals

Documentation for subsea umbilical equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Umbilical Class](#umbilical-class)
- [Umbilical Types](#umbilical-types)
- [Functional Elements](#functional-elements)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Design Standards](#design-standards)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.subsea`

An umbilical is a bundled assembly of tubes, hoses, and cables used to supply subsea equipment with:

- Hydraulic fluid for valve actuation
- Electrical power for equipment
- Control signals and data
- Chemicals (MEG, methanol, scale/corrosion inhibitors)

| Class | Description |
|-------|-------------|
| `Umbilical` | Subsea umbilical assembly |
| `UmbilicalMechanicalDesign` | Mechanical design calculations |

---

## Umbilical Class

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── Umbilical
    ├── enum: UmbilicalType
    ├── enum: CrossSectionType
    └── static class: UmbilicalElement
```

### Key Features

- **Multiple Construction Types**: Steel tube, thermoplastic, integrated
- **Element Management**: Hydraulic, chemical, electrical, fiber optic
- **Pressure/Temperature Rating**: Individual element specifications
- **Length-Based Calculations**: Pressure drop and power loss
- **Weight Estimation**: Dry and wet weights

### Constructor

```java
import neqsim.process.equipment.subsea.Umbilical;

// Create steel tube umbilical
Umbilical umbilical = new Umbilical("Main Umbilical");
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setLength(25000.0);  // 25 km
umbilical.setWaterDepth(450.0);
```

---

## Umbilical Types

### UmbilicalType Enumeration

| Type | Description | Application |
|------|-------------|-------------|
| `STEEL_TUBE` | Steel tubes for hydraulic/chemical | Modern standard |
| `THERMOPLASTIC` | Nylon/PE hoses | Legacy systems |
| `INTEGRATED_PRODUCTION` | Combined with small bore production | Compact systems |
| `ELECTRO_HYDRAULIC` | Combined hydraulic + electrical | Standard |
| `ELECTRIC_ONLY` | All-electric control | Future systems |

### Cross-Section Types

| Type | Description |
|------|-------------|
| `CIRCULAR` | Standard circular cross-section |
| `FLAT` | Ribbon/flat configuration |
| `BUNDLED` | Loose bundled elements |

### Selecting Umbilical Type

```java
// Modern steel tube umbilical
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setCrossSectionType(Umbilical.CrossSectionType.CIRCULAR);

// All-electric system
umbilical.setUmbilicalType(Umbilical.UmbilicalType.ELECTRIC_ONLY);
```

---

## Functional Elements

### UmbilicalElement Class

Each element within the umbilical has specific properties:

| Property | Description | Unit |
|----------|-------------|------|
| Type | hydraulic, chemical, electrical, fiber | - |
| ID/OD | Inner/outer diameter | mm |
| Design Pressure | Maximum working pressure | bar |
| Material | Construction material | - |
| Function | Specific purpose | - |

### Adding Hydraulic Lines

```java
// Add LP and HP hydraulic supply lines
umbilical.addHydraulicLine("LP Hydraulic", 12.7, 345.0);  // 12.7mm ID, 345 bar
umbilical.addHydraulicLine("HP Hydraulic", 9.5, 690.0);   // 9.5mm ID, 690 bar

// Hydraulic return line
umbilical.addHydraulicLine("Hydraulic Return", 19.0, 100.0);
```

### Adding Chemical Lines

```java
// MEG injection line
umbilical.addChemicalLine("MEG", 25.4, 150.0);  // 25.4mm ID, 150 bar

// Methanol injection
umbilical.addChemicalLine("Methanol", 19.0, 200.0);

// Scale inhibitor
umbilical.addChemicalLine("Scale Inhibitor", 12.7, 150.0);

// Corrosion inhibitor
umbilical.addChemicalLine("Corrosion Inhibitor", 9.5, 150.0);
```

### Adding Electrical Cables

```java
// Main power cable (3-phase, 6.6 kV)
umbilical.addElectricalCable("Power", 3, 6600.0);  // 3 cores, 6600V

// Auxiliary power
umbilical.addElectricalCable("Auxiliary Power", 3, 690.0);

// Control/signal cables
umbilical.addElectricalCable("Control", 7, 24.0);  // 7 cores, 24V
```

### Adding Fiber Optic Cables

```java
// Control and monitoring fiber
umbilical.addFiberOptic("Control", 12);  // 12 fibers

// Communication fiber
umbilical.addFiberOptic("Communication", 24);  // 24 fibers
```

---

## Configuration

### Basic Configuration

```java
import neqsim.process.equipment.subsea.Umbilical;

// Create and configure umbilical
Umbilical umbilical = new Umbilical("Production Umbilical");

// Set type and length
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setLength(30000.0);      // 30 km
umbilical.setWaterDepth(500.0);

// Design parameters
umbilical.setDesignLife(25);       // years
umbilical.setDesignTemperature(90.0);  // °C max

// Add functional elements
umbilical.addHydraulicLine("LP Hydraulic", 12.7, 345.0);
umbilical.addHydraulicLine("HP Hydraulic", 9.5, 690.0);
umbilical.addChemicalLine("MEG", 25.4, 150.0);
umbilical.addChemicalLine("Methanol", 19.0, 200.0);
umbilical.addElectricalCable("Power", 3, 6600.0);
umbilical.addFiberOptic("Control", 12);
```

### Flow Calculations

```java
// Set chemical injection rates
umbilical.setMEGInjectionRate(5.0, "m3/hr");
umbilical.setMethanolInjectionRate(2.0, "m3/hr");

// Calculate pressure drops
double megDeltaP = umbilical.calculatePressureDrop("MEG");
double methanolDeltaP = umbilical.calculatePressureDrop("Methanol");

System.out.println("MEG pressure drop: " + megDeltaP + " bar");
System.out.println("Methanol pressure drop: " + methanolDeltaP + " bar");
```

### Power Calculations

```java
// Set power transmission parameters
umbilical.setPowerTransmissionVoltage(6600.0);  // V
umbilical.setPowerLoad(5.0);  // MW

// Calculate losses
double voltageDrop = umbilical.calculateVoltageDrop();
double powerLoss = umbilical.calculatePowerLoss();

System.out.println("Voltage drop: " + voltageDrop + "%");
System.out.println("Power loss: " + powerLoss + " MW");
```

---

## Usage Examples

### Example 1: Complete Umbilical Configuration

```java
import neqsim.process.equipment.subsea.Umbilical;

// Create main production umbilical
Umbilical mainUmbilical = new Umbilical("Main Production Umbilical");
mainUmbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
mainUmbilical.setLength(25000.0);  // 25 km to manifold
mainUmbilical.setWaterDepth(450.0);

// Hydraulic supply/return
mainUmbilical.addHydraulicLine("LP Supply", 12.7, 345.0);
mainUmbilical.addHydraulicLine("HP Supply", 9.5, 690.0);
mainUmbilical.addHydraulicLine("LP Return", 15.9, 100.0);

// Chemical injection
mainUmbilical.addChemicalLine("MEG Injection", 25.4, 150.0);
mainUmbilical.addChemicalLine("Methanol", 19.0, 200.0);
mainUmbilical.addChemicalLine("Scale Inhibitor", 12.7, 150.0);
mainUmbilical.addChemicalLine("Corrosion Inhibitor", 9.5, 150.0);
mainUmbilical.addChemicalLine("LDHI", 9.5, 150.0);

// Electrical power
mainUmbilical.addElectricalCable("Main Power", 3, 6600.0);
mainUmbilical.addElectricalCable("Auxiliary Power", 3, 690.0);

// Fiber optics
mainUmbilical.addFiberOptic("Control/Communication", 24);
mainUmbilical.addFiberOptic("Spare", 12);

// Get summary
System.out.println("Umbilical: " + mainUmbilical.getName());
System.out.println("Length: " + mainUmbilical.getLength() / 1000 + " km");
System.out.println("Elements: " + mainUmbilical.getElementCount());
```

### Example 2: Chemical Injection Sizing

```java
// Umbilical for MEG injection system
Umbilical megUmbilical = new Umbilical("MEG Supply");
megUmbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
megUmbilical.setLength(30000.0);

// Main MEG line - sized for injection rate
double megRate = 8.0;  // m³/hr required injection rate
double velocity = 2.0;  // m/s target velocity

// Calculate required diameter
double areaRequired = (megRate / 3600.0) / velocity;  // m²
double diameterRequired = Math.sqrt(4 * areaRequired / Math.PI) * 1000;  // mm

System.out.println("Required MEG line ID: " + diameterRequired + " mm");

// Add MEG line (use next standard size)
umbilical.addChemicalLine("MEG", 32.0, 200.0);  // 32mm ID

// Calculate actual pressure drop
megUmbilical.setMEGInjectionRate(megRate, "m3/hr");
double actualDeltaP = megUmbilical.calculatePressureDrop("MEG");

System.out.println("MEG pressure drop over " + megUmbilical.getLength()/1000 + " km: " + actualDeltaP + " bar");
```

### Example 3: Power Transmission Design

```java
// Long step-out umbilical for subsea booster
Umbilical powerUmbilical = new Umbilical("Power Umbilical");
powerUmbilical.setUmbilicalType(Umbilical.UmbilicalType.ELECTRO_HYDRAULIC);
powerUmbilical.setLength(80000.0);  // 80 km
powerUmbilical.setWaterDepth(1200.0);

// Power cable for 6 MW booster
powerUmbilical.addElectricalCable("Main Power", 3, 36000.0);  // 36 kV transmission

// Set load parameters
powerUmbilical.setPowerLoad(6.0);  // MW
powerUmbilical.setPowerFactor(0.9);

// Calculate transmission losses
double voltageDrop = powerUmbilical.calculateVoltageDrop();
double losses = powerUmbilical.calculatePowerLoss();
double efficiency = (6.0 - losses) / 6.0 * 100;

System.out.println("Transmission voltage: 36 kV");
System.out.println("Step-out distance: 80 km");
System.out.println("Voltage drop: " + String.format("%.1f", voltageDrop) + "%");
System.out.println("Power loss: " + String.format("%.2f", losses) + " MW");
System.out.println("Efficiency: " + String.format("%.1f", efficiency) + "%");
```

### Example 4: Umbilical with Mechanical Design

```java
import neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign;

// Create umbilical
Umbilical umbilical = new Umbilical("Export Umbilical");
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setLength(40000.0);
umbilical.setWaterDepth(800.0);

// Add elements
umbilical.addHydraulicLine("LP", 12.7, 345.0);
umbilical.addHydraulicLine("HP", 9.5, 690.0);
umbilical.addChemicalLine("MEG", 25.4, 150.0);
umbilical.addElectricalCable("Power", 3, 6600.0);
umbilical.addFiberOptic("Control", 24);

// Get mechanical design
UmbilicalMechanicalDesign design = umbilical.getMechanicalDesign();

// Configure
design.setArmorType("Double Cross-Wound");
design.setOuterSheath("HDPE");
design.setCorrosionProtection("Galvanized armor + cathodic");

// Calculate design
design.calcDesign();

// Get results
System.out.println("Outer diameter: " + design.getOuterDiameter() + " mm");
System.out.println("Dry weight: " + design.getDryWeight() + " kg/m");
System.out.println("Wet weight: " + design.getWetWeight() + " kg/m");
System.out.println("MBR: " + design.getMinimumBendRadius() + " m");
System.out.println("MWS: " + design.getMaxWallTension() + " kN");

// Get JSON report
String report = design.toJson();
```

---

## Design Standards

### Applicable Standards

| Standard | Title |
|----------|-------|
| API RP 17E | Subsea Umbilicals |
| API Spec 17E | Specification for Subsea Production Control Umbilicals |
| ISO 13628-5 | Subsea Umbilicals |
| DNV-ST-F101 | Submarine Pipeline Systems (for steel tubes) |

### Design Parameters

| Parameter | Typical Range | Unit |
|-----------|--------------|------|
| Length | 5-150 | km |
| Water Depth | 100-3000 | m |
| Outer Diameter | 80-250 | mm |
| Dry Weight | 15-100 | kg/m |
| MBR | 2-5 | m |
| Design Life | 20-30 | years |

### Material Selection

| Component | Material | Application |
|-----------|----------|-------------|
| Steel Tubes | Super Duplex 25Cr | Hydraulic/chemical |
| Thermoplastic Hoses | PA11, PVDF | Legacy systems |
| Electrical Cables | EPR/XLPE insulation | Power/signal |
| Fiber Optics | Single/multi-mode | Control/comms |
| Armor | Galvanized steel | Mechanical protection |
| Outer Sheath | HDPE, PA11 | Abrasion protection |

---

## Typical Umbilical Cross-Section

```
         ┌─────────────────────┐
         │    Outer Sheath     │
         │  ┌───────────────┐  │
         │  │    Armor      │  │
         │  │  ┌─────────┐  │  │
         │  │  │ LP Hyd  │  │  │
         │  │  │  ┌───┐  │  │  │
         │  │  │  │HP │  │  │  │
         │  │  │  │Hyd│  │  │  │
         │  │  │  └───┘  │  │  │
         │  │  │ MEG  CH │  │  │
         │  │  │ ┌──┐┌──┐│  │  │
         │  │  │ │  ││  ││  │  │
         │  │  │ └──┘└──┘│  │  │
         │  │  │ Power   │  │  │
         │  │  │ ┌────┐  │  │  │
         │  │  │ │Fiber│ │  │  │
         │  │  │ └────┘  │  │  │
         │  │  └─────────┘  │  │
         │  └───────────────┘  │
         └─────────────────────┘
```

---

## Related Documentation

- [Subsea Trees](subsea_trees) - Christmas tree modeling
- [Subsea Manifolds](subsea_manifolds) - Manifold modeling
- [Subsea Boosters](subsea_boosters) - Boosting systems
- [PLEM/PLET](plem_plet) - Pipeline end structures

---

## API Reference

### Umbilical

```java
// Constructors
Umbilical(String name)

// Type configuration
void setUmbilicalType(UmbilicalType type)
UmbilicalType getUmbilicalType()
void setCrossSectionType(CrossSectionType type)
CrossSectionType getCrossSectionType()

// Physical parameters
void setLength(double meters)
double getLength()
void setWaterDepth(double meters)
void setDesignLife(int years)
void setDesignTemperature(double celsius)

// Adding elements
void addHydraulicLine(String name, double idMm, double pressureBar)
void addChemicalLine(String name, double idMm, double pressureBar)
void addElectricalCable(String name, int cores, double voltage)
void addFiberOptic(String name, int fibers)

// Element management
int getElementCount()
List<UmbilicalElement> getElements()

// Flow calculations
void setMEGInjectionRate(double rate, String unit)
void setMethanolInjectionRate(double rate, String unit)
double calculatePressureDrop(String chemicalName)

// Power calculations
void setPowerTransmissionVoltage(double volts)
void setPowerLoad(double megawatts)
void setPowerFactor(double pf)
double calculateVoltageDrop()
double calculatePowerLoss()

// Mechanical design
UmbilicalMechanicalDesign getMechanicalDesign()
```

### UmbilicalType Enum

```java
enum UmbilicalType {
    STEEL_TUBE,
    THERMOPLASTIC,
    INTEGRATED_PRODUCTION,
    ELECTRO_HYDRAULIC,
    ELECTRIC_ONLY
}
```

### CrossSectionType Enum

```java
enum CrossSectionType {
    CIRCULAR,
    FLAT,
    BUNDLED
}
```

### UmbilicalElement Class

```java
class UmbilicalElement {
    String getName()
    String getType()  // hydraulic, chemical, electrical, fiber
    double getInnerDiameter()
    double getOuterDiameter()
    double getDesignPressure()
    String getMaterial()
    String getFunction()
}
```
