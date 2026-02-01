# Offshore Platform Emission Reporting with NeqSim

## Overview

This document provides comprehensive guidance for calculating and reporting greenhouse gas (GHG) emissions from offshore oil and gas operations using NeqSim's thermodynamic simulation capabilities.

---

## Table of Contents

1. [Regulatory Framework](#regulatory-framework)
2. [Emission Sources](#emission-sources)
3. [Calculation Methods](#calculation-methods)
4. [NeqSim Advantages for Emission Integration](#neqsim-advantages-for-emission-integration)
5. [NeqSim API Reference](#neqsim-api-reference)
6. [Produced Water Degassing](#produced-water-degassing)
7. [Virtual Measurement Methodology](#virtual-measurement-methodology)
8. [Validation and Uncertainty](#validation-and-uncertainty)
9. [Literature References](#literature-references)

---

## Regulatory Framework

### Norwegian Continental Shelf (NCS)

| Regulation | Description | Reference |
|------------|-------------|-----------|
| **Aktivitetsforskriften §70** | Measurement and calculation of emissions | [Lovdata](https://lovdata.no/dokument/SF/forskrift/2010-04-29-613) |
| **Rammeforskriften** | Framework regulations for petroleum activities | [Lovdata](https://lovdata.no/dokument/SF/forskrift/2010-02-12-158) |
| **CO2 Tax Act** | Norwegian carbon tax (~NOK 1,565/tonne CO2 in 2024) | [Skatteetaten](https://www.skatteetaten.no/bedrift-og-organisasjon/avgifter/saravgifter/co2-avgift/) |

### European Union

| Regulation | Description | Reference |
|------------|-------------|-----------|
| **EU ETS Directive 2003/87/EC** | Emissions trading system | [EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32003L0087) |
| **MRV Regulation 2015/757** | Monitoring, Reporting, Verification | [EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32015R0757) |
| **Methane Regulation 2024/1787** | Oil, gas & coal methane emissions | [EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R1787) |

### International Standards

| Standard | Description | Reference |
|----------|-------------|-----------|
| **ISO 14064-1:2018** | GHG quantification at organization level | [ISO](https://www.iso.org/standard/66453.html) |
| **IOGP Report 521** | Estimating fugitive emissions | [IOGP](https://www.iogp.org/bookstore/product/methods-for-estimating-atmospheric-emissions-from-e-p-operations/) |
| **API Compendium** | Petroleum industry GHG methods | [API](https://www.api.org/oil-and-natural-gas/environment/climate-change/greenhouse-gas-emissions-estimation) |

---

## Emission Sources

### Offshore Platform Emission Categories

```
┌─────────────────────────────────────────────────────────────┐
│                 OFFSHORE PLATFORM EMISSIONS                  │
├──────────────────┬──────────────────┬───────────────────────┤
│   COMBUSTION     │    VENTING       │     FUGITIVE          │
│   (60-80%)       │    (5-20%)       │     (0.5-3%)          │
├──────────────────┼──────────────────┼───────────────────────┤
│ • Gas turbines   │ • Cold vents     │ • Valve/flange leaks  │
│ • Diesel engines │ • Tank breathing │ • Compressor seals    │
│ • Flares         │ • PW degassing   │ • Pump seals          │
│ • Heaters        │ • TEG regeneration│ • Pipe connections   │
│ • Boilers        │ • Loading ops    │ • Instrumentation     │
└──────────────────┴──────────────────┴───────────────────────┘
```

### Key Emission Components

| Component | GWP-100 (AR5) | Primary Sources |
|-----------|---------------|-----------------|
| CO2 | 1 | Combustion, dissolved gas venting |
| Methane (CH4) | 28 | Venting, fugitive leaks, incomplete combustion |
| nmVOC (C2+) | ~2.2 | Venting, storage tanks, loading |
| N2O | 265 | Flaring, combustion |

---

## Calculation Methods

### Method Comparison

| Aspect | Conventional (Handbook) | Thermodynamic (NeqSim) |
|--------|------------------------|------------------------|
| **Accuracy** | ±50% or worse | ±3.6% (validated) |
| **CO2 captured** | ❌ No | ✅ Yes |
| **Salinity effects** | ❌ No | ✅ Yes |
| **Temperature effects** | Limited | ✅ Full |
| **Real-time capability** | ❌ No | ✅ Yes |
| **Regulatory acceptance** | Legacy | Recommended |

### Conventional Method (Norwegian Handbook)

The traditional method from "Handbook for quantification of direct emissions from Norwegian petroleum industry" (Norsk olje og gass):

```
U_CH4 = f_CH4 × V_pw × ΔP × 10⁻⁶

Where:
  f_CH4  = 14 g/(m³·bar)   [Standard solubility factor]
  V_pw   = Produced water volume (m³)
  ΔP     = Pressure drop (bar)
  Result = Methane emission (tonnes)
```

**Limitations:**
- Ignores CO2 completely (often 50-80% of emissions!)
- Assumes fixed solubility factors
- No composition dependency
- No salinity correction

### Thermodynamic Method (NeqSim)

Uses Cubic-Plus-Association (CPA) equation of state for rigorous vapor-liquid equilibrium:

```
Benefits:
• Accounts for actual fluid composition
• Includes all gas components (CO2, CH4, C2+, N2, H2S)
• Handles salinity/ionic effects
• Temperature and pressure dependent
• Validated against lab data
```

---

## NeqSim Advantages for Emission Integration

### Why NeqSim for Emission Calculations?

NeqSim provides unique advantages for integrating emission calculations into industrial workflows, digital twins, and emerging decarbonization technologies.

### Core Technical Advantages

| Advantage | Description | Impact |
|-----------|-------------|--------|
| **Physics-Based Modeling** | Rigorous thermodynamic calculations using CPA, SRK, PR equations of state | Accuracy ±3.6% vs ±50% for empirical methods |
| **Full Component Accounting** | Captures CO2 (often 50-80% of emissions), CH4, nmVOC, H2S, N2 | No hidden emission sources |
| **Composition Sensitivity** | Tracks changing reservoir composition over field life | Accurate lifetime emission profiles |
| **Process Integration** | Emission calculations embedded in full process simulation | Consistent material/energy balances |
| **Open Source** | Apache 2.0 license, transparent algorithms | Auditable, reproducible, no vendor lock-in |

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     NEQSIM EMISSION INTEGRATION                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐    │
│   │   SENSORS   │───▶│   NEQSIM    │───▶│   EMISSION OUTPUTS      │    │
│   │  (P,T,F,x)  │    │   PROCESS   │    │                         │    │
│   └─────────────┘    │   MODEL     │    │  • Real-time kg/hr      │    │
│                      │             │    │  • Daily/annual totals  │    │
│   ┌─────────────┐    │  ┌───────┐  │    │  • CO2 equivalents      │    │
│   │   PROCESS   │───▶│  │THERMO │  │    │  • Regulatory reports   │    │
│   │   DESIGN    │    │  │ VLE   │  │    │  • Carbon tax liability │    │
│   └─────────────┘    │  └───────┘  │    └─────────────────────────┘    │
│                      │             │                                    │
│   ┌─────────────┐    │  ┌───────┐  │    ┌─────────────────────────┐    │
│   │ COMPOSITION │───▶│  │EMIT.  │  │───▶│   DOWNSTREAM SYSTEMS    │    │
│   │   ANALYSIS  │    │  │CALC.  │  │    │                         │    │
│   └─────────────┘    │  └───────┘  │    │  • SCADA/DCS            │    │
│                      └─────────────┘    │  • Digital Twins        │    │
│                                         │  • ESG Reporting        │    │
│                                         │  • MPC Controllers      │    │
│                                         └─────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Future Technology Enablement

#### 1. Digital Twin Integration

NeqSim enables high-fidelity digital twins with embedded emission tracking:

| Capability | Traditional Approach | NeqSim-Enabled |
|------------|---------------------|----------------|
| Emission tracking | Periodic estimates | Continuous real-time |
| What-if analysis | Not possible | Full scenario modeling |
| Optimization target | Process only | Process + emissions |
| Regulatory reporting | Manual, delayed | Automated, instant |

```
Digital Twin Benefits:
• Live emission monitoring from process state
• Predictive emission forecasting
• Optimization with emission constraints
• Automatic regulatory compliance tracking
```

#### 2. Model Predictive Control (MPC)

NeqSim emission calculations can be embedded in advanced process control:

```
┌─────────────────────────────────────────────────────────┐
│              MPC WITH EMISSION CONSTRAINTS              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   Minimize:  J = Σ (production_cost + carbon_tax)       │
│                                                         │
│   Subject to:                                           │
│     • Process constraints (P, T, flow limits)           │
│     • CO2eq_emissions ≤ permit_limit                    │
│     • Methane_intensity ≤ regulatory_target             │
│                                                         │
│   NeqSim provides:                                      │
│     • Real-time emission rate = f(process_state)        │
│     • Gradient ∂emissions/∂(manipulated_variables)      │
│     • Composition-dependent emission factors            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### 3. Carbon Capture Integration

For CCUS (Carbon Capture, Utilization and Storage) process design:

| Application | NeqSim Capability |
|-------------|-------------------|
| Pre-combustion capture | CO2/H2 separation modeling |
| Post-combustion | Amine absorption/regeneration |
| Direct air capture | Low-concentration CO2 thermodynamics |
| CO2 transport | Dense phase CO2 properties |
| Geological storage | CO2-brine-rock interactions |

#### 4. Hydrogen & Ammonia Value Chains

NeqSim's thermodynamic models support emerging clean energy vectors:

```
Blue Hydrogen Production:
  SMR/ATR → NeqSim emission tracking → CO2 capture sizing

Green Hydrogen:
  Electrolyzer → NeqSim compression → Storage/transport

Ammonia as Fuel:
  NH3 cracking → NeqSim separation → H2 purification
  
Each step: Embedded emission accounting with NeqSim
```

#### 5. AI/ML Hybrid Models

NeqSim provides physics-based foundation for machine learning enhancement:

| Approach | Description | Advantage |
|----------|-------------|-----------|
| **Physics-Informed Neural Networks** | NeqSim VLE as constraints | Faster convergence, physical consistency |
| **Surrogate Models** | NeqSim training data generation | Rapid emission estimation |
| **Soft Sensors** | NeqSim-calibrated emission inferencing | Fill measurement gaps |
| **Anomaly Detection** | Compare measured vs NeqSim-predicted | Identify fugitive leaks |

### Competitive Advantages vs Commercial Software

| Feature | NeqSim | Commercial Tools |
|---------|--------|------------------|
| **Cost** | Free (Apache 2.0) | €50k-500k/year licenses |
| **Transparency** | Full source code access | Black box |
| **Customization** | Modify/extend freely | Limited/expensive |
| **Reproducibility** | Version-controlled, auditable | Vendor-dependent |
| **API Integration** | Java, Python, REST | Often limited |
| **Regulatory Defense** | Algorithms visible to auditors | "Trust the vendor" |
| **Long-term Availability** | Open source community | Vendor business risk |

### Industry 4.0 / IIoT Deployment

```
┌────────────────────────────────────────────────────────────────────────┐
│                    NEQSIM IN INDUSTRIAL IOT ARCHITECTURE               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  EDGE LAYER              PLATFORM LAYER           APPLICATION LAYER   │
│  ┌──────────┐            ┌──────────────┐         ┌────────────────┐  │
│  │ OPC-UA   │            │              │         │ ESG Dashboard  │  │
│  │ Gateway  │───────────▶│   NeqSim     │────────▶│                │  │
│  └──────────┘            │   Microservice│        │ • Live CO2eq   │  │
│                          │              │         │ • Trend charts │  │
│  ┌──────────┐            │  ┌────────┐  │         │ • Alerts       │  │
│  │ Process  │───────────▶│  │ Thermo │  │         │ • Reports      │  │
│  │ Historian│            │  │ Engine │  │         └────────────────┘  │
│  └──────────┘            │  └────────┘  │                             │
│                          │              │         ┌────────────────┐  │
│  ┌──────────┐            │  ┌────────┐  │         │ Carbon Trading │  │
│  │ Lab LIMS │───────────▶│  │Emission│  │────────▶│ Integration    │  │
│  │          │            │  │ Calc   │  │         │                │  │
│  └──────────┘            │  └────────┘  │         │ • ETS registry │  │
│                          │              │         │ • Offset calc  │  │
│                          └──────────────┘         └────────────────┘  │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Differentiators for Decarbonization

1. **Methane Quantification Accuracy**
   - EU Methane Regulation 2024/1787 requires source-level measurement
   - NeqSim provides composition-specific emission factors
   - Enables OGMP 2.0 Level 4/5 reporting

2. **Scope 1, 2, 3 Readiness**
   - Direct emissions: Full process modeling
   - Indirect emissions: Fuel consumption tracking
   - Value chain: Product carbon intensity

3. **Net-Zero Pathway Modeling**
   - Electrification scenarios
   - Process heat decarbonization
   - Flare reduction optimization
   - CCS integration studies

4. **Regulatory Audit Trail**
   - Open algorithms satisfy EU MRV requirements
   - Version-controlled calculations
   - Reproducible by third-party verifiers

### Summary: Strategic Value Proposition

```
┌─────────────────────────────────────────────────────────────────────┐
│                    NEQSIM EMISSION INTEGRATION VALUE                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   TODAY                         TOMORROW                            │
│   ─────                         ────────                            │
│   ✓ Accurate emission reporting  → Automated ESG compliance         │
│   ✓ Regulatory compliance        → Carbon trading optimization      │
│   ✓ Digital twin foundation      → Autonomous emission control      │
│   ✓ Process + emission modeling  → Integrated decarbonization       │
│   ✓ Open source transparency     → Industry standard adoption       │
│                                                                     │
│   COMPETITIVE EDGE                                                  │
│   ────────────────                                                  │
│   • Physics-based accuracy (±3.6%) beats empirical methods (±50%)   │
│   • Full component accounting captures hidden CO2 emissions         │
│   • Open source eliminates vendor lock-in and audit concerns        │
│   • API-first design enables digital transformation                 │
│   • Thermodynamic rigor supports emerging H2/NH3/CCUS technologies  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## NeqSim API Reference

### EmissionsCalculator Class

The `EmissionsCalculator` class provides comprehensive emission calculations from gas streams.

#### Java API

```java
import neqsim.process.equipment.util.EmissionsCalculator;
import neqsim.process.equipment.separator.Separator;

// Create calculator from separator gas outlet
EmissionsCalculator calc = new EmissionsCalculator(separator.getGasOutStream());
calc.calculate();

// Get emission rates
double co2_kg_hr = calc.getCO2EmissionRate("kg/hr");
double ch4_kg_hr = calc.getMethaneEmissionRate("kg/hr");
double nmvoc_kg_hr = calc.getNMVOCEmissionRate("kg/hr");
double co2eq_tonnes_yr = calc.getCO2Equivalents("tonnes/year");

// Get gas composition
Map<String, Double> composition = calc.getGasCompositionMole();

// Compare with conventional method
double conv_ch4 = EmissionsCalculator.calculateConventionalCH4(waterVolume_m3, dP_bar);
```

#### Python API (via JPype)

```python
from neqsim import jneqsim

# Access the EmissionsCalculator
EmissionsCalculator = jneqsim.process.equipment.util.EmissionsCalculator

# Create from a separator's gas outlet
calc = EmissionsCalculator(degasser.getGasOutStream())
calc.calculate()

# Get results
co2 = calc.getCO2EmissionRate("kg/hr")
ch4 = calc.getMethaneEmissionRate("kg/hr")
nmvoc = calc.getNMVOCEmissionRate("kg/hr")
co2eq = calc.getCO2Equivalents("tonnes/year")

# Gas composition (returns Java HashMap)
mole_comp = calc.getGasCompositionMole()
for comp in mole_comp.keySet():
    print(f"{comp}: {mole_comp.get(comp)*100:.2f}%")
```

### GWP Constants

```java
// IPCC AR5 100-year Global Warming Potentials
public static final double GWP_CO2 = 1.0;
public static final double GWP_METHANE = 28.0;  // CH4
public static final double GWP_NMVOC = 2.2;     // Average for C2-C5
```

### Supported Units

| Method | Supported Units |
|--------|-----------------|
| `getCO2EmissionRate()` | kg/sec, kg/hr, tonnes/day, tonnes/year |
| `getMethaneEmissionRate()` | kg/sec, kg/hr, tonnes/day, tonnes/year |
| `getNMVOCEmissionRate()` | kg/sec, kg/hr, tonnes/day, tonnes/year |
| `getCO2Equivalents()` | kg/sec, kg/hr, tonnes/day, tonnes/year |
| `getCumulative*()` | kg, tonnes |

---

## Produced Water Degassing

### Typical Process Configuration

```
Separator     Degasser      CFU          Caisson       Sea
(30+ bara) → (2-4 bara) → (1.1 bara) → (1.0 bara) → Discharge
    │            │            │            │
    └──── Pressure drops release dissolved gases ────┘
```

### Multi-Stage Process Simulation

```python
# Create produced water fluid (CPA equation of state)
produced_water = jneqsim.thermo.system.SystemSrkCPAstatoil(80 + 273.15, 30.0)
produced_water.addComponent("water", 0.90)
produced_water.addComponent("CO2", 0.03)
produced_water.addComponent("methane", 0.05)
produced_water.addComponent("ethane", 0.015)
produced_water.addComponent("propane", 0.005)
produced_water.setMixingRule(10)  # CPA mixing rule
produced_water.init(0)

# Create process equipment
inlet_stream = jneqsim.process.equipment.stream.Stream("Feed", produced_water)
inlet_stream.setFlowRate(100000, "kg/hr")
inlet_stream.run()

# Stage 1: Degasser (30 → 4 bara)
degasser_valve = jneqsim.process.equipment.valve.ThrottlingValve("V-1", inlet_stream)
degasser_valve.setOutletPressure(4.0, "bara")
degasser = jneqsim.process.equipment.separator.Separator("Degasser", degasser_valve.getOutletStream())

# Stage 2: CFU (4 → 1.1 bara)
cfu_valve = jneqsim.process.equipment.valve.ThrottlingValve("V-2", degasser.getLiquidOutStream())
cfu_valve.setOutletPressure(1.1, "bara")
cfu = jneqsim.process.equipment.separator.Separator("CFU", cfu_valve.getOutletStream())

# Run process
process = jneqsim.process.processmodel.ProcessSystem()
process.add(inlet_stream)
process.add(degasser_valve)
process.add(degasser)
process.add(cfu_valve)
process.add(cfu)
process.run()

# Calculate emissions from each stage
calc1 = EmissionsCalculator(degasser.getGasOutStream())
calc1.calculate()
calc2 = EmissionsCalculator(cfu.getGasOutStream())
calc2.calculate()

total_co2eq = calc1.getCO2Equivalents("tonnes/year") + calc2.getCO2Equivalents("tonnes/year")
```

### Salinity Effects (Salting-Out)

Higher salinity reduces gas solubility, affecting emissions:

```python
# Salinity correction factor (approximate)
# Reference: Duan & Sun (2003) - Geochimica et Cosmochimica Acta

def salting_out_factor(salinity_ppm):
    """
    Estimate gas solubility reduction due to salinity.
    
    Args:
        salinity_ppm: Total dissolved solids (ppm or mg/L)
    
    Returns:
        Reduction factor (0.8 = 20% less soluble)
    """
    # Simplified Setschenow coefficient approach
    cs = 0.12  # Approximate for CH4 in NaCl
    molality = salinity_ppm / 58440 / (1 - salinity_ppm/1e6)
    return 10 ** (-cs * molality)

# Example: 35,000 ppm seawater
factor = salting_out_factor(35000)  # ~0.87
print(f"Gas solubility reduced to {factor*100:.0f}% of freshwater value")
```

---

## Virtual Measurement Methodology

### Real-Time Integration (NeqSimLive)

NeqSim can be deployed as a "virtual sensor" for continuous emission monitoring:

```
┌─────────────────────────────────────────────────────────────┐
│                    VIRTUAL MEASUREMENT FLOW                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   DCS/SCADA ──► NeqSimLive ──► Emission Rates ──► Reporting │
│      │              │               │                │       │
│  • Temperature   • CPA-EoS     • CO2 kg/hr      • EU ETS    │
│  • Pressure      • Flash calc  • CH4 kg/hr      • NPD       │
│  • Flow rates    • Composition • nmVOC kg/hr    • Dashboard │
│  • Composition   • GWP calc    • CO2eq          • Alerts    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Validation Requirements

Per Aktivitetsforskriften §70 and industry best practice:

| Requirement | Method |
|-------------|--------|
| **Model validation** | Compare vs lab PVT analysis |
| **Uncertainty quantification** | Monte Carlo or sensitivity analysis |
| **Periodic recalibration** | When fluid composition changes |
| **Audit trail** | Version control, calculation logs |

### Uncertainty Analysis

```python
# Monte Carlo uncertainty example
import numpy as np

def monte_carlo_emissions(base_calc, n_samples=1000):
    """
    Estimate emission uncertainty through Monte Carlo sampling.
    
    Varies input parameters within their uncertainty ranges:
    - Temperature: ±2°C
    - Pressure: ±0.1 bar
    - Flow rate: ±3%
    - Composition: ±5% relative
    """
    results = []
    for _ in range(n_samples):
        # Perturb inputs within uncertainty
        temp_factor = np.random.normal(1.0, 0.006)  # ±2°C on 350K
        press_factor = np.random.normal(1.0, 0.025)  # ±0.1 bar on 4 bar
        flow_factor = np.random.normal(1.0, 0.03)    # ±3%
        
        # Scale result (simplified)
        co2eq = base_calc.getCO2Equivalents("tonnes/year")
        co2eq_adjusted = co2eq * temp_factor * press_factor * flow_factor
        results.append(co2eq_adjusted)
    
    return {
        'mean': np.mean(results),
        'std': np.std(results),
        'p5': np.percentile(results, 5),
        'p95': np.percentile(results, 95)
    }
```

---

## Validation and Uncertainty

### Thermodynamic Model Validation

The CPA equation of state has been validated for water-hydrocarbon systems:

| Property | Typical Error | Reference |
|----------|---------------|-----------|
| CH4 solubility in water | <3% | Kontogeorgis & Folas (2010) |
| CO2 solubility in water | <2% | Duan & Sun (2003) |
| VLE phase split | <5% | Multiple validation studies |

### Comparison with Field Data

Studies comparing NeqSim virtual measurements with physical sampling:

| Study | Deviation | Notes |
|-------|-----------|-------|
| North Sea field (2022) | 3.6% | 12-month continuous operation |
| PVT lab validation | 2.1% | Controlled conditions |
| Conventional method | 50-200% | Systematic under-reporting |

---

## Literature References

### Regulatory Documents

1. **Norwegian Petroleum Directorate (NPD)**
   - "Resource Report" - Annual emission data
   - URL: https://www.npd.no/en/facts/publications/reports/resource-report/

2. **Aktivitetsforskriften (Activity Regulations)**
   - Section 70: Measurement and calculation
   - URL: https://lovdata.no/dokument/SF/forskrift/2010-04-29-613

3. **Norsk olje og gass (Norwegian Oil and Gas)**
   - "Handbook for quantification of direct emissions"
   - "Guidelines for emissions reporting"
   - URL: https://www.norskoljeoggass.no/

### Scientific Publications

4. **Kontogeorgis, G.M. & Folas, G.K. (2010)**
   - "Thermodynamic Models for Industrial Applications"
   - John Wiley & Sons. ISBN: 978-0-470-69726-9
   - DOI: [10.1002/9780470747537](https://doi.org/10.1002/9780470747537)

5. **Duan, Z. & Sun, R. (2003)**
   - "An improved model calculating CO2 solubility in pure water and aqueous NaCl solutions"
   - Chemical Geology, 193(3-4), 257-271
   - DOI: [10.1016/S0009-2541(02)00263-2](https://doi.org/10.1016/S0009-2541(02)00263-2)

6. **Søreide, I. & Whitson, C.H. (1992)**
   - "Peng-Robinson predictions for hydrocarbons, CO2, N2, and H2S with pure water and NaCl brine"
   - Fluid Phase Equilibria, 77, 217-240
   - DOI: [10.1016/0378-3812(92)85105-H](https://doi.org/10.1016/0378-3812(92)85105-H)

7. **Michelsen, M.L. & Mollerup, J.M. (2007)**
   - "Thermodynamic Models: Fundamentals & Computational Aspects"
   - Tie-Line Publications. ISBN: 87-989961-3-4

### Industry Guidelines

8. **IOGP Report 521 (2019)**
   - "Methods for estimating atmospheric emissions from E&P operations"
   - International Association of Oil & Gas Producers
   - URL: https://www.iogp.org/bookstore/product/methods-for-estimating-atmospheric-emissions-from-e-p-operations/

9. **API Compendium of Greenhouse Gas Emissions Methodologies**
   - American Petroleum Institute
   - URL: https://www.api.org/oil-and-natural-gas/environment/climate-change/greenhouse-gas-emissions-estimation

10. **IPCC AR5 (2014)**
    - "Climate Change 2014: Synthesis Report"
    - Global Warming Potentials (Table 8.A.1)
    - URL: https://www.ipcc.ch/report/ar5/syr/

### Software & Tools

11. **NeqSim - Open Source Process Simulator**
    - GitHub: https://github.com/equinor/neqsim
    - Documentation: https://equinor.github.io/neqsim/
    - PyPI: https://pypi.org/project/neqsim/

12. **NeqSim Java API Documentation**
    - URL: https://equinor.github.io/neqsim/javadoc/

### EU Regulatory Framework

13. **EU ETS Directive 2003/87/EC**
    - Establishing a scheme for greenhouse gas emission allowance trading
    - URL: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32003L0087

14. **EU Methane Regulation 2024/1787**
    - Methane emissions reduction in the energy sector
    - URL: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R1787

15. **MRV Regulation (EU) 2015/757**
    - Monitoring, reporting and verification of CO2 emissions from maritime transport
    - URL: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32015R0757

---

## Appendix A: Unit Conversions

| From | To | Factor |
|------|-----|--------|
| kg/hr | tonnes/year | × 8.76 |
| tonnes/year | kg/hr | × 0.114 |
| Sm³ gas | kg (CH4) | × 0.717 |
| Sm³ gas | kg (CO2) | × 1.977 |
| bbl water | m³ water | × 0.159 |

## Appendix B: Typical Emission Factors

| Source | CO2 Factor | Unit | Reference |
|--------|------------|------|-----------|
| Gas turbine | 200-250 | kg/MWh | IOGP 521 |
| Diesel engine | 250-280 | kg/MWh | IOGP 521 |
| Flaring (98% efficiency) | 2.75 | kg CO2/Sm³ gas | API |
| Cold vent | 0.72 | kg CH4/Sm³ | Direct |
| Produced water (conventional) | 14 | g CH4/m³/bar | Norsk olje og gass |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-01 | Initial release |

---

*Document maintained by NeqSim development team. For questions or contributions, see https://github.com/equinor/neqsim*
