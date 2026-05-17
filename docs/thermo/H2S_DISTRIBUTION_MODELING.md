---
title: H2S Distribution Modeling in NeqSim
description: Comprehensive guide to modeling hydrogen sulfide (H2S) distribution between gas, oil, and water phases using various equations of state including SRK, PR, CPA, and electrolyte models with chemical reactions.
---

# H2S Distribution Modeling in NeqSim

> **Related Resources:**
> - For a quick overview with Python examples, see [H2S Distribution Guide (quick)](H2S_distribution_guide)
> - For a hands-on Python notebook, see [H2S Distribution Modeling Tutorial](../examples/H2S_Distribution_Modeling)

## Introduction

Hydrogen sulfide (H2S) is a critical component in oil and gas processing due to its:
- **Toxicity**: Extremely hazardous at low concentrations (>10 ppm)
- **Corrosivity**: Causes severe corrosion, especially with water present
- **Regulatory requirements**: Strict limits on H2S in sales gas and produced water
- **Process design impact**: Affects sweetening unit sizing, materials selection, and safety systems

Accurate modeling of H2S partitioning between gas, oil, and water phases is essential for:
- Sour gas treating design (amine units, Claus plants)
- Produced water handling and disposal
- Corrosion prediction and materials selection
- Safety system design (flare, venting)
- Environmental compliance

## Thermodynamic Basis

### H2S Properties

| Property | Value | Unit |
|----------|-------|------|
| Molecular weight | 34.08 | g/mol |
| Critical temperature | 373.5 | K |
| Critical pressure | 89.63 | bar |
| Acentric factor | 0.094 | - |
| Dipole moment | 0.97 | Debye |
| pKa1 (25°C) | 7.0 | - |
| pKa2 (25°C) | 14.0 | - |

### Phase Behavior Characteristics

H2S exhibits complex phase behavior:

1. **Gas-Oil Partitioning**: Governed primarily by vapor-liquid equilibrium (VLE)
2. **Gas-Water Partitioning**: Involves both physical solubility and chemical reactions
3. **Oil-Water Partitioning**: Typically minor but can be significant at high pressures

The key challenge is that H2S is a **weak acid** that dissociates in water:

$$
\text{H}_2\text{S} \rightleftharpoons \text{H}^+ + \text{HS}^- \quad (K_{a1})
$$

$$
\text{HS}^- \rightleftharpoons \text{H}^+ + \text{S}^{2-} \quad (K_{a2})
$$

This means total H2S in water = molecular H2S + HS⁻ + S²⁻

## Simple Equation of State Models

### 1. Soave-Redlich-Kwong (SRK) EOS

The SRK equation of state is widely used for hydrocarbon systems:

$$
P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b)}
$$

**Advantages for H2S modeling**:
- Well-established parameters for H2S
- Good for gas-oil equilibrium
- Fast computation
- Extensive binary interaction parameter (kij) database

**Limitations**:
- Treats water as non-polar molecule
- Does not account for H2S dissociation in water
- Underestimates H2S solubility in water at high pH

#### NeqSim Example: SRK EOS

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create SRK system at reservoir conditions
SystemInterface fluid = new SystemSrkEos(353.15, 100.0);  // 80°C, 100 bara

// Add components - typical sour gas with oil and water
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-heptane", 0.10);  // Oil pseudo-component
fluid.addComponent("H2S", 0.05);         // 5 mol% H2S
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.05);

fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Run flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Get H2S distribution
if (fluid.hasPhaseType("gas")) {
    double h2sInGas = fluid.getPhase("gas").getComponent("H2S").getx();
    System.out.println("H2S mole fraction in gas: " + h2sInGas);
}
if (fluid.hasPhaseType("oil")) {
    double h2sInOil = fluid.getPhase("oil").getComponent("H2S").getx();
    System.out.println("H2S mole fraction in oil: " + h2sInOil);
}
if (fluid.hasPhaseType("aqueous")) {
    double h2sInWater = fluid.getPhase("aqueous").getComponent("H2S").getx();
    System.out.println("H2S mole fraction in water: " + h2sInWater);
}

// Calculate K-values
double kGasOil = fluid.getPhase("gas").getComponent("H2S").getx() / 
                 fluid.getPhase("oil").getComponent("H2S").getx();
