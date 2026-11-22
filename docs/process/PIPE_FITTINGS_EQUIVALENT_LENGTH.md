---
title: Pipe Fittings and Equivalent Length Method
description: NeqSim supports pressure drop calculations through pipe fittings (bends, valves, tees, reducers, etc.) using the **equivalent length method**. This method converts the pressure loss through each fitti...
---

# Pipe Fittings and Equivalent Length Method

## Overview

NeqSim supports pressure drop calculations through pipe fittings (bends, valves, tees, reducers, etc.) using the **equivalent length method**. This method converts the pressure loss through each fitting into an equivalent length of straight pipe that would produce the same pressure drop.

## Mathematical Basis

### The Equivalent Length Method

For fully turbulent flow, the pressure drop through a fitting can be expressed using the **K-factor** (resistance coefficient) method:

$$\Delta P_{fitting} = K \cdot \frac{\rho V^2}{2}$$

The equivalent length method relates K to the Darcy friction factor $f$:

$$K = f \cdot \frac{L_{eq}}{D}$$

where $\frac{L_{eq}}{D}$ is the equivalent length ratio (L/D).

Combining with the Darcy-Weisbach equation for pipe friction:

$$\Delta P_{friction} = f \cdot \frac{L}{D} \cdot \frac{\rho V^2}{2}$$

The **effective length** for pressure drop calculations becomes:

$$L_{eff} = L_{physical} + \sum_{i} \left(\frac{L}{D}\right)_i \cdot D$$

where:
- $L_{eff}$ = effective length (m)
- $L_{physical}$ = physical pipe length (m)  
- $(L/D)_i$ = equivalent length ratio of fitting $i$ (dimensionless)
- $D$ = pipe internal diameter (m)

### Total Pressure Drop

The total pressure drop in a pipe with fittings is:

$$\Delta P_{total} = f \cdot \frac{L_{eff}}{D} \cdot \frac{\rho V^2}{2} + \rho g \Delta z$$

**Components:**
- **Friction loss**: $\Delta P_f = f \cdot \frac{L_{eff}}{D} \cdot \frac{\rho V^2}{2}$
- **Elevation change**: $\Delta P_z = \rho g (z_{in} - z_{out})$

Note: Fittings affect only the friction pressure drop, not the elevation term.

### Friction Factor Correlations

NeqSim uses the **Haaland equation** for turbulent flow:

$$f = \left[ -1.8 \log_{10} \left( \left( \frac{\varepsilon/D}{3.7} \right)^{1.11} + \frac{6.9}{Re} \right) \right]^{-2}$$

For laminar flow ($Re < 2300$):

$$f = \frac{64}{Re}$$

For transition flow ($2300 < Re < 4000$): Linear interpolation between laminar and turbulent.

---

## Standard L/D Values

The following values are **NeqSim equivalent-length defaults**. The built-in catalogue mixes
traditional fitting ratios and engineering approximations; it is not a verified transcription
of a particular Crane TP-410 edition or table. Radius alone does not specify a fitting's
connection type, bore, or manufacturing geometry. Use a documented project or manufacturer
value for the actual fitting when those details matter.

The built-in identifiers and `src/main/resources/designdata/Fittings.csv` are separate catalogues.
Their overlapping, radius-qualified elbows use the same defaults:

| Database name | Built-in identifier | L/D |
|---|---|---:|
| `Standard elbow (R=1.5D), 90deg` | `elbow_90_long_radius` | 16 |
| `Standard elbow (R=1D), 90deg` | `elbow_90_standard` | 30 |
| `Standard elbow (R=1.5D), 45deg` | `elbow_45_long_radius` | 10 |
| `Standard elbow (R=1D), 45deg` | `elbow_45_standard` | 16 |

The CSV also retains handbook-attributed size-, bore-, and opening-specific valve entries.
These are not aliases for every built-in valve. For example, the database full-bore,
half-open gate valve has L/D = 400, while the generic built-in half-open gate valve is 160.
Choose the appropriate catalogue explicitly.

