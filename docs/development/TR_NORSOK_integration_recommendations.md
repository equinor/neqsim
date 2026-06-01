---
title: "TR/NORSOK Integration Recommendations for NeqSim"
description: "Synthesis of Equinor TR and NORSOK standard review with concrete recommendations for NeqSim skills, agents, Java code, and data access integrations."
---

## Documents Reviewed

| Document | Scope | Key NeqSim Relevance |
|----------|-------|---------------------|
| **STS0131** (2026) | Technical safety offshore — supplement to NORSOK S-001 | Time-to-rupture criteria, overpressure frequency targets, dispersion LEL thresholds, subsea leak detection |
| **NORSOK S-001:2020** | Technical safety (26 clauses) | ESD, EDP, fire/gas detection, PFP, structural, flare/vent |
| **TR2237** | Performance standards for safety barriers (onshore) | PS-01 through PS-14+ definition, demand/capacity framework |
| **TR1965** (v5.02) | Gas scrubber design | K-factor tables, surface tension derating, internals sizing, documentation requirements |
| **NORSOK P-002:2023** | Process system design | Design P/T, line sizing, separator/scrubber/HX/compressor requirements |
| **TR2329** (v2.02) | PFD and P&ID requirements | Drawing standards for flowsheet documentation |
| **TR3031** | Automation requirements | Control system, SIS, F&G architecture |

---

## Executive Summary: Top 6 Recommendations

| # | Recommendation | Value | Effort | Priority |
|---|---------------|-------|--------|----------|
| 1 | **TR1965 Scrubber Compliance Checker** (extend `ConformityRuleSet`) | Direct compliance verification for every scrubber design | Medium | **High** |
| 2 | **STS0131 Time-to-Rupture Acceptance** (extend `DepressurizationSimulator`) | Fire-case acceptance against Equinor-specific criteria | Low | **High** |
| 3 | **NORSOK P-002 Line Sizing Validator** (new utility class) | Automated pipe sizing compliance for process models | Medium | **High** |
| 4 | **Subsea Leak Detection Sensitivity Model** (new agent workflow) | Mass balance sensitivity analysis for subsea systems | Medium | **Medium** |
| 5 | **Performance Standard Auto-Population from TR2237** (extend `BarrierRegister`) | Pre-populated PS templates from TR numbering | Low | **Medium** |
| 6 | **Standards-Aware Design Review Agent** (new agent) | Automated TR/NORSOK compliance checking across full process models | High | **Medium** |

---

## Detailed Recommendations

### 1. TR1965 Gas Scrubber Compliance Checker

**What exists:** `ConformityRuleSet` with `TR3500RuleSet` implementation, `GasScrubberMechanicalDesign` with K-factor, internals, and inlet device configuration.

**Gap:** TR1965 (v5.02) defines a different/more detailed set of requirements than TR3500:
- K-factor limits by internals type (mesh 0.11, mesh+AFC 0.15, mesh+HF-vane 0.15, compact 0.9)
- Surface tension derating below 10 mN/m using Weinaug-Katz model (SR-58579)
- Design margins: 10-25% gas capacity, 20% liquid capacity
- Max entrainment: 13 litre/MSm³ unless documented otherwise
- Internal distances: HHLL to inlet ≥500mm or 1D, inlet to mesh ≥900mm or 0.45D

**Implementation:**

```java
// In neqsim.process.mechanicaldesign.separator.conformity
public class TR1965RuleSet extends ConformityRuleSet {

    // K-factor limits per internals type (Table 2-1)
    private static final double K_MESH_PAD = 0.11;
    private static final double K_MESH_AFC = 0.15;
    private static final double K_MESH_HF_VANE = 0.15;
    private static final double K_COMPACT = 0.9;

    // Surface tension derating threshold
    private static final double SIGMA_DERATING_THRESHOLD_MNM = 10.0;

    // Max entrainment [litre/MSm3]
    private static final double MAX_ENTRAINMENT = 13.0;

    @Override
    public ConformityReport evaluate(GasScrubberMechanicalDesign design) {
        // 1. Select K-factor limit based on internals type
        // 2. Apply Weinaug-Katz surface tension derating if sigma < 10 mN/m
        // 3. Check design margins (gas 10-25%, liquid 20%)
        // 4. Check entrainment from DemistingInternal.getCarryOverFraction()
        // 5. Check internal distances (HHLL-to-inlet, inlet-to-mesh)
    }
}
```

