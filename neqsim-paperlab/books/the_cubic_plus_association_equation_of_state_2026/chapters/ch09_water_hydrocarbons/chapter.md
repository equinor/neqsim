# Water–Hydrocarbon Systems

<!-- Chapter metadata -->
<!-- Notebooks: 01_water_solubility.ipynb, 02_gas_dehydration.ipynb, 03_lle_predictions.ipynb -->
<!-- Estimated pages: 18 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Predict mutual solubilities of water and hydrocarbons using CPA
2. Calculate the water content of natural gas at various conditions
3. Model liquid–liquid equilibrium in water–alkane systems
4. Assess CPA accuracy against experimental data for key industrial systems
5. Apply CPA for hydrate prediction boundary conditions

## 9.1 Industrial Importance of Water–Hydrocarbon Systems

Water is ubiquitous in oil and gas processing. Every reservoir fluid contains dissolved water, and every surface processing facility must handle water in multiple forms:

- **Dissolved water in gas**: determines dehydration requirements and hydrate risk
- **Free water in oil**: affects corrosion, emulsion stability, and water treatment design
- **Dissolved hydrocarbons in water**: governs produced water treatment and environmental discharge
- **Three-phase VLLE**: occurs in separators, pipelines, and processing equipment

The accuracy of thermodynamic predictions for water–hydrocarbon systems directly impacts:

- Pipeline design (hydrate prevention, corrosion allowance)
- Dehydration unit sizing (TEG contactor design, molecular sieve capacity)
- Separator sizing (water-in-oil specification, oil-in-water discharge)
- Environmental compliance (dissolved hydrocarbon limits in produced water)

Errors of 50–100% in water content prediction — common with classical cubic EoS — translate directly into oversized or undersized equipment, incorrect inhibitor dosing, and potential hydrate blockages.

## 9.2 Mutual Solubilities: Water–Alkane Systems

### 9.2.1 Thermodynamic Framework

The mutual solubility of water and hydrocarbons is governed by the equality of fugacities at equilibrium. For a two-liquid system (aqueous phase $\alpha$ and hydrocarbon phase $\beta$):

$$f_i^\alpha(T, P, \mathbf{x}^\alpha) = f_i^\beta(T, P, \mathbf{x}^\beta) \quad \text{for } i = 1, \ldots, C$$

For the water component, this can be written using activity coefficients:

$$x_w^\alpha \gamma_w^\alpha P_w^{\text{sat}} = x_w^\beta \gamma_w^\beta P_w^{\text{sat}}$$

which simplifies to:

$$x_w^\beta = x_w^\alpha \frac{\gamma_w^\alpha}{\gamma_w^\beta}$$

