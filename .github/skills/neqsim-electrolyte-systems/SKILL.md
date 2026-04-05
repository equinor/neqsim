---
name: neqsim-electrolyte-systems
description: "Electrolyte and brine chemistry guidance for NeqSim. USE WHEN: modeling produced water, scale prediction, CO2/H2S in aqueous systems, MEG/DEG injection, hydrate inhibitor dosing, or any system with ions, salts, or electrolytes. Covers SystemElectrolyteCPAstatoil setup, ion components, scale risk assessment, and brine handling patterns."
---

# Electrolyte Systems Guide

Guide for modeling electrolyte/brine systems in NeqSim.

## When to Use Electrolyte Models

- Produced water with dissolved salts (NaCl, CaCl2, BaCl4)
- Scale prediction (CaCO3, BaSO4, CaSO4)
- CO2/H2S solubility in brines
- MEG injection rate calculations
- Hydrate inhibitor dosing
- Seawater injection and mixing
- Desalination process modeling

## EOS Selection for Electrolyte Systems

| System | NeqSim Class | Mixing Rule |
|--------|-------------|-------------|
| Water + salt + HC gas | `SystemElectrolyteCPAstatoil` | `10` |
| MEG/DEG + water + gas | `SystemSrkCPAstatoil` | `10` |
| Pure water + gas | `SystemSrkCPAstatoil` | `10` |
| Brine + multiple salts | `SystemElectrolyteCPAstatoil` | `10` |

## Basic Electrolyte Setup

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

// Create electrolyte system
SystemInterface brine = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 200.0);

// Add gas components
brine.addComponent("CO2", 0.05);
brine.addComponent("methane", 0.80);

// Add water
brine.addComponent("water", 0.10);

// Add salt components (as ions)
brine.addComponent("Na+", 0.02);
brine.addComponent("Cl-", 0.02);
brine.addComponent("Ca++", 0.005);
brine.addComponent("SO4--", 0.005);

// Set mixing rule (MUST be numeric for CPA)
brine.setMixingRule(10);
brine.setMultiPhaseCheck(true);
```

## Common Ion Components in NeqSim

| Ion | NeqSim Name | Common Source |
|-----|------------|---------------|
| Sodium | `"Na+"` | NaCl |
| Chloride | `"Cl-"` | NaCl, CaCl2 |
| Calcium | `"Ca++"` | CaCl2, CaCO3 |
| Barium | `"Ba++"` | BaSO4, BaCl2 |
| Strontium | `"Sr++"` | SrSO4 |
| Sulfate | `"SO4--"` | Na2SO4, BaSO4 |
| Bicarbonate | `"HCO3-"` | NaHCO3 |
| Carbonate | `"CO3--"` | CaCO3, Na2CO3 |
| Magnesium | `"Mg++"` | MgCl2 |
| Potassium | `"K+"` | KCl |
| Iron(II) | `"Fe++"` | FeCl2, FeS |

## Produced Water Modeling

```java
// Typical produced water composition
SystemInterface prodWater = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 50.0);

// Dissolved gas
prodWater.addComponent("CO2", 0.01);
prodWater.addComponent("H2S", 0.001);
prodWater.addComponent("methane", 0.005);

// Water (dominant)
prodWater.addComponent("water", 0.90);

// Ions (typical North Sea produced water)
prodWater.addComponent("Na+", 0.03);
prodWater.addComponent("Cl-", 0.035);
prodWater.addComponent("Ca++", 0.003);
prodWater.addComponent("Ba++", 0.0001);
prodWater.addComponent("SO4--", 0.001);
prodWater.addComponent("HCO3-", 0.005);

prodWater.setMixingRule(10);
prodWater.setMultiPhaseCheck(true);

