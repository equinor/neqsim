---
layout: default
title: "Emissions & Sustainability"
description: "GHG emission calculation and reporting with NeqSim"
nav_order: 8
has_children: true
---

# 🌱 Emissions & Sustainability

NeqSim provides physics-based emission calculations for offshore oil & gas operations, enabling accurate regulatory reporting and decarbonization planning.

<div class="highlight-box" style="background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%); border-left: 4px solid #4caf50; padding: 1.5rem; border-radius: 8px; margin: 1.5rem 0;">
<strong>Key Capability:</strong> Thermodynamic emission calculations use rigorous phase equilibrium modeling to account for process conditions, fluid composition, and dissolved gases including CO₂—factors that simplified handbook correlations may approximate differently.
</div>

---

## Quick Links

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; margin: 1.5rem 0;">

<div style="background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem;">
<h3 style="margin-top: 0;">📚 Documentation</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.3rem 0;"><a href="OFFSHORE_EMISSION_REPORTING.html"><strong>Offshore Emission Reporting Guide</strong></a> — Reference</li>
<li style="padding: 0.3rem 0;"><a href="../REFERENCE_MANUAL_INDEX.html#chapter-43-sustainability--emissions">API Reference (Chapter 43)</a> — EmissionsCalculator class</li>
</ul>
</div>

<div style="background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem;">
<h3 style="margin-top: 0;">🎓 Tutorials</h3>
<ul style="list-style: none; padding: 0; margin: 0;">
<li style="padding: 0.3rem 0;"><a href="../examples/ProducedWaterEmissions_Tutorial.html"><strong>Produced Water Emissions Tutorial</strong></a> — Interactive Jupyter notebook</li>
<li style="padding: 0.3rem 0;"><a href="../examples/NorwegianEmissionMethods_Comparison.html">Norwegian Methods Comparison</a> — Validation against handbook</li>
<li style="padding: 0.3rem 0;"><a href="https://github.com/equinor/neqsim/blob/master/docs/examples/OffshoreEmissionReportingExample.java">Java Example</a> — Complete code sample</li>
</ul>
</div>

</div>

---

## Emission Sources Covered

```
┌─────────────────────────────────────────────────────────────┐
│                 OFFSHORE PLATFORM EMISSIONS                  │
├──────────────────┬──────────────────┬───────────────────────┤
│   COMBUSTION     │    VENTING       │     FUGITIVE          │
│   (typically     │    (typically    │     (typically        │
│    dominant)     │    5-20%)        │      <5%)             │
├──────────────────┼──────────────────┼───────────────────────┤
│ • Gas turbines   │ • Cold vents     │ • Valve/flange leaks  │
│ • Diesel engines │ • Tank breathing │ • Compressor seals    │
│ • Flares         │ • PW degassing   │ • Pump seals          │
│ • Heaters        │ • TEG regen.     │ • Pipe connections    │
└──────────────────┴──────────────────┴───────────────────────┘
```

*Source distribution varies significantly by facility type, age, and operations.*

NeqSim specializes in **venting emissions** from:
- Produced water degassing (Degasser, CFU, Caisson)
- TEG regeneration off-gas
- Tank breathing/loading losses
- Cold vent streams

---

## Regulatory Compliance

| Regulation/Framework | Jurisdiction | NeqSim Capability |
|----------------------|--------------|-------------------|
| **Aktivitetsforskriften §70** | Norway | Virtual measurement methodology |
| **EU ETS Directive** | European Union | CO₂ equivalent reporting |
| **EU Methane Regulation 2024/1787** | European Union | Source-level CH₄ quantification |
| **OGMP 2.0** (voluntary) | International | Supports Level 4/5 site-specific methods |
| **ISO 14064-1:2018** | International | Organization-level GHG inventory |

---

## Online Emission Calculation & Automated Reporting

NeqSim can be deployed for **online emission calculations**, enabling real-time monitoring and automated regulatory reporting. This capability transforms emissions management from a periodic reporting exercise into a continuous operational tool.

### Field Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                 ONLINE EMISSION CALCULATION SYSTEM                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   PLANT DATA                    NEQSIM ENGINE              REPORTING    │
│   ──────────                    ─────────────              ─────────    │
│                                                                         │
│   ┌───────────┐               ┌─────────────────┐      ┌─────────────┐ │
│   │   SCADA   │───────────────│  Thermodynamic  │      │  Dashboard  │ │
│   │  Real-time│  Flow rates   │     Model       │      │  (Real-time)│ │
│   │   tags    │  Pressures    │                 │      └─────────────┘ │
│   └───────────┘  Temperatures │  • CPA/SRK EoS  │              │       │
│                               │  • Søreide-     │              ▼       │
│   ┌───────────┐               │    Whitson      │      ┌─────────────┐ │
│   │    Lab    │───────────────│  • Multi-stage  │──────│  Automated  │ │
│   │  Analysis │  Compositions │    separation   │      │   Reports   │ │
│   │           │  Water cuts   │                 │      │  (Daily/    │ │
│   └───────────┘               └─────────────────┘      │   Monthly)  │ │
│                                       │                └─────────────┘ │
│   ┌───────────┐                       │                        │       │
│   │ Historian │◀──────────────────────┘                        ▼       │
│   │  Archive  │  Store calculated                      ┌─────────────┐ │
│   │           │  emissions for audit                   │ Regulatory  │ │
│   └───────────┘                                        │ Submission  │ │
│                                                        │             │
│                                                        └─────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Thermodynamic Model: Søreide-Whitson

