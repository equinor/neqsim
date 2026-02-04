---
title: H2S Distribution Between Gas, Oil, and Water
description: Comprehensive guide to modeling hydrogen sulfide (H2S) phase distribution in NeqSim using SRK, PR, CPA, and Electrolyte-CPA equations of state. Covers acid-base chemistry, pH effects, salinity impacts, and model selection guidance.
---

# H2S Distribution Between Gas, Oil, and Water Phases

## Overview

Hydrogen sulfide (H2S) distribution between phases is critical for:

- **Safety**: H2S is extremely toxic (TLV-TWA = 1 ppm, IDLH = 100 ppm)
- **Corrosion**: Causes sulfide stress cracking and pitting
- **Environmental**: Strict emission limits on H2S in sales gas
- **Process design**: Affects sweetening unit sizing and material selection

This guide covers modeling approaches in NeqSim from simple to advanced.

## Physical Properties of H2S

| Property | Value |
|----------|-------|
| Molecular weight | 34.08 g/mol |
| Critical temperature | 373.2 K (100.05°C) |
| Critical pressure | 89.37 bar |
| Acentric factor | 0.0942 |
| Dipole moment | 0.97 D |

## Modeling Approaches

### 1. Simple Cubic Equations of State (SRK, PR)

The Soave-Redlich-Kwong and Peng-Robinson equations treat H2S as a non-associating, non-electrolyte component.

**SRK Equation:**

$$
P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b)}
$$

**When to use:**
- Quick screening calculations
- Gas-dominated systems with minimal water
- Composition estimates for preliminary design
- When CPU time is critical (many flash calculations)

**Limitations:**
- No H2S dissociation in water
- No pH calculation
- No salting-out effects
- May underestimate H2S solubility in water at low pressures

**Python Example (SRK):**

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

# Create system at 55°C, 50 bara
fluid = SystemSrkEos(273.15 + 55.0, 50.0)

# Add components
fluid.addComponent("H2S", 0.5)
fluid.addComponent("methane", 70.0)
fluid.addComponent("ethane", 5.0)
fluid.addComponent("water", 10.0)

fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

# Flash calculation
ops = ThermodynamicOperations(fluid)
ops.TPflash()

# Get H2S distribution
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    h2s_x = phase.getComponent("H2S").getx()
    print(f"{phase.getType()}: H2S = {h2s_x:.6e}")
```

### 2. CPA Equation of State (Cubic Plus Association)

CPA combines a cubic EoS with an association term:

$$
A^{CPA} = A^{SRK} + A^{assoc}
$$

The association term accounts for hydrogen bonding between molecules.

**When to use:**
- Systems with significant water content
- When water dew point is important
- H2S concentration > 1 mol%
- Hydrate predictions needed
- MEG/glycol systems

**Advantages over SRK/PR:**
- Better water mutual solubility
- Accounts for polar interactions
- More accurate liquid densities

**Python Example (CPA):**

```python
from neqsim import jneqsim

SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

fluid = SystemSrkCPAstatoil(273.15 + 55.0, 50.0)

fluid.addComponent("H2S", 0.5)
fluid.addComponent("CO2", 0.3)
fluid.addComponent("methane", 70.0)
fluid.addComponent("water", 10.0)

fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

ops = ThermodynamicOperations(fluid)
ops.TPflash()
```

### 3. Electrolyte-CPA with Chemical Reactions

This is the most rigorous approach for H2S in aqueous systems.

**H2S Dissociation Chemistry:**

$$
\text{H}_2\text{S}_{(aq)} \rightleftharpoons \text{H}^+ + \text{HS}^- \quad (pK_{a1} \approx 7.0)
$$

$$
\text{HS}^- \rightleftharpoons \text{H}^+ + \text{S}^{2-} \quad (pK_{a2} \approx 14)
$$

**Water auto-ionization:**

$$
\text{H}_2\text{O} \rightleftharpoons \text{H}^+ + \text{OH}^-
$$

**When to use:**
- Produced water treatment
- Sour water stripper design
- Amine sweetening simulation
- Corrosion prediction (pH required)
- High salinity formation water
- Systems where pH affects the process

**Python Example (Electrolyte-CPA):**

```python
from neqsim import jneqsim

SystemElectrolyteCPAstatoil = jneqsim.thermo.system.SystemElectrolyteCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

fluid = SystemElectrolyteCPAstatoil(273.15 + 55.0, 50.0)

# Add hydrocarbon components
fluid.addComponent("H2S", 0.5)
fluid.addComponent("CO2", 0.3)
fluid.addComponent("methane", 70.0)
fluid.addComponent("water", 10.0)

