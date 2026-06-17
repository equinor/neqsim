---
name: neqsim-standards-lookup
description: "Industry standards lookup and compliance tracking for NeqSim engineering tasks. USE WHEN: any engineering task requires standards compliance (API, ISO, NORSOK, DNV, ASME, EN, ASTM), risk assessment, or safety analysis. Provides equipment-to-standards mapping, database query patterns, results.json schema for standards_applied, and risk standards quick-reference."
last_verified: "2026-07-04"
---

# NeqSim Standards Lookup

Reference for identifying, applying, and documenting industry standards compliance
in every engineering task. All tasks — Quick, Standard, or Comprehensive — must
identify applicable standards proportional to task depth.

## Standards Identification (MANDATORY First Step)

Before any simulation or analysis, identify applicable standards:

| Task Scale | Standards Requirement |
|------------|----------------------|
| **Quick** | 1-line note: "Per \[STANDARD\]" or "N/A — property lookup" |
| **Standard** | Table of applicable standards with scope and status |
| **Comprehensive** | Full table with clause numbers, design values, and compliance evidence |

## Equipment → Standards Mapping

NeqSim's standards database is in `src/main/resources/designdata/standards/`.
The index file `standards_index.csv` maps equipment types to applicable standards:

| Equipment Type | Primary Standards | NeqSim CSV File |
|----------------|-------------------|-----------------|
| Separator, ThreePhaseSeparator, GasScrubber | NORSOK P-001, API 12J, ASME VIII | `norsok_standards.csv`, `api_standards.csv`, `asme_standards.csv` |
| Compressor | API 617, NORSOK P-002 | `api_standards.csv`, `norsok_standards.csv` |
| Pump | API 610 | `api_standards.csv` |
| Pipeline, AdiabaticPipe, MultiphasePipe | NORSOK L-001, ASME B31.3/B31.4/B31.8, DNV-ST-F101 | `norsok_standards.csv`, `asme_standards.csv`, `dnv_iso_en_standards.csv` |
| HeatExchanger, Heater, Cooler | API 660/661, TEMA | `api_standards.csv` |
| Tank | API 650/620 | `api_standards.csv` |
| Valve | ASME B31.3 | `asme_standards.csv` |
| Subsea equipment | NORSOK U-001, DNV-ST-F101 | `norsok_standards.csv`, `subsea_standards.csv` |
| Well casing/tubing | API 5CT, API TR 5C3, NORSOK D-010 | `api_standards.csv`, `norsok_standards.csv` |
| Flange | ASME B16.5 | `asme_standards.csv` |

## TR/NORSOK Integration Classes

Use these Java classes when a task references Equinor technical requirements,
STS0131, TR1965, TR2237, or NORSOK P-002:

| Scope | Class | Use |
|-------|-------|-----|
| Gas scrubber conformance | `neqsim.process.mechanicaldesign.separator.conformity.ConformityRuleSet.create("TR1965")` | Checks TR1965 K-factor, gas/liquid margins, entrainment, and scrubber layout metadata configured on `GasScrubberMechanicalDesign`. |
| Blowdown fire acceptance | `neqsim.process.safety.depressurization.STS0131AcceptanceCriteria` | Evaluates `DepressurizationResult` against time-to-escape/time-to-rupture pressure, inventory, and escalated fire-rate limits. |
| Piping/line sizing | `neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator` | Screens `PipeLineInterface` velocity, pressure gradient, and erosional velocity using NORSOK P-002 style limits. |
| Leak detection sensitivity | `neqsim.process.safety.leakdetection.MassBalanceLeakDetector` | Estimates minimum detectable leak rate from flow, pressure, temperature, and linepack uncertainty. |
| Performance standards | `neqsim.process.safety.barrier.TR2237Templates` | Creates starter barrier registers with TR2237-style performance standards and NORSOK S-001 topic mappings. |
| Standards review | `neqsim.process.safety.compliance.StandardsDesignReview` | Converts supported calculated checks from a `ProcessSystem` into `StandardsComplianceReport`. |
| Overpressure LOPA targets | `LOPAResult.getSTS0131OverpressureTargetFrequency(...)` | Selects target event frequency from STS0131 pressure severity bands. |
| LEL endpoint policy | `GasDispersionAnalyzer.builder().sts0131IntegralEndpoint()` | Uses 20% LFL for integral dispersion tools; `sts0131CfdEndpoint()` uses 50% LFL. |

### TR1965 Gas Scrubber Pattern

```java
GasScrubber scrubber = new GasScrubber("inlet scrubber", feed);
scrubber.run();
scrubber.initMechanicalDesign();
GasScrubberMechanicalDesign design = scrubber.getMechanicalDesign();
design.setInnerDiameter(2.0);
design.setMeshPad(3.0, 100.0);
design.setLaHHElevationM(0.5);
design.setInletDeviceElevationM(1.2);
design.setMeshPadElevationM(2.3);
design.setLiquidEntrainmentLitresPerMSm3(5.0);
design.setLiquidDesignMarginFraction(0.25);
design.setConformityRules("TR1965");
ConformityReport report = design.checkConformity();
```

