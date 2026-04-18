# CO$_2$ and Acid Gas Systems

<!-- Chapter metadata -->
<!-- Notebooks: 01_co2_water_vle.ipynb, 02_co2_phase_behavior.ipynb, 03_ccs_pipeline.ipynb -->
<!-- Estimated pages: 20 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Model CO$_2$–water phase behavior using CPA with solvation
2. Predict the effect of impurities on CO$_2$ phase boundaries
3. Apply CPA to CCS pipeline design and injection well analysis
4. Model H$_2$S–water–hydrocarbon systems for sour gas processing
5. Evaluate CPA accuracy for acid gas systems against experimental data

## 11.1 The Growing Importance of CO$_2$ Modeling

Carbon capture and storage (CCS) has emerged as one of the most important industrial applications for accurate CO$_2$ thermodynamic modeling. The entire CCS value chain — capture, compression, transport, and injection — requires reliable predictions of:

- CO$_2$ phase behavior (dense phase, two-phase, supercritical)
- Water solubility in CO$_2$ (to prevent free water formation in pipelines)
- CO$_2$ solubility in water/brine (for storage capacity estimation)
- Effect of impurities (N$_2$, O$_2$, H$_2$, Ar, H$_2$S, SO$_2$) on phase boundaries
- Transport properties (density, viscosity) for pipeline hydraulics

CPA is uniquely suited for these applications because CO$_2$ forms Lewis acid–base interactions with water — a type of solvation that is naturally modeled by CPA's cross-association framework.

## 11.2 CO$_2$–Water Phase Behavior

### 11.2.1 Experimental Observations

The CO$_2$–water system is one of the most extensively studied binary systems in thermodynamics. Key features include:

**CO$_2$ solubility in water:**
- Increases with pressure up to about 100–200 bar, then levels off
- Decreases with temperature above ~100°C at moderate pressures
- Shows a maximum around 80–100°C at high pressures (> 200 bar)
- Is reduced by dissolved salts (salting-out effect)

**Water solubility in CO$_2$:**
- Very low (< 1 mol%) at conditions relevant for CCS
- Shows a minimum as a function of pressure at constant temperature
- The minimum corresponds to the transition from gas-like to liquid-like CO$_2$

### 11.2.2 The Solvation Approach in CPA

CO$_2$ does not self-associate — it has no proton donor sites. However, it acts as a Lewis acid and can accept hydrogen bonds from water. In CPA, this is modeled by assigning an electron acceptor site to CO$_2$ that can interact with water's proton donor sites:

$$\Delta^{A_{\text{H}_2\text{O}} B_{\text{CO}_2}} = g(\rho) \left[\exp\left(\frac{\varepsilon^{A_{\text{H}_2\text{O}} B_{\text{CO}_2}}}{RT}\right) - 1\right] b_{ij} \beta^{A_{\text{H}_2\text{O}} B_{\text{CO}_2}}$$

The solvation parameters ($\varepsilon_{\text{cross}}$, $\beta_{\text{cross}}$) are fitted to CO$_2$–water mutual solubility data.

### 11.2.3 CPA Predictions vs. Experimental Data

```python
from neqsim import jneqsim
import numpy as np

# CO2-water VLE at various pressures
temperatures = [298.15, 323.15, 348.15, 373.15]
pressures = np.arange(10, 210, 10)

for T in temperatures:
    print(f"\nT = {T - 273.15:.0f} C:")
    for P in [50, 100, 150, 200]:
        fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(T, float(P))
        fluid.addComponent("CO2", 0.5)
        fluid.addComponent("water", 0.5)
        fluid.setMixingRule(10)
        fluid.setMultiPhaseCheck(True)

        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.initProperties()

        if fluid.getNumberOfPhases() >= 2:
            # CO2-rich phase
            co2_phase = fluid.getPhase(0)
            aq_phase = fluid.getPhase(1)
            x_co2_aq = aq_phase.getComponent("CO2").getx()
            y_w_co2 = co2_phase.getComponent("water").getx()
            print(f"  P={P} bar: x_CO2 in water = {x_co2_aq:.4f}, "
                  f"x_water in CO2 = {y_w_co2:.6f}")
```

