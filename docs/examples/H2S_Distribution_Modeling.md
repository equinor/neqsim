---
layout: default
title: "H2S Distribution Modeling"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# H2S Distribution Modeling

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`H2S_Distribution_Modeling.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/H2S_Distribution_Modeling.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/H2S_Distribution_Modeling.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/H2S_Distribution_Modeling.ipynb).

---

# H2S Distribution Between Gas, Oil, and Water Phases in NeqSim

## Introduction

Hydrogen sulfide (H2S) is a critical component in oil and gas processing due to its:
- **Toxicity**: Extremely hazardous even at low concentrations
- **Corrosivity**: Causes severe corrosion, especially in the presence of water
- **Environmental concerns**: Strict emission regulations
- **Process implications**: Affects equipment design and material selection

Understanding how H2S distributes between gas, oil, and water phases is essential for:
1. Designing separation equipment
2. Specifying materials for corrosion resistance
3. Meeting product specifications
4. Environmental compliance
5. Safety assessments

## Physical and Chemical Behavior of H2S

H2S exhibits complex phase behavior:

| Property | Value |
|----------|-------|
| Molecular weight | 34.08 g/mol |
| Critical temperature | 373.2 K (100°C) |
| Critical pressure | 89.4 bar |
| Acentric factor | 0.1 |

**Key characteristics:**
- Moderately soluble in hydrocarbons
- Highly soluble in water compared to hydrocarbons
- Forms weak acid in water (H2S ⇌ H⁺ + HS⁻ ⇌ 2H⁺ + S²⁻)
- Solubility affected by pH, salinity, and temperature

## Modeling Approaches in NeqSim

NeqSim offers multiple thermodynamic models for H2S distribution:

| Model | Complexity | Water Chemistry | Best For |
|-------|------------|-----------------|----------|
| SRK-EoS | Low | No | Quick estimates, gas-dominated systems |
| PR-EoS | Low | No | Similar to SRK, slightly different accuracy |
| CPA-EoS | Medium | No | Systems with polar components, associating fluids |
| Electrolyte-CPA | High | Yes | Water-dominated, pH effects, ionic species |

This notebook demonstrates each approach and when to use them.

## Setup and Imports

```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# Import commonly used Java classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
SystemElectrolyteCPAstatoil = jneqsim.thermo.system.SystemElectrolyteCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

print("NeqSim loaded successfully")
```

## Part 1: Simple Cubic Equations of State (SRK and PR)

### 1.1 SRK Equation of State

The Soave-Redlich-Kwong (SRK) equation of state is a cubic EoS that treats H2S as a non-associating component. It calculates phase equilibrium based on:

$$P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b)}$$

**Advantages:**
- Fast computation
- Good for hydrocarbon-dominated systems
- Well-established interaction parameters

**Limitations:**
- Does not account for H2S dissociation in water
- May underestimate water solubility
- No ionic species or pH effects

```python
# H2S Distribution using SRK-EoS
def create_srk_system(temp_C, pressure_bara):
    """Create a three-phase system with H2S using SRK-EoS."""
    fluid = SystemSrkEos(273.15 + temp_C, pressure_bara)
    
    # Add components (typical reservoir fluid with H2S)
    fluid.addComponent("H2S", 0.5)        # mol/sec - sour component
    fluid.addComponent("CO2", 0.3)        # mol/sec
    fluid.addComponent("nitrogen", 0.5)   # mol/sec
    fluid.addComponent("methane", 70.0)   # mol/sec
    fluid.addComponent("ethane", 5.0)     # mol/sec
    fluid.addComponent("propane", 3.0)    # mol/sec
    fluid.addComponent("n-butane", 1.5)   # mol/sec
    fluid.addComponent("n-hexane", 1.0)   # mol/sec
    fluid.addComponent("n-heptane", 0.5)  # mol/sec
    fluid.addComponent("water", 10.0)     # mol/sec
    
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    
    return fluid

# Create system at typical separator conditions
temp_C = 55.0  # °C
pressure_bara = 50.0  # bara

