---
title: NeqSim Safety Simulation Roadmap
description: A comprehensive analysis of existing safety capabilities and a realistic implementation plan for enhancing NeqSim's safety simulation features.
---

# NeqSim Safety Simulation Roadmap

A comprehensive analysis of existing safety capabilities and a realistic implementation plan for enhancing NeqSim's safety simulation features.

---

## Executive Summary

NeqSim already has **substantial safety infrastructure** that covers approximately **90-95%** of the proposed roadmap. 

**Recently Implemented (2024):**
- ✅ `LeakModel` - Choked/subsonic flow with time-series source terms
- ✅ `SourceTermResult` - Export to PHAST, FLACS, KFX, OpenFOAM
- ✅ `InitiatingEvent` enum - Standard safety scenario initiators
- ✅ `BoundaryConditions` - Environmental conditions with geographic presets
- ✅ `RiskModel` - Monte Carlo, event trees, sensitivity analysis (tornado diagrams)
- ✅ `RiskEvent` / `RiskResult` - Probabilistic risk quantification with F-N curves
- ✅ `SafetyEnvelopeCalculator` - Hydrate, wax, CO2, MDMT, phase envelope calculation
- ✅ `SafetyEnvelope` - P-T curve container with DCS/PI/Seeq export

**Remaining Gaps:**
1. Dynamic PSV back-pressure modeling
2. Reaction force calculations per API 520 Annex D
3. Two-phase relief sizing (API 520 Section 4.6)

---

## 1. Current State Analysis

### ✅ 3.1 Native Safety Scenario Framework — **LARGELY EXISTS**

| Feature | Status | Implementation |
|---------|--------|----------------|
| `SafetyScenario` API | ✅ Exists | `ProcessSafetyScenario` with Builder pattern |
| Initiating events | ✅ Exists | Blocked outlets, utility loss, controller overrides |
| Custom manipulators | ✅ Exists | Lambda-based equipment manipulation |
| Scenario execution | ✅ Exists | `ProcessSafetyAnalyzer.analyzeScenario()` |
| Load case definition | ✅ Exists | `ProcessSafetyLoadCase` |
| Result repository | ✅ Exists | `ProcessSafetyResultRepository` |

**Existing Code Location:** `neqsim.process.safety.*`

```java
// Current API
ProcessSafetyScenario scenario = ProcessSafetyScenario.builder()
    .name("Compressor blowdown")
    .blockedOutlet("V-101")
    .utilityLoss("Cooling-Water")
    .controllerSetPointOverride("PC-101", 50.0)
    .customManipulator("SEP-001", eq -> eq.setRegulatorOutSignal(0.0))
    .build();

ProcessSafetyAnalyzer analyzer = new ProcessSafetyAnalyzer(processSystem);
ProcessSafetyLoadCase result = analyzer.analyzeScenario(scenario);
```

**Implemented Additions:**
- [x] `InitiatingEvent` enum (ESD, PSV_LIFT, RUPTURE, LEAK_SMALL, LEAK_MEDIUM, LEAK_LARGE, FIRE_EXPOSURE, etc.)
- [x] `BoundaryConditions` class (ambient temp, wind speed, humidity, stability class, presets for North Sea, Gulf of Mexico, etc.)

**Remaining Gaps:**
- [ ] Automatic inventory extraction from scenarios
- [ ] Phase state tracking during scenario execution

---

### ✅ 3.2 Dynamic Depressurization & Blowdown — **FULLY IMPLEMENTED**

| Feature | Status | Implementation |
|---------|--------|----------------|
| Time-dependent pressure | ✅ Complete | `VesselDepressurization.runTransient()` |
| Time-dependent temperature | ✅ Complete | Energy balance, adiabatic, isothermal modes |
| Phase split evolution | ✅ Complete | Two-phase support with separate wall temps |
| Choked vs non-choked flow | ✅ Complete | Critical pressure ratio calculation |
| Real-gas speed of sound | ✅ Complete | NeqSim thermodynamics |
| MDMT prediction | ✅ Complete | `getMinimumWallTemperatureReached()` |
| Blowdown duration | ✅ Complete | `getTimeToReachPressure()` |
| Two-phase formation timing | ✅ Complete | `hasLiquidRainout()` |
| Valve/piping temperature | ✅ Complete | `TransientWallHeatTransfer` |
| Fire case (API 521) | ✅ Complete | `setFireCase()`, heat flux models |
| Valve dynamics | ✅ Complete | `setValveOpeningTime()` |
| Hydrate risk | ✅ Complete | `getHydrateFormationTemperature()`, `hasHydrateRisk()` |
| CO2 freezing risk | ✅ Complete | `getCO2FreezingTemperature()`, `hasCO2FreezingRisk()` |
| Export to CSV/JSON | ✅ Complete | `exportResultsToCSV()`, `exportResultsToJSON()` |

