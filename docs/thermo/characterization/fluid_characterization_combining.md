# Combining and Re-Characterizing Fluids in NeqSim

This guide explains how to add a fluid into another's characterization framework in NeqSim, making them compatible for use with a common Equation of State (EOS).

## Table of Contents

1. [Overview](#overview)
2. [Mathematical Background](#mathematical-background)
3. [Method 1: Simple Fluid Addition](#method-1-using-addfluid-for-simple-fluid-addition)
4. [Method 2: Non-Destructive Addition](#method-2-static-addfluids-for-creating-new-combined-fluid)
5. [Method 3: Unified Pseudo-Components](#method-3-combining-reservoir-fluids-with-unified-pseudo-components)
6. [Method 4: Re-Characterizing to Reference](#method-4-re-characterizing-to-a-reference-fluid)
7. [Method 5: Advanced Options](#method-5-advanced-characterization-with-options)
8. [Best Practices](#best-practices)

---

## Overview

When working with multiple reservoir fluids or combining fluids from different sources, it is often necessary to:

1. **Combine fluids** with different pseudo-component definitions into a unified system
2. **Re-characterize** a fluid to match another fluid's pseudo-component structure
3. **Add fluids** together while preserving thermodynamic consistency

NeqSim provides several approaches based on the methods described in Pedersen et al., "Phase Behavior of Petroleum Reservoir Fluids".

---

## Mathematical Background

The mathematical foundations for fluid combining and re-characterization are based on Pedersen et al. (2014), Chapters 5.5 and 5.6.

### Mass and Mole Conservation

When combining fluids, total mass and moles must be conserved:

$$n_{total} = \sum_{f=1}^{N_f} \sum_{i=1}^{N_c} n_{f,i}$$

$$m_{total} = \sum_{f=1}^{N_f} \sum_{i=1}^{N_c} n_{f,i} \cdot M_{f,i}$$

where:
- $n_{f,i}$ = moles of component $i$ in fluid $f$
- $M_{f,i}$ = molar mass of component $i$ in fluid $f$ (kg/mol)
- $N_f$ = number of fluids being combined
- $N_c$ = number of components in each fluid

### Pseudo-Component Property Weighting

For combining pseudo-components from multiple fluids into unified groups, NeqSim uses **mass-weighted averaging** for intensive properties.

#### Molar Mass

The combined molar mass of a pseudo-component group $g$ is:

$$M_g = \frac{m_g}{n_g} = \frac{\sum_{j \in g} m_j}{\sum_{j \in g} n_j}$$

where $m_j$ and $n_j$ are the mass and moles of contributing pseudo-component $j$.

#### Density (Normal Liquid Density)

Combined density uses volume-weighted averaging to ensure volume additivity:

$$\rho_g = \frac{m_g}{V_g} = \frac{\sum_{j \in g} m_j}{\sum_{j \in g} \frac{m_j}{\rho_j}}$$

#### Critical Temperature, Critical Pressure, and Acentric Factor

These properties use mass-weighted averaging within each group:

$$T_{c,g} = \frac{\sum_{j \in g} m_j \cdot T_{c,j}}{\sum_{j \in g} m_j}$$

$$P_{c,g} = \frac{\sum_{j \in g} m_j \cdot P_{c,j}}{\sum_{j \in g} m_j}$$

$$\omega_g = \frac{\sum_{j \in g} m_j \cdot \omega_j}{\sum_{j \in g} m_j}$$

#### Normal Boiling Point

$$T_{b,g} = \frac{\sum_{j \in g} m_j \cdot T_{b,j}}{\sum_{j \in g} m_j}$$

### Multi-Fluid Mixing (Pedersen Chapter 5.5)

When combining multiple fluids with different pseudo-component structures, a two-level weighting scheme is applied:

1. **Fluid-level weighting**: Each fluid contributes proportionally to its total mass
2. **Component-level weighting**: Within each fluid, components contribute by their mole fraction

For property $\theta$ (such as $T_c$, $P_c$, $\omega$, or $T_b$) in combined group $g$:

$$\theta_g = \frac{\sum_{f=1}^{N_f} w_f \cdot x_{f,g} \cdot \theta_{f,g}}{\sum_{f=1}^{N_f} w_f \cdot x_{f,g}}$$

where:
- $w_f = \frac{m_f}{\sum_{k=1}^{N_f} m_k}$ is the mass fraction of fluid $f$
- $x_{f,g} = \frac{n_{f,g}}{n_f}$ is the mole fraction of group $g$ within fluid $f$
- $\theta_{f,g}$ is the property value for group $g$ from fluid $f$

### Pseudo-Component Boundary Determination

NeqSim determines boundaries between pseudo-component groups using **mass-based quantiles** of the boiling point (or molar mass) distribution.

For $N_{PC}$ target pseudo-components:

$$M_{cumulative,k} = \frac{k}{N_{PC}} \cdot m_{total,pseudo}$$

where $k = 1, 2, ..., N_{PC}-1$ defines the boundary positions.

The boundary boiling point $T_{b,boundary,k}$ is the boiling point at which cumulative mass equals $M_{cumulative,k}$.

### Characterization to Reference (Pedersen Chapter 5.6)

When re-characterizing a source fluid to match a reference fluid's pseudo-component structure:

1. **Extract boundary points** from the reference fluid:
   $$T_{b,boundary,k} = \frac{T_{b,k} + T_{b,k+1}}{2}$$
   
   where $T_{b,k}$ is the boiling point of reference pseudo-component $k$.

2. **Redistribute source components** into reference bins based on these boundaries

3. **Calculate weighted properties** for each bin using the mass-weighted formulas above

### Volume Shift and Density Consistency

When combining fluids, density is calculated to preserve volume additivity:

$$V_{total} = \sum_{j=1}^{N} \frac{m_j}{\rho_j}$$

$$\rho_{combined} = \frac{\sum_{j=1}^{N} m_j}{\sum_{j=1}^{N} \frac{m_j}{\rho_j}}$$

This is equivalent to the harmonic mean weighted by mass fractions.

---

## Method 1: Using `addFluid()` for Simple Fluid Addition

The simplest way to add one fluid to another is using the `addFluid()` method. This works best when:
- Both fluids use the same EOS
- Components are either identical or can be added as new TBP fractions

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

// Create first fluid
SystemInterface fluid1 = new SystemSrkEos(298.15, 50.0);
fluid1.addComponent("methane", 0.6);
fluid1.addComponent("ethane", 0.1);
fluid1.addTBPfraction("C7", 0.2, 0.100, 0.80);
fluid1.addTBPfraction("C10", 0.1, 0.200, 0.85);
fluid1.setMixingRule("classic");

// Create second fluid
SystemInterface fluid2 = new SystemSrkEos(298.15, 50.0);
fluid2.addComponent("methane", 0.4);
fluid2.addComponent("propane", 0.15);
fluid2.addTBPfraction("C8", 0.3, 0.120, 0.82);
fluid2.setMixingRule("classic");

// Add fluid2 to fluid1
fluid1.addFluid(fluid2);

// The combined fluid now contains all components from both fluids
// - Existing components (methane) have moles added together
// - New components (propane, C8_PC) are added with their properties
```

### Key Behaviors of `addFluid()`:
- **Matching components**: Moles are summed when component names match
- **New base components**: Added directly from the NeqSim database
- **New TBP fractions**: Added with their molar mass and density preserved
- **Database update**: `createDatabase(true)` is called automatically when new components are added
- **Mixing rule**: Re-applied after adding new components

---

## Method 2: Static `addFluids()` for Creating New Combined Fluid

To create a new fluid without modifying the originals:

```java
import neqsim.thermo.system.SystemInterface;

// Create a new combined fluid (originals are unchanged)
SystemInterface combined = SystemInterface.addFluids(fluid1, fluid2);
```

This clones `fluid1`, then adds `fluid2` to it.

---

## Method 3: Combining Reservoir Fluids with Unified Pseudo-Components

When combining fluids with different pseudo-component characterizations, use `combineReservoirFluids()` to redistribute heavy fractions into a common pseudo-component structure:

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.component.ComponentInterface;

// Fluid 1: Has C7 and C10 pseudo-components
SystemInterface fluid1 = new SystemPrEos(298.15, 50.0);
fluid1.addComponent("methane", 0.6);
fluid1.addComponent("ethane", 0.1);
fluid1.addTBPfraction("C7", 0.2, 0.100, 0.80);
ComponentInterface c7 = fluid1.getComponent("C7_PC");
c7.setNormalBoilingPoint(350.0);
c7.setTC(540.0);
c7.setPC(28.0);
c7.setAcentricFactor(0.32);

fluid1.addTBPfraction("C10", 0.1, 0.200, 0.85);
ComponentInterface c10 = fluid1.getComponent("C10_PC");
c10.setNormalBoilingPoint(410.0);
c10.setTC(620.0);
c10.setPC(22.0);
c10.setAcentricFactor(0.36);

// Fluid 2: Has C8 and C11 pseudo-components (different structure!)
SystemInterface fluid2 = new SystemPrEos(298.15, 50.0);
fluid2.addComponent("methane", 0.4);
fluid2.addComponent("n-butane", 0.05);
fluid2.addTBPfraction("C8", 0.15, 0.120, 0.82);
ComponentInterface c8 = fluid2.getComponent("C8_PC");
c8.setNormalBoilingPoint(380.0);
c8.setTC(580.0);
c8.setPC(26.0);
c8.setAcentricFactor(0.34);

fluid2.addTBPfraction("C11", 0.05, 0.220, 0.86);
ComponentInterface c11 = fluid2.getComponent("C11_PC");
c11.setNormalBoilingPoint(440.0);
c11.setTC(660.0);
c11.setPC(20.0);
c11.setAcentricFactor(0.38);

// Combine into 2 unified pseudo-components using Pedersen mixing weights
int targetPseudoComponents = 2;
SystemInterface combined = SystemInterface.combineReservoirFluids(
    targetPseudoComponents, 
    fluid1, 
    fluid2
);

// Result: Combined fluid has:
// - methane: 1.0 mol (summed)
// - ethane: 0.1 mol
// - n-butane: 0.05 mol  
// - PC1_PC: Lower boiling pseudo-component (weighted blend)
// - PC2_PC: Higher boiling pseudo-component (weighted blend)
```

### How It Works (Pedersen et al., Chapter 5.5):
1. Base components are summed directly
2. All pseudo-components from all fluids are collected
3. Quantile boundaries are calculated based on boiling points
4. New pseudo-components are created with mass-weighted properties:
   - Molar mass
   - Density
   - Critical temperature (Tc)
   - Critical pressure (Pc)
   - Acentric factor
   - Normal boiling point

#### Mathematical Example

Consider combining two fluids with pseudo-components:

| Fluid | Component | Moles | MW (kg/mol) | Mass (kg) | Tb (K) |
|-------|-----------|-------|-------------|-----------|--------|
| 1 | C7_PC | 0.20 | 0.100 | 0.020 | 350 |
| 1 | C10_PC | 0.10 | 0.200 | 0.020 | 410 |
| 2 | C8_PC | 0.15 | 0.120 | 0.018 | 380 |
| 2 | C11_PC | 0.05 | 0.220 | 0.011 | 440 |

**Step 1**: Sort by boiling point: C7 (350K) → C8 (380K) → C10 (410K) → C11 (440K)

**Step 2**: For 2 target groups, find mass boundary at 50%:
- Total pseudo mass = 0.069 kg
- Boundary at 0.0345 kg → falls between C8 and C10

**Step 3**: Group 1 (C7 + C8):
$$M_1 = \frac{0.020 + 0.018}{0.20 + 0.15} = 0.1086 \text{ kg/mol}$$

$$T_{b,1} = \frac{0.020 \times 350 + 0.018 \times 380}{0.020 + 0.018} = 364.2 \text{ K}$$

**Step 4**: Group 2 (C10 + C11):
$$M_2 = \frac{0.020 + 0.011}{0.10 + 0.05} = 0.207 \text{ kg/mol}$$

$$T_{b,2} = \frac{0.020 \times 410 + 0.011 \times 440}{0.020 + 0.011} = 420.6 \text{ K}$$

---

## Method 4: Re-Characterizing to a Reference Fluid

When you need to make one fluid's characterization match another (e.g., for blending in simulation), use `characterizeToReference()`:

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

// Reference fluid defines the target pseudo-component structure
SystemInterface reference = new SystemPrEos(298.15, 50.0);
reference.addTBPfraction("C7", 0.10, 0.090, 0.78);
reference.getComponent("C7_PC").setNormalBoilingPoint(340.0);
reference.getComponent("C7_PC").setTC(530.0);
reference.getComponent("C7_PC").setPC(29.0);
reference.getComponent("C7_PC").setAcentricFactor(0.31);

reference.addTBPfraction("C8", 0.12, 0.110, 0.81);
reference.getComponent("C8_PC").setNormalBoilingPoint(360.0);
reference.getComponent("C8_PC").setTC(550.0);
reference.getComponent("C8_PC").setPC(27.0);
reference.getComponent("C8_PC").setAcentricFactor(0.33);

reference.addTBPfraction("C9", 0.15, 0.150, 0.84);
reference.getComponent("C9_PC").setNormalBoilingPoint(380.0);
reference.getComponent("C9_PC").setTC(570.0);
reference.getComponent("C9_PC").setPC(25.0);
reference.getComponent("C9_PC").setAcentricFactor(0.35);

// Source fluid has different characterization
SystemInterface source = new SystemPrEos(298.15, 50.0);
source.addComponent("methane", 0.7);
source.addTBPfraction("S1", 0.05, 0.090, 0.79);
source.addTBPfraction("S2", 0.07, 0.095, 0.80);
source.addTBPfraction("S3", 0.08, 0.120, 0.82);
source.addTBPfraction("S4", 0.09, 0.150, 0.83);

// Re-characterize source to match reference's pseudo-component structure
SystemInterface characterized = SystemInterface.characterizeToReference(source, reference);

// Result: characterized has C7_PC, C8_PC, C9_PC (same names as reference)
// with properties redistributed from S1-S4 components
```

### How It Works (Pedersen et al., Chapter 5.6):
1. Base components from source are preserved
2. Reference fluid's pseudo-component cut points define boundaries
3. Source pseudo-components are redistributed into reference's structure
4. Mass is conserved; properties are weighted by contribution

#### Mathematical Formulation

Given a reference fluid with $N$ pseudo-components having boiling points $T_{b,1} < T_{b,2} < ... < T_{b,N}$:

**Step 1**: Calculate boundary boiling points:
$$T_{boundary,k} = \frac{T_{b,k} + T_{b,k+1}}{2}, \quad k = 1, ..., N-1$$

**Step 2**: Assign source pseudo-components to bins:
- Bin 1: all components with $T_b \leq T_{boundary,1}$
- Bin $k$: all components with $T_{boundary,k-1} < T_b \leq T_{boundary,k}$
- Bin $N$: all components with $T_b > T_{boundary,N-1}$

**Step 3**: Calculate properties for each bin using mass-weighted averaging:
$$\theta_{bin,k} = \frac{\sum_{j \in bin_k} m_j \cdot \theta_j}{\sum_{j \in bin_k} m_j}$$

The resulting fluid has pseudo-components with the same names as the reference (C7_PC, C8_PC, etc.) but with properties derived from the source fluid's heavy fractions.

---

## Method 5: Advanced Characterization with Options

For more control, use `CharacterizationOptions`:

```java
import neqsim.thermo.characterization.PseudoComponentCombiner;
import neqsim.thermo.characterization.CharacterizationOptions;

CharacterizationOptions options = new CharacterizationOptions();
options.setTransferBinaryInteractionParameters(true);  // Copy BIPs from reference
options.setNormalizeComposition(true);                  // Normalize mole fractions
options.setGenerateValidationReport(true);              // Log validation info

SystemInterface characterized = PseudoComponentCombiner.characterizeToReference(
    source, 
    reference, 
    options
);
```

### Transferring Binary Interaction Parameters

BIPs are critical for accurate phase behavior. When characterizing to a reference:

```java
import neqsim.thermo.characterization.PseudoComponentCombiner;

// Transfer BIPs from reference to characterized fluid
PseudoComponentCombiner.transferBinaryInteractionParameters(reference, characterized);

// BIPs are matched by:
// - Component name for base components
// - Position for pseudo-components (1st PC to 1st PC, etc.)
```

---

## Best Practices

### 1. Always Use the Same EOS Type
```java
// Correct: Both use Peng-Robinson
SystemInterface fluid1 = new SystemPrEos(298.15, 50.0);
SystemInterface fluid2 = new SystemPrEos(298.15, 50.0);

// Caution: Mixing EOS types may cause inconsistencies
// SystemInterface fluid1 = new SystemSrkEos(298.15, 50.0);
// SystemInterface fluid2 = new SystemPrEos(298.15, 50.0);
```

### 2. Set Complete Pseudo-Component Properties
```java
fluid.addTBPfraction("C7", moles, molarMass, density);
ComponentInterface pc = fluid.getComponent("C7_PC");
pc.setNormalBoilingPoint(350.0);  // Important for redistribution
pc.setTC(540.0);
pc.setPC(28.0);
pc.setAcentricFactor(0.32);
```

### 3. Initialize and Set Mixing Rules
```java
fluid.setMixingRule("classic");
// or
fluid.setMixingRule(2);  // Numeric code for specific mixing rule
```

### 4. Validate After Combining
```java
combined.init(0);  // Initialize mole numbers
combined.init(1);  // Initialize thermodynamic properties

// Check total mass conservation
double originalMass = fluid1.getMolarMass() * fluid1.getTotalNumberOfMoles()
                    + fluid2.getMolarMass() * fluid2.getTotalNumberOfMoles();
double combinedMass = combined.getMolarMass() * combined.getTotalNumberOfMoles();
// These should be equal within tolerance
```

---

## Summary Table

| Method | Use Case | Creates New Fluid? | Modifies Original? |
|--------|----------|-------------------|-------------------|
| `fluid1.addFluid(fluid2)` | Simple addition | No | Yes (fluid1) |
| `SystemInterface.addFluids(f1, f2)` | Non-destructive addition | Yes | No |
| `combineReservoirFluids(n, fluids...)` | Unified characterization | Yes | No |
| `characterizeToReference(src, ref)` | Match reference structure | Yes | No |

---

## References

- Pedersen, K. S., Christensen, P. L., & Shaikh, J. A. (2014). *Phase Behavior of Petroleum Reservoir Fluids* (2nd ed.). CRC Press.
  - Chapter 5.5: Mixing of Reservoir Fluids
  - Chapter 5.6: Characterization to Reference Fluid

---

## Appendix: Complete List of Mass-Weighted Properties

When combining pseudo-components, NeqSim calculates mass-weighted averages for the following properties:

| Property | Symbol | Unit | Weighting Method |
|----------|--------|------|------------------|
| Molar Mass | $M$ | kg/mol | Mass/Moles ratio |
| Normal Liquid Density | $\rho$ | kg/m³ | Volume-weighted (harmonic) |
| Normal Boiling Point | $T_b$ | K | Mass-weighted |
| Critical Temperature | $T_c$ | K | Mass-weighted |
| Critical Pressure | $P_c$ | bar | Mass-weighted |
| Acentric Factor | $\omega$ | - | Mass-weighted |
| Critical Volume | $V_c$ | m³/kmol | Mass-weighted |
| Rackett Z-factor | $Z_{RA}$ | - | Mass-weighted |
| Parachor | $P$ | - | Mass-weighted |
| Critical Viscosity | $\mu_c$ | Pa·s | Mass-weighted |
| Triple Point Temperature | $T_{tp}$ | K | Mass-weighted |
| Heat of Fusion | $\Delta H_f$ | J/mol | Mass-weighted |
| Ideal Gas Enthalpy of Formation | $\Delta H_f^{ig}$ | J/mol | Mass-weighted |
| Heat Capacity Coefficients | $C_{p,A}, C_{p,B}, C_{p,C}, C_{p,D}$ | various | Mass-weighted |
| EOS Attractive Term Parameter | $m$ | - | Mass-weighted |

---

## See Also

- [Fluid Creation Guide](../fluid_creation_guide.md)
- [PVT Fluid Characterization](../pvt_fluid_characterization.md)
- [Mixing Rules Guide](../mixing_rules_guide.md)