### 11.2.4 Accuracy Assessment

| T (°C) | P (bar) | CPA x$_{\text{CO}_2}$ in water | Exp. x$_{\text{CO}_2}$ in water | AAD (%) |
|---------|---------|-------------------------------|--------------------------------|---------|
| 25 | 50 | 0.0210 | 0.0220 | 4.5 |
| 25 | 100 | 0.0260 | 0.0270 | 3.7 |
| 50 | 50 | 0.0165 | 0.0170 | 2.9 |
| 50 | 100 | 0.0215 | 0.0225 | 4.4 |
| 75 | 100 | 0.0180 | 0.0190 | 5.3 |
| 100 | 200 | 0.0210 | 0.0220 | 4.5 |

*Table 11.1: CPA predictions of CO$_2$ solubility in water (representative values).*

CPA typically achieves 3–7% accuracy for CO$_2$ solubility in water, compared to 15–30% for SRK with a temperature-independent $k_{ij}$.

## 11.3 Effect of Impurities on CO$_2$ Phase Behavior

### 11.3.1 Why Impurities Matter

Industrial CO$_2$ streams from capture processes are never pure. Depending on the capture technology and source:

| Source | Typical Impurities | Total Impurity Level |
|--------|-------------------|---------------------|
| Post-combustion | N$_2$, O$_2$, Ar, H$_2$O | 0.1–3 mol% |
| Pre-combustion | H$_2$, CO, CH$_4$, H$_2$S | 1–5 mol% |
| Oxy-fuel | O$_2$, N$_2$, Ar, SO$_2$ | 2–8 mol% |
| Natural sources | CH$_4$, N$_2$, H$_2$S | 0.5–5 mol% |

*Table 11.2: Typical impurities in industrial CO$_2$ streams.*

Even small amounts of impurities significantly affect:

- **Bubble and dew point pressures**: shifted by 5–50 bar depending on impurity type and concentration
- **Saturation temperature**: shifted by 5–30°C
- **Two-phase region**: expanded, requiring higher pipeline operating pressures
- **Density**: reduced in the dense phase, affecting pipeline capacity
- **Critical point**: shifted to higher pressures and lower temperatures

### 11.3.2 CPA Predictions for Impure CO$_2$

```python
from neqsim import jneqsim

# Phase envelope of CO2 with impurities
# Pure CO2
fluid_pure = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 25, 80.0)
fluid_pure.addComponent("CO2", 1.0)
fluid_pure.setMixingRule(10)

# CO2 with 3% N2
fluid_n2 = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 25, 80.0)
fluid_n2.addComponent("CO2", 0.97)
fluid_n2.addComponent("nitrogen", 0.03)
fluid_n2.setMixingRule(10)

# CO2 with 2% H2
fluid_h2 = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 25, 80.0)
fluid_h2.addComponent("CO2", 0.98)
fluid_h2.addComponent("hydrogen", 0.02)
fluid_h2.setMixingRule(10)

for fluid, label in [(fluid_pure, "Pure CO2"),
                     (fluid_n2, "CO2 + 3% N2"),
                     (fluid_h2, "CO2 + 2% H2")]:
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()
    rho = fluid.getDensity("kg/m3")
    print(f"{label}: density = {rho:.1f} kg/m3 at 25 C, 80 bar")
```

### 11.3.3 Impact on Pipeline Design

For CCS pipeline design, the minimum operating pressure must keep the CO$_2$ in single-phase (dense phase) throughout the pipeline. Impurities raise this minimum:

- Pure CO$_2$: critical pressure = 73.8 bar → minimum operating pressure ≈ 85 bar
- CO$_2$ + 2% N$_2$: saturation pressure ≈ 85 bar at 10°C → minimum operating ≈ 95 bar
- CO$_2$ + 5% N$_2$: saturation pressure ≈ 95 bar at 10°C → minimum operating ≈ 105 bar