**Existing Code Location:** 
- `neqsim.process.equipment.tank.VesselDepressurization` (~3000 lines)
- `neqsim.process.util.fire.TransientWallHeatTransfer`
- `neqsim.process.util.fire.VesselHeatTransferCalculator`

```java
// Current API
VesselDepressurization vessel = new VesselDepressurization("Tank", feed);
vessel.setVolume(10.0);
vessel.setOrificeDiameter(0.03);
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setFireCase(true, 100.0); // 100 kW/m² fire

vessel.runTransient(dt, uuid);

// Flow assurance
Map<String, String> risks = vessel.assessFlowAssuranceRisks();
```

**Status: COMPLETE** ✓

---

### ⚠️ 3.3 Relief and Vent System Modeling — **PARTIALLY EXISTS**

| Feature | Status | Implementation |
|---------|--------|----------------|
| SafetyValve class | ✅ Exists | `SafetyValve` extends `ThrottlingValve` |
| SafetyReliefValve | ✅ Exists | `SafetyReliefValve` with lift dynamics |
| Set pressure / blowdown | ✅ Exists | `setPressureSpec()`, `setBlowdownPressure()` |
| API 520 sizing | ✅ Exists | `ReliefValveSizing.calculateRequiredArea()` |
| API 521 fire heat input | ✅ Exists | `calculateAPI521HeatInput()` |
| Relieving scenarios | ✅ Exists | `RelievingScenario` class |
| Choked flow models | ✅ Exists | Critical flow calculations |
| Balanced-bellows support | ✅ Exists | `isBalancedBellows` parameter |
| Rupture disk combination | ✅ Exists | `hasRuptureDisk` parameter |
| Back pressure effects | ⚠️ Partial | Static calculation, needs dynamic |
| Reaction forces | ❌ Missing | Not implemented |
| Flare connection | ⚠️ Partial | `Flare` class exists, limited integration |

**Existing Code Location:**
- `neqsim.process.equipment.valve.SafetyValve`
- `neqsim.process.equipment.valve.SafetyReliefValve`
- `neqsim.process.util.fire.ReliefValveSizing`

**Gaps to Address:**
- [ ] Dynamic back pressure model (time-dependent during relief)
- [ ] Reaction force calculation per API 520 Annex D
- [ ] Discharge piping pressure drop
- [ ] Multiple PSV coordination
- [ ] Two-phase relief sizing (API 520 Section 4.6)

---

### ✅ 3.4 Leak & Rupture Source Term Generator — **IMPLEMENTED**

| Feature | Status | Implementation |
|---------|--------|----------------|
| Leak model class | ✅ Complete | `LeakModel` with Builder pattern |
| Hole diameter specification | ✅ Complete | `holeDiameter(double, String unit)` |
| Mass flow vs time | ✅ Complete | `calculateSourceTerm()` returns time series |
| Gas/liquid split | ✅ Complete | Vapor fraction tracked at each timestep |
| Jet momentum | ✅ Complete | `calculateJetMomentum()` |
| Release temperature | ✅ Complete | Temperature tracked with isentropic expansion |
| Droplet size estimate | ✅ Complete | SMD via modified Weber number correlation |
| Export to PHAST/FLACS/KFX/OpenFOAM | ✅ Complete | All export methods implemented |

**Implementation Location:** `neqsim.process.safety.release.*`

- `LeakModel` - Main leak/rupture model with choked/subsonic flow
- `SourceTermResult` - Time-series container with QRA tool export
- `ReleaseOrientation` - Enum for release directions
- `InitiatingEvent` - Enum for scenario initiating events (in `neqsim.process.safety`)
- `BoundaryConditions` - Environmental conditions with presets (in `neqsim.process.safety`)

```java
// Example usage
LeakModel leak = LeakModel.builder()
    .fluid(system)
    .holeDiameter(25.0, "mm")
    .vesselVolume(10.0)
    .orientation(ReleaseOrientation.HORIZONTAL)
    .scenarioName("HP Separator Leak")
    .build();

SourceTermResult result = leak.calculateSourceTerm(600.0, 1.0); // 10 min, 1s step
result.exportToPHAST("leak_phast.csv");
result.exportToFLACS("leak_flacs.csv");
result.exportToKFX("leak_kfx.csv");
result.exportToOpenFOAM("/path/to/openfoam/case");
```