double kGasWater = fluid.getPhase("gas").getComponent("H2S").getx() / 
                   fluid.getPhase("aqueous").getComponent("H2S").getx();

System.out.println("K(gas/oil) for H2S: " + kGasOil);
System.out.println("K(gas/water) for H2S: " + kGasWater);
```

### 2. Peng-Robinson (PR) EOS

The Peng-Robinson equation provides improved liquid density predictions:

$$
P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b) + b(V-b)}
$$

**Advantages over SRK**:
- Better liquid density predictions
- Slightly improved VLE for polar components
- Industry standard for many applications

**For H2S specifically**:
- Similar accuracy to SRK for gas-oil partitioning
- Still limited for aqueous phase predictions

#### NeqSim Example: PR EOS

```java
import neqsim.thermo.system.SystemPrEos;

// Create PR system
SystemInterface fluid = new SystemPrEos(353.15, 100.0);

// Add components
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.05);

fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Compare with SRK results
System.out.println("\n=== PR EOS Results ===");
printH2SDistribution(fluid);
```

### 3. Cubic-Plus-Association (CPA) EOS

CPA extends cubic EOS with an association term for hydrogen bonding:

$$
P = P_{\text{cubic}} + P_{\text{association}}
$$

The association term accounts for:
- Self-association (H2O-H2O hydrogen bonding)
- Cross-association (H2S-H2O interaction)

**Key advantage**: Properly models water's non-ideal behavior

#### NeqSim Example: CPA EOS

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Create CPA system - uses SRK as cubic part
SystemInterface fluid = new SystemSrkCPAstatoil(353.15, 100.0);

// Add components
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.05);

// CPA requires specific mixing rule
fluid.setMixingRule(10);  // CPA mixing rule
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("\n=== CPA EOS Results ===");
printH2SDistribution(fluid);
```

### Comparison of Simple EOS Models

```java
/**
 * Compare H2S distribution predictions from different EOS.
 */
public class H2SDistributionComparison {
    
    public static void main(String[] args) {
        double temperature = 353.15;  // 80°C
        double pressure = 100.0;       // 100 bara
        
        // Common composition
        double[] composition = {0.70, 0.05, 0.03, 0.10, 0.05, 0.02, 0.05};
        String[] components = {"methane", "ethane", "propane", "n-heptane", 
                               "H2S", "CO2", "water"};
        
        System.out.println("=== H2S Distribution Comparison ===");
        System.out.println("T = " + (temperature - 273.15) + " °C, P = " + pressure + " bara");
        System.out.println();
        
        // SRK
        SystemInterface srkFluid = new SystemSrkEos(temperature, pressure);
        addComponents(srkFluid, components, composition);
        srkFluid.setMixingRule("classic");
        srkFluid.setMultiPhaseCheck(true);
        new ThermodynamicOperations(srkFluid).TPflash();
        
        // PR
        SystemInterface prFluid = new SystemPrEos(temperature, pressure);
        addComponents(prFluid, components, composition);
        prFluid.setMixingRule("classic");
        prFluid.setMultiPhaseCheck(true);
        new ThermodynamicOperations(prFluid).TPflash();
        
        // CPA
        SystemInterface cpaFluid = new SystemSrkCPAstatoil(temperature, pressure);
        addComponents(cpaFluid, components, composition);
        cpaFluid.setMixingRule(10);
        cpaFluid.setMultiPhaseCheck(true);
        new ThermodynamicOperations(cpaFluid).TPflash();
        
        // Print comparison table
        System.out.println("EOS      | H2S in Gas | H2S in Oil | H2S in Water | K(g/w)");
        System.out.println("---------|------------|------------|--------------|-------");
        printRow("SRK", srkFluid);
        printRow("PR", prFluid);
        printRow("CPA", cpaFluid);
    }
    
    private static void addComponents(SystemInterface fluid, 
            String[] names, double[] fractions) {
        for (int i = 0; i < names.length; i++) {
            fluid.addComponent(names[i], fractions[i]);
        }
    }
    
    private static void printRow(String name, SystemInterface fluid) {
        double h2sGas = fluid.hasPhaseType("gas") ? 
            fluid.getPhase("gas").getComponent("H2S").getx() : 0;
        double h2sOil = fluid.hasPhaseType("oil") ? 
            fluid.getPhase("oil").getComponent("H2S").getx() : 0;
        double h2sWater = fluid.hasPhaseType("aqueous") ? 
            fluid.getPhase("aqueous").getComponent("H2S").getx() : 0;
        double kGW = h2sWater > 0 ? h2sGas / h2sWater : 0;
        
        System.out.printf("%-8s | %10.6f | %10.6f | %12.6f | %6.2f%n",
            name, h2sGas, h2sOil, h2sWater, kGW);
    }
}
```