// Flash to get gas/liquid split
ThermodynamicOperations ops = new ThermodynamicOperations(prodWater);
ops.TPflash();
prodWater.initProperties();
```

## Scale Risk Assessment

### CaCO3 (Calcite) Scaling

Scale forms when the product of ion activities exceeds the solubility product:

$$
SI = \log_{10}\left(\frac{a_{Ca^{2+}} \cdot a_{CO_3^{2-}}}{K_{sp}}\right)
$$

Where $SI > 0$ indicates supersaturation (scaling risk).

```java
// Check for scale tendency by mixing formation water with seawater
SystemInterface mixed = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 50.0);
// Add components from both waters...
mixed.setMixingRule(10);
mixed.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(mixed);
ops.TPflash();

// Check for solid phase (scale)
if (mixed.hasPhaseType("solid")) {
    // Scale predicted
    double solidAmount = mixed.getPhase("solid").getNumberOfMolesInPhase();
}
```

### BaSO4 (Barite) Scaling

Most problematic scale in oil production — forms when Ba++ meets SO4--.

```java
// Formation water (high Ba++)
SystemInterface formWater = new SystemElectrolyteCPAstatoil(273.15 + 90.0, 200.0);
formWater.addComponent("water", 0.95);
formWater.addComponent("Na+", 0.025);
formWater.addComponent("Cl-", 0.020);
formWater.addComponent("Ba++", 0.001);  // High barium
formWater.addComponent("Sr++", 0.0005);
formWater.setMixingRule(10);

// Injection water (high SO4--)
SystemInterface injWater = new SystemElectrolyteCPAstatoil(273.15 + 20.0, 200.0);
injWater.addComponent("water", 0.96);
injWater.addComponent("Na+", 0.015);
injWater.addComponent("Cl-", 0.015);
injWater.addComponent("SO4--", 0.01);   // High sulfate
injWater.setMixingRule(10);
```

## MEG Injection Calculations

```java
// Wet gas with MEG injection for hydrate prevention
SystemInterface wetGas = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
wetGas.addComponent("methane", 0.85);
wetGas.addComponent("ethane", 0.08);
wetGas.addComponent("propane", 0.03);
wetGas.addComponent("water", 0.03);
wetGas.addComponent("MEG", 0.01);  // Monoethylene glycol
wetGas.setMixingRule(10);
wetGas.setMultiPhaseCheck(true);

// Check hydrate temperature with MEG
ThermodynamicOperations ops = new ThermodynamicOperations(wetGas);
ops.hydrateFormationTemperature();
double hydrateT = wetGas.getTemperature() - 273.15;  // °C

// Compare with and without MEG to get suppression
```

## CO2 Solubility in Brine

Important for CCS and EOR projects:

```java
// CO2 solubility decreases with salinity (salting-out effect)
SystemInterface co2Brine = new SystemElectrolyteCPAstatoil(273.15 + 50.0, 100.0);
co2Brine.addComponent("CO2", 0.10);
co2Brine.addComponent("water", 0.80);
co2Brine.addComponent("Na+", 0.05);
co2Brine.addComponent("Cl-", 0.05);
co2Brine.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(co2Brine);
ops.TPflash();
co2Brine.initProperties();

// CO2 in aqueous phase
double co2InWater = co2Brine.getPhase("aqueous").getComponent("CO2").getx();
```

## Key Units and Conversions

| Quantity | Unit | Conversion |
|----------|------|------------|
| Ion concentration | mol fraction | ppm = x × MW_solution / MW_ion × 1e6 |
| TDS | mg/L | Sum of all dissolved ion concentrations |
| Salinity | wt% NaCl equiv | Based on Na+/Cl- content |
| pH | dimensionless | From H+ activity |

## Common Pitfalls

1. **Charge balance**: Total positive charges must equal total negative charges
2. **Mixing rule must be numeric `10`**: Not `"classic"` — CPA requires numeric mixing rule
3. **Ion names are case-sensitive**: `"Na+"` not `"na+"` or `"NA+"`
4. **Multi-phase check**: Always enable for electrolyte systems (`setMultiPhaseCheck(true)`)
5. **Temperature limits**: Electrolyte models may have narrower valid T range than HC models
6. **Convergence**: Electrolyte flashes can be slow — be patient or reduce component count
7. **Missing counter-ions**: Always add both cation and anion (e.g., Na+ with Cl-)
