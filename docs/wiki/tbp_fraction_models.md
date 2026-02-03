---
title: "TBP Fraction Models in NeqSim"
description: "This guide provides comprehensive documentation on True Boiling Point (TBP) fraction models available in NeqSim for petroleum fluid characterization."
---

# TBP Fraction Models in NeqSim

This guide provides comprehensive documentation on True Boiling Point (TBP) fraction models available in NeqSim for petroleum fluid characterization.

## 1. Introduction

### What are TBP Models?

TBP (True Boiling Point) models are empirical correlations that estimate the critical properties of petroleum pseudo-components from easily measured bulk properties like molecular weight (MW) and specific gravity (SG). These critical properties are essential inputs for cubic equations of state (EOS) such as SRK and Peng-Robinson.

### Why Do We Need Them?

Petroleum fluids contain thousands of individual hydrocarbon species that cannot all be individually identified and characterized. Instead, heavy fractions (typically C7+) are lumped into pseudo-components. TBP models provide the thermodynamic properties needed for EOS calculations:

- **Critical Temperature (Tc)**: Temperature above which a pure substance cannot exist as a liquid
- **Critical Pressure (Pc)**: Pressure required to liquefy a gas at its critical temperature
- **Acentric Factor (ω)**: Measure of molecular non-sphericity, affects vapor pressure behavior

## 2. Available Models

NeqSim provides 10 TBP models, each optimized for different applications:

| Model Name | Best Application | Key Feature |
|------------|------------------|-------------|
| `PedersenSRK` | General SRK EOS | Default, auto light/heavy switching |
| `PedersenSRKHeavyOil` | Heavy oils with SRK | Optimized for MW > 500 g/mol |
| `PedersenPR` | General PR EOS | Optimized for Peng-Robinson |
| `PedersenPR2` | PR EOS alternate | Søreide boiling point correlation |
| `PedersenPRHeavyOil` | Heavy oils with PR | For viscous/heavy crude |
| `RiaziDaubert` | Light fractions | Best for MW < 300 g/mol |
| `Lee-Kesler` | General purpose | Uses Watson K-factor |
| `Twu` | Paraffinic fluids | n-alkane reference method |
| `Cavett` | Refining industry | API gravity corrections |
| `Standing` | Reservoir engineering | Simple, widely used |

## 3. Model Details

### 3.1 Pedersen Models (PedersenSRK, PedersenPR)

**Best for:** General purpose petroleum characterization

The Pedersen correlations are the default and most widely used TBP models in NeqSim. They were specifically developed for use with cubic equations of state.

#### Correlations

**Critical Temperature:**
$$T_c = a_0 \cdot \rho + a_1 \cdot \ln(M) + a_2 \cdot M + \frac{a_3}{M}$$

**Critical Pressure:**
$$P_c = \exp\left(b_0 + b_1 \cdot \rho^{b_4} + \frac{b_2}{M} + \frac{b_3}{M^2}\right)$$

**EOS m-parameter:**
$$m = c_0 + c_1 \cdot M + c_2 \cdot \rho + c_3 \cdot M^2$$

The model automatically switches between light oil and heavy oil coefficients at MW = 1120 g/mol.

#### Usage

```java
import neqsim.thermo.system.SystemSrkEos;

// For SRK equation of state
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("PedersenSRK");
fluid.addTBPfraction("C7", 1.0, 0.092, 0.73);
```

```java
import neqsim.thermo.system.SystemPrEos;

// For Peng-Robinson equation of state
SystemPrEos fluid = new SystemPrEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("PedersenPR");
fluid.addTBPfraction("C7", 1.0, 0.092, 0.73);
```

**Reference:** Pedersen, K.S., Thomassen, P., Fredenslund, A. (1984). "Thermodynamics of Petroleum Mixtures Containing Heavy Hydrocarbons." *Ind. Eng. Chem. Process Des. Dev.*, 23, 566-573.

---

### 3.2 Riazi-Daubert Model