**Expected Results** (approximate):

| EOS | H2S in Gas | H2S in Oil | H2S in Water | K(g/w) |
|-----|------------|------------|--------------|--------|
| SRK | 0.055 | 0.035 | 0.008 | 6.9 |
| PR | 0.054 | 0.036 | 0.009 | 6.0 |
| CPA | 0.053 | 0.037 | 0.012 | 4.4 |

**Key observations**:
- SRK and PR give similar gas-oil partitioning
- CPA predicts higher H2S solubility in water due to association modeling
- Simple EOS still don't capture H2S dissociation effects

## Electrolyte EOS with Chemical Reactions

### When Simple EOS Models Fail

Simple cubic EOS (SRK, PR) and even CPA have significant limitations for H2S-water systems:

1. **pH effects not captured**: H2S dissociation increases total solubility at high pH
2. **Salt effects ignored**: Produced water contains salts that affect H2S solubility
3. **No speciation**: Cannot predict HS⁻ and S²⁻ concentrations
4. **Corrosion prediction**: Requires knowing molecular H2S concentration, not total

**When you need electrolyte models**:
- Produced water with significant salt content (>1000 ppm TDS)
- High pH conditions (pH > 6) where dissociation is significant
- Corrosion rate predictions requiring molecular H2S
- Amine treating modeling
- Sour water stripping design
- Environmental discharge calculations

### H2S Chemistry in Water

In aqueous solution, H2S participates in acid-base equilibria:

**Primary dissociation** (significant at pH > 5):

$$
\text{H}_2\text{S(aq)} \rightleftharpoons \text{H}^+ + \text{HS}^- \quad pK_{a1} = 7.0
$$

**Secondary dissociation** (only at very high pH > 12):

$$
\text{HS}^- \rightleftharpoons \text{H}^+ + \text{S}^{2-} \quad pK_{a2} = 14.0
$$

**Practical implications**:
- At pH 7: ~50% molecular H2S, ~50% HS⁻
- At pH 8: ~10% molecular H2S, ~90% HS⁻
- At pH 6: ~90% molecular H2S, ~10% HS⁻

Only **molecular H2S** causes corrosion and is in VLE with gas phase!

### NeqSim Electrolyte Model

NeqSim's electrolyte model combines:
1. **Extended UNIQUAC** for activity coefficients
2. **Chemical equilibrium** for ionic species
3. **Phase equilibrium** for VLE

#### Example: Electrolyte Model with H2S Dissociation

```java
import neqsim.thermo.system.SystemFurstElectrolyteEos;

/**
 * H2S distribution with electrolyte model including dissociation.
 * 
 * This model captures:
 * - H2S dissociation to HS⁻ and S²⁻
 * - pH effects on solubility
 * - Salt effects (Na⁺, Cl⁻, etc.)
 * - Molecular vs. total H2S
 */
public class H2SElectrolyteModel {
    
    public static void main(String[] args) {
        // Create electrolyte system
        SystemInterface fluid = new SystemFurstElectrolyteEos(353.15, 50.0);
        
        // Add hydrocarbon components
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("H2S", 0.05);
        fluid.addComponent("CO2", 0.02);
        
        // Add water and electrolytes
        fluid.addComponent("water", 0.13);
        
        // For produced water with salts:
        // fluid.addComponent("Na+", 0.001);
        // fluid.addComponent("Cl-", 0.001);
        
        fluid.chemicalReactionInit();
        fluid.setMixingRule(4);  // Electrolyte mixing rule
        fluid.setMultiPhaseCheck(true);
        
        // Run flash with chemical equilibrium
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Get results
        System.out.println("=== Electrolyte Model Results ===");
        System.out.println("Temperature: " + (fluid.getTemperature() - 273.15) + " °C");
        System.out.println("Pressure: " + fluid.getPressure() + " bara");
        System.out.println();
        
        // H2S in gas phase
        if (fluid.hasPhaseType("gas")) {
            double h2sGas = fluid.getPhase("gas").getComponent("H2S").getx();
            System.out.println("H2S in gas phase: " + h2sGas + " mol/mol");
        }
        
        // H2S speciation in aqueous phase
        if (fluid.hasPhaseType("aqueous")) {
            PhaseInterface aq = fluid.getPhase("aqueous");
            
            // Molecular H2S
            double h2sMolecular = aq.getComponent("H2S").getx();
            System.out.println("Molecular H2S in water: " + h2sMolecular + " mol/mol");
            
            // HS⁻ (if available in model)
            if (aq.hasComponent("HS-")) {
                double hsMinus = aq.getComponent("HS-").getx();
                System.out.println("HS⁻ in water: " + hsMinus + " mol/mol");
            }
            
            // Total sulfide
            double totalSulfide = h2sMolecular;
            if (aq.hasComponent("HS-")) {
                totalSulfide += aq.getComponent("HS-").getx();
            }
            System.out.println("Total sulfide in water: " + totalSulfide + " mol/mol");
            
            // Calculate apparent vs. true solubility ratio
            System.out.println("Apparent/True solubility ratio: " + 
                (totalSulfide / h2sMolecular));
        }
    }
}
```

