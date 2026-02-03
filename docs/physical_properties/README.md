---
title: Physical Properties Package
description: This documentation covers NeqSim's physical properties calculation system, including transport properties (viscosity, thermal conductivity, diffusivity), interfacial properties (surface tension), and ...
---

# Physical Properties Package

This documentation covers NeqSim's physical properties calculation system, including transport properties (viscosity, thermal conductivity, diffusivity), interfacial properties (surface tension), and density correlations.

## Contents

- **Overview** (this page) - Package architecture and basic usage
- [Viscosity Models](viscosity_models) - Dynamic viscosity calculation methods
- [Thermal Conductivity Models](thermal_conductivity_models) - Thermal conductivity methods
- [Diffusivity Models](diffusivity_models) - Binary and multicomponent diffusion coefficients
- [Interfacial Properties](interfacial_properties) - Surface tension and related calculations
- [Density Models](density_models) - Liquid density correlations

---

## Package Architecture

The physical properties package follows a modular design with clear separation between:

1. **Property Handlers** - Manage which models to use for each phase type
2. **Physical Properties System** - Phase-specific property containers
3. **Methods** - Individual calculation models (viscosity, conductivity, etc.)
4. **Mixing Rules** - Combine pure component properties into mixture properties
5. **Interface Properties** - Surface/interfacial tension calculations

```
physicalproperties/
├── PhysicalPropertyHandler.java    # Main entry point
├── PhysicalPropertyType.java       # Property type enum
├── system/
│   ├── PhysicalProperties.java     # Abstract base class
│   ├── PhysicalPropertyModel.java  # Model selection enum
│   ├── gasphysicalproperties/      # Gas phase implementations
│   ├── liquidphysicalproperties/   # Liquid phase implementations
│   └── solidphysicalproperties/    # Solid phase implementations
├── methods/
│   ├── gasphysicalproperties/
│   │   ├── viscosity/
│   │   ├── conductivity/
│   │   └── diffusivity/
│   ├── liquidphysicalproperties/
│   │   ├── viscosity/
│   │   ├── conductivity/
│   │   ├── diffusivity/
│   │   └── density/
│   └── commonphasephysicalproperties/
│       ├── viscosity/              # Models valid for all phases
│       ├── conductivity/
│       └── diffusivity/
├── mixingrule/
│   └── PhysicalPropertyMixingRule.java
└── interfaceproperties/
    ├── InterfaceProperties.java
    └── surfacetension/
```

---

## Basic Usage

### Initializing Physical Properties

Physical properties are calculated after thermodynamic equilibrium has been established:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create fluid and run flash
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();

// Access properties
double gasViscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double gasConductivity = fluid.getPhase("gas").getThermalConductivity("W/mK");
double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
```

### Physical Property Models

NeqSim provides several pre-configured physical property model sets:

| Model | Description | Best For |
|-------|-------------|----------|
| `DEFAULT` | Standard models for oil/gas | General hydrocarbon systems |
| `WATER` | Water-specific correlations | Aqueous systems |
| `SALT_WATER` | Salt water correlations | Brine systems |
| `GLYCOL` | Glycol-specific models | Glycol dehydration |
| `AMINE` | Amine solution models | Gas sweetening |
| `CO2WATER` | CO₂-water system models | CCS applications |
| `BASIC` | Minimal calculations | Fast approximations |

```java
// Set physical property model
fluid.initPhysicalProperties("GLYCOL");

// Or use the enum directly
import neqsim.physicalproperties.system.PhysicalPropertyModel;
fluid.initPhysicalProperties(PhysicalPropertyModel.AMINE);
```

---

## Selecting Individual Models

You can override specific property models while keeping others at defaults:

### Viscosity Model Selection

```java
fluid.initPhysicalProperties();

// Set viscosity model for a specific phase
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
```

Available viscosity models:
- `"polynom"` - Polynomial correlation
- `"friction theory"` - Quiñones-Cisneros friction theory
- `"LBC"` - Lohrenz-Bray-Clark (tunable)
- `"PFCT"` - Pedersen corresponding states
- `"PFCT-Heavy-Oil"` - Pedersen for heavy oils
- `"KTA"` - KTA method
- `"Muzny"` - Muzny (for hydrogen)
- `"CO2Model"` - CO₂ reference
- `"MethaneModel"` - Methane reference

### Thermal Conductivity Model Selection

```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");
```

Available conductivity models:
- `"Chung"` - Chung method (gases)
- `"PFCT"` - Pedersen corresponding states
- `"polynom"` - Polynomial correlation
- `"CO2Model"` - CO₂ reference

### Diffusivity Model Selection

```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
fluid.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
```

Available diffusivity models:
- `"Wilke Lee"` - Wilke-Lee (gases)
- `"Siddiqi Lucas"` - Siddiqi-Lucas (liquids)
- `"CSP"` - Corresponding states
- `"Alkanol amine"` - Amine solutions

### Density Model Selection (Liquids)

```java
fluid.getPhase("oil").getPhysicalProperties().setDensityModel("Costald");
```

Available density models:
- `"Peneloux volume shift"` - EoS with volume translation
- `"Costald"` - COSTALD correlation

---

## Model Tuning

Several models support parameter tuning for better match with experimental data:

### LBC Viscosity Tuning

The LBC model has 5 tunable parameters for the dense-fluid contribution:

```java
// Set all parameters at once
double[] lbcParams = {0.1023, 0.023364, 0.058533, -0.040758, 0.0093324};
fluid.getPhase("oil").getPhysicalProperties().setLbcParameters(lbcParams);

