---
title: "Fluid Characterization in NeqSim"
description: "Real reservoir fluids often contain a complex mixture of heavy hydrocarbons (C7+) that cannot be represented by standard pure components. NeqSim provides a robust characterization framework to model t..."
---

# Fluid Characterization in NeqSim

Real reservoir fluids often contain a complex mixture of heavy hydrocarbons (C7+) that cannot be represented by standard pure components. NeqSim provides a robust characterization framework to model these fluids using TBP (True Boiling Point) fractions and Plus fractions.

> **Related Documentation:**
> - [TBP Fraction Models](tbp_fraction_models.md) - Detailed guide on all available TBP models (Pedersen, Lee-Kesler, Riazi-Daubert, Twu, Cavett, Standing), model selection, and mathematical correlations

## 1. Adding Heavy Fractions

You can add heavy fractions to a system using two primary methods: `addTBPfraction` and `addPlusFraction`.

### 1.1 TBP Fractions
Use `addTBPfraction` when you have data for specific carbon number cuts (e.g., C7, C8, C9) with defined properties.

```java
// addTBPfraction(name, moles, molarMass_kg_mol, density_kg_m3)
system.addTBPfraction("C7", 1.0, 0.092, 0.73); 
system.addTBPfraction("C8", 1.0, 0.104, 0.76);
```

### 1.2 Plus Fractions
Use `addPlusFraction` for the final residue or "plus" fraction (e.g., C10+, C20+) where you only have average properties.

```java
// addPlusFraction(name, moles, molarMass_kg_mol, density_kg_m3)
system.addPlusFraction("C10+", 10.0, 0.250, 0.85);
```

## 2. Characterization Process

After adding the components, you must run the characterization routine to split the plus fraction into pseudo-components and estimate their critical properties (Tc, Pc, w).

### 2.1 Setting the Model
NeqSim supports several characterization models. The most common is the Pedersen model.

```java
// Set the TBP Model (affects how TBP fractions are treated)
system.getCharacterization().setTBPModel("PedersenSRK"); 

// Set the Plus Fraction Model (affects how the plus fraction is split)
system.getCharacterization().setPlusFractionModel("Pedersen");
```

#### Available TBP Models

NeqSim provides 10 TBP models for estimating critical properties (Tc, Pc, ω) from molecular weight and density:

| Model | Best Application |
|-------|------------------|
| `PedersenSRK` | General SRK EOS (default) |
| `PedersenPR` | General Peng-Robinson EOS |
| `PedersenSRKHeavyOil` | Heavy oils with SRK |
| `PedersenPRHeavyOil` | Heavy oils with PR |
| `Lee-Kesler` | General purpose, uses Watson K-factor |
| `RiaziDaubert` | Light fractions (MW < 300 g/mol) |
| `Twu` | Paraffinic fluids, gas condensates |
| `Cavett` | Refining industry, API gravity corrections |
| `Standing` | Reservoir engineering |

See [TBP Fraction Models](tbp_fraction_models.md) for detailed mathematical correlations and model selection guidelines.

#### Available Plus Fraction Models

##### Pedersen Model
The standard Pedersen model assumes an exponential distribution for the mole fraction $z_i$ of each carbon number fraction $i$:

$
z_i = \exp(A + B \cdot i)
$

where $i$ is the carbon number, and $A$ and $B$ are coefficients determined to match the total mole fraction and average molar mass of the plus fraction.

The density $\rho_i$ is modeled as a logarithmic function of the carbon number:

$
\rho_i = C + D \cdot \ln(i)
$

where $C$ and $D$ are fitted coefficients.

##### Whitson Gamma Model
The Whitson Gamma model uses a three-parameter Gamma probability density function (PDF) to describe the molar mass distribution:

$
p(M) = \frac{(M - \eta)^{\alpha - 1} \exp\left(-\frac{M - \eta}{\beta}\right)}{\beta^\alpha \Gamma(\alpha)}
$

