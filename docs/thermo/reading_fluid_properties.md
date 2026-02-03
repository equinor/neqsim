# Reading Fluid Properties in NeqSim

This guide provides comprehensive documentation for calculating and reading thermodynamic and physical properties from fluids, phases, and components in NeqSim.

## Table of Contents

- [Overview](#overview)
- [Property Initialization Levels](#property-initialization-levels)
- [Reading Fluid Properties](#reading-fluid-properties)
- [Reading Phase Properties](#reading-phase-properties)
- [Reading Component Properties](#reading-component-properties)
- [Unit Specifications](#unit-specifications)
- [Volume Translation (Peneloux Correction)](#volume-translation-peneloux-correction)
- [Interphase Properties](#interphase-properties)
- [JSON Property Reports](#json-property-reports)
- [Common Pitfalls and Solutions](#common-pitfalls-and-solutions)
- [Complete Example](#complete-example)

---

## Overview

To calculate fluid properties in NeqSim, you typically follow these steps:

1. **Create a fluid** with specified composition
2. **Set temperature and pressure**
3. **Run a flash calculation** (e.g., TPflash) to determine phase equilibrium
4. **Initialize properties** using `init()` method at the appropriate level
5. **Read properties** from fluid, phases, or components

```java
// Java Example - Basic Property Calculation
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0); // T in Kelvin, P in bara
fluid.addComponent("methane", 0.7);
fluid.addComponent("ethane", 0.2);
fluid.addComponent("propane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize properties
fluid.initProperties();  // Does init(2) + initPhysicalProperties()

// Read properties
double density = fluid.getPhase("gas").getDensity("kg/m3");
double enthalpy = fluid.getEnthalpy("J/mol");
```

```python
# Python (neqsim-python) Example
from neqsim.thermo import createfluid, TPflash, printFrame

fluid = createfluid('srk')
fluid.addComponent("methane", 0.7)
fluid.addComponent("ethane", 0.2)
fluid.addComponent("propane", 0.1)
fluid.setMixingRule("classic")
fluid.setPressure(50.0, "bara")
fluid.setTemperature(25.0, "C")

TPflash(fluid)
fluid.initProperties()

# Read properties
density = fluid.getPhase('gas').getDensity("kg/m3")
```

---

## Property Initialization Levels

NeqSim uses the `init(int type)` method to calculate properties at different levels of detail. **This tiered approach is designed to optimize computational speed** - calculating all possible properties for every flash would be wasteful when you only need basic results.

### Key Principles

1. **Each level includes all properties from lower levels**: `init(2)` calculates everything from `init(1)` plus additional properties. Similarly, `init(3)` includes everything from `init(2)`.

2. **Flash calculations always perform `init(1)`**: After any flash operation (TPflash, PHflash, etc.), properties from `init(1)` - density, fugacities, Z-factor - are always available.

3. **Higher init levels may or may not be done during flash**: Depending on the flash algorithm and settings, `init(2)` or `init(3)` might be called internally, but **you cannot rely on this**.

4. **You can call `init()` without running a flash first**: The `init()` method works on any fluid with defined composition, temperature, and pressure. It calculates properties based on the current state.

5. **Recommended practice**: Always explicitly call the `init()` level you need after a flash calculation, since you don't know which level was performed internally during the flash.

```java
// RECOMMENDED: Explicitly initialize after flash
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.init(2);  // Ensure enthalpy, entropy, Cp, Cv are calculated
// OR
fluid.initProperties();  // For all properties including physical
```

### init(0) - Feed Composition Setup

Sets the feed composition for all phases. This is called automatically when components are added.

**Properties available after init(0):**
- Mole fractions (`getComponent(i).getz()`)
- Component molar masses
- Number of moles

### init(1) - Fugacities and Basic Thermodynamics

Calculates density, fugacity coefficients, and compressibility factor (Z-factor). **This level is always performed during flash calculations**, so these properties are guaranteed to be available after any flash.

**Properties available after init(1):**
- All init(0) properties, plus:
- Density (from EoS, without volume correction)
- Molar volume
- Z-factor (compressibility)
- Fugacity coefficients
- Phase fractions (beta)

```java
fluid.init(1);
double Z = fluid.getPhase(0).getZ();
double fugCoeff = fluid.getPhase(0).getComponent("methane").getFugacityCoefficient();
```

### init(2) - Enthalpy and Heat Capacities

Calculates enthalpy, entropy, heat capacities (Cp, Cv), and most other thermodynamic properties. This level **may or may not** be performed during flash calculations - if you need these properties, call `init(2)` explicitly after the flash.

**Properties available after init(2):**
- All init(1) properties, plus:
- Enthalpy (H)
- Entropy (S)
- Heat capacity at constant pressure (Cp)
- Heat capacity at constant volume (Cv)
- Joule-Thomson coefficient
- Speed of sound
- Gibbs energy
- Internal energy

```java
fluid.init(2);
// Or use the convenience method:
fluid.initThermoProperties();  // Same as init(2)

double enthalpy = fluid.getEnthalpy("J/mol");
double entropy = fluid.getEntropy("J/molK");
double Cp = fluid.getCp("kJ/kgK");
```

### init(3) - Composition Derivatives

Calculates derivatives of fugacity coefficients with respect to composition. Required for advanced calculations like stability analysis. This is the most computationally expensive level and is **rarely performed during standard flash calculations** - call it explicitly when needed.

**Properties available after init(3):**
- All init(2) properties, plus:
- Composition derivatives of fugacity
- Chemical potential derivatives (∂μ/∂n)
- dP/dV, dP/dT derivatives

```java
fluid.init(3);
double dFugdN = fluid.getPhase(0).getComponent("methane").getdfugdn(0);
```

### initPhysicalProperties() - Transport Properties

Initializes physical/transport properties that are not part of the equation of state calculations.

**Properties available after initPhysicalProperties():**
- Viscosity
- Thermal conductivity
- Surface/interfacial tension
- Diffusion coefficients
- **Corrected density** (with Peneloux volume correction)

```java
fluid.initPhysicalProperties();
double viscosity = fluid.getPhase("gas").getViscosity("cP");
double thermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
```

### initProperties() - All Properties (Recommended)

The convenience method that calls both `init(2)` and `initPhysicalProperties()`. **This is the recommended method for most applications.**

```java
fluid.initProperties();  // Equivalent to init(2) + initPhysicalProperties()

// Now ALL properties are available
double density = fluid.getPhase(0).getDensity("kg/m3");  // With volume correction
double enthalpy = fluid.getEnthalpy("J/mol");
double viscosity = fluid.getPhase(0).getViscosity("cP");
```

---

## Reading Fluid Properties

Fluid-level properties represent the overall (total) fluid, weighted across all phases.

### Composition and Flow

| Method | Description | Default Unit |
|--------|-------------|--------------|
| `getMolarComposition()` | Mole fractions of all components | mole fraction |
| `getWeightBasedComposition()` | Mass fractions of all components | weight fraction |
| `getMolarMass()` | Average molar mass | kg/mol |
| `getMolarMass("gr/mol")` | Average molar mass in specified unit | gr/mol |
| `getTotalNumberOfMoles()` | Total moles in fluid | mol |
| `getFlowRate("kg/hr")` | Mass flow rate | kg/hr |
| `getFlowRate("Sm3/hr")` | Standard volumetric flow | Sm3/hr |

```java
double[] composition = fluid.getMolarComposition();
double molarMass = fluid.getMolarMass("gr/mol");
double flowRate = fluid.getFlowRate("kg/hr");
```

### Thermodynamic Properties

| Method | Description | Default Unit | Requires |
|--------|-------------|--------------|----------|
| `getDensity()` | Mixture density (no vol. correction) | kg/m3 | init(1) |
| `getDensity("kg/m3")` | Density with volume correction | kg/m3 | initPhysicalProperties() |
| `getMolarVolume()` | Molar volume (no vol. correction) | m3/mol × 10^5 | init(1) |
| `getMolarVolume("m3/mol")` | Molar volume with correction | m3/mol | initPhysicalProperties() |
| `getEnthalpy()` | Total enthalpy | J | init(2) |
| `getEnthalpy("J/mol")` | Molar enthalpy | J/mol | init(2) |
| `getEnthalpy("kJ/kg")` | Specific enthalpy | kJ/kg | init(2) |
| `getEntropy()` | Total entropy | J/K | init(2) |
| `getEntropy("J/kgK")` | Specific entropy | J/kgK | init(2) |
| `getCp()` | Heat capacity (Cp) | J/K | init(2) |
| `getCp("kJ/kgK")` | Specific heat capacity | kJ/kgK | init(2) |
| `getCv()` | Heat capacity (Cv) | J/K | init(2) |
| `getGamma()` | Heat capacity ratio (Cp/Cv) | - | init(2) |

### Phase Information

| Method | Description |
|--------|-------------|
| `getNumberOfPhases()` | Number of phases present |
| `hasPhaseType("gas")` | Check if phase exists |
| `getBeta(phaseNum)` | Mole fraction of phase |
| `getPhase(0)` | Get phase by number (0=lightest) |
| `getPhase("gas")` | Get phase by name |
| `getPhase(PhaseType.GAS)` | Get phase by type enum |

```java
int numPhases = fluid.getNumberOfPhases();
if (fluid.hasPhaseType("gas")) {
    double gasFraction = fluid.getPhase("gas").getMoleFraction();
}
```

---

## Reading Phase Properties

Phase-level properties are accessed through `fluid.getPhase(...)`. Phase numbering after flash:
- Phase 0: Lightest (typically gas)
- Phase 1: Next lightest (typically oil/liquid)
- Phase 2+: Heavier phases (aqueous, solid, etc.)

### Phase Fraction and Composition

| Method | Description | Default Unit |
|--------|-------------|--------------|
| `getMoleFraction()` | Phase mole fraction (beta) | - |
| `getWtFraction(fluid)` | Phase weight fraction | - |
| `getNumberOfMolesInPhase()` | Moles in phase | mol |
| `getMass()` | Mass of phase | kg |
| `getMolarComposition()` | Component mole fractions in phase | mole fraction |

```java
PhaseInterface gasPhase = fluid.getPhase("gas");
double gasBeta = gasPhase.getMoleFraction();  // Mole fraction of gas phase
double gasWtFrac = gasPhase.getWtFraction(fluid);  // Weight fraction of gas phase
```

### Density and Volume

| Method | Description | Default Unit | Notes |
|--------|-------------|--------------|-------|
| `getDensity()` | Density **without** Peneloux correction | kg/m3 | EoS direct |
| `getDensity("kg/m3")` | Density **with** Peneloux correction | kg/m3 | Recommended |
| `getDensity("mol/m3")` | Molar density with correction | mol/m3 | |
| `getDensity("lb/ft3")` | Density in field units | lb/ft3 | |
| `getMolarVolume()` | Molar volume without correction | m3/mol × 10^5 | Legacy |
| `getMolarVolume("m3/mol")` | Molar volume with correction | m3/mol | |
| `getMolarVolume("litre/mol")` | Molar volume in litres | litre/mol | |
| `getZ()` | Compressibility factor | - | |
| `getTotalVolume()` | Phase volume without correction | m3 × 10^5 | |
| `getCorrectedVolume()` | Phase volume with correction | m3 | |

> **Important:** `getDensity()` without a unit argument returns density **without** volume correction. Always specify a unit (e.g., `"kg/m3"`) to get the corrected density!

```java
// WRONG - returns density without Peneloux correction
double densityRaw = fluid.getPhase(0).getDensity();

// CORRECT - returns density with Peneloux correction
double densityCorrected = fluid.getPhase(0).getDensity("kg/m3");
```

### Thermodynamic Properties

| Method | Description | Default Unit |
|--------|-------------|--------------|
| `getEnthalpy()` | Phase enthalpy | J |
| `getEnthalpy("J/mol")` | Molar enthalpy | J/mol |
| `getEnthalpy("kJ/kg")` | Specific enthalpy | kJ/kg |
| `getEntropy()` | Phase entropy | J/K |
| `getEntropy("J/molK")` | Molar entropy | J/molK |
| `getCp()` | Heat capacity (Cp) | J/K |
| `getCp("kJ/kgK")` | Specific heat capacity | kJ/kgK |
| `getCv()` | Heat capacity (Cv) | J/K |
| `getKappa()` | Isentropic exponent | - |
| `getJouleThomsonCoefficient()` | JT coefficient | K/Pa |
| `getSoundSpeed()` | Speed of sound | m/s |
| `getGibbsEnergy()` | Gibbs free energy | J |

### Transport Properties

| Method | Description | Default Unit | Requires |
|--------|-------------|--------------|----------|
| `getViscosity()` | Dynamic viscosity | kg/(m·s) | initPhysicalProperties() |
| `getViscosity("cP")` | Viscosity in centipoise | cP | |
| `getViscosity("Pas")` | Viscosity in Pascal-seconds | Pa·s | |
| `getThermalConductivity()` | Thermal conductivity | W/(m·K) | initPhysicalProperties() |
| `getThermalConductivity("W/mK")` | Same with explicit unit | W/(m·K) | |

### Specialized Density Methods

For gases, NeqSim provides specialized high-accuracy density methods:

| Method | Description | Applicable To |
|--------|-------------|---------------|
| `getDensity_AGA8()` | AGA8 (GERG-88) density | Natural gas |
| `getDensity_GERG2008()` | GERG-2008 density | Natural gas |
| `getDensity_EOSCG()` | EOS-CG density | CO2-rich gases |
| `getDensity_Leachman()` | Leachman EoS | Hydrogen |
| `getDensity_Vega()` | Vega EoS | Helium |

```java
// High-accuracy gas density for custody transfer
double densityAGA8 = fluid.getPhase("gas").getDensity_AGA8();
double densityGERG = fluid.getPhase("gas").getDensity_GERG2008();
```

---

## Reading Component Properties

Component properties are accessed through `fluid.getPhase(...).getComponent(...)` or `fluid.getComponent(...)`.

### Mole and Weight Fractions

| Method | Description | Notes |
|--------|-------------|-------|
| `getz()` | Mole fraction in total fluid | Overall composition |
| `getx()` | Mole fraction in this phase | Phase composition |
| `getWtFrac(phaseName)` | Weight fraction in phase | |
| `getNumberOfMolesInPhase()` | Moles of component in phase | |

```java
// Mole fraction of methane in total fluid
double zMethane = fluid.getComponent("methane").getz();

// Mole fraction of methane in gas phase
double xMethaneGas = fluid.getPhase("gas").getComponent("methane").getx();

// Mole fraction of methane in oil phase  
double xMethaneOil = fluid.getPhase("oil").getComponent("methane").getx();
```

### Thermodynamic Properties

| Method | Description | Unit |
|--------|-------------|------|
| `getFugacityCoefficient()` | Fugacity coefficient (φ) | - |
| `getFugacity(phase)` | Fugacity (f = φ·x·P) | Pa |
| `getLogFugacityCoefficient()` | ln(φ) | - |
| `getChemicalPotential(phase)` | Chemical potential (μ) | J/mol |
| `getPartialMolarVolume()` | Partial molar volume | m3/mol |

```java
double fugCoeff = fluid.getPhase("gas").getComponent("methane").getFugacityCoefficient();
double chemPot = fluid.getPhase("gas").getComponent("methane").getChemicalPotential(fluid.getPhase("gas"));
```

### Pure Component Properties

| Method | Description | Unit |
|--------|-------------|------|
| `getMolarMass()` | Molar mass | kg/mol |
| `getTC()` | Critical temperature | K |
| `getPC()` | Critical pressure | Pa |
| `getAcentricFactor()` | Acentric factor (ω) | - |
| `getNormalBoilingPoint()` | Normal boiling point | K |
| `getNormalLiquidDensity()` | Liquid density at NBP | kg/m3 |
| `getIdealGasEnthalpyOfFormation()` | ΔHf° (ideal gas) | J/mol |

```java
double Tc = fluid.getComponent("methane").getTC();  // Critical temp in Kelvin
double Pc = fluid.getComponent("methane").getPC();  // Critical pressure in Pa
double omega = fluid.getComponent("methane").getAcentricFactor();
```

### Derivative Properties (requires init(3))

| Method | Description |
|--------|-------------|
| `getdfugdt()` | ∂ln(φ)/∂T |
| `getdfugdp()` | ∂ln(φ)/∂P |
| `getdfugdn(i)` | ∂ln(φ)/∂n_i |
| `getChemicalPotentialdT(phase)` | ∂μ/∂T |
| `getChemicalPotentialdP()` | ∂μ/∂P |
| `getChemicalPotentialdN(i, phase)` | ∂μ/∂n_i |

---

## Unit Specifications

NeqSim supports various unit systems. Units can be specified both when **setting** values (input) and when **getting** values (output).

### Input Units (Setter Methods)

When creating a fluid or setting conditions, you can specify units for input values:

#### System Creation (Constructor)

```java
// Constructor takes temperature in KELVIN and pressure in BARA (always!)
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);  // 298.15 K, 50 bara
```

#### Temperature and Pressure Setters

| Method | Description | Supported Units |
|--------|-------------|-----------------|
| `setTemperature(value, unit)` | Set temperature | `"K"`, `"C"`, `"F"`, `"R"` |
| `setPressure(value, unit)` | Set pressure | `"Pa"`, `"bara"`, `"barg"`, `"psia"`, `"psig"`, `"atm"`, `"MPa"` |

```java
fluid.setTemperature(25.0, "C");      // 25 degrees Celsius
fluid.setTemperature(298.15, "K");    // 298.15 Kelvin
fluid.setPressure(50.0, "bara");      // 50 bar absolute
fluid.setPressure(725.0, "psia");     // 725 psi absolute
```

#### Flow Rate Setters

| Method | Description | Supported Units |
|--------|-------------|-----------------|
| `setTotalFlowRate(value, unit)` | Set total flow | `"kg/sec"`, `"kg/min"`, `"kg/hr"`, `"kg/day"`, `"m3/sec"`, `"m3/min"`, `"m3/hr"`, `"Sm3/sec"`, `"Sm3/hr"`, `"Sm3/day"`, `"MSm3/day"`, `"mole/sec"`, `"mole/min"`, `"mole/hr"` |
| `setFlowRate(value, unit)` | Set stream flow | Same as above |

```java
fluid.setTotalFlowRate(1000.0, "kg/hr");   // Mass flow
fluid.setTotalFlowRate(10000.0, "Sm3/hr"); // Standard volumetric flow
fluid.setTotalFlowRate(50.0, "mole/sec");  // Molar flow
```

#### Component Addition

```java
// Components are added with moles (not mole fraction!)
// The values are normalized internally
fluid.addComponent("methane", 0.70);   // 0.70 moles
fluid.addComponent("ethane", 0.20);    // 0.20 moles
fluid.addComponent("propane", 0.10);   // 0.10 moles
// Total = 1.0 moles, so these become mole fractions

// Or use addComponent with flow rate and unit
fluid.addComponent("methane", 100.0, "kg/hr");  // Mass flow of component
fluid.addComponent("ethane", 50.0, "mole/hr");  // Molar flow of component
```

### Output Units (Getter Methods)

When reading properties, you can either:
1. **No unit specified** → Returns value in default (internal) unit
2. **Unit specified** → Returns value converted to requested unit

### Global Unit Systems

```java
// Activate field units (psia, °F, lb/ft³, etc.)
neqsim.util.unit.Units.activateFieldUnits();

// Activate SI units (Pa, K, kg/m³, etc.)
neqsim.util.unit.Units.activateSIUnits();

// Activate metric units (bara, °C, kg/m³, etc.) - DEFAULT
neqsim.util.unit.Units.activateMetricUnits();
```

### Supported Units by Property

#### Temperature
- `"K"` - Kelvin (SI default)
- `"C"` - Celsius
- `"F"` - Fahrenheit
- `"R"` - Rankine

#### Pressure
- `"Pa"` - Pascal (SI)
- `"bara"` - bar absolute (metric default)
- `"barg"` - bar gauge
- `"psia"` - pounds per square inch absolute
- `"psig"` - pounds per square inch gauge
- `"atm"` - atmospheres
- `"MPa"` - megapascals

#### Density
- `"kg/m3"` - kilograms per cubic meter (default)
- `"mol/m3"` - moles per cubic meter
- `"lb/ft3"` - pounds per cubic foot

#### Enthalpy
- `"J"` - Joules (default)
- `"J/mol"` - Joules per mole
- `"kJ/kmol"` - kilojoules per kilomole
- `"J/kg"` - Joules per kilogram
- `"kJ/kg"` - kilojoules per kilogram
- `"Btu/lbmol"` - BTU per pound-mole (field)

#### Entropy
- `"J/K"` - Joules per Kelvin (default)
- `"J/molK"` - Joules per mole-Kelvin
- `"J/kgK"` - Joules per kilogram-Kelvin
- `"kJ/kgK"` - kilojoules per kilogram-Kelvin

#### Heat Capacity
- `"J/K"` - Joules per Kelvin (default)
- `"J/molK"` - Joules per mole-Kelvin
- `"J/kgK"` - Joules per kilogram-Kelvin
- `"kJ/kgK"` - kilojoules per kilogram-Kelvin

#### Viscosity
- `"kg/msec"` - kg/(m·s) (default)
- `"Pas"` - Pascal-seconds
- `"cP"` - centipoise (= mPa·s)

#### Thermal Conductivity
- `"W/mK"` - Watts per meter-Kelvin (default)
- `"W/cmK"` - Watts per centimeter-Kelvin

#### Interfacial Tension
- Default unit: N/m (Newton per meter)

#### Joule-Thomson Coefficient
- Default unit: K/Pa
- `"C/bar"` - Celsius per bar (metric)
- `"F/psi"` - Fahrenheit per psi (field)

#### Speed of Sound
- Default unit: m/s (meters per second)

#### Molar Mass
- `"kg/mol"` - kilograms per mole (default)
- `"gr/mol"` - grams per mole
- `"lbm/lbmol"` - pounds per pound-mole (field)

#### Flow Rate
- `"kg/sec"`, `"kg/min"`, `"kg/hr"`, `"kg/day"` - mass flow
- `"m3/sec"`, `"m3/min"`, `"m3/hr"` - actual volumetric flow
- `"Sm3/sec"`, `"Sm3/hr"`, `"Sm3/day"`, `"MSm3/day"` - standard volumetric flow
- `"mole/sec"`, `"mole/min"`, `"mole/hr"` - molar flow
- `"idSm3/hr"` - ideal standard cubic meters per hour

#### Molar Volume
- Default: m³/mol × 10⁵ (legacy)
- `"m3/mol"` - cubic meters per mole
- `"litre/mol"` - litres per mole

### Default Units (When Not Specified)

When you call a getter method **without** specifying a unit, it returns the value in the internal/default unit:

| Property | Default Unit (no unit arg) | Notes |
|----------|---------------------------|-------|
| Temperature | K | Kelvin (always internal) |
| Pressure | bara | bar absolute |
| Density | kg/m³ | **Without** Peneloux correction! |
| Molar Volume | m³/mol × 10⁵ | Legacy unit - always specify `"m3/mol"` |
| Molar Mass | kg/mol | Use `"gr/mol"` for grams |
| Enthalpy | J | Total (extensive), not specific |
| Entropy | J/K | Total (extensive), not specific |
| Cp, Cv | J/K | Total (extensive), not specific |
| Viscosity | kg/(m·s) | Same as Pa·s |
| Thermal Conductivity | W/(m·K) | |
| Interfacial Tension | N/m | |
| Speed of Sound | m/s | |
| Joule-Thomson Coeff. | K/Pa | |
| Compressibility (Z) | - | Dimensionless |
| Fugacity Coefficient | - | Dimensionless |

> **Important Notes:**
> - For **density**, always specify a unit like `"kg/m3"` to get Peneloux-corrected values
> - For **molar volume**, always specify `"m3/mol"` to avoid the legacy ×10⁵ scaling
> - For **temperature**, the getter always returns Kelvin; use `getTemperature() - 273.15` for Celsius

---

## Volume Translation (Peneloux Correction)

Cubic equations of state (SRK, PR) systematically overpredict liquid volumes. The Peneloux volume shift corrects this:

$$V_{corrected} = V_{EoS} - c_{mix}$$

where:
$$c_{mix} = \sum_i x_i c_i$$

### Enabling Volume Correction

```java
// Enable Peneloux correction globally
fluid.useVolumeCorrection(true);  // Usually enabled by default

// Or per component
fluid.getPhase(0).getComponent("methane").setVolumeCorrectionConst(0.0);
fluid.getPhase(1).getComponent("n-heptane").setVolumeCorrectionConst(-0.0105);
```

### Key Points About Volume Correction

1. **`getDensity()` (no unit)** returns density calculated directly from EoS **without** Peneloux correction

2. **`getDensity("kg/m3")` (with unit)** returns density **with** Peneloux correction applied

3. **`initPhysicalProperties()`** must be called to use the corrected density methods

4. **Volume correction affects:**
   - `getDensity(unit)` - all units
   - `getMolarVolume(unit)` - when unit specified
   - `getCorrectedVolume()`

5. **Volume correction does NOT affect:**
   - `getDensity()` without unit argument
   - `getMolarVolume()` without unit argument
   - Vapor-liquid equilibrium (VLE) calculations
   - Fugacity coefficients

### Example: Density Comparison

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("n-heptane", 0.3);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

PhaseInterface liquid = fluid.getPhase("oil");

// WITHOUT Peneloux correction
double densityNoCorr = liquid.getDensity();  // Higher error vs experimental

// WITH Peneloux correction (recommended)
double densityCorr = liquid.getDensity("kg/m3");  // Better match to experimental

System.out.println("Density (no correction): " + densityNoCorr + " kg/m3");
System.out.println("Density (with correction): " + densityCorr + " kg/m3");
```

### Tuning Volume Correction

For improved accuracy, especially with heavy hydrocarbons or polar compounds:

```java
// Get current correction constant
double vc = fluid.getPhase(1).getComponent("n-decane").getVolumeCorrectionConst();

// Tune based on experimental data
fluid.getPhase(0).getComponent("n-decane").setVolumeCorrectionConst(-0.015);
fluid.getPhase(1).getComponent("n-decane").setVolumeCorrectionConst(-0.015);
```

---

## Interphase Properties

### Interfacial Tension

```java
fluid.initPhysicalProperties();

// Get interfacial tension between gas and oil phases (N/m)
double sigma = fluid.getInterfacialTension("gas", "oil");

// Gas-water interfacial tension
double sigmaGW = fluid.getInterfacialTension("gas", "aqueous");

// Oil-water interfacial tension  
double sigmaOW = fluid.getInterfacialTension("oil", "aqueous");
```

### Interphase Properties Interface

For more detailed interfacial property calculations:

```java
InterphasePropertiesInterface interphase = fluid.getInterphaseProperties();
// Access additional interfacial models
```

---

## JSON Property Reports

NeqSim can export all fluid properties to JSON format for reporting or data exchange.

### Fluid JSON Report

```java
String fluidJson = fluid.toJson();
System.out.println(fluidJson);
```

Output includes:
- Phase properties (density, molar mass, flow rate)
- Composition (mole fractions, weight fractions by phase)
- Conditions (T, P, model info)

### Component JSON Report

```java
String compJson = fluid.toCompJson();
System.out.println(compJson);
```

Output includes per component:
- Acentric factor
- Critical temperature and pressure
- Mole and weight fractions
- Normal liquid density

### Python Example

```python
import json

# Get fluid properties as JSON
fluid_json = json.loads(str(fluid.toJson()))
print(json.dumps(fluid_json, indent=4))

# Get component properties as JSON
comp_json = json.loads(str(fluid.toCompJson()))
print(json.dumps(comp_json, indent=4))
```

---

## Common Pitfalls and Solutions

### Problem 1: Different Density Values

**Issue:** `getDensity()` and `getDensity("kg/m3")` return different values.

**Cause:** `getDensity()` without unit returns EoS density without Peneloux correction.

**Solution:** Always specify a unit to get corrected density:
```java
double density = fluid.getPhase(0).getDensity("kg/m3");  // Correct
```

### Problem 2: Density Changes with Flow Rate

**Issue:** Setting a flow rate changes the density values.

**Cause:** Some internal calculations may be affected by total moles.

**Solution:** Call `initProperties()` after setting flow rate:
```java
fluid.setTotalFlowRate(1.0, "kg/sec");
ops.TPflash();
fluid.initProperties();
double density = fluid.getPhase(0).getDensity("kg/m3");
```

### Problem 3: Properties Not Available

**Issue:** Methods return 0 or NaN.

**Cause:** Insufficient initialization level.

**Solution:** Call appropriate init method:
```java
// For basic properties
fluid.init(1);

// For enthalpy, entropy, Cp
fluid.init(2);  // or initThermoProperties()

// For transport properties (viscosity, conductivity)
fluid.initPhysicalProperties();

// For all properties (recommended)
fluid.initProperties();
```

### Problem 4: printFrame Shows Different Values

**Issue:** `printFrame()` shows different density than `getDensity()`.

**Cause:** `printFrame()` uses corrected values, `getDensity()` without unit does not.

**Solution:** Use `getDensity("kg/m3")` to match printFrame output.

### Problem 5: Phase Not Found

**Issue:** `getPhase("gas")` returns null or throws exception.

**Cause:** Phase doesn't exist at current conditions.

**Solution:** Check phase existence first:
```java
if (fluid.hasPhaseType("gas")) {
    PhaseInterface gas = fluid.getPhase("gas");
    // ... use gas phase
}
```

---

## Complete Example

```java
import neqsim.thermo.system.*;
import neqsim.thermodynamicoperations.*;

public class PropertyCalculationExample {
    public static void main(String[] args) {
        // 1. Create fluid
        SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
        fluid.addComponent("nitrogen", 0.02);
        fluid.addComponent("CO2", 0.03);
        fluid.addComponent("methane", 0.70);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.08);
        fluid.addComponent("n-butane", 0.04);
        fluid.addComponent("n-pentane", 0.02);
        fluid.addComponent("n-hexane", 0.01);
        fluid.setMixingRule("classic");
        
        // 2. Run flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // 3. Initialize ALL properties
        fluid.initProperties();
        
        // 4. Read fluid-level properties
        System.out.println("=== FLUID PROPERTIES ===");
        System.out.printf("Number of phases: %d%n", fluid.getNumberOfPhases());
        System.out.printf("Molar mass: %.4f kg/mol%n", fluid.getMolarMass());
        System.out.printf("Total enthalpy: %.2f J/mol%n", fluid.getEnthalpy("J/mol"));
        System.out.printf("Total entropy: %.4f J/molK%n", fluid.getEntropy("J/molK"));
        
        // 5. Read phase properties
        for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
            System.out.printf("%n=== PHASE %d (%s) ===%n", i, fluid.getPhase(i).getType());
            System.out.printf("Phase fraction (mole): %.4f%n", fluid.getPhase(i).getMoleFraction());
            System.out.printf("Density (corrected): %.4f kg/m3%n", fluid.getPhase(i).getDensity("kg/m3"));
            System.out.printf("Z-factor: %.6f%n", fluid.getPhase(i).getZ());
            System.out.printf("Enthalpy: %.2f J/mol%n", fluid.getPhase(i).getEnthalpy("J/mol"));
            System.out.printf("Entropy: %.4f J/molK%n", fluid.getPhase(i).getEntropy("J/molK"));
            System.out.printf("Cp: %.4f kJ/kgK%n", fluid.getPhase(i).getCp("kJ/kgK"));
            System.out.printf("Cv: %.4f kJ/kgK%n", fluid.getPhase(i).getCv("kJ/kgK"));
            System.out.printf("Speed of sound: %.2f m/s%n", fluid.getPhase(i).getSoundSpeed());
            System.out.printf("Viscosity: %.6f cP%n", fluid.getPhase(i).getViscosity("cP"));
            System.out.printf("Thermal conductivity: %.6f W/mK%n", fluid.getPhase(i).getThermalConductivity("W/mK"));
        }
        
        // 6. Read component properties in gas phase
        if (fluid.hasPhaseType("gas")) {
            System.out.println("\n=== COMPONENT PROPERTIES (GAS PHASE) ===");
            for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
                String name = fluid.getPhase("gas").getComponent(i).getComponentName();
                double x = fluid.getPhase("gas").getComponent(i).getx();
                double fugCoeff = fluid.getPhase("gas").getComponent(i).getFugacityCoefficient();
                System.out.printf("%s: x=%.6f, phi=%.6f%n", name, x, fugCoeff);
            }
        }
        
        // 7. Interfacial tension (if two phases)
        if (fluid.getNumberOfPhases() > 1) {
            System.out.println("\n=== INTERFACIAL PROPERTIES ===");
            System.out.printf("Gas-Oil IFT: %.6f N/m%n", fluid.getInterfacialTension("gas", "oil"));
        }
    }
}
```

### Python Equivalent

```python
from neqsim import jneqsim

# 1. Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 50.0)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.03)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.08)
fluid.addComponent("n-butane", 0.04)
fluid.addComponent("n-pentane", 0.02)
fluid.addComponent("n-hexane", 0.01)
fluid.setMixingRule("classic")

# 2. Run flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# 3. Initialize ALL properties
fluid.initProperties()

# 4. Read properties
print(f"Number of phases: {fluid.getNumberOfPhases()}")
print(f"Molar mass: {fluid.getMolarMass('gr/mol'):.4f} gr/mol")

for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    print(f"\n=== Phase {i} ({phase.getType()}) ===")
    print(f"Density: {phase.getDensity('kg/m3'):.4f} kg/m3")
    print(f"Viscosity: {phase.getViscosity('cP'):.6f} cP")
    print(f"Cp: {phase.getCp('kJ/kgK'):.4f} kJ/kgK")
```

---

## Related Documentation

- [Density Models](../physical_properties/density_models.md) - Volume correction details
- [Flash Calculations](flash_calculations_guide.md) - Phase equilibrium calculations
- [Thermodynamic Models](thermodynamic_models.md) - EoS selection guide
- [SystemInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/system/SystemInterface.java)
- [PhaseInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/phase/PhaseInterface.java)
- [ComponentInterface API](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/component/ComponentInterface.java)
