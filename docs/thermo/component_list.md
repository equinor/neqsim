---
title: NeqSim Component Reference List
description: Complete list of all components available in NeqSim including hydrocarbons, gases, water, glycols, amines, and plus fractions. Includes component names, CAS numbers, and EoS availability.
---

# NeqSim Component Reference List

This document provides a comprehensive list of all components available in NeqSim. Use the exact component name (case-insensitive) when adding components to your fluid.

## Quick Reference

```java
// Adding components by name
fluid.addComponent("methane", 0.85);      // Mole fraction
fluid.addComponent("CO2", 0.02);          // Case-insensitive
fluid.addComponent("water", 1.0);         // Keyword "water" is recommended

// Adding by molar flow
fluid.addComponent("ethane", 100.0, "mol/sec");
```

---

## Standard vs Extended Component Database

NeqSim provides two pure component parameter databases:

| Database | Components | Performance | Use Case |
|----------|------------|-------------|----------|
| **Standard** | ~250 | Fast (embedded) | Typical oil & gas simulations |
| **Extended** | 50,000+ | Slower (loaded on demand) | Specialty chemicals, research |

The **standard database** is the default and covers most components needed for oil & gas applications. It loads instantly as an embedded database.

The **extended database** contains all components from the standard database (with identical parameters) plus over 50,000 additional components from external chemical databases. This is useful when working with specialty chemicals, pharmaceuticals, or research applications.

### Switching Between Databases

#### Java

```java
import neqsim.util.database.NeqSimDataBase;
import neqsim.thermo.system.SystemSrkEos;

// Default: use standard database (fast)
NeqSimDataBase.useExtendedComponentDatabase(false);

// Switch to extended database (50,000+ components)
NeqSimDataBase.useExtendedComponentDatabase(true);

// Now you can use specialty components
SystemSrkEos fluid = new SystemSrkEos(298.15, 1.01325);
fluid.addComponent("furoin", 1.0);  // Only in extended database
fluid.setMixingRule("classic");

// Switch back to standard for performance
NeqSimDataBase.useExtendedComponentDatabase(false);
```

#### Python

```python
from neqsim import jneqsim
from neqsim.thermo import fluid, TPflash, printFrame

# Check current database mode
print("Using extended database:", 
      jneqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase())

# List components in standard database
jneqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(False)
standard_components = jneqsim.util.database.NeqSimDataBase.getComponentNames()
print(f"Standard database: {len(standard_components)} components")

# Switch to extended database
jneqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(True)
extended_components = jneqsim.util.database.NeqSimDataBase.getComponentNames()
print(f"Extended database: {len(extended_components)} components")

# Use a specialty component from extended database
fluid1 = fluid('srk')
fluid1.setTemperature(20.0, 'C')
fluid1.setPressure(1.1, 'bara')
fluid1.addComponent('furoin', 1.0)  # Available only in extended database
TPflash(fluid1)
printFrame(fluid1)

# Switch back to standard for normal operations
jneqsim.util.database.NeqSimDataBase.useExtendedComponentDatabase(False)
```

### Database Source Files

The database CSV files are located in the NeqSim repository:

