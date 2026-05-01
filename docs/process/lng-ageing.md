---
title: "LNG Cargo Ageing and Transport Simulation"
description: "Comprehensive guide to NeqSim's LNG ageing package for simulating boil-off gas generation, composition change, quality tracking, rollover detection, and multi-tank ship models during LNG transport."
---

# LNG Cargo Ageing and Transport Simulation

The `neqsim.process.equipment.lng` package provides a physics-based simulation
framework for LNG cargo quality evolution during storage and marine transport.
It models boil-off gas (BOG) generation, preferential evaporation, stratification,
rollover risk, quality KPI tracking, and multi-tank ship operations.

## Package Overview

| Class | Purpose |
|-------|---------|
| `LNGAgeingScenario` | Top-level orchestrator for a single-tank ageing simulation |
| `LNGShipModel` | Multi-tank ship model with common BOG header |
| `LNGTankLayeredModel` | Core physics engine: multi-layer VLE, heat ingress, boil-off |
| `LNGTankLayer` | Single horizontal layer with composition and density tracking |
| `LNGVaporSpaceModel` | Vapor space pressure evolution and relief valve logic |
| `LNGRolloverDetector` | Stratification rollover risk assessment |
| `LNGBOGHandlingNetwork` | BOG disposition: fuel, reliquefaction, GCU, venting |
| `LNGVoyageProfile` | Route environment: ambient temperature, waves, wind, solar |
| `LNGHeelManager` | Heel retention, spray cooling, cargo mixing |
| `LNGAgeingResult` | Snapshot of quality KPIs at each time step |
| `TankGeometry` | Physical geometry for membrane, Moss, Type-C, SPB tanks |
| `TankHeatTransferModel` | Multi-zone heat transfer: bottom, sidewalls, roof, cofferdam |
| `MethaneNumberCalculator` | Methane Number via EN 16726, MWM, or simplified correlation |
| `LNGSloshingModel` | Sloshing mixing and BOG enhancement from sea state |

## Quick Start

### Java

```java
import neqsim.process.equipment.lng.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// 1. Define LNG composition
SystemSrkEos lng = new SystemSrkEos(111.0, 1.013);
lng.addComponent("methane", 0.92);
lng.addComponent("ethane", 0.05);
lng.addComponent("propane", 0.02);
lng.addComponent("nitrogen", 0.01);
lng.setMixingRule("classic");

// 2. Create feed stream
Stream feed = new Stream("LNG feed", lng);
feed.setFlowRate(140000.0, "m3/hr");
feed.run();

// 3. Configure scenario
LNGAgeingScenario scenario = new LNGAgeingScenario("Qatar-Japan", feed);
scenario.setTankVolume(174000.0);
scenario.setInitialFillingRatio(0.98);
scenario.setSimulationTime(480.0);  // 20 days
scenario.setTimeStepHours(1.0);
scenario.setOverallHeatTransferCoeff(0.045);
scenario.setAmbientTemperature(308.15);  // 35 degC
scenario.setTankPressure(1.013);

// 4. Run and get results
scenario.run();

for (LNGAgeingResult r : scenario.getResults()) {
    System.out.printf("t=%6.1f h  T=%.2f K  rho=%.1f kg/m3  WI=%.2f MJ/Sm3  MN=%.1f  BOR=%.4f %%/day%n",
        r.getTimeHours(), r.getTemperature(), r.getDensity(),
        r.getWobbeIndex(), r.getMethaneNumber(), r.getBoilOffRatePctPerDay());
}
```

### Python (Jupyter)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
LNGAgeingScenario = jneqsim.process.equipment.lng.LNGAgeingScenario

lng = SystemSrkEos(111.0, 1.013)
lng.addComponent("methane", 0.92)
lng.addComponent("ethane", 0.05)
lng.addComponent("propane", 0.02)
lng.addComponent("nitrogen", 0.01)
lng.setMixingRule("classic")

feed = Stream("LNG feed", lng)
feed.setFlowRate(140000.0, "m3/hr")
feed.run()

scenario = LNGAgeingScenario("Qatar-Japan", feed)
scenario.setTankVolume(174000.0)
scenario.setInitialFillingRatio(0.98)
scenario.setSimulationTime(480.0)
scenario.setTimeStepHours(1.0)
scenario.setOverallHeatTransferCoeff(0.045)
scenario.setAmbientTemperature(308.15)
scenario.run()

for r in scenario.getResults():
    print(f"t={r.getTimeHours():6.1f}h  WI={r.getWobbeIndex():.2f}  MN={r.getMethaneNumber():.1f}")