where:
*   $M$ is the molar mass.
*   $\eta$ is the minimum molar mass (default 90 g/mol).
*   $\alpha$ is the shape factor (default 1.0).
*   $\beta$ is the scale parameter, calculated as $\beta = \frac{M_{plus} - \eta}{\alpha}$.
*   $\Gamma(\alpha)$ is the Gamma function.

The mole fraction $z_i$ for a pseudo-component covering the molar mass range $[M_{L}, M_{U}]$ is obtained by integrating the PDF:

$
z_i = z_{plus} \int_{M_{L}}^{M_{U}} p(M) \, dM
$

The density of each pseudo-component is calculated using the Watson UOP characterization factor $K_w$:

$
K_w = 4.5579 \cdot (M_{plus})^{0.15178} \cdot \rho_{plus}^{-1.18241}
$

$
\rho_i = 6.0108 \cdot M_i^{0.17947} \cdot K_w^{-1.18241}
$

(Note: Molar masses are in g/mol and densities in g/cm³ for these correlations).

### 2.2 Running Characterization
Once models are set, execute the characterization.

```java
system.getCharacterization().characterisePlusFraction();
```

This process will:
1.  Extrapolate the molar distribution to C80+.
2.  Calculate properties for each carbon number.
3.  Group them into pseudo-components (if lumping is enabled).

## 3. Lumping Models

To reduce simulation time, it is often necessary to group the many characterized components into a smaller number of "lumped" pseudo-components. NeqSim provides three lumping models with different behaviors.

### 3.1 Available Lumping Models

| Lumping Model | Behavior | Use Case |
|---------------|----------|----------|
| `"PVTlumpingModel"` | Keeps TBP fractions (C6-C9) as separate pseudo-components, only lumps the plus fraction (C10+) | When you want to preserve individual TBP fractions (default) |
| `"standard"` | Lumps **all** TBP fractions and plus fractions together into N pseudo-components | When you want fewer total heavy components starting from C6 |
| `"no lumping"` | Keeps all individual carbon numbers (C6, C7...C80) | Maximum detail (slower simulation) |

### 3.2 Understanding numberOfPseudoComponents vs numberOfLumpedComponents

The lumping model has two configuration parameters:

| Method | What it Controls |
|--------|------------------|
| `setNumberOfPseudoComponents(n)` | **Total** number of pseudo-components (TBP + lumped) |
| `setNumberOfLumpedComponents(n)` | Number of groups created from the **plus fraction only** |

#### Recommended Method by Model

| Model | Recommended Method | Reason |
|-------|-------------------|--------|
| `"standard"` | `setNumberOfPseudoComponents(n)` | Directly controls total pseudo-components |
| `"PVTlumpingModel"` | `setNumberOfLumpedComponents(n)` | Directly controls C10+ grouping without side effects |

---

### 3.3 Fluent Configuration API (Recommended)

NeqSim provides a fluent builder API that makes lumping configuration clearer and less error-prone:

```java
// PVTlumpingModel: keep C6-C9 separate, lump C10+ into 5 groups
fluid.getCharacterization().configureLumping()
    .model("PVTlumpingModel")
    .plusFractionGroups(5)
    .build();

// Standard model: create exactly 6 total pseudo-components from C6+
fluid.getCharacterization().configureLumping()
    .model("standard")
    .totalPseudoComponents(6)
    .build();

// No lumping: keep all individual SCN components
fluid.getCharacterization().configureLumping()
    .noLumping()
    .build();
```

#### Custom Carbon Number Boundaries

Match specific PVT lab report groupings by specifying carbon number boundaries:

```java
// Creates groups: C6, C7-C9, C10-C14, C15-C19, C20+
fluid.getCharacterization().configureLumping()
    .customBoundaries(6, 7, 10, 15, 20)
    .build();
```

| Boundary Array | Resulting Groups |
|----------------|------------------|
| `[6, 10, 20]` | C6-C9, C10-C19, C20+ |
| `[6, 7, 10, 15, 20]` | C6, C7-C9, C10-C14, C15-C19, C20+ |

