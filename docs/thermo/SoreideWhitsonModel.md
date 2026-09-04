---
title: "Søreide-Whitson Model for Gas Solubility in Brine"
description: "Guide to the Søreide-Whitson Peng-Robinson model for gas solubility in brines used in production, water, and CCS applications."
---

## Overview

The Søreide-Whitson model is a modified Peng-Robinson equation of state specifically designed for predicting gas solubility in aqueous systems containing dissolved salts (brine). This model is essential for accurate prediction of hydrocarbon and acid gas solubility in produced water, formation water, and seawater—applications critical for offshore oil and gas operations, carbon capture and storage (CCS), and environmental emission calculations.

The model is implemented in NeqSim as `SystemSoreideWhitson` and is used extensively in **NeqSimLive** for real-time emission calculations from produced water degassing on offshore platforms.

---

## Table of Contents

1. [Theoretical Background](#theoretical-background)
2. [Mathematical Formulation](#mathematical-formulation)
3. [Implementation in NeqSim](#implementation-in-neqsim)
4. [Application: Produced Water Emissions](#application-produced-water-emissions)
5. [Salt Type Coefficients](#salt-type-coefficients)
6. [Usage Examples](#usage-examples)
7. [Validation and Accuracy](#validation-and-accuracy)
8. [Literature References](#literature-references)

---

## Theoretical Background

### The Salting-Out Effect

When gases dissolve in water, the presence of dissolved salts significantly reduces their solubility—a phenomenon known as the "salting-out" effect. This occurs because:

1. **Ion-dipole interactions**: Dissolved ions strongly attract water molecules, reducing water's availability to solvate gas molecules
2. **Electrostriction**: Ions cause local compression of water structure, reducing the free volume available for dissolved gases
3. **Cavity formation**: Creating a cavity for gas molecules requires more energy in electrolyte solutions

The magnitude of the salting-out effect depends on:
- Salt concentration (molality or ionic strength)
- Salt type (valence, ionic radius)
- Gas species (size, polarizability)
- Temperature and pressure

### Historical Development

The original Søreide-Whitson model was developed by Ingolf Søreide and Curtis H. Whitson at NTNU (Norwegian University of Science and Technology) and published in 1992. The model addressed limitations of standard cubic equations of state for predicting:

- Gas solubility in formation water and seawater
- Water content of hydrocarbon gases in equilibrium with brine
- Phase behavior of reservoir fluids with formation water

The model has become an industry standard for produced water calculations, particularly on the Norwegian Continental Shelf.

Chabab et al. (2019) refitted the aqueous CO₂-water binary-interaction parameter to newer
CO₂-H₂O-NaCl equilibrium data. NeqSim exposes this modified correlation as an explicit option;
the original correlation remains the default so existing simulations are reproducible.

Burgoyne and Nielsen (2026) refreshed the framework for eight gases using about 2,000
pointwise-regressed interaction parameters and extended it to hydrogen. NeqSim exposes their
Søreide-Whitson-alpha-compatible, embedded-salinity parameter set as another explicit option.

---

## Mathematical Formulation

### Modified Peng-Robinson Equation

The Søreide-Whitson model is based on the Peng-Robinson (1978) equation of state:

$$
P = \frac{RT}{v - b} - \frac{a(T)}{v(v + b) + b(v - b)}
$$

where:
- $P$ = pressure
- $T$ = temperature
- $v$ = molar volume
- $R$ = universal gas constant
- $a(T)$ = temperature-dependent energy parameter
- $b$ = co-volume parameter

### Modified Alpha Function for Water

The key innovation is a modified alpha function $\alpha(T_r, c_s)$ for the water component that incorporates salinity:

$$
\alpha = A^2
$$

where:

$$
A(T_r, c_s) = 1.0 + 0.453 \left[ 1.0 - T_r \left( 1.0 - 0.0103 \cdot c_s^{1.1} \right) \right] + 0.0034 \left( T_r^{-3} - 1.0 \right)
$$

and:
- $T_r = T / T_c$ is the reduced temperature (where $T_c$ is the critical temperature of water)
- $c_s$ is the salinity expressed as an equivalent NaCl molality (mol/kg H₂O)

### Temperature Derivative

The first derivative of the alpha function with respect to temperature is:

$$
\frac{\partial \alpha}{\partial T} = 2A \cdot \frac{\partial A}{\partial T_r} \cdot \frac{1}{T_c}
$$

where:

$$
\frac{\partial A}{\partial T_r} = -0.453 \left( 1.0 - 0.0103 \cdot c_s^{1.1} \right) - 3 \times 0.0034 \cdot T_r^{-4}
$$

### Second Temperature Derivative

$$
\frac{\partial^2 \alpha}{\partial T^2} = 2 \left( \frac{\partial A}{\partial T_r} \right)^2 \left( \frac{1}{T_c} \right)^2 + 2A \cdot \frac{\partial^2 A}{\partial T_r^2} \cdot \left( \frac{1}{T_c} \right)^2
$$

where:

$$
\frac{\partial^2 A}{\partial T_r^2} = 12 \times 0.0034 \cdot T_r^{-5}
$$

### Salinity Effect

The salinity parameter $c_s$ appears only in the water alpha function. For pure water ($c_s = 0$), the model reduces to the standard PR-1978 alpha function with a slight modification for improved water vapor pressure prediction.

The effect of salinity on the alpha function:
- **Increases** the attractive parameter for water at given conditions
- **Reduces** water's tendency to dissolve gases (salting-out)
- The effect is approximately exponential: `solubility_reduction ≈ exp(-K_s × c_s)`

### Chabab 2019 aqueous CO₂-water parameterization

For CO₂ in NaCl brine, Chabab et al. (2019) proposed the following aqueous-phase
binary-interaction parameter:

$$
k_{CO_2,w}^{AQ} = T_r(a+bT_r+cT_rm_s)+m_s^2(d+eT_r)+f
$$

where $T_r=T/304.13$, $T$ is in K, and $m_s$ is NaCl molality in mol/kg H₂O. The
published coefficients are:

| Coefficient | Value |
|-------------|------:|
| $a$ | 0.43575155 |
| $b$ | -0.05766906744 |
| $c$ | 0.00826464849 |
| $d$ | 0.00129539193 |
| $e$ | -0.0016698848 |
| $f$ | -0.47866096 |

This option changes only the aqueous CO₂-water interaction. Other gas-water correlations and
the non-aqueous phase retain the existing NeqSim behavior.

### Burgoyne-Nielsen 2026 drop-in parameterization

`BURGOYNE_NIELSEN_2026` uses the authors' drop-in track: the original Søreide-Whitson water
alpha is retained, freshwater BIPs are refreshed, and salinity is embedded as

$$
k_{ij}^{AQ}(T,m)=k_{ij,fw}(T)+(a_0+a_1T_r+a_2T_r^2)m+(b_0+b_1T_r)m^2.
$$

The quadratic molality term is used only for CO₂. The option applies to water paired with CO₂,
H₂S, methane, nitrogen, hydrogen, ethane, propane, or n-butane. It also applies the published
non-aqueous constants for these pairs, including the revised H₂S value of 0.161, the new H₂
value of 0.468, and the corrected Søreide-Whitson Table 5 values of 0.5525 for propane and
0.5091 for n-butane. Unsupported pairs continue to use their existing NeqSim BIP.

The reduced temperatures in these BIP correlations use the fixed critical temperatures from
the regression source. NeqSim's EOS pure-component properties are not mutated. This preserves
the shared component database, but absolute reproduction of the paper's solubility MARE can be
affected by property differences, especially the H₂S acentric factor and the n-butane acentric
factor. Validate those gases against representative project data before production use.

---

## Implementation in NeqSim

### Class Structure

```
neqsim.thermo.system.SystemSoreideWhitson
├── extends SystemPrEos1978
├── uses PhaseSoreideWhitson
├── uses ComponentSoreideWhitson
└── uses AttractiveTermSoreideWhitson
```

### System Class

The `SystemSoreideWhitson` class provides:

```java
// Create a Søreide-Whitson fluid system
SystemSoreideWhitson fluid = new SystemSoreideWhitson(T_kelvin, P_bara);

// Add components
fluid.addComponent("water", 0.95);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("methane", 0.03);

// Add 0.5 mol/s NaCl-equivalent salt to the system
fluid.addSalinity("NaCl", 0.5, "mole/sec");

// Keep LEGACY (default), or explicitly select a newer parameterization
fluid.setSoreideWhitsonParameterization("BURGOYNE_NIELSEN_2026");
```

### Salinity Methods

| Method | Description |
|--------|-------------|
| `setSalinity(value, unit)` | Set total salinity (mole/sec or mole/hr) |
| `addSalinity(value, unit)` | Add to existing salinity |
| `addSalinity(saltType, value, unit)` | Add specific salt type with conversion factor |
| `getSalinity()` | Get current salinity in mole/sec |

`addSalinity` and `setSalinity` store a salt amount flow in mol/s (or mol/h after conversion),
not a concentration. During a Søreide-Whitson TP flash, NeqSim divides this salt amount by the
total aqueous-phase mass flow, used as the model's water-mass basis, to obtain the working
concentration in mol/kg H₂O. With exactly 1 kg/s water, 3.01 mol/s NaCl represents an initial
3.01 mol/kg H₂O basis; the flashed concentration can differ slightly as CO₂ dissolves.

### Parameterization selector

The selector is available through enum and string overloads. Both are directly callable through
JPype:

```java
import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;

fluid.setSoreideWhitsonParameterization(SoreideWhitsonParameterization.BURGOYNE_NIELSEN_2026);
// Equivalent Python/JPype-friendly form:
fluid.setSoreideWhitsonParameterization("BN_2026");
```

```python
modified_brine = jneqsim.thermo.system.SystemSoreideWhitson(342.82, 100.910)
modified_brine.setSoreideWhitsonParameterization("BURGOYNE_NIELSEN_2026")
```

Supported values are `LEGACY`, `CHABAB_2019`, and `BURGOYNE_NIELSEN_2026`. Aliases `M_SW` and
`m-sw` select Chabab; `BN_2026` and `bn-2026` select Burgoyne-Nielsen. The selector is copied
with the thermodynamic system. The historical `setAqueousCO2Parameterization(...)` and
`getAqueousCO2Parameterization()` methods remain supported for source compatibility.

### Salt Type Conversions

When adding specific salts, the model converts to equivalent NaCl molality using empirically-determined factors:

| Salt | Formula | Conversion Factor | Notes |
|------|---------|-------------------|-------|
| Sodium chloride | NaCl | 1.0 | Reference salt |
| Sodium sulfate | Na₂SO₄ | 3.0 | High salting-out effect |
| Magnesium sulfate | MgSO₄ | 2.75 | Divalent ions |
| Magnesium nitrate | Mg(NO₃)₂ | 1.3 | Moderate effect |
| Sodium nitrate | NaNO₃ | 0.6 | Lower effect |
| Potassium chloride | KCl | 0.5 | Larger cation |
| Potassium nitrate | KNO₃ | 0.3 | Lowest effect |

---

## Application: Produced Water Emissions

### NeqSimLive Integration

The Søreide-Whitson model is the primary thermodynamic model used in **NeqSimLive** for calculating emissions from produced water on offshore installations. The application includes:

1. **Real-time emission monitoring** - Continuous calculation of CH₄, CO₂, and nmVOC emissions
2. **Regulatory compliance** - Emissions reporting per Aktivitetsforskriften §70
3. **Virtual metering** - Calculation of emission rates when direct measurement is impractical

### Produced Water Degassing Process

```
┌─────────────────────────────────────────────────────────────────────┐
│                 PRODUCED WATER TREATMENT SYSTEM                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   HP Separator    Degasser        CFU          Caisson      Sea     │
│   (30+ bara)  →  (2-4 bara)  →  (1.1 bara) →  (1.0 bara) → Discharge│
│       │             │              │              │                  │
│       ▼             ▼              ▼              ▼                  │
│   [Dissolved    [Flash gas    [Flash gas    [Final               │
│    gases in      to flare]     to flare]     venting]              │
│    water]                                                            │
│                                                                      │
│   Søreide-Whitson calculates gas solubility at each stage           │
│   accounting for:                                                    │
│   • Formation water salinity (20,000-200,000 ppm TDS)              │
│   • Temperature (40-90°C typical)                                   │
│   • Pressure drop at each stage                                     │
│   • Gas composition (CH₄, CO₂, H₂S, C₂+)                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Why Salinity Matters for Emissions

Formation water salinity on the Norwegian Continental Shelf typically ranges from 20,000 to 200,000 ppm TDS (Total Dissolved Solids). This significantly affects emissions:

| Salinity (ppm TDS) | CH₄ Solubility Reduction | Impact on Emissions |
|--------------------|--------------------------|---------------------|
| 0 (fresh water) | 0% (baseline) | Overestimates dissolved gas |
| 35,000 (seawater) | ~15-20% | Moderate correction |
| 100,000 | ~35-45% | Significant correction |
| 200,000 | ~55-65% | Major correction required |

**Key insight**: Using pure water properties instead of Søreide-Whitson would:
- **Overestimate** dissolved gas content in produced water
- **Underestimate** gas flashed during treatment (already released)
- Lead to **incorrect emission allocations** between process stages

---

## Salt Type Coefficients

### Equivalent NaCl Approach

The Søreide-Whitson model uses equivalent NaCl molality for simplicity. For mixed-salt brines, individual salt contributions are converted using the Sechenov (Setschenow) salting-out coefficients:

$$
\log \frac{S_0}{S} = K_s \cdot m
$$

where:
- $S_0$ = gas solubility in pure water
- $S$ = gas solubility in salt solution
- $K_s$ = Sechenov coefficient
- $m$ = salt molality

### Literature Sechenov Coefficients

For methane in various electrolytes at 25°C (Clever & Holland, 1968; Duan & Mao, 2006):

| Electrolyte | $K_s$ (L/mol) | Relative to NaCl |
|-------------|---------------|------------------|
| NaCl | 0.122 | 1.00 |
| KCl | 0.083 | 0.68 |
| CaCl₂ | 0.158 | 1.30 |
| MgCl₂ | 0.149 | 1.22 |
| Na₂SO₄ | 0.210 | 1.72 |
| NaBr | 0.110 | 0.90 |

### Temperature Dependency

Sechenov coefficients generally decrease with temperature:

$$
K_s(T) = K_s^{298} + \beta (T - 298)
$$

where $\beta \approx -0.0003$ K⁻¹ for most salts.

---

## Usage Examples

### Java Example: Basic Usage

```java
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create Søreide-Whitson system at reservoir conditions
SystemSoreideWhitson fluid = new SystemSoreideWhitson(353.15, 100.0);  // 80°C, 100 bara

// Add components (typical produced water)
fluid.addComponent("water", 0.95);
fluid.addComponent("methane", 0.03);
fluid.addComponent("CO2", 0.015);
fluid.addComponent("ethane", 0.005);

// Set formation water salinity (100,000 ppm NaCl equivalent)
fluid.setSalinity(100000.0 / 58440.0, "mole/sec");  // Convert ppm to molality

// Or add specific salts
// fluid.addSalinity("NaCl", 1.5, "mole/sec");
// fluid.addSalinity("CaCl2", 0.1, "mole/sec");

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Results
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
double aqueousMethaneMolePpm =
    fluid.getPhase("aqueous").getComponent("methane").getx() * 1.0e6;
```

### Java Example: legacy and Chabab 2019 comparison

The runnable
[`SoreideWhitsonChababComparison`](../../examples/neqsim/thermo/SoreideWhitsonChababComparison.java)
uses 1 kg/s water as the reference basis, flashes each Table 2 condition twice, and reports the
experimental, legacy, and `CHABAB_2019` aqueous CO₂ mole fractions side by side. The essential
selection is to call `setAqueousCO2Parameterization("LEGACY")` or
`setAqueousCO2Parameterization("CHABAB_2019")` before the flash.

Selected source values used by the example and regression test are:

| NaCl molality (mol/kg H₂O) | T (K) | P (bara) | Experiment $x_{CO_2}$ | Legacy NeqSim 3.16.0 | Published m-SW |
|---:|---:|---:|---:|---:|---:|
| 1.13 | 323.02 | 53.450 | 0.01030 | 0.010872 | 0.01088 |
| 1.13 | 323.03 | 100.350 | 0.01510 | 0.016205 | 0.01591 |
| 3.01 | 342.82 | 30.391 | 0.00441 | 0.003586 | 0.00405 |
| 3.01 | 342.82 | 100.910 | 0.01057 | 0.009160 | 0.01021 |

The published m-SW column is a reference result from Chabab et al.; the runnable example calculates
the NeqSim result from the selected correlation rather than inserting those values.

### Python Example: Emission Calculation

```python
from neqsim import jneqsim

# Access classes
SystemSoreideWhitson = jneqsim.thermo.system.SystemSoreideWhitson
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

# Create produced water fluid
produced_water = SystemSoreideWhitson(273.15 + 80.0, 30.0)  # 80°C, 30 bara

# Typical produced water composition
produced_water.addComponent("water", 0.92)
produced_water.addComponent("methane", 0.05)
produced_water.addComponent("CO2", 0.02)
produced_water.addComponent("ethane", 0.008)
produced_water.addComponent("propane", 0.002)

# Set North Sea formation water salinity (~80,000 ppm TDS)
produced_water.addSalinity("NaCl", 1.2, "mole/sec")  # Dominant salt
produced_water.addSalinity("CaCl2", 0.08, "mole/sec")
produced_water.addSalinity("MgCl2", 0.03, "mole/sec")

# Initial flash at separator conditions
ops = ThermodynamicOperations(produced_water)
ops.TPflash()
produced_water.initProperties()

print(f"Initial conditions: {produced_water.getTemperature()-273.15:.1f}°C, {produced_water.getPressure():.1f} bara")
print(f"Salinity: {produced_water.getSalinity():.3f} mol/sec")

# Check dissolved gas content
aq_phase = produced_water.getPhase("aqueous")
print(f"\nDissolved in aqueous phase:")
for comp in ["methane", "CO2", "ethane"]:
    x_ppm = aq_phase.getComponent(comp).getx() * 1e6
    print(f"  {comp}: {x_ppm:.1f} ppm mol")
```

### Python Example: Multi-Stage Degassing Simulation

```python
from neqsim import jneqsim

# Classes
SystemSoreideWhitson = jneqsim.thermo.system.SystemSoreideWhitson
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Separator = jneqsim.process.equipment.separator.Separator
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create produced water (Søreide-Whitson for salinity effects)
pw_fluid = SystemSoreideWhitson(273.15 + 75.0, 35.0)
pw_fluid.addComponent("water", 0.90)
pw_fluid.addComponent("methane", 0.06)
pw_fluid.addComponent("CO2", 0.025)
pw_fluid.addComponent("ethane", 0.01)
pw_fluid.addComponent("propane", 0.005)

# Formation water salinity
pw_fluid.addSalinity("NaCl", 1.0, "mole/sec")

# Build process
process = ProcessSystem()

# Inlet stream
inlet = Stream("PW Inlet", pw_fluid)
inlet.setFlowRate(50000.0, "kg/hr")
process.add(inlet)

# Stage 1: Degasser (35 → 4 bara)
valve1 = ThrottlingValve("V-Degasser", inlet)
valve1.setOutletPressure(4.0, "bara")
process.add(valve1)

degasser = Separator("Degasser", valve1.getOutletStream())
process.add(degasser)

# Stage 2: CFU (4 → 1.1 bara)  
valve2 = ThrottlingValve("V-CFU", degasser.getLiquidOutStream())
valve2.setOutletPressure(1.1, "bara")
process.add(valve2)

cfu = Separator("CFU", valve2.getOutletStream())
process.add(cfu)

# Run simulation
process.run()

# Calculate emissions from each stage
print("\n=== Produced Water Degassing Emissions ===")
print("(Using Søreide-Whitson model for salinity correction)\n")

stages = [("Degasser", degasser), ("CFU", cfu)]
total_ch4 = 0.0
total_co2 = 0.0

for name, sep in stages:
    gas = sep.getGasOutStream()
    gas_rate = gas.getFlowRate("kg/hr")
    
    # Get component mass fractions
    ch4_frac = gas.getFluid().getPhase(0).getComponent("methane").getx() * \
               gas.getFluid().getPhase(0).getComponent("methane").getMolarMass() / \
               gas.getFluid().getPhase(0).getMolarMass()
    co2_frac = gas.getFluid().getPhase(0).getComponent("CO2").getx() * \
               gas.getFluid().getPhase(0).getComponent("CO2").getMolarMass() / \
               gas.getFluid().getPhase(0).getMolarMass()
    
    ch4_rate = gas_rate * ch4_frac
    co2_rate = gas_rate * co2_frac
    
    total_ch4 += ch4_rate
    total_co2 += co2_rate
    
    print(f"{name}:")
    print(f"  Flash gas rate: {gas_rate:.1f} kg/hr")
    print(f"  CH4: {ch4_rate:.2f} kg/hr ({ch4_rate * 8.76:.1f} tonnes/year)")
    print(f"  CO2: {co2_rate:.2f} kg/hr ({co2_rate * 8.76:.1f} tonnes/year)")
    print()

# CO2 equivalents (GWP-100: CH4=28, CO2=1)
co2eq_ch4 = total_ch4 * 28 * 8.76 / 1000  # tonnes CO2eq/year
co2eq_co2 = total_co2 * 8.76 / 1000       # tonnes CO2eq/year

print(f"Total Annual Emissions:")
print(f"  CH4: {total_ch4 * 8.76:.1f} tonnes/year")
print(f"  CO2: {total_co2 * 8.76:.1f} tonnes/year")
print(f"  CO2 equivalents: {co2eq_ch4 + co2eq_co2:.1f} tonnes CO2eq/year")
```

---

## Validation and Accuracy

### Comparison with Experimental Data

For the seven selected Chabab et al. Table 2 CO₂-brine points used by the regression test, the
legacy NeqSim 3.16.0 implementation has an average absolute relative deviation of 6.80% at
1.13 mol/kg H₂O and 15.95% at 3.01 mol/kg H₂O. The paper's m-SW results have corresponding
deviations of 5.39% and 5.79%. The `CHABAB_2019` regression verifies the published equation
coefficients directly, preserves the legacy values when that option is selected, covers every one
of these low- and high-molality points, and requires improved high-salinity agreement.

For `BURGOYNE_NIELSEN_2026`, focused regression tests reproduce 24 aqueous BIP values generated
by the authors' drop-in implementation: all eight supported gases at 280 K/freshwater,
320 K/2 mol/kg NaCl, and 400 K/4 mol/kg NaCl. Analytical first and second temperature derivatives
are checked against centered finite differences. The published source reports freshwater
solubility MARE improvements for seven gases and mean deviations below 2% for its embedded
salinity approximation; those are source claims, not a NeqSim-wide reproduction of the full
experimental dataset.

### Validity range

The measurements reported by Chabab et al. cover approximately 1-3 mol/kg NaCl, 323-373 K, and
pressures up to 230 bar. Use `CHABAB_2019` inside this range when traceable accuracy is required.
The authors also compared the fitted model with a broader literature database, including higher
molalities, but calculations outside the measured range are extrapolations and should be checked
against representative data.

The Burgoyne-Nielsen freshwater correlations were fitted at temperatures up to 200 °C. The
embedded salinity correlations approximate the authors' recommended gas-specific Sechenov
models. No runtime clipping is applied, so higher temperatures, extreme molality, mixtures near
critical conditions, and component-property differences require independent validation.

### Limitations

1. **High salinity**: Validate concentrations outside the selected parameterization's source data
2. **Mixed salts**: Simplified conversion factors for non-NaCl salts
3. **Pressure and temperature**: `CHABAB_2019` extrapolates outside approximately 323-373 K and 230 bar;
   `BURGOYNE_NIELSEN_2026` extrapolates above 473.15 K
4. **Near-critical region**: Phase-specific interaction parameters are not thermodynamically
   consistent near mixture critical points
5. **Static salinity derivatives**: The current implementation does not include molality
   composition derivatives in the attractive-term derivatives
6. **Property lineage**: The 2026 BIP fit used a specified Søreide-Whitson property set. NeqSim
   keeps its shared EOS component properties, so reproduce the full source MARE before claiming
   dataset-level parity

### Alternative Models

For systems requiring higher accuracy or outside the Søreide-Whitson validity range:

| Model | When to Use |
|-------|-------------|
| Duan-Sun (2003, 2006) | CO₂-brine systems, very high P |
| Pitzer | High ionic strength, mixed salts |
| Electrolyte-CPA | Full electrolyte thermodynamics |
| SAFT-VRE | Complex aqueous systems |

---

## Literature References

### Original Publication

1. **Søreide, I. & Whitson, C.H. (1992)**
   - "Peng-Robinson predictions for hydrocarbons, CO₂, N₂, and H₂S with pure water and NaCl brine"
   - *Fluid Phase Equilibria*, 77, 217-240
   - DOI: [10.1016/0378-3812(92)85105-H](https://doi.org/10.1016/0378-3812(92)85105-H)
   - **The foundational paper for this model**

### Related Work by the Authors

2. **Whitson, C.H. & Brulé, M.R. (2000)**
   - "Phase Behavior" (SPE Monograph Volume 20)
   - Society of Petroleum Engineers
   - ISBN: 978-1-55563-087-4
   - Comprehensive reference for petroleum thermodynamics

3. **Søreide, I. (1989)**
   - "Improved Phase Behavior Predictions of Petroleum Reservoir Fluids from a Cubic Equation of State"
   - Dr.Ing. Thesis, Norwegian Institute of Technology (NTH/NTNU)
   - Original development of the water alpha function

### Gas Solubility in Brines

4. **Chabab, S., Théveneau, P., Corvisier, J., Coquelet, C., Paricaud, P., Houriez, C. & El Ahmar, E. (2019)**
   - "Thermodynamic study of the CO₂-H₂O-NaCl system: Measurements of CO₂ solubility and modeling of phase equilibria using Soreide and Whitson, electrolyte CPA and SIT models"
   - *International Journal of Greenhouse Gas Control*, 91, 102825
   - DOI: [10.1016/j.ijggc.2019.102825](https://doi.org/10.1016/j.ijggc.2019.102825)
   - Source of the optional `CHABAB_2019` aqueous CO₂-water correlation

5. **Burgoyne, M. & Nielsen, M.H. (2026)**
   - "Refreshed Søreide-Whitson framework for gas solubility in water and brine with extension to hydrogen"
   - *Fluid Phase Equilibria*, 114824
   - DOI: [10.1016/j.fluid.2026.114824](https://doi.org/10.1016/j.fluid.2026.114824)
   - [Reproducibility repository and errata](https://github.com/mwburgoyne/SW_Framework_Refresh)
   - Source of the optional `BURGOYNE_NIELSEN_2026` parameter set

6. **Duan, Z. & Sun, R. (2003)**
   - "An improved model calculating CO₂ solubility in pure water and aqueous NaCl solutions"
   - *Chemical Geology*, 193(3-4), 257-271
   - DOI: [10.1016/S0009-2541(02)00263-2](https://doi.org/10.1016/S0009-2541(02)00263-2)

7. **Duan, Z. & Mao, S. (2006)**
   - "A thermodynamic model for calculating methane solubility, density and gas phase composition of methane-bearing aqueous fluids from 273 to 523 K and from 1 to 2000 bar"
   - *Geochimica et Cosmochimica Acta*, 70(13), 3369-3386
   - DOI: [10.1016/j.gca.2006.03.018](https://doi.org/10.1016/j.gca.2006.03.018)

8. **Clever, H.L. & Holland, C.J. (1968)**
   - "Solubility of Argon Gas in Aqueous Alkali Halide Solutions"
   - *Journal of Chemical & Engineering Data*, 13(3), 411-414
   - Classic source for Sechenov coefficients

### Salting-Out Theory

9. **Sechenov, M. (1889)**
   - "Über die Konstitution der Salzlösungen auf Grund ihres Verhaltens zu Kohlensäure"
   - *Zeitschrift für Physikalische Chemie*, 4, 117-125
   - The original salting-out coefficient concept

10. **Schumpe, A. (1993)**
   - "The estimation of gas solubilities in salt solutions"
   - *Chemical Engineering Science*, 48(1), 153-158
   - DOI: [10.1016/0009-2509(93)80291-W](https://doi.org/10.1016/0009-2509(93)80291-W)

### Produced Water and Emissions

11. **IOGP Report 521 (2019)**
   - "Methods for estimating atmospheric emissions from E&P operations"
   - International Association of Oil & Gas Producers
   - Industry standard for emission calculations

11. **OLF/Norsk olje og gass (2012)**
    - "Recommended Guidelines for the Discharge and Emission Reporting"
    - Norwegian Oil and Gas Association
    - NCS-specific reporting requirements

### Thermodynamic Modeling

12. **Kontogeorgis, G.M. & Folas, G.K. (2010)**
    - "Thermodynamic Models for Industrial Applications: From Classical and Advanced Mixing Rules to Association Theories"
    - John Wiley & Sons
    - ISBN: 978-0-470-69726-9
    - Comprehensive textbook covering EoS models

13. **Michelsen, M.L. & Mollerup, J.M. (2007)**
    - "Thermodynamic Models: Fundamentals & Computational Aspects"
    - Tie-Line Publications
    - ISBN: 87-989961-3-4
    - Mathematical foundations of thermodynamic models

---

## See Also

- [Thermodynamic Models](thermodynamic_models) - Overview of all NeqSim thermodynamic models
- [Electrolyte CPA Model](ElectrolyteCPAModel) - For full electrolyte thermodynamics
- [Offshore Emission Reporting](../emissions/OFFSHORE_EMISSION_REPORTING) - Comprehensive emission calculation guide
- [Fluid Creation Guide](fluid_creation_guide) - Creating fluids in NeqSim
- [Flash Calculations Guide](flash_calculations_guide) - Performing thermodynamic calculations

---

*Last updated: August 2026*