For produced water emission calculations, NeqSim provides the **Søreide-Whitson thermodynamic model** to account for the effect of formation water salinity on gas solubility (the "salting-out" effect). This model is used in **NeqSimLive** for real-time emission calculations on offshore platforms.

**Key features:**
- Modified Peng-Robinson equation of state with salinity-dependent parameters for water
- Accounts for reduction in gas solubility due to ionic strength
- Supports multiple salt types (NaCl, CaCl₂, MgCl₂, etc.)
- Validated against experimental data for CH₄, CO₂, H₂S, and N₂ in brine

**Reference:** Søreide, I. & Whitson, C.H. (1992). "Peng-Robinson predictions for hydrocarbons, CO₂, N₂, and H₂S with pure water and NaCl brine". *Fluid Phase Equilibria*, 77, 217-240.

📖 [**Detailed Søreide-Whitson Model Documentation**](../thermo/SoreideWhitsonModel) — Mathematical formulation, salt type coefficients, validation data, and code examples.

---

## Method Comparison

| Aspect | Conventional (Handbook) | Thermodynamic (NeqSim) |
|--------|------------------------|------------------------|
| **Approach** | Empirical correlations | Rigorous phase equilibrium (CPA-EoS) |
| **CO₂ accounting** | Simplified factors | Explicit component tracking |
| **Salinity effects** | Typically not included | Søreide-Whitson salting-out model |
| **Temperature effects** | Linear correlations | Full equation of state |
| **Computational cost** | Low (spreadsheet) | Moderate (requires simulator) |
| **Regulatory acceptance** | Widely established | Accepted under Aktivitetsforskriften §70 |
| **Transparency** | Published factors | Open-source algorithms |

---

## Quick Start

### Python (neqsim-python)

```python
from neqsim import jneqsim

# Create CPA fluid for accurate water-hydrocarbon VLE
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 80, 30.0)
fluid.addComponent("water", 0.90)
fluid.addComponent("CO2", 0.03)
fluid.addComponent("methane", 0.05)
fluid.addComponent("ethane", 0.015)
fluid.addComponent("propane", 0.005)
fluid.setMixingRule(10)  # CPA mixing rule

# Create stream and separator
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
EmissionsCalculator = jneqsim.process.equipment.util.EmissionsCalculator

feed = Stream("PW-Feed", fluid)
feed.setFlowRate(100000, "kg/hr")  # ~100 m³/hr
feed.run()

degasser = Separator("Degasser", feed)
degasser.run()

# Calculate emissions
calc = EmissionsCalculator(degasser.getGasOutStream())
calc.calculate()

print(f"CO2:     {calc.getCO2EmissionRate('tonnes/year'):.0f} tonnes/year")
print(f"Methane: {calc.getMethaneEmissionRate('tonnes/year'):.0f} tonnes/year")
print(f"CO2eq:   {calc.getCO2Equivalents('tonnes/year'):.0f} tonnes/year")
```

### Java

```java
import neqsim.process.equipment.util.EmissionsCalculator;
import neqsim.process.equipment.separator.Separator;

// After setting up your process...
EmissionsCalculator calc = new EmissionsCalculator(separator.getGasOutStream());
calc.calculate();

double co2eq = calc.getCO2Equivalents("tonnes/year");
System.out.println("CO2 Equivalent: " + co2eq + " tonnes/year");
```

---

## Why NeqSim for Emissions?

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1rem; margin: 1.5rem 0;">

<div style="background: #e3f2fd; padding: 1rem; border-radius: 8px;">
<h4 style="margin-top: 0; color: #1565c0;">🎯 Rigorous Thermodynamics</h4>
Physics-based CPA equation of state models water-hydrocarbon phase behavior including associating interactions.
</div>

<div style="background: #fff3e0; padding: 1rem; border-radius: 8px;">
<h4 style="margin-top: 0; color: #e65100;">📊 Comprehensive Accounting</h4>
Explicitly models all gas components including CO₂, which can be significant in produced water emissions depending on reservoir fluid composition.
</div>

<div style="background: #e8f5e9; padding: 1rem; border-radius: 8px;">
<h4 style="margin-top: 0; color: #2e7d32;">🔓 Open Source</h4>
Transparent algorithms auditable by regulators. No vendor lock-in.
</div>

