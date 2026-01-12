# Power Generation Equipment

Documentation for power generation equipment in NeqSim, including gas turbines, fuel cells, wind turbines, and solar panels.

## Table of Contents
- [Overview](#overview)
- [Gas Turbine](#gas-turbine)
- [Fuel Cell](#fuel-cell)
- [Wind Turbine](#wind-turbine)
- [Solar Panel](#solar-panel)
- [Battery Storage](#battery-storage)
- [Usage Examples](#usage-examples)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.powergeneration`

The power generation package provides equipment models for converting chemical and renewable energy sources into electrical power:

| Equipment | Energy Source | Output |
|-----------|---------------|--------|
| `GasTurbine` | Fuel gas combustion | Electricity + heat |
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
turbine.setCombustionPressure(15.0);  // bara
turbine.setAirGasRatio(3.0);

// Run simulation
turbine.run();

// Results
System.out.println("Net power: " + turbine.getPower() / 1e6 + " MW");
System.out.println("Heat output: " + turbine.getHeat() / 1e6 + " MW");
System.out.println("Thermal efficiency: " + turbine.getEfficiency() * 100 + "%");
```

---

## Fuel Cell

The `FuelCell` class models a hydrogen fuel cell that converts hydrogen and oxygen to electricity and water.

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
turbine.setWindSpeed(12.0);        // m/s
turbine.setRotorDiameter(120.0);   // m
turbine.setEfficiency(0.45);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `windSpeed` | Wind velocity | m/s |
| `rotorDiameter` | Rotor diameter | m |
| `efficiency` | Power coefficient | 0-0.593 (Betz limit) |
| `power` | Electrical power output | W |

---

## Solar Panel

The `SolarPanel` class models photovoltaic power generation.

### Constructor

```java
import neqsim.process.equipment.powergeneration.SolarPanel;

SolarPanel panel = new SolarPanel("PV-Array");
panel.setPanelArea(1000.0);        // m²
panel.setSolarIrradiance(800.0);   // W/m²
panel.setEfficiency(0.20);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `panelArea` | Total panel area | m² |
| `solarIrradiance` | Solar radiation | W/m² |
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
battery.setCapacity(100.0);          // MWh
battery.setMaxPower(25.0);           // MW
battery.setRoundTripEfficiency(0.90);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `capacity` | Total energy capacity | MWh |
| `maxPower` | Maximum charge/discharge rate | MW |
| `roundTripEfficiency` | Charge-discharge efficiency | 0-1 |
| `stateOfCharge` | Current energy level | 0-1 |

---

## Usage Examples

### Combined Heat and Power (CHP) System

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.heatexchanger.Heater;

ProcessSystem chpSystem = new ProcessSystem("CHP Plant");

// Create fuel gas stream
Stream fuelGas = new Stream("Fuel", fuelFluid);
fuelGas.setFlowRate(500.0, "kg/hr");
chpSystem.add(fuelGas);

// Gas turbine
GasTurbine turbine = new GasTurbine("GT", fuelGas);
turbine.setCombustionPressure(12.0);
chpSystem.add(turbine);

// Heat recovery steam generator (simplified)
Heater hrsg = new Heater("HRSG", turbine.getExhaustStream());
hrsg.setOutTemperature(150.0, "C");
chpSystem.add(hrsg);

// Run
chpSystem.run();

// Calculate efficiency
double electricalPower = turbine.getPower();
double thermalPower = hrsg.getDuty();
double fuelInput = fuelGas.getFlowRate("kg/hr") * 50e6 / 3600;  // LHV ~ 50 MJ/kg

double electricalEff = electricalPower / fuelInput;
double totalEff = (electricalPower + thermalPower) / fuelInput;

System.out.println("Electrical efficiency: " + electricalEff * 100 + "%");
System.out.println("Total CHP efficiency: " + totalEff * 100 + "%");
```

### Hybrid Renewable System

```java
// Solar + Wind + Battery system
SolarPanel solar = new SolarPanel("PV");
solar.setPanelArea(5000.0);
solar.setSolarIrradiance(600.0);
solar.setEfficiency(0.18);

WindTurbine wind = new WindTurbine("Wind");
wind.setWindSpeed(8.0);
wind.setRotorDiameter(80.0);

BatteryStorage battery = new BatteryStorage("Battery");
battery.setCapacity(10.0);  // MWh
battery.setMaxPower(5.0);   // MW

// Calculate total renewable generation
solar.run();
wind.run();

double totalGeneration = solar.getPower() + wind.getPower();
System.out.println("Total renewable power: " + totalGeneration / 1e6 + " MW");
```

---

## Related Documentation

- [Electrolyzers](electrolyzers.md) - Hydrogen production
- [Compressors](compressors.md) - Gas compression
- [Heat Exchangers](heat_exchangers.md) - Heat recovery
- [Sustainability](../../process/sustainability/README.md) - Emissions tracking
