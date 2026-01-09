# Solution Gas-Water Ratio (Rsw) Calculation

## Overview

The **Solution Gas-Water Ratio (Rsw)** represents the volume of gas dissolved in water at reservoir conditions, expressed as standard cubic meters of gas per standard cubic meter of water (Sm³/Sm³). This property is essential for:

- Reservoir simulation and material balance calculations
- Formation water production forecasting
- Gas-water contact movement prediction
- Aquifer influx modeling
- Environmental impact assessments (dissolved gas emissions)

The `SolutionGasWaterRatio` class in NeqSim provides three calculation methods with varying levels of complexity and accuracy.

## Physical Background

### Gas Solubility in Water

Gas solubility in water is governed by Henry's Law at low pressures:

$$x_g = \frac{P_g}{H}$$

where:
- $x_g$ = mole fraction of gas dissolved in water
- $P_g$ = partial pressure of gas
- $H$ = Henry's constant (temperature-dependent)

At higher pressures, deviations from Henry's Law become significant, and equation of state methods are required.

### Key Factors Affecting Rsw

1. **Pressure**: Rsw increases approximately linearly with pressure at moderate conditions
2. **Temperature**: Shows a minimum around 70-100°C for hydrocarbons
   - At low T: solubility decreases with increasing T (entropy effect)
   - At high T: solubility increases with T (approaching critical point)
3. **Salinity**: Dissolved salts reduce gas solubility ("salting-out" effect)
4. **Gas Composition**: CO₂ is 20-50× more soluble than methane; H₂S is even more soluble

### Typical Values

| Condition | Rsw (Sm³/Sm³) |
|-----------|---------------|
| Shallow reservoir (50 bar, 50°C) | 0.5 - 1.0 |
| Medium depth (150 bar, 80°C) | 1.5 - 3.0 |
| Deep reservoir (300 bar, 120°C) | 3.0 - 6.0 |
| CO₂-rich gas (100 bar, 50°C) | 5.0 - 15.0 |

## Available Calculation Methods

### 1. McCain (Culberson-McKetta) Correlation

**Best for:** Quick estimates, pure methane systems, engineering screening

The McCain correlation is based on the Culberson-McKetta experimental data (1951) with coefficients from McCain (1990).

#### Formulation

For pure water:
$$R_{sw,pure} = A + B \cdot P + C \cdot P^2$$

where coefficients A, B, C are temperature-dependent polynomials:

$$A = 8.15839 - 6.12265 \times 10^{-2}T + 1.91663 \times 10^{-4}T^2 - 2.1654 \times 10^{-7}T^3$$

$$B = 1.01021 \times 10^{-2} - 7.44241 \times 10^{-5}T + 3.05553 \times 10^{-7}T^2 - 2.94883 \times 10^{-10}T^3$$

$$C = (-9.02505 + 0.130237T - 8.53425 \times 10^{-4}T^2 + 2.34122 \times 10^{-6}T^3 - 2.37049 \times 10^{-9}T^4) \times 10^{-7}$$

where T is in °F and P is in psia.

#### Salinity Correction

$$R_{sw,brine} = R_{sw,pure} \times 10^{-C_s \cdot S}$$

where:
- $S$ = salinity in wt% NaCl
- $C_s$ = salinity coefficient (temperature and pressure dependent)

#### Validity Range

| Parameter | Range |
|-----------|-------|
| Temperature | 60-350°F (15-177°C) |
| Pressure | 14.7-10,000 psia (1-690 bar) |
| Salinity | 0-30 wt% NaCl |
| Gas type | Methane (use with caution for other gases) |

### 2. Søreide-Whitson Method

**Best for:** Multi-component gas mixtures, moderate salinity, process simulation

Uses the modified Peng-Robinson equation of state with Søreide-Whitson alpha function and mixing rules specifically developed for hydrocarbon-water systems.

#### Key Features

- Accounts for gas composition effects (methane, ethane, CO₂, N₂, etc.)
- Built-in salinity correction through modified mixing rules
- Thermodynamically consistent (fugacity-based)
- Applicable to gas-water-hydrocarbon liquid systems

#### Mixing Rule

The Søreide-Whitson mixing rule (mixing rule 11 in NeqSim) uses:

$$a_m = \sum_i \sum_j x_i x_j \sqrt{a_i a_j}(1 - k_{ij})$$

with special binary interaction parameters for water-hydrocarbon pairs that account for salinity.

