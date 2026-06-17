---
title: Valve Mechanical Design
description: This document describes the mechanical design calculations for control valves in NeqSim, implemented in the `ValveMechanicalDesign` class.
---

# Valve Mechanical Design

This document describes the mechanical design calculations for control valves in NeqSim, implemented in the `ValveMechanicalDesign` class.

## Overview

The valve mechanical design module provides sizing and design calculations for control valves based on **IEC 60534**, **ANSI/ISA-75**, and **ASME B16.34** standards. The calculations enable:

- Valve body sizing and pressure rating selection
- Body wall thickness estimation
- Weight estimation for procurement and installation planning
- Actuator sizing for control applications
- Module dimension calculation for layout planning

## Design Standards Reference

| Standard       | Description                                                          |
| -------------- | -------------------------------------------------------------------- |
| IEC 60534      | Industrial-process control valves                                    |
| ANSI/ISA-75.01 | Flow Equations for Sizing Control Valves                             |
| ANSI/ISA-75.08 | Face-to-Face Dimensions for Flanged Globe-Style Control Valve Bodies |
| ASME B16.34    | Valves - Flanged, Threaded, and Welding End                          |
| API 6D         | Pipeline and Piping Valves                                           |

## Design Calculations

### 1. ANSI Pressure Class Selection

The pressure class is automatically selected based on the design pressure:

| Design Pressure | ANSI Class |
| --------------- | ---------- |
| ≤ 19.6 bara     | Class 150  |
| ≤ 51.1 bara     | Class 300  |
| ≤ 102.1 bara    | Class 600  |
| ≤ 153.2 bara    | Class 900  |
| ≤ 255.3 bara    | Class 1500 |
| ≤ 425.5 bara    | Class 2500 |

```java
// Design pressure with 10% margin
designPressure = operatingPressure × 1.10
```

### 2. Nominal Valve Size

The nominal valve size is calculated from the Cv coefficient using the ISA correlation for globe valves:

```
Cv ≈ 10 × d²
```

Where `d` is the nominal pipe size in inches. Rearranging:

```
d = sqrt(Cv / 10)
```

The calculated size is then rounded to the nearest standard pipe size:
- 0.5", 0.75", 1", 1.5", 2", 3", 4", 6", 8", 10", 12", 14", 16", 18", 20", 24"

### 3. Face-to-Face Dimensions

Face-to-face dimensions are per ANSI/ISA-75.08 for globe-style control valves:

| Nominal Size (in) | Face-to-Face (mm)      |
| ----------------- | ---------------------- |
| ≤ 1.0             | 108                    |
| 1.5               | 117                    |
| 2.0               | 152                    |
| 3.0               | 203                    |
| 4.0               | 241                    |
| 6.0               | 292                    |
| 8.0               | 356                    |
| 10.0              | 432                    |
| 12.0              | 495                    |
| > 12.0            | 508 + (size - 12) × 30 |

**Adjustment for Pressure Class:**
- Class 600+: multiply by 1.05
- Class 900+: multiply by 1.15

### 4. Body Wall Thickness

Wall thickness is calculated using the ASME B16.34 pressure vessel formula:

```
t = (P × R) / (S × E - 0.6 × P) + CA
```

Where:
- `P` = design pressure (MPa)
- `R` = inner radius (mm)
- `S` = allowable stress = 138 MPa (carbon steel at ambient)
- `E` = joint efficiency = 1.0 (forged body)
- `CA` = corrosion allowance

**Minimum:** 3.0 mm wall thickness

### 5. Actuator Sizing

The required actuator thrust is calculated from:

1. **Fluid Force:** Force to overcome pressure across the seat
   ```
   F_fluid = P_design × A_seat
   ```

2. **Packing Friction:** Typically 15% of fluid force
   ```
   F_packing = 0.15 × F_fluid
   ```

3. **Seat Load:** For tight shutoff (Class IV/V)
   ```
   F_seat = π × d_seat × 7 N/mm
   ```

4. **Total Thrust:**
   ```
   F_total = (F_fluid + F_packing + F_seat) × 1.25
   ```

### 6. Weight Estimation

Valve weight is estimated using empirical correlations:

#### Body Weight
```
W_body = 2.5 × (size_inches)^2.5 × (class / 150)^0.5
```

#### Trim and Bonnet
```
W_trim = 0.3 × W_body
```

#### Actuator Weight
```
W_actuator = 0.015 × F_thrust + 5.0 kg (minimum 10 kg)
```

#### Total Weight
```
W_total = W_body + W_trim + W_actuator
```

## Usage Example