**Surface tension derating calculation (leverages NeqSim):**
```java
// NeqSim already calculates surface tension via PhysicalProperties
double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1); // N/m
double sigmaMNm = sigma * 1000.0; // convert to mN/m
if (sigmaMNm < SIGMA_DERATING_THRESHOLD_MNM) {
    double deratingFactor = weinaugKatzDerating(sigmaMNm);
    effectiveKLimit = baseKLimit * deratingFactor;
}
```

**Why this is high value:** Every scrubber design study must document TR1965 compliance. NeqSim already has the fluid model (surface tension), separator model (K-factor), and conformity framework (`ConformityRuleSet`). Adding TR1965 is ~200 lines of Java.

---

### 2. STS0131 Time-to-Rupture Acceptance Criteria

**What exists:** `DepressurizationSimulator` with API 521 §5.20 acceptance (50% pressure in 15 min, 7 barg in 15 min).

**Gap:** STS0131 Req-14766 (Clause 5.6.3.4) defines additional Equinor-specific criteria:
- Maximum allowed **escalated fire rate** (kg/s) within time interval t_n
- **Time to escape** requirement (personnel egress time)
- **Maximum pressure at rupture** (material derating with temperature)
- **Acceptable remaining mass** (limiting inventory that can escalate)

**Implementation:**

```java
// Extend DepressurizationResult with STS0131 acceptance
public class STS0131AcceptanceCriteria implements Serializable {
    private double maxEscalatedFireRate;      // kg/s
    private double timeToEscape;              // seconds
    private double maxPressureAtRupture;      // bara (from material derating curve)
    private double acceptableRemainingMass;   // kg

    public STS0131AcceptanceResult evaluate(DepressurizationResult result) {
        // Check at each time step:
        // 1. If rupture occurs (P(T_wall) > allowable stress), what is the fire rate?
        // 2. Is personnel escape complete before escalation?
        // 3. Is remaining inventory below acceptable mass at time of potential rupture?
    }
}
```

**Data access integration:** The time-to-rupture calculation needs:
- Material derating curves (can come from STID material certificates → `neqsim-stid-retriever`)
- Fire zone geometry (from PFP/fire documents → `neqsim-technical-document-reading`)
- Personnel egress time (from escape analysis documents)

**Why this is high value:** Equinor is participating in a JIP to create an industry standard for time-to-rupture. Having the calculation engine ready in NeqSim positions it as the reference implementation.

---

### 3. NORSOK P-002 Line Sizing Validator

**What exists:** `PipeBeggsAndBrills` for pipe flow, but no automated compliance checking against P-002.

**Gap:** NORSOK P-002 Clause 8 defines:
- **Gas lines:** Max velocity limits, recommended pressure drops (Table/Clause 8.5)
- **Liquid lines:** Velocity limits for pump suction/discharge, gravity flow (Clause 8.4)
- **Two-phase lines:** Erosional velocity, flow regime criteria (Clause 8.6)
- **Design pressure/temperature rules:** Clause 5 defines how to set DP/DT from operating conditions

**Implementation:**

```java
package neqsim.process.mechanicaldesign.piping;

public class NorsokP002LineSizingValidator {

    public static class LineSizingResult {
        private double velocity;          // m/s
        private double pressureDrop;      // Pa/m
        private double erosionalVelocity; // m/s (API RP 14E)
        private boolean velocityOK;
        private boolean pressureDropOK;
        private String service;           // "gas", "liquid", "two-phase"
        private String standard = "NORSOK P-002:2023";
    }

    /**
     * Check a NeqSim pipe segment against P-002 criteria.
     */
    public static LineSizingResult validate(PipeBeggsAndBrills pipe) {
        // 1. Determine service from fluid phases
        // 2. Calculate actual velocity
        // 3. Compare against P-002 limits for service type
        // 4. Check pressure drop against recommended values
        // 5. For two-phase: check erosional velocity ratio
    }

    /**
     * Check design pressure against P-002 Clause 5 rules.
     * DP >= max(MAOP, highest normal operating P + margin)
     */
    public static DesignPressureResult validateDesignPressure(
        double designPressure, double normalOperatingPressure, String systemType) {
        // Clause 5.2.1 rules
    }
}
```