<div style="background: #fce4ec; padding: 1rem; border-radius: 8px;">
<h4 style="margin-top: 0; color: #c2185b;">🚀 Future-Ready</h4>
Supports digital twins, live monitoring, online optimization, CO2 and hydrogen value chains.
</div>

</div>

---

## Online Emission Calculation: Transforming Operator Visibility

### The Value of Online Emission Monitoring

Traditional emission reporting is typically **retrospective** — operators compile emission data periodically (monthly, quarterly). Online monitoring provides more frequent visibility:

```
┌─────────────────────────────────────────────────────────────────────────┐
│              PERIODIC vs ONLINE EMISSION MONITORING                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   PERIODIC (Retrospective)             ONLINE (Continuous)              │
│   ───────────────────────────          ────────────────────             │
│                                                                         │
│   ┌─────┐    ┌─────┐    ┌─────┐       ┌─────┐    ┌──────┐              │
│   │Month│───▶│Month│───▶│Report│      │ Now │───▶│Review│              │
│   │  1  │    │  2  │    │      │      │     │    │      │              │
│   └─────┘    └─────┘    └─────┘       └─────┘    └──────┘              │
│                              │              │         │                 │
│                              ▼              │         ▼                 │
│                        "Emissions for       │    "Current emission      │
│                         Q3: X tonnes"       │     rate: Y kg/hr"        │
│                                             │                           │
│   Established regulatory workflow           More frequent feedback      │
│   Aggregated reporting                      Better operations linkage   │
│   Compliance-oriented                       Supports optimization       │
│   Clear audit trail                         Enables trend analysis      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Advantages of Online Emission Calculation

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; margin: 1.5rem 0;">

<div style="background: #e8f5e9; border-left: 4px solid #4caf50; padding: 1rem; border-radius: 0 8px 8px 0;">
<h4 style="margin-top: 0; color: #2e7d32;">👁️ Real-Time Visibility</h4>
<ul style="margin-bottom: 0;">
<li>See emissions as they happen, not months later</li>
<li>Immediate feedback on operational changes</li>
<li>Dashboard showing live CO₂eq/hour</li>
</ul>
</div>

<div style="background: #e3f2fd; border-left: 4px solid #2196f3; padding: 1rem; border-radius: 0 8px 8px 0;">
<h4 style="margin-top: 0; color: #1565c0;">🔗 Cause-Effect Understanding</h4>
<ul style="margin-bottom: 0;">
<li>Link operational decisions to emission impact</li>
<li>Correlate process changes with emission response</li>
<li>Data-driven decision making</li>
</ul>
</div>

<div style="background: #fff3e0; border-left: 4px solid #ff9800; padding: 1rem; border-radius: 0 8px 8px 0;">
<h4 style="margin-top: 0; color: #e65100;">🎯 Targeted Reduction</h4>
<ul style="margin-bottom: 0;">
<li>Identify highest emission sources instantly</li>
<li>Focus effort where impact is greatest</li>
<li>Track reduction initiatives in real-time</li>
</ul>
</div>

<div style="background: #fce4ec; border-left: 4px solid #e91e63; padding: 1rem; border-radius: 0 8px 8px 0;">
<h4 style="margin-top: 0; color: #c2185b;">📈 Continuous Improvement</h4>
<ul style="margin-bottom: 0;">
<li>More frequent improvement cycles</li>
<li>Operational targets with emission KPIs</li>
<li>Team engagement through transparency</li>
</ul>
</div>

</div>

### Operator Empowerment: From Compliance to Optimization

Online emission calculation transforms the operator mindset:

| Traditional Approach | Online-Enabled Approach |
|---------------------|------------------------|
| Emissions reported periodically (monthly/quarterly) | Emissions calculated continuously |
| Compliance-focused reporting | Combines compliance with operational insight |
| Targets set during planning | Better visibility into emission drivers |
| Feedback through periodic reports | More timely feedback on operational changes |
| Focus on meeting reporting requirements | Enables data-driven emission management |

### Key Use Cases for Operators

#### 1. Daily Emission Dashboards

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PLATFORM EMISSION DASHBOARD                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  TODAY: 2026-02-01 14:35                          Target: 500 t CO₂eq   │
│  ═══════════════════════════════════════════════════════════════════    │
│                                                                         │
│  TOTAL EMISSIONS                    BREAKDOWN BY SOURCE                 │
│  ┌─────────────────────┐           ┌────────────────────────────────┐  │
│  │                     │           │ Turbines      ████████░░  62%  │  │
│  │    487 t CO₂eq      │           │ Flaring       ███░░░░░░░  18%  │  │
│  │    ──────────────   │           │ PW Degassing  ██░░░░░░░░  12%  │  │
│  │    Target: 500 t    │           │ Fugitive      █░░░░░░░░░   5%  │  │
│  │    Status: ✅ ON TRACK│          │ Other         █░░░░░░░░░   3%  │  │
│  │                     │           └────────────────────────────────┘  │
│  └─────────────────────┘                                                │
│                                                                         │
│  TREND (Last 24 Hours)              REDUCTION OPPORTUNITIES             │
│  ┌─────────────────────┐           ┌────────────────────────────────┐  │
│  │    ╱╲               │           │ ⚡ Reduce sep pressure by 2 bar│  │
│  │   ╱  ╲    ╱╲       │           │    Est. saving: 8 t/day        │  │
│  │  ╱    ╲  ╱  ╲  ╱   │           │                                │  │
│  │ ╱      ╲╱    ╲╱    │           │ ⚡ Optimize compressor load    │  │
│  │                     │           │    Est. saving: 12 t/day       │  │
│  └─────────────────────┘           └────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 2. What-If Analysis

Operators can instantly evaluate emission impact of operational changes:

```python
# Operator wants to know: "What if I increase separator temperature?"
scenarios = [
    {"sep_temp": 70, "description": "Current operation"},
    {"sep_temp": 75, "description": "+5°C"},
    {"sep_temp": 80, "description": "+10°C"},
    {"sep_temp": 85, "description": "+15°C"},
]

