---
title: Battery Storage
description: Documentation for battery storage equipment in NeqSim for energy storage and power management applications.
---

# Battery Storage

Documentation for battery storage equipment in NeqSim for energy storage and power management applications.

## Table of Contents
- [Overview](#overview)
- [BatteryStorage Class](#batterystorage-class)
- [Charging and Discharging](#charging-and-discharging)
- [Efficiency Modeling](#efficiency-modeling)
- [Usage Examples](#usage-examples)
- [Integration with Renewable Energy](#integration-with-renewable-energy)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.battery`

The battery package provides equipment for modeling energy storage systems. This is particularly useful for:

- Offshore platform power management
- Renewable energy integration (wind, solar)
- Peak shaving and load balancing
- Emergency backup power analysis
- Hybrid energy systems

| Class | Description |
|-------|-------------|
| `BatteryStorage` | Energy storage unit with charge/discharge cycles |

---

## BatteryStorage Class

The `BatteryStorage` class models a simple battery storage unit that maintains a state of charge and can be charged or discharged over time.

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── BatteryStorage
```

### Key Features

- **State of Charge Tracking**: Monitor stored energy in Joules
- **Charge/Discharge Efficiency**: Separate efficiencies for each operation
- **Capacity Limits**: Maximum storage capacity enforcement
- **Energy Stream Integration**: Power flow through energy stream
- **Time-Based Operations**: Charge/discharge with power and duration

### Constructor

```java
import neqsim.process.equipment.battery.BatteryStorage;

// Create battery with capacity in Joules
// 1 MWh = 3.6e9 J
double capacityMWh = 10.0;  // 10 MWh battery
double capacityJ = capacityMWh * 3.6e9;

BatteryStorage battery = new BatteryStorage("ESS-001", capacityJ);
```

### Key Properties

| Property | Method | Description | Unit |
|----------|--------|-------------|------|
| Capacity | `getCapacity()` / `setCapacity(double)` | Maximum energy storage | J |
| State of Charge | `getStateOfCharge()` / `setStateOfCharge(double)` | Current stored energy | J |
| Charge Efficiency | `setChargeEfficiency(double)` | Charging round-trip efficiency | 0-1 |
| Discharge Efficiency | `setDischargeEfficiency(double)` | Discharging efficiency | 0-1 |
| SOC Fraction | `getStateOfChargeFraction()` | SOC as fraction of capacity | 0-1 |

---

## Charging and Discharging

### Charging the Battery

```java
// Charge at 5 MW for 2 hours
double chargePowerW = 5.0e6;  // 5 MW
double hoursCharging = 2.0;

battery.charge(chargePowerW, hoursCharging);

// Check new state of charge
double socJ = battery.getStateOfCharge();
double socFraction = battery.getStateOfChargeFraction();
System.out.println("SOC: " + (socFraction * 100) + "%");
```

### Discharging the Battery

```java
// Discharge at 3 MW for 1 hour
double dischargePowerW = 3.0e6;  // 3 MW
double hoursDischarging = 1.0;

// Returns actual delivered power (may be less if battery depleted)
double actualPower = battery.discharge(dischargePowerW, hoursDischarging);

if (actualPower < dischargePowerW) {
    System.out.println("Battery depleted, delivered: " + actualPower/1e6 + " MW");
}
```

### Energy Balance

The charge and discharge operations account for efficiency losses:

**Charging:**
$$E_{stored} = P_{in} \times t \times \eta_{charge}$$

**Discharging:**
$$E_{delivered} = P_{out} \times t$$
$$E_{consumed} = \frac{P_{out} \times t}{\eta_{discharge}}$$

Where:
- $P_{in}$ = Input power during charging (W)
- $P_{out}$ = Output power during discharging (W)
- $t$ = Duration (hours)
- $\eta_{charge}$ = Charge efficiency (typically 0.90-0.95)
- $\eta_{discharge}$ = Discharge efficiency (typically 0.90-0.95)

---

## Efficiency Modeling

### Round-Trip Efficiency

The total round-trip efficiency is:

$$\eta_{RT} = \eta_{charge} \times \eta_{discharge}$$

For typical Li-ion batteries: $\eta_{RT} \approx 0.85-0.92$

### Setting Custom Efficiencies

```java
BatteryStorage battery = new BatteryStorage("ESS-001", 36.0e9);  // 10 MWh

// Li-ion battery typical efficiencies
battery.setChargeEfficiency(0.95);
battery.setDischargeEfficiency(0.95);
// Round-trip: 0.95 * 0.95 = 0.9025 (90.25%)

// Lead-acid battery
battery.setChargeEfficiency(0.85);
battery.setDischargeEfficiency(0.85);
// Round-trip: 0.85 * 0.85 = 0.7225 (72.25%)
```

---

## Usage Examples

### Example 1: Basic Charge/Discharge Cycle

```java
import neqsim.process.equipment.battery.BatteryStorage;

// Create 5 MWh battery
double capacityJ = 5.0 * 3.6e9;  // 5 MWh in Joules
BatteryStorage battery = new BatteryStorage("Platform Battery", capacityJ);

// Set efficiencies
battery.setChargeEfficiency(0.92);
battery.setDischargeEfficiency(0.92);

// Start with 50% charge
battery.setStateOfCharge(capacityJ * 0.5);
System.out.println("Initial SOC: " + (battery.getStateOfChargeFraction() * 100) + "%");

// Charge at 2 MW for 1 hour
battery.charge(2.0e6, 1.0);
System.out.println("After charging: " + (battery.getStateOfChargeFraction() * 100) + "%");

// Discharge at 1 MW for 2 hours
battery.discharge(1.0e6, 2.0);
System.out.println("After discharging: " + (battery.getStateOfChargeFraction() * 100) + "%");

// Run to update energy stream
battery.run();
```

### Example 2: Wind-Battery Hybrid System

```java
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.powergeneration.WindTurbine;
import neqsim.process.processmodel.ProcessSystem;

// Wind turbine
WindTurbine wind = new WindTurbine("Offshore Wind");
wind.setRotorArea(12000.0);  // 12000 m² (~124m diameter)
wind.setPowerCoefficient(0.45);
wind.setWindSpeed(12.0);  // m/s

// Battery storage (20 MWh)
BatteryStorage battery = new BatteryStorage("Grid Battery", 20.0 * 3.6e9);

// Simulate hourly operation
double[] windSpeeds = {8.0, 10.0, 15.0, 12.0, 6.0, 4.0};  // m/s
double platformDemandMW = 5.0;

for (int hour = 0; hour < windSpeeds.length; hour++) {
    wind.setWindSpeed(windSpeeds[hour]);
    wind.run();
    
    double windPowerMW = wind.getPower() / 1e6;
    double surplus = windPowerMW - platformDemandMW;
    
    if (surplus > 0) {
        // Excess power - charge battery
        battery.charge(surplus * 1e6, 1.0);
        System.out.println("Hour " + hour + ": Charging at " + surplus + " MW");
    } else {
        // Deficit - discharge battery
        double needed = -surplus * 1e6;
        double delivered = battery.discharge(needed, 1.0);
        System.out.println("Hour " + hour + ": Discharging " + delivered/1e6 + " MW");
    }
    
    System.out.println("  SOC: " + (battery.getStateOfChargeFraction() * 100) + "%");
}
```

### Example 3: Peak Shaving Application

```java
// Platform with variable load
double[] hourlyDemandMW = {3.0, 3.5, 5.0, 8.0, 10.0, 12.0, 8.0, 5.0};
double generatorCapacityMW = 8.0;  // Base load generators

BatteryStorage battery = new BatteryStorage("Peak Shaver", 15.0 * 3.6e9);
battery.setStateOfCharge(battery.getCapacity() * 0.8);  // Start at 80%

for (int hour = 0; hour < hourlyDemandMW.length; hour++) {
    double demand = hourlyDemandMW[hour];
    
    if (demand > generatorCapacityMW) {
        // Peak demand - use battery
        double peakMW = demand - generatorCapacityMW;
        double delivered = battery.discharge(peakMW * 1e6, 1.0);
        System.out.println("Hour " + hour + ": Peak shaving " + delivered/1e6 + " MW");
    } else {
        // Low demand - charge battery
        double spareMW = generatorCapacityMW - demand;
        if (spareMW > 1.0) {  // Minimum charge rate
            battery.charge(spareMW * 1e6 * 0.5, 1.0);  // Use 50% of spare
            System.out.println("Hour " + hour + ": Charging at " + spareMW * 0.5 + " MW");
        }
    }
}
```

---

## Integration with Renewable Energy

### Combining with Solar Panels

```java
import neqsim.process.equipment.powergeneration.SolarPanel;
import neqsim.process.equipment.battery.BatteryStorage;

// Solar array
SolarPanel solar = new SolarPanel("Solar Array");
solar.setPanelArea(5000.0);  // 5000 m²
solar.setEfficiency(0.22);   // 22% efficiency

// Battery for overnight storage
BatteryStorage battery = new BatteryStorage("Solar Battery", 30.0 * 3.6e9);

// Hourly irradiance profile (W/m²)
double[] irradiance = {0, 0, 100, 300, 500, 700, 800, 700, 500, 300, 100, 0};

double dailyGeneration = 0;
double dailyStored = 0;

for (int hour = 0; hour < irradiance.length; hour++) {
    solar.setIrradiance(irradiance[hour]);
    solar.run();
    
    double powerMW = solar.getPower() / 1e6;
    dailyGeneration += powerMW;
    
    if (powerMW > 0) {
        // Store excess in battery
        battery.charge(powerMW * 1e6 * 0.7, 1.0);  // Store 70%
        dailyStored += powerMW * 0.7;
    }
}

System.out.println("Daily generation: " + dailyGeneration + " MWh");
System.out.println("Daily stored: " + dailyStored + " MWh");
System.out.println("Battery SOC: " + (battery.getStateOfChargeFraction() * 100) + "%");
```

---

## Unit Conversions

| Unit | To Joules |
|------|-----------|
| 1 kWh | 3.6e6 J |
| 1 MWh | 3.6e9 J |
| 1 GWh | 3.6e12 J |

| Unit | To Watts |
|------|----------|
| 1 kW | 1e3 W |
| 1 MW | 1e6 W |
| 1 GW | 1e9 W |

---

## Related Documentation

- [Power Generation Equipment](power_generation) - Wind, solar, gas turbines
- [Process System](../processmodel/) - System integration
- [Cost Estimation](../COST_ESTIMATION_FRAMEWORK) - Equipment costs

---

## API Reference

### BatteryStorage

```java
// Constructors
BatteryStorage()
BatteryStorage(String name)
BatteryStorage(String name, double capacity)

// Capacity
double getCapacity()
void setCapacity(double capacity)

// State of Charge
double getStateOfCharge()
void setStateOfCharge(double soc)
double getStateOfChargeFraction()

// Operations
void charge(double power, double hours)
double discharge(double power, double hours)

// Efficiency
void setChargeEfficiency(double efficiency)
void setDischargeEfficiency(double efficiency)

// Run simulation
void run()
void run(UUID id)
```