**Compatibility note (#4376):** the CSV R=1.5D elbow entries previously contained 30
(90 degrees) and 16 (45 degrees). They now return 16 and 10, respectively, matching the
built-in defaults. The R=1D names are now present in the CSV. The revised elbow rows are
identified as NeqSim defaults rather than attributed to an unverified handbook entry.
Reassess calculations that used those database names. To retain a previous project assumption,
supply an explicit L/D of 30 or 16 with `addFitting(name, LdivD)`.
Lookups now initialize the process-design database that owns the fittings table. Previously,
a clean runtime could return L/D = 1 even for valid names because only the thermodynamic
database had been initialized. Correct database values can therefore increase losses in
models that previously hit that fallback.

Database lookups require the exact stored name, including case. Unknown names now throw
`IllegalArgumentException` without adding a fitting; database failures throw
`IllegalStateException`. There is no L/D = 1 fallback. Built-in identifiers are accepted
case-insensitively by both lookup routes; `addStandardFitting` still returns `false` for an
unknown built-in identifier.

### Elbows and Bends

| Fitting Type | L/D | Notes |
|--------------|-----|-------|
| 90° elbow, standard (R/D=1) | 30 | Standard radius |
| 90° elbow, long radius (R/D=1.5) | 16 | Most common in process |
| 90° mitre bend | 60 | Sharp corner |
| 45° elbow, standard | 16 | |
| 45° elbow, long radius | 10 | |
| 180° return bend | 50 | |

### Tees

| Fitting Type | L/D | Notes |
|--------------|-----|-------|
| Tee, through flow | 20 | Flow continues straight |
| Tee, branch flow | 60 | Flow turns into branch |

### Valves

| Valve Type | L/D | Notes |
|------------|-----|-------|
| Gate valve, fully open | 8 | Low resistance |
| Gate valve, 3/4 open | 35 | |
| Gate valve, 1/2 open | 160 | |
| Gate valve, 1/4 open | 900 | |
| Globe valve, fully open | 340 | High resistance |
| Ball valve, fully open | 3 | Very low resistance |
| Butterfly valve, fully open | 45 | Generic approximation; use manufacturer Cv data |
| Check valve, swing | 100 | |
| Check valve, lift | 600 | |

### Other Fittings

The entrance, exit, reducer, and expander entries below are fixed equivalent-length
approximations. Their implied reference K values (0.5, 0.2, 1.0, 0.6, 1.0, 0.2, and 0.4 for
sharp entrance, rounded entrance, exit, sudden reducer, sudden expander, gradual reducer, and
gradual expander) correspond to a reference Darcy friction factor of 0.02. They do not
preserve those K values at other friction factors and are not tabulated Crane L/D data.
Reducers and expanders require actual area ratios and angles for a geometry-specific loss.

| Fitting Type | L/D | Notes |
|--------------|-----|-------|
| Sudden expansion | 50 | Depends on area ratio |
| Sudden contraction | 30 | Depends on area ratio |
| Gradual reducer | 10 | |
| Gradual expander | 20 | |
| Entrance, sharp-edged | 25 | Pipe from tank |
| Entrance, rounded | 10 | |
| Exit to tank | 50 | |

---

## Implementation in NeqSim

### Supported Pipe Classes

The equivalent length method is implemented in the following pipe classes:

1. **`Pipeline`** (base class) - Provides fittings management
2. **`AdiabaticPipe`** - Single-phase compressible gas flow
3. **`PipeBeggsAndBrills`** - Multiphase flow (Beggs & Brill correlation)
4. **`IncompressiblePipeFlow`** - Single-phase liquid flow

### Key Methods

```java
// Add a fitting with explicit L/D ratio
pipe.addFitting(String name, double LdivD);

// Add a fitting from database
pipe.addFittingFromDatabase(String name);

// Add a standard fitting type (uses built-in L/D values)
pipe.addStandardFitting(String type);

// Add multiple identical fittings
pipe.addFittings(String name, double LdivD, int count);

// Get equivalent length from fittings
double eqLength = pipe.getEquivalentLength();  // meters

// Get effective length (physical + fittings)
double effLength = pipe.getEffectiveLength();  // meters

// Enable/disable fittings in calculations
pipe.setUseFittings(boolean enable);

// Print fittings summary
pipe.printFittingsSummary();
```

### Standard Fitting Types

Use these type names with `addStandardFitting()`:

**Elbows:**
- `elbow_90_standard`, `elbow_90_long_radius`, `elbow_90_mitre`
- `elbow_45_standard`, `elbow_45_long_radius`

**Tees:**
- `tee_through`, `tee_branch`

**Valves:**
- `valve_gate_open`, `valve_gate_3_4_open`, `valve_gate_1_2_open`, `valve_gate_1_4_open`
- `valve_globe_open`, `valve_ball_open`, `valve_butterfly_open`
- `valve_check_swing`, `valve_check_lift`

**Other:**
- `reducer_sudden`, `reducer_gradual`
- `expander_sudden`, `expander_gradual`
- `entrance_sharp`, `entrance_rounded`, `exit`

---

## Usage Examples

### Example 1: Single-Phase Gas Flow (AdiabaticPipe)

```java
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create gas stream
SystemSrkEos gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 10.0, "MSm3/day");
gas.setMixingRule("classic");
Stream feed = new Stream("Feed", gas);
feed.run();

// Create pipe with fittings
AdiabaticPipe pipe = new AdiabaticPipe("Export Pipe", feed);
pipe.setLength(500.0);           // 500m physical length
pipe.setDiameter(0.508);         // 20 inch
pipe.setPipeWallRoughness(5e-5); // Commercial steel

// Add fittings
pipe.addFittings("90-degree elbow", 30.0, 4);  // 4 elbows, L/D=30 each
pipe.addFitting("gate valve", 8.0);            // 1 gate valve
pipe.addStandardFitting("tee_through");        // 1 tee (through flow)

// Run calculation
pipe.run();

// Results
System.out.println("Physical length: " + pipe.getLength() + " m");
System.out.println("Equivalent length: " + pipe.getEquivalentLength() + " m");
System.out.println("Effective length: " + pipe.getEffectiveLength() + " m");
System.out.println("Pressure drop: " + pipe.getPressureDrop() + " bar");

// Compare with no fittings
pipe.setUseFittings(false);
pipe.run();
System.out.println("Pressure drop (no fittings): " + pipe.getPressureDrop() + " bar");
```

**Output:**
```
Physical length: 500.0 m
Equivalent length: 74.93 m (4×30 + 8 + 20) × 0.508
Effective length: 574.93 m
Pressure drop: 1.45 bar
Pressure drop (no fittings): 1.26 bar
```

### Example 2: Multiphase Flow (PipeBeggsAndBrills)

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create two-phase stream (gas + oil)
SystemSrkEos fluid = new SystemSrkEos(323.15, 80.0);
fluid.addComponent("methane", 5.0, "MSm3/day");
fluid.addComponent("nC10", 500.0, "kg/hr");
fluid.setMixingRule("classic");
Stream feed = new Stream("Wellhead", fluid);
feed.run();

// Create multiphase flowline
PipeBeggsAndBrills flowline = new PipeBeggsAndBrills("Flowline", feed);
flowline.setLength(2000.0);          // 2 km
flowline.setDiameter(0.2032);        // 8 inch
flowline.setPipeWallRoughness(5e-5);
flowline.setInletElevation(0);
flowline.setOutletElevation(-50);    // 50m downward
flowline.setNumberOfIncrements(50);

// Add typical flowline fittings
flowline.addFittings("90-degree elbow", 30.0, 6);  // 6 bends
flowline.addFitting("tee branch", 60.0);          // 1 tee (branch flow)
flowline.addStandardFitting("valve_ball_open");   // Ball valve

flowline.run();

// Results
System.out.println("Flow regime: " + flowline.getFlowRegime());
System.out.println("Liquid holdup: " + flowline.getLiquidHoldup());
System.out.println("Physical length: " + flowline.getLength() + " m");
System.out.println("Equivalent length (fittings): " + flowline.getEquivalentLength() + " m");
System.out.println("Effective length: " + flowline.getEffectiveLength() + " m");
System.out.println("Pressure drop: " + 
    (flowline.getInletPressure() - flowline.getOutletPressure()) + " bar");

// Print fittings summary
flowline.printFittingsSummary();
```

### Example 3: Liquid Flow (IncompressiblePipeFlow)

```java
import neqsim.process.equipment.pipeline.IncompressiblePipeFlow;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create water stream
SystemSrkEos water = new SystemSrkEos(298.15, 5.0);
water.addComponent("water", 50.0, "m3/hr");
water.setMixingRule(2);
Stream feed = new Stream("Water", water);
feed.run();

// Create process piping with fittings
IncompressiblePipeFlow pipe = new IncompressiblePipeFlow("Cooling Water", feed);
pipe.setLength(200.0);           // 200m
pipe.setDiameter(0.1524);        // 6 inch
pipe.setPipeWallRoughness(4.5e-5);

// Typical process piping fittings
pipe.addFittings("elbow_90_long_radius", 16.0, 8);  // 8 long-radius elbows
pipe.addFitting("tee_through", 20.0);
pipe.addFitting("tee_through", 20.0);
pipe.addFitting("gate_valve", 8.0);
pipe.addFitting("gate_valve", 8.0);
pipe.addFittingFromDatabase("Globe Valve, full bore, full open");  // From database

// Elevation change (pump suction from tank)
pipe.setInletElevation(0);
pipe.setOutletElevation(15);  // Pump up 15m

pipe.run();

System.out.println("Effective length: " + pipe.getEffectiveLength() + " m");
System.out.println("Outlet pressure: " + pipe.getOutletPressure("bara") + " bara");
```

### Example 4: Comparing Results With and Without Fittings

```java
// Create pipe
AdiabaticPipe pipe = new AdiabaticPipe("Test Pipe", feed);
pipe.setLength(1000.0);
pipe.setDiameter(0.3);
pipe.setPipeWallRoughness(5e-5);

// Add fittings
pipe.addFittings("90-degree elbow", 30.0, 10);
pipe.addFittings("gate valve", 8.0, 2);

// Run with fittings
pipe.setUseFittings(true);
pipe.run();
double dpWithFittings = pipe.getPressureDrop();

// Run without fittings  
pipe.setUseFittings(false);
pipe.run();
double dpNoFittings = pipe.getPressureDrop();

// Calculate fitting contribution
double fittingContribution = (dpWithFittings - dpNoFittings) / dpWithFittings * 100;
System.out.println("Pressure drop with fittings: " + dpWithFittings + " bar");
System.out.println("Pressure drop without fittings: " + dpNoFittings + " bar");
System.out.println("Fittings contribution: " + fittingContribution + "%");
```

---

## Technical Notes

### When to Use Equivalent Length Method

The equivalent length method is most accurate when:

1. **Turbulent flow** ($Re > 4000$) - The method assumes fully-developed turbulent flow
2. **Consistent pipe size** - Fittings connect pipes of the same diameter
3. **Standard fittings** - Using industry-standard fittings with known L/D values

For laminar flow or complex geometries, the K-factor method may be more accurate.

### Relationship Between L/D and K-Factor

For NeqSim's equivalent-length pressure-loss path, $K = f_D (L/D)$, where $f_D$ is the
Darcy friction factor used by that pipe model. `Fitting.getKFactor(darcyFrictionFactor)`
computes this value from the current L/D and requires a finite, positive Darcy factor.
For L/D = 16, the resulting K is 0.32 at $f_D = 0.02$ and 0.432 at $f_D = 0.027$.
A Fanning factor must be multiplied by four before being supplied to this method.

Crane resistance correlations expressed as $K = f_T (L/D)$ instead require the applicable
fully turbulent reference Darcy factor $f_T$. The overload can multiply by a supplied
$f_T$, but it does not determine that factor or turn the catalogue into a Crane correlation.
The pipe solvers continue to use their calculated flow-dependent friction factor.

The no-argument `Fitting.getKFactor()` and `setKFactor(double)` are deprecated metadata
accessors retained for compatibility. An unset metadata value is now `NaN`, rather than a
misleading zero. Setting this metadata does not change L/D or pipe pressure loss. Use the
explicit-friction-factor overload for an equivalent K. For a specified design K, convert it
to L/D with the Darcy factor appropriate to the calculation before adding the fitting.

For turbulent flow with typical friction factor $f \approx 0.02$:

$$K \approx 0.02 \times (L/D)$$

Example: A 90° elbow with L/D = 30 has $K \approx 0.6$

### Multiphase Flow Considerations

For multiphase flow (e.g., PipeBeggsAndBrills):

1. The equivalent length is added to the physical length before segmentation
2. Each segment's friction calculation uses the proportional effective length
3. The slip correction factor $S$ is applied to the two-phase friction factor
4. Elevation effects are NOT affected by fittings (fittings don't add elevation)

### Limitations

1. **Two-phase K-factors** - The single-phase L/D values may underestimate losses in two-phase flow. Some engineers apply a 1.2-1.5 multiplier for two-phase.

2. **Close-coupled fittings** - When fittings are installed close together (less than 10D apart), the combined loss may differ from the sum of individual losses.

3. **Partial valve openings** - The L/D values for partially open valves are approximate. Use manufacturer's Cv data for accurate calculations.

---

## References

1. Crane Co., "Flow of Fluids Through Valves, Fittings, and Pipe," Technical Paper 410 (TP-410)
2. Beggs, H.D. and Brill, J.P., "A Study of Two-Phase Flow in Inclined Pipes," JPT, May 1973
3. Darby, R., "Chemical Engineering Fluid Mechanics," 2nd Ed., Marcel Dekker, 2001
4. Perry's Chemical Engineers' Handbook, 8th Edition, McGraw-Hill
5. ASME B16.5 - Pipe Flanges and Flanged Fittings
6. ISO 5167 - Measurement of fluid flow by means of pressure differential devices

---

## Database Schema

The bundled `fittings` table is populated from
`src/main/resources/designdata/Fittings.csv`, with columns `ID`, `Type`, `Name`, `LtoD`,
and `Reference`. The exact `Name` is used by `addFittingFromDatabase`.

Use `addFitting(name, LdivD)` for project-specific values without modifying the bundled
catalogue. The name in that explicit-value API is descriptive and need not exist in the
database.