```java
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

// Create and run valve
ThrottlingValve valve = new ThrottlingValve("PCV-101", inletStream);
valve.setOutletPressure(60.0, "bara");
valve.run();

// Get mechanical design
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.calcDesign();

// Access results
System.out.println("ANSI Class: " + mechDesign.getAnsiPressureClass());
System.out.println("Nominal Size: " + mechDesign.getNominalSizeInches() + " inches");
System.out.println("Face-to-Face: " + mechDesign.getFaceToFace() + " mm");
System.out.println("Body Wall: " + mechDesign.getBodyWallThickness() + " mm");
System.out.println("Actuator Thrust: " + mechDesign.getRequiredActuatorThrust() + " N");
System.out.println("Total Weight: " + mechDesign.getWeightTotal() + " kg");
```

## API Reference

### `ValveMechanicalDesign` Class

| Method                        | Return | Description                                         |
| ----------------------------- | ------ | --------------------------------------------------- |
| `calcDesign()`                | void   | Performs all mechanical design calculations         |
| `getAnsiPressureClass()`      | int    | Returns ANSI class (150, 300, 600, 900, 1500, 2500) |
| `getNominalSizeInches()`      | double | Returns nominal valve size in inches                |
| `getFaceToFace()`             | double | Returns face-to-face dimension in mm                |
| `getBodyWallThickness()`      | double | Returns body wall thickness in mm                   |
| `getRequiredActuatorThrust()` | double | Returns required actuator thrust in N               |
| `getActuatorWeight()`         | double | Returns estimated actuator weight in kg             |
| `getDesignPressure()`         | double | Returns design pressure in bara                     |
| `getDesignTemperature()`      | double | Returns design temperature in °C                    |
| `getWeightTotal()`            | double | Returns total valve weight in kg                    |

## Valve Sizing Standards

NeqSim supports multiple valve sizing standards that can be selected via `setValveSizingStandard()`:

### Single-Phase Standards (Control Valves)

| Standard         | Description                         | Best For                 |
| ---------------- | ----------------------------------- | ------------------------ |
| `default`        | IEC 60534-based calculation         | General control valves   |
| `IEC 60534`      | Full IEC 60534-2-1 implementation   | Engineering calculations |
| `IEC 60534 full` | Extended IEC 60534 with all factors | Detailed sizing studies  |
| `prod choke`     | Production choke sizing with Cd     | Wellhead chokes          |

### Multiphase Standards (Production Chokes)

| Standard    | Description                             | Best For                        |
| ----------- | --------------------------------------- | ------------------------------- |
| `Sachdeva`  | Mechanistic two-phase model (SPE 15657) | When fluid composition is known |
| `Gilbert`   | Empirical correlation (1954)            | Quick estimates, field matching |
| `Baxendell` | Empirical correlation (1958)            | Higher flow rates               |
| `Ros`       | Empirical correlation (1960)            | Low GLR systems                 |
| `Achong`    | Empirical correlation (1961)            | High GLR systems                |

### Example: Setting Sizing Standard

```java
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();

// For control valves (single-phase)
mechDesign.setValveSizingStandard("IEC 60534");

// For production chokes (two-phase)
mechDesign.setValveSizingStandard("Sachdeva");
mechDesign.setChokeDiameter(0.5, "in");
mechDesign.setChokeDischargeCoefficient(0.84);
```

### Multiphase Choke Helper Methods

For production choke sizing, additional methods are available:

| Method                             | Description                                          |
| ---------------------------------- | ---------------------------------------------------- |
| `setChokeDiameter(value, unit)`    | Set choke diameter (units: "m", "mm", "in", "64ths") |
| `getChokeDiameter()`               | Get choke diameter in meters                         |
| `setChokeDischargeCoefficient(Cd)` | Set discharge coefficient (0.75-0.90 typical)        |

See [Multiphase Choke Flow Models](./MultiphaseChokeFlow) for detailed two-phase choke documentation.

## Valve Characteristics

Available valve characteristics for flow control:

| Characteristic     | Formula                          | Application                   |
| ------------------ | -------------------------------- | ----------------------------- |
| Linear             | `Cv/Cv₁₀₀ = opening/100`         | Constant ΔP systems           |
| Equal Percentage   | `Cv/Cv₁₀₀ = R^((opening/100)-1)` | Variable ΔP, process control  |
| Quick Opening      | `Cv/Cv₁₀₀ = sqrt(opening/100)`   | On/off, safety applications   |
| Modified Parabolic | `Cv/Cv₁₀₀ = opening²/10000`      | Compromise between linear/EQ% |

### Example: Setting Valve Characteristic