#### Validity Range

| Parameter | Range |
|-----------|-------|
| Temperature | 273-473 K (0-200°C) |
| Pressure | 1-1000 bar |
| Salinity | 0-6 mol/kg (0-26 wt% NaCl) |
| Gas type | Any hydrocarbon mixture with CO₂, N₂, H₂S |

### 3. Electrolyte CPA Method

**Best for:** High-accuracy predictions, electrolyte systems, research applications

Uses the Cubic-Plus-Association (CPA) equation of state with electrolyte extensions for rigorous modeling of ion-water-gas interactions.

#### Key Features

- Explicit treatment of Na⁺ and Cl⁻ ions
- Association term for water hydrogen bonding
- Most accurate for saline systems
- Handles ion-specific effects (different salts)

#### CPA Equation

$$P = \frac{RT}{V_m - b} - \frac{a}{V_m(V_m + b)} - \frac{1}{2}\frac{RT}{V_m}\left(1 + \rho\frac{\partial \ln g}{\partial \rho}\right)\sum_A x_A(1 - X_A)$$

where the last term accounts for hydrogen bonding associations.

#### Validity Range

| Parameter | Range |
|-----------|-------|
| Temperature | 273-473 K (0-200°C) |
| Pressure | 1-1000 bar |
| Salinity | 0-6 mol/kg NaCl equivalent |
| Gas type | Any composition |

## Usage Examples

### Basic Usage with McCain Correlation

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.pvtsimulation.simulation.SolutionGasWaterRatio;

// Create gas system
SystemInterface gas = new SystemSrkCPAstatoil(350.0, 100.0); // 350 K, 100 bar
gas.addComponent("methane", 0.95);
gas.addComponent("CO2", 0.05);
gas.setMixingRule(10);

// Create Rsw calculator
SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

// Set conditions
double[] temperatures = {350.0, 360.0, 370.0}; // K
double[] pressures = {100.0, 150.0, 200.0};    // bar
rswCalc.setTemperaturesAndPressures(temperatures, pressures);

// Set salinity (pure water)
rswCalc.setSalinity(0.0);

// Use McCain method
rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
rswCalc.runCalc();

// Get results
double[] rsw = rswCalc.getRsw();
for (int i = 0; i < rsw.length; i++) {
    System.out.printf("T=%.1f K, P=%.1f bar: Rsw = %.4f Sm³/Sm³%n", 
                      temperatures[i], pressures[i], rsw[i]);
}
```

### Using Søreide-Whitson for Multi-Component Gas

```java
// Create gas with multiple components
SystemInterface gas = new SystemSrkCPAstatoil(373.15, 200.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.05);
gas.addComponent("nitrogen", 0.02);
gas.setMixingRule(10);

SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);
rswCalc.setTemperaturesAndPressures(new double[]{373.15}, new double[]{200.0});

// Use Søreide-Whitson method
rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
rswCalc.runCalc();

System.out.printf("Rsw (Søreide-Whitson) = %.4f Sm³/Sm³%n", rswCalc.getRsw(0));
```

### Accounting for Salinity

```java
SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);
rswCalc.setTemperaturesAndPressures(new double[]{350.0}, new double[]{100.0});

// Compare pure water vs seawater vs formation water
double[] salinities = {0.0, 3.5, 10.0}; // wt% NaCl
String[] waterTypes = {"Pure water", "Seawater", "Formation water"};

rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);