**Best for:** Light to medium petroleum fractions (MW < 300 g/mol)

The Riazi-Daubert model uses a simple exponential-power law form that works well for lighter fractions. For heavier fractions (MW > 300), it automatically falls back to the Pedersen model.

#### Correlations

**Critical Temperature (K):**
$$T_c = \frac{5}{9} \times 554.4 \times \exp(-1.3478 \times 10^{-4} \cdot M - 0.61641 \cdot SG) \times M^{0.2998} \times SG^{1.0555}$$

**Critical Pressure (bar):**
$$P_c = 0.068947 \times 4.5203 \times 10^{4} \times \exp(-1.8078 \times 10^{-3} \cdot M - 0.3084 \cdot SG) \times M^{-0.8063} \times SG^{1.6015}$$

**Boiling Point (K):**
$$T_b = 97.58 \times M^{0.3323} \times SG^{0.04609}$$

#### Applicability Range
- Molecular weight: 70-300 g/mol
- Specific gravity: 0.65-0.90

#### Usage

```java
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("RiaziDaubert");
fluid.addTBPfraction("C7", 1.0, 0.092, 0.73);  // Light fraction - uses Riazi-Daubert
fluid.addTBPfraction("C30", 0.5, 0.400, 0.90); // Heavy fraction - falls back to Pedersen
```

**Reference:** Riazi, M.R. and Daubert, T.E. (1980). "Simplify Property Predictions." *Hydrocarbon Processing*, 59(3), 115-116.

---

### 3.3 Lee-Kesler Model

**Best for:** General purpose characterization, especially when Watson K-factor is known

The Lee-Kesler model is based on generalized correlations using boiling point and specific gravity as primary inputs. It is widely used in the petroleum industry.

#### Correlations

**Critical Temperature (K):**
$$T_c = 189.8 + 450.6 \cdot SG + (0.4244 + 0.1174 \cdot SG) \cdot T_b + (0.1441 - 1.0069 \cdot SG) \times \frac{10^5}{T_b}$$

**Critical Pressure (bar):**
$$\ln(P_c) = 3.3864 - \frac{0.0566}{SG} - f(T_b, SG)$$

where $f(T_b, SG)$ is a polynomial function of boiling point and specific gravity.

**Acentric Factor (Kesler-Lee):**

For $T_{br} < 0.8$:
$$\omega = \frac{\ln(P_{br}) - 5.92714 + \frac{6.09649}{T_{br}} + 1.28862 \cdot \ln(T_{br}) - 0.169347 \cdot T_{br}^6}{15.2518 - \frac{15.6875}{T_{br}} - 13.4721 \cdot \ln(T_{br}) + 0.43577 \cdot T_{br}^6}$$

For $T_{br} \geq 0.8$:
$$\omega = -7.904 + 0.1352 \cdot K_w - 0.007465 \cdot K_w^2 + 8.359 \cdot T_{br} + \frac{1.408 - 0.01063 \cdot K_w}{T_{br}}$$

where $T_{br} = T_b/T_c$ and $K_w$ is the Watson characterization factor.

#### Usage

```java
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("Lee-Kesler");
fluid.addTBPfraction("C10", 1.0, 0.142, 0.78);
```

**Reference:** Kesler, M.G. and Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions." *Hydrocarbon Processing*, 55(3), 153-158.

---

### 3.4 Twu Model

**Best for:** Paraffinic fluids and gas condensates (Watson K > 12)

The Twu model uses n-alkanes as reference compounds and applies perturbation corrections based on specific gravity differences. This approach is particularly accurate for waxy/paraffinic petroleum fractions.

#### Method Overview

1. Calculate reference n-alkane properties from boiling point
2. Apply perturbation corrections based on $\Delta SG = SG_{alkane} - SG_{actual}$
3. Iterate to find equivalent n-alkane molecular weight

#### Key Equations