```java
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.setValveCharacterization("equal percentage");
```

## Configuring Valve Sizing Parameters

### Selecting the Sizing Standard

Use `setValveSizingStandard()` on the `ValveMechanicalDesign` object to select which
sizing model to use:

```java
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.stream.Stream;

// Create fluid and stream
SystemInterface fluid = new SystemSrkEos(273.15 + 25, 50);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");

// Create valve
ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
valve.setOutletPressure(25.0);
valve.setPercentValveOpening(100);

// Select sizing standard
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.setValveSizingStandard("IEC 60534");
```

### Configuring IEC 60534 Parameters

When using IEC 60534 sizing, the following parameters can be configured on the
sizing method object:

| Parameter | Method | Default | Description |
|-----------|--------|---------|-------------|
| $x_T$ | `setxT(double)` | 0.137 | Pressure drop ratio factor at choked flow. Typical values: globe valve 0.69-0.80, butterfly 0.25-0.50, ball 0.15-0.25 |
| $F_L$ | `setFL(double)` | 1.0 | Liquid pressure recovery factor |
| $F_D$ | `setFD(double)` | 1.0 | Valve style modifier |

```java
// Access the sizing method and configure parameters
mechDesign.setValveSizingStandard("IEC 60534");
mechDesign.getValveSizingMethod().setxT(0.75);  // Globe valve typical value
mechDesign.getValveSizingMethod().setFL(0.90);   // Liquid recovery factor
```

### Computing and Retrieving Cv/Kv

After running the valve in a `ProcessSystem` or standalone, call `calcKv()` to compute
the flow coefficient:

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.run();

// Calculate Cv/Kv
valve.calcKv();

double kv = valve.getKv();  // Flow coefficient (metric)
double cv = valve.getCv();  // Flow coefficient (US/imperial, Cv = 1.156 * Kv)
double cg = valve.getCg();  // Gas sizing coefficient

System.out.printf("Kv = %.2f, Cv = %.2f, Cg = %.2f%n", kv, cv, cg);
```

### Complete Gas Valve Sizing Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

// 1. Create fluid
SystemInterface fluid = new SystemSrkEos(273.15 + 25, 50);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");
fluid.setTotalFlowRate(10000.0, "kg/hr");

// 2. Build process
Stream feed = new Stream("feed", fluid);
ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
valve.setOutletPressure(25.0);
valve.setPercentValveOpening(100);

// 3. Select sizing standard and configure
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.setValveSizingStandard("IEC 60534");
mechDesign.getValveSizingMethod().setxT(0.75);

// 4. Run and size
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.run();
valve.calcKv();

// 5. Read results
System.out.printf("Cv = %.2f%n", valve.getCv());
System.out.printf("Kv = %.2f%n", valve.getKv());
```

### Complete Liquid Valve Sizing Example

```java
// Liquid valve - water service
SystemInterface water = new SystemSrkEos(273.15 + 60, 10);
water.addComponent("water", 1.0);
water.setMixingRule("classic");
water.setTotalFlowRate(50000.0, "kg/hr");

Stream waterFeed = new Stream("water feed", water);
ThrottlingValve lcv = new ThrottlingValve("LCV-200", waterFeed);
lcv.setOutletPressure(5.0);
lcv.setPercentValveOpening(100);

// Use default sizing (adequate for liquid)
ProcessSystem process = new ProcessSystem();
process.add(waterFeed);
process.add(lcv);
process.run();
lcv.calcKv();

System.out.printf("Liquid Cv = %.2f%n", lcv.getCv());
```

### Python (Jupyter Notebook) Example

```python
from neqsim import jneqsim

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")
fluid.setTotalFlowRate(10000.0, "kg/hr")

# Build process
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

feed = Stream("feed", fluid)
valve = ThrottlingValve("PCV-100", feed)
valve.setOutletPressure(25.0)
valve.setPercentValveOpening(100)

# Configure sizing standard
mech_design = valve.getMechanicalDesign()
mech_design.setValveSizingStandard("IEC 60534")
mech_design.getValveSizingMethod().setxT(0.75)

# Run
process = ProcessSystem()
process.add(feed)
process.add(valve)
process.run()

valve.calcKv()
print(f"Cv = {valve.getCv():.2f}")
print(f"Kv = {valve.getKv():.2f}")
```

## IEC 60534 Gas Sizing Formula

For compressible fluids, the Kv (or Cv) is calculated per IEC 60534-2-1 using the volumetric
flow rate at **standard conditions** (273.15 K, 101.325 kPa):

