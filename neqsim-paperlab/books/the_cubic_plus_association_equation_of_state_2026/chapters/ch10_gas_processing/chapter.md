# Gas Processing and Chemical Injection

<!-- Chapter metadata -->
<!-- Notebooks: 01_teg_dehydration.ipynb, 02_methanol_injection.ipynb, 03_glycol_losses.ipynb -->
<!-- Estimated pages: 20 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Model TEG dehydration processes using CPA in NeqSim
2. Calculate methanol partitioning in multiphase gas systems
3. Predict glycol losses in dehydration and hydrate inhibition
4. Design MEG injection systems with CPA-based predictions
5. Apply CPA for polar chemical injection in oil and gas processing

## 10.1 Introduction to Gas Processing with CPA

Gas processing operations frequently involve polar chemicals: glycols for dehydration, methanol and MEG for hydrate inhibition, amines for acid gas removal. These chemicals form hydrogen bonds with water and with each other, making CPA the natural thermodynamic model for these applications.

Classical cubic EoS with conventional mixing rules cannot accurately predict:

- The vapor pressure depression of water by glycol addition
- The partitioning of methanol between gas, condensate, and aqueous phases
- Glycol losses to the gas and condensate phases
- The effect of dissolved salts on inhibitor performance

CPA handles all these aspects through its explicit treatment of hydrogen bonding, making it the preferred model for gas processing design.

## 10.2 TEG Dehydration

### 10.2.1 Process Description

Triethylene glycol (TEG) dehydration is the most common method for removing water from natural gas. The process consists of:

1. **Absorption column**: wet gas contacts lean TEG in a countercurrent contactor
2. **Rich TEG flash drum**: dissolved gas is removed from the rich TEG
3. **Rich TEG heat exchangers**: heat recovery before regeneration
4. **Regeneration column**: water is stripped from the TEG at near-atmospheric pressure
5. **Stripping gas injection** (optional): improves TEG purity beyond thermal regeneration limits

Typical specifications:
- Water content in dry gas: < 7 lb/MMscf (pipeline) or < 1 ppmv (cryogenic processing)
- TEG circulation rate: 2–5 gallons TEG per lb of water removed
- Regeneration temperature: 185–204°C (depending on TEG purity target)
- TEG purity: 98.5–99.99 wt% (depending on application)

### 10.2.2 Why CPA Is Important for TEG Design

The key thermodynamic properties that determine TEG dehydration performance are:

1. **Water–TEG activity coefficients**: determines the equilibrium water content achievable
2. **TEG vapor pressure**: determines TEG losses to the gas phase
3. **Hydrocarbon solubility in TEG**: determines BTEX and C$_6$+ pickup
4. **Water dew point of dry gas**: the design target

CPA accurately models the water–TEG system because it captures the hydrogen bonding between water's OH groups and TEG's three ether oxygens and two hydroxyl groups. The association scheme for TEG in CPA is typically 4C (two proton donor sites and two proton acceptor sites).

### 10.2.3 TEG Dehydration Simulation with NeqSim

```python
from neqsim import jneqsim

# Create feed gas with water
feed_gas = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 70.0)
feed_gas.addComponent("methane", 0.85)
feed_gas.addComponent("ethane", 0.07)
feed_gas.addComponent("propane", 0.03)
feed_gas.addComponent("n-butane", 0.01)
feed_gas.addComponent("CO2", 0.02)
feed_gas.addComponent("water", 0.015)
feed_gas.addComponent("TEG", 0.0)
feed_gas.setMixingRule(10)
feed_gas.setMultiPhaseCheck(True)

# Set up the stream
Stream = jneqsim.process.equipment.stream.Stream
feed_stream = Stream("Wet Gas", feed_gas)
feed_stream.setFlowRate(10.0, "MSm3/day")
feed_stream.setTemperature(30.0, "C")
feed_stream.setPressure(70.0, "bara")

# Create lean TEG stream
teg_fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 70.0)
teg_fluid.addComponent("methane", 0.0)
teg_fluid.addComponent("ethane", 0.0)
teg_fluid.addComponent("propane", 0.0)
teg_fluid.addComponent("n-butane", 0.0)
teg_fluid.addComponent("CO2", 0.0)
teg_fluid.addComponent("water", 0.01)
teg_fluid.addComponent("TEG", 0.99)
teg_fluid.setMixingRule(10)

teg_stream = Stream("Lean TEG", teg_fluid)
teg_stream.setFlowRate(5000.0, "kg/hr")
teg_stream.setTemperature(30.0, "C")
teg_stream.setPressure(70.0, "bara")

print("TEG dehydration streams configured with CPA")
```

### 10.2.4 Prediction of Dry Gas Water Content

