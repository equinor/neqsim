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
