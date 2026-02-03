---
title: Subsea Production Systems
description: Documentation for subsea production equipment in NeqSim.
---

# Subsea Production Systems

Documentation for subsea production equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SubseaWell](#subseawell)
- [SimpleFlowLine](#simpleflowline)
- [System Integration](#system-integration)
- [Usage Examples](#usage-examples)
- [Design Considerations](#design-considerations)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.subsea`

The subsea package provides equipment for modeling subsea production systems, including:

- Subsea wells with tubing flow
- Flowlines from wellhead to platform
- Integration with reservoir models
- Choke valve systems

| Class | Description |
|-------|-------------|
| `SubseaWell` | Combined well and tubing model |
| `SimpleFlowLine` | Subsea flowline/riser model |

---

## SubseaWell

The `SubseaWell` class models a subsea production well including the wellbore/tubing flow using an adiabatic two-phase pipe model.

### Class Hierarchy

```
TwoPortEquipment
└── SubseaWell
    └── contains: AdiabaticTwoPhasePipe (tubing)
```

### Key Features

- **Well Tubing Model**: Pressure drop and heat transfer in tubing
- **Two-Phase Flow**: Gas-liquid flow in wellbore
- **Elevation Changes**: Hydrostatic pressure effects
- **Reservoir Integration**: Direct connection to SimpleReservoir

### Constructor

```java
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.process.equipment.stream.StreamInterface;

// Create subsea well with inlet stream from reservoir
SubseaWell well = new SubseaWell("Well-1", reservoirStream);
```

### Properties

| Property | Description | Default |
|----------|-------------|---------|
| `height` | Vertical depth of well | 1000.0 m |
| `length` | Measured depth of well | 1200.0 m |

### Key Methods

| Method | Description |
|--------|-------------|
| `getPipeline()` | Access internal tubing model |
| `getOutletStream()` | Get wellhead stream |
| `run()` | Execute well flow calculation |

### Pipeline Configuration

```java
SubseaWell well = new SubseaWell("OP-1", reservoirStream);

// Configure tubing
AdiabaticTwoPhasePipe tubing = well.getPipeline();
tubing.setDiameter(0.15);           // 6" tubing ID
tubing.setLength(3000.0);           // 3000m MD
tubing.setInletElevation(-2500.0);  // Reservoir depth TVD
tubing.setOutletElevation(-200.0);  // Mudline depth
```

---

## SimpleFlowLine

The `SimpleFlowLine` class models a subsea flowline or riser from the wellhead to the platform.

### Class Hierarchy

```
TwoPortEquipment
└── SimpleFlowLine
    └── contains: AdiabaticTwoPhasePipe (flowline)
```

### Constructor

```java
import neqsim.process.equipment.subsea.SimpleFlowLine;

// Create flowline from choke outlet
SimpleFlowLine flowline = new SimpleFlowLine("FL-1", chokeOutletStream);
```

### Configuration

```java
SimpleFlowLine flowline = new SimpleFlowLine("Flowline", wellheadStream);

// Configure flowline
flowline.getPipeline().setDiameter(0.4);          // 16" flowline
flowline.getPipeline().setLength(5000.0);         // 5 km tieback
flowline.getPipeline().setInletElevation(-200.0); // Mudline
flowline.getPipeline().setOutletElevation(0.0);   // Platform
```

---

## System Integration

### Typical Subsea System Architecture

```
┌─────────────┐
│  RESERVOIR  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ SUBSEA WELL │ ← Tubing flow model
│  (tubing)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│SUBSEA CHOKE │ ← Pressure control
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  FLOWLINE   │ ← Multiphase transport
│   (riser)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│TOPSIDE CHOKE│ ← Final pressure control
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  SEPARATOR  │ ← First-stage separation
└─────────────┘
```

---

## Usage Examples

### Complete Subsea Production System

```java
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.process.equipment.subsea.SimpleFlowLine;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create reservoir fluid
SystemInterface reservoirFluid = new SystemSrkEos(373.15, 250.0);  // 100°C, 250 bar
reservoirFluid.addComponent("nitrogen", 0.1);
reservoirFluid.addComponent("methane", 70.0);
reservoirFluid.addComponent("ethane", 5.0);
reservoirFluid.addComponent("propane", 3.0);
reservoirFluid.addComponent("n-butane", 2.0);
reservoirFluid.addComponent("n-heptane", 10.0);
reservoirFluid.addComponent("nC10", 5.0);
reservoirFluid.addComponent("water", 10.0);
reservoirFluid.setMixingRule(2);
reservoirFluid.setMultiPhaseCheck(true);

// Create reservoir
SimpleReservoir reservoir = new SimpleReservoir("Deepwater Field");
reservoir.setReservoirFluid(reservoirFluid, 5.0e7, 550.0e6, 10.0e6);

// Add oil producer
StreamInterface producedStream = reservoir.addOilProducer("OP-1");
producedStream.setFlowRate(10000.0 * 24.0, "kg/day");

// Run reservoir
reservoir.run();

// Create subsea well
SubseaWell well = new SubseaWell("OP-1 Well", producedStream);
well.getPipeline().setDiameter(0.15);           // 6" tubing
well.getPipeline().setLength(3500.0);           // 3500m MD
well.getPipeline().setInletElevation(-2000.0);  // Reservoir depth
well.getPipeline().setOutletElevation(-500.0);  // Mudline

// Subsea choke
ThrottlingValve subseaChoke = new ThrottlingValve("Subsea Choke", well.getOutletStream());
subseaChoke.setOutletPressure(120.0, "bara");
subseaChoke.setAcceptNegativeDP(false);

// Flowline to platform
SimpleFlowLine flowline = new SimpleFlowLine("Flowline", subseaChoke.getOutletStream());
flowline.getPipeline().setDiameter(0.25);       // 10" flowline
flowline.getPipeline().setLength(8000.0);       // 8 km tieback
flowline.getPipeline().setInletElevation(-500.0);
flowline.getPipeline().setOutletElevation(0.0);

// Topside choke
ThrottlingValve topsideChoke = new ThrottlingValve("Topside Choke", flowline.getOutletStream());
topsideChoke.setOutletPressure(50.0, "bara");

// Create process system
ProcessSystem subsea = new ProcessSystem("Subsea System");
subsea.add(well);
subsea.add(subseaChoke);
subsea.add(flowline);
subsea.add(topsideChoke);

// Run
subsea.run();

// Results
System.out.println("Wellhead pressure: " + well.getOutletStream().getPressure("bara") + " bara");
System.out.println("Subsea choke DP: " + subseaChoke.getDeltaPressure("bar") + " bar");
System.out.println("Arrival pressure: " + flowline.getOutletStream().getPressure("bara") + " bara");
System.out.println("Topside pressure: " + topsideChoke.getOutletStream().getPressure("bara") + " bara");
```

### Transient Reservoir Depletion

```java
import java.util.ArrayList;

// Setup as above...

// Production rate control with adjuster
Adjuster rateControl = new Adjuster("Rate Adjuster");
rateControl.setActivateWhenLess(true);
rateControl.setTargetVariable(flowline.getOutletStream(), "pressure", 80.0, "bara");
rateControl.setAdjustedVariable(producedStream, "flow rate");

// Add adjuster to process
subsea.add(rateControl);

// Run transient simulation
ArrayList<double[]> productionHistory = new ArrayList<double[]>();

for (int day = 0; day < 365; day++) {
    // Run reservoir for one day
    reservoir.runTransient(60 * 60 * 24);  // seconds in day
    
    // Run subsea system
    subsea.run();
    
    // Record data
    productionHistory.add(new double[] {
        day,
        producedStream.getFlowRate("kg/hr"),
        reservoir.getOilProductionTotal("MSm3"),
        reservoir.getPressure()
    });
    
    // Monthly output
    if (day % 30 == 0) {
        System.out.printf("Day %d: Rate=%.0f kg/hr, Cum=%.2f MSm3, P_res=%.1f bara%n",
            day, 
            producedStream.getFlowRate("kg/hr"),
            reservoir.getOilProductionTotal("MSm3"),
            reservoir.getPressure());
    }
}
```

### Multiple Wells with Manifold

```java
// Create three subsea wells
SubseaWell well1 = new SubseaWell("OP-1", reservoir.getOilProducer("OP-1").getStream());
SubseaWell well2 = new SubseaWell("OP-2", reservoir.getOilProducer("OP-2").getStream());
SubseaWell well3 = new SubseaWell("OP-3", reservoir.getOilProducer("OP-3").getStream());

// Configure wells
for (SubseaWell well : new SubseaWell[] {well1, well2, well3}) {
    well.getPipeline().setDiameter(0.15);
    well.getPipeline().setLength(3000.0);
    well.getPipeline().setInletElevation(-2000.0);
    well.getPipeline().setOutletElevation(-400.0);
}

// Create subsea manifold
Manifold subseaManifold = new Manifold("Subsea Manifold");
subseaManifold.addStream(well1.getOutletStream());
subseaManifold.addStream(well2.getOutletStream());
subseaManifold.addStream(well3.getOutletStream());

// Export flowline from manifold
SimpleFlowLine exportLine = new SimpleFlowLine("Export", subseaManifold.getOutletStream());
exportLine.getPipeline().setDiameter(0.4);
exportLine.getPipeline().setLength(15000.0);
```

---

## Design Considerations

### Well Deliverability
- Balance reservoir pressure with required wellhead pressure
- Account for hydrostatic head in tubing
- Consider liquid loading in gas wells

### Flowline Sizing
- Velocity limits (erosion, slugging)
- Pressure drop constraints
- Arrival temperature (wax, hydrates)

### Choke Valve Placement
- Subsea choke: Slug mitigation, well control
- Topside choke: Process control, well testing

### Thermal Management
- Heat loss to seawater
- Insulation requirements
- Electrical heating if needed

---

## Related Documentation

- [Reservoirs](reservoirs.md) - Reservoir modeling
- [Pipelines](pipelines.md) - Pipeline flow models
- [Risers](pipelines.md#risers) - Riser types (SCR, TTR, Flexible, Lazy-Wave)
- [Riser Mechanical Design](../riser_mechanical_design.md) - Riser mechanical design
- [Valves](valves.md) - Choke valves
- [Manifolds](manifolds.md) - Subsea manifolds
- [Networks](networks.md) - Pipeline networks
- [Two-Phase Pipe Flow](../../fluidmechanics/TwoPhasePipeFlowModel.md) - Flow correlations
