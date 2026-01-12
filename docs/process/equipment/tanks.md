# Storage Tanks

Documentation for liquid storage tanks in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Tank Class](#tank-class)
- [Dynamic Operation](#dynamic-operation)
- [Boil-off Gas](#boil-off-gas)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.tank`

**Classes:**
| Class | Description |
|-------|-------------|
| `Tank` | Basic storage tank |
| `LNGTank` | LNG storage tank with boil-off |

---

## Tank Class

### Basic Usage

```java
import neqsim.process.equipment.tank.Tank;

Tank tank = new Tank("T-100", liquidStream);
tank.setVolume(1000.0, "m3");
tank.setLiquidLevel(0.5);  // 50% full
tank.run();
```

### Operating Pressure

```java
// Atmospheric tank
tank.setPressure(1.013, "bara");

// Pressurized storage
tank.setPressure(5.0, "bara");
```

---

## Dynamic Operation

### Fill and Drain

```java
// Initial conditions
tank.setLiquidLevel(0.3);  // 30% full
tank.run();

// Simulate filling
for (double t = 0; t < 3600; t += 60) {
    tank.setInletStream(inletStream);
    tank.runTransient();
    
    double level = tank.getLiquidLevel();
    System.out.println("Time: " + t + " s, Level: " + level * 100 + " %");
}
```

### Level Calculation

$$\frac{dV_{liq}}{dt} = \dot{Q}_{in} - \dot{Q}_{out}$$

$$L = \frac{V_{liq}}{V_{tank}}$$

---

## Boil-off Gas

For cryogenic storage (LNG, LPG).

### LNG Tank

```java
import neqsim.process.equipment.tank.LNGTank;

LNGTank lngTank = new LNGTank("LNG Storage", lngStream);
lngTank.setVolume(160000.0, "m3");
lngTank.setHeatInput(500.0, "kW");  // Heat leak
lngTank.run();

// Boil-off rate
double bogRate = lngTank.getBoilOffGasRate("kg/hr");
Stream bog = lngTank.getBoilOffGasStream();
```

### Boil-off Calculation

$$\dot{m}_{BOG} = \frac{\dot{Q}_{heat}}{\Delta H_{vap}}$$

Where:
- $\dot{Q}_{heat}$ = heat leak rate
- $\Delta H_{vap}$ = latent heat of vaporization

---

## Examples

### Example 1: Simple Storage Tank

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;

// Crude oil
SystemSrkEos oil = new SystemSrkEos(298.15, 1.013);
oil.addComponent("n-heptane", 0.50);
oil.addComponent("n-decane", 0.50);
oil.setMixingRule("classic");

Stream oilStream = new Stream("Crude", oil);
oilStream.setFlowRate(100.0, "m3/hr");
oilStream.run();

// Storage tank
Tank storage = new Tank("Crude Storage", oilStream);
storage.setVolume(10000.0, "m3");
storage.setLiquidLevel(0.6);
storage.run();

double inventory = storage.getLiquidVolume("m3");
System.out.println("Inventory: " + inventory + " m³");
```

### Example 2: LNG Storage with Boil-off

```java
// LNG composition
SystemSrkEos lng = new SystemSrkEos(112.0, 1.013);  // -161°C
lng.addComponent("nitrogen", 0.01);
lng.addComponent("methane", 0.92);
lng.addComponent("ethane", 0.05);
lng.addComponent("propane", 0.02);
lng.setMixingRule("classic");

Stream lngIn = new Stream("LNG In", lng);
lngIn.setFlowRate(1000.0, "m3/hr");
lngIn.run();

// LNG tank
LNGTank tank = new LNGTank("LNG Tank", lngIn);
tank.setVolume(160000.0, "m3");  // 160,000 m³ tank
tank.setHeatInput(300.0, "kW");  // Heat leak
tank.run();

// Results
System.out.println("BOG rate: " + tank.getBoilOffGasRate("kg/hr") + " kg/hr");
System.out.println("BOG temp: " + tank.getBoilOffGasStream().getTemperature("C") + " °C");
System.out.println("BOG rate %: " + tank.getBoilOffRate() * 100 + " %/day");
```

### Example 3: Tank with Level Control

```java
ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Feed", oilFluid);
feed.setFlowRate(100.0, "m3/hr");
process.add(feed);

// Storage tank
Tank tank = new Tank("T-100", feed);
tank.setVolume(5000.0, "m3");
tank.setLiquidLevel(0.5);
process.add(tank);

// Outlet with level control
ThrottlingValve outlet = new ThrottlingValve("LV-100", tank.getOutletStream());
outlet.setOutletPressure(1.0, "bara");
process.add(outlet);

// Level controller
PIDController lc = new PIDController("LC-100");
lc.setMeasuredVariable(tank, "liquidLevel");
lc.setControlledVariable(outlet, "opening");
lc.setSetPoint(0.5);
lc.setKp(5.0);
lc.setKi(0.1);
process.add(lc);

// Run transient
for (double t = 0; t < 7200; t += 60) {
    // Disturb inlet at t=1800
    if (Math.abs(t - 1800) < 30) {
        feed.setFlowRate(150.0, "m3/hr");
    }
    
    process.runTransient();
    System.out.printf("%.0f, %.3f, %.1f%n", 
        t, tank.getLiquidLevel(), outlet.getOpening() * 100);
}
```

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Separators](separators.md) - Phase separation
- [Controllers](../controllers.md) - Level control