srk_fluid = create_srk_system(temp_C, pressure_bara)

# Perform flash calculation
ops = ThermodynamicOperations(srk_fluid)
ops.TPflash()

print(f"=== SRK-EoS Results at {temp_C}°C and {pressure_bara} bara ===")
print(f"Number of phases: {srk_fluid.getNumberOfPhases()}")
```

```python
# Extract H2S distribution from SRK model
def get_h2s_distribution(fluid, model_name):
    """Extract H2S mole fractions in each phase."""
    results = {'Model': model_name}
    
    n_phases = fluid.getNumberOfPhases()
    
    for i in range(n_phases):
        phase = fluid.getPhase(i)
        phase_type = phase.getType().toString()
        h2s_molefrac = phase.getComponent("H2S").getx()
        phase_frac = fluid.getMoleFraction(i)
        
        results[f'{phase_type}_H2S_molefrac'] = h2s_molefrac
        results[f'{phase_type}_phase_frac'] = phase_frac
        
        print(f"{phase_type} phase:")
        print(f"  H2S mole fraction: {h2s_molefrac:.6e}")
        print(f"  Phase fraction: {phase_frac:.4f}")
    
    return results

print("\n--- H2S Distribution (SRK-EoS) ---")
srk_results = get_h2s_distribution(srk_fluid, "SRK")
```

### 1.2 Peng-Robinson Equation of State

The Peng-Robinson (PR) equation is another cubic EoS:

$$P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b) + b(V-b)}$$

**Differences from SRK:**
- Different attractive term formulation
- Generally better liquid density predictions
- Similar accuracy for H2S distribution in most cases

```python
# H2S Distribution using PR-EoS
def create_pr_system(temp_C, pressure_bara):
    """Create a three-phase system with H2S using PR-EoS."""
    fluid = SystemPrEos(273.15 + temp_C, pressure_bara)
    
    # Same composition as SRK
    fluid.addComponent("H2S", 0.5)
    fluid.addComponent("CO2", 0.3)
    fluid.addComponent("nitrogen", 0.5)
    fluid.addComponent("methane", 70.0)
    fluid.addComponent("ethane", 5.0)
    fluid.addComponent("propane", 3.0)
    fluid.addComponent("n-butane", 1.5)
    fluid.addComponent("n-hexane", 1.0)
    fluid.addComponent("n-heptane", 0.5)
    fluid.addComponent("water", 10.0)
    
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    
    return fluid

pr_fluid = create_pr_system(temp_C, pressure_bara)

ops_pr = ThermodynamicOperations(pr_fluid)
ops_pr.TPflash()

print(f"=== PR-EoS Results at {temp_C}°C and {pressure_bara} bara ===")
print(f"Number of phases: {pr_fluid.getNumberOfPhases()}")
print("\n--- H2S Distribution (PR-EoS) ---")
pr_results = get_h2s_distribution(pr_fluid, "PR")
```

## Part 2: CPA Equation of State (Cubic Plus Association)

### Why CPA for H2S?

CPA combines a cubic EoS with an association term from SAFT theory:

$$A^{CPA} = A^{SRK} + A^{assoc}$$

**Key advantages for H2S systems:**
1. **Hydrogen bonding**: H2S can act as both hydrogen bond donor and acceptor
2. **Water interactions**: Better modeling of H2S-water interactions
3. **Polar effects**: Accounts for dipole-dipole interactions

**When to use CPA over SRK/PR:**
- Systems with significant water content
- High H2S concentrations (>1 mol%)
- When accurate water-hydrocarbon mutual solubilities matter
- Design of water handling facilities

```python
# H2S Distribution using CPA-EoS
def create_cpa_system(temp_C, pressure_bara):
    """Create a three-phase system with H2S using CPA-EoS."""
    fluid = SystemSrkCPAstatoil(273.15 + temp_C, pressure_bara)
    
    # Same composition
    fluid.addComponent("H2S", 0.5)
    fluid.addComponent("CO2", 0.3)
    fluid.addComponent("nitrogen", 0.5)
    fluid.addComponent("methane", 70.0)
    fluid.addComponent("ethane", 5.0)
    fluid.addComponent("propane", 3.0)
    fluid.addComponent("n-butane", 1.5)
    fluid.addComponent("n-hexane", 1.0)
    fluid.addComponent("n-heptane", 0.5)
    fluid.addComponent("water", 10.0)
    
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    
    return fluid

