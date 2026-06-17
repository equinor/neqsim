---
title: "TurboExpanderCompressor Model"
description: "This document summarizes the mathematical basis of the coupled expander/compressor model, how reference curves are applied (and can be replaced), and provides a usage walkthrough for configuring and r..."
---

# TurboExpanderCompressor Model

This document summarizes the mathematical basis of the coupled expander/compressor model, how reference curves are applied (and can be replaced), and provides a usage walkthrough for configuring and running the unit in a process simulation.

---

## Mathematical Basis

The expander and compressor share a shaft speed that is iteratively adjusted until the expander power balances the compressor power plus bearing losses using a **Newton-Raphson** iteration. The key computational steps are described below.

### Expander Calculations

#### 1. Isentropic Enthalpy Drop

The isentropic enthalpy drop across the expander is calculated from an isentropic flash at the target outlet pressure:

$$
\Delta h_s = (h_{in} - h_{out,s}) \times 1000 \quad \text{[J/kg]}
$$

where $h_{in}$ is the inlet enthalpy and $h_{out,s}$ is the isentropic outlet enthalpy.

#### 2. Velocity Ratio Calculation

The tip speed $U$ and spouting (jet) velocity $C$ are computed as:

$$
U = \frac{\pi \cdot D \cdot N}{60}
$$

$$
C = \sqrt{2 \cdot \Delta h_s}
$$

The velocity ratio is then:

$$
u_c = \frac{U}{C \cdot u_{c,design}}
$$

An efficiency correction factor is evaluated from the UC reference curve based on this ratio.

#### 3. Efficiency Corrections

The actual expander isentropic efficiency is calculated by applying correction factors:

$$
\eta_s = \eta_{s,design} \cdot f_{UC}(u_c) \cdot f_{Q/N}\left(\frac{Q/N}{(Q/N)_{design}}\right)
$$

where:
- $f_{UC}$ is the velocity ratio correction factor from the UC curve
- $f_{Q/N}$ is the optional flow coefficient correction from the Q/N efficiency spline

#### 4. Expander Shaft Power

The expander shaft power is:

$$
W_{expander} = \dot{m} \cdot \Delta h_s \cdot \eta_s
$$

where $\dot{m}$ is the mass flow rate.

---

### Compressor Calculations

#### 1. Head and Efficiency Corrections

The compressor polytropic head and efficiency are corrected for off-design operation:

$$
H_p = H_{p,design} \cdot f_{head}\left(\frac{Q/N}{(Q/N)_{design}}\right) \cdot \left(\frac{N}{N_{design}}\right)^2
$$

$$
\eta_p = \eta_{p,design} \cdot f_{\eta}\left(\frac{Q/N}{(Q/N)_{design}}\right)
$$

where:
- $f_{head}$ is the head correction factor from the Q/N head spline
- $f_{\eta}$ is the efficiency correction factor from the Q/N efficiency spline
- The $(N/N_{design})^2$ term applies the fan law scaling for head

#### 2. Compressor Shaft Power

The compressor shaft power is:

$$
W_{comp} = \frac{\dot{m} \cdot H_p}{\eta_p}
$$

---

### Power Balance and Speed Iteration

The Newton-Raphson iteration solves for the shaft speed $N$ that satisfies:

$$
f(N) = W_{expander} - \left(W_{comp} + W_{bearing}\right) = 0
$$

where the bearing losses are modeled as a quadratic function of speed:

$$
W_{bearing} = k \cdot N^2
$$

The iteration continues until the power mismatch is negligible or iteration limits are reached. The final speed is applied to compute outlet stream properties.

---

## Specifying the Expander Outlet Temperature

By default the expander outlet temperature is a **result** — it falls out of the actual isentropic efficiency $\eta_s$ (design efficiency × curve corrections). In some workflows the outlet temperature is known from plant data or a process target, and the **base (design) efficiency should be adjusted to reproduce it**. The model supports this inverse mode.

### Activating the Mode

```java
turboExpanderComp.setExpanderOutTemperature(-40.8, "C");
```