for (int i = 0; i < salinities.length; i++) {
    rswCalc.setSalinity(salinities[i], "wt%");
    rswCalc.runCalc();
    System.out.printf("%s (%.1f wt%% NaCl): Rsw = %.4f Sm³/Sm³%n", 
                      waterTypes[i], salinities[i], rswCalc.getRsw(0));
}
```

### Salinity Unit Conversions

The class supports multiple salinity units:

```java
// Set salinity in different units
rswCalc.setSalinity(0.5);           // Default: molality (mol NaCl / kg water)
rswCalc.setSalinity(3.5, "wt%");    // Weight percent NaCl
rswCalc.setSalinity(35000, "ppm");  // Parts per million
```

## Method Selection Guide

| Scenario | Recommended Method | Reason |
|----------|-------------------|--------|
| Quick screening | McCain | Fast, simple |
| Pure methane system | McCain | Optimized for CH₄ |
| Multi-component gas | Søreide-Whitson | Accounts for composition |
| CO₂-rich gas (>20% CO₂) | Søreide-Whitson or CPA | McCain underestimates |
| High salinity (>5 wt%) | Electrolyte CPA | Best ion modeling |
| Research/validation | Electrolyte CPA | Most rigorous |
| Process simulation | Søreide-Whitson | Good balance |

## Comparison with Literature

### Methane Solubility in Pure Water

| T (°C) | P (bar) | Literature (Sm³/Sm³) | McCain | Søreide-Whitson | CPA |
|--------|---------|---------------------|--------|-----------------|-----|
| 25 | 100 | 1.8-2.2 | 2.20 | 1.02 | 2.52 |
| 50 | 100 | 1.5-1.8 | 1.78 | 1.03 | 1.94 |
| 75 | 100 | 1.4-1.7 | 1.57 | 1.13 | 1.65 |
| 100 | 100 | 1.5-1.8 | 1.59 | 1.31 | 1.52 |

### Salinity Effect

The salting-out coefficient ($k_s$) represents the reduction in solubility per unit salinity:

$$\log_{10}\left(\frac{R_{sw,brine}}{R_{sw,pure}}\right) = -k_s \cdot m_{salt}$$

| Method | Typical $k_s$ (L/mol) |
|--------|----------------------|
| McCain | 0.10-0.15 |
| Søreide-Whitson | 0.12-0.18 |
| Electrolyte CPA | 0.10-0.16 |
| Experimental (Duan & Mao, 2006) | 0.11-0.14 |

## API Reference

### Constructor

```java
public SolutionGasWaterRatio(SystemInterface inputSystem)
```

Creates a new Rsw calculator using the given thermodynamic system as the gas composition source.

### Key Methods

| Method | Description |
|--------|-------------|
| `setCalculationMethod(CalculationMethod method)` | Set calculation method (MCCAIN, SOREIDE_WHITSON, ELECTROLYTE_CPA) |
| `setSalinity(double salinity)` | Set salinity in mol/kg water |
| `setSalinity(double salinity, String unit)` | Set salinity with unit ("wt%", "ppm") |
| `setTemperaturesAndPressures(double[] T, double[] P)` | Set calculation conditions |
| `runCalc()` | Execute calculation |
| `getRsw()` | Get array of calculated Rsw values |
| `getRsw(int index)` | Get Rsw at specific index |
| `calculateRsw(double T, double P)` | Single-point calculation |

### Calculation Methods Enum

```java
public enum CalculationMethod {
    MCCAIN,           // Empirical correlation (fast)
    SOREIDE_WHITSON,  // Modified PR-EoS (recommended)
    ELECTROLYTE_CPA   // CPA with electrolytes (most accurate)
}
```

## References

1. **Culberson, O.L. and McKetta, J.J.** (1951). "Phase Equilibria in Hydrocarbon-Water Systems III - The Solubility of Methane in Water at Pressures to 10,000 psia." *Journal of Petroleum Technology*, 3(08), 223-226.

2. **McCain, W.D. Jr.** (1990). *The Properties of Petroleum Fluids*, 2nd Edition. PennWell Publishing Company.

3. **Søreide, I. and Whitson, C.H.** (1992). "Peng-Robinson Predictions for Hydrocarbons, CO₂, N₂, and H₂S with Pure Water and NaCl Brine." *Fluid Phase Equilibria*, 77, 217-240.

4. **Duan, Z. and Mao, S.** (2006). "A Thermodynamic Model for Calculating Methane Solubility, Density and Gas Phase Composition of Methane-Bearing Aqueous Fluids from 273 to 523 K and from 1 to 2000 bar." *Geochimica et Cosmochimica Acta*, 70(13), 3369-3386.

5. **Haghighi, H., Chapoy, A., and Tohidi, B.** (2009). "Methane and Water Phase Equilibria in the Presence of Single and Mixed Electrolyte Solutions Using the Cubic-Plus-Association Equation of State." *Oil & Gas Science and Technology*, 64(2), 141-154.

## See Also

- [GOR (Gas-Oil Ratio)](GOR.md) - Related PVT property for oil systems
- [BasePVTsimulation](BasePVTsimulation.md) - Base class documentation
- [SystemSoreideWhitson](../thermo/SystemSoreideWhitson.md) - Equation of state details
- [SystemElectrolyteCPAstatoil](../thermo/SystemElectrolyteCPAstatoil.md) - CPA electrolyte model