---

#### Behavior in `"standard"` Lumping Model

In the **standard** model, use `setNumberOfPseudoComponents(n)` to specify the **total** number of pseudo-components created from all heavy fractions (C6 through C80). All TBP fractions and plus fractions are combined and redistributed into equal-weight groups.

```java
fluid.getCharacterization().setLumpingModel("standard");
fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(5);
// Result: 5 pseudo-components covering C6 through C80 (PC1, PC2, PC3, PC4, PC5)
```

---

#### Behavior in `"PVTlumpingModel"` (default)

In the **PVTlumpingModel**, the two parameters interact:

- **`setNumberOfLumpedComponents(n)`**: Number of groups created from the **plus fraction only** (e.g., C10+). TBP fractions (C6-C9) remain as separate pseudo-components.
- **`setNumberOfPseudoComponents(n)`**: **Total** pseudo-components = TBP fractions + lumped components

The relationship:
```
numberOfLumpedComponents = numberOfPseudoComponents - numberOfDefinedTBPComponents
```

**Example** with 4 TBP fractions (C6, C7, C8, C9):

| You Set | Calculation | Final Result |
|---------|-------------|--------------|
| `setNumberOfPseudoComponents(12)` | 12 - 4 = 8 lumped | 4 TBP + 8 lumped = **12 total** |
| `setNumberOfLumpedComponents(8)` | 4 + 8 = 12 total | 4 TBP + 8 lumped = **12 total** |

Both give the same result in this case.

##### ⚠️ Gotcha: Minimum Lumped Components Override

If you set `setNumberOfPseudoComponents()` too low, the model may **override** your setting!

**Example**: With 4 TBP fractions and `setNumberOfPseudoComponents(5)`:
- Calculated lumped = 5 - 4 = **1**
- But default `numberOfLumpedComponents` is **7**
- Since 1 < 7, the model overrides: 4 + 7 = **11 total** (not 5!)

**Solution**: Use `setNumberOfLumpedComponents()` directly for PVTlumpingModel:

```java
// RECOMMENDED for PVTlumpingModel - directly control C10+ grouping
fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(5);
// Result: C6_PC, C7_PC, C8_PC, C9_PC + 5 lumped groups from C10+ = 9 total
```

---

### 3.3 Quick Reference: Which Method to Use

| I want to... | Model | Method |
|--------------|-------|--------|
| Get exactly N total pseudo-components (lumping from C6) | `"standard"` | `setNumberOfPseudoComponents(N)` |
| Keep C6-C9 separate, lump C10+ into N groups | `"PVTlumpingModel"` | `setNumberOfLumpedComponents(N)` |
| Keep all SCN components (C6-C80) | `"no lumping"` | N/A |

### 3.4 Choosing the Right Model

| Scenario | Recommended Model | Configuration |
|----------|-------------------|---------------|
| Standard PVT simulation | `"PVTlumpingModel"` | `configureLumping().plusFractionGroups(6-8)` |
| Minimal components for speed | `"standard"` | `configureLumping().totalPseudoComponents(3-5)` |
| Detailed compositional study | `"no lumping"` | `configureLumping().noLumping()` |
| Match specific software output | Use custom boundaries | `configureLumping().customBoundaries(...)` |

### 3.5 Full Examples