```

## Core Concepts

### Preferential Evaporation and Ageing

LNG is a cryogenic mixture (predominantly methane with nitrogen, ethane, propane,
and heavier hydrocarbons). During storage and transport, heat leaks into the tank,
causing boil-off. Lighter components (nitrogen first, then methane) evaporate
preferentially, enriching the liquid in heavier components. This "ageing" effect
changes the cargo's quality KPIs over time:

- **Wobbe Index (WI)** — increases as methane fraction decreases
- **Gross Calorific Value (GCV)** — increases
- **Methane Number (MN)** — decreases (knock resistance drops)
- **Density** — increases as composition becomes heavier

### Multi-Layer Stratification

The tank model supports multiple horizontal layers to simulate stratification.
When LNG of different compositions co-exists (e.g., heel + new cargo, or
ship-to-ship transfer), layers with different densities can form. Key physics:

- **Density inversion** — heavier liquid on top of lighter liquid
- **Natural convection** — Rayleigh-number-based mixing criteria
- **Inter-layer diffusion** — Fick's first law molecular diffusion between layers
- **Layer merging** — layers within density threshold merge automatically

### Boil-Off Gas (BOG) Handling

The `LNGBOGHandlingNetwork` allocates generated BOG across:

1. **Engine fuel** — DFDE, MEGI, or steam turbine consumption
2. **Reliquefaction** — partial or full BOG recondensation
3. **Gas Combustion Unit (GCU)** — excess BOG combustion
4. **Venting** — emergency only (last resort)

## Advanced Features

### Tank Geometry

Use `TankGeometry` to define the physical containment:

```java
TankGeometry membrane = TankGeometry.createQMax();    // 174,000 m3 membrane
TankGeometry moss = TankGeometry.createMossSingle();   // 36,000 m3 Moss sphere
TankGeometry typeC = TankGeometry.createTypeC();       // 7,500 m3 Type C

scenario.setTankGeometry(membrane);  // Wires geometry into tank model
```

The geometry provides surface areas for heat transfer zones, wetted wall areas,
liquid height calculations, and insulation U-values specific to each containment type.

### Multi-Zone Heat Transfer

The `TankHeatTransferModel` replaces the simple U*A*ΔT calculation with zone-specific
heat transfer:

```java
TankHeatTransferModel htModel = new TankHeatTransferModel(geometry, 308.15);
// Automatically creates zones: bottom, sidewalls, roof, cofferdam

// Update for varying ambient conditions
htModel.updateBoundaryConditions(310.0, 600.0, 285.0);
// (ambientTemp, solarRadiation, seaWaterTemp)
```

Heat distribution to layers: bottom heat goes to the bottom layer, roof heat to
the top 30%, sidewall heat distributed proportionally.

### Methane Number Calculation

Three methods are available:

```java
MethaneNumberCalculator calc = new MethaneNumberCalculator();

// EN 16726 (European standard)
calc.setMethod(MethaneNumberCalculator.Method.EN16726);
double mn = calc.calculate(composition);

// MWM (engine manufacturer method)
calc.setMethod(MethaneNumberCalculator.Method.MWM);

// Simplified (fast heuristic)
calc.setMethod(MethaneNumberCalculator.Method.SIMPLIFIED);

// All at once
Map<String, Double> allMN = calc.calculateAll(composition);

// Specification check
boolean ok = calc.meetsSpecification(composition, 70.0);
```

### Sloshing Model

Sea state affects LNG tanks through sloshing, which:

- Enhances inter-layer mixing (accelerates de-stratification)
- Increases BOG generation (surface renewal effect)
- Is containment-type dependent (Membrane > SPB > Type-C > Moss)

```java
LNGSloshingModel sloshing = new LNGSloshingModel(TankGeometry.ContainmentType.MEMBRANE);
double mixingFactor = sloshing.calculateMixingFactor(2.5, 0.5); // Hs=2.5m, 50% fill
double bogFactor = sloshing.calculateBOGEnhancement(2.5, 0.5);
```

Fill level effect follows a bell curve centered at ~40% fill (worst for sloshing).

### Rollover Detection

The `LNGRolloverDetector` monitors stratified tanks for rollover risk:

```java
LNGRolloverDetector detector = new LNGRolloverDetector();
detector.setDensityWarningThreshold(2.0);  // kg/m3
detector.setDensityAlarmThreshold(5.0);     // kg/m3

RolloverAssessment result = detector.assess(tankModel.getLayers());
System.out.println("Risk: " + result.getRiskLevel());
System.out.println("Max density diff: " + result.getMaxDensityDifference());