cpa_fluid = create_cpa_system(temp_C, pressure_bara)

ops_cpa = ThermodynamicOperations(cpa_fluid)
ops_cpa.TPflash()

print(f"=== CPA-EoS Results at {temp_C}°C and {pressure_bara} bara ===")
print(f"Number of phases: {cpa_fluid.getNumberOfPhases()}")
print("\n--- H2S Distribution (CPA-EoS) ---")
cpa_results = get_h2s_distribution(cpa_fluid, "CPA")
```

### Comparison: SRK vs PR vs CPA

```python
# Compare H2S distribution across models at varying temperatures
temperatures = np.arange(20, 100, 10)  # °C
pressure = 50.0  # bara

results_comparison = []

for temp in temperatures:
    for model_name, create_func in [('SRK', create_srk_system), 
                                      ('PR', create_pr_system), 
                                      ('CPA', create_cpa_system)]:
        try:
            fluid = create_func(temp, pressure)
            ops = ThermodynamicOperations(fluid)
            ops.TPflash()
            
            # Get H2S in water phase if exists
            for i in range(fluid.getNumberOfPhases()):
                phase = fluid.getPhase(i)
                phase_type = str(phase.getType().toString())
                if 'aqueous' in phase_type.lower() or 'water' in phase_type.lower():
                    h2s_in_water = phase.getComponent("H2S").getx()
                    results_comparison.append({
                        'Temperature_C': temp,
                        'Model': model_name,
                        'H2S_in_water': h2s_in_water
                    })
                    break
        except Exception as e:
            print(f"Error at {temp}°C with {model_name}: {e}")

df_comparison = pd.DataFrame(results_comparison)
print("\n=== H2S Solubility in Water Phase ===")
print(df_comparison.pivot(index='Temperature_C', columns='Model', values='H2S_in_water'))
```

## Part 3: Electrolyte-CPA with Chemical Reactions

### The Chemistry of H2S in Water

When H2S dissolves in water, it undergoes dissociation reactions:

**First dissociation (weak acid):**
$$\text{H}_2\text{S} \rightleftharpoons \text{H}^+ + \text{HS}^- \quad (pK_{a1} \approx 7.0)$$

**Second dissociation:**
$$\text{HS}^- \rightleftharpoons \text{H}^+ + \text{S}^{2-} \quad (pK_{a2} \approx 14)$$

### Why Electrolyte Modeling Matters

The simple EoS models (SRK, PR, CPA) treat H2S as a **molecular species only**. They do not account for:

1. **pH effects**: At high pH, more H2S converts to HS⁻ and S²⁻, reducing molecular H2S in water
2. **Ionic strength**: Salts (NaCl, etc.) affect H2S solubility via salting-out effects
3. **Metal ions**: Fe²⁺ can precipitate FeS, removing sulfide from solution
4. **Buffering**: CO2/bicarbonate systems affect pH and hence H2S speciation

### When to Use Electrolyte-CPA

| Scenario | Simple EoS | Electrolyte-CPA |
|----------|------------|------------------|
| Gas-dominated system, low water | ✓ Good | Overkill |
| Produced water with H2S | Approximate | ✓ Recommended |
| Sour water stripping | May be OK | ✓ Required |
| H2S scavenging assessment | Poor | ✓ Required |
| Corrosion prediction (pH needed) | Cannot do | ✓ Required |
| High salinity formation water | Poor | ✓ Required |

```python
# H2S Distribution using Electrolyte-CPA-EoS with Chemical Reactions
def create_electrolyte_system(temp_C, pressure_bara, add_salts=True):
    """Create a system with H2S using Electrolyte-CPA-EoS.
    
    This model includes:
    - H2S dissociation to HS- and S2-
    - CO2 hydration to bicarbonate/carbonate
    - Water auto-ionization (pH calculation)
    - Salt effects on solubility
    """
    fluid = SystemElectrolyteCPAstatoil(273.15 + temp_C, pressure_bara)
    
    # Add hydrocarbon components
    fluid.addComponent("H2S", 0.5)
    fluid.addComponent("CO2", 0.3)
    fluid.addComponent("nitrogen", 0.5)
    fluid.addComponent("methane", 70.0)
    fluid.addComponent("ethane", 5.0)
    fluid.addComponent("propane", 3.0)
    fluid.addComponent("n-butane", 1.5)
    fluid.addComponent("n-hexane", 1.0)
    fluid.addComponent("n-heptane", 0.5)
    fluid.addComponent("water", 10.0)
    
    if add_salts:
        # Add salts typical of formation water
        fluid.addComponent("Na+", 0.5)    # Sodium ion
        fluid.addComponent("Cl-", 0.5)    # Chloride ion
    
    # Chemical reactions are automatically included for:
    # - H2S dissociation
    # - CO2 hydration  
    # - Water auto-ionization
    fluid.chemicalReactionInit()
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    
    return fluid

