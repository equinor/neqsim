# Pipeline Mechanical Design - Mathematical Methods Reference

Complete mathematical reference for pipeline mechanical design calculations in NeqSim.

## Table of Contents

- [Constants and Parameters](#constants-and-parameters)
- [Wall Thickness Formulas](#wall-thickness-formulas)
- [Stress Analysis Formulas](#stress-analysis-formulas)
- [External Pressure and Buckling](#external-pressure-and-buckling)
- [Weight and Buoyancy Formulas](#weight-and-buoyancy-formulas)
- [Thermal Design Formulas](#thermal-design-formulas)
- [Structural Design Formulas](#structural-design-formulas)
- [Fatigue Analysis](#fatigue-analysis)
- [Cost Estimation Formulas](#cost-estimation-formulas)

---

## Constants and Parameters

### Physical Constants

| Symbol | Value | Unit | Description |
|--------|-------|------|-------------|
| $g$ | 9.81 | m/s² | Gravitational acceleration |
| $\rho_{sw}$ | 1025 | kg/m³ | Seawater density |
| $\rho_{steel}$ | 7850 | kg/m³ | Carbon steel density |
| $\rho_{conc}$ | 3040 | kg/m³ | Concrete coating density |
| $E$ | 207,000 | MPa | Young's modulus (steel) |
| $\nu$ | 0.3 | - | Poisson's ratio |
| $\alpha$ | 11.7×10⁻⁶ | 1/K | Thermal expansion coefficient |

### API 5L Material Grades

| Grade | $S_y$ (MPa) | $S_u$ (MPa) | Grade | $S_y$ (MPa) | $S_u$ (MPa) |
|-------|-------------|-------------|-------|-------------|-------------|
| A25 | 172 | 310 | X60 | 414 | 517 |
| B | 241 | 414 | X65 | 448 | 531 |
| X42 | 290 | 414 | X70 | 483 | 565 |
| X52 | 359 | 455 | X80 | 552 | 621 |

### Design Factors

| Code | Factor | Value | Condition |
|------|--------|-------|-----------|
| ASME B31.8 | $F$ | 0.72 | Class 1 (rural) |
| ASME B31.8 | $F$ | 0.60 | Class 2 (semi-developed) |
| ASME B31.8 | $F$ | 0.50 | Class 3 (developed) |
| ASME B31.8 | $F$ | 0.40 | Class 4 (high-density) |
| DNV-OS-F101 | $\gamma_m$ | 1.15 | Material factor |
| DNV-OS-F101 | $\gamma_{SC}$ | 0.96 | Low safety class |
| DNV-OS-F101 | $\gamma_{SC}$ | 1.04 | Medium safety class |
| DNV-OS-F101 | $\gamma_{SC}$ | 1.14 | High safety class |

---

## Wall Thickness Formulas

### ASME B31.8 - Gas Transmission Pipelines

**Minimum wall thickness (Barlow formula):**

$$t_{min} = \frac{P_d \cdot D}{2 \cdot S_y \cdot F \cdot E \cdot T}$$

| Symbol | Description | Typical Values |
|--------|-------------|----------------|
| $P_d$ | Design pressure | MPa |
| $D$ | Outside diameter | m |
| $S_y$ | SMYS | 290-827 MPa |
| $F$ | Design factor | 0.40-0.72 |
| $E$ | Joint factor | 1.0 (seamless) |
| $T$ | Temperature derating | 1.0 (<120°C) |

**Maximum Allowable Operating Pressure:**

$$MAOP = \frac{2 \cdot S_y \cdot t_{nom} \cdot F \cdot E \cdot T}{D}$$

**Test Pressure:**

$$P_{test} = 1.25 \times MAOP$$ (Class 1)

$$P_{test} = 1.40 \times MAOP$$ (Class 2-4)

### ASME B31.3 - Process Piping

**Minimum wall thickness:**

$$t_{min} = \frac{P_d \cdot D}{2 \cdot (S_a \cdot E + P_d \cdot Y)}$$

| Symbol | Description | Value |
|--------|-------------|-------|
| $S_a$ | Allowable stress | $S_y / 3$ |
| $Y$ | Coefficient | 0.4 (T ≤ 482°C) |

### ASME B31.4 - Liquid Pipelines

**Minimum wall thickness:**

$$t_{min} = \frac{P_d \cdot D}{2 \cdot S_y \cdot F \cdot E \cdot T}$$

Default design factor $F = 0.72$.

### DNV-OS-F101 - Submarine Pipelines

**Pressure containment wall thickness:**

$$t_1 = \frac{(P_{li} - P_e) \cdot (D - t_1)}{2 \cdot f_y \cdot \alpha_U / (\gamma_m \cdot \gamma_{SC})}$$

Iterative solution required.

**Design pressure:**

$$P_d = P_{inc} + \Delta P_{cont}$$

$$P_{inc} = \gamma_{inc} \cdot P_{mop}$$

| Symbol | Description |
|--------|-------------|
| $P_{li}$ | Local incidental pressure |
| $P_e$ | External pressure |
| $f_y$ | Yield strength |
| $\alpha_U$ | Material strength factor (0.96) |
| $\gamma_{inc}$ | Incidental factor (1.1) |

### Nominal Wall Thickness

After calculating $t_{min}$:

$$t_{nom} = \frac{t_{min} + t_{corr}}{f_{fab}}$$

| Symbol | Description | Typical Value |
|--------|-------------|---------------|
| $t_{corr}$ | Corrosion allowance | 3 mm |
| $f_{fab}$ | Fabrication tolerance | 0.875 (12.5%) |

---

## Stress Analysis Formulas

### Hoop Stress (Barlow's Equation)

$$\sigma_h = \frac{P \cdot D}{2 \cdot t}$$

or for internal diameter:

$$\sigma_h = \frac{P \cdot (D - 2t)}{2 \cdot t}$$

### Longitudinal Stress - Restrained Pipe

$$\sigma_L = \nu \cdot \sigma_h - E \cdot \alpha \cdot \Delta T + \sigma_{press}$$

$$\sigma_{press} = \frac{P \cdot D}{4 \cdot t}$$ (end-cap effect)

### Longitudinal Stress - Unrestrained Pipe

$$\sigma_L = \frac{P \cdot D}{4 \cdot t}$$

### Von Mises Equivalent Stress

**General form:**

$$\sigma_{vm} = \sqrt{\sigma_1^2 + \sigma_2^2 + \sigma_3^2 - \sigma_1\sigma_2 - \sigma_2\sigma_3 - \sigma_1\sigma_3 + 3(\tau_{12}^2 + \tau_{23}^2 + \tau_{13}^2)}$$

**For biaxial stress state (pipeline):**

$$\sigma_{vm} = \sqrt{\sigma_h^2 + \sigma_L^2 - \sigma_h \cdot \sigma_L}$$

### Allowable Stress

$$\sigma_{allow} = \eta \cdot S_y$$

| Code | $\eta$ |
|------|--------|
| ASME B31.8 | 0.72-0.90 |
| DNV-OS-F101 | 0.87 |

### Stress Utilization

$$U = \frac{\sigma_{vm}}{\sigma_{allow}}$$

Design is safe when $U < 1.0$.

---

## External Pressure and Buckling

### External Pressure at Depth

$$P_e = \rho_{sw} \cdot g \cdot h$$

Convert to MPa: $P_e = \frac{\rho_{sw} \cdot g \cdot h}{10^6}$

### Elastic Collapse Pressure (Timoshenko)

$$P_{el} = \frac{2 \cdot E}{1 - \nu^2} \cdot \left(\frac{t}{D}\right)^3$$

### Plastic Collapse Pressure

$$P_p = 2 \cdot f_y \cdot \frac{t}{D}$$

### Combined Collapse Pressure (DNV)

Solving the quartic equation:

$$(P_c^2 - P_{el}^2)(P_c - P_p) = P_c \cdot P_{el} \cdot P_p \cdot f_o$$

where $f_o$ is the ovality factor.

**Simplified approximation:**

$$P_c = \frac{P_{el} \cdot P_p}{\sqrt{P_{el}^2 + P_p^2}}$$

### Propagation Buckling Pressure

$$P_{pr} = 35 \cdot f_y \cdot \left(\frac{t}{D}\right)^{2.5}$$

### External Pressure Check

$$P_e \leq \frac{P_c}{\gamma_m \cdot \gamma_{SC}}$$

If $P_e > P_{pr}$, buckle arrestors required.

---

## Weight and Buoyancy Formulas

### Cross-Sectional Areas

**Steel cross-section:**

$$A_{steel} = \frac{\pi}{4} \left[ D^2 - (D - 2t)^2 \right] = \pi \cdot t \cdot (D - t)$$

**Internal cross-section:**

$$A_{int} = \frac{\pi}{4} (D - 2t)^2$$

**Coating cross-section:**

$$A_{coat} = \frac{\pi}{4} \left[ (D + 2t_{coat})^2 - D^2 \right]$$

**Concrete cross-section:**

$$A_{conc} = \frac{\pi}{4} \left[ (D + 2t_{coat} + 2t_{conc})^2 - (D + 2t_{coat})^2 \right]$$

### Weights per Unit Length

**Steel weight:**

$$w_{steel} = \rho_{steel} \cdot A_{steel}$$

**Coating weight:**

$$w_{coat} = \rho_{coat} \cdot A_{coat}$$

**Concrete weight:**

$$w_{conc} = \rho_{conc} \cdot A_{conc}$$

**Contents weight:**

$$w_{cont} = \rho_{fluid} \cdot A_{int}$$

**Total dry weight:**

$$w_{dry} = w_{steel} + w_{coat} + w_{conc}$$

### Displaced Volume per Unit Length

$$V_{disp} = \frac{\pi}{4} \cdot D_{total}^2$$

where $D_{total} = D + 2t_{coat} + 2t_{conc}$

### Submerged Weight

$$w_{sub} = w_{dry} + w_{cont} - \rho_{sw} \cdot g \cdot V_{disp}$$

- $w_{sub} > 0$: Pipeline sinks
- $w_{sub} < 0$: Pipeline is buoyant

### Required Concrete Thickness for Stability

Solve for $t_{conc}$:

$$w_{target} = w_{dry} + w_{cont} - \rho_{sw} \cdot g \cdot V_{disp}(t_{conc})$$

---

## Thermal Design Formulas

### Thermal Expansion

**Free expansion:**

$$\Delta L = \alpha \cdot L \cdot \Delta T$$

**Restrained thermal stress:**

$$\sigma_{thermal} = E \cdot \alpha \cdot \Delta T$$

### Overall Heat Transfer Coefficient

$$\frac{1}{U \cdot D_o} = \frac{1}{h_i \cdot D_i} + \sum_j \frac{\ln(D_{j+1}/D_j)}{2\pi k_j} + \frac{1}{h_o \cdot D_o}$$

| Layer | Thermal conductivity $k$ (W/m·K) |
|-------|----------------------------------|
| Steel | 50 |
| 3LPE | 0.4 |
| PUF | 0.025 |
| Concrete | 1.5 |

### Temperature Profile

$$T(x) = T_{ambient} + (T_{inlet} - T_{ambient}) \cdot e^{-\frac{U \cdot \pi \cdot D \cdot x}{\dot{m} \cdot c_p}}$$

### Required Insulation Thickness

Solve for $t_{ins}$:

$$T_{arrival} = T_{ambient} + (T_{inlet} - T_{ambient}) \cdot e^{-\frac{U(t_{ins}) \cdot \pi \cdot D \cdot L}{\dot{m} \cdot c_p}}$$

---

## Structural Design Formulas

### Moment of Inertia

$$I = \frac{\pi}{64} \left[ D^4 - (D - 2t)^4 \right]$$

### Support Spacing (Deflection-Based)

**Simply supported span:**

$$L = \left( \frac{384 \cdot E \cdot I \cdot \delta_{max}}{5 \cdot w} \right)^{0.25}$$

**Fixed ends:**

$$L = \left( \frac{384 \cdot E \cdot I \cdot \delta_{max}}{w} \right)^{0.25}$$

| Symbol | Description |
|--------|-------------|
| $\delta_{max}$ | Maximum allowable deflection |
| $w$ | Weight per unit length (N/m) |

### Expansion Loop Length

**U-loop:**

$$L_{loop} = \sqrt{\frac{3 \cdot E \cdot D \cdot \Delta L}{\sigma_{allow}}}$$

where $\Delta L = \alpha \cdot \Delta T \cdot L_{anchor}$

**Z-loop:** $L_{loop} = 1.2 \times$ U-loop result

**Omega loop:** $L_{loop} = 0.9 \times$ U-loop result

### Minimum Bend Radius

**Cold bend (API 5L):**

$$R_{min} = 18 \cdot D$$

**Hot bend:**

$$R_{min} = 5 \cdot D$$

**Induction bend:**

$$R_{min} = 3 \cdot D$$

### Natural Frequency (Simply Supported)

$$f_n = \frac{\pi}{2L^2} \sqrt{\frac{E \cdot I}{m_e}}$$

where $m_e$ = effective mass including added mass for subsea.

### Vortex Shedding Frequency

$$f_s = \frac{St \cdot V}{D_{total}}$$

Strouhal number $St \approx 0.2$ for cylinders.

### VIV Avoidance Criterion

$$f_n > 1.3 \cdot f_s$$

---

## Fatigue Analysis

### S-N Curve (DNV-RP-C203)

$$N = \frac{a}{S^m}$$

| Curve | $a$ | $m$ | Application |
|-------|-----|-----|-------------|
| B1 | $4.0 \times 10^{15}$ | 4.0 | Parent metal, good conditions |
| D | $10^{11.764}$ | 3.0 | Welded joints |
| E | $10^{11.610}$ | 3.0 | Butt welds |
| F | $10^{11.455}$ | 3.0 | Fillet welds |
| W3 | $10^{10.970}$ | 3.0 | Poor quality welds |

### Fatigue Life

$$\text{Life} = \frac{N}{\text{cycles per year}}$$

### Miner's Rule (Cumulative Damage)

$$D = \sum_i \frac{n_i}{N_i} \leq 1.0$$

where:
- $n_i$ = number of cycles at stress range $S_i$
- $N_i$ = allowable cycles at stress range $S_i$

---

## Cost Estimation Formulas

### Material Cost

$$C_{steel} = w_{steel} \cdot L \cdot P_{steel}$$

$$C_{coating} = A_{surface} \cdot L \cdot P_{coating}$$

where $A_{surface} = \pi \cdot D$ (external surface area per meter)

### Fabrication Cost

$$C_{welds} = N_{welds} \cdot P_{weld}$$

$$N_{joints} = \frac{L}{L_{joint}} + 1$$

$$N_{field welds} = N_{joints} - \frac{L}{L_{stalk}}$$

Typical pipe joint length: $L_{joint} = 12.2$ m (40 ft)

### Installation Cost

$$C_{install} = L \cdot R_{base} \cdot (1 + f_{depth})$$

| Method | $R_{base}$ ($/m) | $f_{depth}$ |
|--------|------------------|-------------|
| Onshore | 300 | $50 \times$ burial depth |
| S-lay | 800 | $2 \times$ water depth / 1000 |
| J-lay | 1200 | $3 \times$ water depth / 1000 |
| Reel-lay | 600 | $1.5 \times$ water depth / 1000 |

### Accessories Cost

$$C_{flanges} = N_{flanges} \cdot P_{flange}(class, size)$$

$$C_{valves} = N_{valves} \cdot P_{valve}(type, size)$$

### Total Project Cost

$$C_{direct} = C_{steel} + C_{coating} + C_{welds} + C_{install} + C_{accessories}$$

$$C_{indirect} = C_{direct} \cdot (f_{eng} + f_{test} + f_{conting})$$

$$C_{total} = C_{direct} + C_{indirect}$$

| Factor | Typical Value |
|--------|---------------|
| Engineering ($f_{eng}$) | 10% |
| Testing ($f_{test}$) | 5% |
| Contingency ($f_{conting}$) | 15% |

### Labor Hours

$$H_{total} = H_{welding} + H_{coating} + H_{install} + H_{testing}$$

$$H_{welding} = N_{welds} \cdot h_{weld}$$

where $h_{weld}$ = hours per weld (typically 4-8 hours depending on diameter).

---

## Unit Conversions

| From | To | Multiply by |
|------|----|----|
| bar | MPa | 0.1 |
| psi | MPa | 0.006895 |
| inch | m | 0.0254 |
| ft | m | 0.3048 |
| lb/ft | kg/m | 1.488 |
| $/ft | $/m | 3.281 |

---

## References

1. **ASME B31.3** - Process Piping (2022)
2. **ASME B31.4** - Pipeline Transportation Systems for Liquids and Slurries (2022)
3. **ASME B31.8** - Gas Transmission and Distribution Piping Systems (2022)
4. **DNV-OS-F101** - Submarine Pipeline Systems (2021)
5. **DNV-RP-C203** - Fatigue Design of Offshore Steel Structures (2021)
6. **API 5L** - Specification for Line Pipe (2018)
7. **ISO 13623** - Petroleum and Natural Gas Industries — Pipeline Transportation Systems (2017)
8. **Timoshenko, S.P.** - Theory of Elastic Stability (1961)
9. **Palmer, A.C. & King, R.A.** - Subsea Pipeline Engineering (2008)