// Time-to-rollover prediction (after multiple assessments)
double ttr = result.getEstimatedTimeToRolloverHours();
```

### Voyage Profile

Define time-varying environmental conditions along the route:

```java
LNGVoyageProfile profile = new LNGVoyageProfile("Qatar to Japan");
profile.addSegment(new Segment(0, 120, 308.15, 1.0, 10.0, 800.0));    // Persian Gulf
profile.addSegment(new Segment(120, 240, 305.15, 1.5, 15.0, 700.0));   // Indian Ocean
profile.addSegment(new Segment(240, 400, 300.15, 2.0, 20.0, 500.0));   // South China Sea
profile.addSegment(new Segment(400, 480, 293.15, 1.0, 10.0, 400.0));   // Arrival

scenario.setVoyageProfile(profile);
```

### Heel Management

Model the mixing of residual heel with new cargo:

```java
LNGHeelManager heel = scenario.getHeelManager();
heel.setHeelState(heelComposition, 112.0, 425.0);
// Heel typically 3% of tank volume, aged/methane-rich
```

### Multi-Tank Ship Model

The `LNGShipModel` runs multiple tanks in parallel and aggregates results:

```java
LNGShipModel ship = new LNGShipModel("Q-Max Carrier");

// Add 4 membrane tanks
for (int i = 1; i <= 4; i++) {
    LNGAgeingScenario tank = new LNGAgeingScenario("Tank " + i, feed);
    tank.setTankVolume(43500.0);    // 174,000/4
    tank.setInitialFillingRatio(0.98);
    // ... configure each tank
    ship.addTank(tank);
}

// Shared BOG network
ship.getBogNetwork().setHandlingMode(HandlingMode.FUEL_PLUS_RELIQUEFACTION);
ship.getBogNetwork().setBaseFuelConsumption(3000.0);
ship.getBogNetwork().setReliquefactionCapacity(5000.0);

ship.setSimulationTime(480.0);
ship.setTimeStepHours(1.0);
ship.run();

System.out.println("Cargo loss: " + ship.getTotalCargoLossPct() + "%");
System.out.println(ship.getShipSummary());
```

### GERG-2008 Equation of State

For high-accuracy density calculations, enable GERG-2008:

```java
scenario.setUseGERG2008(true);
// The tank model will use SystemGERG2008Eos for density
// when available — falls back to ISO 6578 Klosek-McKinley otherwise.
```

### Operational Events

Track loading, unloading, and port operations:

```java
scenario.addOperationalEvent(new OperationalEvent(
    EventType.LOADING, 0.0, 12.0));            // 12h loading
scenario.addOperationalEvent(new OperationalEvent(
    EventType.LADEN_VOYAGE, 12.0, 480.0));     // 480h laden voyage
scenario.addOperationalEvent(new OperationalEvent(
    EventType.UNLOADING, 492.0, 18.0));        // 18h unloading
```

## Quality KPIs Tracked

| KPI | Unit | Method |
|-----|------|--------|
| Temperature | K | Mass-averaged across layers |
| Density | kg/m³ | ISO 6578 (Klosek-McKinley) or GERG-2008 |
| Wobbe Index | MJ/Sm³ | ISO 6976 |
| Gross Calorific Value | MJ/Sm³ | ISO 6976 |
| Methane Number | — | EN 16726, MWM, or simplified |
| Boil-Off Rate | %/day | Mass loss / initial mass × 24 |
| BOG mass flow | kg/hr | From VLE flash |
| Heat ingress | kW | Multi-zone heat transfer |
| Rollover risk | Level | Density inversion + Rayleigh number |

## Class Diagram

```
LNGShipModel
  ├── LNGAgeingScenario (per tank)
  │     ├── LNGTankLayeredModel
  │     │     ├── LNGTankLayer[] (1..N layers)
  │     │     ├── TankGeometry (optional)
  │     │     ├── TankHeatTransferModel (optional)
  │     │     ├── MethaneNumberCalculator (optional)
  │     │     └── LNGSloshingModel (optional)
  │     ├── LNGVaporSpaceModel
  │     ├── LNGRolloverDetector
  │     ├── LNGBOGHandlingNetwork
  │     ├── LNGHeelManager
  │     └── LNGVoyageProfile
  └── LNGBOGHandlingNetwork (shared header)
```

## Related Documentation

- [ISO 6578 Standard](../standards/iso6578.md) — LNG density calculation
- [ISO 6976 Standard](../standards/iso6976.md) — Calorific value and Wobbe index
- [Process Equipment Overview](../process/index.md) — All NeqSim process equipment

## Example Notebooks

- [LNG Ageing Basics](../examples/notebooks/lng_ageing_basics.ipynb) — Single-tank simulation with visualization
- [LNG Advanced Features](../examples/notebooks/lng_ageing_advanced.ipynb) — Tank geometry, sloshing, methane number, rollover
- [LNG Ship Voyage Simulation](../examples/notebooks/lng_ship_voyage.ipynb) — Multi-tank Q-Max carrier voyage