CPA provides the phase boundary predictions needed to set these operating pressures correctly.

## 11.4 CCS Pipeline Design

### 11.4.1 Dense Phase Transport

CCS pipelines operate in the "dense phase" — supercritical or compressed liquid CO$_2$ — to maximize transport capacity. The pipeline must be designed to prevent two-phase flow, which causes:

- Increased pressure drop (two-phase friction factors 5–20× higher)
- Liquid slugging and pipeline vibration
- Corrosion acceleration at the gas-liquid interface
- Flow measurement difficulties at custody transfer points

### 11.4.2 Water Specification

A critical specification for CCS pipelines is the water content of the CO$_2$ stream. Free water in the presence of CO$_2$ creates carbonic acid, which is highly corrosive to carbon steel:

$$\text{CO}_2 + \text{H}_2\text{O} \rightleftharpoons \text{H}_2\text{CO}_3 \rightleftharpoons \text{H}^+ + \text{HCO}_3^-$$

Corrosion rates can exceed 10 mm/year for wet CO$_2$ on carbon steel, compared to < 0.1 mm/year for dry CO$_2$. The industry typically specifies water content below 50–500 ppm(mol), depending on operating conditions and material selection.

CPA predicts the water saturation point of CO$_2$ at pipeline conditions:

```python
from neqsim import jneqsim

# Water content at saturation for CO2 pipeline
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(278.15, 110.0)
fluid.addComponent("CO2", 0.999)
fluid.addComponent("water", 0.001)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.waterDewPointTemperatureFlash()
T_wdp = fluid.getTemperature("C")
print(f"Water dew point of CO2 with 1000 ppm water: {T_wdp:.1f} C at 110 bar")
```

### 11.4.3 Pipeline Hydraulics with CPA

For pipeline hydraulic calculations, CPA provides:

- **Density** as a function of T, P, and composition: directly affects pipeline capacity
- **Phase boundary**: determines the minimum operating pressure
- **Joule–Thomson coefficient**: determines temperature changes through pressure reduction (valves, terrain effects)

```python
from neqsim import jneqsim

# CO2 properties for pipeline design
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(278.15, 150.0)
fluid.addComponent("CO2", 0.96)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("methane", 0.01)
fluid.addComponent("water", 0.0001)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

rho = fluid.getDensity("kg/m3")
mu = fluid.getPhase(0).getViscosity("kg/msec")
cp = fluid.getPhase(0).getCp("J/molK")

print(f"Pipeline conditions (5 C, 150 bar):")
print(f"  Density: {rho:.1f} kg/m3")
print(f"  Viscosity: {mu*1000:.4f} mPa.s")
print(f"  Cp: {cp:.1f} J/(mol K)")
```

## 11.5 CO$_2$ Injection Well Analysis

### 11.5.1 Wellbore Flow

CO$_2$ injection wells present unique thermodynamic challenges:

- **Phase transitions**: CO$_2$ may transition from dense/supercritical at the wellhead to liquid or gas downhole, depending on the pressure and temperature profile
- **Joule–Thomson cooling**: pressure loss through the tubing causes cooling, potentially leading to two-phase flow
- **Hydrate risk**: if water is present, hydrates can form in the wellbore
- **Formation compatibility**: the injected CO$_2$ must be compatible with the formation fluids

### 11.5.2 CO$_2$ Phase in the Wellbore

The pressure increases downhole (hydrostatic head), while the temperature increases (geothermal gradient). Whether the CO$_2$ remains in single phase depends on:

- Wellhead pressure and temperature
- Well depth and geothermal gradient
- CO$_2$ composition (impurities widen the two-phase region)
- Injection rate (affects frictional pressure drop and heat transfer)

CPA, integrated with wellbore hydraulic models in NeqSim, can track the phase state of CO$_2$ throughout the wellbore.

### 11.5.3 Shutdown and Restart

During a well shutdown, the CO$_2$ column cools toward the geothermal gradient temperature. This can cause:

- Phase transition from dense to two-phase at shallow depths
- Density inversion (heavier fluid above lighter fluid) causing instability
- Hydrate formation if water is present

Restart requires careful pressure management to avoid rapid decompression and two-phase flow.

## 11.6 H$_2$S–Water–Hydrocarbon Systems

### 11.6.1 H$_2$S Solvation

Like CO$_2$, H$_2$S interacts with water through hydrogen bonding. However, H$_2$S can act as both a proton donor and a proton acceptor:

- **Proton donor**: the SH group can donate hydrogen bonds to water's oxygen
- **Proton acceptor**: the sulfur lone pairs can accept hydrogen bonds from water

This dual character makes H$_2$S modeling more challenging than CO$_2$. In CPA, H$_2$S is typically modeled with:

- **3B scheme**: two acceptor sites (sulfur lone pairs) + one donor site (SH)
- **Solvation**: cross-association parameters with water

### 11.6.2 Sour Gas Water Content

Sour gas (containing H$_2$S and CO$_2$) has higher water content than sweet gas at the same conditions. This is because both acid gases enhance the water-carrying capacity of the gas through solvation interactions. The effect is additive at low acid gas concentrations but non-linear at high concentrations.

CPA captures this effect naturally through the cross-association terms.

### 11.6.3 CPA Predictions for Sour Systems

```python
from neqsim import jneqsim

# Sour gas water content comparison
# Sweet gas
fluid_sweet = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 70.0)
fluid_sweet.addComponent("methane", 0.95)
fluid_sweet.addComponent("water", 0.05)
fluid_sweet.setMixingRule(10)
fluid_sweet.setMultiPhaseCheck(True)

# Sour gas with CO2
fluid_sour = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 70.0)
fluid_sour.addComponent("methane", 0.85)
fluid_sour.addComponent("CO2", 0.10)
fluid_sour.addComponent("water", 0.05)
fluid_sour.setMixingRule(10)
fluid_sour.setMultiPhaseCheck(True)

for fluid, label in [(fluid_sweet, "Sweet gas"), (fluid_sour, "Sour gas (10% CO2)")]:
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    if fluid.hasPhaseType("gas"):
        y_water = fluid.getPhase("gas").getComponent("water").getx()
        print(f"{label}: water in gas = {y_water:.6f} ({y_water*1e6:.0f} ppm)")
```

## 11.7 CO$_2$–Brine Systems

### 11.7.1 Importance for Storage Capacity

CO$_2$ geological storage involves injection into saline aquifers or depleted hydrocarbon reservoirs. The solubility of CO$_2$ in formation brine determines:

- **Solubility trapping capacity**: CO$_2$ dissolved in brine is effectively permanently stored
- **Injectivity**: dissolved CO$_2$ reduces brine viscosity and changes wettability
- **Pressure evolution**: dissolved CO$_2$ changes the compressibility of the brine

### 11.7.2 The Salting-Out Effect

Dissolved salts (NaCl, CaCl$_2$, KCl) reduce the solubility of CO$_2$ in water through the salting-out effect. This can reduce CO$_2$ solubility by 20–50% for typical formation brines (50,000–250,000 mg/L TDS).

CPA, when combined with an electrolyte model, can predict the salting-out effect:

```python
from neqsim import jneqsim

# CO2 solubility in brine (electrolyte CPA)
fluid = jneqsim.thermo.system.SystemElectrolyteCPAstatoil(323.15, 100.0)
fluid.addComponent("CO2", 0.1)
fluid.addComponent("water", 0.8)
fluid.addComponent("Na+", 0.05)
fluid.addComponent("Cl-", 0.05)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

x_co2 = fluid.getPhase("aqueous").getComponent("CO2").getx()
print(f"CO2 solubility in brine: {x_co2:.4f} mol frac")
```

## 11.8 Transport Properties of CO$_2$ Mixtures

### 11.8.1 Viscosity Prediction

Accurate viscosity prediction is essential for pipeline sizing and pressure drop calculations. The viscosity of dense CO$_2$ varies dramatically with pressure and temperature:

- Near the critical point: viscosity changes by factors of 2–5 over small pressure ranges
- In the dense/liquid phase: viscosity is 0.05–0.10 mPa·s (much lower than water)
- Impurities affect viscosity: N$_2$ reduces it, H$_2$O increases it

NeqSim uses the Lohrenz–Bray–Clark (LBC) correlation modified for CO$_2$-dominant systems:

$$(\mu - \mu^*)\xi + 10^{-4} = \sum_{i=0}^{4} a_i \rho_r^i$$

where $\mu^*$ is the dilute gas viscosity, $\xi$ is the viscosity reducing parameter ($T_c^{1/6} / (M^{1/2} P_c^{2/3})$), and $\rho_r = \rho/\rho_c$ is the reduced density. The reduced density is computed from CPA, ensuring consistency between thermodynamic and transport predictions.

### 11.8.2 Thermal Conductivity

The thermal conductivity of CO$_2$ is needed for heat transfer calculations in pipelines (soil–pipe heat exchange) and injection wells (wellbore–formation heat exchange). Like viscosity, it varies strongly near the critical point. NeqSim uses corresponding-states correlations with the density from CPA:

$$\lambda = \lambda_0(T) + \Delta\lambda(\rho) + \Delta\lambda_c$$

where $\lambda_0$ is the dilute gas contribution, $\Delta\lambda(\rho)$ is the residual (density-dependent) contribution, and $\Delta\lambda_c$ is the critical enhancement (significant within ±10% of the critical point).

### 11.8.3 Joule–Thomson Coefficient

The Joule–Thomson (JT) coefficient determines the temperature change during isenthalpic expansion — critical for:

- **Pipeline depressurization**: how much the CO$_2$ cools when pressure drops (terrain effects, valves)
- **Well shut-in**: temperature changes during pressure equilibration
- **Choke valves**: temperature drop at the wellhead choke

The JT coefficient is defined as:

$$\mu_{JT} = \left(\frac{\partial T}{\partial P}\right)_H = \frac{1}{C_P}\left[T\left(\frac{\partial V}{\partial T}\right)_P - V\right]$$

For CO$_2$ near the critical point, $\mu_{JT}$ can be very large (5–20 K/bar), meaning small pressure drops cause significant cooling. In the dense liquid phase, $\mu_{JT}$ is smaller (0.5–2 K/bar) but still important over long pipelines.

CPA provides all the derivatives needed to compute $\mu_{JT}$ consistently, including the association contribution to the volume derivative:

$$\left(\frac{\partial V}{\partial T}\right)_P = -\frac{(\partial P/\partial T)_V}{(\partial P/\partial V)_T}$$

where both partial derivatives include the CPA association term contributions derived in Chapter 5.

```python
from neqsim import jneqsim

# JT coefficient of CO2 at pipeline conditions
for T_C in [5, 15, 25, 35]:
    fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + T_C, 110.0)
    fluid.addComponent("CO2", 0.97)
    fluid.addComponent("nitrogen", 0.02)
    fluid.addComponent("methane", 0.01)
    fluid.setMixingRule(10)

    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    jt = fluid.getPhase(0).getJouleThomsonCoefficient()
    rho = fluid.getDensity("kg/m3")
    print(f"T={T_C} C, P=110 bar: JT coeff = {jt:.3f} K/bar, rho = {rho:.1f} kg/m3")
```

## 11.9 Comparison with Other Models

### 11.9.1 SRK with Huron-Vidal Mixing Rules

An alternative to CPA for CO$_2$–water systems is SRK with Huron-Vidal (HV) or Modified Huron-Vidal (MHV2) mixing rules, which embed an activity coefficient model ($g^E$) inside the EoS. This approach can provide good accuracy for VLE but:

- Requires more adjustable parameters
- May not extrapolate well outside the fitting range
- Does not provide a physically consistent picture of the molecular interactions

### 11.9.2 SAFT Variants