### Effect of pH on H2S Distribution

```java
/**
 * Demonstrate pH effect on H2S solubility.
 * 
 * At higher pH, more H2S dissociates to HS⁻, increasing total solubility
 * but decreasing molecular H2S (which controls VLE and corrosion).
 */
public class H2SpHEffect {
    
    public static void main(String[] args) {
        System.out.println("=== pH Effect on H2S Distribution ===");
        System.out.println("pH    | Molecular H2S | HS⁻       | Total    | % Dissociated");
        System.out.println("------|---------------|-----------|----------|---------------");
        
        // Simulate different pH by adding base (simplified)
        // In practice, pH is controlled by buffer chemistry
        
        double[] pHValues = {5.0, 6.0, 7.0, 8.0, 9.0};
        double pKa1 = 7.0;
        
        for (double pH : pHValues) {
            // Henderson-Hasselbalch: pH = pKa + log([A-]/[HA])
            // [HS-]/[H2S] = 10^(pH - pKa)
            double ratio = Math.pow(10, pH - pKa1);
            double fractionH2S = 1.0 / (1.0 + ratio);
            double fractionHS = ratio / (1.0 + ratio);
            
            // Assume base solubility (molecular H2S at equilibrium with gas)
            double baseSolubility = 0.01;  // mol/mol at low pH
            
            double molecularH2S = baseSolubility;
            double hsMinus = baseSolubility * ratio;
            double totalSulfide = molecularH2S + hsMinus;
            double percentDissociated = 100.0 * hsMinus / totalSulfide;
            
            System.out.printf("%.1f   | %.6f      | %.6f  | %.6f | %.1f%%%n",
                pH, molecularH2S, hsMinus, totalSulfide, percentDissociated);
        }
        
        System.out.println();
        System.out.println("Note: Only molecular H2S is in VLE with gas phase!");
        System.out.println("      Higher pH increases total solubility but");
        System.out.println("      molecular H2S (and corrosion potential) stays constant.");
    }
}
```

**Expected Output**:

| pH | Molecular H2S | HS⁻ | Total | % Dissociated |
|----|---------------|-----|-------|---------------|
| 5.0 | 0.010000 | 0.000100 | 0.010100 | 1.0% |
| 6.0 | 0.010000 | 0.001000 | 0.011000 | 9.1% |
| 7.0 | 0.010000 | 0.010000 | 0.020000 | 50.0% |
| 8.0 | 0.010000 | 0.100000 | 0.110000 | 90.9% |
| 9.0 | 0.010000 | 1.000000 | 1.010000 | 99.0% |

### Salt Effects on H2S Solubility

Salts affect H2S solubility through:
1. **Salting-out effect**: Decreases molecular H2S solubility
2. **Ionic strength**: Affects activity coefficients
3. **pH buffering**: Carbonate/bicarbonate systems

