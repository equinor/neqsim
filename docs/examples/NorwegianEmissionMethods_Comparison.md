---
title: "NeqSim vs Norwegian Handbook: Emission Calculation Methods Comparison"
description: "This document compares the conventional Norwegian handbook method for emission reporting with the NeqSim thermodynamic method implemented via NeqSimLive."
---

# NeqSim vs Norwegian Handbook: Emission Calculation Methods Comparison

> **📖 Related Documentation:**
> - [Produced Water Emissions Tutorial](ProducedWaterEmissions_Tutorial) - Complete implementation guide
> - [Examples Index](index) - All tutorials and examples
> - [REFERENCE_MANUAL_INDEX - Chapter 43](../REFERENCE_MANUAL_INDEX#chapter-43-sustainability--emissions) - Full API documentation

## Overview

This document compares the **conventional Norwegian handbook method** for emission reporting with the **NeqSim thermodynamic method** implemented via NeqSimLive. The comparison is based on the methodology presented in:

> **"Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator"**
> Kristiansen et al., Global Flow Measurement Workshop (GFMW), October 2023
> *Paper presented at GFMW 2023 conference*

---

## Norwegian Continental Shelf Emissions Context

### Regulatory Framework

The Norwegian Continental Shelf (NCS) is one of the world's most strictly regulated offshore petroleum provinces:

| Metric | NCS Statistics (2024) |
|--------|----------------------|
| Total GHG emissions | ~10.9 million tonnes CO₂eq |
| Share of Norway's total GHG | ~25% |
| nmVOC emissions | ~21,500 tonnes |
| Carbon tax + EU ETS cost | ~NOK 1,565/tonne CO₂ |
| Total annual emission cost | ~NOK 16 billion |

*Source: [Norwegian Petroleum - Emissions to Air](https://www.norskpetroleum.no/en/environment-and-technology/emissions-to-air/)*

### Key Regulations

1. **Aktivitetsforskriften §70** (Activities Regulations): Operators must measure or calculate emissions with quality-assured, representative methods
2. **Norwegian Offshore Emission Handbook**: Defines conventional calculation factors
3. **CO₂ Tax Act on Petroleum Activities**: Carbon pricing mechanism
4. **EU ETS**: Emissions trading system participation

---

## Method Comparison

### 1. Norwegian Handbook Method (Conventional)

**Reference:** Håndbok for kvantifisering av direkte metan- og nmVOC-utslipp (Norwegian Environment Agency, Retningslinje 044, ver. 2.2)

#### Formulas

```
U_CH4 = f_CH4 × V_pw × ΔP × 10⁻⁶  [tonnes/year]
U_NMVOC = f_NMVOC × V_pw × ΔP × 10⁻⁶  [tonnes/year]
```

#### Parameters

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| Methane solubility factor | f_CH4 | 14 | g/(m³·bar) |
| nmVOC solubility factor | f_NMVOC | 3.5 | g/(m³·bar) |
| Produced water volume | V_pw | varies | m³/year |
| Pressure drop | ΔP | varies | bar |

#### Limitations

| Issue | Impact |
|-------|--------|
| **CO₂ not included** | Misses 72-78% of total gas emissions |
| **Fixed factors** | Cannot reflect real process conditions |
| **No temperature dependence** | Same factor for 50°C and 100°C |
| **No salinity correction** | Same factor for fresh and saline water |
| **No composition dependence** | Same factor regardless of gas composition |
| **Uncertainty** | Estimated ±50% or higher |

### 2. NeqSim Thermodynamic Method

#### Thermodynamic Model

**Equation of State:** SRK-CPA (Cubic Plus Association)

The CPA-EoS extends the traditional cubic equation of state with an association term for polar molecules:

```
P = P_physical + P_association
```

Where:
- P_physical: SRK equation for non-polar interactions
- P_association: Wertheim's perturbation theory for hydrogen bonding (water, glycols)

#### Mixing Rule

Uses mixing rule 10 with electrolyte correction for saline produced water (NaCl content).

#### Binary Interaction Parameters (kij)

Tuned parameters from laboratory validation (Kristiansen et al., 2023):

| System | kij Formula | Temperature Range |
|--------|-------------|-------------------|
| Water-CO₂ | kij = -0.24 + 0.001121 × T(°C) | 50-100°C |
| Water-CH₄ | kij = -0.72 + 0.002605 × T(°C) | 50-100°C |
| Water-C₂H₆ | kij = 0.11 (fixed) | All |
| Water-C₃H₈ | kij = 0.205 (fixed) | All |

#### Implementation in NeqSim

```java
// Create system with CPA-EoS
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(273.15 + 80, 65.0);
fluid.addComponent("water", 0.85);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("methane", 0.08);
fluid.addComponent("ethane", 0.02);
fluid.addComponent("propane", 0.01);
fluid.addComponent("n-butane", 0.005);
fluid.addComponent("Na+", 0.002);
fluid.addComponent("Cl-", 0.003);

// CPA mixing rule for water-hydrocarbon systems
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);
```

#### Advantages

| Feature | Benefit |
|---------|---------|
| **Includes CO₂** | Captures all emission components |
| **Process conditions** | Reflects actual P, T variations |
| **Salinity effects** | "Salting-out" reduces gas solubility |
| **Composition-based** | Uses actual well stream PVT data |
| **Real-time capable** | Live connection to process data |
| **Validated uncertainty** | ±3.6% total gas, ±7.4% methane |

---

## Validation Results (Gudrun Field)

### Case Study Parameters

| Parameter | Value |
|-----------|-------|
| Field | Gudrun (North Sea) |
| Water salinity | 10-11 wt% NaCl |
| Temperature | 75-90°C |
| Separator pressure | 65 bara typical |
| Degasser pressure | 3-5 barg |
| CFU pressure | 0.2-1 barg |
| Validation period | 2020-2023 |

### Comparison with Field Measurements

| Metric | NeqSim vs Measurement |
|--------|----------------------|
| GWR (Gas-Water Ratio) | -1% to +4% |
| CO₂ composition | ±1% |
| CH₄ composition | ±1% |
| Total gas mass rate | -2% to -7.2% (annual cumulative) |
| Gas molar mass | Good agreement with USM |
| Gas density | Good agreement with USM |

### Emission Comparison (2022 Data)

| Method | CH₄ + nmVOC | CO₂ | Total Gas | CO₂ Equivalents |
|--------|-------------|-----|-----------|-----------------|
| Conventional Handbook | 100% | 0% | Higher | 11,000 tonnes |
| NeqSimLive | 22-28% | 72-78% | Accurate | 4,700 tonnes |
| **Reduction** | - | - | - | **-58%** |

### Key Finding: CO₂ Dominates Emissions

The NeqSimLive data revealed that **72-78% of emissions are CO₂**, not hydrocarbons as assumed by the conventional method. This fundamentally changes emission reporting:

```
Conventional: All emissions = CH₄ + nmVOC (high GWP)
Reality:      Most emissions = CO₂ (lower GWP)
```

### Solubility Factor Comparison

| Component | Handbook Factor | NeqSim Calculated | Difference |
|-----------|-----------------|-------------------|------------|
| Methane | 14 g/(m³·bar) | 5-6 g/(m³·bar) | **-60%** |
| nmVOC | 3.5 g/(m³·bar) | 1.2-1.4 g/(m³·bar) | **-65%** |
| CO₂ | Not included | 15-30 g/(m³·bar) | **Missing!** |

---

## Implementation Complexity Levels

NeqSim supports various implementation levels depending on process complexity:

### Level 1: Simple Calculator

For basic flash calculations without full process modeling:

```python
from neqsim import jNeqSim

# Quick emission estimate from single flash
calc = jNeqSim.process.equipment.util.EmissionsCalculator
ch4 = calc.calculateConventionalCH4(water_volume_m3, pressure_drop_bar)

# Or use thermodynamic flash
fluid = jNeqSim.thermo.system.SystemSrkCPAstatoil(273.15 + 80, 4.0)
# ... configure and flash
```

### Level 2: Multi-Stage Degassing Model

For typical produced water treatment trains:

```java
ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Platform PW");
system.setWaterFlowRate(100.0, "m3/hr");
system.setDegasserPressure(4.0, "bara");
system.setCFUPressure(1.2, "bara");
system.setDissolvedGasComposition(composition);
system.run();

// Get comparison report
System.out.println(system.getMethodComparisonReport());
```

### Level 3: Full Process Plant Model

For complex processes like TEG dehydration with emission tracking:

```java
ProcessSystem process = new ProcessSystem();

// Add all unit operations
Stream feed = new Stream("Feed", feedFluid);
Separator separator = new Separator("HP Sep", feed);
TEGAbsorber absorber = new TEGAbsorber("Contactor");
TEGRegeneration regen = new TEGRegeneration("Regenerator");
// ... configure full process

process.add(feed);
process.add(separator);
process.add(absorber);
process.add(regen);
process.run();

// Track emissions from each source
EmissionsCalculator sepEmissions = new EmissionsCalculator(separator.getGasOutStream());
EmissionsCalculator flashEmissions = new EmissionsCalculator(regen.getStillColumn().getGasOut());
```

### Level 4: NeqSimLive (Real-Time Cloud API)

For production operations with live data integration:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   PI/Aspen      │────▶│   SIGMA         │────▶│   NeqSimAPI     │
│   (Field Data)  │◀────│   (Scheduler)   │◀────│   (Cloud)       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                                                        ▼
                                                ┌─────────────────┐
                                                │   NeqSim        │
                                                │   (Calculation) │
                                                └─────────────────┘
                                                        │
                        ┌───────────────────────────────┤
                        ▼                               ▼
                ┌─────────────────┐             ┌─────────────────┐
                │  Emisoft        │             │  MPRML          │
                │  (Env. Agency)  │             │  (Tax/NPD)      │
                └─────────────────┘             └─────────────────┘
```

Key features:
- Runs in Microsoft Azure cloud
- 5-15 minute calculation intervals
- Automatic emission reporting to authorities
- Supports multiple facilities from single API

---

## Calibration Requirements

### Recommended Calibration Frequency

| Condition | Frequency |
|-----------|-----------|
| Normal operations | 2 samples/year |
| Well composition change | Immediate recalibration |
| Back-production of injection water | Immediate recalibration |
| New wells online | Reassess within 1 month |

### Required Measurements for Calibration

1. **Pressurized water sample** from separator outlet
2. **Single-stage flash** to atmospheric conditions (15°C, 1 atm)
3. **Gas chromatography** for composition
4. **GWR measurement** (Sm³ gas / Sm³ water)
5. **Water salinity** analysis (NaCl content)

### Validation Criteria

Per Norwegian offshore emission handbook, acceptable uncertainty is **±7.5%** for emission gases.

NeqSim achieves:
- Total gas: ±3.6%
- CO₂: ±3.6%
- Methane: ±7.4%
- nmVOC: ±38% (higher due to small quantities)

---

## References

1. Kristiansen, O., et al. (2023). "Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator." Global Flow Measurement Workshop.

2. Petroleum Safety Authority Norway. "Activities Regulations, Chapter XI - Emissions and discharges to the external environment, §70 Measurement and calculation." https://www.ptil.no/en/regulations/

3. Norwegian Environment Agency. "Håndbok for kvantifisering av direkte metan- og nmVOC-utslipp" (Retningslinje 044).

4. Søreide, I. & Whitson, C.H. (1992). "Peng-Robinson predictions for hydrocarbons, CO₂, N₂ and H₂S with pure water and NaCl brine." Fluid Phase Equilibria 77: 217-240.

5. Kontogeorgis, G.M., et al. (2006). "Ten Years with the CPA Equation of State." Ind. Eng. Chem. Res. 45: 4869-4878.

6. Norwegian Petroleum. "Emissions to Air." https://www.norskpetroleum.no/en/environment-and-technology/emissions-to-air/

---

## Conclusion

The NeqSim thermodynamic method provides significant advantages over the conventional Norwegian handbook method:

| Aspect | Handbook | NeqSim |
|--------|----------|--------|
| CO₂ emissions | ❌ Not captured | ✅ Full accounting |
| Process conditions | ❌ Fixed factors | ✅ Real-time P, T, composition |
| Salinity effects | ❌ Ignored | ✅ Electrolyte model |
| Uncertainty | ±50%+ | ±3.6% |
| Automation | ❌ Manual annual | ✅ Live API (NeqSimLive) |
| Regulatory compliance | ✅ Accepted | ✅ More accurate |

**Key outcome from Gudrun:** Reported emissions reduced from **11,000 to 4,700 tonnes CO₂eq** (-58%) by using the more accurate thermodynamic method.

For facilities with significant produced water handling, implementing NeqSim-based emission calculations can:
1. Improve reporting accuracy
2. Identify actual emission sources
3. Support emission reduction initiatives
4. Ensure regulatory compliance with better uncertainty

---

*Document generated from NeqSim emission calculation framework. See [ProducedWaterEmissions_Tutorial.md](ProducedWaterEmissions_Tutorial) for implementation details.*