#### Example 1: PVTlumpingModel with Fluent API (Recommended)

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FluentLumpingExample {
    public static void main(String[] args) {
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
        fluid.addComponent("methane", 60.0);
        fluid.addComponent("ethane", 5.0);
        fluid.addComponent("propane", 3.0);
        
        fluid.getCharacterization().setTBPModel("PedersenSRK");
        
        // Add TBP fractions (these will be preserved as individual pseudo-components)
        fluid.addTBPfraction("C6", 1.0, 0.086, 0.66);
        fluid.addTBPfraction("C7", 2.0, 0.092, 0.73);
        fluid.addTBPfraction("C8", 2.0, 0.104, 0.76);
        fluid.addTBPfraction("C9", 1.0, 0.118, 0.78);
        fluid.addPlusFraction("C10+", 15.0, 0.280, 0.84);
        
        fluid.getCharacterization().setPlusFractionModel("Pedersen");
        
        // Fluent API: Lump C10+ into 5 groups (C6-C9 remain separate)
        fluid.getCharacterization().configureLumping()
            .model("PVTlumpingModel")
            .plusFractionGroups(5)
            .build();
        
        fluid.getCharacterization().characterisePlusFraction();
        // Result: C6_PC, C7_PC, C8_PC, C9_PC + 5 lumped groups = 9 pseudo-components
        
        fluid.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        fluid.prettyPrint();
    }
}
```

#### Example 2: Standard model with Fluent API

```java
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class StandardLumpingExample {
    public static void main(String[] args) {
        SystemPrEos fluid = new SystemPrEos(298.15, 50.0);
        fluid.addComponent("methane", 60.0);
        fluid.addComponent("ethane", 5.0);
        fluid.addComponent("propane", 3.0);
        
        fluid.getCharacterization().setTBPModel("PedersenPR");
        
        fluid.addTBPfraction("C6", 1.0, 0.086, 0.66);
        fluid.addTBPfraction("C7", 2.0, 0.092, 0.73);
        fluid.addTBPfraction("C8", 2.0, 0.104, 0.76);
        fluid.addTBPfraction("C9", 1.0, 0.118, 0.78);
        fluid.addPlusFraction("C10+", 15.0, 0.280, 0.84);
        
        fluid.getCharacterization().setPlusFractionModel("Pedersen");
        
        // Fluent API: Lump ALL heavy fractions (C6 through C80) into 5 pseudo-components
        fluid.getCharacterization().configureLumping()
            .model("standard")
            .totalPseudoComponents(5)
            .build();
        
        fluid.getCharacterization().characterisePlusFraction();
        // Result: 5 pseudo-components covering C6-C80 (PC1, PC2, PC3, PC4, PC5)
        
        fluid.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        fluid.prettyPrint();
    }
}
```

#### Example 3: Custom Boundaries (Match PVT Report Groupings)

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CustomBoundariesExample {
    public static void main(String[] args) {
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
        fluid.addComponent("methane", 60.0);
        fluid.addComponent("ethane", 5.0);
        fluid.addComponent("propane", 3.0);
        
        fluid.getCharacterization().setTBPModel("PedersenSRK");
        
        fluid.addTBPfraction("C6", 1.0, 0.086, 0.66);
        fluid.addTBPfraction("C7", 2.0, 0.092, 0.73);
        fluid.addTBPfraction("C8", 2.0, 0.104, 0.76);
        fluid.addTBPfraction("C9", 1.0, 0.118, 0.78);
        fluid.addPlusFraction("C10+", 15.0, 0.280, 0.84);
        
        fluid.getCharacterization().setPlusFractionModel("Pedersen");
        
        // Custom boundaries to match PVT lab report: C6, C7-C9, C10-C14, C15-C19, C20+
        fluid.getCharacterization().configureLumping()
            .customBoundaries(6, 7, 10, 15, 20)
            .build();
        
        fluid.getCharacterization().characterisePlusFraction();
        
        fluid.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        fluid.prettyPrint();
    }
}
```

#### Example 4: Python (neqsim-python)