```java
/**
 * Demonstrate salt effect on H2S solubility.
 */
public class H2SSaltEffect {
    
    public static void main(String[] args) {
        System.out.println("=== Salt Effect on H2S Solubility ===");
        
        double[] salinities = {0.0, 10000, 50000, 100000, 200000};  // ppm NaCl
        
        System.out.println("NaCl (ppm) | Relative H2S Solubility");
        System.out.println("-----------|------------------------");
        
        for (double salinity : salinities) {
            // Sechenov equation: log(S0/S) = k_s * I
            // where I is ionic strength, k_s is Sechenov coefficient
            double ionicStrength = salinity / 58440.0;  // Approximate for NaCl
            double sechenov = 0.1;  // Typical value for H2S
            double relSolubility = Math.pow(10, -sechenov * ionicStrength);
            
            System.out.printf("%10.0f | %.3f%n", salinity, relSolubility);
        }
    }
}
```

## Complete Example: Three-Phase H2S Distribution

```java
import neqsim.thermo.system.*;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Complete example comparing simple and electrolyte models for 
 * H2S distribution in a three-phase (gas-oil-water) system.
 */
public class H2SDistributionComplete {
    
    public static void main(String[] args) {
        // System conditions
        double temperature = 353.15;  // 80°C
        double pressure = 50.0;        // 50 bara
        
        // Composition (mol fractions)
        String[] hydrocarbons = {"methane", "ethane", "propane", "n-hexane", "n-decane"};
        double[] hcFractions = {0.60, 0.05, 0.03, 0.05, 0.05};
        
        double h2sFraction = 0.05;  // 5 mol% H2S
        double co2Fraction = 0.02;
        double waterFraction = 0.15;
        
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     H2S DISTRIBUTION IN THREE-PHASE SYSTEM               ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  Temperature: %.1f °C                                    ║%n", 
            temperature - 273.15);
        System.out.printf("║  Pressure: %.1f bara                                      ║%n", 
            pressure);
        System.out.printf("║  H2S content: %.1f mol%%                                   ║%n", 
            h2sFraction * 100);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // ═══════════════════════════════════════════════════════════
        // Model 1: SRK EOS (Simple cubic)
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  MODEL 1: SRK Equation of State                          │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        
        SystemInterface srkFluid = new SystemSrkEos(temperature, pressure);
        for (int i = 0; i < hydrocarbons.length; i++) {
            srkFluid.addComponent(hydrocarbons[i], hcFractions[i]);
        }
        srkFluid.addComponent("H2S", h2sFraction);
        srkFluid.addComponent("CO2", co2Fraction);
        srkFluid.addComponent("water", waterFraction);
        srkFluid.setMixingRule("classic");
        srkFluid.setMultiPhaseCheck(true);
        
        ThermodynamicOperations srkOps = new ThermodynamicOperations(srkFluid);
        srkOps.TPflash();
        
        printResults(srkFluid, "SRK");
        
        // ═══════════════════════════════════════════════════════════
        // Model 2: PR EOS
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  MODEL 2: Peng-Robinson Equation of State                │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        
        SystemInterface prFluid = new SystemPrEos(temperature, pressure);
        for (int i = 0; i < hydrocarbons.length; i++) {
            prFluid.addComponent(hydrocarbons[i], hcFractions[i]);
        }
        prFluid.addComponent("H2S", h2sFraction);
        prFluid.addComponent("CO2", co2Fraction);
        prFluid.addComponent("water", waterFraction);
        prFluid.setMixingRule("classic");
        prFluid.setMultiPhaseCheck(true);
        
        ThermodynamicOperations prOps = new ThermodynamicOperations(prFluid);
        prOps.TPflash();
        
        printResults(prFluid, "PR");
        
        // ═══════════════════════════════════════════════════════════
        // Model 3: CPA EOS (Accounts for hydrogen bonding)
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  MODEL 3: CPA Equation of State                          │");
        System.out.println("│  (Cubic-Plus-Association - models H-bonding)             │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        
        SystemInterface cpaFluid = new SystemSrkCPAstatoil(temperature, pressure);
        for (int i = 0; i < hydrocarbons.length; i++) {
            cpaFluid.addComponent(hydrocarbons[i], hcFractions[i]);
        }
        cpaFluid.addComponent("H2S", h2sFraction);
        cpaFluid.addComponent("CO2", co2Fraction);
        cpaFluid.addComponent("water", waterFraction);
        cpaFluid.setMixingRule(10);  // CPA mixing rule
        cpaFluid.setMultiPhaseCheck(true);
        
        ThermodynamicOperations cpaOps = new ThermodynamicOperations(cpaFluid);
        cpaOps.TPflash();
        
        printResults(cpaFluid, "CPA");
        
        // ═══════════════════════════════════════════════════════════
        // Model 4: Electrolyte Model (Accounts for dissociation)
        // ═══════════════════════════════════════════════════════════
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  MODEL 4: Electrolyte Model                              │");
        System.out.println("│  (Includes H2S dissociation and ionic species)           │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        
        SystemInterface elecFluid = new SystemFurstElectrolyteEos(temperature, pressure);
        for (int i = 0; i < hydrocarbons.length; i++) {
            elecFluid.addComponent(hydrocarbons[i], hcFractions[i]);
        }
        elecFluid.addComponent("H2S", h2sFraction);
        elecFluid.addComponent("CO2", co2Fraction);
        elecFluid.addComponent("water", waterFraction);
        elecFluid.chemicalReactionInit();
        elecFluid.setMixingRule(4);
        elecFluid.setMultiPhaseCheck(true);
        
        ThermodynamicOperations elecOps = new ThermodynamicOperations(elecFluid);
        elecOps.TPflash();
        
        printResults(elecFluid, "Electrolyte");
        
        // ═══════════════════════════════════════════════════════════
        // Summary Comparison
        // ═══════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                    SUMMARY COMPARISON                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        printComparisonTable(srkFluid, prFluid, cpaFluid, elecFluid);
    }
    
    private static void printResults(SystemInterface fluid, String modelName) {
        System.out.println();
        System.out.println("Phase distribution:");
        System.out.printf("  Number of phases: %d%n", fluid.getNumberOfPhases());
        
        for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
            String phaseType = fluid.getPhase(i).getPhaseTypeName();
            double phaseFrac = fluid.getPhaseFraction(i, "mole");
            System.out.printf("  Phase %d (%s): %.4f mol/mol%n", i, phaseType, phaseFrac);
        }
        
        System.out.println();
        System.out.println("H2S distribution:");
        
        if (fluid.hasPhaseType("gas")) {
            double h2s = fluid.getPhase("gas").getComponent("H2S").getx();
            System.out.printf("  Gas phase:     y(H2S) = %.6f mol/mol%n", h2s);
        }
        if (fluid.hasPhaseType("oil")) {
            double h2s = fluid.getPhase("oil").getComponent("H2S").getx();
            System.out.printf("  Oil phase:     x(H2S) = %.6f mol/mol%n", h2s);
        }
        if (fluid.hasPhaseType("aqueous")) {
            double h2s = fluid.getPhase("aqueous").getComponent("H2S").getx();
            System.out.printf("  Water phase:   x(H2S) = %.6f mol/mol%n", h2s);
        }
        
        // K-values
        System.out.println();
        System.out.println("Partition coefficients:");
        if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
            double kGO = fluid.getPhase("gas").getComponent("H2S").getx() /
                         fluid.getPhase("oil").getComponent("H2S").getx();
            System.out.printf("  K(gas/oil):   %.3f%n", kGO);
        }
        if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("aqueous")) {
            double kGW = fluid.getPhase("gas").getComponent("H2S").getx() /
                         fluid.getPhase("aqueous").getComponent("H2S").getx();
            System.out.printf("  K(gas/water): %.3f%n", kGW);
        }
        System.out.println();
    }
    
    private static void printComparisonTable(SystemInterface srk, SystemInterface pr,
            SystemInterface cpa, SystemInterface elec) {
        System.out.println();
        System.out.println("Model       | y(H2S) Gas | x(H2S) Oil | x(H2S) Water | K(g/w)");
        System.out.println("------------|------------|------------|--------------|--------");
        printModelRow("SRK", srk);
        printModelRow("PR", pr);
        printModelRow("CPA", cpa);
        printModelRow("Electrolyte", elec);
        System.out.println();
        System.out.println("Note: K(g/w) = y(H2S)/x(H2S) - lower value means more H2S in water");
    }
    
    private static void printModelRow(String name, SystemInterface fluid) {
        double yGas = fluid.hasPhaseType("gas") ? 
            fluid.getPhase("gas").getComponent("H2S").getx() : 0;
        double xOil = fluid.hasPhaseType("oil") ? 
            fluid.getPhase("oil").getComponent("H2S").getx() : 0;
        double xWater = fluid.hasPhaseType("aqueous") ? 
            fluid.getPhase("aqueous").getComponent("H2S").getx() : 0;
        double kGW = xWater > 0 ? yGas / xWater : 0;
        
        System.out.printf("%-11s | %10.6f | %10.6f | %12.6f | %6.2f%n",
            name, yGas, xOil, xWater, kGW);
    }
}
```