This stores the target temperature (converted to Kelvin internally), sets `useOutTemperatureSpec = true`, and triggers the back-calculation during `run()`. The supported units are `"K"`, `"C"`, `"F"`, and `"R"`. The mode can be toggled with `setUseOutTemperatureSpec(boolean)` and queried with `isUseOutTemperatureSpec()`.

### How It Works

1. **Required actual efficiency.** Before the speed iteration, the actual isentropic efficiency needed to reach the target outlet temperature $T_{target}$ at the configured outlet pressure is computed from the inlet state, an isentropic flash, and an isothermal (TP) flash at the target:

$$
\eta_{s,req} = \frac{h_{in} - h_{out}(T_{target}, P_{out})}{h_{in} - h_{out,s}(P_{out})}
$$

Because both enthalpy drops depend only on the inlet state and the outlet pressure, $\eta_{s,req}$ is independent of shaft speed.

2. **Fixed efficiency during speed matching.** The actual efficiency is held at $\eta_{s,req}$ throughout the Newton-Raphson iteration. The expander power $W_{expander} = \dot{m}\,\Delta h_s\,\eta_{s,req}$ is therefore constant, and the iteration simply finds the speed where the compressor plus bearing power balances it.

3. **Back-calculated design efficiency.** After convergence the base (design) isentropic efficiency is recovered from the converged off-design correction factor $CF_{total} = f_{UC}(u_c)\cdot f_{Q/N}$:

$$
\eta_{s,design} = \frac{\eta_{s,req}}{CF_{total}}
$$

so that the standard forward relation $\eta_s = \eta_{s,design}\cdot CF_{total}$ reproduces the required efficiency. The value is available through `getExpanderDesignIsentropicEfficiency()`.

### Round-Trip Consistency

The forward and inverse modes are exact inverses of one another: running with a given design efficiency yields an outlet temperature, and running with that outlet temperature specified recovers the original design efficiency (and the same actual efficiency, speed, and power). This is verified by `TurboExpanderCompressorTest#testOutletTemperatureSpecConsistency`.

> **Physical caveat:** A target temperature that implies $\eta_{s,req} > 1$ or $\eta_{s,req} < 0$ (e.g. an outlet colder than the isentropic outlet) is not physically achievable; the back-calculated design efficiency will reflect that and should be sanity-checked.

---

## Reference Curves

Three types of reference curves tune performance away from the design point:

### 1. UC/Efficiency Curve

A **constrained parabola** through the peak at $(u_c = 1, \eta = 1)$:

$$
f_{UC}(u_c) = a \cdot u_c^2 + b \cdot u_c + c
$$

The curve can be replaced with `setUCcurve(ucValues, efficiencyValues)` if alternate test data are available.

### 2. Q/N Efficiency Curve

A **monotonic cubic Hermite spline** built from paired Q/N and efficiency arrays via `setQNEfficiencycurve`:

$$
f_{\eta}\left(\frac{Q/N}{(Q/N)_{design}}\right) = \text{spline interpolation}
$$

Values are extrapolated linearly outside the provided range, allowing off-map operation while preserving trend continuity.

### 3. Q/N Head Curve

A similar **cubic Hermite spline** created with `setQNHeadcurve` that scales polytropic head at off-design flows:

$$
f_{head}\left(\frac{Q/N}{(Q/N)_{design}}\right) = \text{spline interpolation}
$$

Like the efficiency spline, it preserves monotonicity and extrapolates linearly beyond the data range.

> **Note:** Curve coefficients are stored on the equipment instance and can be replaced at runtime to test alternative reference maps or updated dynamically from external performance monitoring tools.

---

## Using the Model

### Step 1: Construct the Unit and Streams

Clone feeds for the expander and compressor outputs when instantiating the equipment.

### Step 2: Set Design Parameters

Provide impeller diameter, design speed, efficiencies, design Q/N, and optional expander design Q/N if expander flow corrections are needed. The defaults mirror the embedded design values but can be overridden through the available setters.

### Step 3: Load Reference Curves (Optional)

If site-specific head or efficiency curves exist, call `setUCcurve`, `setQNEfficiencycurve`, and `setQNHeadcurve` with measured points before running the unit.

### Step 4: Run the Model

Call `run(UUID id)` (or the no-argument overload) to iterate speed matching and populate result fields and outlet streams. Retrieve shaft powers with `getPowerExpander(unit)` and `getPowerCompressor(unit)` or inspect efficiencies, head, and Q/N ratios through the getters.