# Add electrolytes (formation water salinity)
fluid.addComponent("Na+", 0.5)
fluid.addComponent("Cl-", 0.5)

# Initialize chemical reactions
# This enables H2S dissociation, CO2 hydration, water auto-ionization
fluid.chemicalReactionInit()
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

ops = ThermodynamicOperations(fluid)
ops.TPflash()

# The aqueous phase now contains:
# - Molecular H2S
# - HS- (bisulfide ion)
# - S2- (sulfide ion)
# - OH-, H3O+ (for pH)
# - HCO3-, CO3-- (from CO2)
```

## pH Effects on H2S Distribution

The dominant sulfur species depends strongly on pH:

| pH Range | Dominant Species | H2S Vapor Pressure |
|----------|------------------|-------------------|
| < 5 | H2S (molecular) | Maximum |
| 5-7 | H2S + HS- mix | Moderate |
| 7-9 | HS- dominant | Low |
| > 9 | HS- and S2- | Very low |
| > 12 | S2- dominant | Negligible |

**Critical insight:** Simple EoS models assume 100% molecular H2S in the aqueous phase. At high pH (alkaline), this **overestimates** H2S partial pressure significantly!

## Salting-Out Effects

Dissolved salts reduce gas solubility in water. The Electrolyte-CPA model captures this through:

$$
\ln \gamma_i^{salt} = k_s \cdot I
$$

where $k_s$ is the salting coefficient and $I$ is the ionic strength.

**Typical impact:**
- 3.5 wt% NaCl (seawater): ~10-15% reduction in H2S solubility
- 10 wt% NaCl: ~30-40% reduction
- Formation water (high TDS): Can reduce solubility by 50%+

**Only Electrolyte models capture salting-out!** Simple EoS models ignore this effect entirely.

## Model Selection Guide

| Application | Model | Justification |
|-------------|-------|--------------|
| Quick screening | SRK | Fast, adequate for estimates |
| Gas pipeline transport | SRK/PR | Water phase minor concern |
| Offshore separator sizing | CPA | Good 3-phase equilibrium |
| Gas processing plant | CPA | Balance of speed and accuracy |
| Produced water treatment | Electrolyte-CPA | pH and chemistry essential |
| Sour water stripper | Electrolyte-CPA | H2S speciation critical |
| Amine treating | Electrolyte-CPA | Reaction chemistry required |
| Corrosion assessment | Electrolyte-CPA | Need pH, ionic species |
| High salinity systems | Electrolyte-CPA | Salting-out effects |
| Sulfur recovery unit | Electrolyte-CPA | Complex chemistry |

## Computational Considerations

| Model | Relative Speed | Memory | Convergence |
|-------|---------------|--------|-------------|
| SRK | 1× (fastest) | Low | Robust |
| PR | 1× | Low | Robust |
| CPA | 2-3× | Medium | Good |
| Electrolyte-CPA | 5-10× | High | May need tuning |

**Tips for Electrolyte-CPA:**
- Use simpler models for bulk process simulation
- Apply Electrolyte-CPA to water-critical units only
- Pre-calculate pH vs composition tables for interpolation in large models

## Common Pitfalls

1. **Ignoring water phase chemistry**: Simple EoS gives wrong H2S in water at high pH

2. **Neglecting salinity**: Formation water salting-out can change H2S partitioning significantly

3. **Temperature effects**: H2S solubility is retrograde above ~80°C; verify model captures this

4. **Mixing rules**: Use appropriate interaction parameters (kij) for H2S-water

5. **Phase identification**: Ensure proper aqueous phase identification in three-phase systems

## References

1. Kontogeorgis, G.M., Voutsas, E.C., Yakoumis, I.V., Tassios, D.P. (1996). "An Equation of State for Associating Fluids". Ind. Eng. Chem. Res., 35, 4310-4318.

2. Carroll, J.J. (2020). "Acid Gas Injection and Carbon Dioxide Sequestration". Wiley-Scrivener.

3. Springer, R.D., Wang, Z., Anderko, A., Wang, P., Felmy, A.R. (2012). "A Thermodynamic Model for Predicting Mineral Reactivity in Supercritical Carbon Dioxide". Chem. Geol., 322-323, 30-45.

4. Haghighi, H., Chapoy, A., Burgess, R., Tohidi, B. (2009). "Experimental and thermodynamic modelling of systems containing water and ethylene glycol". Fluid Phase Equilibria, 276, 24-30.

## Related Resources

- [H2S Distribution Modeling Notebook](../examples/H2S_Distribution_Modeling.ipynb)
- [Electrolyte Systems Guide](electrolyte_systems.md)
- [CPA Equation of State](cpa_equation_of_state.md)
- [Chemical Reactions in NeqSim](../chemicalreactions/index.md)