**n-Alkane Critical Temperature:**
$$T_{c,alk} = T_b \times \left[0.533272 + 0.343831 \times 10^{-3} \cdot T_b + 2.526167 \times 10^{-7} \cdot T_b^2 - 1.65848 \times 10^{-10} \cdot T_b^3 + \frac{4.60774 \times 10^{24}}{T_b^{13}}\right]^{-1}$$

**Perturbation Function:**
$$f_T = \Delta S_T \times \left(-0.270159 \cdot T_b^{-0.5} + (0.0398285 - 0.706691 \cdot T_b^{-0.5}) \cdot \Delta S_T\right)$$

where $\Delta S_T = \exp(5.0 \cdot (SG_{alk} - SG)) - 1$

**Corrected Critical Temperature:**
$$T_c = T_{c,alk} \times \left(\frac{1 + 2f_T}{1 - 2f_T}\right)^2$$

#### When to Use

- Gas condensates with high paraffin content
- Waxy crudes
- Light oils with K_w > 12

#### Usage

```java
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("Twu");
fluid.addTBPfraction("C10", 1.0, 0.142, 0.78);
```

**Reference:** Twu, C.H. (1984). "An Internally Consistent Correlation for Predicting the Critical Properties and Molecular Weights of Petroleum and Coal-Tar Liquids." *Fluid Phase Equilibria*, 16, 137-150.

---

### 3.5 Cavett Model

**Best for:** Refining industry applications, heavy oils with API gravity data

The Cavett model in NeqSim uses a hybrid Lee-Kesler/Cavett approach with API gravity corrections. This provides robust results across a wide range of petroleum fractions while maintaining the API gravity sensitivity important for refining applications.

#### API Gravity Relationship

$$API = \frac{141.5}{SG} - 131.5$$

| API Range | Classification | SG Range |
|-----------|---------------|----------|
| > 31.1° | Light crude | < 0.87 |
| 22.3° - 31.1° | Medium crude | 0.87 - 0.92 |
| < 22.3° | Heavy crude | > 0.92 |

#### Correlations

The model uses Lee-Kesler correlations as the base, with API corrections for heavy fractions:

**Critical Temperature (API < 30°):**
$$T_c = T_{c,LK} \times [1 + 0.002 \cdot (30 - API)]$$

**Critical Pressure (API < 30°):**
$$P_c = P_{c,LK} \times [1 + 0.001 \cdot (30 - API)]$$

**Acentric Factor (Edmister):**
$$\omega = \frac{3}{7} \times \frac{\log_{10}(P_c/P_{ref})}{T_c/T_b - 1} - 1$$

Bounded to range [0.0, 1.5] for physical validity.

#### Usage

```java
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("Cavett");

// Heavy oil example (API ~ 20°)
fluid.addTBPfraction("HeavyFrac", 1.0, 0.300, 0.93);
```

**Reference:** Cavett, R.H. (1962). "Physical Data for Distillation Calculations, Vapor-Liquid Equilibria." *Proc. 27th API Meeting*, San Francisco.

---

### 3.6 Standing Model

**Best for:** Reservoir engineering, quick estimates, black oil PVT

The Standing model uses Riazi-Daubert style correlations for robust critical property estimation. It's widely used in reservoir simulation tools.

#### Correlations

Same as Riazi-Daubert (see Section 3.2).

#### Usage

```java
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.getCharacterization().setTBPModel("Standing");
fluid.addTBPfraction("C7", 1.0, 0.092, 0.73);
```

**Reference:** Standing, M.B. (1977). "Volumetric and Phase Behavior of Oil Field Hydrocarbon Systems." SPE, Dallas.

---

## 4. Watson Characterization Factor

The Watson characterization factor ($K_w$) is useful for classifying petroleum fractions and selecting appropriate TBP models:

$$K_w = \frac{(1.8 \cdot T_b)^{1/3}}{SG}$$