# Create electrolyte system
elec_fluid = create_electrolyte_system(temp_C, pressure_bara)

ops_elec = ThermodynamicOperations(elec_fluid)
ops_elec.TPflash()

print(f"=== Electrolyte-CPA Results at {temp_C}°C and {pressure_bara} bara ===")
print(f"Number of phases: {elec_fluid.getNumberOfPhases()}")
```

```python
# Detailed analysis of electrolyte results - H2S speciation
def analyze_h2s_speciation(fluid):
    """Analyze H2S speciation in the aqueous phase."""
    
    print("\n=== H2S Speciation in Aqueous Phase ===")
    
    for i in range(fluid.getNumberOfPhases()):
        phase = fluid.getPhase(i)
        phase_type = str(phase.getType().toString())
        
        if 'aqueous' in phase_type.lower():
            print(f"\nPhase: {phase_type}")
            
            # Get molecular H2S
            try:
                h2s_mol = phase.getComponent("H2S").getx()
                print(f"  H2S (molecular):  {h2s_mol:.6e} mol frac")
            except:
                pass
            
            # Get ionic species if available
            ionic_species = ["HS-", "S--", "OH-", "H3O+", "HCO3-", "CO3--"]
            for species in ionic_species:
                try:
                    comp = phase.getComponent(species)
                    if comp is not None:
                        x = comp.getx()
                        print(f"  {species:12s}:  {x:.6e} mol frac")
                except:
                    pass
            
            # Calculate pH if possible
            try:
                h3o = phase.getComponent("H3O+").getx()
                # Convert to concentration (approximate)
                water_molality = 55.5  # mol/kg
                h3o_conc = h3o * water_molality  # mol/L approximately
                if h3o_conc > 0:
                    pH = -np.log10(h3o_conc)
                    print(f"\n  Estimated pH: {pH:.2f}")
            except:
                pass
        else:
            print(f"\nPhase: {phase_type}")
            try:
                h2s_mol = phase.getComponent("H2S").getx()
                print(f"  H2S: {h2s_mol:.6e} mol frac")
            except:
                pass

