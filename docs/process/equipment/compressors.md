---
title: Compressor Equipment
description: Documentation for compression equipment in NeqSim process simulation.
---

# Compressor Equipment

Documentation for compression equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Compressor Types](#compressor-types)
- [Calculation Methods](#calculation-methods)
- [Performance Curves](#performance-curves)
- [Surge and Stone Wall](#surge-and-stone-wall)
- [Compressor Driver](#compressor-driver) ⭐ NEW
- [Mechanical Losses and Seal Gas](#mechanical-losses-and-seal-gas)
- [Usage Examples](#usage-examples)

> **📖 Detailed Curve Documentation:** For comprehensive information on compressor curves, 
> including multi-speed vs single-speed handling, surge curves, and stone wall curves, 
> see [Compressor Curves and Performance Maps](compressor_curves).

---

## Overview

**Location:** `neqsim.process.equipment.compressor`

**Classes:**
- `Compressor` - General compressor
- `CompressorInterface` - Compressor interface
- `CompressorChartInterface` - Performance map interface

---

## Basic Usage

```java
import neqsim.process.equipment.compressor.Compressor;

Compressor compressor = new Compressor("K-100", inletStream);
compressor.setOutletPressure(80.0, "bara");
compressor.setIsentropicEfficiency(0.75);
compressor.run();

// Results
double power = compressor.getPower("kW");
double outletT = compressor.getOutletStream().getTemperature("C");
double polytropicHead = compressor.getPolytropicHead("kJ/kg");

System.out.println("Power: " + power + " kW");
System.out.println("Outlet temperature: " + outletT + " °C");
```

---

## Calculation Methods

### Isentropic Compression

```java
compressor.setIsentropicEfficiency(0.75);  // 75%
compressor.setUsePolytropicCalc(false);
compressor.run();

double isentropicHead = compressor.getIsentropicHead("kJ/kg");
double isentropicPower = compressor.getPower("kW");
```

### Polytropic Compression

More accurate for real gas behavior.

```java
compressor.setPolytropicEfficiency(0.80);  // 80%
compressor.setUsePolytropicCalc(true);
compressor.run();

double polytropicHead = compressor.getPolytropicHead("kJ/kg");
double polytropicExponent = compressor.getPolytropicExponent();
```

### Power Specified

```java
compressor.setPower(5000.0, "kW");  // Specify power
compressor.setIsentropicEfficiency(0.75);
compressor.run();

double outletP = compressor.getOutletStream().getPressure("bara");
```

---

## Efficiency Relationships

### Isentropic Efficiency

$$\eta_{is} = \frac{H_{is}}{H_{actual}} = \frac{T_{2s} - T_1}{T_2 - T_1}$$

### Polytropic Efficiency

$$\eta_p = \frac{n-1}{n} \cdot \frac{k}{k-1}$$

Where:
- $n$ = polytropic exponent
- $k$ = isentropic exponent (Cp/Cv)

---

## Performance Curves

NeqSim supports detailed compressor performance maps with multiple speed curves. For comprehensive documentation, see [Compressor Curves and Performance Maps](compressor_curves).

### Setting Compressor Map

```java
// Define speed curves
double[] speeds = {8000, 9000, 10000, 11000};  // RPM

// For each speed: arrays of flow, head, efficiency
double[][] flows = { {flow1_curve1, flow2_curve1}, {flow1_curve2, flow2_curve2}, ... };
double[][] heads = { {head1_curve1, head2_curve1}, {head1_curve2, head2_curve2}, ... };
double[][] efficiencies = { {eff1_curve1, eff2_curve1}, {eff1_curve2, eff2_curve2}, ... };

CompressorChartInterface chart = compressor.getCompressorChart();
chart.setCurves(chartConditions, speeds, flows, heads, flows, efficiencies);
chart.setHeadUnit("kJ/kg");
compressor.setSpeed(10000);  // Operating speed
```

### Operating Point

```java
compressor.setSpeed(10000);  // RPM
compressor.run();

double actualFlow = compressor.getActualFlow("m3/hr");
double head = compressor.getPolytropicHead("kJ/kg");
double efficiency = compressor.getPolytropicEfficiency();
```

---

## Surge and Stone Wall

### Multi-Speed vs Single-Speed Compressors

| Compressor Type | Surge/Stone Wall | Setting Method |
|-----------------|------------------|----------------|
| Multi-speed (≥2 speeds) | Curves (interpolated) | Multiple flow/head points |
| Single-speed (1 speed) | Single points (constant) | Single flow/head point |

### Checking Operating Limits

```java
// Distance to surge (positive = above surge, safe)
double distanceToSurge = compressor.getDistanceToSurge();

// Distance to stone wall (positive = below choke, safe)
double distanceToStoneWall = compressor.getDistanceToStoneWall();

// Check if in surge
boolean isSurge = compressor.getCompressorChart().getSurgeCurve().isSurge(head, flow);

// Get surge flow rate
double surgeFlow = compressor.getSurgeFlowRate();
```

### Single-Speed Compressor Example

```java
// For single-speed compressors, surge is a single point
double[] surgeFlow = {5607.45};  // Single value
double[] surgeHead = {150.0};   // Single value
compressor.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);

// Stone wall is also a single point
double[] stoneWallFlow = {9758.49};
double[] stoneWallHead = {112.65};
compressor.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallFlow, stoneWallHead);
```

> **📖 See Also:** [Compressor Curves and Performance Maps](compressor_curves) for detailed
> documentation on curve setup, interpolation methods, and Python examples.

---

## Compressor Staging

### Multi-Stage with Intercooling

```java
ProcessSystem process = new ProcessSystem();

// Stage 1: 20 -> 50 bar
Compressor stage1 = new Compressor("K-100A", inletStream);
stage1.setOutletPressure(50.0, "bara");
stage1.setPolytropicEfficiency(0.78);
process.add(stage1);

// Intercooler
Cooler intercooler = new Cooler("E-100", stage1.getOutletStream());
intercooler.setOutTemperature(40.0, "C");
process.add(intercooler);

// Stage 2: 50 -> 120 bar
Compressor stage2 = new Compressor("K-100B", intercooler.getOutletStream());
stage2.setOutletPressure(120.0, "bara");
stage2.setPolytropicEfficiency(0.78);
process.add(stage2);

// Aftercooler
Cooler aftercooler = new Cooler("E-101", stage2.getOutletStream());
aftercooler.setOutTemperature(40.0, "C");
process.add(aftercooler);

process.run();

// Total power
double totalPower = stage1.getPower("kW") + stage2.getPower("kW");
System.out.println("Total compression power: " + totalPower + " kW");
```

### Optimal Pressure Ratio

For minimum work with equal stage ratios:

$$r_{stage} = \left(\frac{P_{out}}{P_{in}}\right)^{1/n}$$

```java
double overallRatio = 120.0 / 20.0;  // 6:1
int numStages = 2;
double stageRatio = Math.pow(overallRatio, 1.0 / numStages);  // √6 ≈ 2.45
```

---

## Antisurge Control

```java
// Recycle valve for antisurge
Splitter recycle = new Splitter("Antisurge Recycle", stage2.getOutletStream());
recycle.setSplitFactors(new double[]{0.9, 0.1});  // 10% recycle

// Mix with inlet
Mixer mixer = new Mixer("Inlet Mixer");
mixer.addStream(inletStream);
mixer.addStream(recycle.getSplitStream(1));
```

---

## Power Calculation Details

### Real Gas Effects

```java
// Account for compressibility
double Z1 = compressor.getInletStream().getZ();
double Z2 = compressor.getOutletStream().getZ();
double Zavg = (Z1 + Z2) / 2;
```

### Mechanical Efficiency

```java
// Include mechanical losses
compressor.setMechanicalEfficiency(0.98);
double shaftPower = compressor.getShaftPower("kW");
double gasHorsepower = compressor.getGasHorsepower("hp");
```

---

## Example: Export Gas Compression

```java
// Export gas at 100 MSm³/day
SystemInterface gas = new SystemSrkEos(288.15, 30.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.04);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("nitrogen", 0.01);
gas.setMixingRule("classic");

Stream inlet = new Stream("Inlet Gas", gas);
inlet.setFlowRate(100.0, "MSm3/day");
inlet.setTemperature(30.0, "C");
inlet.setPressure(30.0, "bara");

ProcessSystem process = new ProcessSystem();
process.add(inlet);

// 3-stage compression to 200 bar
double[] stagePressures = {55, 105, 200};

Stream currentStream = inlet;
for (int i = 0; i < 3; i++) {
    Compressor comp = new Compressor("K-10" + (i+1), currentStream);
    comp.setOutletPressure(stagePressures[i], "bara");
    comp.setPolytropicEfficiency(0.78);
    comp.setUsePolytropicCalc(true);
    process.add(comp);
    
    Cooler cooler = new Cooler("E-10" + (i+1), comp.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    process.add(cooler);
    
    currentStream = cooler.getOutletStream();
}

process.run();

// Report
System.out.println("\n=== Compression Summary ===");
double totalPower = 0;
for (int i = 1; i <= 3; i++) {
    Compressor c = (Compressor) process.getUnit("K-10" + i);
    totalPower += c.getPower("MW");
    System.out.printf("Stage %d: %.2f bar -> %.2f bar, %.2f MW%n",
        i, c.getInletStream().getPressure("bara"),
        c.getOutletStream().getPressure("bara"),
        c.getPower("MW"));
}
System.out.println("Total power: " + totalPower + " MW");
```

---

## Automatic Curve Generation

When manufacturer performance data is not available, NeqSim can automatically generate realistic compressor curves using predefined templates.

### Quick Start

```java
// 1. Create and run compressor to establish design point
Compressor comp = new Compressor("K-100", inletStream);
comp.setOutletPressure(100.0, "bara");
comp.setPolytropicEfficiency(0.78);
comp.setSpeed(10000);
comp.run();

// 2. Generate curves from template
CompressorChartGenerator generator = new CompressorChartGenerator(comp);
CompressorChartInterface chart = generator.generateFromTemplate("PIPELINE", 5);

// 3. Apply and use
comp.setCompressorChart(chart);
comp.run();
```

### Available Templates

| Category | Templates |
|----------|-----------|
| **Basic** | `CENTRIFUGAL_STANDARD`, `CENTRIFUGAL_HIGH_FLOW`, `CENTRIFUGAL_HIGH_HEAD` |
| **Application** | `PIPELINE`, `EXPORT`, `INJECTION`, `GAS_LIFT`, `REFRIGERATION`, `BOOSTER` |
| **Type** | `SINGLE_STAGE`, `MULTISTAGE_INLINE`, `INTEGRALLY_GEARED`, `OVERHUNG` |

### Template Selection

| Use Case | Recommended Template |
|----------|---------------------|
| Gas transmission | `PIPELINE` |
| Offshore export | `EXPORT` |
| Gas injection/EOR | `INJECTION` |
| Artificial lift | `GAS_LIFT` |
| LNG/refrigeration | `REFRIGERATION` |
| General purpose | `CENTRIFUGAL_STANDARD` |

> **📖 Detailed Documentation:** See [Compressor Curves - Automatic Generation](compressor_curves#automatic-curve-generation) 
> for complete API reference, advanced corrections, and examples.

---

## Compressor Driver

NeqSim supports detailed modeling of compressor drivers including electric motors, gas turbines, and variable frequency drives (VFDs). The driver model includes power limits, efficiency curves, and **speed-dependent maximum power**.

### Overview

**Location:** `neqsim.process.equipment.compressor.CompressorDriver`

**Driver Types:**
- `ELECTRIC_MOTOR` - Fixed speed electric motor
- `VFD_MOTOR` - Variable frequency drive motor
- `GAS_TURBINE` - Gas turbine (with temperature derating)
- `STEAM_TURBINE` - Steam turbine driver
- `RECIPROCATING_ENGINE` - Reciprocating gas/diesel engine

### Basic Usage

```java
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;

// Create driver with type and rated power
CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 5000.0);
driver.setRatedSpeed(5000.0);  // RPM
driver.setMaxSpeed(6000.0);    // RPM
driver.setMinSpeed(1000.0);    // RPM

// Attach to compressor
compressor.setDriver(driver);
```

### Speed-Dependent Maximum Power

A key feature is the ability to specify max power as a function of compressor speed. This is essential for accurate modeling of:

- **VFD motors** where torque is constant and power varies linearly with speed
- **Gas turbines** where power output depends on shaft speed
- **Reciprocating engines** with speed-dependent power limits

#### Power Curve Formula

The max power at a given speed is calculated as:

$$P_{max}(N) = P_{max,rated} \times \left( a + b \cdot \frac{N}{N_{rated}} + c \cdot \left(\frac{N}{N_{rated}}\right)^2 \right)$$

Where:
- $P_{max,rated}$ = Maximum power at rated conditions (kW)
- $N$ = Current speed (RPM)
- $N_{rated}$ = Rated speed (RPM)
- $a, b, c$ = Polynomial coefficients

#### Setting the Power Curve

```java
// Set max power curve coefficients: a, b, c
driver.setMaxPowerCurveCoefficients(a, b, c);

// Get max power at a specific speed
double maxPowerAtSpeed = driver.getMaxAvailablePowerAtSpeed(3000.0);

// Check if power can be delivered at speed
boolean canDeliver = driver.canDeliverPowerAtSpeed(4000.0, 3500.0);

// Get power margin at speed
double margin = driver.getPowerMarginAtSpeed(currentPower, speed);
```

#### Common Curve Coefficients

| Curve Type | a | b | c | Description |
|------------|---|---|---|-------------|
| Constant | 1.0 | 0.0 | 0.0 | Max power same at all speeds (default) |
| Linear (VFD motor) | 0.0 | 1.0 | 0.0 | Power proportional to speed |
| With base offset | 0.5 | 0.5 | 0.0 | 50% at zero speed, 100% at rated |
| Torque limited | 0.2 | 0.6 | 0.2 | Typical motor torque curve |

#### Example: VFD Motor with Linear Power Curve

```java
// VFD motor: constant torque, so power is proportional to speed
CompressorDriver vfdDriver = new CompressorDriver(DriverType.VFD_MOTOR, 5000.0);
vfdDriver.setRatedSpeed(5000.0);
vfdDriver.setMaxPower(5500.0);  // 110% overload capacity

// Linear power curve: P_max = maxPower × (N / N_rated)
vfdDriver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

// At rated speed (5000 RPM): max power = 5500 kW
double maxAtRated = vfdDriver.getMaxAvailablePowerAtSpeed(5000.0);  // 5500 kW

// At half speed (2500 RPM): max power = 2750 kW
double maxAtHalf = vfdDriver.getMaxAvailablePowerAtSpeed(2500.0);   // 2750 kW

// At 120% speed (6000 RPM): max power = 6600 kW
double maxAt120Pct = vfdDriver.getMaxAvailablePowerAtSpeed(6000.0); // 6600 kW
```

#### Example: Gas Turbine with Temperature Derating

```java
// Gas turbine with power curve AND temperature derating
CompressorDriver gtDriver = new CompressorDriver(DriverType.GAS_TURBINE, 10000.0);
gtDriver.setRatedSpeed(10000.0);
gtDriver.setMaxPower(11000.0);

// Set power curve (slight speed dependency)
gtDriver.setMaxPowerCurveCoefficients(0.1, 0.9, 0.0);

// Temperature derating: 0.5% power loss per K above ISO (15°C)
gtDriver.setTemperatureDerateFactor(0.005);

// Hot day at 30°C
gtDriver.setAmbientTemperature(303.15);  // K

// Combined effect: power curve × temperature derating
double maxPower = gtDriver.getMaxAvailablePowerAtSpeed(10000.0);
// = 11000 × 1.0 × (1 - 15×0.005) = 11000 × 0.925 = 10175 kW
```

#### Managing the Power Curve

```java
// Check if curve is enabled
boolean enabled = driver.isMaxPowerCurveEnabled();

// Temporarily disable curve (use constant max power)
driver.disableMaxPowerCurve();
double constantMax = driver.getMaxAvailablePowerAtSpeed(3000.0);  // Same as rated

// Re-enable curve
driver.enableMaxPowerCurve();

// Get current coefficients
double[] coeffs = driver.getMaxPowerCurveCoefficients();  // [a, b, c]
```

### Driver Power Checks During Operation

```java
// Check power limits during compressor operation
compressor.setDriver(driver);
compressor.run();

double requiredPower = compressor.getPower("kW");
double currentSpeed = compressor.getSpeed();

// Check if driver can deliver required power at current speed
if (driver.canDeliverPowerAtSpeed(requiredPower, currentSpeed)) {
    System.out.println("Operating within driver limits");
} else {
    double margin = driver.getPowerMarginAtSpeed(requiredPower, currentSpeed);
    System.out.println("Driver overloaded by " + (-margin) + " kW");
}
```

### VFD Efficiency Curve

For VFD motors, efficiency also varies with speed:

```java
// Set VFD efficiency curve: η = a + b×(N/N_rated) + c×(N/N_rated)²
driver.setVfdEfficiencyCoefficients(0.90, 0.05, -0.02);

// Get efficiency at current speed
double efficiency = driver.getEfficiencyAtSpeed(4000.0);
```

### Complete Example: Power-Limited Compressor

```java
// Create gas and inlet stream
SystemInterface gas = new SystemSrkEos(288.0, 30.0);
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.05);
gas.setMixingRule("classic");

Stream inlet = new Stream("inlet", gas);
inlet.setFlowRate(50000.0, "kg/hr");
inlet.run();

// Create compressor
Compressor comp = new Compressor("Export Compressor", inlet);
comp.setOutletPressure(100.0, "bara");
comp.setPolytropicEfficiency(0.78);
comp.setSpeed(8000);

// Create VFD driver with speed-dependent power limit
CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 8000.0);
driver.setRatedSpeed(10000.0);
driver.setMaxPower(8800.0);  // 110% overload
driver.setMinSpeed(3000.0);
driver.setMaxSpeed(11000.0);

// Linear power curve (constant torque)
driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

comp.setDriver(driver);
comp.run();

// Report
System.out.println("=== Compressor Operation ===");
System.out.println("Speed: " + comp.getSpeed() + " RPM");
System.out.println("Power required: " + comp.getPower("kW") + " kW");
System.out.println();
System.out.println("=== Driver Limits ===");
double maxPowerAtSpeed = driver.getMaxAvailablePowerAtSpeed(comp.getSpeed());
System.out.println("Max power at speed: " + maxPowerAtSpeed + " kW");
System.out.println("Power margin: " + driver.getPowerMarginAtSpeed(comp.getPower("kW"), comp.getSpeed()) + " kW");
System.out.println("Can deliver: " + driver.canDeliverPowerAtSpeed(comp.getPower("kW"), comp.getSpeed()));
```

### Python Example

```python
from neqsim.process.equipment.compressor import CompressorDriver, DriverType

# Create VFD driver
driver = CompressorDriver(DriverType.VFD_MOTOR, 5000.0)  # 5000 kW rated
driver.setRatedSpeed(5000.0)
driver.setMaxPower(5500.0)

# Set linear power curve
driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0)

# Check power at different speeds
for speed in [2500, 3750, 5000, 6000]:
    max_power = driver.getMaxAvailablePowerAtSpeed(speed)
    print(f"Speed {speed} RPM: Max power = {max_power:.0f} kW")
```

---

## Mechanical Losses and Seal Gas

NeqSim supports modeling of compressor mechanical losses and seal gas consumption per **API 617** (bearings) and **API 692** (dry gas seals).

### Overview

The `CompressorMechanicalLosses` class models:

| Component | Standard | Description |
|-----------|----------|-------------|
| **Dry Gas Seals** | API 692 | Primary/secondary leakage, buffer gas, separation gas |
| **Bearings** | API 617 | Radial and thrust bearing friction losses |
| **Lube Oil System** | API 614 | Oil flow requirements and cooler duty |

### Quick Start

```java
// Create and run compressor
Compressor compressor = new Compressor("K-100", inletStream);
compressor.setOutletPressure(100.0, "bara");
compressor.setSpeed(10000);
compressor.run();

// Initialize mechanical losses with shaft diameter (mm)
compressor.initMechanicalLosses(120.0);

// Get results
double sealGas = compressor.getSealGasConsumption();  // Nm³/hr total
double bearingLoss = compressor.getBearingLoss();     // kW
double mechEfficiency = compressor.getMechanicalEfficiency();  // 0-1

System.out.println("Seal gas consumption: " + sealGas + " Nm³/hr");
System.out.println("Bearing power loss: " + bearingLoss + " kW");
System.out.println("Mechanical efficiency: " + (mechEfficiency * 100) + "%");
```

### Seal Types

```java
CompressorMechanicalLosses losses = compressor.getMechanicalLosses();

// Available seal types
losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);  // Most common
losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_DOUBLE);  // Lower leakage
losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_SINGLE);  // Higher leakage
losses.setSealType(CompressorMechanicalLosses.SealType.LABYRINTH);       // Non-contacting
losses.setSealType(CompressorMechanicalLosses.SealType.OIL_FILM);        // Legacy
```

### Seal Gas Flows (API 692)

| Flow Type | Description | Typical Range |
|-----------|-------------|---------------|
| **Primary Leakage** | Gas escaping past primary seal | 0.5-5 Nm³/hr per seal |
| **Secondary Leakage** | Gas past secondary seal (tandem) | 10-30% of primary |
| **Buffer Gas** | Clean gas between seals | 2-10 Nm³/hr per seal |
| **Separation Gas** | N₂ to prevent oil mist ingress | 1-5 Nm³/hr per seal |

```java
// Individual seal gas flows
double primaryLeak = losses.calculatePrimarySealLeakage();    // Nm³/hr
double secondaryLeak = losses.calculateSecondarySealLeakage(); // Nm³/hr  
double bufferGas = losses.calculateBufferGasFlow();           // Nm³/hr
double separationGas = losses.calculateSeparationGasFlow();   // Nm³/hr

// Total consumption
double total = losses.getTotalSealGasConsumption();  // Nm³/hr
```

### Bearing Types

```java
// Available bearing types
losses.setBearingType(CompressorMechanicalLosses.BearingType.TILTING_PAD);    // Standard
losses.setBearingType(CompressorMechanicalLosses.BearingType.PLAIN_SLEEVE);   // Higher loss
losses.setBearingType(CompressorMechanicalLosses.BearingType.MAGNETIC_ACTIVE); // Very low loss
losses.setBearingType(CompressorMechanicalLosses.BearingType.GAS_FOIL);       // Oil-free
```

### Bearing and Lube Oil Calculations (API 617)

```java
// Bearing losses
double radialLoss = losses.calculateRadialBearingLoss();  // kW
double thrustLoss = losses.calculateThrustBearingLoss();  // kW
double totalBearingLoss = losses.getTotalBearingLoss();   // kW

// Lube oil system
losses.setLubeOilInletTemp(40.0);   // °C
losses.setLubeOilOutletTemp(55.0);  // °C

double oilFlow = losses.calculateLubeOilFlowRate();       // L/min
double coolerDuty = losses.calculateLubeOilCoolerDuty();  // kW
```

### Configuration Options

```java
CompressorMechanicalLosses losses = compressor.initMechanicalLosses(100.0);

// Seal configuration
losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);
losses.setNumberOfSeals(2);  // Typically 2 for single-shaft
losses.setSealGasSupplyPressure(105.0);  // bara
losses.setSealGasSupplyTemperature(40.0);  // °C

// Bearing configuration  
losses.setBearingType(CompressorMechanicalLosses.BearingType.TILTING_PAD);
losses.setNumberOfRadialBearings(2);

// Compressor updates operating conditions automatically
compressor.updateMechanicalLosses();
```

### Complete Example

```java
// Natural gas compressor with mechanical losses
SystemInterface gas = new SystemSrkEos(298.0, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

Stream inlet = new Stream("inlet", gas);
inlet.setFlowRate(5000.0, "kg/hr");
inlet.run();

// Create and configure compressor
Compressor comp = new Compressor("HP Compressor", inlet);
comp.setOutletPressure(150.0, "bara");
comp.setPolytropicEfficiency(0.78);
comp.setSpeed(12000);
comp.run();

// Configure mechanical losses
CompressorMechanicalLosses losses = comp.initMechanicalLosses(120.0);
losses.setSealType(CompressorMechanicalLosses.SealType.DRY_GAS_TANDEM);
losses.setBearingType(CompressorMechanicalLosses.BearingType.TILTING_PAD);

// Print summary
System.out.println("=== Compressor Results ===");
System.out.println("Power: " + comp.getPower("kW") + " kW");
System.out.println("Polytropic head: " + comp.getPolytropicHead("kJ/kg") + " kJ/kg");
System.out.println();
System.out.println("=== Mechanical Losses ===");
System.out.println("Seal gas consumption: " + comp.getSealGasConsumption() + " Nm³/hr");
System.out.println("  - Primary leakage: " + losses.calculatePrimarySealLeakage() + " Nm³/hr");
System.out.println("  - Buffer gas: " + losses.calculateBufferGasFlow() + " Nm³/hr");
System.out.println("Bearing loss: " + comp.getBearingLoss() + " kW");
System.out.println("Mechanical efficiency: " + (comp.getMechanicalEfficiency() * 100) + "%");
System.out.println();
System.out.println("=== Lube Oil System ===");
System.out.println("Oil flow: " + losses.calculateLubeOilFlowRate() + " L/min");
System.out.println("Cooler duty: " + losses.calculateLubeOilCoolerDuty() + " kW");
```

### Python Example

```python
from jpype import JImplements, JOverride
from neqsim.thermo import fluid
from neqsim.process import compressor, stream

# Create gas and stream
gas = fluid('srk')
gas.addComponent('methane', 0.9)
gas.addComponent('ethane', 0.1)
gas.setMixingRule('classic')

inlet = stream(gas)
inlet.setFlowRate(5000.0, 'kg/hr')
inlet.setTemperature(25.0, 'C')
inlet.setPressure(50.0, 'bara')
inlet.run()

# Create compressor
comp = compressor(inlet)
comp.setOutletPressure(150.0)
comp.setPolytropicEfficiency(0.78)
comp.setSpeed(12000)
comp.run()

# Initialize mechanical losses (120mm shaft)
losses = comp.initMechanicalLosses(120.0)
losses.setSealType(losses.SealType.DRY_GAS_TANDEM)

# Get results
print(f"Seal gas consumption: {comp.getSealGasConsumption():.2f} Nm³/hr")
print(f"Bearing power loss: {comp.getBearingLoss():.2f} kW")
print(f"Mechanical efficiency: {comp.getMechanicalEfficiency()*100:.1f}%")
```

---

## Related Documentation

- [Compressor Curves](compressor_curves) - Detailed curve documentation, templates, and MW correction
- [Process Package](../) - Package overview
- [Expanders](expanders) - Expansion equipment
- [Pumps](pumps) - Liquid compression