### Example Code

A realistic setup that mirrors common plant data collection and map-updating workflows:

```java
TurboExpanderCompressor turboExpanderComp = new TurboExpanderCompressor(
    "TurboExpanderCompressor", jt_tex_splitter.getSplitStream(0));
turboExpanderComp.setUCcurve(
    new double[] {0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
        0.9552460269880922, 1.0},
    new double[] {0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364,
        0.9943181818181818, 1.0});
turboExpanderComp.setQNEfficiencycurve(
    new double[] {0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6},
    new double[] {0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6});
turboExpanderComp.setQNHeadcurve(
    new double[] {0.5, 0.8, 1.0, 1.2, 1.4, 1.6},
    new double[] {1.1, 1.05, 1.0, 0.9, 0.7, 0.4});
turboExpanderComp.setImpellerDiameter(0.424);
turboExpanderComp.setDesignSpeed(6850.0);
turboExpanderComp.setExpanderDesignIsentropicEfficiency(0.88);
turboExpanderComp.setDesignUC(0.7);
turboExpanderComp.setDesignQn(0.03328);
turboExpanderComp.setExpanderOutPressure(inp.expander_out_pressure);
turboExpanderComp.setCompressorDesignPolytropicEfficiency(0.81);
turboExpanderComp.setCompressorDesignPolytropicHead(20.47);
turboExpanderComp.setMaximumIGVArea(1.637e4);

// Run the coupled model and retrieve power with unit conversion
turboExpanderComp.run();
double expanderPowerMW = turboExpanderComp.getPowerExpander("MW");
double compressorPowerMW = turboExpanderComp.getPowerCompressor("MW");
```

---

## Parameter Reference

### Velocity Ratio (UC) Curve

| Parameter | Description |
|-----------|-------------|
| `setUCcurve(ucValues, effValues)` | Normalizes the velocity ratio $u_c = \frac{U}{C \cdot u_{c,design}}$ to an efficiency multiplier via a constrained parabola fitted to the supplied points |

### Q/N Curves

| Parameter | Description |
|-----------|-------------|
| `setQNEfficiencycurve(qnValues, effValues)` | Cubic Hermite spline that scales expander and compressor efficiencies against flow coefficient deviations $Q/N$ |
| `setQNHeadcurve(qnValues, headValues)` | Spline used to scale the compressor polytropic head for off-design flows before applying the $(N/N_{design})^2$ speed law |

### Geometry and Design Point

| Parameter | Description |
|-----------|-------------|
| `setImpellerDiameter(D)` | Impeller diameter [m] — sets the peripheral velocity $U$ at design, anchoring UC corrections |
| `setDesignSpeed(N)` | Design rotational speed [rpm] — anchor for Newton iteration speed matching |
| `setExpanderDesignIsentropicEfficiency(η)` | Base isentropic efficiency multiplied by curve correction factors |
| `setCompressorDesignPolytropicEfficiency(η)` | Base polytropic efficiency for the compressor |
| `setCompressorDesignPolytropicHead(Hp)` | Design polytropic head [kJ/kg] |
| `setDesignUC(uc)` | Design velocity ratio for the expander |
| `setDesignQn(qn)` | Reference flow coefficient $(Q/N)_{design}$ for the compressor |
| `setDesignExpanderQn(qn)` | Reference flow coefficient for the expander (optional) |

### Operating Conditions

