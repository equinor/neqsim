---
title: Density Models
description: "Density correction models in NeqSim: COSTALD (Hankinson-Thomson), Peneloux volume translation, Rackett equation. Covers liquid density for pure compounds, mixtures, TBP fractions, aqueous/polar systems (water, MEG, TEG, methanol). setLiquidDensityModel API."
keywords: "COSTALD, density, liquid density, Hankinson-Thomson, Peneloux, volume translation, Rackett, molar volume, specific gravity, TBP fraction, pseudo-component, aqueous, polar, water density, MEG, TEG, glycol, methanol, ethanol, setLiquidDensityModel, compressed liquid, characteristic volume, V-star"
---

# Density Models

This guide documents the density correction models available in NeqSim for improving volumetric predictions.

## Table of Contents
- [Overview](#overview)
- [Equation of State Density](#equation-of-state-density)
- [Volume Translation Methods](#volume-translation-methods)
  - [Peneloux Volume Shift](#peneloux-volume-shift)
  - [Component-Specific Corrections](#component-specific-corrections)
- [Liquid Density Correlations](#liquid-density-correlations)
  - [COSTALD](#costald)
  - [NASTALD (COSTALD with Polar Correction)](#nastald-costald-with-polar-correction)
  - [Rackett Equation](#rackett-equation)
- [Usage Examples](#usage-examples)
- [Model Selection Guide](#model-selection-guide)
- [API Reference](#api-reference)
- [References](#references)

---

## Overview

Density predictions from cubic equations of state (SRK, PR) often have systematic errors:
- **Liquid density:** Typically underpredicted by 5-15%
- **Vapor density:** Generally accurate
- **Critical region:** Poor accuracy

NeqSim provides volume translation and correlation-based methods to improve liquid density predictions.

**Basic density access:**
```java
fluid.init(3);  // Initialize with derivatives
fluid.initPhysicalProperties();

double density = fluid.getPhase(1).getDensity("kg/m3");  // Liquid phase
double molarVolume = fluid.getPhase(1).getMolarVolume();  // m³/mol
```

---

## Equation of State Density

### Direct EoS Calculation

Cubic equations of state calculate compressibility factor $Z$:

$$PV = ZnRT$$

Molar volume is then:
$$V_m = \frac{ZRT}{P}$$

Density:
$$\rho = \frac{PM_w}{ZRT}$$

**Issue with cubic EoS:**
At the critical point, $Z_c^{SRK} = 0.333$ and $Z_c^{PR} = 0.307$, while real hydrocarbons have $Z_c \approx 0.26$. This causes systematic liquid volume overprediction.

---

## Volume Translation Methods

### Peneloux Volume Shift

The Peneloux correction adds a constant shift to the EoS molar volume:

$$V_{corrected} = V_{EoS} - c$$

where $c$ is the volume shift parameter.

**Class:** `Peneloux`

**Mixture shift:**
$$c_{mix} = \sum_i x_i c_i$$

**Component shift correlation:**
$$c_i = 0.40768 \frac{RT_{c,i}}{P_{c,i}} \left( 0.29441 - Z_{RA,i} \right)$$

where $Z_{RA}$ is the Rackett compressibility factor (from COMP database).

**Setting shift parameters:**
```java
// Enable Peneloux correction (default for SRK)
fluid.setDensityModel("Peneloux");

// Or set component-specific shifts
fluid.getPhase(0).getComponent("methane").setVolumeCorrectionConst(0.0);
fluid.getPhase(1).getComponent("n-heptane").setVolumeCorrectionConst(-0.0105);
```

**Advantages:**
- Simple to implement
- Preserves vapor-liquid equilibrium
- Works for mixtures

**Limitations:**
- Temperature independent
- May not work well at extreme conditions
- Single parameter per component

---

### Component-Specific Corrections

NeqSim stores volume correction constants in the COMP database. For heavy hydrocarbons or polar compounds, these may need tuning.

**Accessing correction constants:**
```java
// Get current volume correction
double vc = fluid.getPhase(1).getComponent("n-decane").getVolumeCorrectionConst();

// Modify correction
fluid.getPhase(1).getComponent("n-decane").setVolumeCorrectionConst(-0.015);
```

**Temperature-dependent shift (Jhaveri-Youngren):**
Some systems require temperature-dependent corrections:

$$c(T) = c_0 + c_1 (T - T_{ref})$$

This is implemented in specific component models.

---

## Liquid Density Correlations

### COSTALD

The COSTALD (COrreSponding STAtes Liquid Density) method is a generalized
corresponding-states correlation for predicting liquid densities of pure
compounds and mixtures. It is equivalent to the implementation in commercial
simulators such as UniSim/HYSYS, Aspen Plus, and PRO/II.

**Class:** `Costald` (in `neqsim.physicalproperties.methods.liquidphysicalproperties.density`)

#### Method Overview

COSTALD has two parts:

1. **Saturated liquid volume** — Hankinson and Thomson (1979)
2. **Compressed liquid correction** — Aalto et al. (1996) modified Tait equation

The method automatically applies the compressed liquid correction when the
system pressure exceeds the estimated saturation pressure.

#### Quick Start (Java)

```java
// 1. Create fluid and run flash
SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
fluid.addComponent("methane", 70.0);
fluid.addComponent("n-hexane", 20.0);
fluid.addComponent("water", 10.0);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// 2. Switch liquid phases to COSTALD
fluid.setLiquidDensityModel("COSTALD");

// 3. Read density (applies to oil, liquid, and aqueous phases)
double oilDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();
double waterDensity = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
```

#### Quick Start (Python / Jupyter)

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(293.15, 50.0)
fluid.addComponent("methane", 70.0)
fluid.addComponent("n-hexane", 20.0)
fluid.addComponent("water", 10.0)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initPhysicalProperties()

fluid.setLiquidDensityModel("COSTALD")

oil_density = fluid.getPhase("oil").getPhysicalProperties().getDensity()
water_density = fluid.getPhase("aqueous").getPhysicalProperties().getDensity()
print(f"Oil:   {oil_density:.1f} kg/m3")
print(f"Water: {water_density:.1f} kg/m3")
```

#### API Methods

| Method | Level | Description |
|--------|-------|-------------|
| `fluid.setLiquidDensityModel("COSTALD")` | System | Applies COSTALD to all liquid/oil/aqueous phases at once |
| `fluid.setLiquidDensityModel("Peneloux")` | System | Switches back to the default Peneloux volume shift |
| `phase.getPhysicalProperties().setDensityModel("Costald")` | Phase | Applies COSTALD to a single phase |
| `component.setCostaldCharacteristicVolume(v)` | Component | Sets an explicit V\* value (cm³/mol) for fine-tuning |
| `component.getCostaldCharacteristicVolume()` | Component | Returns the explicit V\* if set, otherwise 0 |

**Important:** Call `setLiquidDensityModel()` *after* `initPhysicalProperties()`.
Once set, the density model persists for subsequent `calcDensity()` calls
until changed.

#### Saturated Liquid Volume (Hankinson-Thomson 1979)

The saturated liquid molar volume is:

$$V_s = V^* \cdot V_R^{(0)}(T_r) \cdot \left[1 - \omega \cdot V_R^{(\delta)}(T_r)\right]$$

where $T_r = T / T_{c,m}$ is the reduced temperature and $\omega$ is the
acentric factor.

The dimensionless volume functions are:

$$V_R^{(0)} = 1 - 1.52816\,\tau^{1/3} + 1.43907\,\tau^{2/3} - 0.81446\,\tau + 0.190454\,\tau^{4/3}$$

$$V_R^{(\delta)} = \frac{-0.296123 + 0.386914\,T_r - 0.0427258\,T_r^2 - 0.0480645\,T_r^3}{T_r - 1.00001}$$

where $\tau = 1 - T_r$.

#### Compressed Liquid Correction (Aalto et al. 1996)

When $P > P_{sat}$, a modified Tait correction shrinks the molar volume:

$$V = V_s^{sat} \cdot \frac{A + e^{(d - T_r)^B} \cdot (P_r - P_r^{sat})}{A + e \cdot (P_r - P_r^{sat})}$$

where:

$$A = a_0 + a_1 T_r + a_2 T_r^3 + a_3 T_r^6 + a_4 / T_r$$

$$B = b_0 + b_1 \cdot \omega$$

| Constant | Value |
|----------|-------|
| $a_0$ | -170.335 |
| $a_1$ | -28.578 |
| $a_2$ | 124.809 |
| $a_3$ | -55.5393 |
| $a_4$ | 130.01 |
| $b_0$ | 0.164813 |
| $b_1$ | -0.0914427 |
| $c$ | $e$ (Euler's number) |
| $d$ | 1.00588 |

The pseudocritical pressure is:

$$P_{c,m} = \frac{(0.291 - 0.080\,\omega_m) \cdot R \cdot T_{c,m}}{V^*_m}$$

The saturation pressure uses the Lee-Kesler correlation.

#### Mixing Rules

For mixtures, pseudocritical properties are calculated using the
Hankinson-Thomson (1979) mixing rules (Poling, Table 4-12):

**Characteristic volume** (quadratic):

$$V^*_m = \frac{1}{4}\left[\sum_i x_i V^*_i + 3\left(\sum_i x_i {V^*_i}^{2/3}\right)\left(\sum_i x_i {V^*_i}^{1/3}\right)\right]$$

**Acentric factor** (linear):

$$\omega_m = \sum_i x_i \omega_i$$

**Pseudocritical temperature:**

$$T_{c,m} = \frac{\left[\sum_i x_i \sqrt{T_{c,i} \cdot V^*_i}\right]^2}{V^*_m}$$

These are the same mixing rules used in all major commercial simulators.

#### Characteristic Volume (V\*) Estimation

The characteristic volume $V^*$ is the most important parameter for COSTALD
accuracy. It is **not** the same as the critical volume $V_c$ — for polar
compounds such as water or glycols, $V^*$ can be 10–20% lower than $V_c$.

NeqSim determines $V^*$ using a three-tier priority:

| Priority | Source | When Used |
|----------|--------|-----------|
| 1 | Explicit V\* (`setCostaldCharacteristicVolume`) | User has literature or fitted V\* value |
| 2 | Back-calculated from normal liquid density at 60 °F (288.71 K) | Component has `normalLiquidDensity > 0` and $T_r < 0.9$ at 288.71 K |
| 3 | Critical volume $V_c$ | Fallback when no density data is available |

The back-calculation (priority 2) solves:

$$V^* = \frac{M / \rho_{std}}{V_R^{(0)}(T_{r,std}) \cdot \left[1 - \omega \cdot V_R^{(\delta)}(T_{r,std})\right]}$$

where $\rho_{std}$ is the normal liquid density at 288.71 K from the component
database. This is the same approach used by UniSim/HYSYS and PRO/II. It
automatically handles:

- **Polar/associating compounds** (water, methanol, MEG, TEG, ethanol) —
  the fitted $V^*$ compensates for hydrogen bonding effects
- **TBP/plus fractions** (pseudo-components) — the user-specified density
  directly determines $V^*$
- **Database components** — any component in `COMP.csv` with a non-zero
  `LIQDENS` value

#### Supported Compound Types

| Compound Type | V\* Method | Example Components | Expected Accuracy |
|---------------|------------|-------------------|--------------------|
| Light hydrocarbons ($T_c < 320$ K) | Critical volume $V_c$ | methane, ethane, propane | 1–3% |
| Heavier hydrocarbons | V\* from density | n-hexane, n-decane, nC16, nC20 | 1–2% |
| Polar / associating | V\* from density | water, methanol, ethanol | 3–5% |
| Glycols | V\* from density | MEG, TEG, DEG | 3–5% |
| TBP fractions | V\* from density | C7, C10, C20 pseudo-components | 2–5% |
| Plus fractions (C20+) | V\* from density | Characterized heavy end | 3–8% |

#### Aqueous and Multi-Phase Systems

`setLiquidDensityModel("COSTALD")` applies to **all** liquid-type phases
simultaneously: `LIQUID`, `OIL`, and `AQUEOUS`. This means a single call
enables COSTALD for three-phase gas-oil-water systems.

```java
// Gas-oil-water with MEG inhibitor
SystemInterface fluid = new SystemSrkEos(303.15, 80.0);
fluid.addComponent("methane", 70.0);
fluid.addComponent("n-hexane", 5.0);
fluid.addComponent("water", 10.0);
fluid.addComponent("MEG", 7.0);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Enable COSTALD for both oil and aqueous phases
fluid.setLiquidDensityModel("COSTALD");

if (fluid.hasPhaseType("oil")) {
    double oilRho = fluid.getPhase("oil").getPhysicalProperties().getDensity();
}
if (fluid.hasPhaseType("aqueous")) {
    double aqRho = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
}
```

#### Oil Characterization (TBP / Plus Fractions)

COSTALD works directly with the NeqSim TBP characterization. Each
pseudo-component's $V^*$ is automatically estimated from its specified
density:

```java
SystemInterface fluid = new SystemSrkEos(313.15, 100.0);
fluid.addComponent("methane", 50.0);
fluid.addComponent("ethane", 10.0);
fluid.addTBPfraction("C7", 5.0, 95.0 / 1000.0, 0.738);
fluid.addTBPfraction("C10", 4.0, 134.0 / 1000.0, 0.792);
fluid.addPlusFraction("C20", 3.0, 350.0 / 1000.0, 0.895);
fluid.getCharacterization().setTBPModel("PedersenSRK");
fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
fluid.getCharacterization().characterisePlusFraction();
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// COSTALD will use the density-based V* for each TBP fraction
fluid.setLiquidDensityModel("COSTALD");
double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();
```

#### Tuning with Custom V\*

If you have published V\* values (from Hankinson-Thomson 1979 Table I, DIPPR,
or the API Technical Data Book), you can set them explicitly for better accuracy:

```java
// Set published V* for n-hexane (371.0 cm3/mol from H-T 1979)
fluid.getPhase("oil").getComponent("n-hexane").setCostaldCharacteristicVolume(371.0);

// Set V* for water (46.4 cm3/mol, significantly less than Vc = 56 cm3/mol)
fluid.getPhase("aqueous").getComponent("water").setCostaldCharacteristicVolume(46.4);
```

Explicit V\* values override the density-based estimation. To revert to
automatic estimation, set it back to 0:

```java
fluid.getPhase("oil").getComponent("n-hexane").setCostaldCharacteristicVolume(0.0);
```

#### Switching Back to Default

```java
// Switch back to Peneloux volume shift (default)
fluid.setLiquidDensityModel("Peneloux");

// Or set per-phase
fluid.getPhase("oil").getPhysicalProperties().setDensityModel("Peneloux volume shift");
```

#### Valid Range and Limitations

| Parameter | Valid Range | Notes |
|-----------|-------------|-------|
| Reduced temperature $T_r$ | 0.25 – 0.95 | Warns below 0.25; falls back to EOS above 1.0 |
| Pressure | Up to ~700 bar | Compressed liquid correction from Aalto (1996) |
| Component types | Hydrocarbons, polar, associating, pseudo-components | Best for hydrocarbons; 3–5% for polar |
| Phases | Liquid, oil, aqueous | Does not apply to gas or solid phases |

**Limitations:**

- Near the critical point ($T_r > 0.95$), accuracy degrades as with all
  corresponding-states correlations
- For components that are supercritical at 288.71 K (methane, ethane, propane,
  nitrogen, CO2), V\* falls back to $V_c$ — this is acceptable because these
  light components have $V^* \approx V_c$ within 1%
- Mixture accuracy depends on how well the pseudo-critical mixing rules
  represent the actual mixture — highly asymmetric systems (e.g., methane +
  heavy wax) may have 5–10% error

#### Comparison with Other Commercial Simulators

| Feature | NeqSim COSTALD | NeqSim NASTALD | UniSim/HYSYS | Aspen Plus | PRO/II |
|---------|----------------|----------------|-------------|------------|--------|
| Saturated volume (H-T 1979) | Yes | Yes | Yes | Yes | Yes |
| NBS polar correction (1982) | No | Yes | Yes | Yes | Yes |
| Compressed liquid | Aalto (1996) | Aalto (1996) | Thomson (1982) | Thomson (1982) | Thomson (1982) |
| V\* from density back-calc | Yes | Yes | Yes | DIPPR database | Yes |
| V\* explicit override | Yes | Yes | Yes | Yes | Yes |
| Polar compound support | Via V\* from density | Via V\* + polar term | Via V\* + polar | Via DIPPR V\* | Via V\* + polar |
| Aqueous phase support | Yes | Yes | Yes | Yes | Yes |
| TBP fraction support | Yes | Yes | Yes | Yes | Yes |
| Mixing rules | Standard H-T | Standard H-T | Standard H-T | Standard H-T | Standard H-T |

NeqSim uses the Aalto et al. (1996) modified Tait equation for compressed
liquid correction rather than the Thomson et al. (1982) original Tait form used
by most commercial simulators. The Aalto form works in reduced pressure units
for better numerical behavior at high pressures.

The NASTALD variant adds the Thomson-Brobst-Hankinson (1982) polar correction
term, which is the same approach used by UniSim/HYSYS, Aspen Plus, and PRO/II
for polar compounds.

#### References

1. Hankinson, R.W. and Thomson, G.H. (1979). "A New Correlation for Saturated
   Densities of Liquids and Their Mixtures." *AIChE J.* 25, 653–663.
2. Thomson, G.H., Brobst, K.R. and Hankinson, R.W. (1982). "An Improved
   Correlation for Densities of Compressed Liquids and Liquid Mixtures."
   *AIChE J.* 28, 671–676.
3. Aalto, M., Keskinen, K.I., Aittamaa, J. and Liukkonen, S. (1996).
   "An Improved Correlation for Compressed Liquid Densities of Hydrocarbons.
   Part 2. Mixtures." *Fluid Phase Equil.* 114, 1–19.
4. Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). *The Properties
   of Gases and Liquids*, 5th ed. McGraw-Hill, Chapters 4.
5. API Technical Data Book — Petroleum Refining, Chapter 6.

---

### NASTALD (COSTALD with Polar Correction)

NASTALD extends the standard COSTALD method by adding the Thomson-Brobst-Hankinson
(1982) polar correction term $V_R^{(p)}$. This improves accuracy for strongly
polar and hydrogen-bonding compounds such as water, alcohols, and glycols.

#### Equation

The saturated volume for a polar substance becomes:

$$V_s = V^* \left[ V_R^{(0)} \left(1 - \omega_{SRK} \, V_R^{(\delta)}\right) + \omega_p \, V_R^{(p)} \right]$$

where $\omega_p$ is the substance-specific polar parameter and $V_R^{(p)}$
is the polar correction function:

$$V_R^{(p)} = \frac{e + f \, T_r + g \, T_r^2 + h \, T_r^3}{T_r - 1.00001}$$

with the universal constants:

| Constant | Value |
|----------|-------|
| $e$ | -1.52816 |
| $f$ | 1.43907 |
| $g$ | -0.81446 |
| $h$ | 0.190454 |

For mixtures, the polar parameter is mixed via:

$$\omega_{p,mix} = \sum_i x_i \, \omega_{p,i}$$

#### Polar Parameters ($\omega_p$)

The following polar parameters are built into NeqSim:

| Compound | $\omega_p$ | Source |
|----------|-----------|--------|
| Water | 0.3478 | Thomson et al. (1982) |
| Ammonia | 0.2872 | Thomson et al. (1982) |
| HF | 0.3750 | Thomson et al. (1982) |
| Methanol | 0.1907 | Thomson et al. (1982) |
| Ethanol | 0.1471 | Thomson et al. (1982) |
| 1-propanol | 0.1100 | Thomson et al. (1982) |
| 2-propanol | 0.1050 | Thomson et al. (1982) |
| 1-butanol | 0.0922 | Thomson et al. (1982) |
| Acetic acid | 0.1530 | Thomson et al. (1982) |
| Acetone | 0.0547 | Thomson et al. (1982) |
| MEG | 0.2213 | Estimated |
| DEG | 0.2000 | Estimated |
| TEG | 0.1800 | Estimated |

For compounds not in this table, $\omega_p = 0$ and NASTALD reduces to
standard COSTALD.

#### Important Caveat: V\* Back-Calculation

When V\* is back-calculated from the component's `normalLiquidDensity`
(which is the default for most components), the polarity is already partially
captured in the V\* value. Adding the polar correction on top can lead to
over-correction (typically 5-15% too high for alcohols and glycols).

The NASTALD polar correction is most beneficial when:
- V\* comes from an external database (e.g., DIPPR) rather than density back-calculation
- The component's `costaldCharacteristicVolume` has been explicitly set

For most practical purposes, standard COSTALD with V\* from density gives
excellent results for polar compounds without needing the explicit polar term.

#### Usage

```java
// Enable NASTALD (COSTALD with polar correction) for all liquid phases
fluid.setLiquidDensityModel("NASTALD");

// Run flash and get density
ops.TPflash();
fluid.initPhysicalProperties();
double rho = fluid.getPhase("aqueous").getDensity("kg/m3");
```

#### References

1. Thomson, G.H., Brobst, K.R. and Hankinson, R.W. (1982). "An Improved
   Correlation for Densities of Compressed Liquids and Liquid Mixtures."
   *AIChE J.* 28, 671–676.

---

### Rackett Equation

The Spencer-Danner (1972) modified Rackett equation provides a simple
corresponding-states method for saturated liquid density. It requires only
critical properties and the Rackett compressibility factor $Z_{RA}$.

#### Pure Component Equation

$$V_s = \frac{R T_c}{P_c} \; Z_{RA}^{\left[1 + (1 - T_r)^{2/7}\right]}$$

where $T_r = T / T_c$ is the reduced temperature and $Z_{RA}$ is the Rackett
compressibility factor (an empirically fitted parameter, not the true critical
compressibility $Z_c$).

#### $Z_{RA}$ Selection Priority

1. **Database value** — `component.getRacketZ()` from the NeqSim
   component database (fitted to experimental data)
2. **Yamada-Gunn estimate** (1973) — If no database value is available:

$$Z_{RA} = 0.29056 - 0.08775 \, \omega$$

where $\omega$ is the acentric factor.

#### Mixture Rules (Li, 1971)

For mixtures, pseudo-critical properties are calculated via mole-fraction
weighted mixing:

$$V_{c,mix} = \sum_i x_i V_{c,i}, \quad
   T_{c,mix} = \frac{\sum_i x_i V_{c,i} T_{c,i}}{V_{c,mix}}, \quad
   Z_{RA,mix} = \sum_i x_i Z_{RA,i}$$

$$P_{c,mix} = \frac{Z_{RA,mix} \, R \, T_{c,mix}}{V_{c,mix}}$$

The mixture saturated volume is then:

$$V_{s,mix} = \frac{R \, T_{c,mix}}{P_{c,mix}} \; Z_{RA,mix}^{\left[1 + (1 - T_{r,mix})^{2/7}\right]}$$

#### Usage

```java
// Enable Rackett density model for all liquid phases
fluid.setLiquidDensityModel("Rackett");

// Run flash and get density
ops.TPflash();
fluid.initPhysicalProperties();
double rho = fluid.getPhase("oil").getDensity("kg/m3");

// Access Z_RA for a component
double Zra = fluid.getPhase(1).getComponent("n-pentane").getRacketZ();
```

#### Strengths and Limitations

- **Strengths:** Simple, fast, good accuracy (2-5%) for non-polar hydrocarbons
  and their mixtures.
- **Limitations:** No compressed liquid correction (saturated volume only);
  less accurate for strongly polar or associating compounds. For polar systems,
  use COSTALD or NASTALD instead.

#### References

1. Rackett, H.G. (1970). "Equation of State for Saturated Liquids."
   *J. Chem. Eng. Data* 15, 514–517.
2. Spencer, C.F. and Danner, R.P. (1972). "Improved Equation for Prediction
   of Saturated Liquid Density." *J. Chem. Eng. Data* 17, 236–241.
3. Li, C.C. (1971). "Critical Temperature Estimation for Simple Mixtures."
   *Can. J. Chem. Eng.* 49, 709–710.
4. Yamada, T. and Gunn, R.D. (1973). "Saturated Liquid Molar Volumes. The
   Rackett Equation." *J. Chem. Eng. Data* 18, 234–236.

---

## Usage Examples

### Comparing Density Models

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.1);
fluid.addComponent("n-pentane", 0.9);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Default density (Peneloux volume shift)
double densityPeneloux = fluid.getPhase("oil").getPhysicalProperties().getDensity();
System.out.println("Peneloux: " + densityPeneloux + " kg/m3");

// Switch to COSTALD
fluid.setLiquidDensityModel("COSTALD");
double densityCostald = fluid.getPhase("oil").getPhysicalProperties().getDensity();
System.out.println("COSTALD:  " + densityCostald + " kg/m3");

// Switch to Rackett
fluid.setLiquidDensityModel("Rackett");
double densityRackett = fluid.getPhase("oil").getPhysicalProperties().getDensity();
System.out.println("Rackett:  " + densityRackett + " kg/m3");

// Switch back to Peneloux
fluid.setLiquidDensityModel("Peneloux");
double densityBack = fluid.getPhase("oil").getPhysicalProperties().getDensity();
System.out.println("Peneloux: " + densityBack + " kg/m3");
```

### Comparing with NASTALD for Polar Systems

```java
SystemInterface fluid = new SystemSrkEos(293.15, 1.01325);
fluid.addComponent("water", 0.8);
fluid.addComponent("methanol", 0.2);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// COSTALD (V* captures polarity via back-calculation)
fluid.setLiquidDensityModel("COSTALD");
double rhoCostald = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
System.out.println("COSTALD:  " + rhoCostald + " kg/m3");

// NASTALD (explicit polar correction)
fluid.setLiquidDensityModel("NASTALD");
double rhoNastald = fluid.getPhase("aqueous").getPhysicalProperties().getDensity();
System.out.println("NASTALD:  " + rhoNastald + " kg/m3");
```

### Tuning Liquid Density

```java
// Create fluid with known experimental density
SystemInterface fluid = new SystemSrkEos(293.15, 1.01325);
fluid.addComponent("n-hexane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

double expDensity = 659.0;  // kg/m³ at 20°C
double calcDensity = fluid.getPhase(1).getDensity("kg/m3");
double error = (calcDensity - expDensity) / expDensity * 100;
System.out.println("Initial error: " + error + "%");

// Adjust volume correction to match experimental
double molarMass = fluid.getPhase(1).getMolarMass() * 1000;  // kg/kmol
double calcMolarVolume = molarMass / calcDensity;  // m³/kmol
double expMolarVolume = molarMass / expDensity;    // m³/kmol
double correction = (calcMolarVolume - expMolarVolume) / 1000;  // m³/mol

fluid.getPhase(1).getComponent("n-hexane").setVolumeCorrectionConst(correction);
fluid.initPhysicalProperties();

double newDensity = fluid.getPhase(1).getDensity("kg/m3");
System.out.println("Tuned density: " + newDensity + " kg/m³");
```

### Density vs Temperature

```java
SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
fluid.addComponent("n-heptane", 1.0);
fluid.setMixingRule("classic");

double[] temps = {280, 300, 320, 340, 360, 380};

for (double T : temps) {
    fluid.setTemperature(T, "K");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    if (fluid.getPhase(1).getPhaseTypeName().equals("oil")) {
        fluid.initPhysicalProperties();
        double rho = fluid.getPhase(1).getDensity("kg/m3");
        System.out.println("T=" + T + " K: ρ=" + rho + " kg/m³");
    }
}
```

### High-Pressure Density

```java
// Compressed liquid density at high pressure
SystemInterface fluid = new SystemSrkEos(300.0, 500.0);  // 500 bar
fluid.addComponent("n-decane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

double rho = fluid.getPhase(0).getDensity("kg/m3");
System.out.println("High-P density: " + rho + " kg/m³");

// For high-pressure liquids, Peneloux may be insufficient
// Consider using PC-SAFT or adjusting correction
```

---

## Model Selection Guide

| Situation | Recommended Model | Notes |
|-----------|------------------|-------|
| General hydrocarbons | Peneloux | Default, good accuracy |
| Near saturation | COSTALD | Better for saturated liquids |
| Polar compounds (water, glycols) | COSTALD | V\* from density handles polarity |
| Polar with database V\* | NASTALD | Adds explicit polar correction term |
| Multi-phase (gas-oil-water) | COSTALD | Single call applies to oil + aqueous |
| Different model per phase | Per-phase API | e.g. COSTALD on oil, Peneloux on aqueous |
| TBP/plus fractions | COSTALD | V\* from density input, no tuning needed |
| High pressure (> 200 bar) | COSTALD | Aalto compressed liquid correction |
| Quick simple estimate | Rackett | No high-pressure correction |
| Saturated hydrocarbons only | Rackett | Fast, 2–5% accuracy |
| Critical region | GERG-2008 | If available |
| Quick estimate | EoS only | 5–15% error typical |

### Expected Accuracy

| Method | Liquid Density Error | Vapor Density Error |
|--------|---------------------|---------------------|
| SRK (no correction) | 5–15% | 1–3% |
| SRK + Peneloux | 1–3% | 1–3% |
| PR (no correction) | 3–10% | 1–3% |
| PR + Peneloux | 1–3% | 1–3% |
| COSTALD (hydrocarbons) | 1–2% | N/A |
| COSTALD (polar/aqueous) | 3–5% | N/A |
| COSTALD (TBP fractions) | 2–5% | N/A |
| NASTALD (polar with database V\*) | 1–3% | N/A |
| Rackett (hydrocarbons) | 2–5% | N/A |
| GERG-2008 | 0.1–0.5% | 0.1–0.5% |

---

## API Reference

### Setting Density Model

```java
// Set COSTALD for all liquid phases (oil, liquid, aqueous)
fluid.setLiquidDensityModel("COSTALD");

// Set NASTALD (polar-corrected COSTALD) for all liquid phases
fluid.setLiquidDensityModel("NASTALD");

// Set Rackett equation for all liquid phases
fluid.setLiquidDensityModel("Rackett");

// Switch back to default Peneloux
fluid.setLiquidDensityModel("Peneloux");

// Set model for a specific phase type only
fluid.setLiquidDensityModel("COSTALD", "oil");      // Oil phase uses COSTALD
fluid.setLiquidDensityModel("Peneloux", "aqueous");  // Aqueous keeps Peneloux

// Valid phase type names: "oil", "aqueous", "liquid"

// Low-level: set per-phase directly
fluid.getPhase("oil").getPhysicalProperties().setDensityModel("Costald");
fluid.getPhase("aqueous").getPhysicalProperties().setDensityModel("Rackett");
```

### Accepted Model Strings

| `setLiquidDensityModel(...)` | `setDensityModel(...)` | Description |
|------------------------------|------------------------|-------------|
| `"COSTALD"` | `"Costald"` | Standard COSTALD (Hankinson-Thomson) |
| `"NASTALD"` or `"COSTALD-polar"` | `"Costald polar"` | COSTALD with polar correction |
| `"Rackett"` | `"Rackett"` | Spencer-Danner modified Rackett |
| `"Peneloux"` | `"Peneloux volume shift"` | Default Peneloux volume translation |

### Accessing Density

```java
// Mass density
double rhoMass = phase.getDensity("kg/m3");
double rhoMass2 = phase.getDensity("lb/ft3");

// Molar density
double rhoMolar = phase.getDensity("mol/m3");

// Molar volume
double Vm = phase.getMolarVolume();  // m³/mol
```

### Density at Reference Conditions

```java
// Calculate density at standard conditions without modifying the fluid
double rhoStd = fluid.getDensityAtReferenceConditions(15.0, "C", 1.01325, "bara");
// Returns density in kg/m3 at 15°C / 1.01325 bara

// API standard temperature (60°F)
double rhoAPI = fluid.getDensityAtReferenceConditions(15.56, "C", 1.01325, "bara");

// The original fluid state is NOT modified by this call
```

### Volume Correction Parameters

```java
// Get/set volume correction constant
double c = component.getVolumeCorrectionConst();
component.setVolumeCorrectionConst(newValue);

// Get Rackett parameter
double Zra = component.getRacketZ();

// Get/set COSTALD characteristic volume (V*)
double Vstar = component.getCostaldCharacteristicVolume();
component.setCostaldCharacteristicVolume(newValue);  // cm³/mol
```

---

## References

1. Peneloux, A., Rauzy, E., Freze, R. (1982). A Consistent Correction for Redlich-Kwong-Soave Volumes. *Fluid Phase Equilib.* 8, 7–23.
2. Hankinson, R.W. and Thomson, G.H. (1979). A New Correlation for Saturated Densities of Liquids and Their Mixtures. *AIChE J.* 25, 653–663.
3. Thomson, G.H., Brobst, K.R. and Hankinson, R.W. (1982). An Improved Correlation for Densities of Compressed Liquids and Liquid Mixtures. *AIChE J.* 28, 671–676.
4. Aalto, M., Keskinen, K.I., Aittamaa, J. and Liukkonen, S. (1996). An Improved Correlation for Compressed Liquid Densities of Hydrocarbons. Part 2. Mixtures. *Fluid Phase Equil.* 114, 1–19.
5. Rackett, H.G. (1970). Equation of State for Saturated Liquids. *J. Chem. Eng. Data* 15, 514–517.
6. Spencer, C.F. and Danner, R.P. (1972). Improved Equation for Prediction of Saturated Liquid Density. *J. Chem. Eng. Data* 17, 236–241.
7. Yamada, T. and Gunn, R.D. (1973). Saturated Liquid Molar Volumes. The Rackett Equation. *J. Chem. Eng. Data* 18, 234–236.
8. Li, C.C. (1971). Critical Temperature Estimation for Simple Mixtures. *Can. J. Chem. Eng.* 49, 709–710.
9. Jhaveri, B.S. and Youngren, G.K. (1988). Three-Parameter Modification of the Peng-Robinson Equation of State. *SPE Reservoir Eng.* 3, 1033–1040.
10. Poling, B.E., Prausnitz, J.M. and O'Connell, J.P. (2001). *The Properties of Gases and Liquids*, 5th ed. McGraw-Hill.