## When to Use Each Model

### Use Simple Cubic EOS (SRK/PR) When:

- **Dry gas systems**: No or minimal water content
- **Initial screening**: Quick approximate calculations
- **Gas-oil only**: Water phase not critical
- **Low pH conditions**: pH < 5 where dissociation negligible
- **Dehydrated streams**: After glycol or molecular sieve treatment

**Typical applications**:
- Inlet separator sizing
- Gas transmission pipeline design
- Hydrocarbon dewpoint calculations
- Initial process simulation

### Use CPA EOS When:

- **Wet gas systems**: Significant water with hydrocarbons
- **MEG/TEG systems**: Glycol-water-hydrocarbon mixtures
- **Methanol injection**: Hydrate inhibitor calculations
- **Better water modeling needed**: But chemical reactions not critical

**Typical applications**:
- Hydrate formation predictions
- Glycol dehydration units
- Water content in gas phase
- Improved hydrocarbon solubility in water

### Use Electrolyte Models When:

- **Sour water systems**: High H2S with significant water
- **Produced water**: Saline water with dissolved gases
- **pH effects important**: Buffered or high pH systems
- **Speciation required**: Need molecular vs. ionic H2S
- **Corrosion prediction**: Molecular H2S controls corrosion rate
- **Amine treating**: Acid gas-amine-water equilibria
- **Sour water strippers**: Design and optimization

