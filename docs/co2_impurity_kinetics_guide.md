---
title: Legacy CO2 Impurity Kinetics Experiment Guide
description: Legacy experiment notes for the CO2 impurity kinetics prototype; use the maintained chemical-reactions guide for current API and limitations.
---

# Comprehensive Guide: NeqSim CO2 Impurity Chemical Kinetic Model & CSTR Experiment System

> **Experimental model — calibration required.** The maintained implementation and its current
> limitations are documented in
> [Experimental CO2 impurity kinetic reactor](chemicalreactions/co2_impurity_kinetics_guide.md).
> Treat the numerical parameters and case-study results below as legacy experiment material, not
> validated design data.

## 1. Executive Summary & Scope

This documentation provides the comprehensive technical, thermodynamic, and chemical engineering reference for the **NeqSim $\text{CO}_2$ Impurity Kinetic Model** (`neqsim_co2_kinetics.py` and Java `CO2ImpurityKineticReactor.java`).

The model simulates multi-component chemical reactions between trace impurities ($\text{H}_2\text{S}, \text{SO}_2, \text{NO}_2, \text{NO}, \text{O}_2, \text{H}_2\text{O}$) and formed reaction products ($\text{H}_2\text{SO}_4, \text{HNO}_3, \text{NH}_3, \text{S}_8$) in dense liquid, supercritical, and vapor gas-phase $\text{CO}_2$ streams across Carbon Capture, Transport, and Storage (CCS) systems.

---

## 2. Thermodynamic & Physical Rate Law Engine

### Pure Physical Arrhenius Kinetics
The forward reaction rate constants $k_{f, m}(T)$ are calculated using continuous physical Arrhenius kinetics without empirical hardcoded step functions or magic numbers:

$$k_{f, m}(T) = A_m \cdot \exp\left(-\frac{E_{a, m}}{R \cdot T}\right)$$

where $A_m$ is the pre-exponential factor, $E_{a, m}$ is the activation energy in $\text{J/mol}$, $R = 8.31446\text{ J/(mol}\cdot\text{K)}$, and $T$ is the temperature in Kelvin.

### SRK EOS Fugacity Concentration Driving Forces ($C_{i, \text{f}} = \phi_i \cdot C_i$)
To correctly capture non-ideal high-pressure fluid behavior, reaction rate driving forces utilize thermodynamic fugacity concentrations calculated via the Soave-Redlich-Kwong (SRK) Equation of State:

$$C_{i, \text{f}} = \phi_i \cdot C_i$$

where $\phi_i$ is the SRK fugacity coefficient of impurity component $i$, and $C_i$ is the molar concentration ($C_i = y_i \cdot \rho_m$).

### Physical Explanation of Dense Liquid vs Gas Phase Kinetic Ratio ($> 2,180\times$ Rate Ratio)
Reaction rates depend on fluid molar density as $r_0 \propto \rho_m^n$ (where $n = 2.0$ to $2.5$):

- **Dense Liquid Phase ($-25.0^\circ\text{C}, 25.0\text{ bar}$)**:
  - Fluid density: $\rho_m = \mathbf{24.0339\text{ kmol/m}^3}$ ($\rho = 1057.72\text{ kg/m}^3$).
  - SRK fugacity coefficient: $\phi_i = 0.95 \rightarrow C_{i, \text{f}} = 2.28 \times 10^{-4}\text{ kmol/m}^3$.
  - Initial reaction rate: $r_{0, \text{R3b}} = \mathbf{17,306.5\text{ ppm/hr}}$ (**VERY STRONG REACTIONS**, $4.39\text{ ppm }\text{H}_2\text{SO}_4$ formed in 1 hour).

- **Vapor Gas Phase ($+4.0^\circ\text{C}, 20.0\text{ bar}$)**:
  - Fluid density: $\rho_m = \mathbf{1.0050\text{ kmol/m}^3}$ ($\rho = 44.23\text{ kg/m}^3$).
  - SRK fugacity coefficient: $\phi_i = 0.58 \rightarrow C_{i, \text{f}} = 5.83 \times 10^{-6}\text{ kmol/m}^3$.
  - Rate ratio: $\left(\frac{C_{\text{gas}}}{C_{\text{liq}}}\right)^{2.5} = \frac{1}{9,600}$, making gas-phase reactions **$> 2,180\times$ SLOWER** ($< 0.05\text{ ppm }\text{H}_2\text{SO}_4$ formed after 10 hours, **chemically frozen**).

---

## 3. Reaction Kinetics & Thermochemistry (Reactions R1 to R8)