| K_w Range | Fluid Type | Recommended Model |
|-----------|------------|-------------------|
| > 12.5 | Paraffinic (gas condensates) | Twu |
| 11.5 - 12.5 | Mixed/intermediate | Pedersen or Lee-Kesler |
| 10.5 - 11.5 | Naphthenic | Pedersen or RiaziDaubert |
| < 10.5 | Aromatic | Pedersen |

### Calculating K_w in NeqSim

```java
TBPfractionModel tbpModel = new TBPfractionModel();
double Kw = tbpModel.calcWatsonKFactor(0.142, 0.78);  // MW in kg/mol, density in g/cm³
System.out.println("Watson K-factor: " + Kw);
```

---

## 5. Model Selection Guidelines

### 5.1 Decision Tree

```
Is EOS = Peng-Robinson?
├── Yes → Is fluid heavy (MW > 500)?
│         ├── Yes → PedersenPRHeavyOil
│         └── No → PedersenPR
└── No (SRK) → Is fluid heavy (MW > 500)?
              ├── Yes → PedersenSRKHeavyOil
              └── No → Is K_w > 12 (paraffinic)?
                        ├── Yes → Twu
                        └── No → Is MW < 300?
                                  ├── Yes → RiaziDaubert or Lee-Kesler
                                  └── No → PedersenSRK
```

### 5.2 Automatic Model Recommendation

NeqSim can recommend an appropriate model based on fluid properties:

```java
TBPfractionModel tbpModel = new TBPfractionModel();
String recommended = tbpModel.recommendTBPModel(
    0.200,    // Average MW in kg/mol
    0.85,     // Average density in g/cm³
    "SRK"     // EOS type: "SRK" or "PR"
);
System.out.println("Recommended model: " + recommended);
```

### 5.3 List All Available Models

```java
String[] models = TBPfractionModel.getAvailableModels();
for (String model : models) {
    System.out.println(model);
}
```

---

## 6. Typical Property Values

Reference values for common petroleum fractions:

| Component | MW (g/mol) | SG | T_c (K) | P_c (bar) | ω |
|-----------|------------|------|---------|-----------|------|
| n-Heptane (C7) | 100 | 0.684 | 540 | 27.4 | 0.35 |
| C7 (typical) | 96-100 | 0.72-0.74 | 540-560 | 27-30 | 0.30-0.35 |
| C10 | 134-142 | 0.76-0.79 | 600-640 | 20-25 | 0.45-0.55 |
| C15 | 200-210 | 0.81-0.83 | 680-720 | 15-18 | 0.65-0.75 |
| C20 | 275-285 | 0.85-0.87 | 750-800 | 12-15 | 0.85-0.95 |
| C30 | 400-420 | 0.88-0.90 | 850-900 | 8-10 | 1.0-1.2 |

---

## 7. Complete Examples

### 7.1 Basic Fluid Characterization

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class BasicCharacterization {
    public static void main(String[] args) {
        // Create SRK fluid
        SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
        
        // Add light components
        fluid.addComponent("methane", 70.0);
        fluid.addComponent("ethane", 10.0);
        fluid.addComponent("propane", 5.0);
        
        // Set TBP model before adding heavy fractions
        fluid.getCharacterization().setTBPModel("PedersenSRK");
        
        // Add TBP fractions
        fluid.addTBPfraction("C7", 3.0, 0.092, 0.73);
        fluid.addTBPfraction("C8", 2.5, 0.104, 0.76);
        fluid.addTBPfraction("C9", 2.0, 0.118, 0.78);
        fluid.addTBPfraction("C10", 1.5, 0.134, 0.79);
        
        // Set mixing rule and initialize
        fluid.setMixingRule("classic");
        
        // Perform flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Print results
        fluid.prettyPrint();
        
        // Access individual component properties
        System.out.println("\nC7 Critical Properties:");
        System.out.println("Tc = " + fluid.getComponent("C7_PC").getTC() + " K");
        System.out.println("Pc = " + fluid.getComponent("C7_PC").getPC() + " bar");
        System.out.println("omega = " + fluid.getComponent("C7_PC").getAcentricFactor());
    }
}
```

### 7.2 Comparing Different TBP Models

```java
import neqsim.thermo.system.SystemSrkEos;