**Agent integration:** The `@make a neqsim process simulation` agent could automatically run P-002 validation after building a process model, flagging any pipe segments that violate sizing criteria.

---

### 4. Subsea Leak Detection Sensitivity Model

**What exists:** NeqSim has `PipeBeggsAndBrills` for pipeline flow, mass balance capabilities, and the `neqsim-plant-data` skill for historian integration.

**Gap:** STS0131 Req-13704 requires subsea leak detection using:
- Pressure/flow/mass balance methods
- Acoustic methods
- Satellite radar methods

The computational requirement is: **What is the minimum detectable leak rate?** This requires sensitivity analysis of the mass balance method.

**Implementation (Agent workflow + Java utility):**

```java
package neqsim.process.safety.leakdetection;

public class MassBalanceLeakDetector {
    private ProcessSystem pipeline;
    private double flowMeasurementUncertainty;  // % of reading
    private double pressureMeasurementUncertainty; // bara
    private double temperatureUncertainty; // K
    private double linepackVolume; // m3

    /**
     * Calculate minimum detectable leak rate for a given
     * measurement configuration and steady-state conditions.
     */
    public LeakDetectionSensitivity calculateSensitivity() {
        // 1. Run base case with current pipeline model
        // 2. Introduce small perturbation (leak) at midpoint
        // 3. Calculate resulting signal change at inlet/outlet instruments
        // 4. Compare signal change against measurement uncertainty
        // 5. Find minimum leak rate that exceeds detection threshold
    }
}
```

**Data access integration:**
- Measurement uncertainty from instrument datasheets (STID)
- Pipeline geometry and inventory from process model
- Operating conditions from plant historian (tagreader)

---

### 5. Performance Standard Auto-Population from TR2237

**What exists:** `PerformanceStandard`, `SafetyBarrier`, `BarrierRegister`, `SafetySystemPerformanceAnalyzer` — all fully implemented.

**Gap:** TR2237 defines a standard set of PS numbers (PS 01 through PS 14+) with specific functional requirements, demand modes, and acceptance criteria. Currently, engineers must manually create these. Pre-populating from a TR2237 template would save hours.

**Implementation:**

```java
package neqsim.process.safety.barrier;

public class TR2237Templates {

    /**
     * Creates a pre-populated barrier register with all TR2237 onshore
     * performance standards as templates.
     */
    public static BarrierRegister createOnshoreTemplate() {
        BarrierRegister register = new BarrierRegister("TR2237-ONSHORE");

        // PS 01 - Process containment
        register.addPerformanceStandard(new PerformanceStandard("PS-01")
            .setTitle("Process Containment")
            .setSafetyFunction("Maintain primary containment of hydrocarbons")
            .setDemandMode(DemandMode.CONTINUOUS)
            .setRequiredAvailability(0.999)
            .addAcceptanceCriterion("No uncontrolled releases during normal operations"));

        // PS 08 - Emergency Depressurisation
        register.addPerformanceStandard(new PerformanceStandard("PS-08")
            .setTitle("Emergency Depressurisation")
            .setSafetyFunction("Reduce pressure to limit fire-exposed inventory")
            .setDemandMode(DemandMode.LOW_DEMAND)
            .setTargetPfd(0.01) // SIL 1 minimum
            .setResponseTimeSeconds(900.0) // 15 min per API 521
            .addAcceptanceCriterion("Reduce to 50% of initial pressure within 15 minutes")
            .addAcceptanceCriterion("Reduce to 7 barg within 15 minutes under fire"));

        // PS 09 - Active Fire Protection
        // PS 10 - Passive Fire Protection
        // ... etc.
        return register;
    }

    /**
     * STS0131 mapping: Equinor PS numbers → NORSOK S-001 clauses.
     */
    public static Map<String, String> getEquinorToNorsokMapping() {
        // PS 01 → S-001 Clause 6, PS 02 → Clause 7, etc.
    }
}
```

**Why this is useful:** When the `@run neqsim safety and depressuring simulation` agent creates a barrier register, it can auto-populate with TR2237 templates and then the `SafetySystemPerformanceAnalyzer` validates each barrier against the pre-defined criteria.

---

### 6. Standards-Aware Design Review Agent

**Concept:** A new agent (`@review design against standards`) that takes a completed `ProcessSystem` and systematically checks it against all applicable TR/NORSOK requirements.

