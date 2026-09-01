---
name: neqsim-standards-lookup
description: "Industry standards lookup and compliance tracking for NeqSim engineering tasks. USE WHEN: any engineering task requires standards compliance (API, ISO, NORSOK, DNV, ASME, EN, ASTM), risk assessment, or safety analysis. Provides equipment-to-standards mapping, database query patterns, results.json schema for standards_applied, and risk standards quick-reference."
last_verified: "2026-08-02"
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
| Pipeline, FlexiblePipe, Cable, Umbilical on seabed | DNV-RP-F109 | Typed kernel `DnvRpF109OnBottomStabilityKernel`; project values remain explicit inputs |
| HeatExchanger, Heater, Cooler | API 660/661, TEMA | `api_standards.csv` |
| Tank | API 650/620, API 2000 | Use `Api2000TankVentingScreeningKernel` for current 7th-edition caller-controlled normal/emergency demand and rated-capacity screening; `api_standards.csv` covers catalog data |
| Valve | ASME B31.3 | `asme_standards.csv` |
| Subsea equipment | NORSOK U-001, DNV-ST-F101 | `norsok_standards.csv`, `subsea_standards.csv` |
| Well casing/tubing | API 5CT, API TR 5C3, NORSOK D-010 | `api_standards.csv`, `norsok_standards.csv` |
| Flange | ASME B16.5 | `asme_standards.csv` |
| Orifice plate / differential-pressure metering | ISO 5167-1/-2, AGA 3 / API MPMS 14.3 | Use `Iso5167OrificeMeteringKernel` for strict ISO 5167-2:2022 screening; keep `Standard_AGA3` for an AGA/API basis and `Orifice` for process simulation |
| CO2 corrosion / materials selection | NORSOK M-506, ISO 15156 / NACE MR0175, NORSOK M-001 | Use `NorsokM506CorrosionDesignKernel` for strict M-506 screening; use `NorsokM506ElectrolyteBridge` for a rigorous brine pH/FeCO3 basis and keep `NorsokM506CorrosionRate` for legacy sweeps |
| Offshore steel fatigue | DNV-RP-C203 | Use `DnvRpC203FatigueDesignKernel` with a verified project-controlled S-N curve and stress spectrum; do not report legacy pipeline/riser shortcuts as exact-edition C203 evidence |
| Submarine-pipeline free spans | DNV-RP-F105 | Use `DnvRpF105FreeSpanScreeningKernel` for the current 2025-12 first-mode/dimensionless screen; project response triggers are not DNV acceptance criteria and the legacy allowable-span calculator is not F105 evidence |
| Inspected pipeline metal loss | DNV-RP-F101 | Use `DnvRpF101CorrodedPipelineScreeningKernel` for the current isolated longitudinal defect/internal-pressure screen; measured geometry and caller-controlled factors are evidence, not values inferred from M-506, and ST-F101 design checks remain separate |
| CO2 pipeline design and operation | DNV-RP-F104 | Use `DnvRpF104Co2PipelineEnvelopeScreeningKernel` for the current caller-controlled composition and operating-envelope margin screen; use its requirement pack only for bounded capability discovery and keep ST-F101, fracture, materials/corrosion, construction, operation, safety, and requalification separate |
| Submarine-pipeline global buckling | DNV-RP-F110 | Use `DnvRpF110GlobalBucklingResponseScreeningKernel` for the current caller-controlled external-analysis force/strain/displacement/feed-in response envelope; keep structural response, soil springs, critical buckling, imperfections/triggers, local capacity, and F109/F114/F105/ST-F101 acceptance separate |
| Submarine-pipeline pipe-soil interaction | DNV-RP-F114 | Use `DnvRpF114PipeSoilInteractionScreeningKernel` for the current caller-controlled vertical/axial/lateral demand-resistance envelope; keep geotechnical model derivation and F109/F110/F105/ST-F101 acceptance separate |
| Fixed-roof tank venting | API 2000 | Use `Api2000TankVentingScreeningKernel` for the current 7th-edition caller-controlled demand/capacity screen; do not infer licensed demand factors or report it as device sizing/conformity |
| Mineral scale / produced water | (industry practice; Davies + Ksp(T)) | `ElectrolyteScaleCalculator` / `ScaleKinetics` / `BrineMixingScaleEvaluator` (`process.chemistry.scale`) |

### DNV-RP-F109 implementation status

`EquipmentDesignKernelRegistry.lookup(StandardType.DNV_RP_F109)` exposes a
`SCREENING` kernel for the exact catalogued edition `2021-05+AMD 2025-09`.
It calculates vertical equilibrium and a transparent absolute-static lateral
screen, or checks displacement supplied by an externally validated generalized or
dynamic response model. It intentionally excludes licensed generalized-design
tables, response generation, environmental-statistics derivation, soil-model
qualification, and conformity assessment. Do not report a passing kernel result as
DNV certification or clause-complete compliance; report the implemented check scope
and retain `engineeringApprovalRequired=true`.

### DNV-ST-F101 pipeline screening