public class ModelComparison {
    public static void main(String[] args) {
        String[] models = {"PedersenSRK", "Lee-Kesler", "RiaziDaubert", "Twu", "Cavett", "Standing"};
        
        System.out.println("=== TBP Model Comparison for C10 (MW=142 g/mol, SG=0.78) ===");
        System.out.printf("%-15s %10s %10s %10s%n", "Model", "Tc (K)", "Pc (bar)", "omega");
        System.out.println(StringUtils.repeat("-", 50));
        
        for (String modelName : models) {
            SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
            fluid.getCharacterization().setTBPModel(modelName);
            fluid.addTBPfraction("C10", 1.0, 0.142, 0.78);
            
            double Tc = fluid.getComponent(0).getTC();
            double Pc = fluid.getComponent(0).getPC();
            double omega = fluid.getComponent(0).getAcentricFactor();
            
            System.out.printf("%-15s %10.2f %10.2f %10.4f%n", modelName, Tc, Pc, omega);
        }
    }
}
```

### 7.3 Gas Condensate with Twu Model

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class GasCondensateExample {
    public static void main(String[] args) {
        SystemSrkEos fluid = new SystemSrkEos(350.0, 150.0);
        
        // Lean gas condensate composition
        fluid.addComponent("nitrogen", 1.5);
        fluid.addComponent("CO2", 2.0);
        fluid.addComponent("methane", 80.0);
        fluid.addComponent("ethane", 6.0);
        fluid.addComponent("propane", 3.0);
        fluid.addComponent("i-butane", 0.8);
        fluid.addComponent("n-butane", 1.2);
        fluid.addComponent("i-pentane", 0.5);
        fluid.addComponent("n-pentane", 0.5);
        
        // Use Twu model for paraffinic gas condensate
        fluid.getCharacterization().setTBPModel("Twu");
        
        // Add C6+ fractions
        fluid.addTBPfraction("C6", 1.0, 0.086, 0.68);
        fluid.addTBPfraction("C7+", 3.5, 0.130, 0.76);
        
        fluid.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        System.out.println("Gas Condensate Flash at " + fluid.getTemperature() + " K, " 
            + fluid.getPressure() + " bar");
        fluid.prettyPrint();
    }
}
```

### 7.4 Heavy Oil Characterization

```java
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class HeavyOilExample {
    public static void main(String[] args) {
        // Use Peng-Robinson for heavy oil
        SystemPrEos fluid = new SystemPrEos(333.15, 10.0);
        
        // Light ends
        fluid.addComponent("methane", 5.0);
        fluid.addComponent("ethane", 2.0);
        fluid.addComponent("propane", 3.0);
        fluid.addComponent("n-butane", 2.0);
        fluid.addComponent("n-pentane", 3.0);
        
        // Use heavy oil model
        fluid.getCharacterization().setTBPModel("PedersenPRHeavyOil");
        
        // Heavy fractions (API ~ 15°, SG ~ 0.96)
        fluid.addTBPfraction("C6-C10", 15.0, 0.120, 0.80);
        fluid.addTBPfraction("C11-C20", 25.0, 0.250, 0.88);
        fluid.addTBPfraction("C21-C30", 20.0, 0.380, 0.92);
        fluid.addTBPfraction("C31+", 25.0, 0.550, 0.96);
        
        fluid.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        System.out.println("Heavy Oil Flash at " + fluid.getTemperature() + " K");
        fluid.prettyPrint();
    }
}
```

---

## 8. Python (neqsim-python) Examples

### 8.1 Basic Usage