### STS0131 Blowdown Acceptance Pattern

```java
DepressurizationResult blowdown = simulator.run();
STS0131AcceptanceCriteria criteria = new STS0131AcceptanceCriteria()
  .setTimeToEscapeS(120.0)
  .setEstimatedTimeToRuptureS(300.0)
  .setMaximumPressureAtRuptureBara(15.0)
  .setMaximumRemainingMassKg(500.0)
  .setMaximumEscalatedFireRateKgPerS(2.0);
STS0131AcceptanceResult acceptance = blowdown.evaluateSTS0131(criteria);
```

## Database Query Pattern (Java)

```java
// Query standards values for a specific equipment type
import neqsim.util.database.NeqSimProcessDesignDataBase;
import java.sql.*;

try (Connection conn = NeqSimProcessDesignDataBase.createConnection()) {
    String sql = "SELECT * FROM api_standards "
               + "WHERE EQUIPMENTTYPE = ? AND STANDARD_CODE = ?";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, "Separator");
    stmt.setString(2, "API-12J");
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
        String spec = rs.getString("SPECIFICATION");
        double minVal = rs.getDouble("MINVALUE");
        double maxVal = rs.getDouble("MAXVALUE");
        String unit = rs.getString("UNIT");
    }
}
```

## Database Query Pattern (Python / Jupyter)

```python
# Query via mechanical design classes
separator.initMechanicalDesign()
design = separator.getMechanicalDesign()
design.setDesignStandardCode("NORSOK-P-001")
design.setCompanySpecificDesignStandards("OperatorA")
design.readDesignSpecifications()
design.calcDesign()
print(design.toJson())
```

## CSV Column Reference

All standards CSV files share this schema:

| Column | Type | Description |
|--------|------|-------------|
| `STANDARD_CODE` | String | Standard identifier (e.g., "API-12J", "NORSOK-P-001") |
| `VERSION` | String | Edition/revision (e.g., "8th Ed", "Rev 5") |
| `EQUIPMENTTYPE` | String | NeqSim class name (e.g., "Separator", "Compressor") |
| `SPECIFICATION` | String | Parameter name (e.g., "GasLoadFactor", "SurgeMargin") |
| `MINVALUE` | Double | Minimum allowed or typical low value |
| `MAXVALUE` | Double | Maximum allowed or typical high value |
| `UNIT` | String | Physical unit (e.g., "m/s", "%", "mm") |
| `DESCRIPTION` | String | Human-readable description |

## results.json — `standards_applied` Schema

Every task's `results.json` should include a `standards_applied` array:

```json
"standards_applied": [
  {
    "code": "NORSOK P-001 Rev 5",
    "scope": "Separator sizing — K-factor and retention time",
    "status": "PASS",
    "design_value": 0.13,
    "limit": "0.12–0.15 m/s",
    "unit": "m/s",
    "clause": "Table A-1"
  },
  {
    "code": "API 617 8th Ed",
    "scope": "Compressor surge margin",
    "status": "PASS",
    "design_value": 12.5,
    "limit": ">10%",
    "unit": "%",
    "clause": "Section 2.6"
  },
  {
    "code": "DNV-ST-F101",
    "scope": "Pipeline wall thickness",
    "status": "INFO",
    "design_value": null,
    "limit": null,
    "unit": null,
    "clause": "Not applied — onshore pipeline"
  }
]
```

### Required Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `code` | String | **Yes** | Standard code with version (e.g., "API 520 Part I 10th Ed") |
| `scope` | String | **Yes** | What aspect was checked (e.g., "Relief valve sizing") |
| `status` | String | **Yes** | `PASS` / `FAIL` / `INFO` / `N/A` |
| `design_value` | Number | No | Calculated value from simulation |
| `limit` | String | No | Standard's requirement or range |
| `unit` | String | No | Unit for design_value and limit |
| `clause` | String | No | Specific clause or table reference |

### Status Values

| Status | Meaning |
|--------|---------|
| `PASS` | Design value meets the standard's requirement |
| `FAIL` | Design value violates the standard — action required |
| `INFO` | Standard identified and noted, no pass/fail applicable |
| `N/A` | Standard exists but does not apply to this specific case |

## Risk & Safety Standards Quick-Reference

### Risk Assessment (ISO 31000 / NORSOK Z-013)

NeqSim classes in `neqsim.process.equipment.failure`:

| Class | Standard | Purpose |
|-------|----------|---------|
| `RiskMatrix` | ISO 31000, NORSOK Z-013 | 5×5 risk matrix with likelihood × consequence |
| `RiskEvent` | ISO 31000 | Individual risk event with probability and consequence |
| `RiskModel` | ISO 31000, QRA | Monte Carlo simulation for risk quantification |
| `AutomaticScenarioGenerator` | IEC 61882 (HAZOP) | HAZOP deviation generation (NO_FLOW, HIGH_PRESSURE, etc.) |

### Safety Instrumented Systems (IEC 61508 / IEC 61511)

| Class | Standard | Purpose |
|-------|----------|---------|
| `SafetyInstrumentedFunction` | IEC 61508, IEC 61511 | SIF with SIL rating (1–4) and PFD calculation |
| `SISIntegratedRiskModel` | IEC 61511, LOPA | Layer of Protection Analysis with IPL credit |

### Fire & Depressuring (API 521)

| Class | Standard | Purpose |
|-------|----------|---------|
| `FireProtectionDesign` | API 521 | Fire case heat input, pool/jet fire modeling |
| `AlarmTripScheduleGenerator` | IEC 61511, NORSOK I-001 | Alarm and trip schedule generation |
| `NoiseAssessment` | ISO 9613, NORSOK S-002 | Equipment noise prediction |

### Risk Proportionality by Task Scale

| Scale | Risk Requirement |
|-------|-----------------|
| **Quick** | Not required (unless safety-critical) |
| **Standard** | 3–5 line risk table with top risks and mitigation |
| **Comprehensive** | Full ISO 31000 risk register with 5×5 matrix, mitigation, ALARP |

## Gas Quality Standards

NeqSim has extensive gas quality standard implementations in `neqsim.standards.gasquality`:

| Standard | Class | Purpose |
|----------|-------|---------|
| ISO 6976 | `Standard_ISO6976` | Calorific value, Wobbe index, relative density |
| ISO 12213 | `Standard_ISO12213` | Compression factor (AGA 8) |
| ISO 13443 | Standard_ISO13443 | Natural gas — standard reference conditions |
| ISO 14687 | — | Hydrogen fuel quality |
| ISO 15403 | — | Natural gas for vehicles (CNG) |
| AGA 3 (API 14.3) | `UKofficialOFGEM_ISO6976` | Orifice flow measurement |
| AGA 7 | — | Turbine flow measurement |
| GPA 2145 | `Standard_ISO6976` (via) | Physical constants for hydrocarbons |
| EN 16723 | — | Biomethane injection quality |
| EN 16726 | — | Gas quality — H-gas specification |

## Oil Quality Standards

In `neqsim.standards.oilquality`:

| Standard | Class | Purpose |
|----------|-------|---------|
| ASTM D86 | `Standard_ASTM_D86` | Distillation of petroleum products |
| ASTM D1160 | `Standard_ASTM_D1160` | Vacuum distillation |
| ASTM D2887 | `Standard_ASTM_D2887` | Simulated distillation (GC) |
| ASTM D6377 | `Standard_ASTM_D6377` | Reid vapor pressure (VPCR4) |

## Typical Standards by Task Type

### Type A — Property Calculation
- ISO 6976 (gas properties), GERG-2008 (compressibility)

### Type B — Process Simulation
- NORSOK P-001 (process design), API 12J (separators), API 617 (compressors), TEMA (heat exchangers)

### Type C — PVT Study
- ISO 6976, GPA 2145, ASTM D86/D2887 (oil characterization)

### Type D — Standards Compliance
- Direct application of the requested standard

### Type E — Feature Implementation
- Standards that the new feature must implement

### Type F — Mechanical Design
- ASME VIII (vessels), DNV-ST-F101 (subsea pipe), NORSOK L-001 (piping), API 5CT (casing)

### Type G — Workflow / Field Development
- NORSOK Z-013 (risk), ISO 31000 (risk management), NORSOK P-001 (process), company TRs

## Post-Simulation Standards Check Pattern

After running a process simulation, check key results against standards:

```python
# Example: Check separator K-factor against NORSOK P-001
k_factor = separator.getInternalDiameter()  # Get from results
# Look up limit from standards database
if k_factor < 0.12 or k_factor > 0.15:
    print("WARNING: K-factor outside NORSOK P-001 range (0.12-0.15 m/s)")
    standards_status = "FAIL"
else:
    standards_status = "PASS"
```

## Workflow Integration

1. **Phase 0 (Setup)**: Identify task type → look up applicable standards from mapping table above
2. **Phase 1 (Scope)**: List standards in `task_spec.md` under "Applicable standards"
3. **Phase 2 (Analysis)**: Use standards values as design inputs and validation limits
4. **Phase 2 (Results)**: Populate `standards_applied` array in `results.json`
5. **Phase 3 (Report)**: Standards compliance table auto-rendered in Word/HTML report
