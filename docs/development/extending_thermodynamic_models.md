---
title: Extending NeqSim with New Thermodynamic Models
description: Complete guide to adding custom equations of state (EoS) and thermodynamic systems to NeqSim.
---

# Extending NeqSim with New Thermodynamic Models

This guide explains how to add new thermodynamic models (equations of state, activity coefficient models) to NeqSim. Thermodynamic models are the core of NeqSim's property calculations.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Creating a New Equation of State](#creating-a-new-equation-of-state)
3. [Creating Phase Classes](#creating-phase-classes)
4. [Creating Component Classes](#creating-component-classes)
5. [Implementing Mixing Rules](#implementing-mixing-rules)
6. [Complete Example: Modified SRK EoS](#complete-example-modified-srk-eos)
7. [Testing Thermodynamic Models](#testing-thermodynamic-models)
8. [Python Integration](#python-integration)
9. [Best Practices](#best-practices)

---

## Architecture Overview

### Class Hierarchy

NeqSim's thermodynamic framework follows a three-level hierarchy:

```
SystemInterface (Fluid)
    └── SystemThermo (base implementation)
            └── SystemEos (EoS base)
                    ├── SystemSrkEos (SRK)
                    ├── SystemPrEos (Peng-Robinson)
                    ├── SystemSrkCPAstatoil (CPA)
                    └── YourCustomSystem

PhaseInterface (Phase)
    └── Phase (base implementation)
            └── PhaseEos (EoS base)
                    ├── PhaseSrkEos (SRK)
                    ├── PhasePrEos (Peng-Robinson)
                    └── YourCustomPhase

ComponentInterface (Component)
    └── Component (base implementation)
            └── ComponentEos (EoS base)
                    ├── ComponentSrk (SRK)
                    ├── ComponentPR (Peng-Robinson)
                    └── YourCustomComponent
```

### Key Relationships

| Class Level | Responsibility |
|-------------|----------------|
| System | Holds phases, manages flash calculations, provides user API |
| Phase | Contains components, calculates bulk properties (fugacity, enthalpy) |
| Component | Pure component properties, mixing rule parameters |

### Method Flow for Property Calculation

```
1. System.init(initType) 
   └── Phase.init(initType)
           └── Component.init()       // Pure component a, b parameters
           └── Phase.calcA()          // Mixture attractive parameter
           └── Phase.calcB()          // Mixture co-volume parameter
           └── Phase.molarVolume()    // Solve EoS for volume
           └── Component.Finit()      // Fugacity coefficients
```

---

## Creating a New Equation of State

### Step 1: Create the System Class

The System class is the main entry point for users:

```java
package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseCustomEos;  // Your custom phase

/**
 * Thermodynamic system using a custom equation of state.
 * 
 * <p>This EoS is based on [describe basis and modifications].</p>
 * 
 * <p>Reference: Author et al., Journal, Year</p>
 *
 * @author YourName
 * @version 1.0
 */
public class SystemCustomEos extends SystemEos {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    /**
     * Constructor with default conditions (298.15 K, 1 bar).
     */
    public SystemCustomEos() {
        this(298.15, 1.0, false);
    }
    
    /**
     * Constructor for SystemCustomEos.
     *
     * @param T Temperature in Kelvin
     * @param P Pressure in bara
     */
    public SystemCustomEos(double T, double P) {
        this(T, P, false);
    }
    
    /**
     * Constructor for SystemCustomEos with solid phase option.
     *
     * @param T Temperature in Kelvin
     * @param P Pressure in bara
     * @param checkForSolids Set true to enable solid phase calculations
     */
    public SystemCustomEos(double T, double P, boolean checkForSolids) {
        super(T, P, checkForSolids);
        
        // Set model identification
        modelName = "Custom-EOS";
        
        // Set characterization model for plus fractions
        getCharacterization().setTBPModel("PedersenSRK"); // Or create custom
        
        // Set attractive term type (affects alpha function)
        attractiveTermNumber = 0; // 0=Soave, 1=PR, etc.
        
        // Initialize phases with your custom phase class
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseCustomEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        
        // Handle solid phase if requested
        if (solidPhaseCheck) {
            setNumberOfPhases(5);
            phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
        
        // Handle hydrate phase if requested
        if (hydrateCheck) {
            phaseArray[numberOfPhases - 1] = new PhaseHydrate();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public SystemCustomEos clone() {
        SystemCustomEos clonedSystem = null;
        try {
            clonedSystem = (SystemCustomEos) super.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return clonedSystem;
    }
}
```

---

## Creating Phase Classes

### Step 2: Create the Phase Class

The Phase class implements the EoS calculations:

```java
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCustomEos;

/**
 * Phase class implementing a custom equation of state.
 *
 * @author YourName
 */
public class PhaseCustomEos extends PhaseEos {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    // EoS-specific constants (example: SRK-like)
    // For SRK: delta1 = 1, delta2 = 0, u = 1, w = 0
    // For PR:  delta1 = 1+sqrt(2), delta2 = 1-sqrt(2)
    
    /**
     * Constructor for PhaseCustomEos.
     */
    public PhaseCustomEos() {
        super();
        // Set EoS parameters
        delta1 = 1.0;  // Adjust for your EoS
        delta2 = 0.0;
        uEOS = 1.0;    // u parameter in generalized cubic EoS
        wEOS = 0.0;    // w parameter
    }
    
    /** {@inheritDoc} */
    @Override
    public PhaseCustomEos clone() {
        PhaseCustomEos clonedPhase = null;
        try {
            clonedPhase = (PhaseCustomEos) super.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return clonedPhase;
    }
    
    /** {@inheritDoc} */
    @Override
    public void addComponent(String name, double moles, double molesInPhase,
            int compNumber) {
        // Use your custom component class
        componentArray[compNumber] = new ComponentCustomEos(name, moles, 
            molesInPhase, compNumber);
    }
    
    /**
     * Calculate mixture attractive parameter A.
     * 
     * <p>Uses mixing rule: A = sum_i sum_j x_i x_j sqrt(a_i * a_j) * (1 - k_ij)</p>
     *
     * @param phase the phase
     * @param temperature in Kelvin
     * @param pressure in bara
     * @param numberOfComponents number of components
     * @return mixture A parameter
     */
    @Override
    public double calcA(PhaseInterface phase, double temperature, 
            double pressure, int numberOfComponents) {
        double aij = 0.0;
        
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                double ai = phase.getComponent(i).getaT();
                double aj = phase.getComponent(j).getaT();
                double kij = getMixingRule().getKij(i, j);
                
                aij += phase.getComponent(i).getx() 
                     * phase.getComponent(j).getx() 
                     * Math.sqrt(ai * aj) * (1.0 - kij);
            }
        }
        
        return aij;
    }
    
    /**
     * Calculate mixture co-volume parameter B.
     * 
     * <p>Linear mixing rule: B = sum_i x_i * b_i</p>
     *
     * @param phase the phase
     * @param temperature in Kelvin
     * @param pressure in bara
     * @param numberOfComponents number of components
     * @return mixture B parameter
     */
    @Override
    public double calcB(PhaseInterface phase, double temperature, 
            double pressure, int numberOfComponents) {
        double b = 0.0;
        
        for (int i = 0; i < numberOfComponents; i++) {
            b += phase.getComponent(i).getx() * phase.getComponent(i).getb();
        }
        
        return b;
    }
    
    /**
     * Solve the cubic EoS for molar volume.
     * 
     * <p>General cubic form:
     * P = RT/(V-b) - a(T)/((V + delta1*b)(V + delta2*b))</p>
     *
     * @param pressure in bara
     * @param temperature in Kelvin
     * @param A mixture attractive parameter
     * @param B mixture co-volume parameter
     * @param pt phase type hint
     * @return molar volume in m³/mol
     */
    @Override
    public double molarVolume(double pressure, double temperature, 
            double A, double B, PhaseType pt) throws Exception {
        
        // Convert to dimensionless form
        double Ared = A * pressure / (R * R * temperature * temperature);
        double Bred = B * pressure / (R * temperature);
        
        // Cubic coefficients for Z³ + c2*Z² + c1*Z + c0 = 0
        double c2 = -(1.0 + Bred - uEOS * Bred);
        double c1 = Ared + wEOS * Bred * Bred - uEOS * Bred - uEOS * Bred * Bred;
        double c0 = -(Ared * Bred + wEOS * Bred * Bred + wEOS * Bred * Bred * Bred);
        
        // Solve cubic equation
        double[] roots = solveCubic(c2, c1, c0);
        
        // Select appropriate root based on phase type
        double Z = selectRoot(roots, pt);
        
        if (Z < 0 || Double.isNaN(Z)) {
            throw new Exception("Invalid compressibility factor: " + Z);
        }
        
        return Z * R * temperature / pressure;
    }
    
    /**
     * Solve cubic equation x³ + c2*x² + c1*x + c0 = 0.
     *
     * @param c2 coefficient
     * @param c1 coefficient
     * @param c0 coefficient
     * @return array of real roots
     */
    private double[] solveCubic(double c2, double c1, double c0) {
        // Implement Cardano's formula or Newton-Raphson
        // For simplicity, use existing NeqSim solver
        double[] roots = new double[3];
        
        // Use analytical solution (Cardano)
        double p = c1 - c2 * c2 / 3.0;
        double q = 2.0 * c2 * c2 * c2 / 27.0 - c2 * c1 / 3.0 + c0;
        double D = q * q / 4.0 + p * p * p / 27.0;
        
        if (D > 0) {
            // One real root
            double u = Math.cbrt(-q / 2.0 + Math.sqrt(D));
            double v = Math.cbrt(-q / 2.0 - Math.sqrt(D));
            roots[0] = u + v - c2 / 3.0;
            roots[1] = Double.NaN;
            roots[2] = Double.NaN;
        } else {
            // Three real roots
            double theta = Math.acos(-q / 2.0 * Math.sqrt(-27.0 / (p * p * p)));
            double r = 2.0 * Math.sqrt(-p / 3.0);
            roots[0] = r * Math.cos(theta / 3.0) - c2 / 3.0;
            roots[1] = r * Math.cos((theta + 2.0 * Math.PI) / 3.0) - c2 / 3.0;
            roots[2] = r * Math.cos((theta + 4.0 * Math.PI) / 3.0) - c2 / 3.0;
        }
        
        return roots;
    }
    
    /**
     * Select appropriate root based on phase type.
     *
     * @param roots array of Z roots
     * @param pt phase type
     * @return selected Z value
     */
    private double selectRoot(double[] roots, PhaseType pt) {
        double minZ = Double.MAX_VALUE;
        double maxZ = Double.MIN_VALUE;
        
        for (double root : roots) {
            if (!Double.isNaN(root) && root > 0) {
                minZ = Math.min(minZ, root);
                maxZ = Math.max(maxZ, root);
            }
        }
        
        // Gas phase: largest root; Liquid phase: smallest root
        if (pt == PhaseType.GAS) {
            return maxZ;
        } else {
            return minZ;
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public double getF() {
        // Helmholtz free energy departure function
        return super.getF();
    }
    
    /** {@inheritDoc} */
    @Override
    public double dFdV() {
        // dF/dV derivative (used for pressure calculation)
        return super.dFdV();
    }
    
    /** {@inheritDoc} */
    @Override
    public double dFdVdV() {
        // d²F/dV² derivative
        return super.dFdVdV();
    }
}
```

---

## Creating Component Classes

### Step 3: Create the Component Class

The Component class defines pure component parameters:

```java
package neqsim.thermo.component;

/**
 * Component class for custom equation of state.
 *
 * @author YourName
 */
public class ComponentCustomEos extends ComponentEos {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    // Custom parameters
    private double customParam1 = 0.0;
    private double customParam2 = 0.0;
    
    /**
     * Constructor for ComponentCustomEos.
     *
     * @param name Component name
     * @param moles Total moles
     * @param molesInPhase Moles in this phase
     * @param compIndex Component index
     */
    public ComponentCustomEos(String name, double moles, double molesInPhase, 
            int compIndex) {
        super(name, moles, molesInPhase, compIndex);
    }
    
    /**
     * Constructor for ComponentCustomEos from ComponentInterface.
     *
     * @param number Component index
     * @param TC Critical temperature (K)
     * @param PC Critical pressure (bar)
     * @param M Molar mass (kg/mol)
     * @param a Attractive parameter
     * @param moles Total moles
     */
    public ComponentCustomEos(int number, double TC, double PC, double M, 
            double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }
    
    /** {@inheritDoc} */
    @Override
    public ComponentCustomEos clone() {
        ComponentCustomEos clonedComponent = null;
        try {
            clonedComponent = (ComponentCustomEos) super.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return clonedComponent;
    }
    
    /**
     * Calculate temperature-dependent attractive parameter a(T).
     * 
     * <p>Uses alpha function: a(T) = a_c * alpha(T_r, omega)</p>
     *
     * @param temperature in Kelvin
     */
    @Override
    public void calcAT(double temperature) {
        double Tr = temperature / TC;
        
        // Critical attractive parameter (Soave form)
        double ac = 0.42748 * R * R * TC * TC / PC;
        
        // Alpha function (Soave-type)
        double m = 0.48 + 1.574 * acentricFactor 
                 - 0.176 * acentricFactor * acentricFactor;
        double alpha = Math.pow(1.0 + m * (1.0 - Math.sqrt(Tr)), 2);
        
        // Temperature-dependent a
        aT = ac * alpha;
    }
    
    /**
     * Calculate co-volume parameter b.
     * 
     * <p>For cubic EoS: b = Omega_b * R * Tc / Pc</p>
     */
    @Override
    public void calcB() {
        // Soave form (Omega_b = 0.08664)
        b = 0.08664 * R * TC / PC;
    }
    
    /**
     * Calculate fugacity coefficient.
     *
     * @param phase the phase
     * @param numberOfComponents number of components
     * @param temperature in Kelvin
     * @param pressure in bara
     */
    @Override
    public void Finit(PhaseInterface phase, double temperature, double pressure,
            double totalNumberOfMoles, double beta, int numberOfComponents, 
            int initType) {
        
        super.Finit(phase, temperature, pressure, totalNumberOfMoles, 
                    beta, numberOfComponents, initType);
        
        if (initType > 1) {
            // Calculate fugacity coefficient
            double A = phase.getA() / (numberOfMolesInPhase * numberOfMolesInPhase);
            double B = phase.getB() / numberOfMolesInPhase;
            double Z = phase.getZ();
            
            // Partial molar volume contribution
            double bRatio = b / B;
            
            // Activity coefficient derivative
            double sumAij = 0.0;
            for (int j = 0; j < numberOfComponents; j++) {
                double aij = Math.sqrt(aT * phase.getComponent(j).getaT()) 
                           * (1.0 - phase.getMixingRule().getKij(componentNumber, j));
                sumAij += phase.getComponent(j).getx() * aij;
            }
            double dAdn = 2.0 * sumAij / (numberOfMolesInPhase);
            
            // Fugacity coefficient (for SRK-type)
            double lnPhi = bRatio * (Z - 1.0) - Math.log(Z - B * pressure / (R * temperature))
                         - A / B * (dAdn / A - bRatio) * Math.log(1.0 + B / Z);
            
            fugacityCoefficient = Math.exp(lnPhi);
            logFugacityCoefficient = lnPhi;
        }
    }
    
    // Custom parameter accessors
    public double getCustomParam1() { return customParam1; }
    public void setCustomParam1(double value) { this.customParam1 = value; }
    public double getCustomParam2() { return customParam2; }
    public void setCustomParam2(double value) { this.customParam2 = value; }
}
```

---

## Implementing Mixing Rules

### Step 4: Add Custom Mixing Rules (Optional)

For advanced mixing rules:

```java
package neqsim.thermo.mixingrule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * Custom mixing rule implementation.
 *
 * @author YourName
 */
public class CustomMixingRule implements EosMixingRulesInterface {
    
    private double[][] kij;  // Binary interaction parameters
    private double[][] lij;  // Co-volume interaction parameters (optional)
    
    /**
     * Constructor for CustomMixingRule.
     *
     * @param phase the phase to apply mixing rule
     */
    public CustomMixingRule(PhaseInterface phase) {
        int n = phase.getNumberOfComponents();
        kij = new double[n][n];
        lij = new double[n][n];
        
        // Initialize default values (0 = no interaction)
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                kij[i][j] = 0.0;
                lij[i][j] = 0.0;
            }
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public double getKij(int i, int j) {
        return kij[i][j];
    }
    
    /** {@inheritDoc} */
    @Override
    public void setKij(int i, int j, double value) {
        kij[i][j] = value;
        kij[j][i] = value;  // Symmetric
    }
    
    /**
     * Get co-volume interaction parameter.
     *
     * @param i first component index
     * @param j second component index
     * @return lij value
     */
    public double getLij(int i, int j) {
        return lij[i][j];
    }
    
    /**
     * Set co-volume interaction parameter.
     *
     * @param i first component index
     * @param j second component index
     * @param value lij value
     */
    public void setLij(int i, int j, double value) {
        lij[i][j] = value;
        lij[j][i] = value;
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, 
            double pressure, int numberOfComponents) {
        double A = 0.0;
        
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                double ai = phase.getComponent(i).getaT();
                double aj = phase.getComponent(j).getaT();
                double xi = phase.getComponent(i).getx();
                double xj = phase.getComponent(j).getx();
                
                // Custom combining rule
                A += xi * xj * Math.sqrt(ai * aj) * (1.0 - kij[i][j]);
            }
        }
        
        return A;
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcB(PhaseInterface phase, double temperature, 
            double pressure, int numberOfComponents) {
        double B = 0.0;
        
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                double bi = phase.getComponent(i).getb();
                double bj = phase.getComponent(j).getb();
                double xi = phase.getComponent(i).getx();
                double xj = phase.getComponent(j).getx();
                
                // Custom co-volume mixing (with lij)
                B += xi * xj * (bi + bj) / 2.0 * (1.0 - lij[i][j]);
            }
        }
        
        return B;
    }
    
    /** {@inheritDoc} */
    @Override
    public CustomMixingRule clone() {
        try {
            CustomMixingRule clone = (CustomMixingRule) super.clone();
            clone.kij = new double[kij.length][kij.length];
            clone.lij = new double[lij.length][lij.length];
            for (int i = 0; i < kij.length; i++) {
                clone.kij[i] = this.kij[i].clone();
                clone.lij[i] = this.lij[i].clone();
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

---

## Complete Example: Modified SRK EoS

Here's a complete working example of a modified SRK equation of state with temperature-dependent binary interaction parameters:

### System Class

```java
package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseModifiedSrkEos;

/**
 * Modified SRK EoS with temperature-dependent kij.
 * 
 * <p>kij(T) = kij0 + kij1 * (T - 298.15)</p>
 *
 * @author YourName
 */
public class SystemModifiedSrkEos extends SystemEos {
    private static final long serialVersionUID = 1000;
    
    public SystemModifiedSrkEos() {
        this(298.15, 1.0, false);
    }
    
    public SystemModifiedSrkEos(double T, double P) {
        this(T, P, false);
    }
    
    public SystemModifiedSrkEos(double T, double P, boolean checkForSolids) {
        super(T, P, checkForSolids);
        modelName = "Modified-SRK-EOS";
        getCharacterization().setTBPModel("PedersenSRK");
        attractiveTermNumber = 0;
        
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseModifiedSrkEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }
    
    @Override
    public SystemModifiedSrkEos clone() {
        SystemModifiedSrkEos cloned = null;
        try {
            cloned = (SystemModifiedSrkEos) super.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return cloned;
    }
}
```

### Usage

```java
// Java usage
SystemInterface fluid = new SystemModifiedSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("CO2", 0.2);
fluid.setMixingRule("classic");

// Set temperature-dependent kij (if implemented)
// fluid.setInteractionParameter(0, 1, 0.1, 0.0005); // kij0, kij1

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Z = " + fluid.getPhase("gas").getZ());
System.out.println("Density = " + fluid.getPhase("gas").getDensity("kg/m3"));
```

---

## Testing Thermodynamic Models

### Unit Test Example

```java
package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemModifiedSrkEosTest {
    
    @Test
    void testPureMethane() {
        SystemInterface fluid = new SystemModifiedSrkEos(300.0, 50.0);
        fluid.addComponent("methane", 1.0);
        fluid.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Expected Z for methane at 300K, 50 bar (from NIST)
        double expectedZ = 0.88; // Approximate
        double calculatedZ = fluid.getPhase("gas").getZ();
        
        assertEquals(expectedZ, calculatedZ, 0.05, 
            "Compressibility should match reference");
    }
    
    @Test
    void testBinaryMixture() {
        SystemInterface fluid = new SystemModifiedSrkEos(300.0, 50.0);
        fluid.addComponent("methane", 0.9);
        fluid.addComponent("ethane", 0.1);
        fluid.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        double density = fluid.getPhase("gas").getDensity("kg/m3");
        
        // Density should be reasonable (30-50 kg/m3 at these conditions)
        assertTrue(density > 20 && density < 60, 
            "Density should be in expected range: " + density);
    }
    
    @Test
    void testPhaseEquilibrium() {
        SystemInterface fluid = new SystemModifiedSrkEos(250.0, 30.0);
        fluid.addComponent("methane", 0.7);
        fluid.addComponent("n-hexane", 0.3);
        fluid.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Should have two phases at these conditions
        assertEquals(2, fluid.getNumberOfPhases(), 
            "Should have two phases");
        
        // Check K-values are reasonable
        double KmethaneGas = fluid.getPhase("gas").getComponent("methane").getx();
        double KmethaneLiq = fluid.getPhase("oil").getComponent("methane").getx();
        assertTrue(KmethaneGas > KmethaneLiq, 
            "Methane should concentrate in gas phase");
    }
    
    @Test
    void testVaporPressure() {
        // Test against known vapor pressure data
        double[] Texp = {190.0, 200.0, 210.0};  // K
        double[] Pexp = {5.0, 10.0, 18.0};      // bar (approximate)
        
        for (int i = 0; i < Texp.length; i++) {
            SystemInterface fluid = new SystemModifiedSrkEos(Texp[i], 1.0);
            fluid.addComponent("methane", 1.0);
            fluid.setMixingRule("classic");
            
            ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
            try {
                ops.bubblePointPressureFlash(false);
                double Pcalc = fluid.getPressure();
                
                assertEquals(Pexp[i], Pcalc, Pexp[i] * 0.15, 
                    "Vapor pressure at " + Texp[i] + "K");
            } catch (Exception e) {
                fail("Bubble point calculation failed: " + e.getMessage());
            }
        }
    }
}
```

---

## Python Integration

### Using Your Model from Python

```python
from neqsim import jneqsim

# Import your custom system class
SystemModifiedSrkEos = jneqsim.thermo.system.SystemModifiedSrkEos

# Create fluid with custom EoS
fluid = SystemModifiedSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.8)
fluid.addComponent("CO2", 0.2)
fluid.setMixingRule("classic")

# Run flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# Get results
print(f"Model: {fluid.getModelName()}")
print(f"Z = {fluid.getPhase('gas').getZ():.4f}")
print(f"Density = {fluid.getPhase('gas').getDensity('kg/m3'):.2f} kg/m³")

# Compare with standard SRK
fluid_srk = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
fluid_srk.addComponent("methane", 0.8)
fluid_srk.addComponent("CO2", 0.2)
fluid_srk.setMixingRule("classic")

ops_srk = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid_srk)
ops_srk.TPflash()

print(f"\nComparison with standard SRK:")
print(f"Modified Z = {fluid.getPhase('gas').getZ():.4f}")
print(f"Standard Z = {fluid_srk.getPhase('gas').getZ():.4f}")
```

### Generating Phase Envelopes

```python
import matplotlib.pyplot as plt
import numpy as np

def generate_phase_envelope(fluid_class, composition, name):
    """Generate phase envelope for a given EoS."""
    fluid = fluid_class(250.0, 1.0)
    for comp, frac in composition.items():
        fluid.addComponent(comp, frac)
    fluid.setMixingRule("classic")
    
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    
    try:
        ops.calcPTphaseEnvelope()
        
        # Get envelope data
        T_dew = list(fluid.getPhaseEnvelope().getDewPointLine().get("temperature"))
        P_dew = list(fluid.getPhaseEnvelope().getDewPointLine().get("pressure"))
        T_bub = list(fluid.getPhaseEnvelope().getBubblePointLine().get("temperature"))
        P_bub = list(fluid.getPhaseEnvelope().getBubblePointLine().get("pressure"))
        
        return {
            'T_dew': T_dew, 'P_dew': P_dew,
            'T_bub': T_bub, 'P_bub': P_bub,
            'name': name
        }
    except Exception as e:
        print(f"Phase envelope failed for {name}: {e}")
        return None

# Compare EoS models
composition = {'methane': 0.8, 'ethane': 0.1, 'propane': 0.1}

models = [
    (jneqsim.thermo.system.SystemSrkEos, "SRK"),
    (jneqsim.thermo.system.SystemPrEos, "PR"),
    (jneqsim.thermo.system.SystemModifiedSrkEos, "Modified SRK"),
]

plt.figure(figsize=(10, 8))
for model_class, name in models:
    envelope = generate_phase_envelope(model_class, composition, name)
    if envelope:
        plt.plot(envelope['T_dew'], envelope['P_dew'], label=f"{name} Dew")
        plt.plot(envelope['T_bub'], envelope['P_bub'], '--', label=f"{name} Bubble")

plt.xlabel('Temperature (K)')
plt.ylabel('Pressure (bar)')
plt.title('Phase Envelope Comparison')
plt.legend()
plt.grid(True)
plt.show()
```

---

## Best Practices

### 1. Validate Against Reference Data

```java
// Always test against NIST, DIPPR, or experimental data
@Test
void validateAgainstNIST() {
    // NIST Reference: Methane at 300K, 100 bar
    double Z_NIST = 0.8577;
    double rho_NIST = 66.23; // kg/m³
    
    SystemInterface fluid = new SystemCustomEos(300.0, 100.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    
    assertEquals(Z_NIST, fluid.getPhase(0).getZ(), 0.01);
    assertEquals(rho_NIST, fluid.getPhase(0).getDensity("kg/m3"), 1.0);
}
```

### 2. Handle Numerical Stability

```java
@Override
public double molarVolume(...) throws Exception {
    // Add iteration limits and convergence checks
    int maxIterations = 100;
    double tolerance = 1e-10;
    
    for (int iter = 0; iter < maxIterations; iter++) {
        // Newton-Raphson or other solver
        if (Math.abs(residual) < tolerance) {
            return volume;
        }
    }
    
    throw new TooManyIterationsException("Volume solver did not converge");
}
```

### 3. Document Physical Basis

```java
/**
 * Calculate alpha function for temperature-dependent attractive parameter.
 * 
 * <p>Uses the Mathias-Copeman alpha function for improved accuracy:</p>
 * <p>alpha = [1 + c1*(1-sqrt(Tr)) + c2*(1-sqrt(Tr))² + c3*(1-sqrt(Tr))³]²</p>
 * 
 * <p>Reference: Mathias, P.M., Copeman, T.W., 
 * "Extension of the Peng-Robinson EOS to Complex Mixtures",
 * Fluid Phase Equilibria, 1983.</p>
 */
```

### 4. Support Serialization

```java
// Ensure all fields are serializable
private static final long serialVersionUID = 1000;

// Mark transient fields that shouldn't be serialized
private transient SomeNonSerializableHelper helper;

// Implement proper clone()
@Override
public MySystem clone() {
    MySystem clone = (MySystem) super.clone();
    // Deep copy mutable fields
    clone.customArray = this.customArray.clone();
    return clone;
}
```

### 5. Provide Clear Error Messages

```java
if (temperature <= 0) {
    throw new InvalidInputException(this, "init", "temperature", 
        "must be positive, got " + temperature);
}

if (!hasComponent("water") && modelRequiresWater()) {
    logger.warn("Model {} works best with water present", modelName);
}
```

---

## See Also

- [Mathematical Models Overview](../thermo/mathematical_models)
- [EoS System Types](../thermo/system/README)
- [Mixing Rules Guide](../thermo/mixing_rules_guide)
- [Extending Physical Properties](extending_physical_properties)
- [Python Extension Patterns](python_extension_patterns)

---

*Document last updated: February 2026*
