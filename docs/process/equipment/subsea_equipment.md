# Subsea Equipment

Documentation for subsea production equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SubseaWell](#subseawell)
- [SimpleFlowLine](#simpleflowline)
- [Subsea Production Systems](#subsea-production-systems)
- [Usage Examples](#usage-examples)

---

## Overview

**Package:** `neqsim.process.equipment.subsea`

Subsea production systems require specialized modeling due to:
- Cold temperatures (near seabed ambient)
- High pressures
- Long tiebacks
- Flow assurance challenges (hydrates, wax, asphaltenes)

### Key Classes

| Class | Description |
|-------|-------------|
| `SubseaWell` | Subsea well with integrated pipeline |
| `SimpleFlowLine` | Basic subsea flowline |

---

## SubseaWell

### Overview

`SubseaWell` models a subsea production well with integrated wellbore/riser pipeline. It combines:
- Reservoir inflow
- Wellbore hydraulics
- Height difference (seabed to surface)

### Basic Usage

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.subsea.SubseaWell;

// Create reservoir fluid
SystemSrkEos reservoirFluid = new SystemSrkEos(373.15, 250.0);
reservoirFluid.addComponent("nitrogen", 0.5);
reservoirFluid.addComponent("CO2", 1.5);
reservoirFluid.addComponent("methane", 70.0);
reservoirFluid.addComponent("ethane", 8.0);
reservoirFluid.addComponent("propane", 5.0);
reservoirFluid.addComponent("n-butane", 3.0);
reservoirFluid.addComponent("n-pentane", 2.0);
reservoirFluid.addComponent("n-hexane", 1.5);
reservoirFluid.addComponent("n-heptane", 2.0);
reservoirFluid.addComponent("n-octane", 6.5);
reservoirFluid.setMixingRule("classic");

// Create wellhead stream
Stream wellheadStream = new Stream("Wellhead", reservoirFluid);
wellheadStream.setFlowRate(10000.0, "kg/hr");
wellheadStream.setTemperature(80.0, "C");
wellheadStream.setPressure(150.0, "bara");
wellheadStream.run();

// Create subsea well
SubseaWell well = new SubseaWell("A-1H", wellheadStream);
well.height = 1000.0;  // Water depth (m)
well.length = 1200.0;  // Well length (m)
well.run();

// Get outlet conditions at surface
Stream surfaceStream = (Stream) well.getOutletStream();
System.out.println("Arrival T: " + (surfaceStream.getTemperature() - 273.15) + " °C");
System.out.println("Arrival P: " + surfaceStream.getPressure() + " bara");
```

### Configuration

```java
// Set water depth and wellbore length
well.height = 500.0;   // Water depth in meters
well.length = 800.0;   // Total wellbore/riser length

// Configure internal pipeline
AdiabaticTwoPhasePipe pipeline = well.getPipeline();
pipeline.setDiameter(0.15);  // 6 inch
pipeline.setInnerSurfaceRoughness(1.5e-5);
```

---

## SimpleFlowLine

### Overview

`SimpleFlowLine` models a basic subsea flowline connecting subsea equipment (wellhead, manifold, PLET) to a downstream location.

### Basic Usage

```java
import neqsim.process.equipment.subsea.SimpleFlowLine;
import neqsim.process.equipment.stream.Stream;

// Create flowline
SimpleFlowLine flowline = new SimpleFlowLine("Flowline", wellheadStream);
flowline.length = 5000.0;   // 5 km flowline
flowline.setHeight(100.0);  // Height change (+ = upward)
flowline.setOutletTemperature(313.15);  // Target arrival temp

// Run
flowline.run();

// Get outlet conditions
Stream outlet = (Stream) flowline.getOutletStream();
System.out.println("Arrival temp: " + (outlet.getTemperature() - 273.15) + " °C");
System.out.println("Arrival pressure: " + outlet.getPressure() + " bara");
```

### Internal Pipeline Access

```java
// Access underlying pipeline model
AdiabaticTwoPhasePipe pipe = flowline.getPipeline();

// Configure pipeline properties
pipe.setLength(5000.0);
pipe.setDiameter(0.254);  // 10 inch
pipe.setInnerSurfaceRoughness(2.5e-5);
pipe.setOuterTemperature(277.15);  // Seabed temp (4°C)
```

---

## Subsea Production Systems

### Typical Subsea Architecture

```
Reservoir
    │
    ▼
┌─────────┐
│ Subsea  │
│  Well   │
└────┬────┘
     │
     ▼
┌─────────┐
│Manifold │  (multiple wells)
└────┬────┘
     │
     ▼
┌─────────┐
│Flowline │  (long tieback)
└────┬────┘
     │
     ▼
┌─────────┐
│ Riser   │
└────┬────┘
     │
     ▼
  Platform
```

### Complete Subsea Tieback Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.subsea.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.valve.ThrottlingValve;

// Create process system
ProcessSystem subsea = new ProcessSystem();

// Create multiple wells
Stream well1Stream = createWellStream(wellFluid, 8000.0);
Stream well2Stream = createWellStream(wellFluid, 6000.0);
Stream well3Stream = createWellStream(wellFluid, 7000.0);

SubseaWell well1 = new SubseaWell("Well-1", well1Stream);
well1.height = 350.0;
subsea.add(well1Stream);
subsea.add(well1);

SubseaWell well2 = new SubseaWell("Well-2", well2Stream);
well2.height = 350.0;
subsea.add(well2Stream);
subsea.add(well2);

SubseaWell well3 = new SubseaWell("Well-3", well3Stream);
well3.height = 350.0;
subsea.add(well3Stream);
subsea.add(well3);

// Manifold (mix well streams)
Mixer manifold = new Mixer("Subsea Manifold");
manifold.addStream(well1.getOutletStream());
manifold.addStream(well2.getOutletStream());
manifold.addStream(well3.getOutletStream());
subsea.add(manifold);

// Main flowline to platform
SimpleFlowLine mainFlowline = new SimpleFlowLine("Export Flowline", 
    manifold.getOutletStream());
mainFlowline.length = 15000.0;  // 15 km tieback
mainFlowline.setHeight(350.0);  // Rise to platform
subsea.add(mainFlowline);

// Topside choke
ThrottlingValve topsideChoke = new ThrottlingValve("Topside Choke",
    mainFlowline.getOutletStream());
topsideChoke.setOutletPressure(30.0);  // First stage separator pressure
subsea.add(topsideChoke);

// Run simulation
subsea.run();

// Results
System.out.println("=== Subsea System Results ===");
System.out.println("Total production: " + 
    manifold.getOutletStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Manifold pressure: " + 
    manifold.getOutletStream().getPressure() + " bara");
System.out.println("Arrival temperature: " + 
    (mainFlowline.getOutletStream().getTemperature() - 273.15) + " °C");
System.out.println("Arrival pressure: " + 
    mainFlowline.getOutletStream().getPressure() + " bara");
```

---

## Flow Assurance Considerations

### Hydrate Risk Assessment

```java
// Check hydrate formation temperature along flowline
ThermodynamicOperations ops = new ThermodynamicOperations(
    mainFlowline.getOutletStream().getFluid()
);

// Calculate hydrate equilibrium
ops.hydrateFormationTemperature();
double hydrateTemp = ops.getThermoSystem().getTemperature() - 273.15;
double arrivalTemp = mainFlowline.getOutletStream().getTemperature() - 273.15;

System.out.println("Hydrate formation temp: " + hydrateTemp + " °C");
System.out.println("Arrival temp: " + arrivalTemp + " °C");

if (arrivalTemp < hydrateTemp + 5.0) {
    System.out.println("WARNING: Operating close to hydrate curve!");
    System.out.println("Consider: MEG injection, insulation, or heating");
}
```

### Cool-Down Analysis

```java
// Estimate time to reach hydrate temperature after shutdown
double fluidHeatCapacity = 2500.0;  // J/kg-K (typical)
double fluidMass = 50000.0;  // kg (in flowline)
double seabedTemp = 4.0;  // °C
double initialTemp = arrivalTemp;
double targetTemp = hydrateTemp;

double uValue = 5.0;  // W/m²-K (insulated flowline)
double area = Math.PI * 0.254 * mainFlowline.length;  // Surface area

// Time constant
double tau = fluidMass * fluidHeatCapacity / (uValue * area);

// Time to reach hydrate temperature
double coolDownTime = -tau * Math.log((targetTemp - seabedTemp) / 
                                       (initialTemp - seabedTemp));

System.out.println("Cool-down time to hydrate temp: " + 
    (coolDownTime / 3600.0) + " hours");
```

### Wax Deposition Check

```java
// Check if operating below WAT
WaxCharacterise waxChar = new WaxCharacterise(fluid);
waxChar.getModel().addTBPWax();

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcWAT();
double wat = ops.getThermoSystem().getTemperature() - 273.15;

if (arrivalTemp < wat) {
    System.out.println("WARNING: Operating below WAT!");
    System.out.println("WAT: " + wat + " °C");
    System.out.println("Arrival temp: " + arrivalTemp + " °C");
    System.out.println("Wax deposition likely - consider wax management");
}
```

---

## Design Applications

### Tieback Distance Analysis

```java
// Evaluate maximum tieback distance for given constraints
double minArrivalTemp = hydrateTemp + 5.0;  // 5°C margin above hydrate
double maxFlowlineLength = 0.0;

for (double length = 1000; length <= 50000; length += 1000) {
    SimpleFlowLine testFlowline = new SimpleFlowLine("Test", wellheadStream);
    testFlowline.length = length;
    testFlowline.run();
    
    double arrTemp = testFlowline.getOutletStream().getTemperature() - 273.15;
    
    if (arrTemp >= minArrivalTemp) {
        maxFlowlineLength = length;
    } else {
        break;
    }
}

System.out.println("Maximum tieback without heating: " + 
    (maxFlowlineLength / 1000.0) + " km");
```

### Back Pressure Calculation

```java
// Calculate required wellhead pressure for target arrival pressure
double targetArrivalPressure = 30.0;  // bara
double flowlinePressureDrop = 
    wellheadStream.getPressure() - mainFlowline.getOutletStream().getPressure();

double requiredWHP = targetArrivalPressure + flowlinePressureDrop;
System.out.println("Required wellhead pressure: " + requiredWHP + " bara");

// Check against reservoir deliverability
double reservoirPressure = 250.0;  // bara
double PI = 15.0;  // m³/d/bar
double maxRate = PI * (reservoirPressure - requiredWHP);
System.out.println("Maximum rate at this back pressure: " + maxRate + " m³/d");
```

---

## See Also

- [Pipelines](../pipeline/README.md) - General pipeline modeling
- [Wells](../well/README.md) - Well modeling
- [Flow Assurance](../../pvtsimulation/flow_assurance.md) - Hydrate and wax management
- [Networks](../network/README.md) - Network modeling