For current DNV-ST-F101 requests, use
`neqsim.process.engineering.calculation.DnvStF101PipelineDesignKernel` with a complete
`DnvStF101PipelineDesignInput`. It preserves operating, incidental, and test pressure; collapse;
propagation buckling; local-buckling load interaction; fatigue; temperature/material de-rating;
safety class; ovality; fabrication route; and installation strain as distinct checks.

Do not route DNV-ST-F101 to `PipeMechanicalDesignCalculator.DNV_OS_F101`. That constant is the
legacy DNV-OS-F101 screen. Missing structural inputs or unsupported editions must remain blocked,
and a calculated result must retain `CALCULATED_REVIEW_REQUIRED`. Never describe a passing screen
as certification or code compliance; require a licensed project copy and independent review.

See `docs/process/dnv_st_f101_pipeline_screening.md` and the `neqsim-capability-map` skill.

## TR/NORSOK Integration Classes

### NORSOK M-506 execution rule

For an explicit NORSOK M-506 calculation, prefer
`neqsim.process.engineering.calculation.NorsokM506CorrosionDesignKernel`. It implements only the
unamended 2017 edition, checks `Pipeline` / `AdiabaticPipe` / `Pipe` applicability, retains raw
unit-explicit input values, and blocks unsupported or out-of-range cases before the mutable legacy
calculator runs. Treat `NorsokM506CorrosionAssessment.getProjectedUniformWallLossMm()` as rate
multiplied by exposure time, not as a specified corrosion allowance or acceptance decision.

The kernel is `SCREENING`: verify the purchased standard, wetting and water-chemistry basis,
localized corrosion, sour service, inhibitor availability, materials selection, and project
criteria independently. When `feCO3SaturationRatio` is enabled, report it as a NeqSim film-factor
extension and retain the source chemistry evidence. Standards Norway's May 2026 systematic-review
notice is the lifecycle source for the catalogued 2017 edition; do not silently apply this kernel to
a later revision.

### ISO 5167 execution rule

For an explicit ISO orifice-metering calculation, use
`neqsim.process.engineering.calculation.Iso5167OrificeMeteringKernel`. The registered method supports
only unamended ISO 5167-2:2022, paired with ISO 5167-1:2022. It requires an `Orifice` equipment
basis, explicit liquid or gas/vapour service, a supported tapping arrangement, and affirmative
single-phase/full-pipe/subsonic/non-pulsating and installation evidence before calculation.

Do not treat `geometryAndInstallationVerified(true)` as evidence created by NeqSim; retain the
inspection and installation record. Keep uncertainty, calibration, straight lengths, plate
condition, custody-transfer acceptance, and project metering procedure outside the kernel. Use
`Standard_AGA3` under an AGA 3/API MPMS 14.3 basis and do not relabel that result as ISO 5167.

### DNV-RP-C203 execution rule

For an explicit DNV-RP-C203 basis, use
`neqsim.process.engineering.calculation.DnvRpC203FatigueDesignKernel`. It supports the catalogued
`2024-10` edition including amendment `2025-10`, rejects additional project amendments, and is a
`SCREENING` S-N/Palmgren-Miner calculation.
The caller must supply and verify a controlled single-slope or continuous bi-linear curve, stress
spectrum, SCF, thickness factor, other stress-range factor, design fatigue factor, damage limit, and
exposure.

Do not copy licensed curve tables into NeqSim and do not infer that a verification Boolean is the
evidence itself. Retain curve/detail selection, environment, fabrication, thickness, weld and SCF
basis, structural stress derivation, load combinations, rainflow counting, inspection plan, and
accountable approval externally. The older pipeline/riser fatigue methods have inconsistent embedded
intercepts and remain legacy estimates, not exact-edition C203 calculations.

### DNV-RP-F105 execution rule

For an explicit DNV-RP-F105 basis, use
`neqsim.process.engineering.calculation.DnvRpF105FreeSpanScreeningKernel`. It supports only the
catalogued unamended `2025-12` edition for `Pipeline` and `AdiabaticPipe`. The kernel evaluates a
simply supported Euler-Bernoulli first mode with caller-supplied effective modal mass and axial
force, then reports current/wave frequency ratios, reduced velocities, and Keulegan-Carpenter
number. Steel outside diameter and hydrodynamic diameter are separate inputs.

Treat the geometry, structural-model, environmental, and project-trigger verification Booleans as
attestations whose evidence must be retained externally. Strouhal number, frequency-ratio band, and
reduced-velocity limits are caller-controlled escalation triggers, not embedded DNV requirements or
acceptance decisions. Keep soil/shoulder stiffness, interacting spans, detailed in-line/cross-flow
VIV and direct-wave response, ULS/FLS, fatigue, monitoring, intervention, and conformity open.

`PipeMechanicalDesignCalculator.calculateAllowableSpanLength(...)` is a legacy fixed-assumption
estimate with fallback and cap behavior. Never report that output as current-edition F105 evidence.

### DNV-RP-F101 execution rule