// Or set individual parameters
fluid.getPhase("oil").getPhysicalProperties().setLbcParameter(0, 0.105);
```

### Friction Theory Constants

For non-SRK/PR equations of state:

```java
FrictionTheoryViscosityMethod viscModel = 
    (FrictionTheoryViscosityMethod) fluid.getPhase("oil")
        .getPhysicalProperties().getViscosityModel();

viscModel.setFrictionTheoryConstants(
    kapac,    // Attractive constant
    kaprc,    // Repulsive constant
    kaprrc,   // Repulsive-repulsive constant
    kapa,     // Attractive matrix (3x3)
    kapr,     // Repulsive matrix (3x3)
    kaprr     // Repulsive-repulsive constant
);
```

---

## Accessing Calculated Properties

After initialization, properties are available through the phase interface:

### Per-Phase Properties

```java
// Viscosity
double viscosity = fluid.getPhase("gas").getViscosity();           // Pa·s
double viscosity_cP = fluid.getPhase("gas").getViscosity("cP");    // cP

// Thermal conductivity
double k = fluid.getPhase("gas").getThermalConductivity();         // W/(m·K)
double k_alt = fluid.getPhase("gas").getThermalConductivity("W/mK");

// Density
double rho = fluid.getPhase("gas").getDensity();                   // kg/m³
double rho_alt = fluid.getPhase("gas").getDensity("kg/m3");

// Kinematic viscosity
double nu = fluid.getPhase("gas").getKinematicViscosity();         // m²/s

// Binary diffusion coefficients
double[][] Dij = fluid.getPhase("gas").getPhysicalProperties()
    .getDiffusivityCalc().getBinaryDiffusionCoefficients();        // m²/s
```

### Pure Component Properties

```java
// Pure component viscosity (for mixing rule debugging)
double pureVisc = fluid.getPhase("gas").getPhysicalProperties()
    .getPureComponentViscosity(0);
```

### Interfacial Properties

```java
// Surface tension between phases
fluid.initPhysicalProperties();
double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1);  // N/m
```

---

## Adding New Models

To add a custom physical property model:

### 1. Create the Model Class

```java
package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

public class MyCustomViscosityModel extends Viscosity {
    
    public MyCustomViscosityModel(PhysicalProperties phase) {
        super(phase);
    }
    
    @Override
    public double calcViscosity() {
        // Your implementation here
        double viscosity = 0.0;
        
        // Access phase properties
        double T = phase.getPhase().getTemperature();  // K
        double P = phase.getPhase().getPressure();     // bar
        double rho = phase.getPhase().getDensity();    // kg/m³
        
        // Access component properties
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            double x = phase.getPhase().getComponent(i).getx();
            double Tc = phase.getPhase().getComponent(i).getTC();
            // ... calculate contribution
        }
        
        return viscosity;  // Pa·s
    }
    
    @Override
    public double getPureComponentViscosity(int i) {
        // Return pure component viscosity for component i
        return 0.0;
    }
}
```

### 2. Register in PhysicalProperties

Add to `setViscosityModel()` in `PhysicalProperties.java`:

```java
public void setViscosityModel(String model) {
    // ... existing models ...
    else if ("MyCustomModel".equals(model)) {
        viscosityCalc = new MyCustomViscosityModel(this);
    }
}
```

### 3. Use the Model

```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("MyCustomModel");
```

---

## Performance Considerations

1. **Lazy Initialization**: Properties are only calculated when `initPhysicalProperties()` is called
2. **Selective Updates**: Use `init(phase, PropertyType)` to update only specific properties
3. **Reuse Systems**: Clone fluids rather than recreating for sensitivity studies

```java
// Efficient property sweep
SystemInterface baseFluid = createFluid();
baseFluid.initPhysicalProperties();

for (double T : temperatures) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setTemperature(T, "K");
    ops.TPflash();
    fluid.initPhysicalProperties();
    // ... use properties
}
```

---

## See Also

- [Fluid Creation Guide](../thermo/fluid_creation_guide) - Creating thermodynamic systems
- [Flash Calculations Guide](../thermo/flash_calculations_guide) - Phase equilibrium calculations
- [Thermodynamic Operations](../thermo/thermodynamic_operations) - Property calculation workflow
