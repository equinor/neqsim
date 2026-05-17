---
title: Subsea Manifolds
description: Documentation for subsea production manifold equipment in NeqSim.
---

# Subsea Manifolds

Documentation for subsea production manifold equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SubseaManifold Class](#subseamanifold-class)
- [Manifold Types](#manifold-types)
- [Valve Skid Configuration](#valve-skid-configuration)
- [Well Routing](#well-routing)
- [Usage Examples](#usage-examples)
- [Design Standards](#design-standards)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.subsea`

A subsea manifold gathers production from multiple wells and routes it to flowlines for transport to the host facility. The `SubseaManifold` class models:

- Production headers for well gathering
- Test headers for individual well testing
- Service/utility headers for chemicals and hydraulics
- Valve skids with production and isolation valves
- Flowline connections to export pipelines

| Class | Description |
|-------|-------------|
| `SubseaManifold` | Multi-slot production manifold |
| `SubseaManifoldMechanicalDesign` | Mechanical design calculations |

---

## SubseaManifold Class

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── SubseaManifold
    ├── enum: ManifoldType
    ├── static class: ValveSkid
    └── contains: Mixer (production/test headers)
```

### Key Features

- **Multi-Slot Design**: Support for 2-12 well connections
- **Production/Test Routing**: Switchable flow paths
- **Valve Skid Modeling**: Individual well control
- **Header Sizing**: Based on total flow capacity
- **Pigging Integration**: Launcher/receiver connections

### Constructor

```java
import neqsim.process.equipment.subsea.SubseaManifold;

// Create 6-slot manifold
SubseaManifold manifold = new SubseaManifold("Manifold-A", 6);

// Configure design parameters
manifold.setWaterDepth(380.0);
manifold.setDesignPressure(350.0);
manifold.setDesignTemperature(120.0);
manifold.setHasTestHeader(true);
```

---

## Manifold Types

### ManifoldType Enumeration

| Type | Description | Application |
|------|-------------|-------------|
| `PRODUCTION_ONLY` | Single production header | Simple gathering |
| `PRODUCTION_TEST` | Production + test headers | Standard design |
| `FULL_SERVICE` | Production + test + injection | Complex fields |
| `INJECTION` | Water/gas injection only | IOR projects |

### Selecting Manifold Type

```java
// Standard production with testing capability
manifold.setManifoldType(SubseaManifold.ManifoldType.PRODUCTION_TEST);

// Full service for complex operations
manifold.setManifoldType(SubseaManifold.ManifoldType.FULL_SERVICE);

// Injection manifold for water injection wells
SubseaManifold injManifold = new SubseaManifold("Injection Manifold", 4);
injManifold.setManifoldType(SubseaManifold.ManifoldType.INJECTION);
```

---

## Valve Skid Configuration

### ValveSkid Inner Class

Each well slot has a valve skid with:

| Component | Function |
|-----------|----------|
| Production Valve (PV) | Route to production header |
| Test Valve (TV) | Route to test header |
| Crossover Valve (XV) | Header crossover capability |
| Isolation Valve (IV) | Emergency isolation |

### Configuring Valve Skids

```java
// Get valve skid for slot 1
SubseaManifold.ValveSkid skid = manifold.getValveSkid(1);

// Configure well connection
skid.setWellName("Well-A1");
skid.setActive(true);
skid.setRouting("production");  // or "test"

// Connect stream
skid.setConnectedStream(well1Stream);
```

### Valve Positions

| Routing | PV | TV | Status |
|---------|-----|-----|--------|
| Production | Open | Closed | Normal production |
| Test | Closed | Open | Well testing |
| Shut-in | Closed | Closed | Well isolated |

---

## Well Routing

### Adding Wells to Manifold

```java
// Add well streams to manifold
manifold.addWellStream(well1Stream);
manifold.addWellStream(well2Stream);
manifold.addWellStream(well3Stream);
manifold.addWellStream(well4Stream);

// Check well count
int connectedWells = manifold.getConnectedWellCount();
System.out.println("Connected wells: " + connectedWells + "/" + manifold.getNumberOfSlots());
```

### Routing Wells to Test

```java
// Route well 2 to test header
manifold.routeWellToTest(2);

// Route well back to production
manifold.routeWellToProduction(2);

// Route all wells to production
manifold.routeAllToProduction();
```

### Getting Output Streams

```java
// Run manifold
manifold.run();

// Get output streams
StreamInterface productionStream = manifold.getProductionStream();
StreamInterface testStream = manifold.getTestStream();

System.out.println("Production rate: " + productionStream.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Test rate: " + testStream.getFlowRate("kg/hr") + " kg/hr");
```

---

## Usage Examples

### Example 1: Basic Manifold Setup

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.subsea.SubseaManifold;

// Create well fluids
SystemSrkEos fluid1 = new SystemSrkEos(353.15, 180.0);
fluid1.addComponent("methane", 75.0);
fluid1.addComponent("ethane", 10.0);
fluid1.addComponent("propane", 5.0);
fluid1.addComponent("n-heptane", 10.0);
fluid1.setMixingRule("classic");

// Create well streams
Stream well1 = new Stream("Well-A1", fluid1.clone());
well1.setFlowRate(20000.0, "kg/hr");
well1.run();

Stream well2 = new Stream("Well-A2", fluid1.clone());
well2.setFlowRate(25000.0, "kg/hr");
well2.run();

Stream well3 = new Stream("Well-A3", fluid1.clone());
well3.setFlowRate(18000.0, "kg/hr");
well3.run();

// Create 6-slot manifold
SubseaManifold manifold = new SubseaManifold("Manifold-A", 6);
manifold.setWaterDepth(380.0);
manifold.setDesignPressure(350.0);
manifold.setHasTestHeader(true);

// Add wells
manifold.addWellStream(well1);
manifold.addWellStream(well2);
manifold.addWellStream(well3);

// Run manifold
manifold.run();

// Get commingled production
StreamInterface production = manifold.getProductionStream();
System.out.println("Total production: " + production.getFlowRate("kg/hr") + " kg/hr");
```

### Example 2: Well Test Operation

```java
// Create manifold with wells
SubseaManifold manifold = new SubseaManifold("Manifold-B", 4);
manifold.addWellStream(wellA);
manifold.addWellStream(wellB);
manifold.addWellStream(wellC);
manifold.addWellStream(wellD);

// Normal production mode
manifold.routeAllToProduction();
manifold.run();
System.out.println("Production mode:");
System.out.println("  Production: " + manifold.getProductionStream().getFlowRate("kg/hr") + " kg/hr");

// Test Well C individually
System.out.println("\nTesting Well C:");
manifold.routeWellToTest(3);  // Well C is slot 3
manifold.run();

StreamInterface testStream = manifold.getTestStream();
System.out.println("  Well C rate: " + testStream.getFlowRate("kg/hr") + " kg/hr");
System.out.println("  Well C GOR: " + calculateGOR(testStream) + " Sm3/Sm3");

// Return to normal production
manifold.routeAllToProduction();
```

### Example 3: Manifold with Header Sizing

```java
// Create manifold
SubseaManifold manifold = new SubseaManifold("Main Manifold", 8);
manifold.setWaterDepth(500.0);
manifold.setDesignPressure(400.0);
manifold.setDesignTemperature(130.0);

// Set header sizes (inches)
manifold.setProductionHeaderSize(12.0);  // 12" production header
manifold.setTestHeaderSize(8.0);         // 8" test header
manifold.setServiceHeaderSize(4.0);      // 4" service header

// Configure slot piping
manifold.setSlotPipeSize(6.0);  // 6" from each well slot

// Get mechanical design
SubseaManifoldMechanicalDesign design = manifold.getMechanicalDesign();
design.setMaterialGrade("6Mo");
design.calcDesign();

System.out.println("Manifold weight: " + design.getWeight() + " tonnes");
```

### Example 4: Complex Field Development

```java
import neqsim.process.processmodel.ProcessSystem;

// Create process system
ProcessSystem subsea = new ProcessSystem("Subsea System");

// Create two manifolds
SubseaManifold manifoldNorth = new SubseaManifold("North Manifold", 6);
SubseaManifold manifoldSouth = new SubseaManifold("South Manifold", 6);

// Configure both
for (SubseaManifold m : Arrays.asList(manifoldNorth, manifoldSouth)) {
    m.setWaterDepth(450.0);
    m.setDesignPressure(380.0);
    m.setHasTestHeader(true);
}

// Add wells to north manifold
manifoldNorth.addWellStream(wellN1);
manifoldNorth.addWellStream(wellN2);
manifoldNorth.addWellStream(wellN3);

// Add wells to south manifold
manifoldSouth.addWellStream(wellS1);
manifoldSouth.addWellStream(wellS2);
manifoldSouth.addWellStream(wellS3);
manifoldSouth.addWellStream(wellS4);

// Run manifolds
subsea.add(manifoldNorth);
subsea.add(manifoldSouth);
subsea.run();

// Get total field production
double northRate = manifoldNorth.getProductionStream().getFlowRate("kg/hr");
double southRate = manifoldSouth.getProductionStream().getFlowRate("kg/hr");
System.out.println("Field production: " + (northRate + southRate) + " kg/hr");
```

---

## Design Standards

### Applicable Standards

| Standard | Title |
|----------|-------|
| API RP 17G | Completion/Workover Risers |
| API RP 17N | Subsea Production System Reliability |
| DNV-ST-F101 | Submarine Pipeline Systems |
| NORSOK U-001 | Subsea Production Systems |

### Design Parameters

| Parameter | Typical Range | Unit |
|-----------|--------------|------|
| Number of Slots | 2-12 | - |
| Water Depth | 50-3000 | m |
| Design Pressure | 345-1034 | bar |
| Design Temperature | -29 to 175 | °C |
| Design Life | 20-30 | years |

### Material Selection

| Component | Material | Application |
|-----------|----------|-------------|
| Headers | 25Cr SDSS | Sour, high pressure |
| Valves | Inconel 625 | Corrosion resistant |
| Structure | Carbon steel | Non-wetted |
| Piping | 6Mo, 25Cr | Sour service |

---

## Header Sizing Guidelines

### Production Header

$$D_{header} = \sqrt{\frac{4 \cdot Q_{total}}{\pi \cdot v_{max}}}$$

Where:
- $Q_{total}$ = Total volumetric flow rate
- $v_{max}$ = Maximum allowable velocity (typically 15-20 m/s for gas)

### Typical Sizes

| Wells Connected | Header Size |
|-----------------|-------------|
| 2-4 | 8-10" |
| 4-6 | 10-12" |
| 6-8 | 12-14" |
| 8-12 | 14-16" |

---

## Related Documentation

- [Subsea Trees](subsea_trees) - Christmas tree modeling
- [Subsea Boosters](subsea_boosters) - Subsea pumping
- [Umbilicals](umbilicals) - Control and chemical supply
- [Subsea Equipment Overview](subsea_equipment) - All subsea equipment

---

## API Reference

### SubseaManifold

```java
// Constructors
SubseaManifold(String name)
SubseaManifold(String name, int numberOfSlots)

// Configuration
void setManifoldType(ManifoldType type)
ManifoldType getManifoldType()
void setWaterDepth(double depth)
void setDesignPressure(double pressure)
void setDesignTemperature(double temperature)
void setHasTestHeader(boolean hasTest)

// Header sizing
void setProductionHeaderSize(double inches)
void setTestHeaderSize(double inches)
void setServiceHeaderSize(double inches)
void setSlotPipeSize(double inches)

// Well connections
void addWellStream(StreamInterface wellStream)
int getConnectedWellCount()
int getNumberOfSlots()

// Valve skids
ValveSkid getValveSkid(int slotNumber)

// Well routing
void routeWellToProduction(int slotNumber)
void routeWellToTest(int slotNumber)
void routeAllToProduction()

// Output streams
StreamInterface getProductionStream()
StreamInterface getTestStream()

// Mechanical design
SubseaManifoldMechanicalDesign getMechanicalDesign()

// Run simulation
void run()
void run(UUID id)
```

### ManifoldType Enum

```java
enum ManifoldType {
    PRODUCTION_ONLY,
    PRODUCTION_TEST,
    FULL_SERVICE,
    INJECTION
}
```

### ValveSkid Class

```java
class ValveSkid {
    int getSlotNumber()
    String getWellName()
    void setWellName(String name)
    boolean isActive()
    void setActive(boolean active)
    String getRouting()
    void setRouting(String routing)
    StreamInterface getConnectedStream()
    void setConnectedStream(StreamInterface stream)
}
```
