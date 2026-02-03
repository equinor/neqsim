---
title: Extending NeqSim with New Physical Property Models
description: Complete guide to adding custom viscosity, thermal conductivity, diffusivity, and density models to NeqSim.
---

# Extending NeqSim with New Physical Property Models

This guide explains how to add new physical property models (viscosity, thermal conductivity, diffusivity, density) to NeqSim. Physical properties are essential for transport phenomena calculations in process simulation.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Creating a Viscosity Model](#creating-a-viscosity-model)
3. [Creating a Thermal Conductivity Model](#creating-a-thermal-conductivity-model)
4. [Creating a Diffusivity Model](#creating-a-diffusivity-model)
5. [Creating a Density Model](#creating-a-density-model)
6. [Registering Your Model](#registering-your-model)
7. [Complete Example: Custom Viscosity Model](#complete-example-custom-viscosity-model)
8. [Testing Physical Property Models](#testing-physical-property-models)
9. [Python Integration](#python-integration)
10. [Best Practices](#best-practices)

---

## Architecture Overview

### Package Structure

Physical property models are organized by phase and property type:

```
physicalproperties/
├── system/
│   ├── PhysicalProperties.java          # Base class, model selection
│   ├── GasPhysicalProperties.java       # Gas phase handler
│   ├── LiquidPhysicalProperties.java    # Liquid phase handler
│   └── SolidPhysicalProperties.java     # Solid phase handler
├── methods/
│   ├── methodinterface/                 # Interfaces
│   │   ├── ViscosityInterface.java
│   │   ├── ConductivityInterface.java
│   │   ├── DiffusivityInterface.java
│   │   └── DensityInterface.java
│   ├── commonphasephysicalproperties/   # Models for all phases
│   │   ├── viscosity/
│   │   │   ├── Viscosity.java           # Abstract base
│   │   │   ├── LBCViscosityMethod.java
│   │   │   ├── FrictionTheoryViscosityMethod.java
│   │   │   └── PFCTViscosityMethodMod86.java
│   │   ├── conductivity/
│   │   └── diffusivity/
│   ├── gasphysicalproperties/           # Gas-specific models
│   └── liquidphysicalproperties/        # Liquid-specific models
└── mixingrule/
    └── PhysicalPropertyMixingRuleInterface.java
```

### Key Interfaces

| Interface | Purpose | Key Method |
|-----------|---------|------------|
| `ViscosityInterface` | Viscosity calculations | `calcViscosity()` |
| `ConductivityInterface` | Thermal conductivity | `calcConductivity()` |
| `DiffusivityInterface` | Diffusion coefficients | `calcDiffusionCoefficients()` |
| `DensityInterface` | Liquid density corrections | `calcDensity()` |

### Model Selection Flow

```
SystemInterface.initPhysicalProperties()
    └── PhaseInterface.initPhysicalProperties()
            └── PhysicalProperties (gas/liquid/solid)
                    ├── setViscosityModel("model_name")
                    ├── setConductivityModel("model_name")
                    └── setDiffusionCoefficientModel("model_name")
```

---

## Creating a Viscosity Model

### Step 1: Extend the Base Class

Create your model in the appropriate package:

```java
package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Custom viscosity model based on the ABC correlation.
 * 
 * <p>This model is suitable for [describe application].</p>
 * 
 * <p>Reference: Author et al., Journal, Year</p>
 *
 * @author YourName
 * @version 1.0
 */
public class ABCViscosityMethod extends Viscosity {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    /** Model parameters */
    private double parameterA = 1.0;
    private double parameterB = 0.5;
    private double parameterC = 0.1;
    
    /**
     * Constructor for ABCViscosityMethod.
     *
     * @param phase the physical properties phase object
     */
    public ABCViscosityMethod(PhysicalProperties phase) {
        super(phase);
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcViscosity() {
        // Implement your viscosity correlation here
        // phase.getPhase() gives access to thermodynamic phase
        
        double temperature = phase.getPhase().getTemperature(); // K
        double pressure = phase.getPhase().getPressure();       // bar
        double density = phase.getPhase().getDensity("kg/m3");  // kg/m³
        
        // Example correlation (replace with actual model)
        double reducedT = temperature / getMixtureCriticalTemperature();
        double viscosity = parameterA * Math.exp(parameterB / reducedT) 
                         + parameterC * density / 1000.0;
        
        return viscosity; // Return in Pa·s (kg/(m·s))
    }
    
    /** {@inheritDoc} */
    @Override
    public double getPureComponentViscosity(int i) {
        // Calculate viscosity contribution from component i
        double tc = phase.getPhase().getComponent(i).getTC();
        double tr = phase.getPhase().getTemperature() / tc;
        
        // Your pure component correlation
        return parameterA * Math.pow(tr, parameterB);
    }
    
    /**
     * Calculate mixture pseudo-critical temperature.
     *
     * @return pseudo-critical temperature in K
     */
    private double getMixtureCriticalTemperature() {
        double tcMix = 0.0;
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            tcMix += phase.getPhase().getComponent(i).getx() 
                   * phase.getPhase().getComponent(i).getTC();
        }
        return tcMix;
    }
    
    // Parameter setters for tuning
    public void setParameterA(double a) { this.parameterA = a; }
    public void setParameterB(double b) { this.parameterB = b; }
    public void setParameterC(double c) { this.parameterC = c; }
    
    /** {@inheritDoc} */
    @Override
    public ABCViscosityMethod clone() {
        ABCViscosityMethod clone = null;
        try {
            clone = (ABCViscosityMethod) super.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return clone;
    }
}
```

### Step 2: Access Phase Properties

The `phase` object provides access to thermodynamic data:

```java
// Temperature and Pressure
double T = phase.getPhase().getTemperature();     // Kelvin
double P = phase.getPhase().getPressure();        // bar

// Density
double rho = phase.getPhase().getDensity("kg/m3");
double molarDensity = phase.getPhase().getMolarDensity(); // mol/m³

// Component properties
int nComp = phase.getPhase().getNumberOfComponents();
for (int i = 0; i < nComp; i++) {
    ComponentInterface comp = phase.getPhase().getComponent(i);
    
    double x = comp.getx();           // Mole fraction
    double MW = comp.getMolarMass();  // kg/mol
    double Tc = comp.getTC();         // Critical temperature (K)
    double Pc = comp.getPC();         // Critical pressure (bar)
    double omega = comp.getAcentricFactor();
}

// Phase type
PhaseType type = phase.getPhase().getType();
boolean isGas = type == PhaseType.GAS;
boolean isLiquid = type == PhaseType.LIQUID || type == PhaseType.OIL;
```

---

## Creating a Thermal Conductivity Model

### Step 1: Extend the Base Class

```java
package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Custom thermal conductivity model.
 *
 * @author YourName
 */
public class CustomConductivityMethod extends Conductivity 
    implements ConductivityInterface {
    
    private static final long serialVersionUID = 1000;
    
    public CustomConductivityMethod(PhysicalProperties phase) {
        super(phase);
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcConductivity() {
        double temperature = phase.getPhase().getTemperature();
        double density = phase.getPhase().getDensity("kg/m3");
        
        // Calculate dilute gas contribution
        double lambda0 = calcDiluteGasConductivity();
        
        // Calculate residual (dense fluid) contribution
        double lambdaResidual = calcResidualConductivity(density);
        
        // Total conductivity in W/(m·K)
        return lambda0 + lambdaResidual;
    }
    
    private double calcDiluteGasConductivity() {
        double lambdaMix = 0.0;
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            double xi = phase.getPhase().getComponent(i).getx();
            double lambdai = getPureComponentConductivity(i);
            lambdaMix += xi * lambdai;
        }
        return lambdaMix;
    }
    
    private double calcResidualConductivity(double density) {
        // Implement residual contribution
        return 0.0;
    }
    
    private double getPureComponentConductivity(int i) {
        // Pure component conductivity correlation
        ComponentInterface comp = phase.getPhase().getComponent(i);
        double Tr = phase.getPhase().getTemperature() / comp.getTC();
        
        // Example correlation
        return 0.025 * Math.sqrt(Tr);
    }
    
    /** {@inheritDoc} */
    @Override
    public CustomConductivityMethod clone() {
        CustomConductivityMethod clone = null;
        try {
            clone = (CustomConductivityMethod) super.clone();
        } catch (Exception ex) {
            // Handle exception
        }
        return clone;
    }
}
```

---

## Creating a Diffusivity Model

### Binary Diffusion Coefficients

```java
package neqsim.physicalproperties.methods.commonphasephysicalproperties.diffusivity;

import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Custom diffusivity model for binary diffusion coefficients.
 *
 * @author YourName
 */
public class CustomDiffusivityMethod extends Diffusivity 
    implements DiffusivityInterface {
    
    private static final long serialVersionUID = 1000;
    
    public CustomDiffusivityMethod(PhysicalProperties phase) {
        super(phase);
    }
    
    /** {@inheritDoc} */
    @Override
    public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        
        int nComp = phase.getPhase().getNumberOfComponents();
        double[][] Dij = new double[nComp][nComp];
        
        // Calculate binary diffusion coefficients
        for (int i = 0; i < nComp; i++) {
            for (int j = i + 1; j < nComp; j++) {
                Dij[i][j] = calcBinaryDiffusionCoefficient(i, j);
                Dij[j][i] = Dij[i][j]; // Symmetric
            }
        }
        
        return Dij;
    }
    
    /**
     * Calculate binary diffusion coefficient between components i and j.
     *
     * @param i first component index
     * @param j second component index
     * @return binary diffusion coefficient in m²/s
     */
    private double calcBinaryDiffusionCoefficient(int i, int j) {
        double T = phase.getPhase().getTemperature();
        double P = phase.getPhase().getPressure();
        
        ComponentInterface compi = phase.getPhase().getComponent(i);
        ComponentInterface compj = phase.getPhase().getComponent(j);
        
        // Example: Chapman-Enskog type correlation
        double MWi = compi.getMolarMass() * 1000; // g/mol
        double MWj = compj.getMolarMass() * 1000;
        double MWij = 2.0 / (1.0/MWi + 1.0/MWj);
        
        double sigmai = compi.getLennardJonesMolecularDiameter();
        double sigmaj = compj.getLennardJonesMolecularDiameter();
        double sigmaij = (sigmai + sigmaj) / 2.0;
        
        // Simplified correlation (replace with actual model)
        double Dij = 1.858e-7 * Math.pow(T, 1.5) 
                   / (P * sigmaij * sigmaij * Math.sqrt(MWij));
        
        return Dij; // m²/s
    }
    
    /** {@inheritDoc} */
    @Override
    public CustomDiffusivityMethod clone() {
        // Clone implementation
        return (CustomDiffusivityMethod) super.clone();
    }
}
```

---

## Creating a Density Model

For liquid density corrections (beyond EoS predictions):

```java
package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Custom liquid density correlation.
 *
 * @author YourName
 */
public class CustomDensityMethod extends Density implements DensityInterface {
    
    private static final long serialVersionUID = 1000;
    
    public CustomDensityMethod(PhysicalProperties phase) {
        super(phase);
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcDensity() {
        // Get EoS density as starting point
        double rhoEoS = phase.getPhase().getDensity("kg/m3");
        
        // Apply correction if needed
        double correction = calcDensityCorrection();
        
        return rhoEoS * correction;
    }
    
    /**
     * Calculate density correction factor.
     *
     * @return correction factor (multiply with EoS density)
     */
    private double calcDensityCorrection() {
        double T = phase.getPhase().getTemperature();
        double P = phase.getPhase().getPressure();
        
        // Example: temperature-dependent correction
        double Tr = T / getMixtureCriticalTemperature();
        return 1.0 + 0.01 * (1.0 - Tr);
    }
    
    private double getMixtureCriticalTemperature() {
        double tcMix = 0.0;
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            tcMix += phase.getPhase().getComponent(i).getx() 
                   * phase.getPhase().getComponent(i).getTC();
        }
        return tcMix;
    }
}
```

---

## Registering Your Model

### Step 1: Add to PhysicalProperties.setViscosityModel()

Edit `PhysicalProperties.java` to recognize your model:

```java
public void setViscosityModel(String model) {
    if ("polynom".equals(model)) {
        viscosityCalc = new neqsim.physicalproperties.methods
            .liquidphysicalproperties.viscosity.Viscosity(this);
    } else if ("friction theory".equals(model)) {
        viscosityCalc = new FrictionTheoryViscosityMethod(this);
    } else if ("LBC".equals(model)) {
        viscosityCalc = new LBCViscosityMethod(this);
    // ADD YOUR MODEL HERE
    } else if ("ABC".equals(model)) {
        viscosityCalc = new ABCViscosityMethod(this);
    }
    // ... other models
}
```

### Step 2: Use Your Model

```java
// Java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Set your custom model
fluid.initPhysicalProperties();
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("ABC");

// Calculate
double viscosity = fluid.getPhase("gas").getViscosity("cP");
```

---

## Complete Example: Custom Viscosity Model

Here's a complete working example implementing a modified LBC viscosity model:

```java
package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.component.ComponentInterface;

/**
 * Modified Lohrenz-Bray-Clark (LBC) viscosity method with tunable parameters.
 * 
 * <p>This model extends the standard LBC correlation with additional
 * parameters for improved accuracy in specific applications.</p>
 * 
 * <p>Reference: Lohrenz, J., Bray, B.G., Clark, C.R., 
 * "Calculating Viscosities of Reservoir Fluids From Their Compositions",
 * JPT, October 1964.</p>
 *
 * @author YourName
 * @version 1.0
 */
public class ModifiedLBCViscosityMethod extends Viscosity {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    
    /** Logger object for class. */
    private static final Logger logger = 
        LogManager.getLogger(ModifiedLBCViscosityMethod.class);
    
    /** Default dense contribution parameters. */
    private static final double[] DEFAULT_PARAMS = 
        {0.10230, 0.023364, 0.058533, -0.040758, 0.0093324};
    
    /** Tunable dense contribution parameters. */
    private double[] denseParams = DEFAULT_PARAMS.clone();
    
    /** Temperature correction factor. */
    private double tempCorrectionFactor = 1.0;
    
    /** Pressure correction factor. */
    private double pressureCorrectionFactor = 1.0;
    
    /**
     * Constructor for ModifiedLBCViscosityMethod.
     *
     * @param phase the physical properties phase object
     */
    public ModifiedLBCViscosityMethod(PhysicalProperties phase) {
        super(phase);
    }
    
    /** {@inheritDoc} */
    @Override
    public double calcViscosity() {
        int nComp = phase.getPhase().getNumberOfComponents();
        
        // Calculate mixture properties
        double volumeMixSum = 0.0;
        double epsilonMixSum = 0.0;
        double mixtureMWsqrt = 0.0;
        double weightedLowPVisc = 0.0;
        
        for (int i = 0; i < nComp; i++) {
            ComponentInterface comp = phase.getPhase().getComponent(i);
            double xi = comp.getx();
            double Vc = getOrEstimateCriticalVolume(comp);
            double MW = comp.getMolarMass() * 1000.0; // g/mol
            double Tc = comp.getTC();
            double Pc = comp.getPC() / 1.01325; // Convert to atm
            double Tr = phase.getPhase().getTemperature() / Tc;
            
            volumeMixSum += xi * Vc;
            
            // Epsilon mixing (LBC characteristic parameter)
            double epsilon = Math.pow(Tc, 1.0/6.0) / 
                           (Math.sqrt(MW) * Math.pow(Pc, 2.0/3.0));
            epsilonMixSum += xi * Math.pow(epsilon, 6.0);
            
            // Low-pressure viscosity contribution
            double lowPVisc;
            if (Tr < 1.5) {
                lowPVisc = 34.0e-5 / epsilon * Math.pow(Tr, 0.94);
            } else {
                lowPVisc = 17.78e-5 / epsilon * 
                          Math.pow(4.58 * Tr - 1.67, 0.625);
            }
            lowPVisc *= 1.0e4; // Convert to micropoise
            
            double sqrtMW = Math.sqrt(MW);
            weightedLowPVisc += xi * lowPVisc * sqrtMW;
            mixtureMWsqrt += xi * sqrtMW;
        }
        
        // Mixture low-pressure viscosity
        double lowPViscMix = weightedLowPVisc / mixtureMWsqrt;
        
        // Dense contribution
        double epsilonMix = Math.pow(epsilonMixSum, 1.0/6.0);
        double critDens = volumeMixSum > 0 ? 1.0 / volumeMixSum : 0.0;
        double reducedDens = phase.getPhase().getDensity("mol/m3") * critDens / 1.0e6;
        
        double densePoly = 0.0;
        for (int k = 0; k < 5; k++) {
            densePoly += denseParams[k] * Math.pow(reducedDens, k);
        }
        double denseContrib = (Math.pow(densePoly, 4) - 1.0e-4) / epsilonMix;
        denseContrib *= 1.0e4; // Convert to micropoise
        
        // Apply corrections
        double totalVisc = (lowPViscMix + Math.max(0, denseContrib)) / 1.0e7;
        totalVisc *= tempCorrectionFactor;
        totalVisc *= pressureCorrectionFactor;
        
        return totalVisc; // Pa·s
    }
    
    /**
     * Get or estimate critical volume for a component.
     *
     * @param comp the component
     * @return critical volume in cm³/mol
     */
    private double getOrEstimateCriticalVolume(ComponentInterface comp) {
        double Vc = comp.getCriticalVolume(); // m³/kmol
        if (Vc > 0) {
            return Vc * 1000.0; // Convert to cm³/mol
        }
        
        // Estimate from Tc, Pc using Rackett equation
        double Tc = comp.getTC();
        double Pc = comp.getPC() * 100.0; // Convert to kPa
        double omega = comp.getAcentricFactor();
        double Zc = 0.291 - 0.080 * omega;
        
        return 8.314 * Tc * Zc / Pc * 1000.0; // cm³/mol
    }
    
    /** {@inheritDoc} */
    @Override
    public double getPureComponentViscosity(int i) {
        ComponentInterface comp = phase.getPhase().getComponent(i);
        double Tc = comp.getTC();
        double Tr = phase.getPhase().getTemperature() / Tc;
        double MW = comp.getMolarMass() * 1000.0;
        double Pc = comp.getPC() / 1.01325;
        
        double epsilon = Math.pow(Tc, 1.0/6.0) / 
                        (Math.sqrt(MW) * Math.pow(Pc, 2.0/3.0));
        
        double lowPVisc;
        if (Tr < 1.5) {
            lowPVisc = 34.0e-5 / epsilon * Math.pow(Tr, 0.94);
        } else {
            lowPVisc = 17.78e-5 / epsilon * 
                      Math.pow(4.58 * Tr - 1.67, 0.625);
        }
        
        return lowPVisc * 1.0e-3; // Convert to Pa·s
    }
    
    /**
     * Set dense contribution parameters for tuning.
     *
     * @param params array of 5 parameters
     */
    public void setDenseParameters(double[] params) {
        if (params.length != 5) {
            throw new IllegalArgumentException(
                "Dense parameters must have 5 elements");
        }
        this.denseParams = params.clone();
    }
    
    /**
     * Get dense contribution parameters.
     *
     * @return array of 5 parameters
     */
    public double[] getDenseParameters() {
        return denseParams.clone();
    }
    
    /**
     * Set temperature correction factor.
     *
     * @param factor correction factor (default 1.0)
     */
    public void setTemperatureCorrectionFactor(double factor) {
        this.tempCorrectionFactor = factor;
    }
    
    /**
     * Set pressure correction factor.
     *
     * @param factor correction factor (default 1.0)
     */
    public void setPressureCorrectionFactor(double factor) {
        this.pressureCorrectionFactor = factor;
    }
    
    /** {@inheritDoc} */
    @Override
    public ModifiedLBCViscosityMethod clone() {
        ModifiedLBCViscosityMethod clone = null;
        try {
            clone = (ModifiedLBCViscosityMethod) super.clone();
            clone.denseParams = this.denseParams.clone();
        } catch (Exception ex) {
            logger.error("Cloning failed.", ex);
        }
        return clone;
    }
}
```

---

## Testing Physical Property Models

### Unit Test Example

```java
package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ModifiedLBCViscosityMethodTest {
    
    private SystemSrkEos fluid;
    
    @BeforeEach
    void setUp() {
        fluid = new SystemSrkEos(300.0, 50.0);
        fluid.addComponent("methane", 0.85);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);
        fluid.setMixingRule("classic");
    }
    
    @Test
    void testViscosityCalculation() {
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        fluid.initPhysicalProperties();
        fluid.getPhase("gas").getPhysicalProperties()
            .setViscosityModel("ModifiedLBC");
        
        double viscosity = fluid.getPhase("gas").getViscosity("cP");
        
        // Typical gas viscosity range: 0.01-0.03 cP
        assertTrue(viscosity > 0.005 && viscosity < 0.05,
            "Viscosity should be in reasonable range: " + viscosity);
    }
    
    @Test
    void testParameterTuning() {
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        fluid.initPhysicalProperties();
        fluid.getPhase("gas").getPhysicalProperties()
            .setViscosityModel("ModifiedLBC");
        
        ModifiedLBCViscosityMethod model = 
            (ModifiedLBCViscosityMethod) fluid.getPhase("gas")
                .getPhysicalProperties().getViscosityModel();
        
        double baseVisc = model.calcViscosity();
        
        // Modify parameter
        double[] newParams = model.getDenseParameters();
        newParams[0] *= 1.1;
        model.setDenseParameters(newParams);
        
        double newVisc = model.calcViscosity();
        
        assertNotEquals(baseVisc, newVisc, 
            "Viscosity should change with parameters");
    }
    
    @Test
    void testTemperatureDependence() {
        double[] temperatures = {250.0, 300.0, 350.0, 400.0};
        double[] viscosities = new double[temperatures.length];
        
        for (int i = 0; i < temperatures.length; i++) {
            fluid.setTemperature(temperatures[i]);
            ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
            ops.TPflash();
            fluid.initPhysicalProperties();
            fluid.getPhase("gas").getPhysicalProperties()
                .setViscosityModel("ModifiedLBC");
            viscosities[i] = fluid.getPhase("gas").getViscosity("cP");
        }
        
        // Gas viscosity should increase with temperature
        for (int i = 1; i < viscosities.length; i++) {
            assertTrue(viscosities[i] > viscosities[i-1],
                "Gas viscosity should increase with temperature");
        }
    }
}
```

---

## Python Integration

### Using Your Model from Python

```python
from neqsim import jneqsim

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Run flash and initialize properties
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initPhysicalProperties()

# Set your custom viscosity model
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("ModifiedLBC")

# Get viscosity
viscosity_cP = fluid.getPhase("gas").getViscosity("cP")
print(f"Gas viscosity: {viscosity_cP:.4f} cP")

# Access and tune model parameters (if model supports it)
visc_model = fluid.getPhase("gas").getPhysicalProperties().getViscosityModel()
if hasattr(visc_model, 'getDenseParameters'):
    params = list(visc_model.getDenseParameters())
    print(f"Dense parameters: {params}")
    
    # Tune parameters
    params[0] *= 1.05
    visc_model.setDenseParameters(params)
    
    new_viscosity = fluid.getPhase("gas").getViscosity("cP")
    print(f"Tuned viscosity: {new_viscosity:.4f} cP")
```

### Comparing Models

```python
import matplotlib.pyplot as plt
import numpy as np

def compare_viscosity_models(fluid, temperature_range, models):
    """Compare different viscosity models over a temperature range."""
    results = {model: [] for model in models}
    
    for T in temperature_range:
        fluid.setTemperature(float(T))
        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.initPhysicalProperties()
        
        for model in models:
            fluid.getPhase("gas").getPhysicalProperties().setViscosityModel(model)
            visc = fluid.getPhase("gas").getViscosity("cP")
            results[model].append(visc)
    
    return results

# Create test fluid
fluid = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")

# Compare models
T_range = np.linspace(250, 450, 20)
models = ["LBC", "friction theory", "PFCT"]
results = compare_viscosity_models(fluid, T_range, models)

# Plot
plt.figure(figsize=(10, 6))
for model, visc in results.items():
    plt.plot(T_range, visc, label=model)
plt.xlabel("Temperature (K)")
plt.ylabel("Viscosity (cP)")
plt.title("Viscosity Model Comparison")
plt.legend()
plt.grid(True)
plt.show()
```

---

## Best Practices

### 1. Handle Edge Cases

```java
@Override
public double calcViscosity() {
    // Check for valid conditions
    if (phase.getPhase().getNumberOfComponents() == 0) {
        return 0.0;
    }
    
    double T = phase.getPhase().getTemperature();
    if (T <= 0) {
        logger.warn("Invalid temperature: {}", T);
        return Double.NaN;
    }
    
    // ... rest of calculation
}
```

### 2. Provide Clear Documentation

```java
/**
 * Calculate dynamic viscosity using the ABC correlation.
 * 
 * <p>Valid range: 200 K &lt; T &lt; 500 K, P &lt; 200 bar</p>
 * 
 * <p>Accuracy: ±5% for light hydrocarbons, ±10% for heavy oils</p>
 *
 * @return viscosity in Pa·s (SI units)
 */
@Override
public double calcViscosity() {
```

### 3. Support Unit Conversions in Calling Code

Models should return SI units. Unit conversions happen at the phase level:

```java
// In PhaseInterface implementations
public double getViscosity(String unit) {
    double viscSI = physicalProperties.getViscosity(); // Pa·s
    
    if ("cP".equals(unit)) {
        return viscSI * 1000.0; // Pa·s to cP
    } else if ("mPa·s".equals(unit)) {
        return viscSI * 1000.0;
    } else if ("kg/m-s".equals(unit) || "Pa·s".equals(unit)) {
        return viscSI;
    }
    // ...
}
```

### 4. Implement Clone Properly

```java
@Override
public MyViscosityMethod clone() {
    MyViscosityMethod clone = null;
    try {
        clone = (MyViscosityMethod) super.clone();
        // Deep copy mutable fields
        clone.parameters = this.parameters.clone();
        clone.componentData = new double[this.componentData.length];
        System.arraycopy(this.componentData, 0, 
                        clone.componentData, 0, 
                        this.componentData.length);
    } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
    }
    return clone;
}
```

### 5. Add Logging for Debugging

```java
private static final Logger logger = LogManager.getLogger(MyMethod.class);

@Override
public double calcViscosity() {
    logger.debug("Calculating viscosity at T={} K, P={} bar",
        phase.getPhase().getTemperature(),
        phase.getPhase().getPressure());
    
    double result = doCalculation();
    
    logger.debug("Calculated viscosity: {} Pa·s", result);
    return result;
}
```

---

## See Also

- [Physical Properties Package Overview](../physical_properties/README.md)
- [Viscosity Models](../physical_properties/viscosity_models.md)
- [Thermal Conductivity Models](../physical_properties/thermal_conductivity_models.md)
- [Extending Thermodynamic Models](extending_thermodynamic_models.md)
- [Python Extension Patterns](python_extension_patterns.md)

---

*Document last updated: February 2026*