analyze_h2s_speciation(elec_fluid)
```

### Effect of pH on H2S Distribution

The speciation of H2S is strongly pH-dependent:

| pH Range | Dominant Species | Implications |
|----------|-----------------|---------------|
| < 5 | H2S (molecular) | Maximum vapor pressure, most toxic |
| 5-9 | H2S + HS⁻ mix | Transition region |
| > 9 | HS⁻ dominant | Low H2S vapor pressure, but still corrosive |
| > 12 | S²⁻ dominant | Sulfide precipitation possible |

**Critical insight**: Simple EoS models always assume 100% molecular H2S. At high pH (e.g., in amine systems or caustic scrubbing), this significantly **overestimates** the H2S partial pressure!

```python
# Compare H2S in gas phase: Simple EoS vs Electrolyte
def compare_gas_phase_h2s(temp_C, pressure_bara):
    """Compare H2S concentration in gas phase between models."""
    
    results = {}
    
    # SRK
    srk = create_srk_system(temp_C, pressure_bara)
    ops = ThermodynamicOperations(srk)
    ops.TPflash()
    for i in range(srk.getNumberOfPhases()):
        phase = srk.getPhase(i)
        if 'gas' in str(phase.getType().toString()).lower():
            results['SRK'] = phase.getComponent("H2S").getx()
    
    # CPA
    cpa = create_cpa_system(temp_C, pressure_bara)
    ops = ThermodynamicOperations(cpa)
    ops.TPflash()
    for i in range(cpa.getNumberOfPhases()):
        phase = cpa.getPhase(i)
        if 'gas' in str(phase.getType().toString()).lower():
            results['CPA'] = phase.getComponent("H2S").getx()
    
    # Electrolyte-CPA
    elec = create_electrolyte_system(temp_C, pressure_bara)
    ops = ThermodynamicOperations(elec)
    ops.TPflash()
    for i in range(elec.getNumberOfPhases()):
        phase = elec.getPhase(i)
        if 'gas' in str(phase.getType().toString()).lower():
            results['Electrolyte-CPA'] = phase.getComponent("H2S").getx()
    
    return results

print("=== H2S in Gas Phase: Model Comparison ===")
print(f"Conditions: {temp_C}°C, {pressure_bara} bara\n")

gas_h2s = compare_gas_phase_h2s(temp_C, pressure_bara)
for model, h2s in gas_h2s.items():
    print(f"{model:20s}: {h2s:.6e} mol frac ({h2s*1e6:.1f} ppm)")
```

## Part 4: Effect of Salinity (Salting-Out)

Dissolved salts reduce gas solubility in water through the "salting-out" effect. This is important for:
- Formation water with high TDS (Total Dissolved Solids)
- Produced water treatment design
- Offshore systems with seawater injection

**Only the Electrolyte model can capture salting-out effects!**

```python
# Effect of salinity on H2S solubility
def study_salinity_effect(temp_C, pressure_bara):
    """Study how NaCl concentration affects H2S solubility."""
    
    # NaCl concentrations (as mol fraction in water)
    nacl_levels = [0.0, 0.01, 0.02, 0.05, 0.1]  # mol NaCl per mol water
    
    results = []
    
    for nacl in nacl_levels:
        fluid = SystemElectrolyteCPAstatoil(273.15 + temp_C, pressure_bara)
        
        # Add components
        fluid.addComponent("H2S", 0.5)
        fluid.addComponent("methane", 70.0)
        fluid.addComponent("water", 10.0)
        
        if nacl > 0:
            # Add NaCl
            salt_amount = 10.0 * nacl  # relative to water
            fluid.addComponent("Na+", salt_amount)
            fluid.addComponent("Cl-", salt_amount)
        
        fluid.chemicalReactionInit()
        fluid.setMixingRule("classic")
        fluid.setMultiPhaseCheck(True)
        
        ops = ThermodynamicOperations(fluid)
        ops.TPflash()
        
        # Get H2S in aqueous phase
        for i in range(fluid.getNumberOfPhases()):
            phase = fluid.getPhase(i)
            if 'aqueous' in str(phase.getType().toString()).lower():
                h2s_aq = phase.getComponent("H2S").getx()
                results.append({
                    'NaCl_mol_ratio': nacl,
                    'NaCl_wt_pct': nacl * 58.44 / 18.015 * 100,  # Approximate wt%
                    'H2S_in_water': h2s_aq
                })
                break
    
    return pd.DataFrame(results)

print("=== Effect of Salinity on H2S Solubility ===")
print(f"Conditions: {temp_C}°C, {pressure_bara} bara\n")

