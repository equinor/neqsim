---
title: NeqSim as an Integrated Thermodynamic Backbone
description: A strategic guide for using NeqSim to unify production, flow assurance, and process safety workflows across the asset lifecycle.
---

# NeqSim as an Integrated Thermodynamic Backbone

A strategic guide for using NeqSim to unify production, flow assurance, and process safety workflows across the asset lifecycle.

---

## Executive Summary

NeqSim can serve as a **shared physics layer** that makes production, flow assurance, and process safety work faster, more consistent, and less conservative—while improving technical quality.

> **One-sentence takeaway:** NeqSim replaces fragmented assumptions with a shared, physics-based thermodynamic backbone across the entire asset lifecycle.

---

## Table of Contents

1. [The Core Problem Today](#1-the-core-problem-today)
2. [NeqSim's Strategic Role](#2-neqsims-strategic-role)
3. [Integrated Work Chain](#3-integrated-work-chain)
4. [Concrete Efficiency Gains](#4-concrete-efficiency-gains)
5. [Digital Twin & Lifecycle Benefits](#5-digital-twin--lifecycle-benefits)
6. [Organizational Impact](#6-organizational-impact)
7. [NeqSim Implementation Status](#7-neqsim-implementation-status)
8. [Getting Started](#8-getting-started)

---

## 1. The Core Problem Today

### Discipline Silos in Oil & Gas Organizations

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     TYPICAL DISCIPLINE SILOS                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐       │
│  │    Production    │    │  Flow Assurance  │    │  Process Safety  │       │
│  │    Engineers     │    │    Engineers     │    │    Engineers     │       │
│  ├──────────────────┤    ├──────────────────┤    ├──────────────────┤       │
│  │ • Steady-state   │    │ • OLGA/LEDaFlow  │    │ • PHAST/FLACS    │       │
│  │   simulators     │    │ • Spreadsheets   │    │ • Handbook       │       │
│  │ • HYSYS/UniSim   │    │ • In-house tools │    │   assumptions    │       │
│  │ • PRO/II         │    │                  │    │ • API correlations│      │
│  └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘       │
│           │                       │                       │                  │
│           │    Different          │    Different          │                  │
│           │    fluid models       │    fluid models       │                  │
│           ▼                       ▼                       ▼                  │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │                      INCONSISTENCY ZONE                           │       │
│  │  • Different compositions      • Different EOS parameters         │       │
│  │  • Different JT coefficients   • Different phase split methods    │       │
│  │  • Different Cp/Cv values      • Different water handling         │       │
│  └──────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Typical Pain Points

| Issue | Description | Consequence |
|-------|-------------|-------------|
| **Different fluid models** | Each discipline defines fluid independently | Inconsistent predictions |
| **Manual composition transfer** | Re-entry of compositions between tools | Transcription errors |
| **Inconsistent assumptions** | Different JT, Cp, phase split methods | Conflicting results |
| **Conservative stacking** | Each discipline adds safety margin | Over-design, wasted CAPEX |
| **Slow iteration** | Changes require re-work in all disciplines | Long lead times |

### The Result

```
┌─────────────────────────────────────────────────────────────┐
│  ❌ Long lead times (weeks for iteration cycles)            │
│  ❌ Excessive conservatism (stacked safety margins)         │
│  ❌ Fragile safety margins (based on assumptions)           │
│  ❌ Documentation burden (reconciling different models)     │
│  ❌ Late-stage surprises (when models disagree)            │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. NeqSim's Strategic Role

### Shared Physics Layer

NeqSim acts as a **single thermodynamic backbone** that feeds all disciplines consistently:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NEQSIM AS SHARED PHYSICS LAYER                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                         ┌──────────────────┐                                 │
│                         │      NeqSim      │                                 │
│                         │  Thermodynamic   │                                 │
│                         │     Backbone     │                                 │
│                         └────────┬─────────┘                                 │
│                                  │                                           │
│              ┌───────────────────┼───────────────────┐                       │
│              │                   │                   │                       │
│              ▼                   ▼                   ▼                       │
│     ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
│     │   Production   │  │ Flow Assurance │  │ Process Safety │              │
│     │    Models      │  │     Models     │  │    Studies     │              │
│     └────────────────┘  └────────────────┘  └────────────────┘              │
│                                                                              │
│     Same fluid │ Same EOS │ Same water handling │ Same hydrate logic        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Principle

> NeqSim does **not replace** specialist tools—it **feeds them consistently**.

| Specialist Tool | NeqSim's Role |
|-----------------|---------------|
| HYSYS / UniSim | Provide consistent fluid packages |
| OLGA / LEDaFlow | Provide boundary conditions and fluid tables |
| PHAST / FLACS / KFX | Provide source terms and release conditions |
| QRA platforms | Provide risk event frequencies and consequences |

---

## 3. Integrated Work Chain

### Step 1: Single Source of Truth for Fluids

```java
import neqsim.thermo.system.*;

// NeqSim defines the fluid ONCE for all disciplines
public class AssetFluidDefinition {
    
    public static SystemInterface createProductionFluid() {
        // Single definition used everywhere
        SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 80.0);
        
        // Hydrocarbon composition
        fluid.addComponent("nitrogen", 0.01);
        fluid.addComponent("CO2", 0.02);
        fluid.addComponent("methane", 0.78);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("i-butane", 0.02);
        fluid.addComponent("n-butane", 0.02);
        fluid.addComponent("n-pentane", 0.01);
        fluid.addComponent("n-hexane", 0.01);
        
        // Water and inhibitors (CPA handles association)
        fluid.addComponent("water", 0.005);
        fluid.addComponent("MEG", 0.002);
        
        fluid.setMixingRule("classic");
        fluid.createDatabase(true);
        
        return fluid;
    }
}
```

**Benefits:**

| Benefit | Description |
|---------|-------------|
| ✅ **EOS consistency** | Same equation of state across all disciplines |
| ✅ **Pseudo-component alignment** | Heavy ends handled identically |
| ✅ **Water/MEG handling** | CPA or other models applied consistently |
| ✅ **Hydrate model alignment** | Same hydrate predictions everywhere |
| ✅ **Eliminates re-tuning** | No need to match fluid between tools |

---

### Step 2: Production → Flow Assurance Handover

#### Traditional Handover (Manual)

```
Production delivers:                Flow assurance receives:
─────────────────────              ─────────────────────────
• Flow rates                        • Simplified compositions
• P/T at key nodes                  • Handbook properties
• Basic composition                 • Re-tuned fluid model
```

#### NeqSim-Enhanced Handover

```java
import neqsim.process.equipment.stream.*;
import neqsim.thermo.system.*;

public class ProductionToFlowAssuranceHandover {
    
    /**
     * Creates a complete handover package for flow assurance.
     */
    public FlowAssuranceHandover createHandover(Stream productionNode) {
        SystemInterface fluid = productionNode.getThermoSystem();
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        
        FlowAssuranceHandover handover = new FlowAssuranceHandover();
        
        // Complete thermodynamic state
        handover.pressure_bara = fluid.getPressure();
        handover.temperature_K = fluid.getTemperature();
        handover.massFlowRate_kg_s = productionNode.getFlowRate("kg/sec");
        
        // Phase fractions
        handover.vaporFraction = fluid.getPhase(0).getBeta();
        handover.liquidFraction = 1.0 - handover.vaporFraction;
        handover.waterFraction = fluid.getPhase("aqueous") != null 
            ? fluid.getPhase("aqueous").getBeta() : 0.0;
        
        // Gas properties
        handover.gasDensity_kg_m3 = fluid.getPhase("gas").getDensity("kg/m3");
        handover.gasViscosity_cP = fluid.getPhase("gas").getViscosity("cP");
        handover.gasCp_J_kgK = fluid.getPhase("gas").getCp("J/kgK");
        handover.gasZ = fluid.getPhase("gas").getZ();
        
        // Liquid properties (if present)
        if (handover.liquidFraction > 0.001) {
            handover.liquidDensity_kg_m3 = fluid.getPhase("oil").getDensity("kg/m3");
            handover.liquidViscosity_cP = fluid.getPhase("oil").getViscosity("cP");
        }
        
        // Joule-Thomson coefficient
        handover.JT_K_bar = fluid.getJouleThomsonCoefficient();
        
        // Hydrate equilibrium temperature
        ops.hydrateFormationTemperature();
        handover.hydrateTemperature_K = fluid.getTemperature();
        handover.hydrateMargin_K = productionNode.getTemperature("K") 
            - handover.hydrateTemperature_K;
        
        // Wax appearance temperature (if applicable)
        try {
            ops.calcWAT();
            handover.waxTemperature_K = fluid.getTemperature();
        } catch (Exception e) {
            handover.waxTemperature_K = Double.NaN;
        }
        
        return handover;
    }
}
```

**Flow Assurance Benefits:**

| Benefit | Impact |
|---------|--------|
| **Better inlet conditions** | Accurate boundary for OLGA/LEDaFlow |
| **Reduced uncertainty** | Slugging, liquid dropout, thermal profiles |
| **Hydrate margins** | Pre-calculated, consistent with production |
| **Real JT coefficients** | Not handbook values |

---

### Step 3: Flow Assurance → Safety Handover

This is where NeqSim provides the most value.

#### Typical Safety Problem

> "What is released if this line ruptures at node X?"

**Traditional approach:**
- Flow assurance model is dynamic but not safety-oriented
- Safety engineer re-estimates properties manually
- Uses handbook values for JT, Cp/Cv, phase split

#### NeqSim-Enhanced Safety Handover

`LeakModel` blowdown is bounded to a rigid, adiabatic single gas phase. Its corrected
inventory integration conserves mass and energy, includes the exact requested final time,
and rejects condensation/property failures. Historical results must be regenerated; see
[the blowdown compatibility note](../process/safety/release-dispersion-scenarios.md#conservative-gas-blowdown-and-compatibility).

```java
import neqsim.process.safety.release.*;
import neqsim.process.equipment.tank.*;

public class FlowAssuranceToSafetyHandover {
    
    /**
     * Creates source terms for safety analysis from flow assurance node.
     */
    public SafetySourceTerm createSafetyHandover(
            SystemInterface fluidAtNode,
            double holeDiameter_mm,
            double inventoryVolume_m3) {
        
        // Create leak model with exact local fluid state
        LeakModel leak = LeakModel.builder()
            .fluid(fluidAtNode)
            .holeDiameter(holeDiameter_mm, "mm")
            .dischargeCoefficient(0.62)
            .vesselVolume(inventoryVolume_m3)
            .build();
        
        // Calculate transient source term
        SourceTermResult result = leak.calculateSourceTerm(600.0, 1.0);
        
        SafetySourceTerm handover = new SafetySourceTerm();
        
        // Release characteristics
        handover.peakMassFlow_kg_s = result.getPeakMassFlowRate();
        handover.releaseTemperature_K = result.getTemperature()[0];
        handover.isChoked = result.isChoked()[0];
        handover.vaporFraction = result.getVaporFraction()[0];
        
        // For minimum metal temperature assessment
        if (inventoryVolume_m3 > 0) {
            VesselDepressurization blowdown = createBlowdownCase(
                fluidAtNode, inventoryVolume_m3, holeDiameter_mm);
            handover.minimumTemperature_K = blowdown.getMinimumWallTemperatureReached();
            handover.timeToMinTemp_s = blowdown.getTimeToMinimumTemperature();
        }
        
        // Export for consequence tools
        result.exportToPHAST("node_" + holeDiameter_mm + "mm_phast.csv");
        result.exportToFLACS("node_" + holeDiameter_mm + "mm_flacs.csv");
        
        return handover;
    }
}
```

**Safety Benefits:**

| Benefit | Impact |
|---------|--------|
| **Realistic source terms** | Based on actual fluid, not assumptions |
| **Exact phase split** | Not conservative "all liquid" or "all gas" |
| **Correct release temperature** | Isenthalpic expansion properly modeled |
| **MDMT assessment** | Minimum metal temperature from transient |
| **Consistent assumptions** | Same as production and flow assurance |

---

### Step 4: Unified Treatment of Transient Events

NeqSim sits at the center of transient scenarios that span all disciplines:

| Scenario | Production View | Flow Assurance View | Safety View |
|----------|-----------------|---------------------|-------------|
| **Start-up** | Flow ramp-up | Liquid loading, hydrate risk | Cold vent risk |
| **Shutdown** | Rate decay | Holdup redistribution | Blowdown cooling |
| **ESD** | Valve closure | Pressure waves | Rupture / PSV lift |
| **Restart** | Thermal mismatch | Hydrates in dead legs | Ignition risk |
| **Turndown** | Low flow | Slugging, liquid accumulation | PSV sizing margin |

#### NeqSim Provides Unified Physics

```java
import neqsim.process.safety.envelope.*;

public class TransientScenarioAnalysis {
    
    /**
     * Analyzes a transient scenario across all discipline concerns.
     */
    public TransientAnalysisResult analyzeScenario(
            SystemInterface fluid,
            double initialPressure,
            double finalPressure,
            double ambientTemperature) {
        
        TransientAnalysisResult result = new TransientAnalysisResult();
        
        // Safety envelope calculator
        SafetyEnvelopeCalculator envCalc = new SafetyEnvelopeCalculator(fluid);
        
        // Calculate all relevant envelopes
        SafetyEnvelope hydrateEnv = envCalc.calculateHydrateEnvelope(
            finalPressure, initialPressure, 20);
        SafetyEnvelope mdmtEnv = envCalc.calculateMDMTEnvelope(
            finalPressure, initialPressure, ambientTemperature + 273.15, 20);
        SafetyEnvelope co2Env = envCalc.calculateCO2FreezingEnvelope(
            finalPressure, initialPressure, 10);
        
        // Check operating path against envelopes
        result.hydrateRiskDuringTransient = !hydrateEnv.isOperatingPointSafe(
            initialPressure / 2, ambientTemperature + 273.15);
        result.mdmtRiskDuringBlowdown = !mdmtEnv.isOperatingPointSafe(
            finalPressure, ambientTemperature + 273.15 - 50);
        result.co2FreezingRisk = !co2Env.isOperatingPointSafe(
            finalPressure, 220.0);
        
        // Calculate thermodynamic path
        result.thermodynamicPath = calculateDepressurizationPath(
            fluid, initialPressure, finalPressure);
        
        return result;
    }
}
```

---

## 4. Concrete Efficiency Gains

### 4.1 Faster Iteration Loops

#### Without NeqSim

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TRADITIONAL ITERATION LOOP                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Production ──► Flow Assurance ──► Safety ──► Back to Production            │
│                                                                              │
│  Timeline: WEEKS                                                             │
│                                                                              │
│  • Each discipline re-defines fluid                                         │
│  • Manual handover documents                                                │
│  • Review cycles for consistency                                            │
│  • Reconciliation meetings                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### With NeqSim

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NEQSIM-ENABLED ITERATION LOOP                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                    ┌──────────────┐                                          │
│                    │   Change     │                                          │
│                    │ (P/T/comp)   │                                          │
│                    └──────┬───────┘                                          │
│                           │                                                  │
│                           ▼                                                  │
│                    ┌──────────────┐                                          │
│                    │    NeqSim    │                                          │
│                    │   Update     │                                          │
│                    └──────┬───────┘                                          │
│                           │                                                  │
│              ┌────────────┼────────────┐                                     │
│              ▼            ▼            ▼                                     │
│         ┌────────┐  ┌──────────┐  ┌────────┐                                │
│         │Updated │  │ Updated  │  │Updated │                                │
│         │  FA    │  │  Safety  │  │  Prod  │                                │
│         │Inputs  │  │  Inputs  │  │ Inputs │                                │
│         └────────┘  └──────────┘  └────────┘                                │
│                                                                              │
│  Timeline: HOURS TO DAYS                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Especially powerful for:**

| Application | Time Savings |
|-------------|--------------|
| Late-phase design changes | Days → Hours |
| Brownfield modifications | Weeks → Days |
| Debottlenecking studies | Weeks → Days |
| What-if scenarios | Days → Hours |
| Sensitivity studies | Manual → Automated |

---

### 4.2 Reduced Conservatism (Without Reducing Safety)

#### Sources of Conservatism Today

| Source | Traditional Approach | NeqSim Approach |
|--------|---------------------|-----------------|
| **Ideal gas assumptions** | Handbook γ = 1.3 | Actual γ from EOS |
| **Worst-case phase** | "Assume all liquid" | Actual flash calculation |
| **Handbook JT values** | Generic curves | Composition-specific JT |
| **Safety margin stacking** | Each discipline adds margin | Single, transparent margin |

#### Impact Example: PSV Sizing

```java
// Traditional: Conservative assumptions
double traditionalArea = calculatePSVArea_Traditional(
    flowRate,
    gamma_assumed = 1.3,           // Handbook value
    Z_assumed = 1.0,               // Ideal gas
    MW_assumed = 18.0              // Light estimate
);

// NeqSim: Case-specific thermodynamics
SystemInterface fluid = getActualFluid();
double neqsimArea = calculatePSVArea_NeqSim(
    flowRate,
    gamma = fluid.getGamma(),       // Actual: 1.18
    Z = fluid.getZ(),               // Actual: 0.85
    MW = fluid.getMolarMass()       // Actual: 21.5
);

// Result: NeqSim area may be 15-25% smaller
// → Same safety level, smaller/cheaper valve
```

**Key insight:**
> Safety decisions become **risk-based**, not **assumption-based**

---

### 4.3 Fewer Handover Errors

#### Error Sources Eliminated

| Error Type | Traditional | With NeqSim |
|------------|-------------|-------------|
| Composition transcription | Common | Eliminated |
| Unit conversion mistakes | Occasional | Eliminated |
| EOS mismatch | Frequent | Eliminated |
| Water content disagreement | Common | Eliminated |
| Hydrate model differences | Frequent | Eliminated |

#### Quantified Impact

```
┌─────────────────────────────────────────────────────────────────┐
│              HANDOVER ERROR REDUCTION                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Documentation errors:      ▓▓▓▓▓▓▓▓▓▓ 100% → ▓▓ 20%           │
│  Review comments:           ▓▓▓▓▓▓▓▓▓▓ 100% → ▓▓▓ 30%          │
│  Late-stage surprises:      ▓▓▓▓▓▓▓▓▓▓ 100% → ▓ 10%            │
│  Reconciliation meetings:   ▓▓▓▓▓▓▓▓▓▓ 100% → ▓▓▓▓ 40%         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Digital Twin & Lifecycle Benefits

### Real-Time Operations Integration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NEQSIM IN DIGITAL TWIN ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐           │
│  │   Field     │ ──────► │   NeqSim    │ ──────► │  Decision   │           │
│  │   Data      │         │   Engine    │         │   Support   │           │
│  │  (PI/OPC)   │         │             │         │             │           │
│  └─────────────┘         └──────┬──────┘         └─────────────┘           │
│                                 │                                           │
│                    ┌────────────┴────────────┐                              │
│                    ▼                         ▼                              │
│           ┌─────────────────┐       ┌─────────────────┐                    │
│           │  Real-Time      │       │  Safety         │                    │
│           │  Monitoring     │       │  Assessment     │                    │
│           │  • Hydrate      │       │  • Barrier      │                    │
│           │    margin       │       │    status       │                    │
│           │  • Two-phase    │       │  • SIMOPS       │                    │
│           │    risk         │       │    evaluation   │                    │
│           │  • MDMT during  │       │  • Degraded     │                    │
│           │    blowdown     │       │    mode ops     │                    │
│           └─────────────────┘       └─────────────────┘                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### During Operations

```java
import neqsim.process.safety.envelope.*;

public class RealTimeMonitoring {
    
    private SafetyEnvelopeCalculator envelopeCalc;
    private SystemInterface currentFluid;
    
    /**
     * Called periodically with live data from field.
     */
    public MonitoringResult updateFromLiveData(
            double pressure_bara,
            double temperature_K,
            Map<String, Double> composition) {
        
        // Update fluid state
        currentFluid.setTemperature(temperature_K);
        currentFluid.setPressure(pressure_bara);
        ThermodynamicOperations ops = new ThermodynamicOperations(currentFluid);
        ops.TPflash();
        
        MonitoringResult result = new MonitoringResult();
        
        // Hydrate margin assessment
        ops.hydrateFormationTemperature();
        double hydrateTemp = currentFluid.getTemperature();
        result.hydrateMargin_K = temperature_K - hydrateTemp;
        result.hydrateAlarm = result.hydrateMargin_K < 5.0;
        
        // Two-phase risk
        result.vaporFraction = currentFluid.getPhase(0).getBeta();
        result.twoPhaseRisk = result.vaporFraction > 0.05 && result.vaporFraction < 0.95;
        
        // MDMT risk during potential blowdown
        SafetyEnvelope mdmtEnv = envelopeCalc.calculateMDMTEnvelope(
            1.0, pressure_bara, temperature_K, 10);
        result.blowdownMinTemp_K = mdmtEnv.getTemperature()[9]; // At 1 bara
        result.mdmtAlarm = result.blowdownMinTemp_K < 233.0; // -40°C
        
        return result;
    }
}
```

### During Safety Management

| Assessment | NeqSim Capability |
|------------|-------------------|
| Barrier effectiveness | Real-time calculation of relief capacity |
| Safety envelope monitoring | Live comparison to calculated limits |
| SIMOPS evaluation | Impact of concurrent operations |
| Degraded mode operation | Assessment of reduced barriers |

### Lifecycle Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│     Design ─────────────► Operate ─────────────► Safeguard      │
│        ▲                     │                       │          │
│        │                     │                       │          │
│        │         ┌───────────┴───────────┐          │          │
│        │         │        NeqSim         │          │          │
│        │         │  Thermodynamic Core   │          │          │
│        │         └───────────────────────┘          │          │
│        │                     │                       │          │
│        └─────────────────────┴───────────────────────┘          │
│                        Feedback Loop                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Organizational Impact

### Technical Benefits

| Impact | Description |
|--------|-------------|
| **Cross-discipline language** | Shared terminology and units |
| **Early assumption alignment** | Agreed EOS and methods upfront |
| **Reduced tool-ownership silos** | Focus on physics, not software |
| **Audit trail** | Transparent, reproducible calculations |

### Team Benefits

| Impact | Description |
|--------|-------------|
| **Reuse of PhD/research work** | Academic contributions directly usable |
| **Open, auditable calculations** | No "black box" concerns |
| **Easier onboarding** | New engineers learn one system |
| **Knowledge preservation** | Methods captured in code |

### Project Benefits

```
┌─────────────────────────────────────────────────────────────────┐
│              PROJECT EFFICIENCY IMPROVEMENTS                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Engineering hours:         Reduced 20-30%                      │
│  Review cycles:             Reduced 40-50%                      │
│  Late changes impact:       Reduced 50-60%                      │
│  Documentation effort:      Reduced 30-40%                      │
│  Consistency issues:        Reduced 70-80%                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. NeqSim Implementation Status

### Standardized Handover Objects

| Object | Status | Package |
|--------|--------|---------|
| `SystemInterface` (Fluid State) | ✅ Complete | `neqsim.thermo.system` |
| `SourceTermResult` | ✅ Complete | `neqsim.process.safety.release` |
| `SafetyEnvelope` | ✅ Complete | `neqsim.process.safety.envelope` |
| `RiskEvent` / `RiskResult` | ✅ Complete | `neqsim.process.safety.risk` |
| `ProcessSafetyScenario` | ✅ Complete | `neqsim.process.safety` |
| `BoundaryConditions` | ✅ Complete | `neqsim.process.safety` |

### Dynamic Capabilities

| Capability | Status | Implementation |
|------------|--------|----------------|
| Blowdown transient | ✅ Complete | `VesselDepressurization` |
| Leak/rupture source term | ✅ Complete | `LeakModel` |
| Phase envelope | ✅ Complete | `ThermodynamicOperations` |
| Hydrate formation | ✅ Complete | `ThermodynamicOperations` |
| WAT/Wax | ✅ Complete | `ThermodynamicOperations` |

### Export Adapters

| Target | Status | Method |
|--------|--------|--------|
| PHAST | ✅ Complete | `exportToPHAST()` |
| FLACS | ✅ Complete | `exportToFLACS()` |
| KFX | ✅ Complete | `exportToKFX()` |
| OpenFOAM | ✅ Complete | `exportToOpenFOAM()` |
| CSV (generic) | ✅ Complete | `exportToCSV()` |
| JSON (generic) | ✅ Complete | `exportToJSON()` |
| PI Format | ✅ Complete | `exportToPIFormat()` |
| Seeq | ✅ Complete | `exportToSeeq()` |
| OLGA PVT tables | 🔄 Partial | Under development |

### Assumption Transparency

| Feature | Status | Description |
|---------|--------|-------------|
| EOS selection | ✅ Explicit | `SystemSrkEos`, `SystemPrEos`, etc. |
| Mixing rules | ✅ Explicit | `setMixingRule()` |
| Flash type | ✅ Explicit | `TPflash()`, `PHflash()`, etc. |
| Discharge model | ✅ Documented | HEM, isenthalpic expansion |
| Hydrate model | ✅ Explicit | CPA, van der Waals-Platteeuw |

---

## 8. Getting Started

### Quick Start: Define Asset Fluid

```java
import neqsim.thermo.system.*;

// Step 1: Create fluid with appropriate EOS
SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 80.0);

// Step 2: Add components (single definition for all disciplines)
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-butane", 0.02);
fluid.addComponent("water", 0.01);

// Step 3: Set mixing rules
fluid.setMixingRule("classic");
fluid.createDatabase(true);

// Step 4: Flash to get equilibrium state
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Now this fluid can feed:
// - Production models
// - Flow assurance boundary conditions
// - Safety source terms
```

### Quick Start: Generate Safety Source Terms

```java
import neqsim.process.safety.release.*;

// Create leak model from asset fluid
LeakModel leak = LeakModel.builder()
    .fluid(fluid)
    .holeDiameter(25.0, "mm")
    .dischargeCoefficient(0.62)
    .vesselVolume(10.0)
    .build();

// Calculate and export
SourceTermResult result = leak.calculateSourceTerm(600.0, 1.0);
result.exportToPHAST("source_term.csv");
result.exportToFLACS("source_term_flacs.csv");
```

### Quick Start: Calculate Safety Envelopes

```java
import neqsim.process.safety.envelope.*;

// Create envelope calculator
SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(fluid);

// Calculate all relevant envelopes
SafetyEnvelope hydrate = calc.calculateHydrateEnvelope(1.0, 100.0, 20);
SafetyEnvelope mdmt = calc.calculateMDMTEnvelope(1.0, 100.0, 300.0, 20);

// Export for DCS/historian
hydrate.exportToPIFormat("hydrate_limits.csv");
mdmt.exportToPIFormat("mdmt_limits.csv");
```

---

## References

- [NeqSim QRA Integration Guide](../integration/QRA_INTEGRATION_GUIDE)
- [Safety Simulation Roadmap](../safety/SAFETY_SIMULATION_ROADMAP)
- [NeqSim Documentation](https://equinor.github.io/neqsim/)
- [NeqSim GitHub Repository](https://github.com/equinor/neqsim)

---

*Document version: 1.0*
*Last updated: December 2024*
