---
title: Subsea Trees
description: Documentation for subsea Christmas tree equipment in NeqSim for subsea production modeling.
---

# Subsea Trees

Documentation for subsea Christmas tree equipment in NeqSim for subsea production modeling.

## Table of Contents
- [Overview](#overview)
- [SubseaTree Class](#subseatree-class)
- [Tree Types](#tree-types)
- [Valve Configuration](#valve-configuration)
- [Pressure Ratings](#pressure-ratings)
- [Usage Examples](#usage-examples)
- [Design Standards](#design-standards)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.subsea`

A subsea tree (Christmas tree) is a system of valves and connectors installed on top of a subsea wellhead to control production from the well. The `SubseaTree` class models:

- Well control and shut-in capability
- Production and annulus access
- Chemical injection points
- Pressure and temperature monitoring
- Flowline connection interface

| Class | Description |
|-------|-------------|
| `SubseaTree` | Subsea Christmas tree with valve controls |
| `SubseaTreeMechanicalDesign` | Mechanical design calculations |

---

## SubseaTree Class

### Class Hierarchy

```
TwoPortEquipment
└── SubseaTree
    ├── enum: TreeType
    ├── enum: PressureRating
    └── contains: ThrottlingValve (choke)
```

### Key Features

- **Multiple Tree Types**: Vertical, horizontal, dual bore, mudline
- **API Pressure Ratings**: 5000, 10000, 15000, 20000 psi
- **Valve Modeling**: Master, wing, and choke valves
- **Thermal Analysis**: Cooldown and heating considerations
- **Flow Assurance**: Hydrate and wax temperature monitoring

### Constructor

```java
import neqsim.process.equipment.subsea.SubseaTree;
import neqsim.process.equipment.stream.StreamInterface;

// Create subsea tree with well stream
SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);

// Configure tree type and design
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setWaterDepth(450.0);
tree.setDesignPressure(690.0);  // 10000 psi
tree.setDesignTemperature(150.0);
```

---

## Tree Types

### TreeType Enumeration

| Type | Description | Application |
|------|-------------|-------------|
| `VERTICAL` | Conventional tree, tubing hanger in wellhead | Standard wells |
| `HORIZONTAL` | Tubing hanger inside tree body | Most new developments |
| `DUAL_BORE` | Two production bores | High-rate producers |
| `MUDLINE` | Simplified design | Shallow water |
| `SPOOL` | Compact design | Tight spacing |

### Selecting Tree Type

```java
// Vertical tree - conventional design
tree.setTreeType(SubseaTree.TreeType.VERTICAL);

// Horizontal tree - modern standard
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);

// Dual bore - high rate wells
tree.setTreeType(SubseaTree.TreeType.DUAL_BORE);
```

### Tree Type Comparison

| Feature | Vertical | Horizontal |
|---------|----------|------------|
| Tubing Hanger Location | In wellhead | In tree |
| Wellhead Complexity | Higher | Lower |
| Tree Height | Taller | More compact |
| Workover Access | Full bore | Through-bore |
| Industry Trend | Legacy | Preferred |

---

## Valve Configuration

### Production Valves

```java
// Configure production valves
tree.setProductionMasterValveOpen(true);   // PMV
tree.setProductionWingValveOpen(true);     // PWV
tree.setChokeOpening(75.0);                // Choke position (%)

// Get valve states
boolean pmvOpen = tree.isProductionMasterValveOpen();
boolean pwvOpen = tree.isProductionWingValveOpen();
double chokePos = tree.getChokeOpening();
```

### Annulus Valves

```java
// Configure annulus valves
tree.setAnnulusMasterValveOpen(false);     // AMV
tree.setAnnulusWingValveOpen(false);       // AWV

// Annulus monitoring
tree.setAnnulusPressure(50.0);  // bara
```

### Choke Valve

```java
// Set choke opening (0-100%)
tree.setChokeOpening(50.0);

// Or set target outlet pressure
tree.setOutletPressure(80.0);  // bara

// Get pressure drop
double dp = tree.getPressureDrop();
```

### Valve Position Matrix

| Valve | Normal Production | Well Test | Shut-in |
|-------|-------------------|-----------|---------|
| PMV | Open | Open | Closed |
| PWV | Open | Closed | Closed |
| Choke | Modulating | Modulating | Closed |
| AMV | Closed | Closed | Closed |
| AWV | Closed | Closed | Closed |

---

## Pressure Ratings

### PressureRating Enumeration

| Rating | PSI | Bar | Application |
|--------|-----|-----|-------------|
| `PR5000` | 5,000 | 345 | Low pressure wells |
| `PR10000` | 10,000 | 690 | Standard HPHT |
| `PR15000` | 15,000 | 1,034 | Ultra HPHT |
| `PR20000` | 20,000 | 1,379 | Extreme HPHT |

### Setting Pressure Rating

```java
// Set pressure rating
tree.setPressureRating(SubseaTree.PressureRating.PR10000);

// Or set design pressure directly (bara)
tree.setDesignPressure(690.0);

// Get rating
SubseaTree.PressureRating rating = tree.getPressureRating();
int pressurePsi = rating.getPsi();
int pressureBar = rating.getBar();
```

---

## Usage Examples

### Example 1: Basic Subsea Tree Operation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.subsea.SubseaTree;

// Create well fluid
SystemSrkEos fluid = new SystemSrkEos(373.15, 250.0);
fluid.addComponent("methane", 70.0);
fluid.addComponent("ethane", 8.0);
fluid.addComponent("propane", 5.0);
fluid.addComponent("n-heptane", 15.0);
fluid.addComponent("water", 2.0);
fluid.setMixingRule("classic");

// Create wellhead stream
Stream wellStream = new Stream("Wellhead", fluid);
wellStream.setFlowRate(50000.0, "kg/hr");
wellStream.setTemperature(90.0, "C");
wellStream.setPressure(200.0, "bara");
wellStream.run();

// Create subsea tree
SubseaTree tree = new SubseaTree("A-1H Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setWaterDepth(450.0);
tree.setDesignPressure(690.0);
tree.setDesignTemperature(150.0);

// Configure valves for normal production
tree.setProductionMasterValveOpen(true);
tree.setProductionWingValveOpen(true);
tree.setChokeOpening(80.0);

// Run tree
tree.run();

// Get outlet conditions
StreamInterface outlet = tree.getOutletStream();
System.out.println("Outlet P: " + outlet.getPressure() + " bara");
System.out.println("Outlet T: " + (outlet.getTemperature() - 273.15) + " C");
```

### Example 2: Well Test Configuration

```java
// Configure for well test (route to test separator)
tree.setProductionWingValveOpen(false);  // Close to production header
tree.setTestValveOpen(true);             // Open to test header
tree.setChokeOpening(50.0);              // Reduced rate for test

tree.run();

// Get test rate
double testRate = tree.getOutletStream().getFlowRate("kg/hr");
System.out.println("Test rate: " + testRate + " kg/hr");
```

### Example 3: Well Shut-in Procedure

```java
// Emergency shutdown sequence
System.out.println("Initiating ESD...");

// Close choke first
tree.setChokeOpening(0.0);
tree.run();

// Close wing valves
tree.setProductionWingValveOpen(false);
tree.setAnnulusWingValveOpen(false);

// Close master valves
tree.setProductionMasterValveOpen(false);
tree.setAnnulusMasterValveOpen(false);

System.out.println("Well shut-in complete");
System.out.println("SITP: " + tree.getShutInTubingPressure() + " bara");
```

### Example 4: Tree with Mechanical Design

```java
import neqsim.process.mechanicaldesign.subsea.SubseaTreeMechanicalDesign;

// Create tree
SubseaTree tree = new SubseaTree("C-2H Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setWaterDepth(800.0);
tree.setDesignPressure(690.0);
tree.setDesignTemperature(175.0);

// Get mechanical design
SubseaTreeMechanicalDesign design = tree.getMechanicalDesign();

// Configure design parameters
design.setMaterialGrade("F22");
design.setDesignLife(25);  // years
design.setServiceType("Sour");

// Calculate design
design.calcDesign();

// Get JSON report
String report = design.toJson();
System.out.println(report);
```

---

## Design Standards

### Applicable Standards

| Standard | Title |
|----------|-------|
| API Spec 17D | Design and Operation of Subsea Production Systems |
| API RP 17A | Recommended Practice for Design and Operation of Subsea Production Systems |
| ISO 13628-4 | Subsea Wellhead and Tree Equipment |
| NORSOK U-001 | Subsea Production Systems |

### Material Selection

| Component | Typical Material | Service |
|-----------|-----------------|---------|
| Body | F22, Inconel 625 | HPHT, Sour |
| Trim | Inconel 718 | Erosion resistant |
| Seals | Inconel 625, HNBR | High pressure |
| Bolting | B7M, L7M | Sour service |

### Design Considerations

- **Pressure Containment**: Body thickness per ASME VIII
- **Temperature Rating**: -29°C to +175°C typical
- **Corrosion Allowance**: 3mm minimum for sour service
- **Fatigue Life**: 1000+ cycles for valve actuations
- **Leak Path**: No single point of failure

---

## Flow Assurance Integration

### Hydrate Monitoring

```java
// Check hydrate formation temperature
double fluidTemp = tree.getOutletStream().getTemperature() - 273.15;
double hydrateTemp = tree.calculateHydrateTemperature();  // °C

if (fluidTemp < hydrateTemp + 5.0) {
    System.out.println("WARNING: Approaching hydrate region");
    System.out.println("Fluid: " + fluidTemp + "°C, Hydrate: " + hydrateTemp + "°C");
}
```

### Cooldown Analysis

```java
// Calculate cooldown time to hydrate temperature
double ambientTemp = 4.0;  // Seabed temperature °C
double cooldownTime = tree.calculateCooldownTime(ambientTemp, hydrateTemp);

System.out.println("Cooldown time to hydrate: " + cooldownTime + " hours");
```

---

## Related Documentation

- [Subsea Equipment Overview](subsea_equipment) - All subsea equipment
- [Subsea Manifolds](subsea_manifolds) - Manifold modeling
- [Subsea Boosters](subsea_boosters) - Boosting systems
- [Umbilicals](umbilicals) - Umbilical systems
- [Pipelines](pipelines) - Flowline modeling

---

## API Reference

### SubseaTree

```java
// Constructors
SubseaTree(String name)
SubseaTree(String name, StreamInterface wellStream)

// Tree configuration
void setTreeType(TreeType type)
TreeType getTreeType()
void setPressureRating(PressureRating rating)
PressureRating getPressureRating()

// Design parameters
void setWaterDepth(double depth)
void setDesignPressure(double pressure)
void setDesignTemperature(double temperature)

// Production valves
void setProductionMasterValveOpen(boolean open)
void setProductionWingValveOpen(boolean open)
void setChokeOpening(double percent)
void setOutletPressure(double pressure)

// Annulus valves
void setAnnulusMasterValveOpen(boolean open)
void setAnnulusWingValveOpen(boolean open)
void setAnnulusPressure(double pressure)

// Mechanical design
SubseaTreeMechanicalDesign getMechanicalDesign()

// Run simulation
void run()
void run(UUID id)

// Results
StreamInterface getOutletStream()
double getPressureDrop()
double getShutInTubingPressure()
```

### TreeType Enum

```java
enum TreeType {
    VERTICAL,
    HORIZONTAL,
    DUAL_BORE,
    MUDLINE,
    SPOOL
}
```

### PressureRating Enum

```java
enum PressureRating {
    PR5000(5000, 345),
    PR10000(10000, 690),
    PR15000(15000, 1034),
    PR20000(20000, 1379);
    
    int getPsi()
    int getBar()
}
```