**Typical applications**:
- Sour water stripper design
- Produced water treatment
- Amine unit simulation
- Corrosion rate prediction
- Environmental discharge compliance
- Acid gas injection studies

## Decision Tree for Model Selection

```
                    ┌─────────────────────────┐
                    │ H2S Distribution Problem │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼───────────┐
                    │ Is water phase present │
                    │ and significant?       │
                    └───────────┬───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │ No              │ Yes             │
              ▼                 │                 │
    ┌─────────────────┐        │      ┌──────────▼──────────┐
    │ Use SRK or PR   │        │      │ Is pH > 6 or are   │
    │ (gas-oil only)  │        │      │ salts significant?  │
    └─────────────────┘        │      └──────────┬──────────┘
                               │                  │
                               │    ┌─────────────┼─────────────┐
                               │    │ No          │ Yes         │
                               │    ▼             │             ▼
                               │ ┌──────────────┐ │ ┌────────────────────┐
                               │ │ Is H-bonding │ │ │ Use Electrolyte    │
                               │ │ important?   │ │ │ Model with         │
                               │ └──────┬───────┘ │ │ chemical reactions │
                               │        │         │ └────────────────────┘
                               │  ┌─────┼─────┐   │
                               │  │ No  │ Yes │   │
                               │  ▼     │     ▼   │
                          ┌────────┐  ┌────────┐  │
                          │Use SRK │  │Use CPA │  │
                          │or PR   │  │        │  │
                          └────────┘  └────────┘  │
```

## Practical Recommendations

### For Upstream Production

1. **Wellhead to separator**: Use CPA for wet gas, SRK/PR for dry gas
2. **Produced water**: Use electrolyte model with salts
3. **Corrosion assessment**: Always need molecular H2S → use electrolyte model

### For Gas Processing

1. **Inlet facilities**: CPA sufficient if no amine treating
2. **Amine units**: Electrolyte model essential
3. **Dehydration**: CPA for glycol units
4. **Sulfur recovery**: Electrolyte for Claus tail gas

### For Pipeline Transport

1. **Dry gas pipelines**: SRK/PR adequate
2. **Wet gas/multiphase**: CPA recommended
3. **Corrosion hot spots**: Electrolyte model for water accumulation zones

## Python Example (Jupyter Notebook)