**Original API design (now implemented):**

```java
// New package: neqsim.process.safety.release

public class LeakModel {
    private double holeDiameter;  // m
    private String location;
    private LeakOrientation orientation; // HORIZONTAL, VERTICAL_UP, VERTICAL_DOWN
    
    public SourceTermResult calculateSourceTerm(SystemInterface system);
}

public class SourceTermResult {
    private double[] time;           // s
    private double[] massFlowRate;   // kg/s
    private double[] temperature;    // K
    private double[] vaporFraction;  // mol/mol
    private double[] jetMomentum;    // N
    private double[] liquidDropletSize; // m (SMD)
    
    // Export methods
    public void exportToPHAST(String filename);
    public void exportToFLACS(String filename);
    public void exportToKFX(String filename);
    public void exportToOpenFOAM(String directory);
}
```

---

### ✅ 3.5 Probabilistic & Risk Integration — **IMPLEMENTED**

| Feature | Status | Implementation |
|---------|--------|----------------|
| Monte Carlo analysis | ✅ Complete | `RiskModel.runMonteCarloAnalysis(iterations)` |
| Failure frequencies | ✅ Complete | `RiskEvent` with frequency and error factors |
| Conditional probabilities | ✅ Complete | `RiskEvent` with parent events and conditional probability |
| Event tree logic | ✅ Complete | `RiskEvent.parentEvent()` for event tree branching |
| Sensitivity analysis | ✅ Complete | `RiskModel.runSensitivityAnalysis()` with tornado diagrams |
| Risk quantification | ✅ Complete | `RiskResult` with category frequencies and F-N curves |
| Consequence categories | ✅ Complete | `ConsequenceCategory` enum (NEGLIGIBLE to CATASTROPHIC) |
| Export to CSV/JSON | ✅ Complete | `RiskResult.exportToCSV()`, `exportToJSON()` |

**Implementation Location:** `neqsim.process.safety.risk.*`

- `RiskEvent` - Individual risk event with frequency, probability, consequence category
- `RiskModel` - Monte Carlo simulation and sensitivity analysis engine
- `RiskResult` - Results container with F-N curves and export methods
- `SensitivityResult` - Tornado diagram data and sensitivity indices

```java
// Example usage
RiskModel model = new RiskModel("HP Separator Study");
model.setRandomSeed(42);

// Add initiating events with frequencies (per year)
model.addInitiatingEvent("Small Leak", 1e-3, ConsequenceCategory.MINOR);
model.addInitiatingEvent("Medium Leak", 1e-4, ConsequenceCategory.MODERATE);
model.addInitiatingEvent("Large Rupture", 1e-5, ConsequenceCategory.MAJOR);

// Or use builder pattern for event trees
RiskEvent fireEvent = RiskEvent.builder()
    .name("Fire on Leak")
    .parentEvent(leakEvent)
    .conditionalProbability(0.1)
    .consequenceCategory(ConsequenceCategory.MAJOR)
    .build();
model.addEvent(fireEvent);

// Run Monte Carlo analysis
RiskResult result = model.runMonteCarloAnalysis(10000);
result.exportToCSV("risk_results.csv");

// Run sensitivity analysis (tornado diagram)
SensitivityResult sensitivity = model.runSensitivityAnalysis(0.1, 10.0);
sensitivity.exportToCSV("sensitivity.csv");
```

---

### ✅ 3.6 Safety Envelopes & Operating Limits — **IMPLEMENTED**

| Feature | Status | Implementation |
|---------|--------|----------------|
| Hydrate envelope | ✅ Complete | `SafetyEnvelopeCalculator.calculateHydrateEnvelope()` |
| Wax appearance | ✅ Complete | `SafetyEnvelopeCalculator.calculateWaxEnvelope()` |
| MDMT/Brittle fracture | ✅ Complete | `SafetyEnvelopeCalculator.calculateMDMTEnvelope()` |
| CO2 solid formation | ✅ Complete | `SafetyEnvelopeCalculator.calculateCO2FreezingEnvelope()` |
| Phase envelope | ✅ Complete | `SafetyEnvelopeCalculator.calculatePhaseEnvelope()` |
| Temperature interpolation | ✅ Complete | `SafetyEnvelope.getTemperatureAtPressure()` |
| Operating point check | ✅ Complete | `SafetyEnvelope.isOperatingPointSafe()` |
| Safety margin calc | ✅ Complete | `SafetyEnvelope.calculateMarginToLimit()` |
| Export to CSV/JSON | ✅ Complete | `SafetyEnvelope.exportToCSV()`, `exportToJSON()` |
| Export to PI/Seeq | ✅ Complete | `SafetyEnvelope.exportToPIFormat()`, `exportToSeeq()` |