**Workflow:**
1. Identify equipment types in the process model (separators, scrubbers, compressors, pipes, HX)
2. For each equipment type, look up applicable standards (TR1965 for scrubbers, P-002 for pipe sizing, TR3500 for separators, etc.)
3. Run the relevant conformity checker / validator
4. Produce a consolidated compliance report with PASS/FAIL/WARNING per requirement

**Agent definition:**
```yaml
name: review design against standards
description: >
  Reviews a NeqSim process model against applicable TR/NORSOK requirements.
  Checks scrubber K-factors (TR1965), pipe sizing (P-002), design P/T (P-002),
  separator internals (TR3500), EDP acceptance (S-001/STS0131), and performance
  standards (TR2237). Produces a compliance matrix.
```

**Skills it would load:**
- `neqsim-standards-lookup` — equipment-to-standards mapping
- `neqsim-api-patterns` — reading equipment state from process models
- `neqsim-controllability-operability` — turndown and control valve checks

---

## Additional Integration Opportunities (Lower Priority)

### 7. STS0131 Overpressure Frequency Table for LOPA

The existing `LOPAResult` class could include the STS0131 Clause 10.4.7 frequency targets:

| Pressure Level | Target Frequency (per year) |
|---|---|
| P > 2× DP | ≤ 1×10⁻⁵ |
| Test P < P ≤ 2× DP | ≤ 1×10⁻⁴ |
| DP < P ≤ Test P | ≤ 1×10⁻³ |
| P ≤ DP | ≤ 1×10⁻² |

This could be a lookup table in `LOPAResult.evaluateOverpressureFrequency(pressure, designPressure, testPressure)`.

### 8. TR3031 Automation — SIS Architecture Validation

The SIS/SIF infrastructure (`SafetyInstrumentedFunction`, `VotingLogic`) already supports architecture specification. TR3031 could provide pre-defined voting architecture requirements for common SIF types (ESD, HIPPS, F&G) that the agent could validate against.

### 9. Gas Detection Coverage from STS0131

STS0131 requires specific gas detection coverage calculations (area coverage, detector placement). NeqSim's `GasDetector` class and the consequence analysis (`ReleaseDispersionScenarioGenerator`) could feed a coverage assessment that checks if detectors would activate for modelled release scenarios.

### 10. NORSOK P-002 TEG Regeneration Compliance (Clause 16)

P-002 Clause 16 specifies TEG system requirements (reboiler temperature, stripping gas flow, contactor design). NeqSim already has TEG process models — adding a P-002 compliance check would automate verification.

---

## Data Access / STID Integration Priorities

| Data Source | Use Case | Integration Point |
|---|---|---|
| **Material certificates** (STID) | Time-to-rupture material derating curves | `DepressurizationSimulator` + `STS0131AcceptanceCriteria` |
| **Instrument datasheets** (STID) | Measurement uncertainty for leak detection sensitivity | `MassBalanceLeakDetector` |
| **Fire zone drawings** (STID) | Fire exposure area for API 521 heat input | `ReliefValveSizing.calculateFireHeatInput()` |
| **Line list / piping specs** (STID) | Pipe diameters, materials, design P/T for P-002 validation | `NorsokP002LineSizingValidator` |
| **C&E matrices** (STID) | SIF architecture, voting logic, trip setpoints | `SafetyInstrumentedFunction` population |
| **Scrubber datasheets** (STID) | Internals type, K-factor rating, design parameters | `TR1965RuleSet` input data |
| **PI historian** (tagreader) | Operating conditions for real-time compliance checking | Digital twin + compliance monitoring |

---

## Recommended Implementation Order

1. **TR1965 Scrubber RuleSet** — Minimal code (~200 lines), builds on existing `ConformityRuleSet` framework, immediate value for scrubber design studies
2. **STS0131 Depressurization Acceptance** — Small extension (~100 lines) to existing `DepressurizationResult`, high relevance to ongoing JIP
3. **P-002 Line Sizing Validator** — New class (~300 lines), enables automated pipe sizing checks for all process models
4. **TR2237 Performance Standard Templates** — Pre-populated data (~150 lines), improves barrier register creation workflow
5. **Leak Detection Sensitivity** — New capability (~400 lines), required by STS0131 for subsea projects
6. **Design Review Agent** — Orchestration layer that ties 1-5 together into a single review workflow