| Reaction ID | Chemical Reaction Equation | $A$ (Pre-exp Factor) | $E_a$ ($\text{kJ/mol}$) | $\Delta G^\circ_{298}$ ($\text{kJ/mol}$) | $K_{\text{eq}}$ ($25^\circ\text{C}$) | Initial Rate $r_0$ ($\text{ppm/hr}$) |
|---|---|---|---|---|---|---|
| **R1** | $\text{SO}_2 + 0.5 \text{O}_2 + \text{H}_2\text{O} \rightleftharpoons \text{H}_2\text{SO}_4$ | $1.0 \times 10^4$ | $45.0$ | $-152.9$ | $1.528 \times 10^{32}$ | $0.0000$ |
| **R2** | $\text{H}_2\text{S} + 3 \text{NO}_2 \rightleftharpoons \text{SO}_2 + \text{H}_2\text{O} + 3 \text{NO}$ | $5.0 \times 10^7$ | $28.0$ | $-478.7$ | $5.686 \times 10^{83}$ | **498.6141** |
| **R3a** | $\text{SO}_2 + \text{NO}_2 + \text{H}_2\text{O} \rightleftharpoons \text{NO} + \text{H}_2\text{SO}_4$ | $1.4 \times 10^6$ | $26.0$ | $-141.1$ | $5.674 \times 10^{24}$ | $0.0032$ |
| **R3b** | $\text{SO}_2 + \text{H}_2\text{S} + \text{NO}_2 + \text{O}_2 \rightarrow \text{H}_2\text{SO}_4$ | $2.13 \times 10^8$ | $15.0$ | Irreversible | Irreversible | **264.3137** |
| **R4** | $2 \text{NO} + \text{O}_2 \rightleftharpoons 2 \text{NO}_2$ | $1.0 \times 10^5$ | $-4.4$ | $-70.6$ | $7.257 \times 10^{14}$ | $0.0000$ |
| **R5** | $3 \text{NO}_2 + \text{H}_2\text{O} \rightleftharpoons 2 \text{HNO}_3 + \text{NO}$ | $2.4 \times 10^6$ | $28.0$ | $+24.6$ | $5.081 \times 10^{-5}$ | $0.0000$ |
| **R6** | $\text{H}_2\text{S} + 1.5 \text{O}_2 \rightleftharpoons \text{SO}_2 + \text{H}_2\text{O}$ | $2.0 \times 10^3$ | $65.0$ | $-605.1$ | $1.112 \times 10^{106}$ | $0.0000$ |
| **R7** | $5 \text{H}_2\text{S} + 6 \text{NO} + 4 \text{H}_2\text{O} \rightarrow 6 \text{NH}_3 + 5 \text{SO}_2$ | $5.0 \times 10^5$ | $15.0$ | Irreversible | Irreversible | $0.0000$ |
| **R8** | $\text{H}_2\text{S} + 0.5 \text{O}_2 \rightarrow \frac{1}{8} \text{S}_8 + \text{H}_2\text{O}$ (CS) | $1.5 \times 10^4$ | $42.0$ | Irreversible | Irreversible | $0.0112$ |

---

## 4. Reactor Sizing & Hydrodynamic Derivations

### Autoclave Geometry & Calculated Length $L = \mathbf{9.0408\text{ cm}}$
- Target Reactor Volume ($V$): $300.0\text{ mL} = 300.0\text{ cm}^3 = 3.0 \times 10^{-4}\text{ m}^3$
- Inner Diameter ($D$): $6.50\text{ cm} = 0.0650\text{ m}$
- Cross-Sectional Area ($A_{\text{cross}}$):
  $$A_{\text{cross}} = \frac{\pi \cdot D^2}{4} = \frac{\pi \cdot (6.50\text{ cm})^2}{4} = \mathbf{33.1831\text{ cm}^2} \quad (3.31831 \times 10^{-3}\text{ m}^2)$$
- Calculated Reactor Length ($L$):
  $$L = \frac{V}{A_{\text{cross}}} = \frac{300.0\text{ cm}^3}{33.1831\text{ cm}^2} = \mathbf{9.0408\text{ cm}} \quad (0.090408\text{ m})$$

### Hydrodynamic Residence Time $\tau = \mathbf{6.3463\text{ HOURS}}$
- Liquid $\text{CO}_2$ density at $25.0\text{ bar}, -25.0^\circ\text{C}$: $\rho = 1057.72\text{ kg/m}^3 = 1.05772\text{ g/mL}$.
- Liquid mass inventory in $300.0\text{ mL}$ vessel:
  $$m_{\text{reactor}} = 300.0\text{ mL} \times 1.05772\text{ g/mL} = \mathbf{317.32\text{ grams of liquid }\text{CO}_2}$$
- Mass flow rate ($\dot{m}$): $50.0\text{ g/h}$.
- CSTR Hydrodynamic Residence Time ($\tau$):
  $$\tau = \frac{m_{\text{reactor}}}{\dot{m}} = \frac{317.32\text{ g}}{50.0\text{ g/h}} = \mathbf{6.3463\text{ HOURS}} \quad (\mathbf{22,846.8\text{ seconds}})$$