PC-SAFT and SAFT-VR can also model CO$_2$–water systems. Compared to CPA:

- **PC-SAFT**: similar accuracy for VLE, but more parameters and higher computational cost
- **SAFT-VR**: good for modeling the critical region, but more complex to implement
- **SAFT-$\gamma$ Mie**: excellent accuracy and predictive capability, but significantly more complex

CPA offers the best balance of accuracy, simplicity, and computational efficiency for engineering applications.

### 11.9.3 Accuracy Summary

| Property | CPA AAD (%) | SRK AAD (%) | PC-SAFT AAD (%) |
|----------|-------------|-------------|-----------------|
| CO$_2$ in water (VLE) | 3–7 | 15–30 | 3–8 |
| Water in CO$_2$ (VLE) | 5–15 | 30–100 | 5–12 |
| CO$_2$ phase density | 1–3 | 3–8 | 1–3 |
| CO$_2$ saturation P | 1–2 | 1–3 | 0.5–1.5 |
| CO$_2$–brine solubility | 5–15 | 20–50 | 5–10 |

*Table 11.3: Accuracy comparison for CO$_2$ system properties.*

## 11.10 Worked Example: CO$_2$ Pipeline Design with CPA

This section presents a comprehensive worked example of CO$_2$ pipeline sizing using CPA, illustrating how the thermodynamic model integrates into pipeline engineering.

### 11.10.1 Design Basis

A 150 km onshore CO$_2$ pipeline must transport 5 million tonnes per year (Mt/yr) from a capture plant to a geological storage site. The CO$_2$ stream composition after purification is: CO$_2$ 96%, N$_2$ 2%, methane 1%, water 500 ppm (after dehydration), with trace amounts of H$_2$S and O$_2$.

| Parameter | Value | Unit |
|-----------|-------|------|
| Mass flow rate | 5 | Mt/yr = 158.5 kg/s |
| Inlet pressure | 150 | bar |
| Minimum pressure (arrival) | 80 | bar |
| Ground temperature | 8 | °C |
| Burial depth | 1.0 | m |
| Maximum allowable velocity | 5 | m/s |
| Pipeline material | X65 carbon steel | — |
| Design standard | DNV-ST-F101 | — |

*Table 11.4: CO$_2$ pipeline design basis.*

### 11.10.2 Phase Behavior Analysis

The first step is to confirm that the CO$_2$ remains in the dense phase throughout the pipeline. Using CPA to compute the phase envelope:

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 8.0, 150.0)
fluid.addComponent("CO2", 0.96)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("methane", 0.01)
fluid.addComponent("water", 0.0005)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