- **Standard database**: [`src/main/resources/data/COMP.csv`](https://github.com/equinor/neqsim/blob/master/src/main/resources/data/COMP.csv)
- **Extended database**: [`src/main/resources/data/COMP_EXT.csv`](https://github.com/equinor/neqsim/blob/master/src/main/resources/data/COMP_EXT.csv)

### When to Use Extended Database

| Scenario | Database |
|----------|----------|
| Natural gas processing | Standard |
| Oil refining | Standard |
| Gas dehydration (TEG/MEG) | Standard |
| Amine sweetening | Standard |
| Specialty solvents | Extended |
| Pharmaceutical research | Extended |
| Academic/research chemicals | Extended |
| Unknown component lookup | Extended |

> **Performance Note**: The extended database loads components into an embedded database on demand, which takes slightly longer than the pre-loaded standard database. For production simulations with common components, use the standard database.

---

## Light Hydrocarbons (C1-C4)

| Component Name | Formula | CAS Number | MW (g/mol) | Tc (K) | Pc (bar) | Notes |
|----------------|---------|------------|------------|--------|----------|-------|
| `methane` | CH₄ | 74-82-8 | 16.04 | 190.6 | 46.0 | Primary natural gas component |
| `ethane` | C₂H₆ | 74-84-0 | 30.07 | 305.4 | 48.8 | |
| `propane` | C₃H₈ | 74-98-6 | 44.10 | 369.8 | 42.5 | LPG component |
| `i-butane` | C₄H₁₀ | 75-28-5 | 58.12 | 408.1 | 36.5 | Isobutane |
| `n-butane` | C₄H₁₀ | 106-97-8 | 58.12 | 425.2 | 38.0 | |

## Medium Hydrocarbons (C5-C10)

| Component Name | Formula | CAS Number | MW (g/mol) | Tc (K) | Pc (bar) | Notes |
|----------------|---------|------------|------------|--------|----------|-------|
| `i-pentane` | C₅H₁₂ | 78-78-4 | 72.15 | 460.4 | 33.8 | Isopentane |
| `n-pentane` | C₅H₁₂ | 109-66-0 | 72.15 | 469.7 | 33.7 | |
| `22-dim-C3` | C₅H₁₂ | 463-82-1 | 72.15 | 433.8 | 32.0 | Neopentane |
| `n-hexane` | C₆H₁₄ | 110-54-3 | 86.18 | 507.4 | 30.1 | |
| `c-hexane` | C₆H₁₂ | 110-82-7 | 84.16 | 553.5 | 40.7 | Cyclohexane |
| `benzene` | C₆H₆ | 71-43-2 | 78.11 | 562.2 | 48.9 | Aromatic |
| `n-heptane` | C₇H₁₆ | 142-82-5 | 100.20 | 540.2 | 27.4 | |
| `c-C7` | C₇H₁₄ | 291-64-5 | 98.19 | 604.3 | 38.4 | Cycloheptane |
| `toluene` | C₇H₈ | 108-88-3 | 92.14 | 591.8 | 41.0 | Methylbenzene |
| `n-octane` | C₈H₁₈ | 111-65-9 | 114.23 | 568.8 | 24.9 | |
| `c-C8` | C₈H₁₆ | 292-64-8 | 112.22 | 647.2 | 35.6 | Cyclooctane |
| `m-Xylene` | C₈H₁₀ | 108-38-3 | 106.17 | 617.0 | 35.4 | |
| `ethylbenzene` | C₈H₁₀ | 100-41-4 | 106.17 | 617.2 | 36.0 | |
| `n-nonane` | C₉H₂₀ | 111-84-2 | 128.26 | 594.6 | 22.9 | |
| `n-decane` | C₁₀H₂₂ | 124-18-5 | 142.28 | 617.7 | 21.2 | |

## Heavy Hydrocarbons (C11+)

| Component Name | Formula | CAS Number | MW (g/mol) | Tc (K) | Pc (bar) |
|----------------|---------|------------|------------|--------|----------|
| `nC11` | C₁₁H₂₄ | 1120-21-4 | 156.31 | 638.8 | 19.7 |
| `nC12` | C₁₂H₂₆ | 112-40-3 | 170.34 | 658.2 | 18.2 |
| `nC13` | C₁₃H₂₈ | 629-50-5 | 184.37 | 676.0 | 17.2 |
| `nC14` | C₁₄H₃₀ | 629-59-4 | 198.39 | 692.4 | 16.2 |
| `nC15` | C₁₅H₃₂ | 629-62-9 | 212.42 | 707.0 | 15.2 |
| `nC16` | C₁₆H₃₄ | 544-76-3 | 226.45 | 720.6 | 14.4 |
| `nC17` | C₁₇H₃₆ | 629-78-7 | 240.47 | 733.4 | 13.7 |
| `nC18` | C₁₈H₃₈ | 593-45-3 | 254.50 | 745.3 | 13.0 |
| `nC19` | C₁₉H₄₀ | 629-92-5 | 268.53 | 756.4 | 12.4 |
| `nC20` | C₂₀H₄₂ | 112-95-8 | 282.55 | 767.0 | 11.8 |
| `nC21` | C₂₁H₄₄ | 629-94-7 | 296.58 | 778.0 | 11.4 |
| `nC22` | C₂₂H₄₆ | 629-97-0 | 310.61 | 787.0 | 10.9 |
| `nC23` | C₂₃H₄₈ | 638-67-5 | 324.63 | 796.0 | 10.5 |
| `nC24` | C₂₄H₅₀ | 646-31-1 | 338.66 | 804.0 | 10.1 |
| `nC25` | C₂₅H₅₂ | 629-99-2 | 352.69 | 812.0 | 9.8 |

## Acid Gases and Inorganics

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `CO2` | CO₂ | 124-38-9 | 44.01 | Carbon dioxide |
| `H2S` | H₂S | 7783-06-4 | 34.08 | Hydrogen sulfide |
| `nitrogen` | N₂ | 7727-37-9 | 28.01 | |
| `N2` | N₂ | 7727-37-9 | 28.01 | Alias for nitrogen |
| `oxygen` | O₂ | 7782-44-7 | 32.00 | |
| `argon` | Ar | 7440-37-1 | 39.95 | |
| `helium` | He | 7440-59-7 | 4.00 | |
| `hydrogen` | H₂ | 1333-74-0 | 2.02 | |
| `H2` | H₂ | 1333-74-0 | 2.02 | Alias for hydrogen |
| `CO` | CO | 630-08-0 | 28.01 | Carbon monoxide |
| `SO2` | SO₂ | 7446-09-5 | 64.07 | Sulfur dioxide |
| `NO` | NO | 10102-43-9 | 30.01 | Nitric oxide |
| `NO2` | NO₂ | 10102-44-0 | 46.01 | Nitrogen dioxide |
| `COS` | COS | 463-58-1 | 60.08 | Carbonyl sulfide |
| `H2O` | H₂O | 7732-18-5 | 18.02 | Water (use `water` preferred) |
| `water` | H₂O | 7732-18-5 | 18.02 | Preferred name for water |

## Glycols (TEG/MEG Dehydration)

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `MEG` | C₂H₆O₂ | 107-21-1 | 62.07 | Monoethylene glycol (hydrate inhibitor) |
| `TEG` | C₆H₁₄O₄ | 112-27-6 | 150.17 | Triethylene glycol (gas dehydration) |
| `DEG` | C₄H₁₀O₃ | 111-46-6 | 106.12 | Diethylene glycol |
| `PG` | C₃H₈O₂ | 57-55-6 | 76.09 | Propylene glycol |

## Amines (Gas Sweetening)

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `MDEA` | C₅H₁₃NO₂ | 105-59-9 | 119.16 | Methyldiethanolamine |
| `DEA` | C₄H₁₁NO₂ | 111-42-2 | 105.14 | Diethanolamine |
| `MEA` | C₂H₇NO | 141-43-5 | 61.08 | Monoethanolamine |
| `Piperazine` | C₄H₁₀N₂ | 110-85-0 | 86.14 | Promoter for MDEA |
| `ammonia` | NH₃ | 7664-41-7 | 17.03 | |

## Alcohols

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `methanol` | CH₃OH | 67-56-1 | 32.04 | Hydrate inhibitor |
| `ethanol` | C₂H₅OH | 64-17-5 | 46.07 | |
| `1-propanol` | C₃H₇OH | 71-23-8 | 60.10 | n-Propanol |
| `2-propanol` | C₃H₇OH | 67-63-0 | 60.10 | Isopropanol |
| `1-butanol` | C₄H₉OH | 71-36-3 | 74.12 | n-Butanol |
| `2-butanol` | C₄H₉OH | 78-92-2 | 74.12 | sec-Butanol |

## Refrigerants

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `R-134a` | C₂H₂F₄ | 811-97-2 | 102.03 | 1,1,1,2-Tetrafluoroethane |
| `R-22` | CHClF₂ | 75-45-6 | 86.47 | Chlorodifluoromethane |
| `R-32` | CH₂F₂ | 75-10-5 | 52.02 | Difluoromethane |
| `R-125` | C₂HF₅ | 354-33-6 | 120.02 | Pentafluoroethane |
| `R-143a` | C₂H₃F₃ | 420-46-2 | 84.04 | 1,1,1-Trifluoroethane |
| `R-152a` | C₂H₄F₂ | 75-37-6 | 66.05 | 1,1-Difluoroethane |
| `R-1234yf` | C₃H₂F₄ | 754-12-1 | 114.04 | 2,3,3,3-Tetrafluoropropene |

## Mercury Compounds

| Component Name | Formula | CAS Number | MW (g/mol) | Notes |
|----------------|---------|------------|------------|-------|
| `Hg` | Hg | 7439-97-6 | 200.59 | Elemental mercury |

## Ions and Electrolytes

| Component Name | Formula | Notes |
|----------------|---------|-------|
| `Na+` | Na⁺ | Sodium ion |
| `K+` | K⁺ | Potassium ion |
| `Ca++` | Ca²⁺ | Calcium ion |
| `Mg++` | Mg²⁺ | Magnesium ion |
| `Fe++` | Fe²⁺ | Ferrous ion |
| `Ba++` | Ba²⁺ | Barium ion |
| `Sr++` | Sr²⁺ | Strontium ion |
| `Cl-` | Cl⁻ | Chloride ion |
| `SO4--` | SO₄²⁻ | Sulfate ion |
| `HCO3-` | HCO₃⁻ | Bicarbonate ion |
| `CO3--` | CO₃²⁻ | Carbonate ion |
| `OH-` | OH⁻ | Hydroxide ion |
| `Ac-` | CH₃COO⁻ | Acetate ion |

---

## Plus Fractions and Pseudo-Components

NeqSim supports characterizing heavy oil fractions using TBP (True Boiling Point) pseudo-components.

### Adding Plus Fractions

```java
// Method 1: Add by boiling point and density
fluid.addTBPfraction("C7", 0.10, 95.0, 0.68);   // Name, moleFrac, MW, SG
fluid.addTBPfraction("C8", 0.08, 107.0, 0.72);
fluid.addTBPfraction("C9", 0.06, 121.0, 0.75);
fluid.addTBPfraction("C10+", 0.04, 200.0, 0.82);

// Method 2: Using oil characterization
CharacterisationTBP characterization = new CharacterisationTBP(fluid);
characterization.characterisePlusFraction();
```

### Plus Fraction Naming Convention

| Component | Description |
|-----------|-------------|
| `C7` | Heptanes fraction (C7 SCN) |
| `C8` | Octanes fraction |
| `C9` | Nonanes fraction |
| `C10` | Decanes fraction |
| `C11` | Undecanes fraction |
| `C7+`, `C10+`, `C20+` | Plus fraction lumps |

---

## EoS Availability by Component Type

| Component Category | SRK | PR | CPA | GERG-2008 | Electrolyte |
|--------------------|-----|-----|-----|-----------|-------------|
| Light hydrocarbons (C1-C4) | ✅ | ✅ | ✅ | ✅ | ❌ |
| Medium hydrocarbons (C5-C10) | ✅ | ✅ | ✅ | ✅ | ❌ |
| Heavy hydrocarbons (C11+) | ✅ | ✅ | ✅ | ⚠️ | ❌ |
| CO2, H2S, N2 | ✅ | ✅ | ✅ | ✅ | ❌ |
| Water | ✅ | ✅ | ✅ | ✅ | ✅ |
| Glycols (MEG, TEG) | ✅ | ✅ | ✅ | ❌ | ❌ |
| Amines (MDEA, DEA) | ✅ | ✅ | ✅ | ❌ | ✅ |
| Alcohols | ✅ | ✅ | ✅ | ❌ | ❌ |
| Ions/Electrolytes | ❌ | ❌ | ❌ | ❌ | ✅ |
| Refrigerants | ✅ | ✅ | ✅ | ⚠️ | ❌ |

Legend: ✅ Full support | ⚠️ Partial/limited | ❌ Not supported

---

## Complete Component Count by Category

| Category | Count | Examples |
|----------|-------|----------|
| Paraffins (alkanes) | ~50 | methane, ethane, n-decane |
| Naphthenes (cycloalkanes) | ~15 | c-hexane, c-C7, c-C8 |
| Aromatics | ~20 | benzene, toluene, m-Xylene |
| Acid gases | 6 | CO2, H2S, SO2, NO, NO2, COS |
| Inert gases | 5 | N2, O2, Ar, He, H2 |
| Water and glycols | 5 | water, MEG, DEG, TEG, PG |
| Amines | 5 | MDEA, DEA, MEA, Piperazine, ammonia |
| Alcohols | ~10 | methanol, ethanol, 1-propanol |
| Refrigerants | ~15 | R-134a, R-22, R-32 |
| Ions | ~15 | Na+, Cl-, Ca++, SO4-- |
| **Total** | **~150+ pure components** | Plus unlimited TBP fractions |

---

## Adding Custom Components

If a component is not in the database, you can add it manually:

```java
// Add component with critical properties
fluid.addComponent("myComponent", 1.0);  // Will use default properties

// Or use TBP characterization for undefined heavy fractions
fluid.addTBPfraction("MyHeavy", 0.05, 350.0, 0.88);  // MW=350, SG=0.88
```

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide.md) - How to create and configure fluids
- [Component Database Guide](component_database_guide.md) - Database structure and customization
- [Thermodynamic Models](thermodynamic_models.md) - EoS selection guide
