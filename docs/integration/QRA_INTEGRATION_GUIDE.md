---
title: NeqSim QRA Integration Guide
description: A comprehensive guide for integrating NeqSim thermodynamic calculations into Quantitative Risk Assessment (QRA) workflows.
---

# NeqSim QRA Integration Guide

A comprehensive guide for integrating NeqSim thermodynamic calculations into Quantitative Risk Assessment (QRA) workflows.

---

## Table of Contents

1. [Where NeqSim Fits in the QRA Chain](#1-where-neqsim-fits-in-the-qra-chain)
2. [Workflow Map by Scenario Type](#2-workflow-map-by-scenario-type)
3. [Mapping to Common QRA Tool Interfaces](#3-mapping-to-common-qra-tool-interfaces)
4. [Standard Output Schemas](#4-standard-output-schemas)
5. [NeqSim Implementation Details](#5-neqsim-implementation-details)
6. [Quality Controls for QRA Credibility](#6-quality-controls-for-qra-credibility)
7. [End-to-End Example Workflow](#7-end-to-end-example-workflow)
8. [Tool-Specific Export Formats](#8-tool-specific-export-formats)

---

## 1. Where NeqSim Fits in the QRA Chain

### NeqSim's Role

NeqSim's primary role in QRA is to produce **high-quality thermodynamics, phase behavior, and discharge conditions** that become inputs ("source terms") to consequence modeling tools.

### Typical QRA Chain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          QRA WORKFLOW CHAIN                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ Process/Operating │                                                       │
│  │ Case Definition   │                                                       │
│  └────────┬─────────┘                                                        │
│           │                                                                  │
│           ▼                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │                        NeqSim                                 │           │
│  │  • State at leak point (P, T, composition)                   │           │
│  │  • Flash/expansion calculations                               │           │
│  │  • Blowdown transients                                        │           │
│  │  • Source term generation (mass flow, phase split, T, ρ)     │           │
│  └────────┬─────────────────────────────────────────────────────┘           │
│           │                                                                  │
│           │  Source Term Files (CSV/JSON)                                   │
│           │  • PHAST format                                                 │
│           │  • FLACS format                                                 │
│           │  • KFX format                                                   │
│           │  • OpenFOAM format                                              │
│           ▼                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │              Consequence Modeling Tools                       │           │
│  │  • PHAST / SAFETI / EFFECTS / ALOHA (dispersion)             │           │
│  │  • FLACS / KFX / OpenFOAM (CFD)                              │           │
│  │  • Fire modules (jet/pool/flash fire)                        │           │
│  │  • Explosion models                                           │           │
│  └────────┬─────────────────────────────────────────────────────┘           │
│           │                                                                  │
│           │  Consequence Results                                            │
│           │  • Dispersion contours                                          │
│           │  • Radiation levels                                             │
│           │  • Overpressure contours                                        │
│           ▼                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │                    QRA Platform                               │           │
│  │  • Event frequencies (OREDA, company data)                   │           │
│  │  • Ignition probabilities                                    │           │
│  │  • Escalation logic                                          │           │
│  │  • Risk integration (F-N curves, risk contours)              │           │
│  └──────────────────────────────────────────────────────────────┘           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### NeqSim Capabilities Summary

| Capability | NeqSim Package | Description |
|------------|----------------|-------------|
| Fluid thermodynamics | `neqsim.thermo` | EoS calculations, phase equilibria |
| Source term generation | `neqsim.process.safety.release` | `LeakModel`, `SourceTermResult` |
| Depressurization/blowdown | `neqsim.process.equipment.tank` | `VesselDepressurization` |
| Safety envelopes | `neqsim.process.safety.envelope` | `SafetyEnvelopeCalculator` |
| Risk quantification | `neqsim.process.safety.risk` | `RiskModel`, Monte Carlo |
| Relief valve sizing | `neqsim.process.util.fire` | `ReliefValveSizing` |

---

## 2. Workflow Map by Scenario Type

### A. Continuous Leak (Hole) from Pressurized Equipment

**Goal:** Mass release rate, phase split, release temperature, jet momentum.

#### NeqSim Outputs

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| P, T, composition | Upstream fluid state at leak node | `SystemInterface` |
| Z, MW, ρ | Real-gas properties | `system.getZ()`, `system.getMolarMass()`, `system.getDensity()` |
| Cp/Cv (γ) | Heat capacity ratio | `system.getGamma()` |
| JT coefficient | Joule-Thomson coefficient | `system.getJouleThomsonCoefficient()` |
| Gas/liquid split | Flash at near-field conditions | `ThermodynamicOperations.TPflash()` |
| mdot, T_release | Mass flow and release temperature | `LeakModel.calculateSourceTerm()` |

#### NeqSim Implementation

```java
import neqsim.process.safety.release.*;
import neqsim.thermo.system.*;

// Define upstream fluid
SystemInterface fluid = new SystemSrkEos(350.0, 80.0); // 80 bara, 350 K
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Create leak model
LeakModel leak = LeakModel.builder()
    .fluid(fluid)
    .holeDiameter(25.0, "mm")
    .dischargeCoefficient(0.62)
    .vesselVolume(10.0)  // m³
    .orientation(ReleaseOrientation.HORIZONTAL)
    .scenarioName("Small Leak - Separator")
    .build();

// Calculate source term (steady-state)
SourceTermResult result = leak.calculateSourceTerm(600.0, 1.0); // 10 min, 1s step

// Export for consequence tools
result.exportToPHAST("leak_phast.csv");
result.exportToFLACS("leak_flacs.csv");
result.exportToKFX("leak_kfx.csv");
result.exportToOpenFOAM("/path/to/openfoam/case");
```

#### Hand-off Artifacts

CSV/JSON file per leak size containing:

```csv
time_s,mdot_total_kg_s,mdot_gas_kg_s,mdot_liquid_kg_s,T_release_K,P_release_bar,rho_gas_kg_m3,MW_gas,Z,gamma,velocity_m_s,momentum_N,choked
0.0,5.23,5.23,0.0,285.4,1.013,1.15,17.2,0.998,1.31,412.5,2156.3,true
1.0,5.21,5.21,0.0,285.2,1.013,1.15,17.2,0.998,1.31,411.8,2148.7,true
...
```

---

### B. Rupture / Full-Bore Release with Inventory Depletion

**Goal:** Release rate vs time, evolving phase split, minimum temperature (MDMT risk).

#### NeqSim Outputs

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| Initial inventory | Mass and phase split in vessel | `VesselDepressurization.getInitialInventory()` |
| P(t), T(t) | Pressure and temperature vs time | `runTransient()` results |
| mdot(t) | Mass flow rate vs time | Transient output |
| T_min | Minimum temperature reached | `getMinimumWallTemperatureReached()` |
| Time to T_min | When minimum occurs | Transient output |

#### NeqSim Implementation

```java
import neqsim.process.equipment.tank.VesselDepressurization;
import neqsim.process.equipment.stream.Stream;

// Setup vessel with initial conditions
SystemInterface gas = new SystemSrkEos(300.0, 100.0); // 100 bara
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(100.0, "kg/hr");
feed.run();

VesselDepressurization vessel = new VesselDepressurization("Blowdown", feed);
vessel.setVolume(50.0);  // m³
vessel.setOrificeDiameter(0.05);  // 50 mm orifice
vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
vessel.setMaxBlowdownTime(1800.0);  // 30 minutes max

// Run transient blowdown
double dt = 1.0;  // 1 second timestep
UUID uuid = UUID.randomUUID();
while (!vessel.isBlowdownComplete()) {
    vessel.runTransient(dt, uuid);
}

// Export results
vessel.exportResultsToCSV("blowdown_results.csv");
vessel.exportResultsToJSON("blowdown_results.json");

// Get summary for QRA documentation
double peakRelease = vessel.getPeakMassFlowRate();
double duration = vessel.getBlowdownDuration();
double totalMass = vessel.getTotalMassReleased();
double minTemp = vessel.getMinimumWallTemperatureReached();
```

#### Hand-off Artifacts

**Time-series file:**

```csv
time_s,P_upstream_bar,T_upstream_K,mdot_total_kg_s,mdot_gas_kg_s,mdot_liquid_kg_s,T_release_K,vapor_fraction
0.0,100.0,300.0,125.4,125.4,0.0,245.2,1.0
1.0,98.5,298.2,123.1,123.1,0.0,244.8,1.0
2.0,97.1,296.5,120.9,120.9,0.0,244.3,1.0
...
```

**Summary card for QRA documentation:**

```json
{
  "scenario": "HP Separator Blowdown",
  "peak_release_kg_s": 125.4,
  "duration_above_10kg_s": 245.0,
  "total_mass_released_kg": 15420.0,
  "minimum_temperature_K": 198.5,
  "time_to_min_temp_s": 312.0,
  "hydrate_risk": true,
  "co2_freezing_risk": false
}
```

---

### C. PSV / BDV Discharge to Flare or Vent

**Goal:** Discharge composition, temperature, phase into flare network; two-phase risk; hydrate/ice risk.

#### NeqSim Outputs

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| P_in, T_in | PSV inlet conditions | `SafetyValve.getInletPressure/Temperature()` |
| mdot | Relieving flow rate | `ReliefValveSizing.calculateRequiredArea()` |
| Composition | Relieving fluid composition | `SystemInterface.getComponent(i)` |
| Quality | Vapor fraction at discharge | `system.getPhase(0).getBeta()` |
| T_out | Discharge temperature | Flash calculation |
| Hydrate indicators | Hydrate formation risk | `SafetyEnvelopeCalculator` |

#### NeqSim Implementation

```java
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.process.safety.envelope.*;

// Define relieving fluid
SystemInterface fluid = new SystemSrkEos(400.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.15);
fluid.addComponent("water", 0.05);
fluid.setMixingRule("classic");

// API 520 sizing
ReliefValveSizing sizing = new ReliefValveSizing(fluid);
sizing.setReliefPressure(55.0);  // bara (set pressure + accumulation)
sizing.setBackPressure(5.0);     // bara
double requiredArea = sizing.calculateRequiredArea();
double massFlow = sizing.getReliefMassFlow();

// Check for hydrate risk in tailpipe
SafetyEnvelopeCalculator envCalc = new SafetyEnvelopeCalculator(fluid);
SafetyEnvelope hydrateEnv = envCalc.calculateHydrateEnvelope(1.0, 60.0, 20);
boolean hydrateRisk = !hydrateEnv.isOperatingPointSafe(5.0, 280.0);

// Generate relieving case table
System.out.printf("P_in: %.1f bara, T_in: %.1f K, mdot: %.2f kg/s%n",
    50.0, 400.0, massFlow);
System.out.printf("Quality: %.3f, T_out: %.1f K%n",
    fluid.getPhase(0).getBeta(), sizing.getDischargeTemperature());
System.out.printf("Hydrate risk: %s%n", hydrateRisk);
```

#### Hand-off Artifacts

**Relieving case table:**

| Case | P_in (bara) | T_in (K) | mdot (kg/s) | Composition | Quality | T_out (K) | Hydrate Risk |
|------|-------------|----------|-------------|-------------|---------|-----------|--------------|
| Fire | 55.0 | 400 | 12.5 | CH4/C2H6 | 0.98 | 285 | No |
| Blocked | 52.0 | 380 | 8.2 | CH4/C2H6 | 1.00 | 290 | No |
| Tube rupture | 55.0 | 350 | 25.0 | CH4/C2H6/H2O | 0.85 | 275 | Yes |

---

### D. Pool Formation (Liquid Release, Rainout)

**Goal:** Whether liquid forms, how much, evaporation rate basis.

#### NeqSim Outputs

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| Flash fraction | Vapor vs liquid at ambient | `ThermodynamicOperations.TPflash()` |
| Liquid density | kg/m³ | `system.getPhase("oil").getDensity()` |
| Liquid viscosity | Pa·s | `system.getPhase("oil").getViscosity()` |
| Boiling range | Temperature range | Phase envelope calculation |
| Volatility split | Light vs heavy fractions | Component distribution |

#### NeqSim Implementation

```java
import neqsim.thermo.system.*;
import neqsim.thermodynamicoperations.*;

// Condensate release
SystemInterface condensate = new SystemSrkEos(288.15, 1.01325); // Ambient P, T
condensate.addComponent("n-pentane", 0.15);
condensate.addComponent("n-hexane", 0.25);
condensate.addComponent("n-heptane", 0.30);
condensate.addComponent("n-octane", 0.20);
condensate.addComponent("n-nonane", 0.10);
condensate.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(condensate);
ops.TPflash();

// Get liquid properties for pool model
double liquidFraction = 1.0 - condensate.getPhase(0).getBeta();
double liquidDensity = condensate.getPhase("oil").getDensity("kg/m3");
double liquidViscosity = condensate.getPhase("oil").getViscosity("kg/msec");

// Estimate initial evaporation rate (simplified)
double vaporPressure = condensate.getPhase("oil").getAntoineVaporPressure(288.15);
double evapRate = estimateEvaporationRate(vaporPressure, liquidDensity);

System.out.printf("Liquid fraction: %.1f%%%n", liquidFraction * 100);
System.out.printf("Liquid density: %.1f kg/m³%n", liquidDensity);
System.out.printf("Initial evap rate: %.3f kg/m²/s%n", evapRate);
```

#### Hand-off Artifacts

```json
{
  "scenario": "Condensate Spill",
  "liquid_rate_kg_s": 5.2,
  "liquid_fraction": 0.85,
  "liquid_density_kg_m3": 680.5,
  "liquid_viscosity_Pa_s": 0.00045,
  "vapor_rate_initial_kg_s": 0.8,
  "boiling_range_K": [309, 424],
  "pseudo_components": [
    {"name": "C5-C6", "fraction": 0.40, "MW": 78},
    {"name": "C7-C9", "fraction": 0.60, "MW": 107}
  ]
}
```

---

### E. Toxic Release (H₂S, CO₂, NH₃)

**Goal:** Concentration vs distance from dispersion tool; NeqSim ensures correct density/phase.

#### NeqSim Outputs

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| Mixture MW | Molecular weight | `system.getMolarMass()` |
| Density | At release conditions | `system.getDensity()` |
| Compressibility | Z-factor | `system.getZ()` |
| Phase split | For CO₂ dense phase | Flash calculations |
| Temperature | Affects buoyancy | `system.getTemperature()` |

#### NeqSim Implementation

```java
import neqsim.process.safety.release.*;
import neqsim.process.safety.envelope.*;

// CO2 with H2S (sour gas)
SystemInterface sourGas = new SystemSrkEos(300.0, 80.0);
sourGas.addComponent("CO2", 0.90);
sourGas.addComponent("H2S", 0.05);
sourGas.addComponent("methane", 0.05);
sourGas.setMixingRule("classic");

// Create leak model
LeakModel leak = LeakModel.builder()
    .fluid(sourGas)
    .holeDiameter(50.0, "mm")
    .dischargeCoefficient(0.62)
    .orientation(ReleaseOrientation.HORIZONTAL)
    .scenarioName("Sour Gas Leak")
    .build();

SourceTermResult result = leak.calculateSourceTerm(300.0, 1.0);

// Check for CO2 freezing / dense phase
SafetyEnvelopeCalculator envCalc = new SafetyEnvelopeCalculator(sourGas);
SafetyEnvelope co2Env = envCalc.calculateCO2FreezingEnvelope(10.0, 100.0, 10);

// Dense gas flag for dispersion modeling
boolean denseGas = sourGas.getDensity("kg/m3") > 1.5; // Heavier than air

// Export with toxic flags
result.exportToPHAST("toxic_release_phast.csv");
```

#### Hand-off Artifacts

Same as leak source term, with additional toxic-specific fields:

```csv
time_s,mdot_total_kg_s,mdot_gas_kg_s,T_release_K,MW,rho_kg_m3,Z,dense_gas_flag,h2s_fraction,co2_fraction
0.0,15.2,15.2,245.0,43.2,2.05,0.82,true,0.05,0.90
...
```

---

## 3. Mapping to Common QRA Tool Interfaces

### Consequence Tool Requirements

| Parameter | PHAST | FLACS | KFX | OpenFOAM | ALOHA |
|-----------|-------|-------|-----|----------|-------|
| Upstream P, T | ✓ | ✓ | ✓ | ✓ | ✓ |
| Composition | ✓ | ✓ | ✓ | ✓ | Simplified |
| Hole size + Cd | ✓ | ✓ | ✓ | ✓ | ✓ |
| Orientation/height | User input | User input | User input | User input | User input |
| Choked flow info | ✓ | ✓ | ✓ | ✓ | ✓ |
| Gas/liquid split | ✓ | ✓ | ✓ | ✓ | Limited |
| Release T | ✓ | ✓ | ✓ | ✓ | ✓ |
| MW, γ, Z | ✓ | ✓ | ✓ | ✓ | ✓ |

### QRA Platform Requirements

| Parameter | Description | NeqSim Source |
|-----------|-------------|---------------|
| Release category | Small/medium/large/rupture | Hole diameter mapping |
| Duration bins | Time above threshold | `LeakModel` transient |
| Total mass released | Per outcome branch | Integration of mdot(t) |
| Peak release rate | For consequence scaling | Max of mdot(t) |

### NeqSim Mapping Layer

```java
import neqsim.process.safety.release.*;

// NeqSim → SourceTerm DTO
public class SourceTermDTO {
    // Identification
    public String caseId;
    public String nodeId;
    public String scenarioType;
    public double holeDiameter_m;
    
    // Upstream conditions
    public double P_upstream_bar;
    public double T_upstream_K;
    public Map<String, Double> composition;
    
    // Discharge conditions
    public double mdot_total_kg_s;
    public double mdot_gas_kg_s;
    public double mdot_liquid_kg_s;
    
    // Thermodynamic properties
    public double T_release_K;
    public double P_release_bar;
    public double Z;
    public double MW_kg_kmol;
    public double rho_gas_kg_m3;
    public double gamma;
    public double Cp_J_kgK;
    
    // Flags
    public boolean choked;
    public boolean twoPhase;
    public boolean hydrateRisk;
    public boolean solidRisk;
    
    // Convert from NeqSim SourceTermResult
    public static SourceTermDTO fromNeqSim(SourceTermResult result, int timeIndex) {
        SourceTermDTO dto = new SourceTermDTO();
        dto.mdot_total_kg_s = result.getMassFlowRate()[timeIndex];
        dto.T_release_K = result.getTemperature()[timeIndex];
        // ... populate other fields
        return dto;
    }
    
    // Export to various formats
    public void exportToPHAST(String filename) { /* ... */ }
    public void exportToFLACS(String filename) { /* ... */ }
    public void exportToKFX(String filename) { /* ... */ }
}
```

---

## 4. Standard Output Schemas

### Single-Release (Steady-State) Schema

```json
{
  "identification": {
    "case_id": "CASE-001",
    "node_id": "SEP-V-101",
    "scenario_type": "small_leak",
    "hole_diameter_mm": 25.0
  },
  "upstream": {
    "P_bar": 80.0,
    "T_K": 350.0,
    "composition": {
      "methane": 0.85,
      "ethane": 0.10,
      "propane": 0.05
    }
  },
  "discharge": {
    "mdot_total_kg_s": 5.23,
    "mdot_gas_kg_s": 5.23,
    "mdot_liquid_kg_s": 0.0
  },
  "thermodynamics": {
    "T_release_K": 285.4,
    "P_release_bar": 1.013,
    "Z": 0.998,
    "MW_kg_kmol": 17.2,
    "rho_gas_kg_m3": 1.15,
    "gamma": 1.31,
    "Cp_J_kgK": 2250
  },
  "flags": {
    "choked": true,
    "two_phase": false,
    "hydrate_risk": false,
    "solid_risk": false
  },
  "momentum": {
    "velocity_m_s": 412.5,
    "momentum_flux_N": 2156.3
  }
}
```

### Transient Release Schema

```json
{
  "header": {
    "case_id": "CASE-002",
    "node_id": "SEP-V-101",
    "scenario_type": "blowdown",
    "orifice_diameter_mm": 50.0,
    "initial_inventory_kg": 5420.0,
    "initial_P_bar": 100.0,
    "initial_T_K": 300.0
  },
  "summary": {
    "peak_release_kg_s": 125.4,
    "duration_s": 892.0,
    "total_mass_released_kg": 5420.0,
    "min_temperature_K": 198.5,
    "time_to_min_temp_s": 312.0
  },
  "timeseries": [
    {
      "t_s": 0.0,
      "P_upstream_bar": 100.0,
      "T_upstream_K": 300.0,
      "mdot_total_kg_s": 125.4,
      "mdot_gas_kg_s": 125.4,
      "mdot_liquid_kg_s": 0.0,
      "T_release_K": 245.2,
      "vapor_fraction": 1.0
    },
    {
      "t_s": 1.0,
      "P_upstream_bar": 98.5,
      "T_upstream_K": 298.2,
      "mdot_total_kg_s": 123.1,
      "mdot_gas_kg_s": 123.1,
      "mdot_liquid_kg_s": 0.0,
      "T_release_K": 244.8,
      "vapor_fraction": 1.0
    }
  ]
}
```

---

## 5. NeqSim Implementation Details

### Package Structure

```
neqsim.process.safety/
├── release/
│   ├── LeakModel.java              # Main leak/rupture calculator
│   ├── SourceTermResult.java       # Time-series container + export
│   ├── ReleaseOrientation.java     # HORIZONTAL, VERTICAL_UP, VERTICAL_DOWN
│   └── package-info.java
├── risk/
│   ├── RiskModel.java              # Monte Carlo + event trees
│   ├── RiskEvent.java              # Individual risk event
│   ├── RiskResult.java             # F-N curves, risk indices
│   └── SensitivityResult.java      # Tornado diagram data
├── envelope/
│   ├── SafetyEnvelopeCalculator.java  # Envelope generator
│   └── SafetyEnvelope.java            # P-T curve container
├── InitiatingEvent.java            # Standard initiating events
├── BoundaryConditions.java         # Environmental conditions
├── ProcessSafetyScenario.java      # Scenario definition
├── ProcessSafetyAnalyzer.java      # Scenario execution
└── ProcessSafetyLoadCase.java      # Load case results
```

### Key Classes and Methods

#### LeakModel

```java
LeakModel leak = LeakModel.builder()
    .fluid(system)                          // SystemInterface
    .holeDiameter(25.0, "mm")               // Leak size
    .dischargeCoefficient(0.62)             // Cd
    .vesselVolume(10.0)                     // m³ (for inventory depletion)
    .orientation(ReleaseOrientation.HORIZONTAL)
    .scenarioName("Description")
    .build();

// Steady-state
double mdot = leak.calculateMassFlowRate();

// Transient (inventory depletion)
SourceTermResult result = leak.calculateSourceTerm(duration, timestep);

// Exports
result.exportToPHAST(filename);   // DNV PHAST format
result.exportToFLACS(filename);   // FLACS/Gexcon format
result.exportToKFX(filename);     // KFX format
result.exportToOpenFOAM(path);    // OpenFOAM boundary files
```

#### VesselDepressurization

```java
VesselDepressurization vessel = new VesselDepressurization(name, feed);
vessel.setVolume(50.0);
vessel.setOrificeDiameter(0.05);
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setFireCase(true, 100.0);  // API 521 fire scenario

// Run transient
while (!vessel.isBlowdownComplete()) {
    vessel.runTransient(dt, uuid);
}

// Results
double tMin = vessel.getMinimumWallTemperatureReached();
Map<String, String> risks = vessel.assessFlowAssuranceRisks();

// Export
vessel.exportResultsToCSV(filename);
vessel.exportResultsToJSON(filename);
```

#### SafetyEnvelopeCalculator

```java
SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(fluid);

// Individual envelopes
SafetyEnvelope hydrate = calc.calculateHydrateEnvelope(pMin, pMax, nPoints);
SafetyEnvelope wax = calc.calculateWaxEnvelope(pMin, pMax, nPoints);
SafetyEnvelope co2 = calc.calculateCO2FreezingEnvelope(pMin, pMax, nPoints);
SafetyEnvelope mdmt = calc.calculateMDMTEnvelope(pMin, pMax, designT, nPoints);

// Combined
SafetyEnvelope[] all = calc.calculateAllEnvelopes(pMin, pMax, nPoints);

// Safety checks
boolean safe = hydrate.isOperatingPointSafe(P, T);
double margin = hydrate.calculateMarginToLimit(P, T);

// Export for DCS/historian
hydrate.exportToCSV(filename);
hydrate.exportToPIFormat(filename);
hydrate.exportToSeeq(filename);
```

#### RiskModel

```java
RiskModel model = new RiskModel("HP Separator Study");
model.setRandomSeed(42);

// Add events with OREDA-style frequencies
model.addInitiatingEvent("Small Leak", 1e-3, ConsequenceCategory.MINOR);
model.addInitiatingEvent("Medium Leak", 1e-4, ConsequenceCategory.MODERATE);
model.addInitiatingEvent("Large Rupture", 1e-5, ConsequenceCategory.MAJOR);

// Event tree branching
RiskEvent leakEvent = model.getEvent("Small Leak");
RiskEvent fireEvent = RiskEvent.builder()
    .name("Fire on Leak")
    .parentEvent(leakEvent)
    .conditionalProbability(0.1)
    .consequenceCategory(ConsequenceCategory.MAJOR)
    .build();
model.addEvent(fireEvent);

// Analysis
RiskResult result = model.runMonteCarloAnalysis(10000);
SensitivityResult sensitivity = model.runSensitivityAnalysis(0.1, 10.0);

// Export
result.exportToCSV(filename);
result.exportToJSON(filename);
sensitivity.exportToCSV(filename);
```

---

## 6. Quality Controls for QRA Credibility

### Clear Assumptions Documentation

| Assumption | Options | Default | Impact |
|------------|---------|---------|--------|
| Expansion type | Isenthalpic / Isentropic | Isenthalpic | Temperature at release |
| Flash type | Equilibrium / Non-equilibrium | Equilibrium | Phase split accuracy |
| Two-phase model | HEM / Slip | HEM | Mass flow rate |
| Discharge coefficient | 0.6 - 0.85 | 0.62 | Mass flow rate |

### Validation Cases

NeqSim source terms should be validated against:

1. **PHAST source term comparison** for selected fluids
2. **API 520 / ISO 4126** critical flow for gas-only sanity checks
3. **Experimental data** where available

Example validation test:

```java
@Test
void validateAgainstAPI520() {
    // Methane at 100 bara, 300 K through 25mm hole
    SystemInterface methane = new SystemSrkEos(300.0, 100.0);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");
    
    LeakModel leak = LeakModel.builder()
        .fluid(methane)
        .holeDiameter(25.0, "mm")
        .dischargeCoefficient(0.62)
        .build();
    
    double mdot = leak.calculateMassFlowRate();
    
    // API 520 correlation for comparison
    double mdotAPI520 = calculateAPI520CriticalFlow(methane, 0.025);
    
    // Should agree within 5%
    assertEquals(mdotAPI520, mdot, mdotAPI520 * 0.05);
}
```

### Sensitivity Ranges

Document sensitivity of results to:

| Parameter | Typical Range | Sensitivity |
|-----------|---------------|-------------|
| Discharge coefficient (Cd) | 0.6 - 0.85 | ±20% on mass flow |
| Hole diameter | ±10% | ±21% on mass flow |
| Upstream P uncertainty | ±5% | ±5% on mass flow |
| Upstream T uncertainty | ±5 K | ±2% on mass flow |
| Composition uncertainty | ±5% per component | Varies |

---

## 7. End-to-End Example Workflow

### Automated QRA Source Term Generation

```java
import neqsim.process.safety.release.*;
import neqsim.process.safety.risk.*;
import java.util.*;

public class QRASourceTermGenerator {
    
    // Standard hole sizes per NORSOK Z-013 / company practice
    private static final double[] HOLE_SIZES_MM = {5.0, 25.0, 100.0};
    private static final String[] SIZE_NAMES = {"Small", "Medium", "Large"};
    
    public void generateSourceTerms(SystemInterface fluid, String nodeId) {
        List<SourceTermResult> results = new ArrayList<>();
        
        for (int i = 0; i < HOLE_SIZES_MM.length; i++) {
            LeakModel leak = LeakModel.builder()
                .fluid(fluid)
                .holeDiameter(HOLE_SIZES_MM[i], "mm")
                .dischargeCoefficient(0.62)
                .vesselVolume(10.0)
                .scenarioName(SIZE_NAMES[i] + " Leak - " + nodeId)
                .build();
            
            SourceTermResult result = leak.calculateSourceTerm(600.0, 1.0);
            results.add(result);
            
            // Export for each consequence tool
            String baseName = nodeId + "_" + SIZE_NAMES[i].toLowerCase();
            result.exportToPHAST(baseName + "_phast.csv");
            result.exportToFLACS(baseName + "_flacs.csv");
            result.exportToJSON(baseName + ".json");
        }
        
        // Generate rupture case
        VesselDepressurization rupture = createRuptureCase(fluid, nodeId);
        rupture.exportResultsToCSV(nodeId + "_rupture.csv");
        
        // Generate summary documentation
        generateDocumentation(results, nodeId);
    }
    
    private void generateDocumentation(List<SourceTermResult> results, String nodeId) {
        StringBuilder doc = new StringBuilder();
        doc.append("# Source Term Summary - ").append(nodeId).append("\n\n");
        doc.append("| Scenario | Hole (mm) | mdot (kg/s) | T_rel (K) | Phase |\n");
        doc.append("|----------|-----------|-------------|-----------|-------|\n");
        
        for (int i = 0; i < results.size(); i++) {
            SourceTermResult r = results.get(i);
            doc.append(String.format("| %s | %.0f | %.2f | %.1f | %s |\n",
                SIZE_NAMES[i], HOLE_SIZES_MM[i],
                r.getMassFlowRate()[0], r.getTemperature()[0],
                r.getVaporFraction()[0] > 0.99 ? "Gas" : "Two-phase"));
        }
        
        // Write to file
        writeToFile(nodeId + "_summary.md", doc.toString());
    }
}
```

### Batch Processing Script

```java
// Process multiple nodes from process model
List<ProcessNode> nodes = loadProcessNodes("plant_model.json");

QRASourceTermGenerator generator = new QRASourceTermGenerator();

for (ProcessNode node : nodes) {
    SystemInterface fluid = node.getFluid();
    generator.generateSourceTerms(fluid, node.getId());
}

// Run consequence tool batch
executeConsequenceTool("PHAST", "output/*.csv");

// Import to QRA platform
importToQRAPlatform("Safeti", "consequence_results/");
```

---

## 8. Tool-Specific Export Formats

### PHAST Format

```csv
# PHAST Source Term Input
# Generated by NeqSim
Scenario,HP_SEP_Small_Leak
Hole_Diameter_mm,25.0
Discharge_Coefficient,0.62
Release_Rate_kg_s,5.23
Temperature_K,285.4
Pressure_barg,0.0
Phase,Gas
Molecular_Weight,17.2
Specific_Heat_Ratio,1.31
Duration_s,600.0
Inventory_kg,5000.0
```

### FLACS Format

```
! FLACS Source Term Definition
! Generated by NeqSim
&SOURCE
  NAME = 'HP_SEP_Leak'
  TYPE = 'JET'
  POSITION = 10.0, 5.0, 2.0
  DIRECTION = 1.0, 0.0, 0.0
  DIAMETER = 0.025
  MASS_FLOW = 5.23
  TEMPERATURE = 285.4
  VELOCITY = 412.5
  SPECIES = 'METHANE'
  DURATION = 600.0
/
```

### KFX Format

```xml
<?xml version="1.0" encoding="UTF-8"?>
<kfx_source_term>
  <scenario name="HP_SEP_Leak">
    <release_type>jet</release_type>
    <position x="10.0" y="5.0" z="2.0"/>
    <direction dx="1.0" dy="0.0" dz="0.0"/>
    <mass_flow unit="kg/s">5.23</mass_flow>
    <temperature unit="K">285.4</temperature>
    <phase>gas</phase>
    <composition>
      <species name="methane" fraction="0.85"/>
      <species name="ethane" fraction="0.10"/>
      <species name="propane" fraction="0.05"/>
    </composition>
    <duration unit="s">600.0</duration>
  </scenario>
</kfx_source_term>
```

### OpenFOAM Format

Generated files in case directory:

```
0/
  U           # Velocity boundary conditions
  T           # Temperature boundary conditions
  p           # Pressure boundary conditions
  CH4         # Species mass fraction
constant/
  sourceTerms # Time-varying source definition
```

---

## Appendix: Common Fluid Templates

### Natural Gas (North Sea Typical)

```java
SystemInterface natGas = new SystemSrkEos(300.0, 80.0);
natGas.addComponent("nitrogen", 0.01);
natGas.addComponent("CO2", 0.02);
natGas.addComponent("methane", 0.85);
natGas.addComponent("ethane", 0.07);
natGas.addComponent("propane", 0.03);
natGas.addComponent("i-butane", 0.01);
natGas.addComponent("n-butane", 0.01);
natGas.setMixingRule("classic");
```

### Condensate

```java
SystemInterface condensate = new SystemSrkEos(320.0, 50.0);
condensate.addComponent("methane", 0.05);
condensate.addComponent("ethane", 0.10);
condensate.addComponent("propane", 0.15);
condensate.addComponent("n-butane", 0.15);
condensate.addComponent("n-pentane", 0.20);
condensate.addComponent("n-hexane", 0.20);
condensate.addComponent("n-heptane", 0.15);
condensate.setMixingRule("classic");
```

### CO₂ Rich Stream

```java
SystemInterface co2Stream = new SystemSrkEos(310.0, 100.0);
co2Stream.addComponent("CO2", 0.95);
co2Stream.addComponent("methane", 0.03);
co2Stream.addComponent("nitrogen", 0.02);
co2Stream.setMixingRule("classic");
```

### Sour Gas (H₂S)

```java
SystemInterface sourGas = new SystemSrkEos(300.0, 60.0);
sourGas.addComponent("methane", 0.80);
sourGas.addComponent("H2S", 0.05);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("ethane", 0.05);
sourGas.setMixingRule("classic");
```

---

## References

- API 520: Sizing, Selection, and Installation of Pressure-Relieving Devices
- API 521: Pressure-relieving and Depressuring Systems
- NORSOK Z-013: Risk and emergency preparedness assessment
- ISO 4126: Safety devices for protection against excessive pressure
- DNV-RP-C208: Determination of structural capacity by non-linear FE analysis methods
- OGP Risk Assessment Data Directory (RADD)

---

*Document generated for NeqSim version 3.x*
*Last updated: December 2024*