**Implementation Location:** `neqsim.process.safety.envelope.*`

- `SafetyEnvelope` - P-T curve container with interpolation and export methods
- `SafetyEnvelopeCalculator` - Calculates hydrate, wax, CO2, MDMT, phase envelopes
- `EnvelopeType` - Enum for HYDRATE, WAX, CO2_FREEZING, MDMT, PHASE_ENVELOPE, BRITTLE_FRACTURE

```java
// Example usage
SystemInterface naturalGas = new SystemSrkEos(300.0, 50.0);
naturalGas.addComponent("methane", 0.85);
naturalGas.addComponent("ethane", 0.10);
naturalGas.addComponent("propane", 0.05);
naturalGas.setMixingRule("classic");

SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(naturalGas);

// Calculate safety envelopes
SafetyEnvelope hydrateEnv = calc.calculateHydrateEnvelope(1.0, 100.0, 20);
SafetyEnvelope co2Env = calc.calculateCO2FreezingEnvelope(10.0, 100.0, 10);
SafetyEnvelope mdmtEnv = calc.calculateMDMTEnvelope(1.0, 100.0, 300.0, 10);

// Check operating point safety
boolean safe = hydrateEnv.isOperatingPointSafe(50.0, 280.0);
double margin = hydrateEnv.calculateMarginToLimit(50.0, 280.0);

// Export for DCS/historian integration
hydrateEnv.exportToCSV("hydrate_envelope.csv");
hydrateEnv.exportToPIFormat("hydrate_pi.csv");
hydrateEnv.exportToSeeq("hydrate_seeq.json");

// Calculate all envelopes at once
SafetyEnvelope[] allEnvelopes = calc.calculateAllEnvelopes(1.0, 100.0, 20);
```

---

## 2. Implementation Priority Matrix

| Priority | Feature | Effort | Impact | Status |
|----------|---------|--------|--------|--------|
| 1 | Leak/Release Source Term (3.4) | High | Critical | ❌ Not started |
| 2 | Safety Envelope Calculator (3.6) | Medium | High | ❌ Not started |
| 3 | Risk Model Framework (3.5) | High | High | ❌ Not started |
| 4 | PSV Back Pressure Dynamics (3.3) | Medium | Medium | ⚠️ Partial |
| 5 | Initiating Event Enum (3.1) | Low | Medium | ❌ Not started |
| 6 | Reaction Force Calculation (3.3) | Low | Low | ❌ Not started |

---

## 3. Recommended Implementation Order

### Phase 1: Complete the Release Model (Priority 1)
**Timeline: 2-3 weeks**

Create `neqsim.process.safety.release` package with:
1. `LeakModel` - Hole/rupture specification
2. `SourceTermResult` - Time-series release data
3. `ReleaseExporter` - PHAST/FLACS/KFX/OpenFOAM export
4. Integration with `VesselDepressurization` for inventory tracking

### Phase 2: Safety Envelope Calculator (Priority 2)
**Timeline: 1-2 weeks**

Create `neqsim.process.safety.envelope` package with:
1. `SafetyEnvelopeCalculator` - Compute P-T envelopes
2. `SafetyEnvelope` - Data container with export
3. Integration with existing hydrate/CO2/MDMT calculations

### Phase 3: Risk Framework (Priority 3)
**Timeline: 3-4 weeks**

Create `neqsim.process.safety.risk` package with:
1. `RiskModel` - Event tree / fault tree basics
2. `RiskEvent` - Frequencies and probabilities
3. `MonteCarloRiskAnalysis` - Uncertainty propagation
4. Integration with `ProcessSafetyScenario`

### Phase 4: Enhanced PSV Modeling (Priority 4)
**Timeline: 1-2 weeks**

Enhance existing `SafetyValve` with:
1. Dynamic back pressure during relief
2. Two-phase relief flow
3. Reaction force calculation

---

## 4. Existing Infrastructure Summary