print("What-If Analysis: Separator Temperature Impact")
print("=" * 60)
for scenario in scenarios:
    result = evaluate_operation([sep_pressure, scenario['sep_temp']])
    print(f"{scenario['description']:20} | "
          f"Emissions: {result['emissions_co2eq']:,.0f} t/yr | "
          f"Production: {result['gas_rate']:.2f} MSm³/d")
```

#### 3. Emission Alerts & Anomaly Detection

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        EMISSION ALERT SYSTEM                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  🔴 ALERT: Methane emissions 35% above baseline                         │
│  ─────────────────────────────────────────────────                      │
│  Time: 14:32 UTC                                                        │
│  Source: HP Separator liquid outlet                                     │
│  Current: 45 kg/hr CH₄    Baseline: 33 kg/hr CH₄                       │
│                                                                         │
│  Possible causes:                                                       │
│  • Separator level too high (check LIC-101)                            │
│  • Gas carry-under to liquid phase                                      │
│  • Changed feed composition                                             │
│                                                                         │
│  Recommended actions:                                                   │
│  1. Check separator level controller output                             │
│  2. Review feed analysis from last sample                               │
│  3. Consider reducing throughput temporarily                            │
│                                                                         │
│  [Acknowledge]  [Investigate]  [Dismiss]                                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 4. Shift Handover with Emission Context

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SHIFT HANDOVER REPORT                                │
│                    Night Shift → Day Shift                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  EMISSION SUMMARY (Last 12 hours)                                       │
│  ─────────────────────────────────                                      │
│  Total CO₂eq:     245 tonnes (vs target 250) ✅                         │
│  Methane vented:  12.3 tonnes                                           │
│  Flared gas:      0.8 MSm³                                              │
│                                                                         │
│  KEY EVENTS                                                             │
│  ──────────                                                             │
│  • 02:15 - Reduced flaring by 30% after compressor restart              │
│  • 04:45 - PW degassing spike due to slug arrival (normalized by 05:30) │
│  • 06:00 - Implemented new separator setpoint (emissions -8%)           │
│                                                                         │
│  HANDOVER NOTES                                                         │
│  ──────────────                                                         │
│  • New separator setpoint working well - recommend keeping              │
│  • Watch for another slug expected around 10:00                         │
│  • Turbine B showing higher than normal emissions - maintenance aware   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Emission Reduction Strategies Enabled by Online Monitoring

| Strategy | How Online Monitoring Helps | Potential Benefit |
|----------|----------------------------|-------------------|
| **Operating Envelope Optimization** | Identify conditions where production is maintained with lower emissions | Site-specific; depends on operating flexibility |
| **Flare Minimization** | Real-time flare gas tracking enables faster response | Depends on current flaring levels |
| **Leak Detection (LDAR)** | Anomaly detection can flag fugitive emission increases | Depends on baseline fugitive levels |
| **Produced Water Management** | Optimize degassing stages based on modeled dissolved gas | Depends on water volume and gas content |
| **Compressor Optimization** | Balance power consumption vs venting from recycle | Depends on compressor operating range |
| **Predictive Scheduling** | Plan maintenance during low-emission windows | Depends on maintenance flexibility |

### Building an Emission-Aware Culture

Online emission monitoring can support cultural transformation toward emission awareness:

```
┌─────────────────────────────────────────────────────────────────────────┐
│               EMISSION-AWARE OPERATIONAL CULTURE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  LEVEL 1: AWARENESS                                                     │
│  ─────────────────────                                                  │
│  Real-time emissions visible in control room                            │
│  → Dashboards display current emission rates                           │
│  → Daily reports included in morning meetings                          │
│                                                                         │
│  LEVEL 2: UNDERSTANDING                                                 │
│  ─────────────────────────                                              │
│  Operations understand emission drivers                                 │
│  → Training on emission sources and mechanisms                         │
│  → What-if analysis tools available                                    │
│                                                                         │
│  LEVEL 3: OWNERSHIP                                                     │
│  ─────────────────────                                                  │
│  Teams take responsibility for emission performance                     │
│  → Emission KPIs included in operational targets                       │
│  → Operators propose and test reduction ideas                          │
│                                                                         │
│  LEVEL 4: OPTIMIZATION                                                  │
│  ────────────────────────                                               │
│  Active optimization for reduced emissions                              │
│  → Automated advisory systems with emission constraints                 │
│  → Continuous improvement integrated in daily operations                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Implementation Roadmap

