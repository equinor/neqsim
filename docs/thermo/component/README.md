# Component Package

Documentation for component modeling in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Component Interface](#component-interface)
- [Pure Component Properties](#pure-component-properties)
- [Component in Phase](#component-in-phase)
- [Component Types](#component-types)
- [Database Components](#database-components)

---

## Overview

**Location:** `neqsim.thermo.component`

The component package contains 65+ classes for modeling pure component properties and their behavior in mixtures.

---

## Component Interface

### Accessing Components

```java
// By name
ComponentInterface methane = fluid.getComponent("methane");

// By index
ComponentInterface comp = fluid.getComponent(0);

// In specific phase
ComponentInterface methaneInGas = fluid.getGasPhase().getComponent("methane");
```

### Common Methods

```java
ComponentInterface comp = fluid.getComponent("methane");

// Pure component properties
double Tc = comp.getTC();           // Critical temperature (K)
double Pc = comp.getPC();           // Critical pressure (bar)
double omega = comp.getAcentricFactor();
double MW = comp.getMolarMass();    // kg/mol

// Composition
double z = comp.getz();             // Overall mole fraction
double x = comp.getx();             // Phase mole fraction
double n = comp.getNumberOfMolesInPhase();

// Fugacity
double f = comp.getFugacity();
double phi = comp.getFugacityCoefficient();
```

---

## Pure Component Properties

### Critical Properties

```java
ComponentInterface comp = fluid.getComponent("propane");

double Tc = comp.getTC();        // 369.83 K
double Pc = comp.getPC();        // 42.48 bar
double Vc = comp.getVc();        // Critical volume (m³/mol)
double Zc = comp.getZc();        // Critical Z-factor
double omega = comp.getAcentricFactor();  // 0.1523
```

### Physical Properties

```java
// Molecular properties
double MW = comp.getMolarMass();            // kg/mol
double Tb = comp.getNormalBoilingPoint();   // K
double Tf = comp.getTriplePointTemperature(); // K

// Reference properties
double dHf = comp.getEnthalpyOfFormation(); // kJ/mol
double dGf = comp.getGibbsEnergyOfFormation();
double Href = comp.getReferencePotential();
```

### EoS Parameters

```java
// SRK/PR parameters
double a = comp.geta();     // Attraction parameter
double b = comp.getb();     // Co-volume parameter

// CPA parameters (for associating)
double eps = comp.getAssociationEnergy();
double beta = comp.getAssociationVolume();

// PC-SAFT parameters
double m = comp.getmSAFTi();        // Segment number
double sigma = comp.getSigmaSAFTi(); // Segment diameter
double epsilon = comp.getEpsSAFTi(); // Dispersion energy
```

---

## Component in Phase

### Phase Composition

```java
PhaseInterface gas = fluid.getGasPhase();
ComponentInterface methane = gas.getComponent("methane");

// Mole fraction in phase
double x_i = methane.getx();

// Fugacity coefficient
double phi_i = methane.getFugacityCoefficient();
double lnPhi = methane.getLogFugacityCoefficient();

// Fugacity
double f_i = methane.getFugacity();

// Chemical potential
double mu_i = methane.getChemicalPotential();

// Activity (for liquid phases)
double a_i = methane.getActivity();
double gamma = methane.getActivityCoefficient();
```

### Partial Properties

```java
// Partial molar volume
double Vbar_i = comp.getPartialMolarVolume();

// Partial molar enthalpy
double Hbar_i = comp.getPartialMolarEnthalpy();

// Partial molar entropy
double Sbar_i = comp.getPartialMolarEntropy();
```

### Derivatives

```java
// Fugacity coefficient derivatives
double dPhidT = comp.getdfugdT();        // d(ln φ)/dT
double dPhidP = comp.getdfugdP();        // d(ln φ)/dP
double dPhidx = comp.getdfugdx(j);       // d(ln φ_i)/dx_j
```

---

## Component Types

### EoS Components

```java
// Standard EoS component
ComponentEos compEos = (ComponentEos) fluid.getComponent("methane");

// Access EoS-specific properties
double ai = compEos.getaT();  // Temperature-dependent a
double bi = compEos.getb();
double alphai = compEos.getAlpha();
```

### CPA Components

```java
// For associating components
ComponentCPA compCPA = (ComponentCPA) fluid.getComponent("water");

// Association properties
double eps = compCPA.getAssociationEnergy();
double beta = compCPA.getAssociationVolume();
int sites = compCPA.getNumberOfAssociationSites();

// Association fraction
double X_A = compCPA.getXsite(0);  // Fraction of site A unbonded
```

### PC-SAFT Components

```java
ComponentPCSAFT compSAFT = (ComponentPCSAFT) fluid.getComponent("n-hexane");

double m = compSAFT.getmSAFTi();      // Chain length
double sigma = compSAFT.getSigmaSAFTi(); // Segment diameter (Å)
double eps = compSAFT.getEpsSAFTi();  // Dispersion energy (K)
```

### Electrolyte Components

```java
// Ions
ComponentElectrolyte ion = (ComponentElectrolyte) fluid.getComponent("Na+");

double charge = ion.getIonicCharge();
double diameter = ion.getIonicDiameter();
```

### Pseudo-Components

```java
// Plus fraction components
ComponentTBP plus = (ComponentTBP) fluid.getComponent("C7+");

double Tb = plus.getNormalBoilingPoint();
double SG = plus.getSpecificGravity();
double MW = plus.getMolarMass();
```

---

## Database Components

### Available Components

NeqSim includes a database with 100+ components:

| Category | Examples |
|----------|----------|
| Light gases | nitrogen, oxygen, CO2, H2S, hydrogen |
| Hydrocarbons | methane through n-C20 |
| Cyclic | cyclohexane, benzene, toluene |
| Polar | water, methanol, ethanol, MEG, DEG, TEG |
| Amines | MDEA, MEA, DEA, piperazine |
| Refrigerants | R-134a, R-32, ammonia |

### Adding Components

```java
// Add from database
fluid.addComponent("methane", 1.0);

// Add with alias
fluid.addComponent("CO2", 0.5);  // Carbon dioxide

// Add pseudo-component (TBP method)
fluid.addTBPfraction("C10", 0.1, 140.0, 0.75);  // name, moles, MW, SG

// Add plus fraction
fluid.addPlusFraction("C7+", 0.05, 150.0, 0.78);
```

### Component Name Lookup

```java
// Check if component exists
boolean exists = fluid.hasComponent("methane");

// Get component index
int index = fluid.getComponentIndex("ethane");
```

---

## Example: Component Properties Report

```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");
fluid.init(0);

System.out.println("Component Properties:");
System.out.println("-------------------------------------------");
System.out.printf("%-10s %8s %8s %8s %8s%n", 
    "Name", "Tc(K)", "Pc(bar)", "omega", "MW");
System.out.println("-------------------------------------------");

for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
    ComponentInterface comp = fluid.getComponent(i);
    System.out.printf("%-10s %8.2f %8.2f %8.4f %8.4f%n",
        comp.getName(),
        comp.getTC(),
        comp.getPC(),
        comp.getAcentricFactor(),
        comp.getMolarMass() * 1000);  // g/mol
}
```

---

## Related Documentation

- [System Package](system/README.md) - Fluid systems
- [Phase Package](phase/README.md) - Phase modeling
- [Mixing Rules](mixingrule/README.md) - Binary interactions
- [Thermo Package](README.md) - Package overview
