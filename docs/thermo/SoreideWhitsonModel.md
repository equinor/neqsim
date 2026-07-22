---
title: "Søreide-Whitson Model for Gas Solubility in Brine"
description: "The Søreide-Whitson model is a modified Peng-Robinson equation of state specifically designed for predicting gas solubility in aqueous systems containing dissolved salts (brine). This model is essent..."
---

# Søreide-Whitson Model for Gas Solubility in Brine

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

// Set salinity
fluid.setSalinity(35000, "ppm");  // or
fluid.addSalinity("NaCl", 0.5, "mole/sec");
```

### Salinity Methods

| Method | Description |
|--------|-------------|
| `setSalinity(value, unit)` | Set total salinity (mole/sec or mole/hr) |
| `addSalinity(value, unit)` | Add to existing salinity |
| `addSalinity(saltType, value, unit)` | Add specific salt type with conversion factor |
| `getSalinity()` | Get current salinity in mole/sec |

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
if (fluid.hasPhaseType("aqueous")) {
    System.out.println("CH4 in water: " + 
        fluid.getPhase("aqueous").getComponent("methane").getx() * 1e6 + " ppm mol");
}
```

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

The Søreide-Whitson model has been validated against extensive experimental data:

| System | T Range (°C) | P Range (bar) | Salinity (molal) | AAD (%) |
|--------|--------------|---------------|------------------|---------|
| CH₄-H₂O-NaCl | 25-200 | 1-1000 | 0-6 | 5-10 |
| CO₂-H₂O-NaCl | 25-250 | 1-700 | 0-6 | 3-8 |
| H₂S-H₂O-NaCl | 25-150 | 1-100 | 0-4 | 5-12 |
| N₂-H₂O-NaCl | 25-150 | 1-500 | 0-4 | 8-15 |

AAD = Average Absolute Deviation

### Limitations

1. **High salinity**: Above ~6 molal NaCl equivalent, accuracy decreases
2. **Mixed salts**: Simplified conversion factors for non-NaCl salts
3. **Very high pressure**: Above 1000 bar, model extrapolates
4. **Near-critical water**: Model not designed for supercritical conditions
5. **Heavy hydrocarbons**: C7+ solubility predictions less accurate

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

4. **Duan, Z. & Sun, R. (2003)**
   - "An improved model calculating CO₂ solubility in pure water and aqueous NaCl solutions"
   - *Chemical Geology*, 193(3-4), 257-271
   - DOI: [10.1016/S0009-2541(02)00263-2](https://doi.org/10.1016/S0009-2541(02)00263-2)

5. **Duan, Z. & Mao, S. (2006)**
   - "A thermodynamic model for calculating methane solubility, density and gas phase composition of methane-bearing aqueous fluids from 273 to 523 K and from 1 to 2000 bar"
   - *Geochimica et Cosmochimica Acta*, 70(13), 3369-3386
   - DOI: [10.1016/j.gca.2006.03.018](https://doi.org/10.1016/j.gca.2006.03.018)

6. **Clever, H.L. & Holland, C.J. (1968)**
   - "Solubility of Argon Gas in Aqueous Alkali Halide Solutions"
   - *Journal of Chemical & Engineering Data*, 13(3), 411-414
   - Classic source for Sechenov coefficients

### Salting-Out Theory

7. **Sechenov, M. (1889)**
   - "Über die Konstitution der Salzlösungen auf Grund ihres Verhaltens zu Kohlensäure"
   - *Zeitschrift für Physikalische Chemie*, 4, 117-125
   - The original salting-out coefficient concept

8. **Schumpe, A. (1993)**
   - "The estimation of gas solubilities in salt solutions"
   - *Chemical Engineering Science*, 48(1), 153-158
   - DOI: [10.1016/0009-2509(93)80291-W](https://doi.org/10.1016/0009-2509(93)80291-W)

### Produced Water and Emissions

9. **IOGP Report 521 (2019)**
   - "Methods for estimating atmospheric emissions from E&P operations"
   - International Association of Oil & Gas Producers
   - Industry standard for emission calculations

10. **OLF/Norsk olje og gass (2012)**
    - "Recommended Guidelines for the Discharge and Emission Reporting"
    - Norwegian Oil and Gas Association
    - NCS-specific reporting requirements

### Thermodynamic Modeling

11. **Kontogeorgis, G.M. & Folas, G.K. (2010)**
    - "Thermodynamic Models for Industrial Applications: From Classical and Advanced Mixing Rules to Association Theories"
    - John Wiley & Sons
    - ISBN: 978-0-470-69726-9
    - Comprehensive textbook covering EoS models

12. **Michelsen, M.L. & Mollerup, J.M. (2007)**
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

*Last updated: February 2026*