Since $x_w^\alpha \approx 1$ (aqueous phase is mostly water) and $\gamma_w^\alpha \approx 1$ (Raoult's law limit), the solubility of water in the hydrocarbon phase is approximately:

$$x_w^\beta \approx \frac{1}{\gamma_w^\beta}$$

The activity coefficient of water at infinite dilution in the hydrocarbon phase, $\gamma_w^{\beta,\infty}$, is very large (typically $10^2$–$10^3$), reflecting the enormous thermodynamic penalty of placing a hydrogen-bonding molecule in a non-polar environment. CPA computes this activity coefficient correctly through the association term, while classical cubic EoS underestimate $\gamma_w^{\beta,\infty}$ by factors of 2–10.

### 9.2.2 Experimental Behavior

The mutual solubility of water and alkanes exhibits several characteristic features:

**Solubility of water in alkanes:**
- Increases with temperature (endothermic dissolution)
- Relatively independent of alkane chain length for C$_5$+
- On the order of $10^{-3}$–$10^{-2}$ mole fraction at ambient conditions
- Shows a minimum around 70–100°C for some systems

**Solubility of alkanes in water:**
- Extremely small ($10^{-5}$–$10^{-8}$ mole fraction)
- Decreases strongly with alkane chain length
- Shows a minimum as a function of temperature (around 25°C for light alkanes)
- The minimum shifts to higher temperatures for heavier alkanes

### 9.2.3 Why Classical EoS Fail

Classical cubic EoS (SRK, PR) with a single $k_{ij}$ cannot simultaneously reproduce:

1. The solubility of water in the hydrocarbon phase
2. The solubility of hydrocarbon in the aqueous phase
3. The temperature dependence of both solubilities

The fundamental reason is that the aqueous phase is dominated by hydrogen bonding, which dramatically reduces the fugacity of water relative to what a non-associating model predicts. A classical EoS with a large positive $k_{ij}$ can match one solubility at one temperature but cannot capture the temperature dependence correctly.

### 9.2.4 CPA Predictions

CPA resolves these difficulties by explicitly modeling the hydrogen-bond network in the aqueous phase. The association term:

1. Reduces the fugacity of water in the aqueous phase (correctly modeling the hydrogen-bond stabilization)
2. Correctly predicts the temperature dependence (hydrogen bonds break at higher temperatures, increasing water fugacity)
3. Captures the chain-length dependence (larger alkanes are more incompatible with the aqueous hydrogen-bond network)

```python
from neqsim import jneqsim

# Water-hexane LLE at 25 C, 1 bar
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.01325)
fluid.addComponent("water", 0.5)
fluid.addComponent("n-hexane", 0.5)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    x_water = phase.getComponent("water").getx()
    x_hexane = phase.getComponent("n-hexane").getx()
    print(f"Phase {i} ({phase.getType()}): x_water={x_water:.6f}, x_hexane={x_hexane:.6f}")
```

### 9.2.5 Comparison with Experimental Data

| System | T (°C) | P (bar) | CPA x$_w$ in HC | Exp x$_w$ in HC | CPA x$_{\text{HC}}$ in aq | Exp x$_{\text{HC}}$ in aq |
|--------|--------|---------|-----------------|-----------------|---------------------------|---------------------------|
| Water–n-hexane | 25 | 1 | $2.0 \times 10^{-3}$ | $2.1 \times 10^{-3}$ | $1.5 \times 10^{-5}$ | $1.6 \times 10^{-5}$ |
| Water–n-octane | 25 | 1 | $2.3 \times 10^{-3}$ | $2.5 \times 10^{-3}$ | $1.0 \times 10^{-6}$ | $0.9 \times 10^{-6}$ |
| Water–n-decane | 25 | 1 | $2.5 \times 10^{-3}$ | $2.6 \times 10^{-3}$ | $3.5 \times 10^{-8}$ | $3.2 \times 10^{-8}$ |

*Table 9.1: CPA predictions vs. experimental mutual solubilities (representative values).*

CPA typically reproduces mutual solubilities within 10–30%, compared to errors of 100–1000% with SRK.

## 9.3 Water Content of Natural Gas

### 9.3.1 The Engineering Problem

The water content of natural gas (also called the water dew point or moisture content) is one of the most important parameters in gas processing. Sales gas specifications typically require water content below 7 lb/MMscf (approximately 112 mg/Sm$^3$) to prevent:

- Hydrate formation in pipelines
- Corrosion in the presence of CO$_2$ or H$_2$S
- Two-phase flow and slugging
- Ice formation at cryogenic conditions

### 9.3.2 McKetta–Wehe Chart and Its Limitations

The McKetta–Wehe chart has been the industry standard for estimating water content of sweet natural gas since the 1950s. It provides water content as a function of temperature and pressure for pure methane–water systems. Corrections are applied for gas composition (gravity correction) and sour gas content (Maddox correction).

However, the chart-based approach has significant limitations:

- Accuracy of $\pm 15$–$25$% even for sweet gas
- Gravity correction is approximate for rich gas (high C$_3$+ content)
- Sour gas corrections are unreliable at high H$_2$S or CO$_2$ concentrations
- No treatment of methanol or glycol in the gas phase

### 9.3.3 Thermodynamic Basis for Water Content Prediction

The water content of a gas in equilibrium with liquid water (or an aqueous solution) is determined by the fugacity balance:

$$y_w P \varphi_w^V = x_w \gamma_w f_w^L$$

where $y_w$ is the water mole fraction in the gas, $\varphi_w^V$ is the fugacity coefficient of water in the gas phase, $x_w$ is the water mole fraction in the aqueous phase, $\gamma_w$ is the activity coefficient of water, and $f_w^L$ is the fugacity of pure liquid water.

For saturated gas in contact with pure water ($x_w = 1$, $\gamma_w = 1$):

$$y_w = \frac{f_w^L(T, P)}{\varphi_w^V(T, P, \mathbf{y}) \cdot P}$$

The key quantities are:

1. **$f_w^L$**: the fugacity of pure liquid water, which depends on temperature and pressure. This is computed by CPA using the pure water parameters, and the Poynting correction for the pressure effect on the liquid:

$$f_w^L(T, P) = P_w^{\text{sat}}(T) \varphi_w^{\text{sat}}(T) \exp\left[\frac{V_w^L(P - P_w^{\text{sat}})}{RT}\right]$$

2. **$\varphi_w^V$**: the fugacity coefficient of water in the gas mixture. In CPA, this includes the association contribution — the solvation of water with CO$_2$ or H$_2$S in the gas phase, which increases $\varphi_w^V$ and thus increases $y_w$.

The improvement of CPA over SRK for water content prediction comes primarily from:
- Better $f_w^L$ (association stabilizes liquid water, correctly lowering its fugacity)
- Better $\varphi_w^V$ for sour gas (solvation with CO$_2$/H$_2$S captured by cross-association)

### 9.3.4 CPA Predictions of Water Content

CPA provides a rigorous, composition-dependent prediction of water content:

```python
from neqsim import jneqsim

# Natural gas water content at pipeline conditions
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 30, 70.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.07)
fluid.addComponent("propane", 0.03)
fluid.addComponent("n-butane", 0.01)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("nitrogen", 0.01)
fluid.addComponent("water", 0.01)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

# Water content in gas phase
gas_phase = fluid.getPhase("gas")
y_water = gas_phase.getComponent("water").getx()
print(f"Water mole fraction in gas: {y_water:.6f}")

# Convert to mg/Sm3
# y_water * P_std / (R * T_std) * M_water * 1e6
print(f"Approximate water content: {y_water * 1e6:.0f} ppm(mol)")
```

### 9.3.5 Effect of Gas Composition

CPA correctly predicts the effect of gas composition on water content:

- **CO$_2$ increases water content**: CO$_2$–water solvation makes the gas phase more hospitable for water molecules
- **H$_2$S increases water content**: similar solvation effect, even stronger than CO$_2$
- **Heavier hydrocarbons increase water content**: the gas becomes denser, accommodating more water
- **Nitrogen decreases water content**: N$_2$ is less hospitable to water than methane

These compositional effects are automatically captured by CPA through the association and solvation terms, without the need for empirical corrections.

### 9.3.6 Water Dew Point Calculations

The water dew point temperature at a given pressure is the temperature at which the first liquid water droplet forms upon cooling. In NeqSim:

```python
from neqsim import jneqsim

# Water dew point of a natural gas
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 30, 70.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.02)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("water", 0.01)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.waterDewPointTemperatureFlash()

T_dew = fluid.getTemperature("C")
print(f"Water dew point: {T_dew:.1f} C at {fluid.getPressure('bara'):.0f} bara")
```

## 9.4 Three-Phase Equilibrium: VLLE

### 9.4.1 Thermodynamic Conditions for VLLE

Three-phase vapor–liquid–liquid equilibrium (VLLE) requires the simultaneous satisfaction of fugacity equality across all three phases. For a system with $C$ components and phases $V$, $L_1$ (hydrocarbon liquid), and $L_2$ (aqueous liquid):

$$f_i^V(T, P, \mathbf{y}) = f_i^{L_1}(T, P, \mathbf{x}^{L_1}) = f_i^{L_2}(T, P, \mathbf{x}^{L_2}) \quad i = 1, \ldots, C$$

Together with the material balances:

$$z_i = \beta_V y_i + \beta_{L_1} x_i^{L_1} + \beta_{L_2} x_i^{L_2}$$

$$\beta_V + \beta_{L_1} + \beta_{L_2} = 1$$

where $\beta$ denotes the molar fraction of each phase. By the Gibbs phase rule, a three-phase system with $C$ components has $C - 1$ degrees of freedom. For a ternary system ($C = 3$), this gives 2 degrees of freedom — specifying $T$ and $P$ fully determines the equilibrium.

### 9.4.2 When Three Phases Coexist

For natural gas–water systems at moderate conditions (0–100°C, 10–200 bar), three phases can coexist:

1. **Vapor**: mainly hydrocarbons with dissolved water
2. **Hydrocarbon liquid**: condensate or LPG with dissolved water
3. **Aqueous liquid**: mainly water with dissolved hydrocarbons and CO$_2$

Three-phase equilibrium is particularly important in:

- **High-pressure separators** on offshore platforms
- **Pipeline conditions** near the hydrocarbon dew point
- **Glycol dehydration contactors** (vapor + glycol solution + hydrocarbon condensate)

### 9.4.3 CPA for VLLE Predictions

CPA handles three-phase equilibrium naturally — the same parameters and mixing rules that describe VLE and LLE are used for VLLE. The stability analysis identifies the three-phase region automatically:

```python
from neqsim import jneqsim

# Three-phase system: methane + n-pentane + water
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 30.0)
fluid.addComponent("methane", 0.5)
fluid.addComponent("n-pentane", 0.3)
fluid.addComponent("water", 0.2)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    print(f"\nPhase {i} ({phase.getType()}):")
    print(f"  Density: {phase.getDensity('kg/m3'):.1f} kg/m3")
    for comp in ["methane", "n-pentane", "water"]:
        print(f"  x_{comp}: {phase.getComponent(comp).getx():.6f}")
```

## 9.5 Gas Solubility in Water

### 9.5.1 Henry's Law and Its Extensions

The solubility of a gas in water at low pressures follows Henry's law:

$$f_i^V = H_i(T) x_i$$

where $H_i(T)$ is Henry's constant for species $i$ in water, which depends strongly on temperature. For an ideal gas at pressure $P$:

$$x_i = \frac{y_i P}{H_i(T)}$$

At higher pressures, deviations from Henry's law become significant, and the fugacity coefficient in the gas phase must be included:

$$x_i = \frac{y_i \varphi_i^V P}{H_i(T) \exp\left[\frac{\bar{V}_i^\infty(P - P_w^{\text{sat}})}{RT}\right]}$$

where $\bar{V}_i^\infty$ is the partial molar volume of gas $i$ at infinite dilution in water and the exponential is the Krichevsky correction for pressure.

### 9.5.2 CPA Approach vs. Henry's Law

CPA does not use Henry's law explicitly. Instead, the solubility of gas in water is computed directly from the fugacity equality:

$$\varphi_i^V(T, P, \mathbf{y}) \cdot y_i \cdot P = \varphi_i^{L,\text{aq}}(T, P, \mathbf{x}) \cdot x_i \cdot P$$

The fugacity coefficient in the aqueous phase $\varphi_i^{L,\text{aq}}$ includes contributions from:

1. The cubic EoS (repulsion and attraction): dominant for non-polar gases
2. The association term: important for solvating gases (CO$_2$, H$_2$S)

For CO$_2$ in water, the solvation contribution to the fugacity coefficient reduces $\varphi_{\text{CO}_2}^{L,\text{aq}}$, which increases the predicted solubility. This is why CPA with solvation (see Chapter 7) gives much better CO$_2$ solubility than SRK.

### 9.5.3 Temperature Dependence of Gas Solubility

Gas solubility in water shows a characteristic minimum near 80–100°C for many gases. This is because:

- At low temperatures, solubility decreases with increasing temperature (enthalpy-driven dissolution)
- At high temperatures, solubility increases with increasing temperature (entropy-driven, as water structure breaks down)

The relationship between Henry's constant and temperature follows:

$$\frac{d \ln H_i}{d(1/T)} = \frac{\Delta \bar{H}_i^{\text{sol}}}{R}$$

where $\Delta \bar{H}_i^{\text{sol}}$ is the partial molar enthalpy of solution. The sign change of $\Delta \bar{H}_i^{\text{sol}}$ produces the solubility minimum.

CPA captures this behavior through the temperature dependence of both the cubic and association contributions to the fugacity coefficient.

## 9.6 Temperature-Dependent LLE: Upper and Lower Critical Solution Temperatures

### 9.6.1 Closed-Loop Behavior

Some water–hydrocarbon systems exhibit an upper critical solution temperature (UCST) where the two liquid phases become miscible. For example, water–n-butylamine shows UCST behavior around 125°C. CPA can predict this behavior through the temperature dependence of the association term — as temperature increases, hydrogen bonds weaken, reducing the thermodynamic penalty of mixing.

### 9.6.2 Lower Critical Solution Temperature (LCST)

Certain aqueous systems (e.g., water–poly(ethylene glycol)) exhibit LCST behavior, where a homogeneous solution separates into two phases upon heating. This seemingly counterintuitive behavior occurs because the entropy of mixing decreases at higher temperatures due to the loss of organized hydration structures. CPA captures this through the temperature dependence of $\Delta^{AB}$.

## 9.7 Hydrate Boundary Conditions

### 9.7.1 Connection to Hydrate Prediction

Gas hydrates form when water molecules create ice-like cage structures around small gas molecules at elevated pressures and low temperatures. Accurate hydrate prediction requires knowing:

1. The water content of the gas phase (from CPA VLE)
2. The composition of the aqueous phase (dissolved gas from CPA)
3. The activity of water in the aqueous phase (from CPA fugacity)

The van der Waals–Platteeuw model for hydrate prediction uses the fugacity of water in the aqueous phase as a key input. CPA provides this fugacity more accurately than classical EoS because it correctly models the hydrogen-bond network.

### 9.7.2 Hydrate Prediction in NeqSim

```python
from neqsim import jneqsim

# Hydrate formation temperature for natural gas
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 10, 100.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.03)
fluid.addComponent("CO2", 0.01)
fluid.addComponent("water", 0.01)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.hydrateFormationTemperature()

T_hydrate = fluid.getTemperature("C")
print(f"Hydrate formation temperature: {T_hydrate:.1f} C at 100 bara")
```

## 9.8 Validation Against Experimental Data

### 9.8.1 Systematic Assessment

CPA has been extensively validated against experimental data for water–hydrocarbon systems. The following summary covers the key systems:

| System | Property | T Range (°C) | P Range (bar) | CPA AAD (%) | SRK AAD (%) |
|--------|----------|-------------|--------------|-------------|-------------|
| Water–methane | Water in gas | 25–200 | 10–1000 | 5–15 | 30–200 |
| Water–ethane | Mutual LLE | 25–200 | 10–500 | 10–20 | > 100 |
| Water–n-hexane | Mutual LLE | 25–200 | 1–50 | 5–15 | > 100 |
| Water–benzene | LLE | 25–300 | 1–100 | 10–25 | > 100 |
| Natural gas–water | Water content | $-10$ to 100 | 10–200 | 5–10 | 50–200 |

*Table 9.2: Comparison of CPA and SRK accuracy for water–hydrocarbon systems.*

### 9.8.2 Regions of Poorer Accuracy

CPA predictions are less accurate in certain regions:

- **Near the critical point of water** ($T > 350°C$): CPA, like all cubic-based models, is less reliable near the critical point
- **Very high pressures** ($P > 500$ bar): the cubic term becomes less accurate for compressed liquids
- **Aromatic systems**: benzene–water requires solvation parameters that may not always be available
- **Heavy oils**: limited validation data for asphaltene-containing systems

## 9.9 Worked Example: Water Content of a Natural Gas Pipeline

This section presents a complete worked example of computing the water content of a natural gas at pipeline conditions, comparing CPA predictions with the standard McKetta–Wehe chart used in industry.

### 9.9.1 Problem Statement

A natural gas pipeline operates at 80 bar and 25°C. The gas composition is: methane 88%, ethane 5%, propane 3%, n-butane 1%, CO$_2$ 2%, and N$_2$ 1%. Determine:

1. The water content of the gas at saturation
2. The water dew point temperature at 80 bar
3. How sensitive the water content is to temperature

### 9.9.2 Analytical Framework

The water content of a gas at saturation is determined by the fugacity equality:

$$f_{\text{water}}^{\text{gas}}(T, P, \mathbf{y}) = f_{\text{water}}^{\text{liquid}}(T, P, \mathbf{x})$$

For a gas in equilibrium with nearly pure water:

$$y_{\text{water}} P \varphi_{\text{water}}^V \approx P_{\text{water}}^{\text{sat}} \varphi_{\text{water}}^{\text{sat}} \exp\left[\frac{V_{\text{water}}^L(P - P_{\text{water}}^{\text{sat}})}{RT}\right]$$

The exponential is the **Poynting correction** factor, which accounts for the effect of pressure on the liquid fugacity. At 80 bar and 25°C, the Poynting correction is approximately 1.04 — a 4% increase that becomes significant at higher pressures.

### 9.9.3 NeqSim Solution

```python
from neqsim import jneqsim

# Set up the gas mixture
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 25.0, 80.0)
fluid.addComponent("methane", 0.88)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.03)
fluid.addComponent("n-butane", 0.01)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("nitrogen", 0.01)
fluid.addComponent("water", 0.001)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

# Water content in mg/Sm3
y_water = fluid.getPhase("gas").getComponent("water").getx()
print(f"Water mole fraction in gas: {y_water:.6f}")
print(f"Water content: {y_water * 1e6:.0f} ppm (mole)")

# Water dew point
ops.waterDewPointTemperature()
T_dew = fluid.getTemperature("C")
print(f"Water dew point: {T_dew:.1f} C at 80 bar")
```

### 9.9.4 Comparison with the McKetta–Wehe Chart

The McKetta–Wehe chart (GPSA Engineering Data Book) is the industry-standard correlation for water content of natural gas. At 25°C and 80 bar, the chart gives approximately 600–650 mg/Sm$^3$. CPA typically predicts values within 5% of this range, while SRK overpredicts by 30–50%.

The advantage of CPA over the correlation chart is that CPA:
- Accounts for the effect of gas composition (heavy hydrocarbons reduce water content)
- Handles the presence of CO$_2$ and H$_2$S (which increase water content)
- Provides consistent thermodynamic derivatives for process design
- Works seamlessly in dynamic simulations and optimization

### 9.9.5 Temperature Sensitivity Analysis

The water content of natural gas increases exponentially with temperature, approximately following:

$$\ln(y_{\text{water}}) \approx A + \frac{B}{T} + C\ln P$$

where $A$, $B$, $C$ are constants. A 10°C increase in temperature roughly doubles the water content. This strong temperature sensitivity explains why gas cooling (prior to dehydration) is critical for achieving the pipeline water specification.

| T (°C) | Water content (mg/Sm$^3$) | Ratio to 25°C |
|--------|--------------------------|---------------|
| 10 | ~250 | 0.4 |
| 15 | ~330 | 0.5 |
| 20 | ~450 | 0.7 |
| 25 | ~620 | 1.0 |
| 30 | ~850 | 1.4 |
| 35 | ~1150 | 1.9 |
| 40 | ~1550 | 2.5 |
| 50 | ~2700 | 4.4 |

*Table 9.3: Water content of typical North Sea gas at 80 bar as a function of temperature.*

## 9.10 The Microscopic Origin of Water–Hydrocarbon Immiscibility

Understanding *why* water and hydrocarbons are immiscible provides physical insight that helps interpret CPA results and identify model limitations.

### 9.10.1 The Thermodynamic Driving Force

Mixing of water and a hydrocarbon at constant $T$ and $P$ requires $\Delta G_{\text{mix}} < 0$, where:

$$\Delta G_{\text{mix}} = \Delta H_{\text{mix}} - T\Delta S_{\text{mix}}$$

For ideal solutions, $\Delta H_{\text{mix}} = 0$ and $\Delta S_{\text{mix}} = -R\sum_i x_i \ln x_i > 0$, so mixing is always favorable. For water–hydrocarbon systems, the situation is dramatically different:

- $\Delta H_{\text{mix}} > 0$ (endothermic): Breaking water–water hydrogen bonds (each ~20 kJ/mol) costs more energy than is gained from water–hydrocarbon dispersion interactions
- $\Delta S_{\text{mix}}$: Surprisingly, the excess entropy is also unfavorable — water molecules near a hydrocarbon solute form ordered "iceberg" structures, reducing the entropy

The combination of positive $\Delta H_{\text{mix}}$ and negative $\Delta S^E_{\text{mix}}$ gives a strongly positive $\Delta G_{\text{mix}}$, driving phase separation.

### 9.10.2 How CPA Captures This Physics

In CPA, the association term directly models the cost of disrupting water's hydrogen bond network:

1. In pure water, each molecule forms ~3.4 hydrogen bonds at 25°C ($X_A \approx 0.15$)
2. When a hydrocarbon molecule is introduced, nearby water molecules lose hydrogen-bonding partners
3. The site fractions $X_A$ increase (fewer bonds) in the dilute aqueous solution
4. The increased $X_A$ raises the chemical potential of water, penalizing mixing

The SRK cubic term, by contrast, treats all intermolecular interactions as a mean-field dispersion — it has no mechanism to distinguish between a water molecule surrounded by other water molecules (highly bonded) and a water molecule adjacent to a hydrocarbon (poorly bonded).

### 9.10.3 The Hydrophobic Hydration Shell

Molecular dynamics simulations show that water molecules adjacent to non-polar solutes reorganize their hydrogen-bond network to avoid "wasting" bonding sites that face the solute. This reorganization:

- Maintains the number of hydrogen bonds (enthalpy is nearly unchanged for small solutes)
- Reduces the orientational entropy (water molecules are more constrained)
- Creates an ordered "hydration shell" that becomes the dominant thermodynamic penalty for small solute molecules

This explains the seemingly paradoxical experimental observation that small gas molecules (methane, ethane) dissolve in water with $\Delta H_{\text{sol}} < 0$ (exothermic) but $\Delta S_{\text{sol}} \ll 0$ (strongly entropy-driven immiscibility). CPA captures this partially through the radial distribution function $g(\rho)$ at contact, which increases near hydrophobic solutes.

### 9.10.4 Practical Implications for Process Design

The molecular-level understanding has direct engineering consequences:

| Observation | CPA Captures? | Engineering Impact |
|-------------|--------------|-------------------|
| Water content decreases with heavier gas components | Yes (via $k_{ij}$) | Richer gas needs less dehydration |
| CO$_2$ increases water content of gas | Yes (solvation) | Acid gas increases dehydration duty |
| Methanol distributes between gas and water phases | Yes (cross-association) | Methanol losses to gas phase |
| Salts reduce water activity | Via electrolyte CPA | Salt in produced water shifts equilibrium |
| Temperature has exponential effect | Yes (association $T$-dependence) | Small $T$ changes $\Rightarrow$ large water content changes |

*Table 9.4: CPA's ability to capture key physical phenomena in water–hydrocarbon systems.*

## Summary

Key points from this chapter:

- CPA dramatically improves predictions for water–hydrocarbon systems compared to classical EoS
- Mutual solubilities of water and alkanes are reproduced within 10–30% (vs. 100–1000% for SRK)
- Water content of natural gas is predicted composition-dependently, capturing CO$_2$ and H$_2$S effects
- Three-phase VLLE is handled naturally with the same model parameters
- CPA provides accurate water fugacities needed for hydrate prediction
- The improvement comes from explicitly modeling the hydrogen-bond network in the aqueous phase

## Exercises

1. **Exercise 9.1:** Using NeqSim, compute the mutual solubilities of water and n-hexane at temperatures from 25°C to 200°C. Plot both solubilities on a logarithmic scale and identify any minima.

2. **Exercise 9.2:** Compare the predicted water content of methane at 50°C from 10 to 200 bar using CPA and SRK. Add experimental data from \cite{Olds1942} for comparison.

3. **Exercise 9.3:** Calculate the water dew point of a natural gas (C1 85%, C2 7%, C3 3%, CO$_2$ 3%, N$_2$ 1%, H$_2$O 1%) at pressures from 20 to 150 bar. Plot the water dew point curve and compare with the hydrocarbon dew point.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 9.1: 01 Water Content Methane](figures/fig_ch09_01_water_content_methane.png)

*Figure 9.1: 01 Water Content Methane*

![Figure 9.2: 02 Methane Solubility Water](figures/fig_ch09_02_methane_solubility_water.png)

*Figure 9.2: 02 Methane Solubility Water*

![Figure 9.3: 03 Natgas Water Content](figures/fig_ch09_03_natgas_water_content.png)

*Figure 9.3: 03 Natgas Water Content*

![Figure 9.4: Ex01 Water Content](figures/fig_ch09_ex01_water_content.png)

*Figure 9.4: Ex01 Water Content*

![Figure 9.5: Ex02 Solubility](figures/fig_ch09_ex02_ch4_solubility.png)

*Figure 9.5: Ex02 Solubility*