```python
from neqsim import jneqsim
import numpy as np
import matplotlib.pyplot as plt

# Java class imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

def create_fluid(SystemClass, T_K, P_bara, mixing_rule):
    """Create fluid with standard sour gas composition."""
    fluid = SystemClass(T_K, P_bara)
    fluid.addComponent("methane", 0.70)
    fluid.addComponent("ethane", 0.05)
    fluid.addComponent("propane", 0.03)
    fluid.addComponent("n-heptane", 0.05)
    fluid.addComponent("H2S", 0.05)
    fluid.addComponent("CO2", 0.02)
    fluid.addComponent("water", 0.10)
    fluid.setMixingRule(mixing_rule)
    fluid.setMultiPhaseCheck(True)
    return fluid

# Temperature range for study
temperatures = np.linspace(300, 400, 11)  # K
pressure = 50.0  # bara

# Storage for results
results = {
    'SRK': {'T': [], 'h2s_gas': [], 'h2s_water': [], 'K_gw': []},
    'PR': {'T': [], 'h2s_gas': [], 'h2s_water': [], 'K_gw': []},
    'CPA': {'T': [], 'h2s_gas': [], 'h2s_water': [], 'K_gw': []}
}

for T in temperatures:
    # SRK
    srk = create_fluid(SystemSrkEos, float(T), pressure, "classic")
    ThermodynamicOperations(srk).TPflash()
    
    # PR
    pr = create_fluid(SystemPrEos, float(T), pressure, "classic")
    ThermodynamicOperations(pr).TPflash()
    
    # CPA
    cpa = create_fluid(SystemSrkCPAstatoil, float(T), pressure, 10)
    ThermodynamicOperations(cpa).TPflash()
    
    for name, fluid in [('SRK', srk), ('PR', pr), ('CPA', cpa)]:
        h2s_g = fluid.getPhase("gas").getComponent("H2S").getx() if fluid.hasPhaseType("gas") else 0
        h2s_w = fluid.getPhase("aqueous").getComponent("H2S").getx() if fluid.hasPhaseType("aqueous") else 1e-10
        
        results[name]['T'].append(T - 273.15)
        results[name]['h2s_gas'].append(h2s_g)
        results[name]['h2s_water'].append(h2s_w)
        results[name]['K_gw'].append(h2s_g / h2s_w if h2s_w > 0 else 0)

# Plot results
fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Plot 1: H2S in water phase
ax1 = axes[0]
for name, style in [('SRK', 'o-'), ('PR', 's-'), ('CPA', '^-')]:
    ax1.plot(results[name]['T'], results[name]['h2s_water'], style, label=name, markersize=6)
ax1.set_xlabel('Temperature (°C)', fontsize=12)
ax1.set_ylabel('H2S mole fraction in water', fontsize=12)
ax1.set_title('H2S Solubility in Water Phase')
ax1.legend()
ax1.grid(True, alpha=0.3)
ax1.set_yscale('log')

# Plot 2: K-value (gas/water)
ax2 = axes[1]
for name, style in [('SRK', 'o-'), ('PR', 's-'), ('CPA', '^-')]:
    ax2.plot(results[name]['T'], results[name]['K_gw'], style, label=name, markersize=6)
ax2.set_xlabel('Temperature (°C)', fontsize=12)
ax2.set_ylabel('K(gas/water) = y/x', fontsize=12)
ax2.set_title('H2S Partition Coefficient (Gas/Water)')
ax2.legend()
ax2.grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig('h2s_distribution_comparison.png', dpi=150)
plt.show()

print("\nKey observations:")
print("- CPA predicts higher H2S solubility in water (lower K-value)")
print("- SRK and PR give similar results for gas-oil partitioning")
print("- Temperature increase generally increases K(g/w) - less H2S in water")
print("- For accurate water phase predictions, use CPA or electrolyte model")
```

## References

1. Carroll, J.J., "Acid Gas Injection and Carbon Dioxide Sequestration", Wiley, 2010
2. Experiment data sources for H2S-water: Selleck et al. (1952), Lee & Mather (1977)
3. CPA model development: Kontogeorgis & Folas, "Thermodynamic Models for Industrial Applications", Wiley, 2010
4. Electrolyte modeling: Chen et al., "Generalized Electrolyte-NRTL Model", AIChE J., 1982

---

*Document Version: 1.0*  
*Author: NeqSim Development Team*  
*Last Updated: February 2026*