The achievable water content of the dry gas depends on:

- **TEG purity**: higher purity achieves lower water dew points
- **Temperature**: lower contactor temperature improves dehydration
- **Number of stages**: more theoretical stages improve separation
- **TEG circulation rate**: higher rates approach but never exceed the equilibrium limit

CPA predicts the equilibrium water content above TEG solutions accurately:

| TEG Purity (wt%) | Temp (°C) | CPA Water Content (mg/Sm$^3$) | Exp. (mg/Sm$^3$) | SRK (mg/Sm$^3$) |
|-------------------|-----------|-------------------------------|-------------------|------------------|
| 98.5 | 30 | 95 | 90 | 150 |
| 99.0 | 30 | 55 | 52 | 95 |
| 99.5 | 30 | 28 | 26 | 52 |
| 99.9 | 30 | 6 | 5.5 | 15 |
| 99.95 | 30 | 3 | 2.8 | 8 |

*Table 10.1: Water content of gas in equilibrium with TEG solutions (representative values).*

## 10.3 Methanol Injection for Hydrate Inhibition

### 10.3.1 The Methanol Partitioning Problem

Methanol is widely used as a thermodynamic hydrate inhibitor in subsea gas production. The dosing rate must account for methanol that partitions into three phases:

1. **Aqueous phase**: where methanol acts as a hydrate inhibitor
2. **Gas phase**: methanol losses that do not contribute to inhibition
3. **Condensate phase**: additional losses, particularly for rich gas systems

Accurate prediction of methanol partitioning is critical: under-dosing leads to hydrate blockage, while over-dosing wastes expensive chemical and creates downstream problems (methanol in sales gas, in produced water).

### 10.3.2 CPA for Methanol Partitioning

Methanol (3B association scheme: one OH donor, one OH acceptor, one electron pair) is a strong self-associating compound that also cross-associates with water. CPA with the binary parameters for methanol–water, methanol–methane, and methanol–hydrocarbons provides accurate three-phase partitioning:

```python
from neqsim import jneqsim

# Methanol partitioning in a gas-condensate-water system
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(278.15, 100.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.03)
fluid.addComponent("n-pentane", 0.02)
fluid.addComponent("n-hexane", 0.01)
fluid.addComponent("water", 0.05)
fluid.addComponent("methanol", 0.06)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    x_meoh = phase.getComponent("methanol").getx()
    print(f"Phase {i} ({phase.getType()}): x_MeOH = {x_meoh:.6f}")
```

### 10.3.3 Effect of Pressure and Temperature on Methanol Partitioning

CPA predictions show:

- **Gas-phase methanol losses increase** with decreasing pressure (lower gas density) and increasing temperature
- **Condensate-phase methanol losses** are relatively insensitive to pressure but increase with the amount of C$_5$+ present
- **At typical pipeline conditions** (80–150 bar, 4–20°C): 1–5% of injected methanol is lost to the gas, 2–8% to condensate

### 10.3.4 Hydrate Inhibition Effectiveness

The Hammerschmidt equation provides a simple estimate of the hydrate temperature depression:

$$\Delta T = \frac{K_H \cdot w}{M(100 - w)}$$

where $w$ is the weight percent of inhibitor in the aqueous phase, $M$ is the molecular weight, and $K_H$ is an empirical constant. CPA provides a more rigorous prediction through the activity of water in the methanol–water solution:

$$\Delta T \propto -\frac{RT^2}{\Delta H_{\text{hyd}}} \ln(a_w)$$

where $a_w$ is the activity of water computed by CPA.

## 10.4 MEG (Mono-Ethylene Glycol) Injection

### 10.4.1 Advantages Over Methanol

MEG (mono-ethylene glycol, also called ethylene glycol or EG) has several advantages over methanol for long-distance subsea gas transport:

- **Lower volatility**: much lower losses to the gas phase
- **Regenerable**: can be recovered and recycled at the receiving terminal
- **Less toxic**: lower environmental impact from accidental release
- **Non-flammable**: safer handling and storage

The main disadvantage is higher viscosity and lower hydrate suppression per unit weight, requiring higher injection rates.

### 10.4.2 CPA for MEG Systems

MEG (4C association scheme) forms strong hydrogen bonds with water. CPA accurately predicts:

- **MEG–water VLE**: vapor pressure depression of water by MEG
- **MEG losses to gas**: very small (typically < 0.1% of injected MEG), but important for MEG makeup calculation
- **MEG–hydrocarbon interactions**: negligible solubility, but relevant at extreme conditions

