---
title: Electrolyzer Equipment
description: "Documentation for electrolyzer equipment in NeqSim — water electrolysis (green hydrogen), PEM/Alkaline/SOEC/AEM technologies, I-V polarisation models, power-driven operation, stack sizing, turndown/standby, ramp limits, balance-of-plant losses, thermal and water consumption, delivery pressure, and CO2 electrolysis."
---

# Electrolyzer Equipment

Documentation for electrolyzer equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Electrolyzer Class](#electrolyzer-class)
- [Technologies and I-V Models](#technologies-and-i-v-models)
- [Operating Modes](#operating-modes)
- [Power-Driven Mode and Stack Sizing](#power-driven-mode-and-stack-sizing)
- [Turndown, Standby and Curtailment](#turndown-standby-and-curtailment)
- [Ramp-Rate Limits and Transient Operation](#ramp-rate-limits-and-transient-operation)
- [Balance-of-Plant Losses](#balance-of-plant-losses)
- [Thermal Model and Water Consumption](#thermal-model-and-water-consumption)
- [Hydrogen Delivery Pressure](#hydrogen-delivery-pressure)
- [Energy Calculations](#energy-calculations)
- [CO₂ Electrolyzer](#co2-electrolyzer)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.electrolyzer`

**Classes:**

| Class | Description |
|-------|-------------|
| `Electrolyzer` | Water electrolysis unit (green hydrogen) |
| `ElectrolyzerTechnology` | Enum of technology defaults (PEM, ALKALINE, SOEC, AEM) |
| `ElectrolyzerIVCharacteristic` | Polarisation (I-V) model driving cell voltage from current density |
| `CO2Electrolyzer` | CO₂ electrolysis unit |

Electrolyzers convert electrical energy into chemical energy through electrochemical reactions. Key applications:
- Green hydrogen production (water electrolysis)
- CO₂ reduction to fuels/chemicals
- Power-to-X systems
- Renewable energy storage with variable power input

The water `Electrolyzer` reaction is:

$$2\,H_2O \rightarrow 2\,H_2 + O_2$$

The hydrogen and oxygen products are exposed as `getHydrogenOutStream()` and
`getOxygenOutStream()`, and the electrical demand is published on the unit's
energy stream (`getEnergyStream().getDuty()`).

---

## Electrolyzer Class

### Basic Usage (water-feed mode)

In the default water-feed mode the inlet water molar rate sets production; the
electrical power is computed as an output.

```java
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

// Water feed
SystemInterface water = new Fluid().create("water");
Stream waterFeed = new Stream("water", water);
waterFeed.setPressure(1.0, "bara");
waterFeed.setTemperature(298.15, "K");
waterFeed.setFlowRate(2.0, "mole/sec");
waterFeed.run();

// Electrolyzer (default 1.23 V reversible cell voltage)
Electrolyzer electrolyzer = new Electrolyzer("PEM Electrolyzer", waterFeed);
electrolyzer.run();

// Products and power
double h2Rate = electrolyzer.getHydrogenOutStream().getFlowRate("kg/hr");
double o2Rate = electrolyzer.getOxygenOutStream().getFlowRate("kg/hr");
double power = electrolyzer.getEnergyStream().getDuty();   // W
double stackPower = electrolyzer.getStackPower();          // W

System.out.println("H2 production: " + h2Rate + " kg/hr");
System.out.println("Stack power:   " + power / 1.0e3 + " kW");
```

### Key Methods

| Method | Description |
|--------|-------------|
| `getHydrogenOutStream()` | Hydrogen product stream |
| `getOxygenOutStream()` | Oxygen product stream |
| `setCellVoltage(double)` / `getCellVoltage()` | Fixed operating cell voltage (V) |
| `setCurrentDensity(double)` / `getCurrentDensity()` | Current density (A/cm²), used with an I-V model |
| `setFaradaicEfficiency(double)` / `getFaradaicEfficiency()` | Current efficiency (0..1] |
| `getStackPower()` | Stack (DC) power from the last run (W) |
| `getSpecificEnergyConsumption_kWh_per_kg_H2()` | Stack specific energy (kWh/kg H₂) |
| `getMassBalance(String unit)` | Mass balance check |

---

## Technologies and I-V Models

Select a technology to apply representative defaults for cell voltage, current
density, operating temperature/pressure and faradaic efficiency:

```java
import neqsim.process.equipment.electrolyzer.ElectrolyzerTechnology;
import neqsim.process.equipment.electrolyzer.ElectrolyzerIVCharacteristic;

electrolyzer.setTechnology(ElectrolyzerTechnology.PEM);
```

Available technologies: `PEM`, `ALKALINE`, `SOEC`, `AEM`.

For a physically consistent operating voltage, attach an
`ElectrolyzerIVCharacteristic`. When set, the cell voltage is computed from the
current density and temperature through the polarisation model:

```java
electrolyzer.setIVCharacteristic(
    new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
electrolyzer.run();
double v = electrolyzer.getCellVoltage();   // e.g. ~1.85 V for PEM at 2 A/cm2, 80 C
```

---

## Operating Modes

The `Electrolyzer` supports two operating modes via
`setOperationMode(OperationMode)`:

| Mode | Driver | Output |
|------|--------|--------|
| `WATER_FEED` (default) | Inlet water molar rate | Electrical power |
| `POWER` | Available electrical power | Water demand and H₂/O₂ production |

`WATER_FEED` is the default and preserves backward-compatible behaviour.
`setAvailablePower(...)` automatically switches the unit into `POWER` mode.

---

## Power-Driven Mode and Stack Sizing

In `POWER` mode the unit is given an available electrical power and inverts the
stack power balance

$$P_{stack} = j \cdot A_{stack} \cdot V_{cell}(j, T)$$

for the current density $j$, then computes the hydrogen production and water
demand. This requires the stack geometry to be defined first.

### Sizing the stack

```java
// Size a 1 MW stack at the nominal current density (technology default) and inlet T
electrolyzer.setTechnology(ElectrolyzerTechnology.PEM);
electrolyzer.setIVCharacteristic(
    new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
electrolyzer.sizeStack(1.0e6);   // rated power 1 MW

// Or specify nominal current density and temperature explicitly
electrolyzer.sizeStack(1.0e6, 2.0, 353.15);  // 1 MW, 2 A/cm2, 80 C

// Or define geometry directly
electrolyzer.setActiveCellArea(1000.0);   // cm2 per cell
electrolyzer.setNumberOfCells(200);
// (or) electrolyzer.setStackActiveArea(200000.0);  // cm2 total
```

### Running on a power budget

```java
electrolyzer.setAvailablePower(0.5e6);   // 500 kW (switches to POWER mode)
electrolyzer.run();

double h2 = electrolyzer.getHydrogenOutStream().getFlowRate("kg/hr");
double current = electrolyzer.getStackCurrent();   // A
double stackPower = electrolyzer.getStackPower();  // W
```

If the stack geometry has not been configured, `run()` throws an
`IllegalStateException` describing how to size the stack.

| Method | Description |
|--------|-------------|
| `setAvailablePower(double)` | Available electrical (AC) power (W); switches to `POWER` mode |
| `sizeStack(double ratedPowerW)` | Size area from rated power and nominal current density |
| `sizeStack(double ratedPowerW, double nominalJ, double tempK)` | Size area explicitly |
| `setActiveCellArea(double)` / `setNumberOfCells(int)` | Per-cell area (cm²) and cell count |
| `setStackActiveArea(double)` / `getStackActiveArea()` | Total active area (cm²) |
| `setRatedPower(double)` / `getRatedPower()` | Nameplate stack power (W) |
| `setNominalCurrentDensity(double)` | Nominal current density (A/cm²) |
| `getStackCurrent()` | Total stack current from the last run (A) |

---

## Turndown, Standby and Curtailment

Electrolyzer stacks have a minimum turndown below which they idle, and an upper
limit at their rated power. In `POWER` mode:

- Available power **below** `minimumLoadFraction × rated` puts the stack into
  **standby**: no hydrogen is produced, and the energy duty is set to the
  standby auxiliary draw (`standbyPowerFraction × rated`).
- Available power **above** the rated power is **curtailed**; the excess is
  reported by `getCurtailedPower()`.

```java
electrolyzer.setMinimumLoadFraction(0.10);   // 10 % minimum load
electrolyzer.setStandbyPowerFraction(0.02);  // 2 % idle draw

electrolyzer.setAvailablePower(0.05e6);       // below 10 % of 1 MW
electrolyzer.run();
boolean idle = electrolyzer.isStandby();       // true, no H2

electrolyzer.setAvailablePower(1.5e6);         // above rated
electrolyzer.run();
double curtailed = electrolyzer.getCurtailedPower();  // ~0.5 MW
```

| Method | Description |
|--------|-------------|
| `setMinimumLoadFraction(double)` | Minimum load as a fraction of rated power (0..1) |
| `setStandbyPowerFraction(double)` | Idle auxiliary draw as a fraction of rated power (0..1) |
| `isStandby()` | Whether the last run left the stack idling |
| `getCurtailedPower()` | Electrical power curtailed above rated (W) |

---

## Ramp-Rate Limits and Transient Operation

Real stacks cannot follow instantaneous power steps. Set a maximum ramp rate (as
a fraction of rated power per second) and drive the unit with
`runTransient(dt, id)`. The operating power ramps from a cold start toward the
commanded `availablePower`, clamped by `maxRampRate × rated × dt` each step.

```java
import java.util.UUID;

electrolyzer.setMaxRampRate(0.1);          // 10 % of rated per second
electrolyzer.setCalculateSteadyState(false);
electrolyzer.setAvailablePower(1.0e6);     // commanded power

UUID id = UUID.randomUUID();
electrolyzer.runTransient(1.0, id);        // first second: reaches 0.1 MW
electrolyzer.runTransient(1.0, id);        // second: reaches 0.2 MW
double delivered = electrolyzer.getOperatingPower();   // 0.2 MW
```

| Method | Description |
|--------|-------------|
| `setMaxRampRate(double)` | Maximum ramp rate (fraction of rated power per second; 0 = unlimited) |
| `runTransient(double dt, UUID id)` | Advance one time step with ramp limiting |
| `getOperatingPower()` | System power delivered after the last transient step (W) |

---

## Balance-of-Plant Losses

The system (AC) power exceeds the stack (DC) power because of rectifier losses
and auxiliary loads (pumps, thermal management):

$$P_{system} = \frac{P_{stack}}{\eta_{rect}} + f_{aux}\,P_{stack}$$

Defaults (`rectifierEfficiency = 1.0`, `auxiliaryLoadFraction = 0.0`) make
system power equal stack power, preserving backward compatibility.

```java
electrolyzer.setRectifierEfficiency(0.95);    // 95 % AC/DC
electrolyzer.setAuxiliaryLoadFraction(0.05);  // 5 % of stack power

electrolyzer.setAvailablePower(0.5e6);
electrolyzer.run();

double stackSec  = electrolyzer.getSpecificEnergyConsumption_kWh_per_kg_H2();
double systemSec = electrolyzer.getSystemSpecificEnergyConsumption_kWh_per_kg_H2();
double systemPower = electrolyzer.getSystemPower();   // W (includes BoP)
```

| Method | Description |
|--------|-------------|
| `setRectifierEfficiency(double)` | Rectifier AC/DC efficiency (0,1] |
| `setAuxiliaryLoadFraction(double)` | Auxiliary load as a fraction of stack power (≥ 0) |
| `getSystemPower()` | System-level electrical power (W) |
| `getSystemSpecificEnergyConsumption_kWh_per_kg_H2()` | System specific energy (kWh/kg H₂) |

---

## Thermal Model and Water Consumption

Waste heat is generated by the overpotential above the thermoneutral voltage
($V_{tn} = 1.481$ V, HHV basis):

$$Q = \max(0,\; V_{cell} - V_{tn})\,I_{stack}$$

Water consumption is stoichiometric — one mole of water per mole of hydrogen:

```java
double wasteHeat = electrolyzer.getWasteHeat();              // W
double water_mol = electrolyzer.getWaterConsumption("mole/sec");
double water_kgs = electrolyzer.getWaterConsumption("kg/sec");
double water_kgh = electrolyzer.getWaterConsumption("kg/hr");
```

| Method | Description |
|--------|-------------|
| `getWasteHeat()` | Stack waste heat from the overpotential (W) |
| `getWaterConsumption(String unit)` | Stoichiometric water demand (`mole/sec`, `kg/sec`, `kg/hr`) |

---

## Hydrogen Delivery Pressure

Set a hydrogen delivery pressure to leave the product at elevated pressure; the
ideal isothermal compression power from the stack pressure is then available:

$$W = \dot{n}\,R\,T\,\ln\!\left(\frac{P_2}{P_1}\right)$$

```java
electrolyzer.setHydrogenDeliveryPressure(30.0);   // bara (0 keeps inlet pressure)
electrolyzer.run();

double pOut = electrolyzer.getHydrogenOutStream().getPressure("bara");  // 30 bara
double wComp = electrolyzer.getHydrogenCompressionPower();              // W (ideal)
```

| Method | Description |
|--------|-------------|
| `setHydrogenDeliveryPressure(double)` | Delivery pressure (bara); 0 keeps inlet pressure |
| `getHydrogenCompressionPower()` | Ideal isothermal compression power (W) |

---

## Energy Calculations

### Faradaic Efficiency

The fraction of electrical current that drives the desired reaction:

$$\eta_F = \frac{n \times F \times \dot{n}_{product}}{I}$$

Where $n$ = electrons per molecule, $F$ = Faraday constant (96485 C/mol),
$\dot{n}_{product}$ = molar production rate, $I$ = current.

### Specific Energy

Theoretical minimum: 39.4 kWh/kg H₂ (HHV basis). Typical actual consumption:

| Technology | Energy (kWh/kg H₂) |
|------------|-------------------|
| Alkaline | 50–55 |
| PEM | 50–60 |
| SOEC | 35–45 |

`getSpecificEnergyConsumption_kWh_per_kg_H2()` returns the stack value;
`getSystemSpecificEnergyConsumption_kWh_per_kg_H2()` adds balance-of-plant
losses.

---

## CO₂ Electrolyzer

The `CO2Electrolyzer` converts CO₂ to valuable products (CO, formic acid,
methanol, ethylene, syngas) through electrolysis. Refer to the class for the
full API (CO₂ conversion, product selectivity, faradaic efficiency per product).

```java
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;

CO2Electrolyzer co2Elec = new CO2Electrolyzer("CO2 electrolyzer", co2Stream);
co2Elec.run();

double power = co2Elec.getEnergyStream().getDuty();   // W
```

CO₂ electrolysis can produce various products depending on the catalyst:
carbon monoxide (CO), formic acid (HCOOH), methanol (CH₃OH), ethylene (C₂H₄),
and syngas (CO + H₂).

---

## Usage Examples

### Green hydrogen with variable renewable power

```java
import java.util.UUID;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.electrolyzer.ElectrolyzerTechnology;
import neqsim.process.equipment.electrolyzer.ElectrolyzerIVCharacteristic;

// 1 MW PEM stack with realistic balance-of-plant losses
Electrolyzer el = new Electrolyzer("H2 Electrolyzer", waterFeed);
el.setTechnology(ElectrolyzerTechnology.PEM);
el.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
el.sizeStack(1.0e6);
el.setRectifierEfficiency(0.95);
el.setAuxiliaryLoadFraction(0.05);
el.setMinimumLoadFraction(0.10);
el.setStandbyPowerFraction(0.02);
el.setMaxRampRate(0.1);
el.setCalculateSteadyState(false);

double[] powerProfileMW = loadRenewablePowerProfile();   // hourly MW
UUID id = UUID.randomUUID();
double totalH2 = 0.0;
for (int hour = 0; hour < powerProfileMW.length; hour++) {
  el.setAvailablePower(powerProfileMW[hour] * 1.0e6);
  el.runTransient(3600.0, id);
  if (!el.isStandby()) {
    totalH2 += el.getHydrogenOutStream().getFlowRate("kg/hr");   // kg over the hour
  }
}
System.out.println("Total H2: " + totalH2 + " kg");
```

A complete, executed example is available as a notebook:
[`examples/notebooks/green_hydrogen_variable_power.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/green_hydrogen_variable_power.ipynb).

### Green hydrogen with elevated delivery pressure

```java
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem process = new ProcessSystem();
process.add(waterFeed);

Electrolyzer electrolyzer = new Electrolyzer("H2 Electrolyzer", waterFeed);
electrolyzer.setTechnology(ElectrolyzerTechnology.PEM);
electrolyzer.setHydrogenDeliveryPressure(350.0);   // deliver at 350 bara
process.add(electrolyzer);

process.run();

double h2Output = electrolyzer.getHydrogenOutStream().getFlowRate("kg/hr");
double wComp = electrolyzer.getHydrogenCompressionPower();   // ideal compression duty
System.out.println("H2 output: " + h2Output + " kg/hr");
```

---

## Related Documentation

- [Compressors](compressors) - H₂ compression
- [Process Package](../) - Package overview