salinity_df = study_salinity_effect(temp_C, pressure_bara)
print(salinity_df.to_string(index=False))
```

## Part 5: Summary and Recommendations

### Model Selection Guide

| Application | Recommended Model | Reason |
|-------------|-------------------|--------|
| Quick screening calculations | SRK or PR | Fast, adequate for estimates |
| Gas processing design | CPA | Better water content prediction |
| Separator sizing | CPA | Good three-phase equilibrium |
| Produced water treatment | Electrolyte-CPA | pH effects, ionic species |
| Sour water stripper design | Electrolyte-CPA | H2S speciation critical |
| Corrosion assessment | Electrolyte-CPA | Need pH and ionic strength |
| Amine sweetening | Electrolyte-CPA | Chemical reactions essential |
| Pipeline flow assurance | CPA | Balance of accuracy and speed |
| High salinity systems | Electrolyte-CPA | Salting-out effects |

### Key Takeaways

1. **Simple EoS (SRK, PR)**: Adequate for gas-phase dominated systems where water phase H2S is secondary concern.

2. **CPA**: Recommended as default for three-phase systems. Handles polar interactions and gives better water mutual solubility.

3. **Electrolyte-CPA**: Essential when:
   - pH affects the process (>90% of water treatment applications)
   - Ionic species matter (corrosion, scale prediction)
   - Salinity is significant (formation water, seawater)
   - Chemical reactions occur (amine treating, caustic scrubbing)

### Computational Cost Comparison

| Model | Relative Speed | Memory |
|-------|---------------|--------|
| SRK | 1x (fastest) | Low |
| PR | 1x | Low |
| CPA | 2-3x | Medium |
| Electrolyte-CPA | 5-10x | High |

For process simulation with many flash calculations (e.g., column stage-by-stage), the computational cost of Electrolyte-CPA can be significant. Use it where chemistry matters, and simpler models elsewhere.

```python
# Final summary comparison
print("="*60)
print("SUMMARY: H2S Distribution Model Comparison")
print("="*60)
print(f"\nConditions: {temp_C}°C, {pressure_bara} bara")
print(f"Feed: Natural gas with 0.5 mol% H2S, 10 mol% water\n")

models = [
    ('SRK-EoS', create_srk_system),
    ('PR-EoS', create_pr_system),
    ('CPA-EoS', create_cpa_system),
    ('Electrolyte-CPA', lambda t, p: create_electrolyte_system(t, p, add_salts=False))
]

summary_data = []

for model_name, create_func in models:
    try:
        fluid = create_func(temp_C, pressure_bara)
        ops = ThermodynamicOperations(fluid)
        ops.TPflash()
        
        row = {'Model': model_name}
        
        for i in range(fluid.getNumberOfPhases()):
            phase = fluid.getPhase(i)
            phase_type = str(phase.getType().toString())
            h2s = phase.getComponent("H2S").getx()
            
            if 'gas' in phase_type.lower():
                row['H2S_gas_ppm'] = h2s * 1e6
            elif 'oil' in phase_type.lower():
                row['H2S_oil_ppm'] = h2s * 1e6
            elif 'aqueous' in phase_type.lower() or 'water' in phase_type.lower():
                row['H2S_water_ppm'] = h2s * 1e6
        
        summary_data.append(row)
    except Exception as e:
        print(f"Error with {model_name}: {e}")

summary_df = pd.DataFrame(summary_data)
print(summary_df.to_string(index=False))
```

## References

1. Kontogeorgis, G.M., Voutsas, E.C., Yakoumis, I.V., Tassios, D.P. (1996). "An Equation of State for Associating Fluids". Ind. Eng. Chem. Res., 35, 4310-4318.

2. Haghighi, H., Chapoy, A., Burgess, R., Tohidi, B. (2009). "Experimental and thermodynamic modelling of systems containing water and ethylene glycol: Application to flow assurance and gas processing". Fluid Phase Equilibria, 276, 24-30.

3. Solbraa, E. (2002). "Measurement and Modelling of Absorption of Carbon Dioxide into Methyldiethanolamine Solutions at High Pressures". PhD Thesis, NTNU.

4. Carroll, J.J. (2020). "Acid Gas Injection and Carbon Dioxide Sequestration". Wiley-Scrivener.

5. NeqSim Documentation: https://equinor.github.io/neqsim/