| Parameter | Description |
|-----------|-------------|
| `setExpanderOutPressure(P)` | Target outlet pressure for the isentropic flash that produces $\Delta h_s$ |
| `setExpanderOutTemperature(T, unit)` | Specify the expander outlet temperature; the design isentropic efficiency is back-calculated to match it (see [Specifying the Expander Outlet Temperature](#specifying-the-expander-outlet-temperature)) |
| `setUseOutTemperatureSpec(flag)` | Enable/disable the outlet-temperature specification mode |

### IGV Geometry

| Parameter | Description |
|-----------|-------------|
| `setMaximumIGVArea(A)` | Maximum inlet guide vane throat area [mm²] |
| `setIgvAreaIncreaseFactor(f)` | Optional factor to expand available IGV area |

> **Tip:** The same update paths can be invoked during runtime if monitoring identifies drift in the reference maps; supplying new curve points and re-running will propagate the new performance predictions.

---

## IGV Handling

The Inlet Guide Vane (IGV) opening is computed from the last stage enthalpy drop, mass flow, and volumetric flow each time `run()` completes.

### IGV Calculation Method

The helper `evaluateIGV` performs the following:

1. **Infer density** from the fluid properties
2. **Estimate nozzle velocity** from half the stage enthalpy drop:

$$
v_{nozzle} = \sqrt{\Delta h_{stage}}
$$

3. **Derive required area** to pass the flow:

$$
A_{required} = \frac{\dot{V}}{v_{nozzle}}
$$

4. **Calculate IGV opening** as the area ratio:

$$
\text{IGV}_{opening} = \min\left(\frac{A_{required}}{A_{throat}}, 1.0\right)
$$

### IGV Area Expansion

If the required area exceeds the installed IGV area, an optional enlargement factor (`setIgvAreaIncreaseFactor`) increases the available area:

$$
A_{available} = A_{max} \cdot f_{increase}
$$

### IGV Output Methods

| Method | Description |
|--------|-------------|
| `calcIGVOpening()` | Returns the calculated IGV opening fraction (0–1) |
| `calcIGVOpenArea()` | Returns the actual open area [mm²] |
| `getCurrentIGVArea()` | Returns the current IGV throat area [mm²] |

---

## Off-Design State-of-the-Art Enhancements

The base model above uses 1-D reference curves. For high-fidelity off-design
operability studies (turndown, trip/blowdown, mechanical limits, multi-variable
envelopes) the following companion classes extend the expander side to the same
fidelity as the composition-aware compressor side.

### 1. Composition-Aware Expander Map — `ExpanderChartKhader`

A 2-D radial-inflow expander performance map modeled on
`CompressorChartKhader2015`. It returns isentropic efficiency and stage head
drop as a function of velocity ratio $U/C$ and IGV position, and is
**composition-aware**: head is normalized by the reference-fluid speed of
sound squared and rescaled by the actual process-fluid $c_s^2$ at run time.

$$
\hat{H} = \frac{\Delta h_s}{c_{s,ref}^2}, \qquad
\Delta h_s(\text{fluid}) = \hat{H}\,(U/C, \text{IGV}) \cdot c_{s,fluid}^2
$$

Efficiency and head are interpolated bilinearly over $U/C$ and IGV position
with edge clamping. When a map is attached, `TurboExpanderCompressor`'s
`computeExpanderEfficiency()` uses it; otherwise it falls back to the parabolic
$U/C \times \eta_{design}$ law.

```java
ExpanderChartKhader chart = new ExpanderChartKhader(referenceFluid, 0.424);
chart.setCurves(igvPositions, ucGrid, etaGrid, headDropKjPerKgGrid);
turboExpanderComp.setExpanderChart(chart);
double eta = chart.getEfficiency(0.7, 1.0);
double dhDrop = chart.getStageHeadDrop(0.7, 1.0, processFluid);
```

### 2. IGV as a Controllable Degree of Freedom

By default the IGV opening is recomputed by the model after every `run()`
(see [IGV Handling](#igv-handling)). Enabling **IGV control mode** promotes
`IGVopening` to a true input — the model no longer overwrites it — and couples
it to an efficiency-penalty curve so the IGV can act as the primary turndown
actuator with speed and power balanced around it.

```java
turboExpanderComp.setIgvControlMode(true);
turboExpanderComp.setIgvEfficiencyPenaltyCurve(
    new double[] {0.2, 0.4, 0.6, 0.8, 1.0},   // IGV opening fractions
    new double[] {0.82, 0.90, 0.95, 0.99, 1.0}); // efficiency multipliers
turboExpanderComp.setIGVopening(0.6);
double penalty = turboExpanderComp.getIgvEfficiencyPenalty(0.6);
```

| Method | Description |
|--------|-------------|
| `setIgvControlMode(boolean)` | Enable/disable IGV as a fixed input (turndown actuator) |
| `isIgvControlMode()` | Returns whether IGV control mode is active |
| `setIgvEfficiencyPenaltyCurve(openings, factors)` | Set the $\eta(\text{IGV})$ loss curve |
| `getIgvEfficiencyPenalty(igv)` | Linear-interpolated efficiency multiplier (1.0 if no curve) |

### 3. Dynamic Anti-Surge Control — `AntiSurgeController`

A transient regulator (subclass of `ControllerDeviceBaseClass`) that reads
`Compressor.getDistanceToSurge()` and drives a recycle `ThrottlingValve` so the
machine can run through trip, blowdown, startup, and load-rejection transients.
It uses a reverse-acting PI law (error $= $ set-point $-$ surge margin) with
anti-windup clamping, applied each `runTransient` timestep.

```java
AntiSurgeController asc = new AntiSurgeController("ASC", compressor, recycleValve);
asc.setSurgeMarginSetPoint(0.10);
asc.setProportionalGain(400.0);
asc.setIntegralTime(20.0);
asc.setOpeningRange(0.0, 100.0);
recycleValve.addController("ASC", asc); // runs inside ProcessSystem.runTransient()
```

### 4. OEM Map Ingestion — `TurboExpanderMapIngestion`

A loader that builds auditable maps from digitized OEM data sheets (e.g.
ACR00162 design and Case B points). It constructs a
`CompressorChartKhader2015` plus an `ExpanderChartKhader`, then validates the
expander map against the certified anchor points within a tolerance.

```java
TurboExpanderMapIngestion ingest = new TurboExpanderMapIngestion();
ingest.setReferenceFluid(referenceFluid);
ingest.addAnchorPoint("design", 0.70, 1.0, 0.88);
ingest.addAnchorPoint("caseB", 0.66, 0.6, 0.84);
ExpanderChartKhader chart =
    ingest.buildExpanderChart(igvPositions, ucGrid, etaGrid, headDropGrid);
boolean ok = ingest.validateExpanderChart(chart, 0.02); // ±2% on anchors
```

### 5. Mechanical / Seal-Gas Envelope — `TurboExpanderSealGasEnvelope`

Converts a thermodynamically feasible operating point into a mechanically
allowable one by checking three independent limits:

- **Axial thrust** vs differential pressure and thrust-bearing capacity
  (with balance-piston offload).
- **Seal-gas heater duty** against the installed heater rating
  (default tuned to FE-25832 = 28 kW, 30 °C set-point).
- **Critical-speed margin** between operating speed and the first critical
  speed.

$$
\text{margin}_{crit} = \frac{N - N_{crit,1}}{N_{crit,1}}, \qquad
Q_{seal} = 2\,\dot{m}_{seal}\,c_p\,\Delta T
$$

```java
TurboExpanderSealGasEnvelope env = new TurboExpanderSealGasEnvelope(turboExpanderComp);
env.setThrustAreas(0.0140, 0.0150);
boolean allowable = env.evaluate(); // true if thrust, heater, and speed all OK
String json = env.toJson();
```

### 6. Multi-Variable Operating Envelope — `TurboExpanderOperatingEnvelope`

Sweeps inlet pressure × flow (extensible to composition) and produces grids of
**feasibility**, **surge margin**, **cold-end temperature**, and **hydrate
margin**, emitted via `toJson()` for the report generator.

```java
TurboExpanderOperatingEnvelope env = new TurboExpanderOperatingEnvelope(turboExpanderComp);
env.setGrid(
    new double[] {55.0, 60.95, 65.0},        // inlet pressures [bara]
    new double[] {300000.0, 400000.0, 456000.0}); // flow rates [kg/hr]
env.setSurgeQnLimit(0.6);
env.setSpeedLimits(1100.0, 8950.0);
env.run();
String json = env.toJson(); // feasibility / surgeMargin / coldEndT / hydrateMargin grids
```

> See `docs/development/TASK_LOG.md` (2026-06-17 entry) for the design
> rationale and the originating Oseberg turboexpander capability assessment.

## Closing the Capability Gaps vs Commercial Software

Three classes close the remaining honest gaps between NeqSim and dedicated
turbomachinery / dynamic-simulation tools. They are deliberately transparent and
fully tested rather than black-box, so the assumptions are inspectable.

### 7. Validated Dynamics — `AntiSurgeDynamicBenchmark`

HYSYS/UniSim Dynamics are the reference for compressor trip and anti-surge
tuning. This benchmark gives NeqSim a **reproducible, deterministic transient
case** that drives the production `AntiSurgeController` and a real
`ThrottlingValve` against a transparent first-order gas-path surrogate:

$$
m_{k+1} = m_k - \dot{d}\,\Delta t + a\,\frac{u_k}{100}\,\Delta t
$$

where $m$ is the distance to surge, $\dot{d}$ the disturbance rate, $a$ the
recycle authority and $u$ the valve opening. The closed loop must hold the
margin at or above zero through the flow-reduction transient; the open-loop
reference surges — proving the scenario is a genuine challenge and the
controller adds value. The surrogate is a tuning aid (not validated field
data), but it makes the controller's proportional kick, integral action,
anti-windup and valve actuation analytically checkable before commissioning.

```java
AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
benchmark.run(true);                          // controller active
boolean safe = benchmark.isSurgeAvoided();     // true: stayed out of surge
double minMargin = benchmark.getMinimumSurgeMargin();
double maxOpening = benchmark.getMaximumValveOpening();
double[] marginTrace = benchmark.getSurgeMarginTrace();

benchmark.run(false);                          // open-loop reference - surges
```

### 8. Reference Map Library — `TurboMachineryChartLibrary`

Commercial tools ship validated OEM curve libraries. This class ships a small,
versioned, **vendor-neutral reference-map library** so a user gets a
physically-reasonable, dimensionally-correct map by name without digitising one.
The maps are generic reference characteristics (not proprietary OEM data) and
are composition-aware via the Khader normalisation, so one map serves many
fluids. For fiscal or guarantee work the certified OEM curve is still required.

```java
TurboMachineryChartLibrary library = new TurboMachineryChartLibrary();
library.listCompressorCharts(); // [GENERIC_CENTRIFUGAL_3SPEED]
library.listExpanderCharts();   // [GENERIC_CRYO_EXPANDER, GEOMETRY_RADIAL_IFR]

// fluid must be TPflashed before querying a compressor map (sound speed needed)
new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash();
fluid.initThermoProperties();
CompressorChartKhader2015 cmap = library.getCompressorChart(
    TurboMachineryChartLibrary.GENERIC_CENTRIFUGAL_3SPEED, fluid, 0.3);

ExpanderChartKhader emap = library.getExpanderChart(
    TurboMachineryChartLibrary.GENERIC_CRYO_EXPANDER, referenceFluid);
```

### 9. Geometry-Based Map Generation — `RadialExpanderGeometryMap`

AxSTREAM / Concepts NREC generate maps from blade geometry. This class is a
**preliminary mean-line radial-inflow (IFR) turbine model** that builds an
`ExpanderChartKhader` from a small set of geometric inputs — useful for concept
screening or to seed a map when no OEM curve exists. It is not a blade-to-blade
or CFD design code. Working with the velocity ratio $\nu = U_2/c_0$ and the
rotor-inlet flow angle $\alpha_2$, the nominal (zero-incidence) velocity ratio is

$$
\nu_{opt} = \sqrt{1-R}\,\sin\alpha_2
$$

and the total-to-static efficiency follows a classic incidence + nozzle/rotor
loss accounting (Dixon & Hall; Whitfield & Baines) that produces the
characteristic efficiency peak near $\nu_{opt}\approx 0.7$.

```java
RadialExpanderGeometryMap gen = new RadialExpanderGeometryMap(0.424, 0.45, 0.45);
gen.setReferenceFluid(referenceFluid);
gen.setDesignHeadDropKjPerKg(45.0);
ExpanderChartKhader chart = gen.generateChart(
    new double[] {0.5, 0.75, 1.0},     // IGV positions
    new double[] {78.0, 74.0, 70.0});  // nozzle angle per IGV [deg]
double nuOpt = gen.nominalVelocityRatio(70.0);
```

> See `docs/development/TASK_LOG.md` (2026-06-17 "Closing the three turbomachinery
> capability gaps" entry) for the rationale, tuning, and test coverage.