For an explicit DNV-RP-F101 basis, use
`neqsim.process.engineering.calculation.DnvRpF101CorrodedPipelineScreeningKernel`. It supports the
catalogued `2019-09+AMD:2025-09` edition and only calculates the deterministic isolated
longitudinal metal-loss equation under internal pressure. Require externally verified assessment
wall thickness, measured defect depth and length, caller-controlled depth allowance,
characteristic ultimate tensile strength, internal/external pressures, caller-controlled pressure
factor, and isolated-defect applicability.

Treat every verification Boolean as an attestation, not the evidence itself. Keep inspection
accuracy and growth derivation, factor selection, interacting/complex defects, combined
longitudinal compression, probabilistic methods, crack/dent/gouge/blister or weld damage, repair,
fitness-for-service acceptance, and accountable approval external. Do not turn
`NorsokM506CorrosionAssessment.getProjectedUniformWallLossMm()` into RP-F101 defect dimensions.

RP-F101 remaining-strength screening is not DNV-ST-F101 original design. It does not replace
pressure containment, collapse, propagation buckling, local buckling, load interaction, fatigue,
incidental/test pressure, de-rating, safety class, ovality, fabrication route, or installation
strain checks.

### DNV-RP-F104 execution rule

For an explicit current `DNV-RP-F104 2021-02+AMD:2021-09` basis, use
`DnvRpF104Co2PipelineEnvelopeScreeningKernel`. Require verified project CO2/water limits,
other-impurity status, composition/EOS basis, the minimum-pressure interpretation and uncertainty of
each supplied single-phase boundary, an ordered operating profile, MAOP, design temperatures, and
external integrity/lifecycle review evidence. Negative margins remain calculated findings; missing
evidence blocks execution.

Use `StandardRegistry.requireRequirementPack(StandardSelection.strictRequirements(
StandardType.DNV_RP_F104))` to discover related thermodynamic, hydraulic, corrosion, mechanical,
monitoring, and consequence capabilities. The pack does not prove clause coverage. Do not substitute
the pure-CO2 critical point, `CO2FlowCorrections.isDensePhase(...)`, or embedded
`DensePhaseCO2Corrosion` values for the project basis. F104 does not replace DNV-ST-F101 structural
design or the external fracture, materials, corrosion, construction, safety, operation, and
requalification assessments.

### DNV-RP-F114 execution rule

For an explicit current `DNV-RP-F114 2021-05` basis, use
`DnvRpF114PipeSoilInteractionScreeningKernel`. Supply positive pipe diameter and submerged weight,
then named route/design-situation cases with externally established non-negative vertical, axial,
and lateral demand magnitudes and positive resistance magnitudes on a consistent N/m basis.

Require external evidence for applicability, site investigation, soil interpretation,
pipe/interface configuration, installation history, cyclic/drainage/rate/consolidation effects,
load-displacement and resistance models, uncertainty/spatial variability, design actions and
acceptance criteria, and lifecycle interfaces. A negative margin remains a calculated finding.
Never derive F114 resistance from NeqSim soil thermal conductivity, burial heat-transfer inputs, a
generic friction coefficient, or submerged weight alone. Keep F109 on-bottom stability, F110 global
buckling, F105 free spans, ST-F101 structural design, and conformity external.

### DNV-RP-F110 execution rule

For an explicit current `DNV-RP-F110 2019-09+AMD:2021-09` basis, use
`DnvRpF110GlobalBucklingResponseScreeningKernel`. Supply positive pipe diameter and structural wall
thickness, then named route/design-situation cases containing externally analysed effective force,
peak longitudinal strain, peak global displacement, and required feed-in length together with
caller-controlled allowable or available values.

Require external evidence for applicability, operating envelope/effective force, pipe properties
and as-laid geometry, pipe-soil interaction, imperfections/triggers/design strategy, global
structural model, design situations/load combinations, local capacity/strain criteria,
uncertainty/sensitivity/buckle sharing, and lifecycle actions. A negative margin remains calculated.
Never interpret the force limit as a NeqSim-derived critical-buckling or initiation criterion. Keep
F109/F114/F105, every DNV-ST-F101 check, conformity, and accountable approval external.
### API 2000 execution rule

For an explicit API 2000 basis, use
`neqsim.process.engineering.calculation.Api2000TankVentingScreeningKernel`. It supports only the
catalogued unamended `7th Ed` for caller-verified non-refrigerated fixed-roof `Tank` or
`SimpleTankFiller` service. Supply maximum filling/withdrawal rates, caller-controlled movement
ratios, externally established thermal/other normal demands, total emergency demand, rated
normal/emergency capacities, their rated pressure/vacuum conditions, tank limits, and one common
gas-volume reference state.

Treat the evidence Booleans as attestations, not proof. Keep API demand tables/equations, scenario
derivation, vent area and device selection, manufacturer curves, pressure losses, flame arresters,
blanketing, external floating roofs, refrigerated storage, installation/testing, and conformity
external. An adequate caller-controlled constraint result is not API compliance.

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