$$K_v = \frac{Q_{std}}{N_9 \cdot P_1 \cdot Y} \sqrt{\frac{M \cdot T \cdot Z}{x}}$$

Where:
- $Q_{std}$ = volumetric flow rate at standard conditions (m³/h at 273.15 K, 101.325 kPa)
- $N_9$ = 24.6 (numerical constant for SI units with standard flow)
- $P_1$ = inlet pressure (kPa abs)
- $Y$ = expansion factor = $1 - \frac{x}{3 \cdot F_\gamma \cdot x_T}$
- $M$ = molecular weight (g/mol)
- $T$ = temperature (K)
- $Z$ = compressibility factor
- $x$ = pressure drop ratio = $\Delta P / P_1$
- $F_\gamma$ = specific heat ratio factor = $\gamma / 1.40$
- $x_T$ = pressure drop ratio factor at choked flow

The conversion from actual volumetric flow to standard flow is:

$$Q_{std} = Q_{actual} \cdot \frac{P_1}{P_{std}} \cdot \frac{T_{std}}{T} \cdot \frac{1}{Z}$$

where $P_{std}$ = 101.325 kPa and $T_{std}$ = 273.15 K.

> **Note:** Prior to the fix for [issue #1918](https://github.com/equinor/neqsim/issues/1918),
> the default and simple sizing methods incorrectly used actual volumetric flow instead of
> standard volumetric flow, resulting in significantly underestimated Cv values.

### Choked Flow

When $x \geq F_\gamma \cdot x_T$, flow becomes choked and:

$$Y = \frac{2}{3}$$
$$x_{effective} = F_\gamma \cdot x_T$$

## Related Documentation

- [Valve Equipment](equipment/valves) - Valve simulation overview
- [Compressor Mechanical Design](CompressorMechanicalDesign) - Compressor mechanical design
- [Process Package](index.md) - Package overview
- [Safety Systems](safety/) - Safety valve sizing

---

## Acoustic-Induced Vibration (AIV) Analysis

Throttling valves are primary sources of Acoustic-Induced Vibration (AIV) due to the turbulent flow and acoustic energy generated during pressure reduction. AIV can cause fatigue failures in downstream piping.

### AIV Calculation

The `ThrottlingValve` class includes AIV analysis per Energy Institute Guidelines:

$$W_{acoustic} = 3.2 \times 10^{-9} \cdot \dot{m} \cdot P_1 \cdot \left(\frac{\Delta P}{P_1}\right)^{3.6} \cdot \left(\frac{T}{273.15}\right)^{0.8}$$

Where:
- $W_{acoustic}$ = Acoustic power (kW)
- $\dot{m}$ = Mass flow rate (kg/s)
- $P_1$ = Upstream pressure (Pa)
- $\Delta P$ = Pressure drop across valve (Pa)
- $T$ = Temperature (K)

### AIV Risk Levels

| Acoustic Power (kW) | Risk Level | Action Required            |
| ------------------- | ---------- | -------------------------- |
| < 1                 | LOW        | No action required         |
| 1 - 10              | MEDIUM     | Review piping layout       |
| 10 - 25             | HIGH       | Detailed analysis required |
| > 25                | VERY HIGH  | Mitigation required        |

### Using AIV Analysis

```java
// Create valve with significant pressure drop
ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
valve.setOutletPressure(30.0, "bara");  // Large ΔP from ~80 bara inlet
valve.run();

// Calculate AIV power
double aivPower = valve.calculateAIV();  // Returns kW
System.out.printf("AIV Power: %.2f kW%n", aivPower);

// Calculate AIV likelihood of failure (requires downstream pipe geometry)
double downstreamDiameter = 0.2032;  // 8 inch
double downstreamThickness = 0.008;  // 8mm wall
double aivLOF = valve.calculateAIVLikelihoodOfFailure(
    downstreamDiameter, downstreamThickness);
System.out.printf("AIV LOF: %.3f%n", aivLOF);

// Set AIV design limit as capacity constraint
valve.setMaxDesignAIV(10.0);  // kW (default is 10 kW for valves)

// Access AIV constraint
CapacityConstraint aivConstraint = valve.getCapacityConstraints().get("AIV");
double utilization = aivConstraint.getUtilization();  // Current/Design ratio
```

### AIV Mitigation Strategies

When AIV is identified as a concern:

1. **Increase downstream pipe wall thickness** - Reduces LOF
2. **Use acoustic/vibration analysis software** - Detailed assessment
3. **Install acoustic dampeners** - Reduces transmitted energy
4. **Multi-stage pressure reduction** - Reduces ΔP per stage
5. **Increase pipe diameter** - Reduces velocity and acoustic intensity
