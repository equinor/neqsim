---
title: Adsorption Modeling Review and Enhancement Proposal
description: Comprehensive review of NeqSim's adsorption implementation with suggestions for new isotherm models and capillary condensation calculations.
---

# Adsorption Modeling Review and Enhancement Proposal

## Executive Summary

This document reviews NeqSim's current adsorption modeling capabilities and proposes enhancements including additional isotherm models, capillary condensation, and improved database support for multiple adsorbent materials.

---

## Current Implementation Review

### Architecture Overview

The adsorption functionality in NeqSim is located in:
- Interface: `neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface`
- Implementation: `neqsim.physicalproperties.interfaceproperties.solidadsorption.PotentialTheoryAdsorption`
- Database: `src/main/resources/data/AdsorptionParameters.csv`

### Current Model: Potential Theory (Polanyi/DRA)

The existing implementation uses the **Dubinin-Radushkevich-Astakhov (DRA)** potential theory with the following key equations:

**Adsorption Potential:**
$$\epsilon = \epsilon_0 \cdot \left( \ln\frac{z_0}{z} \right)^{1/\beta}$$

**Surface Excess Integration:**
The model integrates from the bulk phase to the surface using fugacity equilibrium.

### Strengths of Current Implementation

1. **Thermodynamically rigorous** - Uses fugacity-based calculations
2. **Multi-component capable** - Handles mixtures
3. **EoS integration** - Uses NeqSim's thermodynamic models for real gas behavior
4. **Database-driven parameters** - Parameters stored in CSV for easy extension

### Limitations Identified

| Issue | Description | Impact |
|-------|-------------|--------|
| Single isotherm model | Only DRA potential theory available | Limited applicability |
| Limited database entries | Only 9 component/solid combinations | Many systems unsupported |
| No capillary condensation | Critical for mesoporous materials | Incomplete physics |
| No temperature dependence | Parameters assumed constant | Poor extrapolation |
| Missing `getSurfaceExcess(int)` | Throws `UnsupportedOperationException` | Incomplete API |
| No BET surface area | Common characterization missing | No standardization |
| No pore size distribution | Required for accurate modeling | Oversimplified |

---

## Proposed Enhancements

### 1. Additional Isotherm Models

#### 1.1 Langmuir Isotherm (Single Component)

For monolayer adsorption on homogeneous surfaces:

$$q = q_{max} \cdot \frac{K \cdot P}{1 + K \cdot P}$$

Where:
- $q$ = amount adsorbed (mol/kg)
- $q_{max}$ = maximum adsorption capacity
- $K$ = Langmuir constant (1/bar)
- $P$ = partial pressure (bar)

#### 1.2 Freundlich Isotherm

For heterogeneous surfaces:

$$q = K_F \cdot P^{1/n}$$

Where:
- $K_F$ = Freundlich capacity constant
- $n$ = heterogeneity parameter (n > 1 for favorable adsorption)

#### 1.3 BET Isotherm (Brunauer-Emmett-Teller)

For multilayer adsorption:

$$\frac{P}{q(P_0 - P)} = \frac{1}{q_m \cdot C} + \frac{C - 1}{q_m \cdot C} \cdot \frac{P}{P_0}$$

Where:
- $P_0$ = saturation pressure
- $q_m$ = monolayer capacity
- $C$ = BET constant (related to heat of adsorption)

#### 1.4 Sips Isotherm (Langmuir-Freundlich)

Combines Langmuir and Freundlich for heterogeneous surfaces:

$$q = q_{max} \cdot \frac{(K \cdot P)^{1/n}}{1 + (K \cdot P)^{1/n}}$$

#### 1.5 Extended Langmuir (Multi-Component)

For competitive adsorption:

$$q_i = q_{max,i} \cdot \frac{K_i \cdot P_i}{1 + \sum_j K_j \cdot P_j}$$

### 2. Capillary Condensation Model

#### 2.1 Theoretical Background

Capillary condensation occurs in mesopores (2-50 nm) when vapor condenses at pressures below saturation due to curved meniscus effects.

**Kelvin Equation:**
$$\ln\frac{P}{P_0} = -\frac{2 \gamma V_m \cos\theta}{r \cdot R \cdot T}$$

Where:
- $\gamma$ = surface tension (N/m)
- $V_m$ = molar volume of liquid (m³/mol)
- $\theta$ = contact angle
- $r$ = pore radius (m)
- $R$ = gas constant
- $T$ = temperature (K)

**Kelvin Radius:**
$$r_K = -\frac{2 \gamma V_m \cos\theta}{R T \ln(P/P_0)}$$

#### 2.2 Pore Size Distribution

Implement support for common PSD models:

1. **Cylindrical pores (BJH method)**
2. **Slit pores (for activated carbons)**
3. **User-defined distribution**

#### 2.3 Combined Adsorption + Capillary Condensation

Total uptake = Multilayer adsorption + Capillary condensate:

$$q_{total} = q_{BET} + \int_{r_{min}}^{r_K} \rho_L \cdot V(r) \cdot f(r) \, dr$$

### 3. Database Enhancement

#### New Database Table Structure

**AdsorptionParameters.csv** - Extended:
```csv
ID,Name,Solid,IsothermModel,eps,z0,beta,qmax,K_langmuir,K_freundlich,n_freundlich,C_BET,TempRef,dH_ads
1,methane,AC Calgon F400,DRA,7.458,0.298,2.0,,,,,298.15,-20000
2,CO2,AC Calgon F400,DRA,7.76,0.298,2.0,,,,,298.15,-25000
```