### Phase 0 Pressurization Duration ($1\text{ bar}, 25^\circ\text{C }\text{N}_2$ Charge)
- Initial $\text{N}_2$ gas mass in $300\text{ mL}$ autoclave: $m_{\text{N2}} = 0.3390\text{ grams}$.
- Duration to feed $317.32\text{ g}$ liquid $\text{CO}_2$ at $50.0\text{ g/h}$:
  $$t_{\text{pressurization}} = \frac{317.32\text{ g}}{50.0\text{ g/h}} = \mathbf{6.3463\text{ HOURS}} \quad (\mathbf{6\text{h } 20\text{m } 46\text{s}})$$

---

## 5. Chemical Engineering Case Studies & Key Insights

### Case Study 1: Closed Batch vs Continuous CSTR Flow ($15.50\text{ ppm}$ vs $17.27\text{ ppm }\text{SO}_2$)
- **Closed Batch Reactor**: NO2 ($10\text{ ppm}$) is the limiting reactant and is 100% consumed within 1 hour. Reaction R2 stops completely. Maximum $\text{SO}_2$ formed = $10.0 + 3.33 + 2.20 - 0.03 = \mathbf{15.50\text{ ppm}}$.
- **Continuous Flow CSTR**: Fresh feed continuously supplies $10\text{ ppm }\text{H}_2\text{S}$ and $10\text{ ppm }\text{NO}_2$ every hour. $9.68\text{ ppm }\text{H}_2\text{S}$ is continuously oxidized to $\text{SO}_2$, yielding a net steady-state balance $[\text{SO}_2]_{\text{out}} = 10.00 + 9.68 - 2.41 = \mathbf{17.27\text{ ppm}}$.

### Case Study 2: Sulfuric Acid Peak & Decay Dynamics
- **Peak Spike ($8.68-9.00\text{ ppm }\text{H}_2\text{SO}_4$)**: When $\text{H}_2\text{S}$ is injected into accumulated $\text{SO}_2, \text{NO}_2, \text{O}_2$ ($~10\text{ ppm}$ each), Reaction R3b operates at maximum rate ($r_0 = 264\text{ ppm/hr}$), creating an acid surge.
- **Decay to Steady State ($2.41-2.85\text{ ppm }\text{H}_2\text{SO}_4$)**: Reaction R3b rapidly consumes $\text{O}_2$ (dropping $9.57 \rightarrow 0.03\text{ ppm}$, $99.7\%$ reduction). Starving $\text{O}_2$ throttles acid production down by $> 95\%$, and CSTR outflow dilutes the excess acid down to steady state.

---

## 6. Python & Java API Usage

### Python Modular API (`CO2ImpurityReactorExperiment`)
```python
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment

# 1. Initialize SRK CO2 system
exp = CO2ImpurityReactorExperiment(
    target_pressure_bar=25.0,
    target_temp_C=-25.0,
    diameter_cm=6.50,
    volume_ml=300.0,
    mass_flow_g_h=50.0
)

# 2. Specify initial vessel charge (Default: N2 at 1 bar, 25 °C)
exp.set_initial_vessel_charge(gas_name='N2', pressure_bar=1.0, temp_C=25.0)

# 3. Add custom phases
exp.add_phase(10.0, {'H2S': 0, 'SO2': 0, 'NO2': 0, 'O2': 0, 'H2O': 0}, "Phase 0: Pressurization")
exp.add_phase(40.0, {'SO2': 10, 'NO2': 10, 'O2': 10, 'H2O': 10}, "Phase 1: 10 ppm Without H2S")
exp.add_phase(50.0, {'H2S': 10, 'SO2': 10, 'NO2': 10, 'O2': 10, 'H2O': 10}, "Phase 2: 10 ppm All Impurities")

# 4. Run experiment and get 2-hour resolution table
exp.run_experiment()
df_table = exp.get_table_results(resolution_hours=2.0)

# 5. One-line plotting helper
fig, axes = exp.plot_results(save_path='cstr_100hr_experiment_plot.png')
```

### Java NeqSim Equipment Class (`CO2ImpurityKineticReactor.java`)
```java
import neqsim.process.equipment.reactor.CO2ImpurityKineticReactor;

CO2ImpurityKineticReactor reactor = new CO2ImpurityKineticReactor("CO2 CSTR Reactor");
reactor.setReactorGeometry(6.50, 300.0, 50.0);
reactor.setReactionConstants("SO2 + H2S + NO2 + O2 -> H2SO4", 2.13e8, 15.0);

String report = reactor.generateReactorReport(248.15, 25.0);
System.out.println(report);
```