| Phase | Duration | Activities | Outcome |
|-------|----------|------------|---------|
| **1. Pilot** | 1-2 months | Deploy NeqSim model for one emission source | Proof of concept |
| **2. Expand** | 2-3 months | Add all major emission sources | Complete visibility |
| **3. Integrate** | 1-2 months | Connect to SCADA, build dashboards | Real-time monitoring |
| **4. Optimize** | Ongoing | Implement reduction strategies | Continuous improvement |

---

## Support

### ✅ Support Infrastructure

| Support Channel | Description | Response |
|-----------------|-------------|----------|
| **GitHub Issues** | [equinor/neqsim/issues](https://github.com/equinor/neqsim/issues) | Active maintainers, typically < 48h |
| **GitHub Discussions** | [Q&A forum](https://github.com/equinor/neqsim/discussions) | Community + core team |
| **Equinor Internal** | Internal Teams channel, expert network | Same-day for critical issues |
| **NTNU Collaboration** | Academic partnership for advanced thermodynamics | Research support |

### ✅ Documentation

| Documentation Type | Status | Location |
|--------------------|--------|----------|
| **API Reference** | ✅ Complete | [JavaDoc](https://equinor.github.io/neqsim/javadoc/), [Reference Manual](../REFERENCE_MANUAL_INDEX.html) |
| **Getting Started** | ✅ Complete | [Wiki](../wiki/getting_started.html) |
| **Emission Calculations** | ✅ Complete | This page + [Guide](OFFSHORE_EMISSION_REPORTING.html) |
| **Interactive Tutorials** | ✅ Complete | [Jupyter Notebooks](../examples/index.html) with Colab links |
| **Code Examples** | ✅ Complete | Java + Python examples for all features |
| **Regulatory Context** | ✅ Complete | Norwegian/EU framework documented |
| **Validation Data** | ✅ Complete | Gudrun case study, uncertainty analysis |

### ✅ Expertise & Learning Path

**Time to Competency:**

| Level | Timeframe | Deliverable |
|-------|-----------|-------------|
| **Basic User** | 1-2 days | Run emission calculations using provided notebooks |
| **Process Engineer** | 1-2 weeks | Build custom process models, interpret results |
| **Developer** | 2-4 weeks | Integrate into applications, extend functionality |
| **Expert** | 2-3 months | Customize thermodynamic models, contribute code |

**Learning Resources:**

1. **Self-Paced**
   - Interactive Jupyter notebooks (run in browser via Colab)
   - Step-by-step tutorials with explanations
   - Example code library

2. **Guided**
   - Equinor internal training sessions (2-day workshop)
   - NTNU course modules (thermodynamics background)
   - Pair programming with experienced users

3. **Reference**
   - Complete API documentation
   - Theory background in [Kontogeorgis & Folas (2010)](https://doi.org/10.1002/9780470747537)
   - Application notes for specific use cases

### ✅ Integration & Deployment Options

| Deployment | Complexity | Use Case |
|------------|------------|----------|
| **Python Notebook** | ⭐ Low | Ad-hoc analysis, prototyping |
| **Python Script** | ⭐ Low | Batch processing, automation |
| **Java Application** | ⭐⭐ Medium | Enterprise integration |
| **REST API/Microservice** | ⭐⭐ Medium | Real-time digital twins |
| **Excel Add-in** | ⭐ Low | End-user access (via Python) |
| **Cloud Deployment** | ⭐⭐ Medium | Azure, AWS, Kubernetes |

### Comparison: NeqSim vs Commercial Alternatives

| Aspect | NeqSim | Commercial Tools |
|--------|--------|------------------|
| **License Cost** | Free (Apache 2.0) | Varies by vendor |
| **Source Code Access** | Full access | Typically limited |
| **Customization** | Unlimited | Vendor-dependent |
| **Audit Trail** | Git history | Vendor-dependent |
| **Regulatory Defense** | Transparent algorithms, peer review | Established vendor support |
| **Long-term Availability** | Open source, community-maintained | Vendor support agreements |
| **Integration Flexibility** | Java/Python/REST | Varies by product |
| **Support** | Community + Equinor | Vendor SLA |
| **Validation/Certification** | User responsibility | Often pre-validated |

---

## Production Optimization with Emission & Energy Minimization

### The Multi-Objective Challenge

Modern offshore operations face competing objectives that must be optimized simultaneously:

```
┌─────────────────────────────────────────────────────────────────────────┐
│           MULTI-OBJECTIVE PRODUCTION OPTIMIZATION                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   MAXIMIZE                    MINIMIZE                                  │
│   ────────                    ────────                                  │
│   • Oil/gas production        • CO₂ equivalent emissions                │
│   • Revenue                   • Energy consumption                      │
│   • Export quality            • Flaring/venting                         │
│   • Uptime                    • Operating costs                         │
│                                                                         │
│                     ┌─────────────────┐                                 │
│                     │    NEQSIM       │                                 │
│                     │  PROCESS MODEL  │                                 │
│                     └────────┬────────┘                                 │
│                              │                                          │
│              ┌───────────────┼───────────────┐                          │
│              ▼               ▼               ▼                          │
│        ┌─────────┐     ┌─────────┐     ┌─────────┐                      │
│        │PRODUCTION│    │EMISSIONS│     │ ENERGY  │                      │
│        │  MODEL   │    │  CALC   │     │ BALANCE │                      │
│        └─────────┘     └─────────┘     └─────────┘                      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### NeqSim Integrated Optimization Capabilities

NeqSim enables **simultaneous optimization** of production, emissions, and energy because all three are calculated from the same thermodynamic model:

| Capability | How NeqSim Supports It |
|------------|----------------------|
| **Consistent Material Balance** | Single process model tracks mass flows for production and emissions |
| **Energy Integration** | Heat/power duties calculated from same thermodynamic properties |
| **Computational Speed** | Suitable for online optimization applications |
| **Gradient Information** | Supports efficient optimization algorithms |
| **What-If Analysis** | Rapid scenario evaluation for operational decisions |

### Optimization Problem Formulation

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PARETO-OPTIMAL OPERATION                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   Multi-Objective Function:                                             │
│                                                                         │
│   minimize  f(x) = [ -Production(x),                                    │
│                      Emissions(x),                                      │
│                      Energy(x) ]                                        │
│                                                                         │
│   subject to:                                                           │
│     • Process constraints (pressures, temperatures, capacities)         │
│     • Product specifications (export quality, water content)            │
│     • Equipment limits (compressor surge, pump curves)                  │
│     • Regulatory limits (emission permits, flare consent)               │
│                                                                         │
│   Decision variables x:                                                 │
│     • Separator pressures and temperatures                              │
│     • Compressor speeds / recycle rates                                 │
│     • Heat exchanger duties                                             │
│     • Choke/valve positions                                             │
│     • Gas lift / injection rates                                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Python Example: Integrated Optimization

```python
from neqsim import jneqsim
from scipy.optimize import minimize, differential_evolution
import numpy as np

# === SETUP NEQSIM PROCESS MODEL ===
def create_process(sep_pressure, sep_temp, compressor_speed):
    """Create offshore process with given operating parameters."""
    
    # Reservoir fluid
    fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 80, 50.0)
    fluid.addComponent("water", 0.15)
    fluid.addComponent("CO2", 0.02)
    fluid.addComponent("methane", 0.60)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.08)
    fluid.addComponent("n-butane", 0.05)
    fluid.setMixingRule(10)
    
    # Build process
    Stream = jneqsim.process.equipment.stream.Stream
    Separator = jneqsim.process.equipment.separator.Separator
    Compressor = jneqsim.process.equipment.compressor.Compressor
    
    inlet = Stream("Well-Feed", fluid)
    inlet.setFlowRate(50000, "kg/hr")
    inlet.setTemperature(sep_temp, "C")
    inlet.setPressure(sep_pressure, "bara")
    
    sep = Separator("HP-Sep", inlet)
    
    compressor = Compressor("Export-Comp", sep.getGasOutStream())
    compressor.setOutletPressure(120.0, "bara")
    compressor.setPolytropicEfficiency(0.75)
    
    # Run simulation
    process = jneqsim.process.processmodel.ProcessSystem()
    process.add(inlet)
    process.add(sep)
    process.add(compressor)
    process.run()
    
    return process, sep, compressor

# === OBJECTIVE FUNCTIONS ===
def evaluate_operation(x):
    """Evaluate production, emissions, and energy for given operation."""
    sep_pressure, sep_temp = x
    
    try:
        process, sep, compressor = create_process(sep_pressure, sep_temp, 1.0)
        
        # 1. PRODUCTION: Gas export rate (maximize)
        gas_rate = sep.getGasOutStream().getFlowRate("MSm3/day")
        oil_rate = sep.getLiquidOutStream().getFlowRate("m3/hr")
        
        # 2. EMISSIONS: From liquid degassing (minimize)
        EmissionsCalculator = jneqsim.process.equipment.util.EmissionsCalculator
        calc = EmissionsCalculator(sep.getGasOutStream())
        calc.calculate()
        co2eq = calc.getCO2Equivalents("tonnes/year")
        
        # 3. ENERGY: Compressor power (minimize)
        power_MW = compressor.getPower("MW")
        
        return {
            'gas_rate': gas_rate,
            'oil_rate': oil_rate,
            'emissions_co2eq': co2eq,
            'power_MW': power_MW,
            'feasible': True
        }
    except Exception as e:
        return {'feasible': False, 'error': str(e)}

# === WEIGHTED OBJECTIVE (for single-objective solver) ===
def weighted_objective(x, weights={'production': 1.0, 'emissions': 0.5, 'energy': 0.3}):
    """Combined objective with configurable weights."""
    result = evaluate_operation(x)
    
    if not result['feasible']:
        return 1e10  # Penalty for infeasible
    
    # Normalize and combine (negative production because we maximize it)
    obj = (
        -weights['production'] * result['gas_rate'] / 10.0 +  # Normalize ~10 MSm3/d
        weights['emissions'] * result['emissions_co2eq'] / 10000.0 +  # Normalize ~10k t/yr
        weights['energy'] * result['power_MW'] / 5.0  # Normalize ~5 MW
    )
    return obj

# === OPTIMIZATION ===
# Bounds: [sep_pressure (bara), sep_temp (°C)]
bounds = [(20, 80), (40, 100)]

# Run optimization
result = differential_evolution(
    weighted_objective,
    bounds,
    maxiter=50,
    seed=42,
    workers=-1  # Parallel
)

print(f"Optimal separator pressure: {result.x[0]:.1f} bara")
print(f"Optimal separator temperature: {result.x[1]:.1f} °C")

# Evaluate optimal point
optimal = evaluate_operation(result.x)
print(f"\nOptimal Operation:")
print(f"  Gas production: {optimal['gas_rate']:.2f} MSm3/day")
print(f"  CO2 equivalent: {optimal['emissions_co2eq']:.0f} tonnes/year")
print(f"  Compressor power: {optimal['power_MW']:.2f} MW")
```

### Pareto Front Analysis

For true multi-objective optimization, generate the Pareto front:

```python
from scipy.optimize import minimize
import matplotlib.pyplot as plt

def generate_pareto_front(n_points=20):
    """Generate Pareto-optimal solutions trading off objectives."""
    
    pareto_points = []
    
    # Sweep emission weight from 0 (production only) to 1 (emissions only)
    for emission_weight in np.linspace(0.0, 1.0, n_points):
        weights = {
            'production': 1.0 - emission_weight,
            'emissions': emission_weight,
            'energy': 0.2  # Fixed energy weight
        }
        
        result = differential_evolution(
            lambda x: weighted_objective(x, weights),
            bounds=[(20, 80), (40, 100)],
            maxiter=30,
            seed=42
        )
        
        if result.success:
            eval_result = evaluate_operation(result.x)
            if eval_result['feasible']:
                pareto_points.append({
                    'pressure': result.x[0],
                    'temperature': result.x[1],
                    'gas_rate': eval_result['gas_rate'],
                    'emissions': eval_result['emissions_co2eq'],
                    'power': eval_result['power_MW'],
                    'emission_weight': emission_weight
                })
    
    return pareto_points

# Generate and plot Pareto front
pareto = generate_pareto_front()

plt.figure(figsize=(10, 6))
plt.scatter(
    [p['gas_rate'] for p in pareto],
    [p['emissions'] for p in pareto],
    c=[p['power'] for p in pareto],
    cmap='viridis',
    s=100
)
plt.colorbar(label='Compressor Power (MW)')
plt.xlabel('Gas Production (MSm³/day)')
plt.ylabel('CO₂ Equivalent Emissions (tonnes/year)')
plt.title('Pareto Front: Production vs Emissions Trade-off')
plt.grid(True, alpha=0.3)
plt.show()
```

### Real-Time Optimization Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│              REAL-TIME OPTIMIZATION WITH NEQSIM                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐       │
│  │  SCADA   │────▶│  STATE   │────▶│  NEQSIM  │────▶│OPTIMIZER │       │
│  │   DCS    │     │ESTIMATOR │     │  MODEL   │     │          │       │
│  └──────────┘     └──────────┘     └──────────┘     └────┬─────┘       │
│       │                                                   │             │
│       │           ┌──────────────────────────────────────┘             │
│       │           │                                                     │
│       │           ▼                                                     │
│       │    ┌─────────────────────────────────────────────┐             │
│       │    │           OPTIMAL SETPOINTS                 │             │
│       │    │  • Separator P/T        • Compressor speed  │             │
│       │    │  • Valve positions      • Heat duties       │             │
│       │    └─────────────────────────────────────────────┘             │
│       │           │                                                     │
│       │           │  ┌─────────────────────────────────┐               │
│       │           │  │      OBJECTIVE DASHBOARD        │               │
│       │           │  │                                 │               │
│       │           │  │  Production: ████████░░ 85%    │               │
│       │           │  │  Emissions:  ██░░░░░░░░ 15%    │               │
│       │           │  │  Energy:     ███░░░░░░░ 25%    │               │
│       │           │  │                                 │               │
│       │           │  │  CO₂eq Reduced: 2,500 t/month  │               │
│       │           │  └─────────────────────────────────┘               │
│       │           │                                                     │
│       └───────────┴───────────────▶ CLOSED LOOP                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Potential Benefits of Online Emission Monitoring

*Note: Actual benefits depend on facility-specific factors including current monitoring practices, operational flexibility, and emission source distribution.*

| Metric | Potential Benefit | Environmental Relevance |
|--------|--------------------|-----------------------|
| **Emission Visibility** | Real-time monitoring vs periodic reporting | Enables faster response to deviations |
| **Methane Tracking** | Source-level attribution | High-GWP gas (28× CO₂ over 100 years) |
| **Flare Monitoring** | Improved flare efficiency tracking | Direct combustion emission quantification |
| **Reporting Quality** | More frequent, data-driven reports | Better baseline for improvement tracking |

### Integration with NeqSim Production Optimizer

NeqSim includes a built-in `ProductionOptimizer` class that can be extended for multi-objective optimization:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.ProductionOptimizer;

// Create process system
ProcessSystem process = new ProcessSystem();
// ... add equipment ...

// Create optimizer with emission constraints
ProductionOptimizer optimizer = new ProductionOptimizer(process);
optimizer.addObjective("gasProduction", "maximize");
optimizer.addObjective("emissions", "minimize");
optimizer.addConstraint("CO2eq", "<=", emissionPermit);

// Run multi-objective optimization
optimizer.runParetoOptimization();
List<Solution> paretoFront = optimizer.getParetoFront();
```

### Related Documentation

- [Production Optimizer Tutorial](../examples/ProductionOptimizer_Tutorial.html) — Multi-variable optimization with constraints
- [MPC Integration Tutorial](../examples/MPC_Integration_Tutorial.html) — Model Predictive Control integration
- [External Optimizer Integration](../integration/EXTERNAL_OPTIMIZER_INTEGRATION.html) — Python/SciPy integration patterns

---

## Documentation Structure

| Document | Purpose | Audience |
|----------|---------|----------|
| [**Offshore Emission Reporting Guide**](OFFSHORE_EMISSION_REPORTING.html) | Reference with regulatory framework, methods, API, validation, literature | Engineers, Regulators, Auditors |
| [**Produced Water Emissions Tutorial**](../examples/ProducedWaterEmissions_Tutorial.html) | Step-by-step Jupyter notebook with runnable code | Data Scientists, Developers |
| [**Norwegian Methods Comparison**](../examples/NorwegianEmissionMethods_Comparison.html) | Validation against handbook, uncertainty analysis | Engineers, Regulators |
| [**Java Example**](https://github.com/equinor/neqsim/blob/master/docs/examples/OffshoreEmissionReportingExample.java) | Complete Java code sample | Java Developers |
| [**API Reference**](../REFERENCE_MANUAL_INDEX.html#chapter-43-sustainability--emissions) | EmissionsCalculator class documentation | All Developers |

---

## Run in Browser (No Installation)

Click to open the tutorial in Google Colab:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb)

---

## Literature & Standards

Key references for emission calculations:

1. **Kontogeorgis & Folas (2010)** - CPA equation of state theory  
   DOI: [10.1002/9780470747537](https://doi.org/10.1002/9780470747537)

2. **IOGP Report 521 (2019)** - E&P emission estimation methods  
   [IOGP Bookstore](https://www.iogp.org/bookstore/product/methods-for-estimating-atmospheric-emissions-from-e-p-operations/)

3. **IPCC AR5 (2014)** - Global Warming Potentials (GWP)  
   [IPCC Report](https://www.ipcc.ch/report/ar5/syr/)  
   *Note: NeqSim uses AR5 GWP100 values (CH₄=28, N₂O=265) by default. AR6 (2021) values (CH₄=27.9) are also available.*

4. **Søreide & Whitson (1992)** - Peng-Robinson predictions for hydrocarbons in brine  
   *Fluid Phase Equilibria*, 77, 217-240

5. **EU Methane Regulation 2024/1787** - Methane emission requirements  
   [EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R1787)

6. **Aktivitetsforskriften §70** - Norwegian offshore emission quantification requirements  
   [Lovdata](https://lovdata.no/dokument/SF/forskrift/2010-04-29-613)

See [full literature list](OFFSHORE_EMISSION_REPORTING.html#literature-references) in the guide.

---

## Contact & Support

- **GitHub Issues**: [equinor/neqsim](https://github.com/equinor/neqsim/issues)
- **Discussions**: [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Documentation**: [equinor.github.io/neqsim](https://equinor.github.io/neqsim/)

---

*This documentation is part of NeqSim, an open-source thermodynamic and process simulation library by [Equinor](https://www.equinor.com/).*