**AdsorbentProperties.csv** - New:
```csv
ID,SolidName,BETSurfaceArea_m2g,PoreVolume_cm3g,MeanPoreRadius_nm,PoreType,MinPoreRadius_nm,MaxPoreRadius_nm
1,AC Calgon F400,1100,0.45,1.2,cylindrical,0.3,5.0
2,AC Norit R1,950,0.42,1.5,slit,0.4,6.0
3,Zeolite 13X,540,0.28,0.4,cylindrical,0.3,0.7
4,Silica Gel,650,0.38,3.0,cylindrical,1.0,10.0
```

---

## Proposed Class Architecture

### New Interface Hierarchy

```
AdsorptionInterface (existing)
├── calcAdsorption(int phaseNum)
├── getSurfaceExcess(int component)
├── getSurfaceExcess(String componentName)
├── setSolidMaterial(String solidM)
│
├── NEW: setIsothermModel(IsothermType type)
├── NEW: getIsothermModel()
├── NEW: calcCapillaryCondensation()
├── NEW: getCapillaryCondensateAmount(String component)
├── NEW: getTotalUptake(String component)
├── NEW: getKelvinRadius()
├── NEW: setPoreDistribution(PoreDistribution psd)
```

### New Enums

```java
public enum IsothermType {
    DRA,           // Dubinin-Radushkevich-Astakhov
    LANGMUIR,      // Single-site Langmuir
    FREUNDLICH,    // Freundlich
    BET,           // BET multilayer
    SIPS,          // Langmuir-Freundlich
    EXTENDED_LANGMUIR  // Multi-component Langmuir
}

public enum PoreType {
    CYLINDRICAL,   // e.g., MCM-41, zeolites
    SLIT,          // e.g., activated carbons
    SPHERICAL,     // e.g., cage-type zeolites
    INK_BOTTLE     // Constrained pores
}
```

### New Classes

1. **AbstractAdsorptionModel** - Base class with common functionality
2. **LangmuirAdsorption** - Langmuir isotherm implementation
3. **FreundlichAdsorption** - Freundlich isotherm implementation
4. **BETAdsorption** - BET multilayer model
5. **SipsAdsorption** - Sips isotherm implementation
6. **ExtendedLangmuirAdsorption** - Multi-component competitive adsorption
7. **CapillaryCondensationModel** - Kelvin equation + PSD
8. **PoreDistribution** - Pore size distribution handling
9. **AdsorbentMaterial** - Adsorbent properties container

---

## Implementation Priority

| Priority | Feature | Effort | Impact |
|----------|---------|--------|--------|
| 1 | Fix `getSurfaceExcess(int)` | Low | High |
| 2 | Langmuir isotherm | Medium | High |
| 3 | BET isotherm | Medium | High |
| 4 | Capillary condensation | High | High |
| 5 | Extended database | Medium | Medium |
| 6 | Freundlich/Sips | Low | Medium |
| 7 | Pore size distribution | High | Medium |

---

## Test Cases Required

### Unit Tests

1. **LangmuirAdsorptionTest**
   - Single component at various pressures
   - Verify saturation behavior
   - Check against published data

2. **BETAdsorptionTest**
   - Multilayer coverage calculation
   - BET plot linearity
   - Surface area determination

3. **CapillaryCondensationTest**
   - Kelvin radius calculation
   - Hysteresis handling (adsorption vs desorption)
   - Integration with BET model

4. **MultiComponentAdsorptionTest**
   - Competitive adsorption
   - Selectivity calculations
   - Mixture isotherms

### Integration Tests

1. Adsorption + Flash calculations
2. Column breakthrough simulation
3. Temperature swing adsorption cycles

---

## Example Usage (Proposed API)

```java
// Create fluid system
SystemInterface gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("CO2", 0.5);
gas.addComponent("methane", 0.5);
gas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();
gas.initProperties();

// Initialize adsorption with specific model
gas.getInterphaseProperties().initAdsorption();
AdsorptionInterface ads = gas.getInterphaseProperties().getAdsorptionCalc("gas");
ads.setSolidMaterial("Zeolite 13X");
ads.setIsothermModel(IsothermType.EXTENDED_LANGMUIR);

// Calculate adsorption
ads.calcAdsorption(0);

// Get component uptakes
double co2Uptake = ads.getSurfaceExcess("CO2");  // mol/kg
double ch4Uptake = ads.getSurfaceExcess("methane");

// Calculate selectivity
double selectivity = (co2Uptake / ch4Uptake) /
    (gas.getPhase(0).getComponent("CO2").getx() /
     gas.getPhase(0).getComponent("methane").getx());

// With capillary condensation (for mesoporous materials)
ads.calcCapillaryCondensation();
double kelvinRadius = ads.getKelvinRadius();  // nm
double condensate = ads.getCapillaryCondensateAmount("CO2");
double totalUptake = ads.getTotalUptake("CO2");
```

---

## References

1. Dubinin, M.M., "The Potential Theory of Adsorption of Gases and Vapors for Adsorbents with Energetically Nonuniform Surfaces", Chem. Rev. 1960, 60(2), 235-241
2. Langmuir, I., "The Constitution and Fundamental Properties of Solids and Liquids", J. Am. Chem. Soc. 1916, 38(11), 2221-2295
3. Brunauer, Emmett, Teller, "Adsorption of Gases in Multimolecular Layers", J. Am. Chem. Soc. 1938, 60(2), 309-319
4. Barrett, Joyner, Halenda, "The Determination of Pore Volume and Area Distributions in Porous Substances", J. Am. Chem. Soc. 1951, 73(1), 373-380
5. Do, D.D., "Adsorption Analysis: Equilibria and Kinetics", Imperial College Press, 1998

---

## Related Documentation

- [Interfacial Properties](interfacial_properties.md)
- [Thermodynamic Models](../thermo/thermodynamic_models.md)
- [Component Database Guide](../thermo/component_database_guide.md)
