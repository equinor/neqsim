---
title: "Adsorption Isotherm Models"
description: "Complete mathematical reference for all adsorption isotherm models in NeqSim: Langmuir, Extended Langmuir, BET, Freundlich, Sips (Langmuir-Freundlich), and Dubinin-Radushkevich-Astakhov (DRA) potential theory. Covers equilibrium thermodynamics, temperature dependence, multi-component competition, and parameter estimation."
---

# Adsorption Isotherm Models

NeqSim provides six adsorption isotherm models for calculating gas–solid equilibrium on porous adsorbents. Each model captures different physics — from ideal monolayer coverage to heterogeneous surfaces and micropore filling — and can be selected via the `IsothermType` enum.

**Package:** `neqsim.physicalproperties.interfaceproperties.solidadsorption`

## Overview of Available Models

| Isotherm | Class | `IsothermType` | Multi-Component | Best For |
|----------|-------|----------------|-----------------|----------|
| Langmuir | `LangmuirAdsorption` | `LANGMUIR` | Single only | Monolayer, homogeneous surfaces |
| Extended Langmuir | `LangmuirAdsorption` | `EXTENDED_LANGMUIR` | Yes | Competitive monolayer adsorption |
| BET | `BETAdsorption` | `BET` | Per-component | Multilayer, surface area measurement |
| Freundlich | `FreundlichAdsorption` | `FREUNDLICH` | Per-component | Heterogeneous surfaces, moderate coverage |
| Sips | `SipsAdsorption` | `SIPS` | Yes (Extended) | Heterogeneous + saturation plateau |
| DRA Potential Theory | `PotentialTheoryAdsorption` | `DRA` | Yes | Micropore filling, rigorous EOS-based |

All models except DRA extend `AbstractAdsorptionModel` which provides common functionality for selectivity calculations, adsorbed-phase mole fractions, and parameter database loading.

---

## 1. Langmuir Isotherm

The Langmuir model assumes monolayer adsorption on a homogeneous surface with a finite number of identical sites. Once a site is occupied, no further adsorption occurs there.

### Single-Component Equation

$$
q = q_{max} \cdot \frac{K \cdot P}{1 + K \cdot P}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $q$ | Equilibrium loading (surface excess) | mol/kg |
| $q_{max}$ | Maximum monolayer capacity | mol/kg |
| $K$ | Langmuir equilibrium constant (affinity) | 1/bar |
| $P$ | Partial pressure of the adsorbate | bar |

### Temperature Dependence (van 't Hoff)

The equilibrium constant $K$ varies with temperature according to:

$$
K(T) = K(T_{ref}) \cdot \exp\left(\frac{-\Delta H_{ads}}{R} \cdot \left(\frac{1}{T} - \frac{1}{T_{ref}}\right)\right)
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $T_{ref}$ | Reference temperature | K |
| $\Delta H_{ads}$ | Isosteric heat of adsorption (negative for exothermic) | J/mol |
| $R$ | Universal gas constant ($8.314$) | J/(mol K) |

Since adsorption is exothermic ($\Delta H_{ads} < 0$), increasing temperature decreases $K$, reducing the equilibrium loading — the thermodynamic basis for Temperature Swing Adsorption (TSA).

### Fractional Coverage

The fractional surface coverage (dimensionless, 0 to 1) is:

$$
\theta = \frac{q}{q_{max}} = \frac{K \cdot P}{1 + K \cdot P}
$$

### Limiting Behaviour

