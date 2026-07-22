---
title: Reservoir Modeling
description: Documentation for reservoir modeling equipment in NeqSim, enabling coupled reservoir-process simulations.
---

# Reservoir Modeling

Documentation for reservoir modeling equipment in NeqSim, enabling coupled reservoir-process simulations.

## Table of Contents
- [Overview](#overview)
- [SimpleReservoir](#simplereservoir)
- [Well Classes](#well-classes)
- [Usage Examples](#usage-examples)
- [Material Balance](#material-balance)
- [Integration with Process Systems](#integration-with-process-systems)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.reservoir`

The reservoir package provides classes for simplified reservoir modeling that can be integrated with process simulations. This enables:

- Material balance reservoir simulation
- Well management (producers and injectors)
- Oil/gas/water volume tracking
- Depletion studies
- Coupled reservoir-surface network modeling

| Class | Description |
|-------|-------------|
| `SimpleReservoir` | Material balance reservoir model |
| `Well` | Well connection to reservoir |
| `WellFlow` | Well flow calculations |
| `WellSystem` | System of connected wells |
| `ReservoirCVDsim` | Constant volume depletion simulation |

---

## SimpleReservoir

The `SimpleReservoir` class provides a material balance approach to reservoir modeling with support for multiple producers and injectors.

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── SimpleReservoir
    ├── contains: Well[] gasProducers
    ├── contains: Well[] oilProducers
    ├── contains: Well[] waterProducers
    ├── contains: Well[] gasInjectors
    └── contains: Well[] waterInjectors
```

### Key Features

- **Multi-Phase Reservoir**: Track oil, gas, and water volumes
- **Producer/Injector Wells**: Multiple wells of each type
- **Material Balance**: Mass conservation during production/injection
- **OOIP/OGIP Tracking**: Original oil/gas in place monitoring
- **Pressure Depletion**: Reservoir pressure as function of production

### Constructor

```java
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.thermo.system.SystemSrkEos;

// Create reservoir fluid
SystemInterface reservoirFluid = new SystemSrkEos(373.0, 250.0);  // 100°C, 250 bar
reservoirFluid.addComponent("methane", 50.0);
reservoirFluid.addComponent("ethane", 5.0);
reservoirFluid.addComponent("propane", 3.0);
reservoirFluid.addComponent("n-heptane", 40.0);
reservoirFluid.addComponent("water", 2.0);
reservoirFluid.setMixingRule("classic");

// Create reservoir
SimpleReservoir reservoir = new SimpleReservoir("North Sea Field");
reservoir.setReservoirFluid(reservoirFluid);
reservoir.setGasVolume(1.0e9, "Sm3");      // Initial gas volume
reservoir.setOilVolume(100.0e6, "Sm3");     // Initial oil volume
reservoir.setWaterVolume(50.0e6, "m3");     // Initial water volume
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setReservoirFluid(SystemInterface)` | Set reservoir fluid composition |
| `setGasVolume(double, String)` | Set initial gas volume |
| `setOilVolume(double, String)` | Set initial oil volume |
| `setWaterVolume(double, String)` | Set initial water volume |
| `addGasProducer(String)` | Add gas production well |
| `addOilProducer(String)` | Add oil production well |
| `addWaterProducer(String)` | Add water production well |
| `addGasInjector(String)` | Add gas injection well |
| `addWaterInjector(String)` | Add water injection well |
| `getGasInPlace(String)` | Get remaining gas in place |
| `getOilInPlace(String)` | Get remaining oil in place |
| `getPressure()` | Get current reservoir pressure |
| `run()` | Execute material balance update |

### Adding Wells

```java
// Add production wells
StreamInterface gasStream = reservoir.addGasProducer("Well-G1");
StreamInterface oilStream = reservoir.addOilProducer("Well-O1");
StreamInterface waterStream = reservoir.addWaterProducer("Well-W1");

// Add injection wells
StreamInterface gasInjStream = reservoir.addGasInjector("Well-GI1");
StreamInterface waterInjStream = reservoir.addWaterInjector("Well-WI1");

// Set production rates
reservoir.getGasProducer(0).getStream().setFlowRate(1.0, "MSm3/day");
reservoir.getOilProducer(0).getStream().setFlowRate(1000, "m3/day");
```

### Retrieving Wells

```java
// By index
Well gasWell = reservoir.getGasProducer(0);
Well oilWell = reservoir.getOilProducer(0);

// By name
Well namedWell = reservoir.getOilProducer("Well-O1");

// Access well stream
StreamInterface wellStream = gasWell.getStream();
```

---

## Well Classes

### Well

Represents a well connection to the reservoir.

```java
import neqsim.process.equipment.reservoir.Well;

Well well = new Well("Production Well 1");
well.setStream(productionStream);
```

### WellFlow

Provides well inflow performance relationship (IPR) calculations.

```java
import neqsim.process.equipment.reservoir.WellFlow;

WellFlow wellFlow = new WellFlow("Well Inflow");
wellFlow.setReservoirPressure(250.0, "bara");
wellFlow.setWellheadPressure(80.0, "bara");
wellFlow.setProductivityIndex(10.0);  // m³/day/bar
```

---

## Usage Examples

### Basic Depletion Study

```java
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.thermo.system.SystemSrkEos;

// Create reservoir
SystemInterface fluid = new SystemSrkEos(373.0, 250.0);
fluid.addComponent("methane", 70.0);
fluid.addComponent("n-heptane", 30.0);
fluid.setMixingRule("classic");

SimpleReservoir reservoir = new SimpleReservoir("Test Reservoir");
reservoir.setReservoirFluid(fluid);
reservoir.setGasVolume(5.0e9, "Sm3");   // 5 GSm³
reservoir.setOilVolume(50.0e6, "Sm3");  // 50 MSm³

// Add producer
StreamInterface gasOut = reservoir.addGasProducer("GP-1");
reservoir.getGasProducer(0).getStream().setFlowRate(5.0, "MSm3/day");

// Simulate 10 years of production
double dt = 1.0;  // day
for (int day = 0; day < 3650; day++) {
    reservoir.run();
    
    // Log every month
    if (day % 30 == 0) {
        double year = day / 365.0;
        double gasInPlace = reservoir.getGasInPlace("GSm3");
        double pressure = reservoir.getPressure();
        
        System.out.printf("Year %.1f: GIP=%.2f GSm³, P=%.1f bara%n", 
                          year, gasInPlace, pressure);
    }
}
```

### Oil Field with Water Injection

```java
// Create oil reservoir
SimpleReservoir oilField = new SimpleReservoir("Oil Field");
oilField.setReservoirFluid(oilFluid);
oilField.setOilVolume(200.0e6, "Sm3");
oilField.setGasVolume(20.0e9, "Sm3");
oilField.setWaterVolume(100.0e6, "m3");
oilField.setLowPressureLimit(100.0);  // Minimum reservoir pressure

// Production wells
StreamInterface oil1 = oilField.addOilProducer("OP-1");
StreamInterface oil2 = oilField.addOilProducer("OP-2");
StreamInterface gas1 = oilField.addGasProducer("GP-1");

// Injection well for pressure maintenance
StreamInterface waterInj = oilField.addWaterInjector("WI-1");

// Set rates
oilField.getOilProducer(0).getStream().setFlowRate(5000, "bbl/day");
oilField.getOilProducer(1).getStream().setFlowRate(4000, "bbl/day");
oilField.getGasProducer(0).getStream().setFlowRate(1.0, "MSm3/day");

// Water injection for voidage replacement
oilField.getWaterInjector(0).getStream().setFlowRate(6000, "bbl/day");

// Run simulation
oilField.run();
```

### Integrated Reservoir-Process System

```java
import neqsim.process.processmodel.ProcessSystem;

// Create reservoir
SimpleReservoir reservoir = new SimpleReservoir("Field A");
reservoir.setReservoirFluid(reservoirFluid);
reservoir.setOilVolume(100.0e6, "Sm3");

// Add producer
StreamInterface wellStream = reservoir.addOilProducer("OP-1");

// Create process system
ProcessSystem facility = new ProcessSystem("FPSO");

// Reservoir production
reservoir.getOilProducer(0).getStream().setFlowRate(10000, "bbl/day");
facility.add(reservoir);

// Production separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("HP Separator");
separator.setInletStream(wellStream);
facility.add(separator);

// Gas processing
Stream gasStream = separator.getGasOutStream();
Compressor compressor = new Compressor("Export Compressor", gasStream);
compressor.setOutletPressure(200.0, "bara");
facility.add(compressor);

// Run integrated simulation
facility.run();

// Check OOIP recovery
double initialOOIP = 100.0e6;
double remainingOil = reservoir.getOilInPlace("MSm3") * 1e6;
double recovery = (initialOOIP - remainingOil) / initialOOIP * 100;
System.out.println("Oil recovery: " + recovery + " %");
```

---

## Material Balance

The reservoir uses a simplified material balance approach:

### Gas Reservoir
```
G_p = G_i × (1 - P/P_i × Z_i/Z)
```

Where:
- G_p = cumulative gas production
- G_i = initial gas in place
- P, P_i = current and initial pressure
- Z, Z_i = current and initial compressibility factors

### Oil Reservoir with Gas Cap
Includes gas cap expansion, oil zone compressibility, and water influx terms.

### Volume Tracking
```java
// Get original volumes
double OOIP = reservoir.getOOIP();  // Original oil in place
double OGIP = reservoir.getOGIP();  // Original gas in place

// Get current in-place volumes
double currentGIP = reservoir.getGasInPlace("GSm3");
double currentOIP = reservoir.getOilInPlace("MSm3");

// Get cumulative production
double cumGas = reservoir.getGasProductionTotal();
double cumOil = reservoir.getOilProductionTotal();
```

---

## Integration with Process Systems

The reservoir can be integrated with surface facilities:

```
┌─────────────────┐
│   RESERVOIR     │
│                 │
│  ┌───────────┐  │
│  │  Gas Cap  │  │
│  └─────┬─────┘  │
│        │        │
│  ┌─────┴─────┐  │      ┌─────────────┐      ┌──────────┐
│  │ Oil Zone  │──┼──────│  Separator  │──────│ Pipeline │
│  └─────┬─────┘  │      └─────────────┘      └──────────┘
│        │        │
│  ┌─────┴─────┐  │
│  │   Water   │◄─┼───── Water Injection
│  └───────────┘  │
│                 │
└─────────────────┘
```

---

## Related Documentation

- [Wells](wells) - Well modeling
- [Separators](separators) - Production separators
- [Pipelines](pipelines) - Export pipelines
- [Compressors](compressors) - Gas compression
- [Subsea Systems](subsea_systems) - Subsea production