```python
from neqsim.thermo.thermoTools import TPflash, printFrame, fluid

# Create fluid
fluid1 = fluid('pr')
fluid1.addComponent("methane", 65.0)
fluid1.addComponent("ethane", 3.0)
fluid1.addComponent("propane", 2.0)

fluid1.getCharacterization().setTBPModel("PedersenPR")

fluid1.addTBPfraction("C6", 1.0, 90.0 / 1000.0, 0.7)
fluid1.addTBPfraction("C7", 1.0, 110.0 / 1000.0, 0.73)
fluid1.addTBPfraction("C8", 1.0, 120.0 / 1000.0, 0.76)
fluid1.addTBPfraction("C9", 1.0, 140.0 / 1000.0, 0.79)
fluid1.addPlusFraction("C10", 11.0, 290.0 / 1000.0, 0.82)

fluid1.getCharacterization().setPlusFractionModel("Pedersen")

# Option A: Fluent API (Recommended) - PVTlumpingModel with 6 plus fraction groups
fluid1.getCharacterization().configureLumping() \
    .model("PVTlumpingModel") \
    .plusFractionGroups(6) \
    .build()

# Option B: Legacy API - PVTlumpingModel keeps C6-C9 separate
# fluid1.getCharacterization().setLumpingModel("PVTlumpingModel")
# fluid1.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6)

# Option C: Fluent API - standard model lumps everything from C6
# fluid1.getCharacterization().configureLumping() \
#     .model("standard") \
#     .totalPseudoComponents(5) \
#     .build()

# Option D: Custom boundaries to match lab report groupings
# fluid1.getCharacterization().configureLumping() \
#     .customBoundaries(6, 10, 15, 20) \  # C6-C9, C10-C14, C15-C19, C20+
#     .build()

fluid1.getCharacterization().characterisePlusFraction()

fluid1.setMixingRule('classic')
fluid1.setTemperature(80.0, 'C')
fluid1.setPressure(30.0, 'bara')

TPflash(fluid1)
printFrame(fluid1)
```

## 4. Advanced Options

*   **Heavy Oil**: For very heavy oils, use `setPlusFractionModel("Pedersen Heavy Oil")`.
*   **Whitson Gamma**: Use `setPlusFractionModel("Whitson Gamma")` if you have specific gamma distribution parameters.
*   **No Lumping**: To keep all individual carbon number components (C6, C7... C80), use `configureLumping().noLumping().build()` or `setLumpingModel("no lumping")`. Note that this will result in a system with many components, which is slower to simulate.
*   **Custom Boundaries**: Match PVT lab report groupings with `configureLumping().customBoundaries(6, 10, 20).build()`.

## 5. Common Issues and Solutions

### Issue: More pseudo-components than expected

**Problem**: Setting `setNumberOfPseudoComponents(5)` with `PVTlumpingModel` results in more than 5 components.

**Cause**: With `PVTlumpingModel`, the TBP fractions (C6-C9) are preserved separately. If you have 4 TBP fractions and request 5 total, that leaves only 1 lumped component for C10+. However, the default `numberOfLumpedComponents` is 7, so the model overrides your setting. A warning is now logged when this occurs.

**Solution**: Use the fluent API or `setNumberOfLumpedComponents()`:
```java
// Fluent API (recommended)
fluid.getCharacterization().configureLumping()
    .model("PVTlumpingModel")
    .plusFractionGroups(5)  // Directly controls C10+ grouping
    .build();

// Or legacy API
fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(5);
```

### Issue: Want to start lumping from C6 or C7

**Problem**: You want all heavy fractions lumped together, not just C10+.

**Solution**: Use the `"standard"` lumping model:
```java
// Fluent API (recommended)
fluid.getCharacterization().configureLumping()
    .model("standard")
    .totalPseudoComponents(6)
    .build();

// Or legacy API
fluid.getCharacterization().setLumpingModel("standard");
fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(6);
```

### Issue: Need to match specific PVT lab report groupings

**Problem**: Your PVT report uses groupings like C6, C7-C9, C10-C14, C15-C19, C20+ and you need to match exactly.

**Solution**: Use custom boundaries:
```java
fluid.getCharacterization().configureLumping()
    .customBoundaries(6, 7, 10, 15, 20)
    .build();
```

## 6. API Deprecation Notice

> ⚠️ **Deprecation**: For `PVTlumpingModel`, the method `setNumberOfPseudoComponents()` is deprecated because it can lead to unexpected override behavior. Use one of these alternatives:
>
> - **Fluent API**: `configureLumping().plusFractionGroups(n).build()`
> - **Legacy API**: `getLumpingModel().setNumberOfLumpedComponents(n)`
