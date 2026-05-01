---
title: Power Generation Equipment
description: "Documentation for power generation equipment in NeqSim: gas turbines, steam turbines, HRSG, combined-cycle systems, fuel cells, wind turbines, and solar panels."
---

# Power Generation Equipment

Documentation for power generation equipment in NeqSim, including gas turbines, steam turbines, HRSG, combined-cycle systems, fuel cells, wind turbines, and solar panels.

## Table of Contents
- [Overview](#overview)
- [Gas Turbine](#gas-turbine)
- [Steam Turbine](#steam-turbine)
- [HRSG](#hrsg)
- [Combined Cycle System](#combined-cycle-system)
- [Fuel Cell](#fuel-cell)
- [Wind Turbine](#wind-turbine)
- [Solar Panel](#solar-panel)
- [Battery Storage](#battery-storage)
- [Usage Examples](#usage-examples)
- [Capacity Constraints and Optimization](#capacity-constraints-and-optimization)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.powergeneration`

The power generation package provides equipment models for converting chemical and renewable energy sources into electrical power:

| Equipment | Energy Source | Output |
|-----------|---------------|--------|
| `GasTurbine` | Fuel gas combustion | Electricity + heat |
| `SteamTurbine` | High-pressure steam | Electricity |
| `HRSG` | Gas turbine exhaust | Steam |
| `CombinedCycleSystem` | Fuel gas (GT + HRSG + ST) | Electricity (high efficiency) |
| `FuelCell` | Hydrogen + oxygen | Electricity + water |
| `WindTurbine` | Wind | Electricity |
| `SolarPanel` | Solar radiation | Electricity |
| `BatteryStorage` | Stored electricity | Electricity |

---

## Gas Turbine

The `GasTurbine` class models a simple cycle gas turbine with integrated air compression, combustion, and expansion.

### Class Hierarchy

```
TwoPortEquipment
└── GasTurbine
```

### Constructor

```java
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.stream.Stream;

// Basic constructor
GasTurbine turbine = new GasTurbine("GT-101");

// Constructor with fuel stream
GasTurbine turbine = new GasTurbine("GT-101", fuelGasStream);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `combustionPressure` | Combustor pressure | bara |
| `airGasRatio` | Air to fuel ratio | - |
| `power` | Net electrical power output | W |
| `heat` | Heat output | W |
| `compressorPower` | Air compressor power | W |
| `expanderPower` | Expander power | W |

### Example Usage

```java
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create fuel gas
SystemInterface fuelGas = new SystemSrkEos(288.15, 25.0);
fuelGas.addComponent("methane", 0.90);
fuelGas.addComponent("ethane", 0.05);
fuelGas.addComponent("propane", 0.03);
fuelGas.addComponent("nitrogen", 0.02);
fuelGas.setMixingRule("classic");

Stream fuelStream = new Stream("Fuel Gas", fuelGas);
fuelStream.setFlowRate(1000.0, "kg/hr");

// Create gas turbine
GasTurbine turbine = new GasTurbine("Power Turbine", fuelStream);
turbine.combustionpressure = 15.0;  // bara (public field)

// Run simulation
turbine.run();

// Results
System.out.println("Net power: " + turbine.getPower() / 1e6 + " MW");
System.out.println("Heat output: " + turbine.getHeat() / 1e6 + " MW");
System.out.println("Ideal air/fuel ratio: " + turbine.calcIdealAirFuelRatio());
```

---

## Steam Turbine

The `SteamTurbine` class models isentropic expansion of high-pressure steam to produce power. Outlet conditions are computed via PS-flash (isentropic) followed by PH-flash (actual) using the specified isentropic efficiency.

### Class Hierarchy

```
TwoPortEquipment
└── SteamTurbine
```

### Constructor

```java
import neqsim.process.equipment.powergeneration.SteamTurbine;

// Basic constructor
SteamTurbine st = new SteamTurbine("ST-100");

// Constructor with inlet steam stream
SteamTurbine st = new SteamTurbine("ST-100", steamStream);
```

### Key Properties

| Property | Setter | Default | Unit |
|----------|--------|---------|------|
| `outletPressure` | `setOutletPressure(p)` / `setOutletPressure(p, "bara")` | 1.01325 | bara |
| `isentropicEfficiency` | `setIsentropicEfficiency(e)` | 0.85 | 0-1 |
| `numberOfStages` | `setNumberOfStages(n)` | 1 | - |
| `power` | (result) | - | W |

### Example Usage

```java
import neqsim.process.equipment.powergeneration.SteamTurbine;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create superheated steam
SystemInterface steam = new SystemSrkEos(273.15 + 450.0, 40.0);
steam.addComponent("water", 1.0);
steam.setMixingRule("classic");

Stream steamFeed = new Stream("HP Steam", steam);
steamFeed.setFlowRate(50000.0, "kg/hr");

// Create steam turbine
SteamTurbine turbine = new SteamTurbine("ST-100", steamFeed);
turbine.setOutletPressure(0.05, "bara");
turbine.setIsentropicEfficiency(0.88);
turbine.run();

System.out.println("Power output: " + turbine.getPower("MW") + " MW");
```

---

## HRSG

The `HRSG` (Heat Recovery Steam Generator) class models counter-current heat exchange between hot gas turbine exhaust and a water/steam loop. It calculates the heat transferred and the steam production rate for given steam conditions.

### Class Hierarchy

```
TwoPortEquipment
└── HRSG
```

### Constructor

```java
import neqsim.process.equipment.powergeneration.HRSG;

// Basic constructor
HRSG hrsg = new HRSG("HRSG-1");

// Constructor with hot gas inlet (gas turbine exhaust)
HRSG hrsg = new HRSG("HRSG-1", gasTurbineExhaust);
```

### Key Properties

| Property | Setter | Default | Unit |
|----------|--------|---------|------|
| `steamPressure` | `setSteamPressure(p)` | 40.0 | bara |
| `steamTemperature` | `setSteamTemperature(t)` / `setSteamTemperature(t, "C")` | 400 C | K |
| `feedWaterTemperature` | `setFeedWaterTemperature(t)` / `setFeedWaterTemperature(t, "C")` | 60 C | K |
| `approachTemperature` | `setApproachTemperature(dT)` | 15.0 | K |
| `effectiveness` | `setEffectiveness(e)` | 0.85 | 0-1 |

### Results

| Method | Returns | Unit |
|--------|---------|------|
| `getHeatTransferred()` / `getHeatTransferred("MW")` | Heat to steam | W / MW |
| `getSteamFlowRate()` / `getSteamFlowRate("kg/hr")` | Steam production | kg/s / kg/hr |
| `getGasOutletTemperature()` | Gas stack temperature | K |

### Example Usage

```java
HRSG hrsg = new HRSG("HRSG-1", turbine.getOutletStream());
hrsg.setSteamPressure(40.0);
hrsg.setSteamTemperature(400.0, "C");
hrsg.setApproachTemperature(15.0);
hrsg.run();

System.out.println("Heat recovered: " + hrsg.getHeatTransferred("MW") + " MW");
System.out.println("Steam production: " + hrsg.getSteamFlowRate("kg/hr") + " kg/hr");
```

---

## Combined Cycle System

The `CombinedCycleSystem` class integrates a `GasTurbine`, `HRSG`, and `SteamTurbine` into a single equipment unit. It models the full gas turbine combined cycle (GTCC) workflow:

1. Fuel gas combustion in the gas turbine
2. Exhaust heat recovery in the HRSG to produce steam
3. Steam expansion through the steam turbine

### Class Hierarchy

```
TwoPortEquipment
└── CombinedCycleSystem  (composes GasTurbine + HRSG + SteamTurbine)
```

### Constructor

```java
import neqsim.process.equipment.powergeneration.CombinedCycleSystem;

CombinedCycleSystem cc = new CombinedCycleSystem("CC-Plant");
CombinedCycleSystem cc = new CombinedCycleSystem("CC-Plant", fuelGasStream);
```

### Key Properties

| Property | Setter | Default | Unit |
|----------|--------|---------|------|
| `combustionPressure` | `combustionpressure` (public field) | 2.5 | bara |
| `steamPressure` | `setSteamPressure(p)` | 40.0 | bara |
| `steamTemperature` | `setSteamTemperature(t, "C")` | 400.0 | C |
| `steamTurbineEfficiency` | `setSteamTurbineEfficiency(e)` | 0.85 | 0-1 |
| `steamCondensorPressure` | `setSteamCondensorPressure(p)` | 0.05 | bara |
| `hrsgApproachTemperature` | `setHrsgApproachTemperature(dT)` | 15.0 | K |
| `hrsgEffectiveness` | `setHrsgEffectiveness(e)` | 0.85 | 0-1 |

### Results

| Method | Returns | Unit |
|--------|---------|------|
| `getTotalPower()` / `getTotalPower("MW")` | Combined GT + ST power | W / MW |
| `getGasTurbinePower()` | GT contribution | W |
| `getSteamTurbinePower()` | ST contribution | W |
| `getOverallEfficiency()` | LHV thermal efficiency | 0-1 |
| `getFuelEnergyInput()` | Fuel energy (LHV) | W |
| `toJson()` | Full results JSON | String |

### Example Usage

```java
CombinedCycleSystem cc = new CombinedCycleSystem("CC-1", fuelStream);
cc.setCombustionPressure(15.0);
cc.setSteamPressure(40.0);
cc.setSteamTemperature(400.0, "C");
cc.setSteamTurbineEfficiency(0.85);
cc.run();

System.out.println("Total power: " + cc.getTotalPower("MW") + " MW");
System.out.println("GT power: " + cc.getGasTurbinePower() / 1e6 + " MW");
System.out.println("ST power: " + cc.getSteamTurbinePower() / 1e6 + " MW");
System.out.println("Efficiency: " + cc.getOverallEfficiency() * 100 + "%");
System.out.println(cc.toJson());
```

---

## Fuel Cell

### Class Hierarchy

```
TwoPortEquipment
└── FuelCell
```

### Constructor

```java
import neqsim.process.equipment.powergeneration.FuelCell;

// Basic constructor
FuelCell cell = new FuelCell("FC-101");

// Constructor with fuel and oxidant streams
FuelCell cell = new FuelCell("FC-101", hydrogenStream, airStream);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `efficiency` | Electrical efficiency | 0-1 |
| `power` | Electrical power output | W |
| `heatLoss` | Heat loss to environment | W |

### Example Usage

```java
import neqsim.process.equipment.powergeneration.FuelCell;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create hydrogen fuel stream
SystemInterface h2Fluid = new SystemSrkEos(298.15, 5.0);
h2Fluid.addComponent("hydrogen", 1.0);
h2Fluid.setMixingRule("classic");

Stream hydrogenFeed = new Stream("Hydrogen", h2Fluid);
hydrogenFeed.setFlowRate(10.0, "kg/hr");

// Create air stream
SystemInterface airFluid = new SystemSrkEos(298.15, 1.01325);
airFluid.addComponent("nitrogen", 0.79);
airFluid.addComponent("oxygen", 0.21);
airFluid.setMixingRule("classic");

Stream airFeed = new Stream("Air", airFluid);
airFeed.setFlowRate(100.0, "kg/hr");

// Create fuel cell
FuelCell fuelCell = new FuelCell("SOFC", hydrogenFeed, airFeed);
fuelCell.setEfficiency(0.55);

// Run simulation
fuelCell.run();

// Results
System.out.println("Electrical power: " + fuelCell.getPower() / 1000 + " kW");
System.out.println("Heat loss: " + fuelCell.getHeatLoss() / 1000 + " kW");
```

---

## Wind Turbine

The `WindTurbine` class models wind power generation based on wind speed and turbine characteristics.

### Constructor

```java
import neqsim.process.equipment.powergeneration.WindTurbine;

WindTurbine turbine = new WindTurbine("WT-01");
turbine.setWindSpeed(12.0);          // m/s
turbine.setRotorArea(11310.0);       // m² (e.g. pi * 60² for 120m diameter)
turbine.setPowerCoefficient(0.45);   // Betz limit max ~0.593
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `windSpeed` | Wind velocity | m/s |
| `rotorArea` | Rotor swept area | m² |
| `powerCoefficient` | Aerodynamic efficiency (Cp) | 0-0.593 (Betz limit) |
| `airDensity` | Air density | kg/m³ |
| `power` | Electrical power output | W |

---

## Solar Panel

The `SolarPanel` class models photovoltaic power generation.

### Constructor

```java
import neqsim.process.equipment.powergeneration.SolarPanel;

SolarPanel panel = new SolarPanel("PV-Array");
panel.setPanelArea(1000.0);        // m²
panel.setIrradiance(800.0);        // W/m²
panel.setEfficiency(0.20);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `panelArea` | Total panel area | m² |
| `irradiance` | Solar radiation | W/m² |
| `efficiency` | Panel efficiency | 0-1 |
| `power` | Electrical power output | W |

---

## Battery Storage

**Location:** `neqsim.process.equipment.battery`

The `BatteryStorage` class models electrical energy storage systems.

### Constructor

```java
import neqsim.process.equipment.battery.BatteryStorage;

BatteryStorage battery = new BatteryStorage("BESS-01");
battery.setCapacity(3.6e11);         // Joules (= 100 MWh)
// Note: charge/discharge efficiencies are internal (default 0.95 each)
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `capacity` | Total energy capacity | J |
| `stateOfCharge` | Current energy level | J |
| `stateOfChargeFraction` | SOC as fraction | 0-1 |

### Operations

```java
battery.charge(25e6, 2.0);         // charge at 25 MW for 2 hours
double delivered = battery.discharge(25e6, 1.0);  // discharge at 25 MW for 1 hour
double soc = battery.getStateOfChargeFraction();   // 0-1
```

---

## Usage Examples

### Combined Heat and Power (CHP) System

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.HRSG;

ProcessSystem chpSystem = new ProcessSystem("CHP Plant");

// Create fuel gas stream
Stream fuelGas = new Stream("Fuel", fuelFluid);
fuelGas.setFlowRate(500.0, "kg/hr");
chpSystem.add(fuelGas);

// Gas turbine
GasTurbine turbine = new GasTurbine("GT", fuelGas);
turbine.combustionpressure = 12.0;
chpSystem.add(turbine);

// Heat recovery steam generator
HRSG hrsg = new HRSG("HRSG", turbine.getOutletStream());
hrsg.setSteamPressure(20.0);
hrsg.setSteamTemperature(250.0, "C");
hrsg.setApproachTemperature(15.0);
chpSystem.add(hrsg);

// Run
chpSystem.run();

// Calculate efficiency
double electricalPower = turbine.getPower();
double thermalPower = hrsg.getHeatTransferred();
double fuelInput = fuelGas.getFlowRate("kg/hr") * 50e6 / 3600;  // LHV ~ 50 MJ/kg

double electricalEff = electricalPower / fuelInput;
double totalEff = (electricalPower + thermalPower) / fuelInput;

System.out.println("Electrical efficiency: " + electricalEff * 100 + "%");
System.out.println("Total CHP efficiency: " + totalEff * 100 + "%");
System.out.println("Steam production: " + hrsg.getSteamFlowRate("kg/hr") + " kg/hr");
```

### Combined Cycle Power Plant

```java
import neqsim.process.equipment.powergeneration.CombinedCycleSystem;

// One-call combined cycle with internal GT + HRSG + ST
CombinedCycleSystem ccPlant = new CombinedCycleSystem("GTCC", fuelGasStream);
ccPlant.setCombustionPressure(15.0);
ccPlant.setSteamPressure(40.0);
ccPlant.setSteamTemperature(400.0, "C");
ccPlant.setSteamTurbineEfficiency(0.85);
ccPlant.run();

System.out.println("Total power: " + ccPlant.getTotalPower("MW") + " MW");
System.out.println("GT power: " + ccPlant.getGasTurbinePower() / 1e6 + " MW");
System.out.println("ST power: " + ccPlant.getSteamTurbinePower() / 1e6 + " MW");
System.out.println("Overall efficiency: " + ccPlant.getOverallEfficiency() * 100 + "%");
```

### Hybrid Renewable System

```java
// Solar + Wind + Battery system
SolarPanel solar = new SolarPanel("PV");
solar.setPanelArea(5000.0);
solar.setIrradiance(600.0);
solar.setEfficiency(0.18);

WindTurbine wind = new WindTurbine("Wind");
wind.setWindSpeed(8.0);
wind.setRotorArea(5027.0);  // ~80m diameter

BatteryStorage battery = new BatteryStorage("Battery");
battery.setCapacity(3.6e10);  // 10 MWh in Joules

// Calculate total renewable generation
solar.run();
wind.run();

double totalGeneration = solar.getPower() + wind.getPower();
System.out.println("Total renewable power: " + totalGeneration / 1e6 + " MW");
```

---

## Capacity Constraints and Optimization

All four core power generation equipment types (`GasTurbine`, `SteamTurbine`, `HRSG`, `CombinedCycleSystem`) implement the `CapacityConstrainedEquipment` and `AutoSizeable` interfaces. This enables rated-capacity tracking, bottleneck detection, and integration with the plant-wide `ProcessOptimizationEngine`.

### Interfaces

| Interface | Purpose |
|-----------|---------|
| `CapacityConstrainedEquipment` | Tracks operating point vs rated capacity, detects violations |
| `AutoSizeable` | Sets rated capacity from current operating point with a safety factor |

### Setting Rated Capacity

Each equipment type has a rated capacity field that defines its design limit:

```java
// Gas turbine — rated power
GasTurbine gt = new GasTurbine("GT-1", fuelStream);
gt.setRatedPower(30.0, "MW");  // 30 MW design rating

// Steam turbine — rated power
SteamTurbine st = new SteamTurbine("ST-1", steamStream);
st.setRatedPower(15.0, "MW");

// HRSG — design heat duty
HRSG hrsg = new HRSG("HRSG-1", gt.getOutletStream());
hrsg.setDesignHeatDuty(50.0, "MW");

// Combined cycle — rated total power
CombinedCycleSystem cc = new CombinedCycleSystem("CC-1", fuelStream);
cc.setRatedTotalPower(45.0, "MW");
```

### Auto-Sizing

When design ratings are not known, auto-size from the current operating point:

```java
// Run the process first to establish operating conditions
process.run();

// Auto-size with a 20% safety margin (factor = 1.2)
gt.autoSize(1.2);
st.autoSize(1.2);
hrsg.autoSize(1.2);
cc.autoSize(1.2);

// Check sizing results
System.out.println(gt.getSizingReport());
System.out.println(gt.isAutoSized());  // true
```

### Querying Capacity

After running and setting rated capacity (manually or via auto-size), query the operating margin:

```java
// Current operating duty vs maximum
double duty = gt.getCapacityDuty();  // current power output (W)
double max = gt.getCapacityMax();    // rated power (W)

// Utilization fraction (0-1, where 1.0 = at capacity)
double utilization = gt.getMaxUtilization();

// Check if any constraint is violated
boolean exceeded = gt.isCapacityExceeded();    // soft or hard limit
boolean hardTrip = gt.isHardLimitExceeded();   // hard limit only

// Get the most-loaded constraint
CapacityConstraint bottleneck = gt.getBottleneckConstraint();
if (bottleneck != null) {
    System.out.println(bottleneck.getName() + ": " + bottleneck.getUtilization());
}

// Iterate all constraints
for (Map.Entry<String, CapacityConstraint> entry :
        gt.getCapacityConstraints().entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue().getUtilization());
}
```

### Adding Custom Constraints

The built-in constraint (power or heat duty) covers the primary capacity limit. Add additional constraints for operational envelopes:

```java
import neqsim.process.equipment.capacity.CapacityConstraint;

// Add an exhaust temperature limit to the gas turbine
gt.addCapacityConstraint(
    new CapacityConstraint("exhaustTemp", "C", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(550.0)
        .setMaxValue(600.0)
        .setWarningThreshold(0.90)
        .setDescription("Exhaust gas temperature limit")
        .setValueSupplier(() -> gt.getOutletStream().getTemperature("C")));

// Remove a constraint by name
gt.removeCapacityConstraint("exhaustTemp");

// Clear all constraints
gt.clearCapacityConstraints();
```

### Integration with ProcessOptimizationEngine

The `ProcessOptimizationEngine` reads capacity constraints from all equipment in a `ProcessSystem` to find the plant-wide maximum throughput and identify bottlenecks.

#### Bottleneck Detection

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

// Build a combined heat and power system
ProcessSystem chp = new ProcessSystem();
chp.add(fuelStream);
chp.add(gt);
chp.add(hrsg);
chp.run();

// Set rated capacities
gt.setRatedPower(30.0, "MW");
hrsg.setDesignHeatDuty(50.0, "MW");

// Evaluate constraints across the entire process
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(chp);
ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
        report.getEquipmentStatuses()) {
    System.out.printf("%s %s: %.1f%% utilization%n",
        status.isWithinLimits() ? "OK" : "!!",
        status.getEquipmentName(),
        status.getMaxUtilization() * 100);
}
```

#### Maximum Throughput Optimization

Find how much fuel gas the system can handle before hitting a capacity limit:

```java
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(
        25.0,       // inlet pressure (bara)
        25.0,       // outlet pressure (bara — no compression)
        100.0,      // min fuel flow (kg/hr)
        10000.0     // max fuel flow (kg/hr)
    );

System.out.println("Max fuel rate: " + result.getOptimalFlowRate() + " kg/hr");
System.out.println("Bottleneck: " + result.getBottleneckEquipment());
System.out.println("Total power at max: " + result.getTotalPower() + " kW");
```

#### Capacity Utilization Summary

For a quick overview of all equipment headroom in the process:

```java
Map<String, Double> utilization = chp.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    System.out.printf("%-25s %.1f%%%n", entry.getKey(), entry.getValue() * 100);
}
// Example output:
// GT-1                      72.3%
// HRSG-1                    65.8%
```

### Python (Jupyter) Example

```python
from neqsim import jneqsim

# Create fuel gas
gas = jneqsim.thermo.system.SystemSrkEos(288.15, 25.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.05)
gas.addComponent("propane", 0.03)
gas.addComponent("nitrogen", 0.02)
gas.setMixingRule("classic")

Stream = jneqsim.process.equipment.stream.Stream
GasTurbine = jneqsim.process.equipment.powergeneration.GasTurbine
HRSG = jneqsim.process.equipment.powergeneration.HRSG
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

fuel = Stream("Fuel Gas", gas)
fuel.setFlowRate(1000.0, "kg/hr")

gt = GasTurbine("GT-1", fuel)
hrsg = HRSG("HRSG-1", gt.getOutletStream())
hrsg.setSteamPressure(40.0)

process = ProcessSystem()
process.add(fuel)
process.add(gt)
process.add(hrsg)
process.run()

# Auto-size with 20% margin
gt.autoSize(1.2)
hrsg.autoSize(1.2)

# Check utilization
print(f"GT utilization:   {gt.getMaxUtilization() * 100:.1f}%")
print(f"HRSG utilization: {hrsg.getMaxUtilization() * 100:.1f}%")
print(f"GT capacity exceeded: {gt.isCapacityExceeded()}")

# Sizing report
print(gt.getSizingReport())
```

---

## Related Documentation

- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) - Equipment capacity limits and bottleneck detection
- [Optimization Overview](../optimization/OPTIMIZATION_OVERVIEW) - When to use which optimizer
- [Optimization Practical Examples](../optimization/PRACTICAL_EXAMPLES) - Java and Python optimization code samples
- [Heat Integration (Pinch Analysis)](heat_integration) - Minimum utility targeting
- [Electrolyzers](electrolyzers) - Hydrogen production
- [Compressors](compressors) - Gas compression
- [Heat Exchangers](heat_exchangers) - Heat recovery
- [Sustainability](../sustainability/) - Emissions tracking