rho = fluid.getDensity("kg/m3")
cp = fluid.getCp("J/molK")
print(f"Dense phase density: {rho:.1f} kg/m3")
print(f"Heat capacity: {cp:.1f} J/(mol·K)")
```

The cricondenbar for this CO$_2$ mixture (with 2% N$_2$ and 1% CH$_4$) is approximately 77–82 bar. Since the minimum pipeline pressure is 80 bar, we must verify that the operating envelope stays above the phase boundary with adequate margin.

### 11.10.3 Density and Viscosity Profiles

CPA provides the density and viscosity along the pipeline, which vary with pressure and temperature. For CO$_2$ in the dense phase at pipeline conditions:

| Location | P (bar) | T (°C) | $\rho$ (kg/m$^3$) | $\mu$ (mPa·s) | Phase |
|----------|---------|--------|-------------------|---------------|-------|
| Inlet | 150 | 25 | 890 | 0.072 | Dense |
| 50 km | 130 | 15 | 905 | 0.082 | Dense |
| 100 km | 105 | 10 | 860 | 0.065 | Dense |
| 150 km (arrival) | 85 | 8 | 780 | 0.055 | Dense |

*Table 11.5: CPA-predicted properties along the CO$_2$ pipeline.*

The non-monotonic density behavior reflects the competing effects of decreasing pressure (which reduces density) and decreasing temperature (which increases density for a dense fluid).

### 11.10.4 Impact of Impurities

The presence of N$_2$ and CH$_4$ has a significant effect on the phase envelope and transport properties. CPA allows systematic evaluation:

| Composition | Cricondenbar (bar) | $\rho$ at 100 bar, 10°C (kg/m$^3$) | Phase risk |
|-------------|-------------------|--------------------------------------|-----------|
| Pure CO$_2$ | 73.8 (critical P) | 860 | Low |
| + 2% N$_2$ | 82 | 810 | Moderate |
| + 2% N$_2$ + 1% CH$_4$ | 85 | 790 | Higher |
| + 2% N$_2$ + 1% CH$_4$ + 1% H$_2$ | 95 | 750 | Significant |

*Table 11.6: Effect of impurities on CO$_2$ pipeline phase behavior.*

The key insight is that light impurities (N$_2$, H$_2$, CH$_4$) raise the cricondenbar, increasing the minimum operating pressure required to avoid two-phase flow. Hydrogen is particularly problematic because even 1% H$_2$ raises the cricondenbar by ~10 bar. This is critical for "blue hydrogen" CCS projects where the CO$_2$ stream may contain residual hydrogen.

## 11.11 CPA for Carbon Capture Solvents

### 11.11.1 CO$_2$ Solubility in Amine Solutions

While CPA does not model the chemical reaction between CO$_2$ and amines directly (this requires electrolyte or reactive models), it can model the physical solubility of CO$_2$ in amine solutions, which is important for:

- **Rich solvent CO$_2$ loading**: the total CO$_2$ uptake combines chemical and physical absorption
- **Flash regeneration**: at high temperatures, the chemical equilibrium shifts, and physical solubility becomes more important
- **Water wash sections**: physical VLE governs amine losses from the absorber

### 11.11.2 CO$_2$ in MEA Solutions

Monoethanolamine (MEA) is an associating molecule with both an amine group (NH$_2$) and a hydroxyl group (OH). CPA can model the MEA–water–CO$_2$ system by treating MEA with a 4C association scheme and using solvation parameters for the CO$_2$–MEA interaction.

The physical solubility of CO$_2$ in 30 wt% MEA at absorber conditions (40°C, 1–2 bar CO$_2$ partial pressure) is approximately 0.01–0.02 mol CO$_2$/mol MEA, compared to a total loading of 0.4–0.5 mol/mol when chemical reaction is included. While the physical contribution is only ~3–5% of the total, it becomes significant at high loadings and elevated pressures encountered in pressurized absorbers.

## Summary

Key points from this chapter:

- CO$_2$ systems are central to CCS, one of the most important new industrial applications
- CPA models CO$_2$–water solvation through cross-association with 3–7% accuracy
- Impurities significantly affect CO$_2$ phase boundaries and pipeline design pressures
- CCS pipeline design requires accurate dense-phase property predictions
- H$_2$S solvation with water is modeled similarly to CO$_2$ but requires additional association sites
- Electrolyte CPA extends predictions to brine systems for storage applications
- CPA provides the best balance of accuracy and simplicity for industrial CO$_2$ modeling

## Exercises

1. **Exercise 11.1:** Plot the CO$_2$ solubility in water from 10 to 200 bar at 25°C, 50°C, and 75°C using CPA. Compare with SRK predictions and identify where the models diverge most.

2. **Exercise 11.2:** Calculate the phase envelope of CO$_2$ containing 0%, 2%, and 5% nitrogen. Determine the minimum pipeline operating pressure to avoid two-phase flow at 5°C.

3. **Exercise 11.3:** Estimate the CO$_2$ solubility in a formation brine (3 wt% NaCl) at 50°C from 50 to 300 bar. Compare with pure water results to quantify the salting-out effect.

4. **Exercise 11.4:** For a sour gas containing 5% CO$_2$ and 3% H$_2$S, calculate the water content at 40°C and 70 bar. Compare with a sweet gas prediction and quantify the additional water load.

## References

<!-- Chapter-level references are merged into master refs.bib -->