```python
from neqsim import jneqsim

# MEG-water-natural gas system
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(278.15, 150.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("water", 0.06)
fluid.addComponent("MEG", 0.03)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    x_meg = phase.getComponent("MEG").getx()
    x_water = phase.getComponent("water").getx()
    print(f"Phase {i} ({phase.getType()}): x_MEG={x_meg:.6f}, x_water={x_water:.6f}")
```

### 10.4.3 Rich vs. Lean MEG Properties

The viscosity and density of MEG–water solutions vary strongly with concentration and temperature. These physical properties are important for:

- Pump sizing in the MEG injection system
- Heat exchanger design in the MEG regeneration unit
- Pipeline hydraulics (increased frictional pressure drop)

CPA, combined with the physical property correlations in NeqSim, provides these properties as a function of composition and conditions.

## 10.5 Glycol Losses and Emissions

### 10.5.1 Sources of Glycol Loss

Glycol losses in gas processing occur through several mechanisms:

1. **Vaporization losses**: glycol carried in the gas phase (dominant at high temperatures)
2. **Entrainment losses**: liquid glycol droplets carried by the gas stream
3. **Solubility losses**: glycol dissolved in the hydrocarbon condensate phase
4. **Degradation losses**: thermal or oxidative degradation at regeneration temperatures

CPA predicts the first three mechanisms through phase equilibrium calculations. The degradation losses are kinetic and must be estimated separately.

### 10.5.2 TEG Vaporization Losses

TEG vaporization losses increase exponentially with temperature:

| T (°C) | P (bar) | CPA TEG in Gas (mg/Sm$^3$) | Exp. (mg/Sm$^3$) |
|---------|---------|---------------------------|-------------------|
| 20 | 70 | 0.5 | 0.4 |
| 30 | 70 | 2.0 | 1.8 |
| 40 | 70 | 7.5 | 6.8 |
| 50 | 70 | 24 | 21 |

*Table 10.2: TEG vaporization losses to natural gas (representative values).*

These losses, while small on a per-volume basis, can amount to significant TEG consumption and downstream contamination over a year of operation.

### 10.5.3 BTEX Absorption by TEG

TEG absorbs aromatic hydrocarbons (benzene, toluene, ethylbenzene, xylenes — BTEX) from the gas, which are then emitted during regeneration. This is a significant environmental concern:

- BTEX emissions from glycol regeneration are regulated in many jurisdictions
- The amount of BTEX absorbed depends on gas composition, temperature, and TEG rate
- CPA can model BTEX–TEG interactions through solvation parameters

```python
from neqsim import jneqsim

# BTEX absorption example
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 70.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("benzene", 0.001)
fluid.addComponent("water", 0.01)
fluid.addComponent("TEG", 0.139)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
```

## 10.6 Acid Gas Removal with Amines

### 10.6.1 Overview

Amine-based acid gas removal (sweetening) uses aqueous solutions of amines (MEA, DEA, MDEA, and blends) to absorb CO$_2$ and H$_2$S from natural gas. The process involves both physical dissolution and chemical reaction (carbamate/bicarbonate formation).

While the chemical reactions are not directly modeled by the CPA EoS, the phase equilibrium aspects benefit from CPA:

- **Water–amine–acid gas VLE**: CPA provides accurate vapor-liquid equilibrium
- **Hydrocarbon solubility in amine solutions**: important for estimating hydrocarbon losses
- **Amine volatility**: contributes to amine emissions and losses

### 10.6.2 CPA Parameters for Amines

Common amines used in gas sweetening have been characterized for CPA:

| Amine | Association Scheme | Hydrogen Bond Sites |
|-------|-------------------|---------------------|
| MEA (monoethanolamine) | 4C | 2 OH-donors + 1 NH-donor + 1 acceptor |
| DEA (diethanolamine) | 3B | 2 OH-donors + 1 acceptor |
| MDEA (methyldiethanolamine) | 2B | 2 OH-donors |
| Piperazine | 2B | 2 NH-donors |

*Table 10.3: CPA association schemes for common amines.*

The cross-association between amines and water is modeled using the CR-1 combining rule, providing good predictions of amine–water VLE and activity coefficients.

## 10.7 Process Simulation Examples

### 10.7.1 Comparing CPA with SRK for Dehydration Design

A practical comparison demonstrates the impact of model selection:

```python
from neqsim import jneqsim

# Same conditions, two models
conditions = [(273.15 + 30, 70.0), (273.15 + 50, 100.0), (273.15 + 10, 50.0)]

for T, P in conditions:
    # CPA model
    fluid_cpa = jneqsim.thermo.system.SystemSrkCPAstatoil(T, P)
    fluid_cpa.addComponent("methane", 0.90)
    fluid_cpa.addComponent("ethane", 0.05)
    fluid_cpa.addComponent("CO2", 0.03)
    fluid_cpa.addComponent("water", 0.02)
    fluid_cpa.setMixingRule(10)
    fluid_cpa.setMultiPhaseCheck(True)

    ops_cpa = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid_cpa)
    ops_cpa.TPflash()
    fluid_cpa.initProperties()

    # SRK model for comparison
    fluid_srk = jneqsim.thermo.system.SystemSrkEos(T, P)
    fluid_srk.addComponent("methane", 0.90)
    fluid_srk.addComponent("ethane", 0.05)
    fluid_srk.addComponent("CO2", 0.03)
    fluid_srk.addComponent("water", 0.02)
    fluid_srk.setMixingRule("classic")
    fluid_srk.setMultiPhaseCheck(True)

    ops_srk = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid_srk)
    ops_srk.TPflash()
    fluid_srk.initProperties()

    T_C = T - 273.15
    print(f"\nT={T_C:.0f} C, P={P:.0f} bar:")
    if fluid_cpa.hasPhaseType("gas"):
        y_w_cpa = fluid_cpa.getPhase("gas").getComponent("water").getx()
        y_w_srk = fluid_srk.getPhase("gas").getComponent("water").getx()
        print(f"  CPA water in gas: {y_w_cpa:.6f}")
        print(f"  SRK water in gas: {y_w_srk:.6f}")
        print(f"  Ratio SRK/CPA:   {y_w_srk/y_w_cpa:.2f}")
```

### 10.7.2 Design Implications

The differences between CPA and SRK predictions translate directly into equipment sizing:

- **Contactor height**: SRK may under-predict required height by 20–40% due to overestimating water removal per stage
- **TEG circulation rate**: SRK may recommend a lower rate, leading to off-spec dry gas
- **Regeneration energy**: linked to TEG rate and regeneration temperature, both affected by model accuracy

For critical dehydration applications (cryogenic gas processing, LNG production), the 5–15% accuracy of CPA vs. the 30–200% error of SRK can mean the difference between a successful design and a plant that cannot meet specifications.

## Summary

Key points from this chapter:

- CPA is essential for accurate modeling of gas processing operations involving polar chemicals
- TEG dehydration predictions improve from 30–200% error (SRK) to 5–15% (CPA)
- Methanol partitioning between gas, condensate, and aqueous phases is accurately predicted by CPA
- MEG injection design benefits from CPA's correct treatment of glycol–water hydrogen bonding
- Glycol losses to gas and condensate are correctly predicted, enabling accurate chemical consumption estimates
- BTEX absorption by TEG is modeled through solvation parameters
- CPA parameters are available for all common gas processing chemicals (TEG, MEG, DEG, methanol, ethanol, amines)

## Exercises

1. **Exercise 10.1:** Design a TEG dehydration unit using NeqSim with CPA. The wet gas (10 MSm$^3$/d, 70 bar, 30°C, water-saturated) must be dried to < 50 mg/Sm$^3$. Determine the required TEG purity, circulation rate, and number of contactor stages.

2. **Exercise 10.2:** Calculate methanol partitioning for a rich gas (methane 75%, ethane 8%, propane 5%, C$_4$ 3%, C$_5$+ 2%, CO$_2$ 2%, N$_2$ 1%, water 4%) at 80 bar and 5°C. How much of the injected methanol is effective for hydrate inhibition?

3. **Exercise 10.3:** Compare the predicted hydrate temperature depression for 30 wt% MEG and 20 wt% methanol aqueous solutions at pressures from 50 to 200 bar for a natural gas. Which inhibitor is more effective on a weight basis?

4. **Exercise 10.4:** Estimate TEG losses to the gas phase for a dehydration unit operating at temperatures from 20°C to 50°C at 70 bar. At what contactor temperature should BTEX emission controls be considered?

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 10.1: 01 Teg Water Vle](figures/fig_ch10_01_teg_water_vle.png)

*Figure 10.1: 01 Teg Water Vle*

![Figure 10.2: 02 Dew Point Teg](figures/fig_ch10_02_dew_point_teg.png)

*Figure 10.2: 02 Dew Point Teg*

![Figure 10.3: 03 Meg Water Gamma](figures/fig_ch10_03_meg_water_gamma.png)

*Figure 10.3: 03 Meg Water Gamma*

![Figure 10.4: Ex01 Teg Water Bp](figures/fig_ch10_ex01_teg_water_bp.png)

*Figure 10.4: Ex01 Teg Water Bp*

![Figure 10.5: Ex02 Water Dewpoint](figures/fig_ch10_ex02_water_dewpoint.png)

*Figure 10.5: Ex02 Water Dewpoint*

![Figure 10.6: Ex03 Meg Activity](figures/fig_ch10_ex03_meg_activity.png)

*Figure 10.6: Ex03 Meg Activity*