```python
from neqsim.thermo import fluid

# Create fluid with TBP model selection
oil = fluid('srk')
oil.getCharacterization().setTBPModel("PedersenSRK")

# Add components
oil.addComponent("methane", 70.0)
oil.addComponent("ethane", 10.0)
oil.addTBPfraction("C7", 5.0, 0.092, 0.73)
oil.addTBPfraction("C10", 3.0, 0.142, 0.78)

oil.setMixingRule("classic")
oil.setTemperature(298.15, "K")
oil.setPressure(50.0, "bara")

# Flash and print
from neqsim.thermodynamicoperations import TPflash
TPflash(oil)
oil.initProperties()
oil.prettyPrint()
```

### 8.2 Direct Java API Access

```python
from jpype import JClass

# Import Java classes directly
SystemSrkEos = JClass('neqsim.thermo.system.SystemSrkEos')
TBPfractionModel = JClass('neqsim.thermo.characterization.TBPfractionModel')

# Create fluid
fluid = SystemSrkEos(298.15, 50.0)

# Get model recommendation
tbpModel = TBPfractionModel()
recommended = tbpModel.recommendTBPModel(0.200, 0.85, "SRK")
print(f"Recommended model: {recommended}")

# Set model and add fractions
fluid.getCharacterization().setTBPModel(recommended)
fluid.addTBPfraction("C10", 1.0, 0.142, 0.78)

# Print critical properties
print(f"Tc = {fluid.getComponent(0).getTC()} K")
print(f"Pc = {fluid.getComponent(0).getPC()} bar")
```

---

## 9. Troubleshooting

### Common Issues

1. **Unrealistic Tc values (> 1000 K or < 400 K)**
   - Check that MW is in kg/mol (not g/mol)
   - Check that density is in g/cm³ (not kg/m³)
   - Try a different model (Pedersen is most robust)

2. **Negative acentric factor**
   - Usually indicates incorrect input data
   - Cavett model has built-in bounds checking [0, 1.5]

3. **Flash convergence issues with heavy fractions**
   - Use heavy oil variants (PedersenSRKHeavyOil, PedersenPRHeavyOil)
   - Check that fluid composition is normalized

4. **Model not found error**
   - Model names are case-sensitive
   - Use `TBPfractionModel.getAvailableModels()` to see valid names

### Input Unit Requirements

| Property | Required Unit | Common Mistake |
|----------|---------------|----------------|
| Molecular Weight | kg/mol | Using g/mol (divide by 1000) |
| Density | g/cm³ | Using kg/m³ (divide by 1000) |
| Temperature | K | (internal) |
| Pressure | bar | (internal) |

---

## 10. References

1. Pedersen, K.S., Thomassen, P., Fredenslund, A. (1984). "Thermodynamics of Petroleum Mixtures Containing Heavy Hydrocarbons." *Ind. Eng. Chem. Process Des. Dev.*, 23, 566-573.

2. Kesler, M.G., Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions." *Hydrocarbon Processing*, 55(3), 153-158.

3. Riazi, M.R., Daubert, T.E. (1980). "Simplify Property Predictions." *Hydrocarbon Processing*, 59(3), 115-116.

4. Twu, C.H. (1984). "An Internally Consistent Correlation for Predicting the Critical Properties and Molecular Weights of Petroleum and Coal-Tar Liquids." *Fluid Phase Equilibria*, 16, 137-150.

5. Cavett, R.H. (1962). "Physical Data for Distillation Calculations, Vapor-Liquid Equilibria." *Proc. 27th API Meeting*, San Francisco.

6. Standing, M.B. (1977). "Volumetric and Phase Behavior of Oil Field Hydrocarbon Systems." SPE, Dallas.

7. Edmister, W.C. (1958). "Applied Hydrocarbon Thermodynamics, Part 4: Compressibility Factors and Equations of State." *Petroleum Refiner*, 37(4), 173-179.

---

## See Also

- [Fluid Characterization](fluid_characterization.md) - Plus fraction models and lumping
- [PVT Simulation Workflows](pvt_simulation_workflows.md) - Complete PVT analysis
- [Thermodynamics Guide](thermodynamics_guide.md) - Equation of state selection