```
neqsim.process.safety/
├── ProcessSafetyScenario.java      ✅ Scenario definition
├── ProcessSafetyAnalyzer.java      ✅ Scenario execution
├── ProcessSafetyLoadCase.java      ✅ Results container
├── ProcessSafetyResultRepository.java  ✅ Results storage
└── ProcessSafetyAnalysisSummary.java   ✅ Summary report

neqsim.process.logic/
├── sis/
│   ├── SafetyInstrumentedFunction.java  ✅ SIF with voting
│   ├── Detector.java                    ✅ Fire/gas detectors
│   └── VotingLogic.java                 ✅ 1oo1, 2oo3, etc.
├── hipps/                               ✅ HIPPS logic
├── esd/                                 ✅ ESD sequences
├── shutdown/                            ✅ Shutdown logic
└── voting/                              ✅ Voting patterns

neqsim.process.equipment.valve/
├── SafetyValve.java                ✅ PSV with hysteresis
├── SafetyReliefValve.java          ✅ Relief valve dynamics
└── RelievingScenario.java          ✅ Scenario definitions

neqsim.process.util.fire/
├── ReliefValveSizing.java          ✅ API 520/521 sizing
├── FireHeatLoadCalculator.java     ✅ API 521 heat input
├── VesselRuptureCalculator.java    ✅ Von Mises stress
├── SeparatorFireExposure.java      ✅ Fire case wrapper
├── TransientWallHeatTransfer.java  ✅ Wall temperature
└── VesselHeatTransferCalculator.java  ✅ Heat transfer

neqsim.process.equipment.tank/
└── VesselDepressurization.java     ✅ Full blowdown (~3000 lines)
    ├── 5 calculation types
    ├── Two-phase support
    ├── Fire case (API 521)
    ├── Valve dynamics
    ├── Hydrate/CO2 risk
    ├── MDMT monitoring
    ├── Flare integration
    └── CSV/JSON export

neqsim.process.equipment.flare/
└── Flare.java                      ✅ Flare equipment
```

---

## 5. Quick Wins (Can Implement Now)

### 5.1 Add InitiatingEvent Enum

```java
public enum InitiatingEvent {
    ESD("Emergency Shutdown"),
    PSV_LIFT("Pressure Safety Valve Lift"),
    RUPTURE("Vessel/Pipe Rupture"),
    LEAK_SMALL("Small Leak (< 10mm)"),
    LEAK_MEDIUM("Medium Leak (10-50mm)"),
    LEAK_LARGE("Large Leak (> 50mm)"),
    BLOCKED_OUTLET("Blocked Outlet"),
    UTILITY_LOSS("Loss of Utility"),
    FIRE_EXPOSURE("Fire Exposure"),
    RUNAWAY_REACTION("Runaway Reaction");
    
    private final String description;
    // ...
}
```

### 5.2 Add BoundaryConditions Class

```java
public class BoundaryConditions implements Serializable {
    private double ambientTemperature = 288.15; // K
    private double windSpeed = 5.0;             // m/s
    private double relativeHumidity = 0.6;      // fraction
    private double solarRadiation = 0.0;        // W/m²
    
    // Builder pattern...
}
```

### 5.3 Hydrate Envelope Method (Add to ThermodynamicOperations)

```java
public double[][] calculateHydrateEnvelope(double pMin, double pMax, int points) {
    double[][] envelope = new double[2][points];
    double pStep = (pMax - pMin) / (points - 1);
    
    for (int i = 0; i < points; i++) {
        double p = pMin + i * pStep;
        system.setPressure(p);
        hydrateFormationTemperature();
        envelope[0][i] = p;
        envelope[1][i] = system.getTemperature();
    }
    return envelope;
}
```

---

## 6. Conclusion

NeqSim's safety simulation capabilities are **more mature than initially apparent**. The main gaps are:

1. **Source Term Generation** — Critical for QRA, needs new package
2. **Safety Envelopes** — Straightforward extension of existing calculations
3. **Risk Framework** — Foundational work needed for probabilistic analysis

The dynamic blowdown module (3.2) is **fully complete** with the `VesselDepressurization` class, including all requested features plus hydrate/CO2 risk assessment.

---

## References

- NeqSim Process Safety Documentation: `docs/fire_blowdown_capabilities.md`
- HIPPS Implementation: `docs/hipps_implementation.md`
- Alarm System Guide: `docs/alarm_system_guide.md`
- VesselDepressurization Tutorial: `notebooks/VesselDepressurizationTutorial.ipynb`