- **Low pressure** ($K \cdot P \ll 1$): $q \approx q_{max} \cdot K \cdot P$ (Henry's law, linear)
- **High pressure** ($K \cdot P \gg 1$): $q \approx q_{max}$ (saturation plateau)

### Default Parameters

| Parameter | Default | Valid Range |
|-----------|---------|-------------|
| $q_{max}$ | 5.0 mol/kg | > 0 |
| $K$ | 0.1 1/bar | > 0 |
| $T_{ref}$ | 298.15 K | > 0 |
| $\Delta H_{ads}$ | −20,000 J/mol | typically −5,000 to −50,000 |

### Java API

```java
LangmuirAdsorption model = new LangmuirAdsorption(system);
model.setSolidMaterial("Zeolite 13X");

// Override parameters if needed
model.setQmax(0, 6.5);              // mol/kg for component 0
model.setKLangmuir(0, 0.8);         // 1/bar
model.setHeatOfAdsorption(0, -25000.0); // J/mol

model.calcAdsorption(0);            // phase 0
double loading = model.getSurfaceExcess(0);  // mol/kg
double coverage = model.getCoverage(0);      // 0-1
```

---

## 2. Extended Langmuir (Multi-Component)

When multiple gases compete for the same adsorption sites, the Extended Langmuir model accounts for competitive inhibition:

$$
q_i = q_{max,i} \cdot \frac{K_i \cdot P_i}{1 + \displaystyle\sum_{j=1}^{N} K_j \cdot P_j}
$$

The denominator sums over **all** species, so each component's loading is reduced by the presence of others. This is the standard model for multi-component adsorption in PSA simulation.

### Competitive Selectivity

The selectivity of component $i$ over component $j$ is:

$$
\alpha_{i/j} = \frac{q_i / y_i}{q_j / y_j} = \frac{q_{max,i} \cdot K_i}{q_{max,j} \cdot K_j}
$$

where $y_i$ is the gas-phase mole fraction.

### Java API

```java
LangmuirAdsorption model = new LangmuirAdsorption(system);
model.setSolidMaterial("Zeolite 13X");
model.calcExtendedLangmuir(0);  // multi-component calculation
double co2Loading = model.getSurfaceExcess("CO2");
double selectivity = model.getSelectivity(co2Index, ch4Index, 0);
```

---

## 3. BET (Brunauer-Emmett-Teller) Isotherm

The BET model extends Langmuir theory to allow **multilayer adsorption**. It is the standard method for measuring surface areas of porous materials.

### Standard BET Equation (Infinite Layers)

$$
q = \frac{q_m \cdot C \cdot x}{(1 - x)\left(1 - x + C \cdot x\right)}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $q_m$ | Monolayer capacity | mol/kg |
| $C$ | BET constant (energy parameter) | dimensionless |
| $x$ | Relative pressure $P / P_0$ | dimensionless |
| $P_0$ | Saturation (vapour) pressure at temperature $T$ | bar |

### Physical Meaning of the BET Constant

$$
C \approx \exp\left(\frac{E_1 - E_L}{RT}\right)
$$

where $E_1$ is the heat of adsorption of the first layer and $E_L$ is the heat of liquefaction. Large $C$ ($> 100$) indicates strong adsorbate–surface interaction; small $C$ ($< 20$) indicates weak interaction.

### Modified BET for Finite Layers

When adsorption is limited to $n$ layers (e.g., in mesopores):

$$
q = q_m \cdot C \cdot x \cdot \frac{1 - (n+1) x^n + n \cdot x^{n+1}}{(1 - x)\left(1 + (C - 1)x - C \cdot x^{n+1}\right)}
$$

For $n = 1$, this reduces to the Langmuir isotherm. For $n \to \infty$, it reduces to the standard BET equation.

### BET Surface Area Calculation

From the monolayer capacity:

$$
A_{BET} = q_m \cdot N_A \cdot \sigma
$$

where $N_A = 6.022 \times 10^{23}$ mol$^{-1}$ is Avogadro's number and $\sigma$ is the cross-sectional area of the adsorbate molecule (typically 0.162 nm$^2$ for N$_2$ at 77 K).

### Saturation Pressure Estimation

When experimental $P_0$ values are not available, NeqSim estimates them using the **Lee-Kesler** correlation (see [Fluid Property Estimation](#fluid-property-estimation) section below).

### Default Parameters

| Parameter | Default | Notes |
|-----------|---------|-------|
| $q_m$ | $\frac{A_{BET} \times 10^{-3}}{M \times 0.162}$ | Derived from BET surface area |
| $C$ | 100 | Moderate interaction |
| Max layers | Unlimited | Set via `setMaxLayers(n)` |
| $A_{BET}$ | 1000 m$^2$/g | From `AbstractAdsorptionModel` |

### Java API

```java
BETAdsorption model = new BETAdsorption(system);
model.setSolidMaterial("AC");
model.setMaxLayers(5);  // Limit to 5 adsorbed layers

model.calcAdsorption(0);
double loading = model.getSurfaceExcess(0);
double relativePressure = model.getRelativePressure(0, 0);
int layers = model.getNumberOfLayers(0);
double surfaceArea = model.calculateBETSurfaceArea(0, 0.162); // nm^2 cross-section
```

---

## 4. Freundlich Isotherm

The Freundlich model is an empirical isotherm for adsorption on **heterogeneous surfaces** with a distribution of adsorption energies. Unlike Langmuir, it does not predict a saturation plateau.

### Freundlich Equation

$$
q = K_F \cdot P^{1/n}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $K_F$ | Freundlich capacity constant | mol/kg/bar$^{1/n}$ |
| $n$ | Freundlich intensity parameter | dimensionless |
| $P$ | Partial pressure | bar |

### Interpretation of the Exponent $n$

- $n = 1$: Linear (Henry's law) — equal energy sites
- $n > 1$: **Favourable** adsorption — the isotherm is concave, loading increases steeply at low pressure
- $n < 1$: **Unfavourable** adsorption — convex isotherm

### Temperature Dependence

$$
K_F(T) = K_F(T_{ref}) \cdot \exp\left(\frac{-\Delta H_{ads}}{R} \cdot \left(\frac{1}{T} - \frac{1}{T_{ref}}\right)\right)
$$

### Limitations

The Freundlich isotherm:
- Does not obey Henry's law at very low pressure ($dq/dP \to \infty$ as $P \to 0$ for $n > 1$)
- Does not predict a saturation capacity at high pressure
- Best used in the **intermediate pressure range** where data is available

### Default Parameters

| Parameter | Default | Valid Range |
|-----------|---------|-------------|
| $K_F$ | 2.0 | > 0 |
| $n$ | 2.0 | > 0 (typically 1–10) |
| $T_{ref}$ | 298.15 K | > 0 |
| $\Delta H_{ads}$ | −20,000 J/mol | typically −5,000 to −50,000 |

### Java API

```java
FreundlichAdsorption model = new FreundlichAdsorption(system);
model.setSolidMaterial("AC BPL");

model.setKFreundlich(0, 3.5);
model.setNFreundlich(0, 2.5);

model.calcAdsorption(0);
double loading = model.getSurfaceExcess("CO2");
boolean favorable = model.isFavorableAdsorption(0); // true if n > 1
```

---

## 5. Sips (Langmuir-Freundlich) Isotherm

The Sips isotherm combines Langmuir saturation behaviour with Freundlich heterogeneity. It is the most versatile two-parameter extension and reduces to both Langmuir and Freundlich as special cases.

### Single-Component Sips Equation

$$
q = q_{max} \cdot \frac{(K_S \cdot P)^{1/n}}{1 + (K_S \cdot P)^{1/n}}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $q_{max}$ | Maximum adsorption capacity | mol/kg |
| $K_S$ | Sips affinity constant | 1/bar |
| $n$ | Heterogeneity parameter | dimensionless |

### Special Cases

| Condition | Reduces To |
|-----------|-----------|
| $n = 1$ | Langmuir isotherm |
| Low pressure ($K_S P \ll 1$) | Freundlich: $q \approx q_{max} \cdot (K_S P)^{1/n}$ |
| High pressure ($K_S P \gg 1$) | Saturation: $q \to q_{max}$ |

### Extended Sips (Multi-Component)

$$
q_i = q_{max,i} \cdot \frac{(K_{S,i} \cdot P_i)^{1/n_i}}{1 + \displaystyle\sum_{j=1}^{N} (K_{S,j} \cdot P_j)^{1/n_j}}
$$

### Temperature Dependence

$$
K_S(T) = K_S(T_{ref}) \cdot \exp\left(\frac{-\Delta H_{ads}}{R} \cdot \left(\frac{1}{T} - \frac{1}{T_{ref}}\right)\right)
$$

### Default Parameters

| Parameter | Default | Valid Range |
|-----------|---------|-------------|
| $q_{max}$ | 5.0 mol/kg | > 0 |
| $K_S$ | 0.1 1/bar | > 0 |
| $n$ | 1.2 | > 0 (typically 0.5–5) |
| $T_{ref}$ | 298.15 K | > 0 |
| $\Delta H_{ads}$ | −20,000 J/mol | typically −5,000 to −50,000 |

### Java API

```java
SipsAdsorption model = new SipsAdsorption(system);
model.setSolidMaterial("MOF HKUST-1");

model.setQmax(0, 8.0);
model.setKSips(0, 0.5);
model.setNSips(0, 1.5);

model.calcAdsorption(0);       // single-component
model.calcExtendedSips(0);     // multi-component competitive
double coverage = model.getCoverage(0);
```

---

## 6. Dubinin-Radushkevich-Astakhov (DRA) Potential Theory

The DRA model is NeqSim's most rigorous adsorption model. It uses Polanyi potential theory with full equation-of-state calculations to determine local composition and density within the adsorbent micropores.

### Theory

The adsorbent surface creates a potential field that enhances gas-phase fugacities. The adsorption potential at distance $z$ from the surface is:

$$
\varepsilon(z) = \varepsilon_0 \cdot \left[\ln\left(\frac{z_0}{z}\right)\right]^{1/\beta}
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $\varepsilon_0$ | Characteristic adsorption energy | kJ/mol |
| $z_0$ | Limiting adsorption volume (per kg adsorbent) | m$^3$/kg $\times 10^{-3}$ |
| $\beta$ | Structural heterogeneity exponent | dimensionless |
| $z$ | Integration variable (spatial coordinate) | same as $z_0$ |

### Fugacity Enhancement

Within the potential field, the fugacity of each component is enhanced:

$$
f_i^{field}(z) = f_i^{bulk} \cdot \exp\left(\frac{\varepsilon(z)}{RT}\right)
$$

This creates a thermodynamic driving force for molecules to accumulate near the adsorbent surface.

### Surface Excess Calculation

The total surface excess of component $i$ is obtained by numerical integration over the pore volume:

$$
q_i = \int_0^{z_0} \left(\frac{x_i^{local}(z)}{V_m^{local}(z)} - \frac{y_i^{bulk}}{V_m^{bulk}}\right) dz
$$

where $x_i^{local}$ and $V_m^{local}$ are the local mole fraction and molar volume at position $z$, determined by solving the full phase equilibrium at the enhanced fugacity conditions.

### Numerical Algorithm

The DRA implementation uses the following algorithm with 500 integration steps:

1. Clone the thermodynamic system
2. For each integration step $k$ from 1 to $N_{steps}$:
   - Compute point $z_k = z_0 \cdot k / N_{steps}$
   - Calculate the potential $\varepsilon(z_k)$
   - Compute the correction factor: $\text{corr} = \exp(\varepsilon / RT)$
   - Enhance fugacities: $f_i = \text{corr} \cdot f_i^{bulk}$
   - Iteratively solve for local composition (up to 100 inner iterations):
     - Update mole fractions from enhanced fugacity ratios
     - Adjust local pressure until $\sum x_i = 1$ (tolerance: $10^{-12}$)
   - Accumulate surface excess: $\Delta q_i = \Delta z \cdot (x_i / V_m^{local} - y_i / V_m^{bulk})$
3. Sum over all steps to get total $q_i$ for each component

### Key Characteristics

- **Thermodynamically rigorous**: Uses the full equation of state at each point
- **Inherently temperature-dependent**: No separate van 't Hoff correction needed
- **Multi-component**: Naturally handles mixtures through competitive fugacity equilibrium
- **Computationally expensive**: 500 integration steps × 100 inner iterations per component

### Parameters and Typical Values

| Parameter | Typical Range | Physical Basis |
|-----------|--------------|----------------|
| $\varepsilon_0$ | 0.05 – 16.0 kJ/mol | Interaction strength (surface energy) |
| $z_0$ | 0.1 – 0.45 m$^3$/kg $\times 10^{-3}$ | Micropore volume per unit mass |
| $\beta$ | 2.0 – 3.0 | $\beta = 2$: Gaussian energy distribution (DR); $\beta \neq 2$: general DA |

### Java API

```java
PotentialTheoryAdsorption model = new PotentialTheoryAdsorption(system);
model.setSolidMaterial("AC Calgon F400");
model.calcAdsorption(0);

double co2Loading = model.getSurfaceExcess("CO2");
double totalLoading = model.getTotalSurfaceExcess();
```

---

## Fluid Property Estimation

Several isotherm models (BET, capillary condensation) require pure-component physical properties that may not be directly available from the thermodynamic system. The `FluidPropertyEstimator` utility class provides correlations for these:

### Lee-Kesler Vapour Pressure

$$
\ln P_r^{sat} = f^{(0)}(T_r) + \omega \cdot f^{(1)}(T_r)
$$

$$
f^{(0)} = 5.92714 - \frac{6.09648}{T_r} - 1.28862 \ln T_r + 0.169347\, T_r^6
$$

$$
f^{(1)} = 15.2518 - \frac{15.6875}{T_r} - 13.4721 \ln T_r + 0.43577\, T_r^6
$$

where $T_r = T / T_c$ and $P_r = P / P_c$.

### Rackett Liquid Molar Volume

$$
V_m^{sat} = V_c \cdot Z_{RA}^{(1 - T_r)^{2/7}} \times 10^{-3}
$$

$$
Z_{RA} = 0.29056 - 0.08775 \omega
$$

### Macleod-Sugden Surface Tension

$$
\sigma = \left(\frac{[P] \cdot \rho_L}{10^6}\right)^4
$$

with a fallback correlation:

$$
\sigma = 0.02 \cdot (1 - T_r)^{1.26}
$$

where $[P]$ is the parachor.

---

## Parameter Database

All isotherm parameters are stored in [`AdsorptionParameters.csv`](https://github.com/equinor/neqsim/blob/master/src/main/resources/data/AdsorptionParameters.csv) and loaded automatically when `calcAdsorption()` is called.

### Database Schema

| Column | Description | Unit |
|--------|-------------|------|
| `ID` | Row identifier | — |
| `Name` | Component name (NeqSim convention) | — |
| `Solid` | Adsorbent material code | — |
| `eps` | DRA characteristic energy | kJ/mol |
| `z0` | DRA limiting adsorption volume | m$^3$/kg $\times 10^{-3}$ |
| `beta` | DRA structural heterogeneity | dimensionless |
| `qmax` | Langmuir/Sips maximum capacity | mol/kg |
| `K_langmuir` | Langmuir equilibrium constant | 1/bar |
| `C_BET` | BET constant | dimensionless |
| `TempRef` | Reference temperature | K |
| `dH_ads` | Heat of adsorption | J/mol |
| `K_freundlich` | Freundlich capacity constant | mol/kg/bar$^{1/n}$ |
| `n_freundlich` | Freundlich intensity parameter | dimensionless |
| `K_sips` | Sips affinity constant | 1/bar |
| `n_sips` | Sips heterogeneity parameter | dimensionless |

### Supported Adsorbent Materials (14)

| Material Code | Type | Typical Application |
|--------------|------|---------------------|
| `AC` | Activated Carbon (generic) | General gas separation |
| `AC Calgon F400` | Activated Carbon | VOC removal, gas purification |
| `AC Norit R1` | Activated Carbon | Air/gas purification |
| `AC BPL` | Activated Carbon | Gas-phase adsorption |
| `Zeolite 13X` | Faujasite zeolite | CO$_2$ removal, PSA air separation |
| `Zeolite 5A` | LTA zeolite | N$_2$/O$_2$ separation, gas drying |
| `Zeolite 4A` | LTA zeolite | Dehydration |
| `Zeolite ZSM-5` | MFI zeolite | Hydrocarbon separation |
| `Silica Gel` | Amorphous silica | Dehydration |
| `MOF HKUST-1` | Metal-Organic Framework | CO$_2$ capture, gas storage |
| `MOF-5` | Metal-Organic Framework | H$_2$ storage |
| `CMS` | Carbon Molecular Sieve | N$_2$/O$_2$ kinetic separation |
| `Alumina` | Activated alumina | Dehydration, fluoride removal |
| `MCM-41` | Mesoporous silica | Catalysis, controlled release |

### Supported Components (10)

methane, CO2, nitrogen, argon, ethane, propane, H2S, water, hydrogen, n-butane

### Fallback Parameters

When specific isotherm parameters are not available in the database, models fall back to DRA parameters with the following conversions:

| Isotherm | Fallback Expression |
|----------|-------------------|
| Langmuir $q_{max}$ | $z_0 \times 1000 / M$ |
| Langmuir $K$ | $\exp(\varepsilon_0 / (R \times 298.15))$ |
| Langmuir $\Delta H$ | $-\varepsilon_0 \times 1000$ |
| BET $q_m$ | $z_0 \times 1000 / M \times 0.4$ |
| BET $C$ | $\exp(\varepsilon_0 \times 1000 / (R \times T))$ |
| Freundlich $K_F$ | $z_0 \times 1000 / M \times 0.5$ |
| Freundlich $n$ | $1 + \varepsilon_0 / 5$ |
| Sips $q_{max}$ | $z_0 \times 1000 / M$ |
| Sips $K_S$ | $\exp(\varepsilon_0 / (R \times 298.15))$ |
| Sips $n$ | 1.2 |

---

## Capillary Condensation

For mesoporous materials (pore radius 2–50 nm), adsorption can trigger **capillary condensation** — the liquid fills the pore below the bulk saturation pressure. This is modelled separately via `CapillaryCondensationModel`.

### Kelvin Equation

The critical pore radius at which condensation occurs is:

$$
r_K = -\frac{g_f \cdot \gamma \cdot V_m^{liq} \cdot \cos\theta}{R \cdot T \cdot \ln(P / P_0)} \times 10^9 \quad (\text{nm})
$$

where:

| Symbol | Description | Unit |
|--------|-------------|------|
| $g_f$ | Geometry factor (2 for cylindrical/spherical, 1 for slit) | dimensionless |
| $\gamma$ | Surface tension of adsorbate | N/m |
| $V_m^{liq}$ | Liquid molar volume | m$^3$/mol |
| $\theta$ | Contact angle | radians |
| $P/P_0$ | Relative pressure | dimensionless |

### Condensation Onset Pressure

$$
\frac{P}{P_0} = \exp\left(-\frac{g_f \cdot \gamma \cdot V_m^{liq} \cdot \cos\theta}{R \cdot T \cdot r_{eff} \times 10^{-9}}\right)
$$

where $r_{eff} = r_{pore} - t_{ads}$ accounts for the pre-adsorbed layer thickness $t_{ads}$.

### Pore Size Distribution (Log-Normal)

$$
f(r) = \frac{1}{r \cdot \sigma_{ln} \cdot \sqrt{2\pi}} \cdot \exp\left(-\frac{(\ln r - \ln \bar{r})^2}{2\sigma_{ln}^2}\right)
$$

with $\sigma_{ln} = \ln(1 + \sigma_r / \bar{r})$.

The total condensed amount is integrated over pores where $P > P_{condensation}(r)$:

$$
n_{condensed} = \int_{r_{min}}^{r_{max}} \frac{V_{pore}(r)}{V_m^{liq}} \cdot f(r) \, dr \quad \text{(mol/g)}
$$

### Pore Geometry Types

| Pore Type | Geometry Factor $g_f$ | Description |
|-----------|----------------------|-------------|
| `CYLINDRICAL` | 2.0 | Most common, capillary-tube model |
| `SLIT` | 1.0 | Layered materials (clays, graphite) |
| `SPHERICAL` | 2.0 | Ink-bottle pores |
| `INK_BOTTLE` | 2.0 | Constricted-neck pores (hysteresis) |

### Default Parameters

| Parameter | Default | Unit |
|-----------|---------|------|
| Mean pore radius | 5.0 | nm |
| Pore radius std. dev. | 2.0 | nm |
| Min pore radius | 1.0 | nm |
| Max pore radius | 25.0 | nm |
| Total pore volume | 0.5 | cm$^3$/g |
| Contact angle | 0.0 | radians |
| Adsorbed layer thickness | 0.35 | nm |
| Integration steps | 100 | — |

### Java API

```java
CapillaryCondensationModel ccModel = new CapillaryCondensationModel(system);
ccModel.setMeanPoreRadius(4.0);
ccModel.setPoreRadiusStdDev(1.5);
ccModel.setTotalPoreVolume(0.6);
ccModel.setPoreType(CapillaryCondensationModel.PoreType.CYLINDRICAL);

ccModel.calcCapillaryCondensation(0);
double kelvinRadius = ccModel.getKelvinRadius("CO2");
double condensate = ccModel.getCondensateAmount("CO2");
```

---

## Model Selection Guide

### By Application

| Application | Recommended Model | Rationale |
|------------|-------------------|-----------|
| PSA process design | Extended Langmuir or Sips | Multi-component, fast computation |
| TSA process design | Langmuir + van 't Hoff | Temperature dependence built in |
| Material screening | Langmuir or Freundlich | Simple, fits most data |
| Surface area measurement | BET | Industry standard (ISO 9277) |
| Rigorous thermodynamic study | DRA | Full EOS treatment |
| Mesoporous materials | BET + Capillary Condensation | Multilayer + pore filling |
| Carbon molecular sieves | DRA | Micropore filling physics |
| MOFs and novel materials | Sips | Heterogeneity + saturation |

### By Data Availability

| Available Data | Recommended Model |
|---------------|-------------------|
| Only $q_{max}$ and $K$ | Langmuir |
| Heterogeneous isotherm shape | Sips or Freundlich |
| BET surface area measurements | BET |
| Full thermodynamic properties | DRA |
| No data (estimation needed) | Langmuir with database fallback |

---

## Related Documentation

- [Adsorption Bed Process Equipment](../process/equipment/adsorption_bed.md) — Transient bed simulation, PSA/TSA cycles
- [Adsorption Cookbook](../cookbook/adsorption-recipes.md) — Quick-start recipes and common workflows
- [Adsorption Review](../physical_properties/adsorption_review.md) — Original enhancement proposal
- [Physical Properties Overview](../physical_properties/) — Other property models (viscosity, density, etc.)
- [Thermodynamic Models](thermodynamic_models.md) — Equations of state used in DRA calculations
