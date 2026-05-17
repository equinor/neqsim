---
title: "Bio-Processing Unit Operations"
description: "Comprehensive guide to NeqSim's bio-processing equipment including reactors, fermenters, solid-liquid separators, liquid-liquid extractors, evaporators, dryers, and crystallizers. Covers mathematical models, design equations, and simulation examples."
---

# Bio-Processing Unit Operations

NeqSim provides a suite of unit operations for modeling bio-processing and biorefinery flowsheets. These integrate with NeqSim's rigorous thermodynamic engine so that phase equilibria, energy balances, and physical properties are handled consistently across the entire process.

## Contents

- [Overview](#overview)
- [Reactors](#reactors)
  - [StoichiometricReaction](#stoichiometricreaction)
  - [StirredTankReactor (CSTR)](#stirredtankreactor-cstr)
  - [Fermenter](#fermenter)
  - [EnzymeTreatment](#enzymetreatment)
- [Solid-Liquid Separators](#solid-liquid-separators)
  - [SolidsSeparator (Base)](#solidsseparator)
  - [SolidsCentrifuge](#solidscentrifuge)
  - [RotaryVacuumFilter](#rotaryvacuumfilter)
  - [PressureFilter](#pressurefilter)
  - [ScrewPress](#screwpress)
- [Liquid-Liquid Extraction](#liquid-liquid-extraction)
  - [LiquidLiquidExtractor](#liquidliquidextractor)
- [Thermal Processing](#thermal-processing)
  - [MultiEffectEvaporator](#multieffectevaporator)
  - [Dryer](#dryer)
- [Crystallization](#crystallization)
  - [Crystallizer](#crystallizer)
- [Integration with ProcessSystem](#integration-with-processsystem)
- [Complete Biorefinery Example](#complete-biorefinery-example)
- [Bioenergy Classes](#bioenergy-classes)
  - [AnaerobicDigester](#anaerobicdigester)
  - [BiomassGasifier](#biomassgasifier)
  - [BiogasUpgrader](#biogasupgrader)
  - [FermentationReactor](#fermentationreactor)
  - [SustainabilityMetrics](#sustainabilitymetrics)
- [Pre-Built Biorefinery Modules](#pre-built-biorefinery-modules)
  - [BiogasToGridModule](#biogastoGridmodule)
  - [GasificationSynthesisModule](#gasificationsynthesismodule)
  - [WasteToEnergyCHPModule](#wastetoenergychpmodule)

---

## Overview

All bio-processing equipment follows the standard NeqSim pattern:

1. Create a thermodynamic system (`SystemSrkEos`, `SystemPrEos`, etc.)
2. Set up a feed `Stream`
3. Instantiate the equipment, connecting the feed stream
4. Configure operating parameters
5. Call `run()`, which clones the feed, applies the unit model, performs a thermodynamic flash, and populates outlet streams

### Class Hierarchy

```
ProcessEquipmentBaseClass
  +-- TwoPortEquipment (single inlet, single outlet)
  |     +-- StirredTankReactor
  |           +-- Fermenter
  |           +-- EnzymeTreatment
  +-- ProcessEquipmentBaseClass (multi-outlet)
        +-- SolidsSeparator
        |     +-- SolidsCentrifuge
        |     +-- RotaryVacuumFilter
        |     +-- PressureFilter
        |     +-- ScrewPress
        +-- LiquidLiquidExtractor
        +-- Crystallizer
        +-- MultiEffectEvaporator
        +-- Dryer
```

### Using Bio-Processing Components (COMP_EXT)

Bio-processing simulations typically require components such as sugars, organic acids, and sugar alcohols that are not in the default component database (COMP). NeqSim's **extended component database** (COMP_EXT) contains thousands of additional components including many bio-relevant species.

**Enable COMP_EXT before creating the fluid system:**

```java
import neqsim.util.database.NeqSimDataBase;

// Enable extended database
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 1.01325);
fluid.addComponent("glucose", 0.10);
fluid.addComponent("ethanol", 0.05);
fluid.addComponent("water", 0.85);
fluid.setMixingRule("classic");

// Reset when done (especially in tests)
NeqSimDataBase.useExtendedComponentDatabase(false);
```

**Available bio-processing components in COMP_EXT:**

| Category | Components |
|----------|------------|
| **Sugars** | glucose, fructose, sucrose, dl-Xylose, arabinose, mannose, galactose, cellobiose, maltose monohydrate |
| **Organic acids** | lactic acid, citric acid, succinic acid, gluconic acid, acetic acid, propionic acid, butyric acid, itaconic acid, levulinic acid, pyruvic acid, fumaric acid, malic acid |
| **Alcohols** | ethanol, methanol, 1-butanol, 2-butanol, glycerol, 2-methyl-1-propanol (isobutanol) |
| **Sugar alcohols** | d-sorbitol, xylitol, d-mannitol, erythritol |
| **Platform chemicals** | 5-hydroxymethylfurfural, 5-methylfurfural, furfural, vanillin, phenol |
| **Amino acids** | l-lysine, l-glutamic acid |
| **Other** | urea, ethylene glycol, 1,2-propanediol, acetone, 2,3-butanediol |

---

## Reactors

### StoichiometricReaction

**Package:** `neqsim.process.equipment.reactor`

A standalone reaction object that defines stoichiometry, a limiting reactant, and a fractional conversion. It is not process equipment itself but is added to a `StirredTankReactor` or its subclasses.

#### Mathematical Model

Given a general reaction:

$$
\nu_A A + \nu_B B \;\longrightarrow\; \nu_C C + \nu_D D
$$

where $\nu_i$ are stoichiometric coefficients (negative for reactants, positive for products), the limiting reactant $A$ has $n_{A,0}$ moles in the feed and fractional conversion $X$:

$$
n_{A,\text{reacted}} = n_{A,0} \cdot X
$$

The moles consumed or produced for each species $i$ are:

$$
\Delta n_i = n_{A,\text{reacted}} \cdot \frac{\nu_i}{|\nu_A|}
$$

After reaction, the system composition becomes:

$$
n_i = n_{i,0} + \Delta n_i
$$

#### API

| Method | Description |
|--------|-------------|
| `addReactant(name, coeff)` | Add reactant with positive coefficient (stored as negative internally) |
| `addProduct(name, coeff)` | Add product with positive coefficient |
| `setLimitingReactant(name)` | Designate limiting reactant |
| `setConversion(X)` | Set fractional conversion $X \in [0, 1]$ |
| `react(system)` | Apply reaction to a `SystemInterface`, returns moles consumed |

#### Example

```java
// Ethanol fermentation: C6H12O6 -> 2 C2H5OH + 2 CO2
StoichiometricReaction rxn = new StoichiometricReaction("EthanolFermentation");
rxn.addReactant("glucose", 1.0);
rxn.addProduct("ethanol", 2.0);
rxn.addProduct("CO2", 2.0);
rxn.setLimitingReactant("glucose");
rxn.setConversion(0.90);
```

---

### StirredTankReactor (CSTR)

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `TwoPortEquipment` (single inlet, single outlet)

Models a continuous stirred-tank reactor (or batch reactor). Reactions are applied sequentially to the cloned feed system, followed by a thermodynamic flash at specified outlet conditions.

#### Mathematical Model

**Reaction step** — Each `StoichiometricReaction` is applied in sequence using the $\Delta n_i$ formula above.

**Energy balance** — Two operating modes:

*Isothermal* (fixed temperature $T_\text{out}$):

After applying reactions, a TP-flash is performed at $(T_\text{out}, P_\text{out})$. The required heat duty is:

$$
\dot{Q} = H_\text{out} - H_\text{in} \qquad [\text{W}]
$$

where $H$ denotes stream enthalpy evaluated at init level 3.

*Adiabatic* ($\dot{Q} = 0$):

A PH-flash is performed at constant enthalpy $H_\text{in}$ and outlet pressure $P_\text{out}$. The outlet temperature is determined by the flash.

**Pressure:**

$$
P_\text{out} = \begin{cases} P_\text{set} & \text{if reactor pressure specified} \\ P_\text{in} - \Delta P & \text{otherwise} \end{cases}
$$

**Agitator power:**

$$
W_\text{agitator} = \left(\frac{P}{V}\right) \cdot V \qquad [\text{kW}]
$$

where $P/V$ is the specific power in kW/m$^3$ and $V$ is the vessel volume.

#### API

| Method | Description |
|--------|-------------|
| `addReaction(rxn)` | Add a `StoichiometricReaction` |
| `setReactorTemperature(T)` | Set outlet temperature [K] (enables isothermal mode) |
| `setReactorTemperature(T, unit)` | Set temperature with unit ("K", "C", "F") |
| `setReactorPressure(P)` | Set outlet pressure [bara] |
| `setVesselVolume(V)` | Set vessel volume [m$^3$] |
| `setResidenceTime(t, unit)` | Set residence time ("hr", "min", "s") |
| `setAgitatorPowerPerVolume(PV)` | Set agitator power [kW/m$^3$] |
| `setPressureDrop(dP)` | Set pressure drop [bar] |
| `setIsothermal(bool)` | Toggle isothermal/adiabatic mode |
| `getHeatDuty()` | Get heat duty [W] after run |
| `getHeatDuty(unit)` | Get heat duty in "W", "kW", or "MW" |
| `getAgitatorPower()` | Get total agitator power [kW] |

#### Simulation Example

```java
// Enable extended component database for bio-relevant components
NeqSimDataBase.useExtendedComponentDatabase(true);

// Create fluid system with bio-processing components from COMP_EXT
SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 1.01325);
fluid.addComponent("water", 0.80);
fluid.addComponent("glucose", 0.10);
fluid.addComponent("ethanol", 0.05);
fluid.addComponent("lactic acid", 0.05);
fluid.setMixingRule("classic");

// Feed stream
Stream feed = new Stream("Reactor Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.run();

// Create CSTR for lactic acid fermentation
StirredTankReactor cstr = new StirredTankReactor("CSTR-1", feed);
cstr.setReactorTemperature(273.15 + 37.0);  // 37 C, isothermal
cstr.setVesselVolume(20.0);                  // 20 m3
cstr.setResidenceTime(24.0, "hr");
cstr.setAgitatorPowerPerVolume(1.5);         // 1.5 kW/m3

// Define lactic acid fermentation: C6H12O6 -> 2 C3H6O3
StoichiometricReaction rxn = new StoichiometricReaction("LacticFermentation");
rxn.addReactant("glucose", 1.0);
rxn.addProduct("lactic acid", 2.0);
rxn.setLimitingReactant("glucose");
rxn.setConversion(0.90);
cstr.addReaction(rxn);

// Run
cstr.run();

// Results
System.out.println("Heat duty: " + cstr.getHeatDuty("kW") + " kW");
System.out.println("Agitator power: " + cstr.getAgitatorPower() + " kW");
System.out.println("Outlet T: " + (cstr.getOutletStream().getTemperature() - 273.15) + " C");
```

---

### Fermenter

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `StirredTankReactor`

A bioreactor with additional bio-process features: aeration, oxygen mass transfer, pH control, and cell growth.

#### Mathematical Model

The fermenter inherits the CSTR model and adds:

**Aeration power** (simplified):

$$
W_\text{aeration} = 0.5 \cdot q_\text{vvm} \cdot V \qquad [\text{kW}]
$$

where $q_\text{vvm}$ is the aeration rate in volume of air per volume of liquid per minute (vvm).

**Total power:**

$$
W_\text{total} = W_\text{agitator} + W_\text{aeration}
$$

**Oxygen transfer rate (OTR):**

$$
\text{OTR} = k_L a \cdot (C^* - C_L) \qquad [\text{mol}\,\text{O}_2 / (\text{L} \cdot \text{hr})]
$$

where $k_L a$ is the volumetric mass transfer coefficient [1/hr], $C^*$ is the saturation dissolved oxygen concentration, and $C_L$ is the actual dissolved oxygen.

**Cell growth** (when cell yield $Y_{X/S}$ is specified):

$$
\Delta X = Y_{X/S} \cdot \Delta S
$$

where $\Delta S$ is the substrate consumed [g] and $\Delta X$ is the biomass produced [g].

#### Typical Operating Ranges

| Parameter | Aerobic | Anaerobic |
|-----------|---------|-----------|
| Temperature | 25-37 C | 30-55 C |
| Agitator power | 0.5-3 kW/m$^3$ | 0.3-1 kW/m$^3$ |
| Aeration rate | 0.5-2.0 vvm | 0 |
| $k_L a$ | 50-500 hr$^{-1}$ | N/A |
| Residence time | 12-72 hr | 24-120 hr |

#### API (additions over StirredTankReactor)

| Method | Description |
|--------|-------------|
| `setAerobic(bool)` | Enable aerobic mode |
| `setAerationRate(vvm)` | Aeration rate [vvm] |
| `setKLa(kLa)` | Volumetric mass transfer coefficient [1/hr] |
| `setTargetPH(pH)` | Target pH for controlled fermentation |
| `setCellYield(Y)` | Cell yield [g cells / g substrate] |
| `getTotalPower()` | Agitator + aeration power [kW] |
| `getAerationPower()` | Aeration compressor power [kW] |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface broth = new SystemSrkEos(273.15 + 32.0, 1.01325);
broth.addComponent("water", 0.80);
broth.addComponent("glucose", 0.15);
broth.addComponent("ethanol", 0.0);
broth.addComponent("CO2", 0.0);
broth.addComponent("glycerol", 0.05);
broth.setMixingRule("classic");

Stream feed = new Stream("Broth Feed", broth);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

Fermenter fermenter = new Fermenter("EtOH Fermenter", feed);
fermenter.setReactorTemperature(273.15 + 32.0);
fermenter.setResidenceTime(48.0, "hr");
fermenter.setVesselVolume(200.0);
fermenter.setAerobic(false);  // anaerobic ethanol fermentation

// Gay-Lussac equation: C6H12O6 -> 2 C2H5OH + 2 CO2
StoichiometricReaction etoh = new StoichiometricReaction("EtOHFermentation");
etoh.addReactant("glucose", 1.0);
etoh.addProduct("ethanol", 2.0);
etoh.addProduct("CO2", 2.0);
etoh.setLimitingReactant("glucose");
etoh.setConversion(0.90);
fermenter.addReaction(etoh);

fermenter.run();

System.out.println("Total power: " + fermenter.getTotalPower() + " kW");
```

---

### EnzymeTreatment

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `StirredTankReactor`

Models enzyme-catalyzed reactions (hydrolysis, saccharification, proteolysis). Operates at mild conditions with lower agitator power than a standard CSTR.

#### Mathematical Model

Inherits the full CSTR model. Adds an enzyme activity model:

**Relative activity:**

$$
a_\text{rel} = \max\left(0,\; 1 - k_T \cdot |T - T_\text{opt}|\right)
$$

where $k_T$ is the temperature sensitivity coefficient [1/K] (default 0.02) and $T_\text{opt}$ is the optimal enzyme temperature.

**Enzyme consumption:**

$$
\dot{m}_\text{enzyme} = E_L \cdot \dot{m}_\text{feed} \times 10^{-6} \qquad [\text{kg/hr}]
$$

where $E_L$ is the enzyme loading [mg enzyme / g substrate].

**Enzyme cost:**

$$
C_\text{enzyme} = \dot{m}_\text{enzyme} \cdot c_\text{enzyme} \qquad [\$/\text{hr}]
$$

#### Typical Conditions by Enzyme Type

| Enzyme | $T_\text{opt}$ | pH$_\text{opt}$ | Typical Loading (mg/g) |
|--------|:---------:|:---------:|:---:|
| Cellulase | 50 C | 4.8-5.0 | 10-30 |
| Amylase | 60-70 C | 5.5-6.5 | 0.5-2 |
| Glucoamylase | 55-65 C | 4.0-4.5 | 0.5-1 |
| Protease | 50-60 C | 6.5-8.0 | 1-5 |
| Lipase | 35-45 C | 7.0-8.0 | 1-10 |

#### API (additions over StirredTankReactor)

| Method | Description |
|--------|-------------|
| `setEnzymeLoading(EL)` | Loading [mg enzyme / g substrate] |
| `setEnzymeType(type)` | "cellulase", "amylase", "protease", etc. |
| `setOptimalPH(pH)` | Optimal enzyme pH |
| `setOptimalTemperature(T)` | Optimal temperature [K] |
| `setEnzymeHalfLife(t)` | Half-life at operating conditions [hr] |
| `setEnzymeCostPerKg(c)` | Enzyme cost [$/kg] |
| `getRelativeActivity()` | Activity factor $a_\text{rel} \in [0,1]$ |
| `getEnzymeConsumption()` | Enzyme usage rate [kg/hr] |
| `getEnzymeCostPerHour()` | Operating enzyme cost [$/hr] |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface substrate = new SystemSrkEos(273.15 + 50.0, 1.01325);
substrate.addComponent("water", 0.80);
substrate.addComponent("sucrose", 0.15);
substrate.addComponent("glucose", 0.03);
substrate.addComponent("fructose", 0.02);
substrate.setMixingRule("classic");

Stream feed = new Stream("Substrate Feed", substrate);
feed.setFlowRate(2000.0, "kg/hr");
feed.run();

EnzymeTreatment hydrolysis = new EnzymeTreatment("Invertase Treatment", feed);
hydrolysis.setReactorTemperature(273.15 + 50.0);
hydrolysis.setResidenceTime(4.0, "hr");
hydrolysis.setEnzymeLoading(5.0);
hydrolysis.setEnzymeType("invertase");
hydrolysis.setOptimalPH(4.5);
hydrolysis.setEnzymeCostPerKg(10.0);

// Sucrose inversion: C12H22O11 + H2O -> C6H12O6 + C6H12O6
StoichiometricReaction rxn = new StoichiometricReaction("SucroseInversion");
rxn.addReactant("sucrose", 1.0);
rxn.addProduct("glucose", 1.0);
rxn.addProduct("fructose", 1.0);
rxn.setLimitingReactant("sucrose");
rxn.setConversion(0.95);
hydrolysis.addReaction(rxn);

hydrolysis.run();

System.out.println("Relative activity: " + hydrolysis.getRelativeActivity());
System.out.println("Enzyme consumption: " + hydrolysis.getEnzymeConsumption() + " kg/hr");
System.out.println("Enzyme cost: $" + hydrolysis.getEnzymeCostPerHour() + "/hr");
```

---

## Solid-Liquid Separators

### SolidsSeparator

**Package:** `neqsim.process.equipment.separator`
**Extends:** `ProcessEquipmentBaseClass` (two outlets: solids + liquid)

Base class for all solid-liquid separation equipment. Splits the feed into a solids-rich stream (cake) and a liquid-clear stream (filtrate) using component-specific split fractions.

#### Mathematical Model

For each component $i$ in the feed, the split is governed by a split fraction $\alpha_i$:

$$
n_{i,\text{solids}} = n_{i,\text{feed}} \cdot \alpha_i
$$

$$
n_{i,\text{liquid}} = n_{i,\text{feed}} \cdot (1 - \alpha_i)
$$

where $\alpha_i$ is the fraction of component $i$ directed to the solids outlet.

Components without an explicit $\alpha_i$ use the default split fraction $\alpha_\text{default}$ (typically 0 — everything goes to liquid).

After the split, both outlet systems are flashed at the feed conditions (temperature, pressure minus $\Delta P$) using a TP-flash.

**Power consumption:**

$$
W = E_s \cdot \dot{V}_\text{feed} \qquad [\text{kW}]
$$

where $E_s$ is the specific energy [kWh/m$^3$] and $\dot{V}_\text{feed}$ is the feed volumetric flow rate [m$^3$/hr].

#### API

| Method | Description |
|--------|-------------|
| `setInletStream(stream)` | Set feed stream |
| `setSolidsSplitFraction(name, alpha)` | Set split fraction $\alpha_i$ for component |
| `setDefaultSolidsSplit(alpha)` | Default $\alpha$ for unspecified components |
| `setMoistureContent(wt)` | Target cake moisture (mass fraction) |
| `setPressureDrop(dP)` | Pressure drop [bar] |
| `setSpecificEnergy(E)` | Specific energy [kWh/m$^3$] |
| `getSolidsOutStream()` | Get cake/retentate outlet |
| `getLiquidOutStream()` | Get filtrate/permeate outlet |
| `getPowerConsumption()` | Get power [kW] after run |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface slurry = new SystemSrkEos(273.15 + 25.0, 1.01325);
slurry.addComponent("water", 0.85);
slurry.addComponent("glucose", 0.10);
slurry.addComponent("lactic acid", 0.05);
slurry.setMixingRule("classic");

Stream feed = new Stream("Slurry Feed", slurry);
feed.setFlowRate(500.0, "kg/hr");
feed.run();

SolidsSeparator sep = new SolidsSeparator("Separator", feed);
sep.setSolidsSplitFraction("glucose", 0.95);      // 95% glucose to solids
sep.setSolidsSplitFraction("lactic acid", 0.10);   // 10% acid to solids
// water defaults to 0% solids
sep.setMoistureContent(0.50);
sep.run();

StreamInterface cake = sep.getSolidsOutStream();
StreamInterface filtrate = sep.getLiquidOutStream();
```

---

### SolidsCentrifuge

**Extends:** `SolidsSeparator`

Centrifugal solid-liquid separation. Higher energy, better separation.

| Parameter | Default | Range |
|-----------|---------|-------|
| Specific energy | 5.0 kWh/m$^3$ | 3-10 |
| Moisture content | 0.40 | 0.30-0.60 |
| G-force | 3000 g | 1000-10000 |

**Additional method:** `setGForce(g)` — set relative centrifugal force.

```java
SolidsCentrifuge centrifuge = new SolidsCentrifuge("Centrifuge", feed);
centrifuge.setSolidsSplitFraction("biomass", 0.99);
centrifuge.setGForce(5000.0);
centrifuge.run();
```

### RotaryVacuumFilter

**Extends:** `SolidsSeparator`

Continuous vacuum filtration. Lower energy, wetter cake.

| Parameter | Default | Range |
|-----------|---------|-------|
| Specific energy | 2.0 kWh/m$^3$ | 1-3 |
| Moisture content | 0.60 | 0.50-0.70 |
| Vacuum pressure | 0.5 bara | 0.3-0.7 |

**Additional methods:** `setVacuumPressure(P)`, `setFilterArea(A)`, `setSpecificCakeResistance(r)`.

### PressureFilter

**Extends:** `SolidsSeparator`

Batch/semi-continuous pressure filtration.

| Parameter | Default | Range |
|-----------|---------|-------|
| Specific energy | 3.0 kWh/m$^3$ | 2-5 |
| Moisture content | 0.45 | 0.35-0.55 |
| Operating pressure | 5.0 barg | 2-10 |

**Additional methods:** `setOperatingPressure(P)`, `setFilterArea(A)`.

### ScrewPress

**Extends:** `SolidsSeparator`

Mechanical dewatering via rotating screw compression. Lowest energy consumption.

| Parameter | Default | Range |
|-----------|---------|-------|
| Specific energy | 1.0 kWh/m$^3$ | 0.5-2 |
| Moisture content | 0.65 | 0.55-0.75 |
| Screw speed | 5 RPM | 1-20 |

**Additional methods:** `setScrewSpeed(rpm)`, `setCompressionRatio(r)`.

### Equipment Selection Guide

| Equipment | Best For | Energy | Cake Dryness |
|-----------|----------|--------|:------------:|
| Centrifuge | Fine particles, cell mass, high throughput | High | Best |
| Rotary Vacuum Filter | Coarse solids, continuous operations | Low | Moderate |
| Pressure Filter | Fine particles, clarification, pharma | Medium | Good |
| Screw Press | Fiber dewatering, large particles | Lowest | Poorest |

---

## Liquid-Liquid Extraction

### LiquidLiquidExtractor

**Package:** `neqsim.process.equipment.separator`
**Extends:** `ProcessEquipmentBaseClass` (two inlets, two outlets)

Models liquid-liquid extraction by mixing a feed stream with a solvent stream and performing a thermodynamic LLE flash to separate into extract and raffinate phases.

#### Mathematical Model

**Mixing step** — The feed and solvent systems are combined:

$$
n_{i,\text{mix}} = n_{i,\text{feed}} + n_{i,\text{solvent}}
$$

**Equilibrium flash** — A TP-flash with `multiPhaseCheck = true` is performed on the combined system at:

$$
T = T_\text{feed}, \qquad P = P_\text{feed} - \Delta P
$$

NeqSim solves the multi-phase Rachford-Rice equations:

$$
\sum_i \frac{z_i (K_i - 1)}{1 + \beta(K_i - 1)} = 0
$$

where $z_i$ are overall mole fractions, $K_i$ are equilibrium ratios, and $\beta$ is the phase fraction.

**Phase separation** — The resulting phases are assigned:
- Phase 0 (lighter) $\to$ extract stream (solvent-rich)
- Phase 1 (heavier) $\to$ raffinate stream (feed-rich)

#### Distribution Coefficient

The partition of a solute $s$ between the two liquid phases is characterized by:

$$
K_D = \frac{x_{s,\text{extract}}}{x_{s,\text{raffinate}}}
$$

This is computed rigorously from the equation of state (SRK, PR, CPA, etc.) — no empirical partition coefficients are needed.

#### API

| Method | Description |
|--------|-------------|
| `setFeedStream(stream)` | Set aqueous/original feed |
| `setSolventStream(stream)` | Set extraction solvent |
| `setNumberOfStages(n)` | Theoretical stages (currently single-stage flash) |
| `setStageEfficiency(eta)` | Stage efficiency [0-1] |
| `setPressureDrop(dP)` | Pressure drop [bar] |
| `getExtractStream()` | Get solvent-rich extract outlet |
| `getRaffinateStream()` | Get feed-rich raffinate outlet |

#### Important: Component Compatibility

When combining feed and solvent systems, both systems must contain all the same components (use 0.0 moles for absent components). This ensures the mixing rule interaction parameter matrices are consistently sized.

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

// CORRECT: Both systems have all 3 components
SystemInterface feedSys = new SystemSrkEos(298.15, 1.01325);
feedSys.addComponent("water", 5.0);
feedSys.addComponent("lactic acid", 0.5);
feedSys.addComponent("1-butanol", 0.0);   // absent but declared
feedSys.setMixingRule("classic");

SystemInterface solventSys = new SystemSrkEos(298.15, 1.01325);
solventSys.addComponent("water", 0.0);         // absent but declared
solventSys.addComponent("lactic acid", 0.0);   // absent but declared
solventSys.addComponent("1-butanol", 3.0);
solventSys.setMixingRule("classic");
```

#### Simulation Example

```java
Stream feed = new Stream("Feed", feedSys);
feed.setFlowRate(1000.0, "kg/hr");
feed.run();

Stream solvent = new Stream("Solvent", solventSys);
solvent.setFlowRate(500.0, "kg/hr");
solvent.run();

LiquidLiquidExtractor lle = new LiquidLiquidExtractor("Extractor", feed, solvent);
lle.setNumberOfStages(3);
lle.run();

StreamInterface extract = lle.getExtractStream();
StreamInterface raffinate = lle.getRaffinateStream();

System.out.println("Extract phases: " + extract.getThermoSystem().getNumberOfPhases());
System.out.println("Raffinate phases: " + raffinate.getThermoSystem().getNumberOfPhases());
```

---

## Thermal Processing

### MultiEffectEvaporator

**Package:** `neqsim.process.equipment.heatexchanger`
**Extends:** `ProcessEquipmentBaseClass` (two outlets: concentrate + vapor condensate)

Models a series of evaporator effects at decreasing pressures. The vapor from each effect provides heating for the next, achieving significant steam economy.

#### Mathematical Model

**Pressure distribution** — Effect pressures are spaced geometrically between $P_1$ (first) and $P_N$ (last):

$$
P_k = P_1 \cdot \left(\frac{P_N}{P_1}\right)^{\frac{k-1}{N-1}}, \qquad k = 1, \ldots, N
$$

**Per-effect calculation** — At each effect $k$, the liquid from the previous effect is flashed at pressure $P_k$:

$$
\text{TP-flash}(T_k, P_k) \;\to\; \text{vapor}_k + \text{liquid}_k
$$

The vapor is separated and accumulated; the liquid passes to the next effect.

**Steam economy** — A simplified estimate of the overall steam economy:

$$
\eta_\text{steam} \approx 0.8 \cdot N
$$

where $N$ is the number of effects. For example, a 3-effect evaporator evaporates approximately 2.4 kg water per kg of live steam.

**Overall energy:** The total vapor removed is the sum of vapor from all effects:

$$
\dot{m}_\text{vapor} = \sum_{k=1}^{N} \dot{m}_{\text{vapor},k}
$$

#### Typical Parameters

| Parameter | Range |
|-----------|-------|
| Number of effects | 2-7 |
| First effect pressure | 1.5-4 bara |
| Last effect pressure | 0.1-0.5 bara |
| Heat transfer coefficient U | 1000-3000 W/(m$^2$K) |
| Concentration factor | 2-10x |

#### API

| Method | Description |
|--------|-------------|
| `setInletStream(stream)` | Set dilute feed |
| `setNumberOfEffects(N)` | Number of effects |
| `setFirstEffectPressure(P)` | Highest pressure [bara] |
| `setLastEffectPressure(P)` | Lowest pressure [bara] |
| `setTargetConcentrationFactor(f)` | Target feed/concentrate ratio |
| `setOverallHeatTransferCoefficient(U)` | U-value [W/(m$^2$K)] |
| `getConcentrateStream()` | Concentrated product outlet |
| `getVaporCondensateStream()` | Combined vapor/condensate outlet |
| `getSteamEconomy()` | Steam economy after run |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface juice = new SystemSrkEos(273.15 + 80.0, 1.5);
juice.addComponent("water", 0.85);
juice.addComponent("glucose", 0.15);
juice.setMixingRule("classic");

Stream feed = new Stream("Dilute Juice", juice);
feed.setFlowRate(10000.0, "kg/hr");
feed.run();

MultiEffectEvaporator mee = new MultiEffectEvaporator("MEE-3", feed);
mee.setNumberOfEffects(3);
mee.setFirstEffectPressure(2.0);
mee.setLastEffectPressure(0.2);
mee.setTargetConcentrationFactor(5.0);
mee.run();

System.out.println("Steam economy: " + mee.getSteamEconomy());
StreamInterface concentrate = mee.getConcentrateStream();
StreamInterface condensate = mee.getVaporCondensateStream();
```

---

### Dryer

**Package:** `neqsim.process.equipment.heatexchanger`
**Extends:** `ProcessEquipmentBaseClass` (two outlets: dried product + vapor)

Models drying equipment (drum, spray, flash, rotary) that removes moisture from a wet feed by heating and phase separation.

#### Mathematical Model

**Flash model** — The feed is flashed at the outlet temperature and pressure:

$$
\text{TP-flash}(T_\text{out}, P_\text{out}) \;\to\; \text{vapor (moisture)} + \text{dried product}
$$

**Energy balance:**

$$
\dot{Q}_\text{actual} = \frac{H_\text{out} - H_\text{in}}{\eta_\text{thermal}} \qquad [\text{W}]
$$

where $\eta_\text{thermal}$ is the thermal efficiency (fraction of heat input used for evaporation, typically 0.80-0.95).

**Phase separation** — After the flash:
- Gas phase $\to$ vapor stream (evaporated moisture)
- Liquid phase $\to$ dried product stream

#### Typical Operating Conditions

| Dryer Type | Outlet Temp | Typical $\eta$ | Applications |
|------------|:-----------:|:---------:|--------------|
| Drum | 100-140 C | 0.80-0.90 | Pastes, slurries |
| Spray | 80-200 C | 0.50-0.70 | Milk powder, detergents |
| Flash | 100-300 C | 0.60-0.80 | Starches, minerals |
| Rotary | 80-120 C | 0.70-0.85 | Biomass, grain |

#### API

| Method | Description |
|--------|-------------|
| `setInletStream(stream)` | Set wet feed |
| `setDryerType(type)` | "drum", "spray", "flash", "rotary" |
| `setOutletTemperature(T)` | Target outlet temperature [K] |
| `setOutletTemperature(T, unit)` | With unit ("K", "C", "F") |
| `setTargetMoistureContent(wt)` | Target moisture mass fraction |
| `setThermalEfficiency(eta)` | Thermal efficiency [0-1] |
| `setPressureDrop(dP)` | Pressure drop [bar] |
| `getDriedProductStream()` | Dried product outlet |
| `getVaporStream()` | Vapor/moisture outlet |
| `getHeatDuty()` | Heat duty [W] after run |
| `getHeatDuty(unit)` | Heat duty in "W", "kW", "MW" |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface wetProduct = new SystemSrkEos(273.15 + 60.0, 1.01325);
wetProduct.addComponent("water", 0.70);
wetProduct.addComponent("d-sorbitol", 0.30);
wetProduct.setMixingRule("classic");

Stream feed = new Stream("Wet Feed", wetProduct);
feed.setFlowRate(800.0, "kg/hr");
feed.run();

Dryer dryer = new Dryer("Drum Dryer", feed);
dryer.setDryerType("drum");
dryer.setOutletTemperature(105.0, "C");
dryer.setTargetMoistureContent(0.05);
dryer.setThermalEfficiency(0.85);
dryer.run();

System.out.println("Heat duty: " + dryer.getHeatDuty("kW") + " kW");
StreamInterface dried = dryer.getDriedProductStream();
StreamInterface vapor = dryer.getVaporStream();
```
---

## Crystallization

### Crystallizer

**Package:** `neqsim.process.equipment.separator`
**Extends:** `ProcessEquipmentBaseClass` (two outlets: crystals + mother liquor)

Models crystallization processes: cooling, evaporative, or anti-solvent crystallization.

#### Mathematical Model

**Cooling crystallization** — The feed is cooled to a target temperature and flashed. The solute that exceeds solubility precipitates.

**Simplified split model** — For the target solute component, a recovery fraction $R$ defines the crystal yield:

$$
n_{\text{solute},\text{crystal}} = n_{\text{solute},\text{feed}} \cdot R
$$

For all other components, a small entrainment fraction $\varepsilon$ (default 0.02) accounts for mother liquor trapped in the crystal cake:

$$
n_{i,\text{crystal}} = n_{i,\text{feed}} \cdot \varepsilon \qquad (i \neq \text{solute})
$$

$$
n_{i,\text{mother liquor}} = n_{i,\text{feed}} - n_{i,\text{crystal}}
$$

**Energy balance:**

$$
\dot{Q} = H_\text{out} - H_\text{in} \qquad [\text{W}]
$$

where $H_\text{out}$ is evaluated at the target temperature/pressure after a TP-flash.

#### Crystallization Types

| Type | Mechanism | Key Parameter |
|------|-----------|---------------|
| Cooling | Reduce temperature | `setOutletTemperature(T)` |
| Evaporative | Reduce pressure (remove solvent) | `setOutletPressure(P)` |
| Anti-solvent | Add anti-solvent | Combine streams before crystallizer |

#### API

| Method | Description |
|--------|-------------|
| `setInletStream(stream)` | Set saturated feed |
| `setCrystallizationType(type)` | "cooling", "evaporative", "antisolvent" |
| `setOutletTemperature(T)` | Cooling target [K] |
| `setOutletTemperature(T, unit)` | With unit ("K", "C", "F") |
| `setOutletPressure(P)` | Evaporative target [bara] |
| `setTargetSolute(name)` | Component to crystallize |
| `setSolidRecovery(R)` | Recovery fraction [0-1] |
| `setCrystalPurity(p)` | Crystal purity (mass fraction) |
| `setResidenceTime(hr)` | Residence time [hr] |
| `getCrystalStream()` | Crystal/solid outlet |
| `getMotherLiquorStream()` | Mother liquor outlet |
| `getHeatDuty()` | Heat duty [W] |

#### Simulation Example

```java
NeqSimDataBase.useExtendedComponentDatabase(true);

SystemInterface solution = new SystemSrkEos(273.15 + 80.0, 1.01325);
solution.addComponent("water", 0.70);
solution.addComponent("citric acid", 0.30);
solution.setMixingRule("classic");

Stream feed = new Stream("Saturated Solution", solution);
feed.setFlowRate(1000.0, "kg/hr");
feed.run();

Crystallizer cryst = new Crystallizer("Cooling Crystallizer", feed);
cryst.setCrystallizationType("cooling");
cryst.setOutletTemperature(30.0, "C");
cryst.setTargetSolute("citric acid");
cryst.setSolidRecovery(0.85);
cryst.setCrystalPurity(0.98);
cryst.setResidenceTime(4.0);
cryst.run();

StreamInterface crystals = cryst.getCrystalStream();
StreamInterface liquor = cryst.getMotherLiquorStream();
System.out.println("Heat duty: " + cryst.getHeatDuty() + " W");
```

---

## Integration with ProcessSystem

All bio-processing equipment can be added to a `ProcessSystem` and run as part of a complete flowsheet:

```java
ProcessSystem process = new ProcessSystem();

// Build flowsheet
process.add(feed);
process.add(fermenter);
process.add(centrifuge);
process.add(evaporator);
process.add(dryer);

// Run entire process
process.run();

// Access any equipment results
double fermentationHeat = fermenter.getHeatDuty("kW");
double centrifugePower = centrifuge.getPowerConsumption();
double dryerHeat = dryer.getHeatDuty("kW");
```

### Connecting Equipment

Equipment is connected through stream objects. The outlet of one unit becomes the inlet of the next:

```java
// Fermenter -> Centrifuge -> Dryer
Fermenter fermenter = new Fermenter("Fermenter", feed);
// ... configure ...

SolidsCentrifuge centrifuge = new SolidsCentrifuge("Centrifuge");
// Must set the inlet after fermenter runs, or use ProcessSystem which handles sequencing

// With ProcessSystem, connections are implicit through shared stream objects
Stream fermentedBroth = (Stream) fermenter.getOutletStream();
centrifuge.setInletStream(fermentedBroth);
```

---

## Complete Biorefinery Example

```java
import neqsim.process.equipment.reactor.*;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.database.NeqSimDataBase;

// Enable extended component database for bio-processing components
NeqSimDataBase.useExtendedComponentDatabase(true);

// 1. Create feed fluid with glucose substrate
SystemInterface feedFluid = new SystemSrkEos(273.15 + 30.0, 1.01325);
feedFluid.addComponent("water", 0.80);
feedFluid.addComponent("glucose", 0.15);
feedFluid.addComponent("ethanol", 0.0);
feedFluid.addComponent("CO2", 0.0);
feedFluid.addComponent("glycerol", 0.05);
feedFluid.setMixingRule("classic");

Stream feed = new Stream("Sugar Feed", feedFluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.run();

// 2. Fermentation: C6H12O6 -> 2 C2H5OH + 2 CO2
StoichiometricReaction fermentation = new StoichiometricReaction("GlucoseToEthanol");
fermentation.addReactant("glucose", 1.0);
fermentation.addProduct("ethanol", 2.0);
fermentation.addProduct("CO2", 2.0);
fermentation.setLimitingReactant("glucose");
fermentation.setConversion(0.90);

Fermenter fermenter = new Fermenter("Fermenter", feed);
fermenter.setReactorTemperature(273.15 + 32.0);
fermenter.setVesselVolume(500.0);
fermenter.setResidenceTime(48.0, "hr");
fermenter.setAerobic(false);
fermenter.addReaction(fermentation);
fermenter.run();

// 3. Centrifuge - remove cell mass and residual glucose
SolidsCentrifuge centrifuge = new SolidsCentrifuge("Cell Separator",
    fermenter.getOutletStream());
centrifuge.setSolidsSplitFraction("glucose", 0.95);
centrifuge.setGForce(5000.0);
centrifuge.run();

// 4. Evaporator - concentrate ethanol
MultiEffectEvaporator evaporator = new MultiEffectEvaporator("Concentrator",
    centrifuge.getLiquidOutStream());
evaporator.setNumberOfEffects(3);
evaporator.setFirstEffectPressure(1.5);
evaporator.setLastEffectPressure(0.3);
evaporator.run();

// 5. Results
System.out.println("=== Biorefinery Results ===");
System.out.println("Fermenter heat duty: " + fermenter.getHeatDuty("kW") + " kW");
System.out.println("Fermenter total power: " + fermenter.getTotalPower() + " kW");
System.out.println("Centrifuge power: " + centrifuge.getPowerConsumption() + " kW");
System.out.println("Evaporator steam economy: " + evaporator.getSteamEconomy());

// Reset database
NeqSimDataBase.useExtendedComponentDatabase(false);
```

---

## Summary of Classes

| Class | Package | Ports | Key Parameters |
|-------|---------|:-----:|----------------|
| `StoichiometricReaction` | `reactor` | N/A | stoichiometry, conversion, limiting reactant |
| `StirredTankReactor` | `reactor` | 1 in, 1 out | temperature, pressure, volume, reactions |
| `Fermenter` | `reactor` | 1 in, 1 out | aeration, kLa, pH, cell yield |
| `EnzymeTreatment` | `reactor` | 1 in, 1 out | enzyme loading, type, optimal T/pH |
| `SolidsSeparator` | `separator` | 1 in, 2 out | split fractions, moisture, energy |
| `SolidsCentrifuge` | `separator` | 1 in, 2 out | G-force |
| `RotaryVacuumFilter` | `separator` | 1 in, 2 out | vacuum pressure, filter area |
| `PressureFilter` | `separator` | 1 in, 2 out | operating pressure, filter area |
| `ScrewPress` | `separator` | 1 in, 2 out | screw speed, compression ratio |
| `LiquidLiquidExtractor` | `separator` | 2 in, 2 out | stages, efficiency |
| `MultiEffectEvaporator` | `heatexchanger` | 1 in, 2 out | effects, pressures, concentration factor |
| `Dryer` | `heatexchanger` | 1 in, 2 out | type, outlet T, moisture, efficiency |
| `Crystallizer` | `separator` | 1 in, 2 out | type, solute, recovery, purity |

---

## Bioenergy Classes

The bioenergy classes model the full value chain from biomass feedstock to energy product.

### AnaerobicDigester

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `Fermenter`

Models anaerobic digestion of organic waste producing biogas (CH4 + CO2) and digestate.

**Substrate types:** FOOD_WASTE, SEWAGE_SLUDGE, MANURE, CROP_RESIDUE, ENERGY_CROPS, INDUSTRIAL_WASTE, MUNICIPAL_SOLID_WASTE — each with default specific methane yield, VS destruction, and VS/TS ratio.

```java
AnaerobicDigester digester = new AnaerobicDigester("AD-1", feedStream);
digester.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
digester.setDigesterTemperature(37.0, "C");
digester.setFeedRate(10000.0, 0.25);  // kg/hr, total solids fraction
digester.setVesselVolume(5000.0);     // m3
digester.run();

StreamInterface biogas = digester.getBiogasOutStream();
StreamInterface digestate = digester.getDigestateOutStream();
```

### BiomassGasifier

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `StirredTankReactor`

Thermochemical gasification of biomass, producing syngas (H2, CO, CO2, CH4, N2).

```java
BiomassGasifier gasifier = new BiomassGasifier("Gasifier-1");
BiomassCharacterization wood = new BiomassCharacterization("wood_chips");
// set proximate/ultimate analysis on wood...
wood.calculate();
gasifier.setBiomass(wood, 5000.0);  // 5 t/hr
gasifier.setGasificationTemperature(273.15 + 850.0);
gasifier.run();

StreamInterface syngas = gasifier.getSyngasOutStream();
StreamInterface charAsh = gasifier.getCharAshOutStream();
```

### BiogasUpgrader

**Package:** `neqsim.process.equipment.splitter`
**Extends:** `ComponentSplitter`

Purifies raw biogas to biomethane spec by removing CO2, H2S, and moisture.

**Technologies:** WATER_SCRUBBING, AMINE_SCRUBBING, PSA, MEMBRANE, CRYOGENIC — each with specific CO2 removal efficiency, CH4 recovery, and energy consumption.

```java
BiogasUpgrader upgrader = new BiogasUpgrader("Upgrader", biogasStream);
upgrader.setTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
upgrader.run();

StreamInterface biomethane = upgrader.getBiomethaneOutStream();
StreamInterface offgas = upgrader.getOffgasOutStream();
```

### FermentationReactor

**Package:** `neqsim.process.equipment.reactor`
**Extends:** `Fermenter`

Advanced fermentation modeling with multiple kinetic models and operation modes.

**Kinetic models:**

| Model | Growth Rate Equation |
|-------|---------------------|
| MONOD | $\mu = \mu_{max} \frac{S}{K_s + S}$ |
| CONTOIS | $\mu = \mu_{max} \frac{S}{K_{sx} X + S}$ |
| SUBSTRATE_INHIBITED | $\mu = \mu_{max} \frac{S}{K_s + S + S^2/K_i}$ |
| PRODUCT_INHIBITED | $\mu = \mu_{max} \frac{S}{K_s + S}(1 - P/P_{max})$ |

**Operation modes:** CONTINUOUS (steady-state CSTR), BATCH (time integration), FED_BATCH (variable volume).

```java
FermentationReactor reactor = new FermentationReactor("FR-1", feed);
reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
reactor.setSubstrateConcentration(100.0);  // g/L
reactor.setBiomassConcentration(1.0);      // g/L
reactor.setMaxSpecificGrowthRate(0.30);    // 1/hr
reactor.setMonodConstant(1.0);             // g/L
reactor.setYieldBiomass(0.10);
reactor.setYieldProduct(0.45);
reactor.setResidenceTime(10.0, "hr");
reactor.run();

Map<String, Object> results = reactor.getResults();
// Keys: finalProductConc_g_per_L, finalSubstrateConc_g_per_L,
//       substrateConversion, productivity_g_per_L_hr, etc.
```

### SustainabilityMetrics

**Package:** `neqsim.process.util.fielddevelopment`
**Implements:** `Serializable`

LCA/sustainability KPI calculator for biorefinery processes. Tracks CO2-equivalent
emissions (IPCC AR6 GWP values), carbon intensity, EROI, and fossil fuel displacement.

```java
SustainabilityMetrics metrics = new SustainabilityMetrics();
metrics.setBiogasProductionNm3PerYear(2_000_000.0);
metrics.setMethaneContentFraction(0.60);
metrics.setElectricityProductionMWhPerYear(5000.0);
metrics.setHeatProductionMWhPerYear(6000.0);
metrics.setParasiticElectricityMWhPerYear(500.0);
metrics.setMethaneSlipPercent(1.5);
metrics.setFossilReferenceEmissionFactor(0.450);
metrics.calculate();

double carbonIntensity = metrics.getCarbonIntensityKgCO2PerMWh();
double netCarbon = metrics.getNetCarbonBalanceTCO2PerYear();
double eroi = metrics.getEnergyReturnOnInvestment();
String json = metrics.toJson();
```

---

## Pre-Built Biorefinery Modules

These modules extend `ProcessModule` and compose multiple equipment into
ready-to-use biorefinery process chains.

### BiogasToGridModule

Anaerobic digestion + biogas upgrading + compression + cooling for grid injection.

```java
BiogasToGridModule module = new BiogasToGridModule("BTG-Plant");
module.setFeedStream(wasteStream);
module.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
module.setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
module.setGridPressureBara(40.0);
module.setGridTemperatureC(25.0);
module.run();

StreamInterface biomethane = module.getBiomethaneOutStream();
```

### GasificationSynthesisModule

Biomass gasification + syngas cooling + Fischer-Tropsch synthesis + product separation.

```java
GasificationSynthesisModule module = new GasificationSynthesisModule("GTL");
BiomassCharacterization wood = new BiomassCharacterization("wood_chips");
// configure biomass...
wood.calculate();
module.setBiomass(wood, 5000.0);
module.setGasificationTemperatureC(850.0);
module.setFtReactorTemperatureC(220.0);
module.run();
```

### WasteToEnergyCHPModule

Anaerobic digestion + CHP engine for combined heat and power from waste.

```java
WasteToEnergyCHPModule module = new WasteToEnergyCHPModule("CHP-Plant");
module.setFeedStream(wasteStream);
module.setSubstrateType(AnaerobicDigester.SubstrateType.SEWAGE_SLUDGE);
module.setElectricalEfficiency(0.38);
module.setThermalEfficiency(0.45);
module.setOperatingHoursPerYear(8000.0);
module.run();

double powerKW = module.getElectricalPowerKW();
double heatKW = module.getHeatOutputKW();
double co2 = module.getCO2EmissionsKgPerHr();
```
